package com.scriptpro.controllers;

import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.models.ExecutionStatus;
import com.scriptpro.models.Script;
import com.scriptpro.models.ScriptLanguage;
import com.scriptpro.models.SecurityWarning;
import com.scriptpro.models.VariableEntry;
import com.scriptpro.services.ExecutionService;
import com.scriptpro.ui.UiFactory;
import com.scriptpro.utils.DateFormatter;
import com.scriptpro.utils.SyntaxHighlighter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.scriptpro.ui.UiFactory.label;

/**
 * Éditeur de scripts : édition avec coloration syntaxique, sauvegarde, et exécution
 * avec console en direct.
 *
 * <p>Cet objet vit pendant toute la session : l'interface est reconstruite à chaque
 * affichage, mais l'exécution en cours est conservée. On peut donc quitter l'éditeur
 * pendant qu'un script tourne, puis revenir pour voir la suite ou l'arrêter.</p>
 *
 * <p>Raccourcis : Ctrl+S sauvegarder, F5 exécuter, Maj+F5 arrêter, Ctrl+F rechercher.</p>
 *
 * @author LM-Code
 */
final class EditorView {
    private static final KeyCombination SAVE = new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination FIND = new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN);
    private static final KeyCombination RUN = new KeyCodeCombination(KeyCode.F5);
    private static final KeyCombination STOP = new KeyCodeCombination(KeyCode.F5, KeyCombination.SHIFT_DOWN);
    private static final String READY = "Console prête.";

    private final ViewContext context;
    private final FileActions fileActions;
    private final LiveConsole liveConsole = new LiveConsole();

    /** Script affiché dans l'éditeur (peut être un nouveau script non encore sauvegardé). */
    private Script selectedScript;
    /** Exécution en cours, ou {@code null} si aucun script ne tourne. */
    private ExecutionService.ExecutionHandle runningExecution;

    // Composants de l'interface, recréés à chaque affichage de l'écran.
    private CodeArea editorArea;
    private TextField editorName;
    private ComboBox<ScriptLanguage> editorLanguage;
    private ComboBox<String> editorCategory;
    private TextField editorTags;
    private TextArea editorDescription;
    private TextField editorAuthor;
    private CheckBox editorFavorite;
    private TextField findField;
    private TextArea executionConsole;
    private Button runButton;
    private Button stopButton;

    EditorView(ViewContext context, FileActions fileActions) {
        this.context = context;
        this.fileActions = fileActions;
    }

    /** Affiche l'éditeur sur le dernier script ouvert (ou le plus récent de la bibliothèque). */
    void show() {
        context.refreshData();
        // Le dernier script ouvert a pu être supprimé ou archivé depuis la bibliothèque.
        boolean stillActive = selectedScript != null && (selectedScript.getId() == null
                || context.scripts.stream().anyMatch(script -> script.getId().equals(selectedScript.getId())));
        if (!stillActive) {
            selectedScript = context.scripts.stream().findFirst().orElseGet(Script::new);
        }
        build();
    }

    void open(Script script) {
        selectedScript = script;
        context.refreshData();
        build();
    }

    void openAndRun(Script script) {
        open(script);
        runCurrentScript();
    }

    boolean isRunning() {
        return runningExecution != null;
    }

    void stopExecution() {
        if (runningExecution != null) {
            runningExecution.cancel();
        }
    }

    // ------------------------------------------------------------------ Construction de l'écran

    private void build() {
        Script initialScript = selectedScript;
        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().addAll(scriptList(initialScript), editorPanel());
        splitPane.setDividerPositions(0.24);
        splitPane.addEventFilter(KeyEvent.KEY_PRESSED, this::handleShortcut);
        context.setView("editor", "Éditeur", "Édition RichTextFX, variables dynamiques et console intégrée", splitPane);
        loadScriptInEditor(initialScript);
        updateRunControls();
        if (isRunning()) {
            executionConsole.setText("Exécution en cours… (la sortie reprend ci-dessous)\n");
        }
    }

    private VBox scriptList(Script initialScript) {
        ListView<Script> listView = new ListView<>(context.scripts);
        listView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Script script, boolean empty) {
                super.updateItem(script, empty);
                if (empty || script == null) {
                    setText(null);
                } else {
                    setText(script.getName() + "\n" + script.getLanguage().getDisplayName() + " · " + script.getCategory());
                }
            }
        });
        if (initialScript.getId() != null) {
            context.scripts.stream()
                    .filter(script -> script.getId().equals(initialScript.getId()))
                    .findFirst()
                    .ifPresent(listView.getSelectionModel()::select);
        }
        listView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, script) -> {
            if (script != null) {
                loadScriptInEditor(script);
            }
        });

        Button newScript = UiFactory.button("Nouveau script", "fas-plus", "primary-button");
        newScript.setMaxWidth(Double.MAX_VALUE);
        newScript.setOnAction(event -> loadScriptInEditor(new Script()));

        VBox box = UiFactory.card();
        box.getChildren().addAll(UiFactory.title("Scripts"), newScript, listView);
        VBox.setVgrow(listView, Priority.ALWAYS);
        return box;
    }

    private VBox editorPanel() {
        editorName = new TextField();
        editorName.setPromptText("Nom du script");
        editorLanguage = new ComboBox<>(FXCollections.observableArrayList(ScriptLanguage.values()));
        editorCategory = new ComboBox<>(FXCollections.observableArrayList(context.scriptService.categories()));
        editorCategory.setEditable(true);
        editorAuthor = new TextField();
        editorFavorite = new CheckBox("Favori");
        editorTags = new TextField();
        editorTags.setPromptText("Tags séparés par des virgules (ex. réseau, urgence)");
        editorDescription = new TextArea();
        editorDescription.setPromptText("Description opérationnelle");
        editorDescription.setPrefRowCount(2);

        editorArea = new CodeArea();
        editorArea.getStyleClass().add("code-area");
        editorArea.setParagraphGraphicFactory(LineNumberFactory.get(editorArea));
        editorArea.setStyle("-fx-font-size: " + context.settingsService.editorFontSize() + "px;");
        editorArea.multiPlainChanges()
                .successionEnds(java.time.Duration.ofMillis(160))
                .subscribe(ignore -> highlightEditor());
        editorLanguage.valueProperty().addListener((observable, oldValue, newValue) -> highlightEditor());

        findField = new TextField();
        findField.setPromptText("Rechercher (Ctrl+F)");
        findField.setOnAction(event -> findNext(findField.getText()));
        TextField replaceField = new TextField();
        replaceField.setPromptText("Remplacer");
        Button findButton = UiFactory.button("Suivant", "fas-search", "ghost-button");
        Button replaceButton = UiFactory.button("Remplacer", "fas-exchange-alt", "ghost-button");
        findButton.setOnAction(event -> findNext(findField.getText()));
        replaceButton.setOnAction(event -> replaceNext(findField.getText(), replaceField.getText()));

        runButton = UiFactory.button("Exécuter", "fas-play", "success-button", "Exécuter le script (F5)");
        stopButton = UiFactory.button("Arrêter", "fas-stop", "danger-button", "Arrêter l'exécution en cours (Maj+F5)");
        Button save = UiFactory.button("Sauvegarder", "fas-save", "primary-button", "Sauvegarder (Ctrl+S)");
        Button duplicate = UiFactory.button("Dupliquer", "fas-copy", "ghost-button");
        Button export = UiFactory.button("Exporter source", "fas-file-export", "ghost-button");
        Button exportPdf = UiFactory.button("Exporter PDF", "fas-file-pdf", "ghost-button");
        Button favorite = UiFactory.button("Favori", "fas-star", "ghost-button");
        runButton.setOnAction(event -> runCurrentScript());
        stopButton.setOnAction(event -> requestStop());
        save.setOnAction(event -> saveEditorScript(true));
        duplicate.setOnAction(event -> saveEditorScript(false).ifPresent(script -> {
            Script duplicateScript = context.scriptService.duplicate(script);
            context.notifyInfo("Script dupliqué");
            open(duplicateScript);
        }));
        export.setOnAction(event -> saveEditorScript(false).ifPresent(fileActions::exportScript));
        exportPdf.setOnAction(event -> saveEditorScript(false).ifPresent(fileActions::exportScriptPdf));
        favorite.setOnAction(event -> {
            editorFavorite.setSelected(!editorFavorite.isSelected());
            saveEditorScript(true);
        });

        HBox identity = new HBox(12, editorName, editorLanguage, editorCategory, editorAuthor, editorFavorite);
        HBox.setHgrow(editorName, Priority.ALWAYS);
        HBox.setHgrow(editorCategory, Priority.ALWAYS);
        HBox.setHgrow(editorAuthor, Priority.ALWAYS);
        identity.setAlignment(Pos.CENTER_LEFT);

        HBox tools = new HBox(10, runButton, stopButton, save, duplicate, export, exportPdf, favorite);
        tools.setAlignment(Pos.CENTER_LEFT);
        HBox search = new HBox(10, findField, replaceField, findButton, replaceButton);
        HBox.setHgrow(findField, Priority.ALWAYS);
        HBox.setHgrow(replaceField, Priority.ALWAYS);

        executionConsole = new TextArea();
        executionConsole.getStyleClass().add("console");
        executionConsole.setEditable(false);
        executionConsole.setPrefRowCount(9);
        executionConsole.setText(READY);

        VirtualizedScrollPane<CodeArea> codeScroll = new VirtualizedScrollPane<>(editorArea);
        VBox panel = new VBox(14,
                tools,
                identity,
                editorTags,
                editorDescription,
                search,
                codeScroll,
                label("Console intégrée", "section-title"),
                executionConsole);
        panel.getStyleClass().add("editor-panel");
        VBox.setVgrow(codeScroll, Priority.ALWAYS);
        panel.setPadding(new Insets(16));
        return panel;
    }

    private void handleShortcut(KeyEvent event) {
        if (SAVE.match(event)) {
            saveEditorScript(true);
        } else if (STOP.match(event)) {
            requestStop();
        } else if (RUN.match(event)) {
            runCurrentScript();
        } else if (FIND.match(event)) {
            findField.requestFocus();
            findField.selectAll();
        } else {
            return;
        }
        event.consume();
    }

    // ------------------------------------------------------------------ Chargement et sauvegarde

    private void loadScriptInEditor(Script script) {
        selectedScript = script;
        if (editorName == null) {
            return;
        }
        editorName.setText(value(script.getName()));
        editorLanguage.setValue(script.getLanguage());
        if (!editorCategory.getItems().contains(script.getCategory())) {
            editorCategory.getItems().add(script.getCategory());
        }
        editorCategory.setValue(script.getCategory());
        editorAuthor.setText(value(script.getAuthor()));
        editorFavorite.setSelected(script.isFavorite());
        editorTags.setText(script.getTagsText());
        editorDescription.setText(value(script.getDescription()));
        editorArea.replaceText(value(script.getContent()));
        // Pendant une exécution, la console montre la sortie en cours : on ne l'efface pas.
        if (!isRunning()) {
            executionConsole.setText(READY);
        }
        highlightEditor();
    }

    private Optional<Script> saveEditorScript(boolean notify) {
        try {
            if (selectedScript == null) {
                selectedScript = new Script();
            }
            selectedScript.setName(editorName.getText());
            selectedScript.setLanguage(editorLanguage.getValue());
            selectedScript.setCategory(comboText(editorCategory));
            selectedScript.setTagsText(editorTags.getText());
            selectedScript.setDescription(editorDescription.getText());
            selectedScript.setAuthor(editorAuthor.getText());
            selectedScript.setFavorite(editorFavorite.isSelected());
            selectedScript.setContent(editorArea.getText());
            Script saved = context.scriptService.save(selectedScript);
            selectedScript = saved;
            editorTags.setText(saved.getTagsText());
            context.refreshData();
            if (notify) {
                context.notifyInfo("Script sauvegardé");
            }
            return Optional.of(saved);
        } catch (RuntimeException exception) {
            context.showError("Sauvegarde impossible", exception.getMessage());
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ Exécution

    private void runCurrentScript() {
        if (isRunning()) {
            context.notifyWarning("Une exécution est déjà en cours. Arrêtez-la ou attendez sa fin.");
            return;
        }
        Optional<Script> savedScript = saveEditorScript(false);
        if (savedScript.isEmpty()) {
            return;
        }
        Script script = savedScript.get();

        // Vérification de l'interpréteur avant tout : message clair plutôt qu'une erreur système.
        if (context.interpreterLocator.find(script.getLanguage()).isEmpty()) {
            context.showError("Interpréteur introuvable", context.interpreterLocator.missingMessage(script.getLanguage()));
            return;
        }
        List<SecurityWarning> warnings = context.securityService.detectWarnings(script.getContent());
        if (!warnings.isEmpty() && !context.confirm("Confirmation de sécurité", formatWarnings(warnings))) {
            return;
        }
        Map<String, String> variables = promptVariables(script.getContent());
        if (variables == null) {
            return;
        }

        liveConsole.reset();
        executionConsole.setText("▶ Exécution de « " + script.getName() + " » (" + script.getLanguage().getDisplayName()
                + ") dans " + context.settingsService.workingDirectory() + "\n\n");
        runningExecution = context.executionService.executeAsync(script, variables,
                (text, error) -> liveConsole.append(error ? markErrorLines(text) : text));
        updateRunControls();

        Task<ExecutionRecord> task = runningExecution.task();
        task.setOnSucceeded(event -> {
            ExecutionRecord record = task.getValue();
            finishExecution();
            appendToConsole("\n────────────────────────────\n"
                    + record.getStatus().getDisplayName()
                    + " · Code retour : " + record.getExitCode()
                    + " · Durée : " + DateFormatter.duration(record.getDurationMillis()) + "\n");
            if (record.getStatus() == ExecutionStatus.SUCCESS) {
                context.notifyInfo("Script exécuté avec succès");
            } else if (record.getStatus() == ExecutionStatus.CANCELLED) {
                context.notifyWarning("Exécution arrêtée");
            } else {
                context.notifyWarning("Exécution terminée avec erreur");
            }
        });
        task.setOnFailed(event -> {
            finishExecution();
            appendToConsole("\nErreur interne : " + task.getException().getMessage() + "\n");
            context.notifyWarning("Erreur d'exécution");
        });
    }

    private void requestStop() {
        if (isRunning()) {
            runningExecution.cancel();
            appendToConsole("\n■ Arrêt demandé…\n");
        }
    }

    private void finishExecution() {
        liveConsole.flush();
        runningExecution = null;
        updateRunControls();
        context.refreshData();
    }

    private void updateRunControls() {
        if (runButton != null) {
            runButton.setDisable(isRunning());
            stopButton.setDisable(!isRunning());
        }
    }

    private void appendToConsole(String text) {
        if (executionConsole != null) {
            executionConsole.appendText(text);
        }
    }

    /** Préfixe les lignes d'erreur pour les distinguer de la sortie normale dans la console. */
    private static String markErrorLines(String text) {
        return text.lines().map(line -> "[ERR] " + line).collect(Collectors.joining("\n", "", text.endsWith("\n") ? "\n" : ""));
    }

    private static String formatWarnings(List<SecurityWarning> warnings) {
        StringBuilder builder = new StringBuilder("Commandes sensibles détectées :\n\n");
        warnings.stream().limit(10).forEach(warning -> builder
                .append("• Ligne ").append(warning.lineNumber()).append(" — ").append(warning.rule()).append("\n")
                .append("    ").append(warning.excerpt()).append("\n"));
        if (warnings.size() > 10) {
            builder.append("… et ").append(warnings.size() - 10).append(" autre(s).\n");
        }
        return builder.append("\nConfirmer l'exécution ?").toString();
    }

    /**
     * Demande la valeur de chaque variable {@code {{NOM}}} du script.
     * Les variables sensibles sont saisies dans un champ masqué.
     *
     * @return les valeurs saisies, ou {@code null} si l'utilisateur annule
     */
    private Map<String, String> promptVariables(String content) {
        Set<String> variableNames = context.variableService.extractVariables(content);
        if (variableNames.isEmpty()) {
            return Map.of();
        }
        Map<String, VariableEntry> knownVariables = context.variableService.findAllByName();
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Variables d'exécution");
        dialog.setHeaderText("Renseigner les valeurs avant exécution");
        ButtonType confirmButton = new ButtonType("Exécuter", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirmButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));
        Map<String, TextField> fields = new HashMap<>();
        int row = 0;
        for (String variableName : variableNames) {
            VariableEntry variable = knownVariables.get(variableName);
            boolean secret = variable != null && variable.isSecret();
            TextField field = secret ? new PasswordField() : new TextField();
            field.setText(variable == null ? "" : value(variable.getDefaultValue()));
            field.setPrefColumnCount(24);
            fields.put(variableName, field);
            grid.add(label("{{" + variableName + "}}" + (secret ? "  (sensible)" : ""), "muted-label"), 0, row);
            grid.add(field, 1, row);
            row++;
        }
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> {
            if (button != confirmButton) {
                return null;
            }
            Map<String, String> values = new HashMap<>();
            fields.forEach((name, field) -> values.put(name, field.getText()));
            return values;
        });
        return dialog.showAndWait().orElse(null);
    }

    // ------------------------------------------------------------------ Recherche et coloration

    private void highlightEditor() {
        if (editorArea == null || editorLanguage == null || editorLanguage.getValue() == null) {
            return;
        }
        editorArea.setStyleSpans(0, SyntaxHighlighter.compute(editorArea.getText(), editorLanguage.getValue()));
    }

    /** Sélectionne l'occurrence suivante après le curseur, en reprenant au début si besoin. */
    private void findNext(String query) {
        if (query == null || query.isEmpty()) {
            return;
        }
        String text = editorArea.getText().toLowerCase(Locale.ROOT);
        String needle = query.toLowerCase(Locale.ROOT);
        int index = text.indexOf(needle, editorArea.getCaretPosition());
        if (index < 0) {
            index = text.indexOf(needle);
        }
        if (index >= 0) {
            editorArea.selectRange(index, index + query.length());
            editorArea.requestFollowCaret();
        } else {
            context.notifyWarning("« " + query + " » introuvable");
        }
    }

    /** Remplace la sélection si elle correspond à la recherche, puis passe à l'occurrence suivante. */
    private void replaceNext(String query, String replacement) {
        if (query == null || query.isEmpty()) {
            return;
        }
        if (editorArea.getSelectedText().equalsIgnoreCase(query)) {
            editorArea.replaceSelection(replacement == null ? "" : replacement);
        }
        findNext(query);
    }

    private static String comboText(ComboBox<String> comboBox) {
        String editorText = comboBox.getEditor() == null ? "" : comboBox.getEditor().getText();
        if (editorText != null && !editorText.isBlank()) {
            return editorText.trim();
        }
        return comboBox.getValue() == null ? "Général" : comboBox.getValue();
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }

    /**
     * Regroupe la sortie reçue des threads de lecture et l'affiche par paquets sur le thread
     * JavaFX. Sans ce regroupement, un script très bavard saturerait l'interface d'appels
     * {@code Platform.runLater}.
     */
    private final class LiveConsole {
        private final StringBuilder pending = new StringBuilder();
        private boolean flushScheduled;

        void append(String text) {
            synchronized (pending) {
                pending.append(text);
                if (flushScheduled) {
                    return;
                }
                flushScheduled = true;
            }
            Platform.runLater(this::flush);
        }

        void flush() {
            String text;
            synchronized (pending) {
                text = pending.toString();
                pending.setLength(0);
                flushScheduled = false;
            }
            if (!text.isEmpty()) {
                appendToConsole(text);
            }
        }

        void reset() {
            synchronized (pending) {
                pending.setLength(0);
            }
        }
    }
}
