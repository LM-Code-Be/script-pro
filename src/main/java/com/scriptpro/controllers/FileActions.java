package com.scriptpro.controllers;

import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.models.Script;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Actions d'import/export communes à plusieurs écrans (bibliothèque, éditeur, historique) :
 * choix du fichier, appel du service et retour visuel à l'utilisateur.
 *
 * @author LM-Code
 */
final class FileActions {
    private final ViewContext context;

    FileActions(ViewContext context) {
        this.context = context;
    }

    /** Importe un fichier script choisi par l'utilisateur ; renvoie le script créé. */
    Optional<Script> importScript() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importer un script");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Scripts", "*.ps1", "*.bat", "*.cmd", "*.sh", "*.py"));
        File file = chooser.showOpenDialog(context.owner());
        if (file == null) {
            return Optional.empty();
        }
        try {
            Script imported = context.importExportService.importScript(file.toPath());
            context.refreshData();
            context.notifyInfo("Import terminé");
            return Optional.of(imported);
        } catch (IOException | RuntimeException exception) {
            context.showError("Import impossible", exception.getMessage());
            return Optional.empty();
        }
    }

    void exportScript(Script script) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Exporter le script");
        initialDirectory().ifPresent(chooser::setInitialDirectory);
        File directory = chooser.showDialog(context.owner());
        if (directory == null) {
            return;
        }
        try {
            Path target = context.importExportService.exportScript(directory.toPath(), script);
            context.notifyInfo("Export terminé : " + target.getFileName());
        } catch (IOException exception) {
            context.showError("Export impossible", exception.getMessage());
        }
    }

    void exportScriptPdf(Script script) {
        File file = choosePdfFile("Exporter le script en PDF", safeFileName(script.getName()) + ".pdf");
        if (file == null) {
            return;
        }
        try {
            Path target = context.importExportService.exportScriptPdf(file.toPath(), script);
            pdfDone("Export PDF terminé : ", target);
        } catch (IOException exception) {
            context.showError("Export PDF impossible", exception.getMessage());
        }
    }

    void exportLibraryPdf(List<Script> scripts) {
        if (scripts.isEmpty()) {
            context.notifyWarning("Aucun script à exporter");
            return;
        }
        File file = choosePdfFile("Exporter la bibliothèque en PDF", "bibliotheque-scriptpro.pdf");
        if (file == null) {
            return;
        }
        try {
            Path target = context.importExportService.exportLibraryPdf(file.toPath(), scripts);
            pdfDone("Bibliothèque PDF exportée : ", target);
        } catch (IOException exception) {
            context.showError("Export PDF impossible", exception.getMessage());
        }
    }

    void exportHistoryPdf(List<ExecutionRecord> records) {
        if (records.isEmpty()) {
            context.notifyWarning("Aucun historique à exporter");
            return;
        }
        File file = choosePdfFile("Exporter l'historique en PDF", "historique-scriptpro.pdf");
        if (file == null) {
            return;
        }
        try {
            Path target = context.importExportService.exportHistoryPdf(file.toPath(), records);
            pdfDone("Historique PDF exporté : ", target);
        } catch (IOException exception) {
            context.showError("Export PDF impossible", exception.getMessage());
        }
    }

    private void pdfDone(String message, Path target) {
        context.notifyInfo(message + target.getFileName());
        context.window().setFooterStatus("Dernier export PDF : " + target.getFileName());
    }

    private File choosePdfFile(String title, String initialFileName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(initialFileName);
        initialDirectory().ifPresent(chooser::setInitialDirectory);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Document PDF", "*.pdf"));
        return chooser.showSaveDialog(context.owner());
    }

    /** Dossier d'export configuré dans les paramètres, s'il existe. */
    private Optional<File> initialDirectory() {
        try {
            Path exportPath = Path.of(context.settingsService.get("exportPath", ""));
            return Files.isDirectory(exportPath) ? Optional.of(exportPath.toFile()) : Optional.empty();
        } catch (RuntimeException invalidPath) {
            return Optional.empty();
        }
    }

    private static String safeFileName(String value) {
        String fileName = value == null || value.isBlank() ? "scriptpro" : value;
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }
}
