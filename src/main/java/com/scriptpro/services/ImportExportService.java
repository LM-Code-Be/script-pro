package com.scriptpro.services;

import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.models.Script;
import com.scriptpro.models.ScriptLanguage;
import com.scriptpro.utils.ConsoleCharsets;
import com.scriptpro.utils.DateFormatter;
import com.scriptpro.utils.PdfReportWriter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Import de fichiers scripts et exports (fichier source, JSON, PDF).
 *
 * @author LM-Code
 */
public final class ImportExportService {
    private final ScriptService scriptService;

    public ImportExportService(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    public Script importScript(Path file) throws IOException {
        String content = readScriptFile(file);
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
            builder.append("    \"tags\": \"").append(escape(script.getTagsText())).append("\",\n");
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
        lines.add("Tags : " + (script.getTags().isEmpty() ? "-" : script.getTagsText()));
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

    /**
     * Lit un fichier script en devinant son encodage : beaucoup d'anciens scripts Windows sont
     * en ANSI (Windows-1252) et non en UTF-8. Les lire en UTF-8 strict échouerait
     * (MalformedInputException) ; on retombe donc sur l'encodage natif du poste.
     * Le BOM éventuel est retiré pour ne pas apparaître dans l'éditeur.
     */
    static String readScriptFile(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        String text = ConsoleCharsets.decode(bytes, bytes.length, nativeCharset());
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static Charset nativeCharset() {
        try {
            return Charset.forName(System.getProperty("native.encoding", "windows-1252"));
        } catch (RuntimeException exception) {
            return Charset.defaultCharset();
        }
    }

    private String escape(String value) {
        return (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
