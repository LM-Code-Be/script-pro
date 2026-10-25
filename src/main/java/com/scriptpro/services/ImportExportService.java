package com.scriptpro.services;

import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.models.Script;
import com.scriptpro.models.ScriptLanguage;
import com.scriptpro.utils.DateFormatter;
import com.scriptpro.utils.PdfReportWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ImportExportService {
    private final ScriptService scriptService;

    public ImportExportService(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    public Script importScript(Path file) throws IOException {
        String content = Files.readString(file, StandardCharsets.UTF_8);
        Script script = new Script();
        String fileName = file.getFileName().toString();
        int extensionIndex = fileName.lastIndexOf('.');
        script.setName(extensionIndex > 0 ? fileName.substring(0, extensionIndex) : fileName);
        script.setCategory("Importés");
        script.setLanguage(ScriptLanguage.fromExtension(fileName));
        script.setDescription("Importé le " + Instant.now());
        script.setContent(content);
        return scriptService.save(script);
    }

    public Path exportScript(Path directory, Script script) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(script.exportFileName());
        Files.writeString(target, script.getContent(), StandardCharsets.UTF_8);
        return target;
    }

    public Path exportLibrary(Path file, List<Script> scripts) throws IOException {
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        StringBuilder builder = new StringBuilder();
        builder.append("[\n");
        for (int index = 0; index < scripts.size(); index++) {
            Script script = scripts.get(index);
            builder.append("  {\n");
            builder.append("    \"name\": \"").append(escape(script.getName())).append("\",\n");
            builder.append("    \"category\": \"").append(escape(script.getCategory())).append("\",\n");
            builder.append("    \"language\": \"").append(script.getLanguage().name()).append("\",\n");
            builder.append("    \"description\": \"").append(escape(script.getDescription())).append("\",\n");
            builder.append("    \"content\": \"").append(escape(script.getContent())).append("\"\n");
            builder.append("  }");
            if (index < scripts.size() - 1) {
                builder.append(",");
            }
            builder.append("\n");
        }
        builder.append("]\n");
        Files.writeString(file, builder.toString(), StandardCharsets.UTF_8);
        return file;
    }

    public Path exportScriptPdf(Path file, Script script) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Fiche script");
        lines.add("Nom : " + script.getName());
        lines.add("Langage : " + script.getLanguage().getDisplayName());
        lines.add("Catégorie : " + script.getCategory());
        lines.add("Auteur : " + script.getAuthor());
        lines.add("Favori : " + (script.isFavorite() ? "Oui" : "Non"));
        lines.add("Créé le : " + DateFormatter.format(script.getCreatedAt()));
        lines.add("Mis à jour le : " + DateFormatter.format(script.getUpdatedAt()));
        lines.add("");
        lines.add("## Description");
        lines.add(script.getDescription());
        lines.add("");
        lines.add("## Contenu");
        lines.add(script.getContent());
        return PdfReportWriter.write(file, "Export script", script.getName(), lines);
    }

    public Path exportLibraryPdf(Path file, List<Script> scripts) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Bibliothèque ScriptPro");
        lines.add("Nombre de scripts : " + scripts.size());
        lines.add("");
        for (Script script : scripts) {
            lines.add("## " + script.getName());
            lines.add("Langage : " + script.getLanguage().getDisplayName() +
                    " · Catégorie : " + script.getCategory() +
                    " · Favori : " + (script.isFavorite() ? "Oui" : "Non"));
            lines.add("Description : " + script.getDescription());
            lines.add("Mis à jour : " + DateFormatter.format(script.getUpdatedAt()));
            lines.add("");
        }
        return PdfReportWriter.write(file, "Export bibliothèque", "Synthèse des scripts actifs", lines);
    }

    public Path exportHistoryPdf(Path file, List<ExecutionRecord> records) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Historique d'exécution");
        lines.add("Nombre d'exécutions : " + records.size());
        lines.add("");
        for (ExecutionRecord record : records) {
            lines.add("## " + record.getScriptName());
            lines.add("Résultat : " + record.getStatus().getDisplayName() +
                    " · Langage : " + record.getLanguage().getDisplayName() +
                    " · Code retour : " + record.getExitCode());
            lines.add("Date : " + DateFormatter.format(record.getStartedAt()) +
                    " · Durée : " + DateFormatter.duration(record.getDurationMillis()) +
                    " · Utilisateur : " + record.getUserName());
            if (record.getStdout() != null && !record.getStdout().isBlank()) {
                lines.add("Sortie : " + record.getStdout());
            }
            if (record.getStderr() != null && !record.getStderr().isBlank()) {
                lines.add("Erreurs : " + record.getStderr());
            }
            lines.add("");
        }
        return PdfReportWriter.write(file, "Export historique", "Journal des exécutions ScriptPro", lines);
    }

    private String escape(String value) {
        return (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
