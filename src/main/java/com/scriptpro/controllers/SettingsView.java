package com.scriptpro.controllers;

import com.scriptpro.models.ScriptLanguage;
import com.scriptpro.services.InterpreterLocator;
import com.scriptpro.services.SecurityService;
import com.scriptpro.ui.UiFactory;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.controlsfx.control.ToggleSwitch;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.scriptpro.ui.UiFactory.label;

/**
 * Paramètres : préférences d'affichage et d'exécution, motifs de sécurité,
 * interpréteurs détectés, sauvegarde et restauration de la base.
 *
 * @author LM-Code
 */
final class SettingsView {
    private final ViewContext context;

    SettingsView(ViewContext context) {
        this.context = context;
    }

    void show() {
        VBox page = new VBox(16);
        page.getChildren().addAll(preferencesCard(), securityCard(), interpretersCard(), backupCard());
        context.setView("settings", "Paramètres", "Préférences persistées dans SQLite", page);
    }

    // ------------------------------------------------------------------ Préférences générales

    private VBox preferencesCard() {
        ToggleSwitch theme = new ToggleSwitch("Mode sombre complet");
        theme.setSelected(context.settingsService.isDarkTheme());
        theme.selectedProperty().addListener((observable, oldValue, dark) -> {
            context.settingsService.setDarkTheme(dark);
            context.window().setDarkMode(dark);
        });

        TextField exportPath = new TextField(context.settingsService.get("exportPath", Path.of("exports").toAbsolutePath().toString()));
        TextField workingDirectory = new TextField(context.settingsService.workingDirectory().toString());
        Spinner<Integer> timeout = new Spinner<>();
        timeout.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 3600, context.settingsService.executionTimeoutSeconds()));
        timeout.setEditable(true);
        Spinner<Integer> fontSize = new Spinner<>();
        fontSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 28, context.settingsService.editorFontSize()));
        CheckBox notifications = new CheckBox("Notifications");
        notifications.setSelected(context.settingsService.notificationsEnabled());
        ComboBox<String> terminal = new ComboBox<>(FXCollections.observableArrayList("PowerShell", "CMD", "Bash", "Python"));
        terminal.setValue(context.settingsService.get("preferredTerminal", "PowerShell"));

        Button save = UiFactory.button("Sauvegarder les paramètres", "fas-save", "primary-button");
        save.setOnAction(event -> {
            Path workingPath;
            try {
                workingPath = Path.of(workingDirectory.getText().trim());
            } catch (RuntimeException invalidPath) {
                workingPath = null;
            }
            if (workingPath == null || !Files.isDirectory(workingPath)) {
                context.showError("Dossier de travail invalide", "Le dossier « " + workingDirectory.getText() + " » n'existe pas.");
                return;
            }
            context.settingsService.set("exportPath", exportPath.getText());
            context.settingsService.set("workingDirectory", workingPath.toString());
            context.settingsService.set("executionTimeout", String.valueOf(timeout.getValue()));
            context.settingsService.set("editorFontSize", String.valueOf(fontSize.getValue()));
            context.settingsService.set("notifications", String.valueOf(notifications.isSelected()));
            context.settingsService.set("preferredTerminal", terminal.getValue());
            context.notifyInfo("Paramètres sauvegardés");
        });

        GridPane grid = formGrid();
        addRow(grid, 0, "Thème", theme);
        addRow(grid, 1, "Chemin export", withBrowse(exportPath, "Chemin d'export"));
        addRow(grid, 2, "Dossier de travail des scripts", withBrowse(workingDirectory, "Dossier de travail"));
        addRow(grid, 3, "Timeout exécution (s)", timeout);
        addRow(grid, 4, "Taille police éditeur", fontSize);
        addRow(grid, 5, "Notifications", notifications);
        addRow(grid, 6, "Terminal préféré", terminal);

        VBox card = UiFactory.card();
        card.getChildren().addAll(UiFactory.title("Paramètres applicatifs"), grid, save);
        return card;
    }

    /** Champ de chemin accompagné d'un bouton « Parcourir » ouvrant un sélecteur de dossier. */
    private HBox withBrowse(TextField field, String title) {
        Button browse = UiFactory.button("Parcourir", "fas-folder-open", "ghost-button");
        browse.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle(title);
            File current = new File(field.getText());
            if (current.isDirectory()) {
                chooser.setInitialDirectory(current);
            }
            File directory = chooser.showDialog(context.owner());
            if (directory != null) {
                field.setText(directory.toPath().toString());
            }
        });
        field.setPrefColumnCount(40);
        HBox box = new HBox(10, field, browse);
        HBox.setHgrow(field, Priority.ALWAYS);
        return box;
    }

    // ------------------------------------------------------------------ Sécurité

    private VBox securityCard() {
        TextArea patterns = new TextArea(String.join("\n", context.settingsService.customSecurityPatterns()));
        patterns.setPromptText("Une expression régulière par ligne, par exemple :\n\\bStop-Service\\b\n\\bInvoke-WebRequest\\b");
        patterns.setPrefRowCount(5);
        Button save = UiFactory.button("Enregistrer les motifs", "fas-shield-alt", "primary-button");
        save.setOnAction(event -> {
            List<String> lines = patterns.getText().lines().map(String::trim).filter(line -> !line.isEmpty()).toList();
            List<String> errors = new ArrayList<>();
            for (String line : lines) {
                SecurityService.validatePattern(line).ifPresent(error -> errors.add("« " + line + " » : " + error));
            }
            if (!errors.isEmpty()) {
                context.showError("Motif invalide", String.join("\n", errors));
                return;
            }
            context.settingsService.setCustomSecurityPatterns(lines);
            context.notifyInfo("Motifs de sécurité enregistrés");
        });

        VBox card = UiFactory.card();
        card.getChildren().addAll(
                UiFactory.title("Contrôle de sécurité"),
                UiFactory.wrappedLabel("Les règles intégrées signalent déjà les suppressions récursives, le formatage, l'arrêt de la machine, "
                        + "le registre, les comptes, l'antivirus, le pare-feu, etc. Ajoutez ici vos propres motifs (insensibles à la casse) : "
                        + "une confirmation sera demandée si l'un d'eux apparaît dans un script."),
                patterns,
                save);
        return card;
    }

    // ------------------------------------------------------------------ Interpréteurs

    private VBox interpretersCard() {
        GridPane grid = formGrid();
        Runnable fill = () -> {
            grid.getChildren().clear();
            int row = 0;
            // CMD et Batch utilisent le même interpréteur : une seule ligne suffit.
            for (ScriptLanguage language : List.of(ScriptLanguage.POWERSHELL, ScriptLanguage.CMD,
                    ScriptLanguage.BASH, ScriptLanguage.PYTHON)) {
                Optional<InterpreterLocator.Interpreter> interpreter = context.interpreterLocator.find(language);
                String name = language == ScriptLanguage.CMD ? "CMD / Batch" : language.getDisplayName();
                String status = interpreter
                        .map(found -> found.executable() + (found.wsl() ? "  (WSL)" : ""))
                        .orElse("Introuvable — " + context.interpreterLocator.missingMessage(language));
                grid.add(label(name, "muted-label"), 0, row);
                grid.add(label(status, interpreter.isPresent() ? "status-success" : "status-failure"), 1, row);
                row++;
            }
        };
        fill.run();
        Button refresh = UiFactory.button("Actualiser la détection", "fas-sync", "ghost-button");
        refresh.setOnAction(event -> {
            context.interpreterLocator.refresh();
            fill.run();
            context.notifyInfo("Détection des interpréteurs mise à jour");
        });

        VBox card = UiFactory.card();
        card.getChildren().addAll(UiFactory.title("Interpréteurs détectés"), grid, refresh);
        return card;
    }

    // ------------------------------------------------------------------ Sauvegarde

    private VBox backupCard() {
        Button backup = UiFactory.button("Sauvegarder la base…", "fas-download", "primary-button");
        Button restore = UiFactory.button("Restaurer une sauvegarde…", "fas-upload", "danger-button");
        backup.setOnAction(event -> backupDatabase());
        restore.setOnAction(event -> restoreDatabase());

        HBox actions = new HBox(10, backup, restore);
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox card = UiFactory.card();
        card.getChildren().addAll(
                UiFactory.title("Sauvegarde et restauration"),
                UiFactory.wrappedLabel("La sauvegarde contient tous les scripts, l'historique, les variables et les paramètres. "
                        + "La restauration remplace les données actuelles ; une copie de sécurité de la base est faite automatiquement avant."),
                actions);
        return card;
    }

    private void backupDatabase() {
        FileChooser chooser = databaseChooser("Sauvegarder la base ScriptPro");
        chooser.setInitialFileName(context.backupService.suggestedFileName());
        File file = chooser.showSaveDialog(context.owner());
        if (file == null) {
            return;
        }
        try {
            Path target = context.backupService.backup(file.toPath());
            context.notifyInfo("Sauvegarde créée : " + target.getFileName());
            context.window().setFooterStatus("Dernière sauvegarde : " + target);
        } catch (IOException exception) {
            context.showError("Sauvegarde impossible", exception.getMessage());
        }
    }

    private void restoreDatabase() {
        if (context.navigator().isExecutionRunning()) {
            context.showError("Restauration impossible", "Un script est en cours d'exécution. Attendez sa fin ou arrêtez-le.");
            return;
        }
        File file = databaseChooser("Restaurer une sauvegarde ScriptPro").showOpenDialog(context.owner());
        if (file == null) {
            return;
        }
        if (!context.confirm("Restaurer la sauvegarde",
                "Toutes les données actuelles seront remplacées par celles de « " + file.getName() + " ».\n"
                        + "Une copie de sécurité de la base actuelle sera créée avant. Continuer ?")) {
            return;
        }
        try {
            Path safetyCopy = context.backupService.restore(file.toPath());
            context.refreshData();
            context.window().setDarkMode(context.settingsService.isDarkTheme());
            context.notifyInfo("Sauvegarde restaurée");
            context.window().setFooterStatus("Base restaurée · ancienne base conservée dans " + safetyCopy);
            show();
        } catch (IOException exception) {
            context.showError("Restauration impossible", exception.getMessage());
        }
    }

    private static FileChooser databaseChooser(String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Base ScriptPro (*.db)", "*.db"));
        return chooser;
    }

    private static GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        return grid;
    }

    private static void addRow(GridPane grid, int row, String title, javafx.scene.Node control) {
        grid.add(label(title, "muted-label"), 0, row);
        grid.add(control, 1, row);
        GridPane.setHgrow(control, Priority.ALWAYS);
    }
}
