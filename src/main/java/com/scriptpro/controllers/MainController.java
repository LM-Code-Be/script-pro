package com.scriptpro.controllers;

import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.models.ExecutionStatus;
import com.scriptpro.models.Script;
import com.scriptpro.models.ScriptLanguage;
import com.scriptpro.models.VariableEntry;
import com.scriptpro.repositories.ExecutionHistoryRepository;
import com.scriptpro.services.ExecutionService;
import com.scriptpro.services.ImportExportService;
import com.scriptpro.services.ScriptService;
import com.scriptpro.services.SecurityService;
import com.scriptpro.services.SettingsService;
import com.scriptpro.services.TemplateService;
import com.scriptpro.services.VariableService;
import com.scriptpro.ui.MainWindow;
import com.scriptpro.ui.UiFactory;
import com.scriptpro.utils.DateFormatter;
import com.scriptpro.utils.SyntaxHighlighter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import org.controlsfx.control.Notifications;
import org.controlsfx.control.ToggleSwitch;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

public final class MainController {
    private final ScriptService scriptService;
    private final ExecutionService executionService;
    private final TemplateService templateService;
    private final VariableService variableService;
    private final SettingsService settingsService;
    private final SecurityService securityService;
    private final ImportExportService importExportService;
    private final ExecutionHistoryRepository historyRepository;
    private final ObservableList<Script> scripts = FXCollections.observableArrayList();
    private final ObservableList<ExecutionRecord> history = FXCollections.observableArrayList();

    private MainWindow window;
    private Script pendingEditorScript;
    private Script selectedScript;
    private CodeArea editorArea;
    private TextField editorName;
    private ComboBox<ScriptLanguage> editorLanguage;
    private ComboBox<String> editorCategory;
    private TextArea editorDescription;
    private TextField editorAuthor;
    private CheckBox editorFavorite;
    private TextArea executionConsole;

    public MainController(ScriptService scriptService,
                          ExecutionService executionService,
                          TemplateService templateService,
                          VariableService variableService,
                          SettingsService settingsService,
                          SecurityService securityService,
                          ImportExportService importExportService,
                          ExecutionHistoryRepository historyRepository) {
        this.scriptService = scriptService;
        this.executionService = executionService;
        this.templateService = templateService;
        this.variableService = variableService;
        this.settingsService = settingsService;
        this.securityService = securityService;
        this.importExportService = importExportService;
        this.historyRepository = historyRepository;
    }

    public Parent createView() {
        window = new MainWindow();
        window.addNavigation("dashboard", "Dashboard", "fas-tachometer-alt", this::showDashboard);
        window.addNavigation("library", "Bibliothèque", "fas-folder-open", this::showLibrary);
        window.addNavigation("editor", "Éditeur", "fas-code", this::showEditor);
        window.addNavigation("executions", "Exécutions", "fas-terminal", this::showExecutions);
        window.addNavigation("favorites", "Favoris", "fas-star", this::showFavorites);
        window.addNavigation("templates", "Modèles", "fas-layer-group", this::showTemplates);
        window.addNavigation("variables", "Variables", "fas-sliders-h", this::showVariables);
        window.addNavigation("history", "Historique", "fas-history", this::showHistory);
        window.addNavigation("documentation", "Documentation", "fas-book-open", this::showDocumentation);
        window.addNavigation("settings", "Paramètres", "fas-cog", this::showSettings);
        window.getSearchField().setOnAction(event -> showLibrary(window.getSearchField().getText()));
        window.getThemeButton().setOnAction(event -> toggleTheme());
        window.setDarkMode(settingsService.isDarkTheme());
        refreshData();
        showDashboard();
        return window.getRoot();
    }

    private void refreshData() {
        scripts.setAll(scriptService.findActiveScripts());
        history.setAll(historyRepository.findAll());
        updateQuickStats();
    }

    private void updateQuickStats() {
        long favorites = scripts.stream().filter(Script::isFavorite).count();
        window.setQuickStats(scripts.size() + " scripts · " + favorites + " favoris · " + historyRepository.countToday() + " exécutions aujourd'hui");
        window.setFooterStatus("SQLite : scriptpro.db · " + scripts.size() + " scripts · " + history.size() + " événements");
    }

    private void setView(String navigationKey, String title, String subtitle, Node content) {
        window.setActiveNavigation(navigationKey);
        window.setContent(title, subtitle, scroll(content));
        updateQuickStats();
    }

    private ScrollPane scroll(Node content) {
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.getStyleClass().add("content-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        return scrollPane;
    }

    private void showDashboard() {
        refreshData();
        VBox page = new VBox(22);
        page.getChildren().addAll(dashboardKpis(), dashboardContent());
        setView("dashboard", "Dashboard", "Vue opérationnelle des scripts, favoris et exécutions", page);
    }

    private GridPane dashboardKpis() {
        long favorites = scripts.stream().filter(Script::isFavorite).count();
        long categories = scripts.stream().map(Script::getCategory).distinct().count();
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(18);
        List<Node> cards = List.of(
                kpiCard("Scripts", String.valueOf(scripts.size()), "fas-folder-open"),
                kpiCard("Favoris", String.valueOf(favorites), "fas-star"),
                kpiCard("Exécutions aujourd'hui", String.valueOf(historyRepository.countToday()), "fas-play"),
                kpiCard("Échecs", String.valueOf(historyRepository.countFailures()), "fas-times-circle"),
                kpiCard("Catégories", String.valueOf(categories), "fas-tags"),
                kpiCard("Historique", String.valueOf(history.size()), "fas-history")
        );
        for (int index = 0; index < cards.size(); index++) {
            Node card = cards.get(index);
            GridPane.setHgrow(card, Priority.ALWAYS);
            grid.add(card, index % 3, index / 3);
        }
        return grid;
    }

    private VBox kpiCard(String title, String value, String iconLiteral) {
        VBox card = UiFactory.card();
        HBox header = new HBox(8, UiFactory.icon(iconLiteral), label(title, "kpi-title"));
        header.setAlignment(Pos.CENTER_LEFT);
        Label valueLabel = label(value, "kpi-value");
        card.getChildren().addAll(header, valueLabel);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private HBox dashboardContent() {
        VBox activity = UiFactory.card();
        activity.getChildren().add(UiFactory.title("Activité récente"));
        List<ExecutionRecord> recentRecords = historyRepository.findRecent(7);
        if (recentRecords.isEmpty()) {
            activity.getChildren().add(label("Aucune exécution enregistrée.", "muted-label"));
        } else {
            recentRecords.forEach(record -> activity.getChildren().add(executionRow(record)));
        }

        VBox recentScripts = UiFactory.card();
        recentScripts.getChildren().add(UiFactory.title("Derniers scripts utilisés"));
        scripts.stream().limit(7).forEach(script -> recentScripts.getChildren().add(scriptRow(script)));

        HBox content = new HBox(18, activity, recentScripts);
        HBox.setHgrow(activity, Priority.ALWAYS);
        HBox.setHgrow(recentScripts, Priority.ALWAYS);
        return content;
    }

    private HBox executionRow(ExecutionRecord record) {
        Label status = label(record.getStatus().getDisplayName(), statusClass(record.getStatus()));
        Label text = label(record.getScriptName() + " · " + DateFormatter.format(record.getStartedAt()), "muted-label");
        HBox row = new HBox(10, status, text);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox scriptRow(Script script) {
        Label name = label(script.getName(), "section-title");
        name.setStyle("-fx-font-size: 14px;");
        Label meta = label(script.getLanguage().getDisplayName() + " · " + script.getCategory(), "muted-label");
        Button open = UiFactory.button("Ouvrir", "fas-code", "ghost-button");
        open.setOnAction(event -> openEditor(script));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, new VBox(2, name, meta), spacer, open);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void showLibrary() {
        showLibrary(window.getSearchField().getText());
    }

    private void showLibrary(String initialQuery) {
        refreshData();
        VBox page = new VBox(16);

        TextField queryField = new TextField(initialQuery == null ? "" : initialQuery);
        queryField.setPromptText("Recherche instantanée");
        ComboBox<String> languageFilter = new ComboBox<>();
        languageFilter.getItems().add("Tous les langages");
        for (ScriptLanguage language : ScriptLanguage.values()) {
            languageFilter.getItems().add(language.getDisplayName());
        }
        languageFilter.getSelectionModel().selectFirst();

        ComboBox<String> categoryFilter = new ComboBox<>();
        categoryFilter.getItems().add("Toutes les catégories");
        categoryFilter.getItems().addAll(scriptService.categories());
        categoryFilter.getSelectionModel().selectFirst();

        CheckBox favoritesOnly = new CheckBox("Favoris");
        DatePicker modifiedAfter = new DatePicker();
        modifiedAfter.setPromptText("Modifié après");

        TableView<Script> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.getColumns().setAll(List.of(
                column("Nom", Script::getName),
                column("Langage", script -> script.getLanguage().getDisplayName()),
                column("Catégorie", Script::getCategory),
                column("Auteur", Script::getAuthor),
                column("Mis à jour", script -> DateFormatter.format(script.getUpdatedAt())),
                column("Favori", script -> script.isFavorite() ? "Oui" : "Non")
        ));
        table.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                openEditor(table.getSelectionModel().getSelectedItem());
            }
        });

        Runnable applyFilter = () -> {
            ScriptLanguage language = selectedLanguage(languageFilter.getValue());
            String category = "Toutes les catégories".equals(categoryFilter.getValue()) ? "" : categoryFilter.getValue();
            List<Script> filtered = scriptService.search(queryField.getText(), language, category, favoritesOnly.isSelected())
                    .stream()
                    .filter(script -> modifiedAfter.getValue() == null ||
                            !script.getUpdatedAt().atZone(ZoneId.systemDefault()).toLocalDate().isBefore(modifiedAfter.getValue()))
                    .toList();
            table.getItems().setAll(filtered);
        };

        queryField.textProperty().addListener((observable, oldValue, newValue) -> applyFilter.run());
        languageFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyFilter.run());
        categoryFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyFilter.run());
        favoritesOnly.selectedProperty().addListener((observable, oldValue, newValue) -> applyFilter.run());
        modifiedAfter.valueProperty().addListener((observable, oldValue, newValue) -> applyFilter.run());
        applyFilter.run();

        HBox filters = new HBox(12, queryField, languageFilter, categoryFilter, favoritesOnly, modifiedAfter);
        filters.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(queryField, Priority.ALWAYS);

        Button add = UiFactory.button("Nouveau", "fas-plus", "primary-button");
        Button edit = UiFactory.button("Modifier", "fas-code", "ghost-button");
        Button duplicate = UiFactory.button("Dupliquer", "fas-copy", "ghost-button");
        Button archive = UiFactory.button("Archiver", "fas-archive", "ghost-button");
        Button delete = UiFactory.button("Supprimer", "fas-trash", "danger-button");
        Button importButton = UiFactory.button("Importer", "fas-upload", "ghost-button");
        Button exportButton = UiFactory.button("Exporter source", "fas-download", "ghost-button");
        Button exportPdfButton = UiFactory.button("PDF script", "fas-file-pdf", "ghost-button");
        Button exportLibraryPdfButton = UiFactory.button("PDF bibliothèque", "fas-book", "ghost-button");

        add.setOnAction(event -> openEditor(new Script()));
        edit.setOnAction(event -> selected(table).ifPresent(this::openEditor));
        duplicate.setOnAction(event -> selected(table).ifPresent(script -> {
            scriptService.duplicate(script);
            refreshData();
            applyFilter.run();
            notifyInfo("Script dupliqué");
        }));
        archive.setOnAction(event -> selected(table).ifPresent(script -> {
            if (confirm("Archiver le script", "Le script reste en base mais disparaît des vues actives.")) {
                scriptService.archive(script);
                refreshData();
                applyFilter.run();
                notifyInfo("Script archivé");
            }
        }));
        delete.setOnAction(event -> selected(table).ifPresent(script -> {
            if (confirm("Supprimer le script", "La suppression est définitive.")) {
                scriptService.delete(script);
                refreshData();
                applyFilter.run();
                notifyInfo("Script supprimé");
            }
        }));
        importButton.setOnAction(event -> importScript(applyFilter));
        exportButton.setOnAction(event -> selected(table).ifPresent(this::exportScript));
        exportPdfButton.setOnAction(event -> selected(table).ifPresent(this::exportScriptPdf));
        exportLibraryPdfButton.setOnAction(event -> exportLibraryPdf(new ArrayList<>(table.getItems())));

        HBox actions = new HBox(10, add, edit, duplicate, archive, delete, importButton, exportButton, exportPdfButton, exportLibraryPdfButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        page.getChildren().addAll(filters, actions, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        setView("library", "Bibliothèque", "CRUD, recherche, filtres, import et export", page);
    }

    private void showEditor() {
        refreshData();
        Script initialScript = pendingEditorScript != null
                ? pendingEditorScript
                : scripts.stream().findFirst().orElseGet(Script::new);
        pendingEditorScript = null;

        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().addAll(editorScriptList(initialScript), editorPanel(initialScript));
        splitPane.setDividerPositions(0.24);
        setView("editor", "Éditeur", "Édition RichTextFX, variables dynamiques et console intégrée", splitPane);
        loadScriptInEditor(initialScript);
    }

    private VBox editorScriptList(Script initialScript) {
        ListView<Script> listView = new ListView<>(scripts);
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
            scripts.stream()
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

    private VBox editorPanel(Script initialScript) {
        editorName = new TextField();
        editorName.setPromptText("Nom du script");
        editorLanguage = new ComboBox<>(FXCollections.observableArrayList(ScriptLanguage.values()));
        editorCategory = new ComboBox<>(FXCollections.observableArrayList(scriptService.categories()));
        editorCategory.setEditable(true);
        editorAuthor = new TextField();
        editorFavorite = new CheckBox("Favori");
        editorDescription = new TextArea();
        editorDescription.setPromptText("Description opérationnelle");
        editorDescription.setPrefRowCount(2);

        editorArea = new CodeArea();
        editorArea.getStyleClass().add("code-area");
        editorArea.setParagraphGraphicFactory(LineNumberFactory.get(editorArea));
        editorArea.setStyle("-fx-font-size: " + settingsService.editorFontSize() + "px;");
        editorArea.multiPlainChanges()
                .successionEnds(java.time.Duration.ofMillis(160))
                .subscribe(ignore -> highlightEditor());
        editorLanguage.valueProperty().addListener((observable, oldValue, newValue) -> highlightEditor());

        TextField findField = new TextField();
        findField.setPromptText("Rechercher");
        TextField replaceField = new TextField();
        replaceField.setPromptText("Remplacer");
        Button findButton = UiFactory.button("Trouver", "fas-search", "ghost-button");
        Button replaceButton = UiFactory.button("Remplacer", "fas-exchange-alt", "ghost-button");
        findButton.setOnAction(event -> findInEditor(findField.getText()));
        replaceButton.setOnAction(event -> replaceInEditor(findField.getText(), replaceField.getText()));

        Button run = UiFactory.button("Exécuter", "fas-play", "success-button");
        Button save = UiFactory.button("Sauvegarder", "fas-save", "primary-button");
        Button duplicate = UiFactory.button("Dupliquer", "fas-copy", "ghost-button");
        Button export = UiFactory.button("Exporter source", "fas-file-export", "ghost-button");
        Button exportPdf = UiFactory.button("Exporter PDF", "fas-file-pdf", "ghost-button");
        Button favorite = UiFactory.button("Favori", "fas-star", "ghost-button");
        run.setOnAction(event -> runCurrentScript(run));
        save.setOnAction(event -> saveEditorScript(true));
        duplicate.setOnAction(event -> saveEditorScript(false).ifPresent(script -> {
            Script duplicateScript = scriptService.duplicate(script);
            notifyInfo("Script dupliqué");
            openEditor(duplicateScript);
        }));
        export.setOnAction(event -> saveEditorScript(false).ifPresent(this::exportScript));
        exportPdf.setOnAction(event -> saveEditorScript(false).ifPresent(this::exportScriptPdf));
        favorite.setOnAction(event -> {
            editorFavorite.setSelected(!editorFavorite.isSelected());
            saveEditorScript(true);
        });

        HBox identity = new HBox(12, editorName, editorLanguage, editorCategory, editorAuthor, editorFavorite);
        HBox.setHgrow(editorName, Priority.ALWAYS);
        HBox.setHgrow(editorCategory, Priority.ALWAYS);
        HBox.setHgrow(editorAuthor, Priority.ALWAYS);
        identity.setAlignment(Pos.CENTER_LEFT);

        HBox tools = new HBox(10, run, save, duplicate, export, exportPdf, favorite);
        tools.setAlignment(Pos.CENTER_LEFT);
        HBox search = new HBox(10, findField, replaceField, findButton, replaceButton);
        HBox.setHgrow(findField, Priority.ALWAYS);
        HBox.setHgrow(replaceField, Priority.ALWAYS);

        executionConsole = new TextArea();
        executionConsole.getStyleClass().add("console");
        executionConsole.setEditable(false);
        executionConsole.setPrefRowCount(7);
        executionConsole.setText("Console prête.");

        VBox panel = new VBox(14,
                tools,
                identity,
                editorDescription,
                search,
                new VirtualizedScrollPane<>(editorArea),
                label("Console intégrée", "section-title"),
                executionConsole);
        panel.getStyleClass().add("editor-panel");
        VBox.setVgrow(panel.getChildren().get(4), Priority.ALWAYS);
        panel.setPadding(new Insets(16));
        selectedScript = initialScript;
        return panel;
    }

    private void showExecutions() {
        refreshData();
        VBox page = new VBox(14);
        TableView<ExecutionRecord> table = executionTable();
        TextArea details = executionDetailsArea();
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, record) -> {
            if (record != null) {
                details.setText(formatExecutionOutput(record));
            }
        });
        table.getItems().setAll(history);
        if (!history.isEmpty()) {
            table.getSelectionModel().selectFirst();
        }
        Button refresh = UiFactory.button("Actualiser", "fas-sync", "ghost-button");
        Button exportPdf = UiFactory.button("Exporter PDF", "fas-file-pdf", "ghost-button");
        refresh.setOnAction(event -> {
            refreshData();
            table.getItems().setAll(history);
        });
        exportPdf.setOnAction(event -> exportHistoryPdf(new ArrayList<>(table.getItems())));
        page.getChildren().addAll(new HBox(10, refresh, exportPdf), table, details);
        VBox.setVgrow(table, Priority.ALWAYS);
        setView("executions", "Exécutions", "Sorties, erreurs, code retour et durée", page);
    }

    private void showFavorites() {
        refreshData();
        VBox page = new VBox(14);
        List<Script> favorites = scriptService.findFavorites();
        if (favorites.isEmpty()) {
            page.getChildren().add(emptyCard("Aucun favori", "Ajoutez des scripts aux favoris pour les retrouver ici."));
        } else {
            favorites.forEach(script -> {
                VBox card = UiFactory.card();
                Label name = UiFactory.title(script.getName());
                Label meta = label(script.getLanguage().getDisplayName() + " · " + script.getCategory(), "muted-label");
                Label description = label(script.getDescription(), "muted-label");
                Button open = UiFactory.button("Ouvrir", "fas-code", "primary-button");
                Button run = UiFactory.button("Exécuter", "fas-play", "success-button");
                open.setOnAction(event -> openEditor(script));
                run.setOnAction(event -> {
                    openEditor(script);
                    runCurrentScript(run);
                });
                card.getChildren().addAll(name, meta, description, new HBox(10, open, run));
                page.getChildren().add(card);
            });
        }
        setView("favorites", "Favoris", "Accès rapide aux scripts les plus utilisés", page);
    }

    private void showTemplates() {
        VBox page = new VBox(14);
        for (Script template : templateService.templates()) {
            VBox card = UiFactory.card();
            Label title = UiFactory.title(template.getName());
            Label meta = label(template.getLanguage().getDisplayName() + " · " + template.getCategory(), "pill");
            Label description = label(template.getDescription(), "muted-label");
            TextArea preview = new TextArea(template.getContent());
            preview.setEditable(false);
            preview.setPrefRowCount(4);
            Button create = UiFactory.button("Créer depuis ce modèle", "fas-plus", "primary-button");
            create.setOnAction(event -> {
                Script script = copyTemplate(template);
                scriptService.save(script);
                notifyInfo("Modèle ajouté à la bibliothèque");
                openEditor(script);
            });
            card.getChildren().addAll(new HBox(10, title, meta), description, preview, create);
            page.getChildren().add(card);
        }
        setView("templates", "Modèles", "Bibliothèque de scripts prêts à adapter", page);
    }

    private void showVariables() {
        ObservableList<VariableEntry> variables = FXCollections.observableArrayList(variableService.findAll());
        VBox page = new VBox(14);
        TableView<VariableEntry> table = new TableView<>(variables);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().setAll(List.of(
                column("Nom", VariableEntry::getName),
                column("Valeur par défaut", VariableEntry::getDefaultValue),
                column("Description", VariableEntry::getDescription),
                column("Type", variable -> variable.isBuiltIn() ? "Intégrée" : "Personnalisée")
        ));

        TextField name = new TextField();
        name.setPromptText("NOM_VARIABLE");
        TextField defaultValue = new TextField();
        defaultValue.setPromptText("Valeur par défaut");
        TextField description = new TextField();
        description.setPromptText("Description");
        Button newVariable = UiFactory.button("Nouvelle", "fas-plus", "ghost-button");
        Button save = UiFactory.button("Sauvegarder", "fas-save", "primary-button");
        Button delete = UiFactory.button("Supprimer", "fas-trash", "danger-button");
        VariableEntry[] selected = new VariableEntry[1];

        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, variable) -> {
            selected[0] = variable;
            if (variable != null) {
                name.setText(variable.getName());
                defaultValue.setText(variable.getDefaultValue());
                description.setText(variable.getDescription());
                delete.setDisable(variable.isBuiltIn());
            }
        });
        newVariable.setOnAction(event -> {
            selected[0] = null;
            name.clear();
            defaultValue.clear();
            description.clear();
            delete.setDisable(false);
        });
        save.setOnAction(event -> {
            VariableEntry variable = selected[0] == null ? new VariableEntry() : selected[0];
            variable.setName(name.getText());
            variable.setDefaultValue(defaultValue.getText());
            variable.setDescription(description.getText());
            if (selected[0] == null) {
                variable.setBuiltIn(false);
            }
            variableService.save(variable);
            variables.setAll(variableService.findAll());
            notifyInfo("Variable sauvegardée");
        });
        delete.setOnAction(event -> {
            if (selected[0] != null && confirm("Supprimer la variable", "Les scripts existants ne sont pas modifiés.")) {
                variableService.delete(selected[0]);
                variables.setAll(variableService.findAll());
                newVariable.fire();
                notifyInfo("Variable supprimée");
            }
        });

        HBox form = new HBox(10, name, defaultValue, description, newVariable, save, delete);
        HBox.setHgrow(name, Priority.ALWAYS);
        HBox.setHgrow(defaultValue, Priority.ALWAYS);
        HBox.setHgrow(description, Priority.ALWAYS);
        page.getChildren().addAll(table, form);
        VBox.setVgrow(table, Priority.ALWAYS);
        setView("variables", "Variables", "Valeurs dynamiques utilisées avant exécution", page);
    }

    private void showHistory() {
        refreshData();
        VBox page = new VBox(14);
        DatePicker dateFilter = new DatePicker();
        dateFilter.setPromptText("Date");
        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().add("Tous les résultats");
        for (ExecutionStatus status : ExecutionStatus.values()) {
            statusFilter.getItems().add(status.getDisplayName());
        }
        statusFilter.getSelectionModel().selectFirst();
        TableView<ExecutionRecord> table = executionTable();
        Runnable filter = () -> {
            List<ExecutionRecord> filtered = history.stream()
                    .filter(record -> dateFilter.getValue() == null ||
                            record.getStartedAt().atZone(ZoneId.systemDefault()).toLocalDate().equals(dateFilter.getValue()))
                    .filter(record -> "Tous les résultats".equals(statusFilter.getValue()) ||
                            record.getStatus().getDisplayName().equals(statusFilter.getValue()))
                    .toList();
            table.getItems().setAll(filtered);
        };
        dateFilter.valueProperty().addListener((observable, oldValue, newValue) -> filter.run());
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> filter.run());
        filter.run();
        TextArea details = executionDetailsArea();
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, record) -> {
            if (record != null) {
                details.setText(formatExecutionOutput(record));
            }
        });
        Button exportPdf = UiFactory.button("Exporter PDF", "fas-file-pdf", "ghost-button");
        exportPdf.setOnAction(event -> exportHistoryPdf(new ArrayList<>(table.getItems())));
        page.getChildren().addAll(new HBox(10, dateFilter, statusFilter, exportPdf), table, details);
        VBox.setVgrow(table, Priority.ALWAYS);
        setView("history", "Historique", "Filtrage par date, script et résultat", page);
    }

    private void showDocumentation() {
        VBox page = new VBox(18);
        page.getStyleClass().add("documentation-page");

        VBox hero = UiFactory.card();
        hero.getStyleClass().add("documentation-hero");
        Label title = label("ScriptPro centralise les scripts IT, leur exécution et leur traçabilité.", "hero-title");
        title.setWrapText(true);
        Label text = wrappedLabel("L'application est conçue pour les administrateurs systèmes, techniciens support et équipes IT qui veulent stocker, retrouver, exécuter et documenter leurs scripts depuis une interface locale, rapide et contrôlée.");
        hero.getChildren().addAll(new HBox(12, UiFactory.icon("fas-shield-alt"), title), text);

        HBox metrics = new HBox(14,
                documentationMetric("Bibliothèque", "Scripts classés, filtrés et exportables", "fas-folder-open"),
                documentationMetric("Éditeur", "RichTextFX, variables et console intégrée", "fas-code"),
                documentationMetric("Traçabilité", "Historique SQLite et exports PDF", "fas-history")
        );

        Accordion accordion = new Accordion();
        accordion.getStyleClass().add("documentation-accordion");
        TitledPane firstPane = documentationPane("Rôle de l'application", "fas-info-circle",
                "ScriptPro sert de coffre opérationnel pour les scripts PowerShell, CMD, Batch, Bash et Python.",
                "Chaque script conserve son nom, son langage, sa catégorie, sa description, son auteur, ses dates, son état favori et son contenu.",
                "Les données sont stockées localement dans SQLite afin de rester disponibles sans serveur externe.");
        accordion.getPanes().addAll(
                firstPane,
                documentationPane("Dashboard", "fas-tachometer-alt",
                        "Le Dashboard affiche les KPI : scripts, favoris, exécutions du jour, échecs, catégories et historique.",
                        "Les blocs Activité récente et Derniers scripts utilisés permettent de reprendre rapidement le travail.",
                        "Utilisez les boutons Ouvrir pour basculer directement vers l'éditeur du script concerné."),
                documentationPane("Bibliothèque", "fas-folder-open",
                        "La Bibliothèque est l'écran principal de gestion CRUD : nouveau script, modification, duplication, archivage, suppression, import et export.",
                        "Les filtres permettent de cibler un langage, une catégorie, les favoris ou une date de modification.",
                        "Les exports disponibles couvrent le fichier source, le PDF du script sélectionné et le PDF de la bibliothèque filtrée."),
                documentationPane("Éditeur", "fas-code",
                        "L'Éditeur propose une zone de code avec numéros de ligne, coloration syntaxique, recherche, remplacement et sauvegarde.",
                        "Avant exécution, ScriptPro sauvegarde le script, détecte les commandes sensibles et demande les variables dynamiques.",
                        "La console intégrée affiche la sortie standard, les erreurs, le code retour, la durée et le statut de l'exécution."),
                documentationPane("Exécutions et Historique", "fas-terminal",
                        "Exécutions liste les runs récents avec résultat, durée et code retour.",
                        "Historique ajoute les filtres par date et résultat pour auditer rapidement une intervention.",
                        "Le bouton Exporter PDF génère un journal lisible, daté et signé LM-Code."),
                documentationPane("Favoris, Modèles et Variables", "fas-star",
                        "Favoris donne un accès rapide aux scripts utilisés fréquemment.",
                        "Modèles propose des bases PowerShell, CMD, Bash et Python prêtes à adapter puis à enregistrer dans la bibliothèque.",
                        "Variables gère les placeholders comme {{IP}}, {{USERNAME}}, {{HOSTNAME}}, {{PATH}} et {{SERVICE}} avant exécution."),
                documentationPane("Import, export et sauvegarde", "fas-file-export",
                        "Import accepte les fichiers .ps1, .bat, .cmd, .sh et .py.",
                        "Export source recrée le fichier script avec l'extension adaptée au langage.",
                        "Export PDF produit une fiche professionnelle pour un script, une bibliothèque filtrée ou un historique d'exécution."),
                documentationPane("Sécurité", "fas-shield-alt",
                        "ScriptPro analyse le contenu avant exécution et signale les commandes sensibles : rm -rf, format, diskpart, del /f /s, shutdown et commandes PowerShell destructives.",
                        "Une confirmation explicite est demandée lorsque ces motifs sont détectés.",
                        "Toutes les exécutions sont journalisées avec utilisateur, date, résultat, durée, sortie et erreurs."),
                documentationPane("FAQ", "fas-question-circle",
                        "Où sont les données ? Dans scriptpro.db à la racine du projet.",
                        "Comment lancer l'application ? Depuis le dossier du projet avec mvn clean javafx:run.",
                        "Pourquoi des warnings Java au lancement ? Java 24 signale les appels natifs de JavaFX et SQLite. Ce n'est pas bloquant.",
                        "Peut-on utiliser l'application hors ligne ? Oui, après téléchargement des dépendances Maven et création de la base locale.",
                        "Que faire si un export échoue ? Vérifiez le dossier cible, les droits d'écriture et le nom du fichier PDF.")
        );
        accordion.setExpandedPane(firstPane);

        page.getChildren().addAll(hero, metrics, accordion);
        setView("documentation", "Documentation", "Guide complet, FAQ et bonnes pratiques d'utilisation", page);
    }

    private void showSettings() {
        VBox page = new VBox(16);
        VBox card = UiFactory.card();
        ToggleSwitch theme = new ToggleSwitch("Mode sombre complet");
        theme.setSelected(settingsService.isDarkTheme());
        theme.selectedProperty().addListener((observable, oldValue, dark) -> {
            settingsService.setDarkTheme(dark);
            window.setDarkMode(dark);
        });

        TextField exportPath = new TextField(settingsService.get("exportPath", Path.of("exports").toAbsolutePath().toString()));
        Button browse = UiFactory.button("Parcourir", "fas-folder-open", "ghost-button");
        browse.setOnAction(event -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Chemin d'export");
            File directory = chooser.showDialog(window.getRoot().getScene().getWindow());
            if (directory != null) {
                exportPath.setText(directory.toPath().toString());
            }
        });

        Spinner<Integer> timeout = new Spinner<>();
        timeout.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(5, 600, settingsService.executionTimeoutSeconds()));
        Spinner<Integer> fontSize = new Spinner<>();
        fontSize.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(10, 28, settingsService.editorFontSize()));
        CheckBox notifications = new CheckBox("Notifications");
        notifications.setSelected(settingsService.notificationsEnabled());
        ComboBox<String> terminal = new ComboBox<>(FXCollections.observableArrayList("PowerShell", "CMD", "Bash", "Python"));
        terminal.setValue(settingsService.get("preferredTerminal", "PowerShell"));
        Button save = UiFactory.button("Sauvegarder les paramètres", "fas-save", "primary-button");
        save.setOnAction(event -> {
            settingsService.set("exportPath", exportPath.getText());
            settingsService.set("executionTimeout", String.valueOf(timeout.getValue()));
            settingsService.set("editorFontSize", String.valueOf(fontSize.getValue()));
            settingsService.set("notifications", String.valueOf(notifications.isSelected()));
            settingsService.set("preferredTerminal", terminal.getValue());
            notifyInfo("Paramètres sauvegardés");
        });

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(14);
        grid.add(label("Thème", "muted-label"), 0, 0);
        grid.add(theme, 1, 0);
        grid.add(label("Chemin export", "muted-label"), 0, 1);
        grid.add(new HBox(10, exportPath, browse), 1, 1);
        grid.add(label("Timeout exécution", "muted-label"), 0, 2);
        grid.add(timeout, 1, 2);
        grid.add(label("Taille police éditeur", "muted-label"), 0, 3);
        grid.add(fontSize, 1, 3);
        grid.add(label("Notifications", "muted-label"), 0, 4);
        grid.add(notifications, 1, 4);
        grid.add(label("Terminal préféré", "muted-label"), 0, 5);
        grid.add(terminal, 1, 5);
        card.getChildren().addAll(UiFactory.title("Paramètres applicatifs"), grid, save);
        page.getChildren().add(card);
        setView("settings", "Paramètres", "Préférences persistées dans SQLite", page);
    }

    private VBox documentationMetric(String title, String description, String iconLiteral) {
        VBox card = UiFactory.card();
        card.getStyleClass().add("doc-metric-card");
        Label titleLabel = label(title, "doc-metric-title");
        Label descriptionLabel = wrappedLabel(description);
        card.getChildren().addAll(new HBox(10, UiFactory.icon(iconLiteral), titleLabel), descriptionLabel);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private TitledPane documentationPane(String title, String iconLiteral, String... paragraphs) {
        VBox content = new VBox(10);
        content.getStyleClass().add("doc-pane-content");
        for (String paragraph : paragraphs) {
            content.getChildren().add(wrappedLabel(paragraph));
        }
        TitledPane pane = new TitledPane(title, content);
        pane.setGraphic(UiFactory.icon(iconLiteral));
        pane.getStyleClass().add("doc-pane");
        return pane;
    }

    private Label wrappedLabel(String text) {
        Label label = label(text, "doc-paragraph");
        label.setWrapText(true);
        return label;
    }

    private TableView<ExecutionRecord> executionTable() {
        TableView<ExecutionRecord> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().setAll(List.of(
                column("Script", ExecutionRecord::getScriptName),
                column("Langage", record -> record.getLanguage().getDisplayName()),
                column("Résultat", record -> record.getStatus().getDisplayName()),
                column("Date", record -> DateFormatter.format(record.getStartedAt())),
                column("Durée", record -> DateFormatter.duration(record.getDurationMillis())),
                column("Code", record -> String.valueOf(record.getExitCode()))
        ));
        return table;
    }

    private TextArea executionDetailsArea() {
        TextArea details = new TextArea();
        details.getStyleClass().add("console");
        details.setEditable(false);
        details.setPrefRowCount(10);
        return details;
    }

    private Optional<Script> saveEditorScript(boolean notify) {
        try {
            if (selectedScript == null) {
                selectedScript = new Script();
            }
            selectedScript.setName(editorName.getText());
            selectedScript.setLanguage(editorLanguage.getValue());
            selectedScript.setCategory(comboText(editorCategory));
            selectedScript.setDescription(editorDescription.getText());
            selectedScript.setAuthor(editorAuthor.getText());
            selectedScript.setFavorite(editorFavorite.isSelected());
            selectedScript.setContent(editorArea.getText());
            Script saved = scriptService.save(selectedScript);
            selectedScript = saved;
            refreshData();
            if (notify) {
                notifyInfo("Script sauvegardé");
            }
            return Optional.of(saved);
        } catch (RuntimeException exception) {
            showError("Sauvegarde impossible", exception.getMessage());
            return Optional.empty();
        }
    }

    private void runCurrentScript(Button runButton) {
        Optional<Script> savedScript = saveEditorScript(false);
        if (savedScript.isEmpty()) {
            return;
        }
        Script script = savedScript.get();
        List<String> warnings = securityService.detectWarnings(script.getContent());
        if (!warnings.isEmpty() && !confirm("Confirmation de sécurité",
                "Commandes sensibles détectées : " + String.join(", ", warnings) + "\nConfirmer l'exécution ?")) {
            return;
        }
        Map<String, String> variables = promptVariables(script.getContent());
        if (variables == null) {
            return;
        }
        runButton.setDisable(true);
        executionConsole.setText("Exécution en cours...\n");
        Task<ExecutionRecord> task = executionService.executeAsync(script, variables);
        task.setOnSucceeded(event -> {
            ExecutionRecord record = task.getValue();
            executionConsole.setText(formatExecutionOutput(record));
            refreshData();
            runButton.setDisable(false);
            if (record.getStatus() == ExecutionStatus.SUCCESS) {
                notifyInfo("Script exécuté avec succès");
            } else {
                notifyWarning("Exécution terminée avec erreur");
            }
        });
        task.setOnFailed(event -> {
            executionConsole.setText(task.getException().getMessage());
            runButton.setDisable(false);
            notifyWarning("Erreur d'exécution");
        });
    }

    private Map<String, String> promptVariables(String content) {
        Set<String> variableNames = variableService.extractVariables(content);
        if (variableNames.isEmpty()) {
            return Map.of();
        }
        Map<String, VariableEntry> knownVariables = variableService.findAllByName();
        Dialog<Map<String, String>> dialog = new Dialog<>();
        dialog.setTitle("Variables d'exécution");
        dialog.setHeaderText("Renseigner les valeurs avant exécution");
        ButtonType runButton = new ButtonType("Exécuter", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(runButton, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(14));
        Map<String, TextField> fields = new HashMap<>();
        int row = 0;
        for (String variableName : variableNames) {
            VariableEntry variable = knownVariables.get(variableName);
            TextField field = new TextField(variable == null ? "" : variable.getDefaultValue());
            fields.put(variableName, field);
            grid.add(label("{{" + variableName + "}}", "muted-label"), 0, row);
            grid.add(field, 1, row);
            row++;
        }
        dialog.getDialogPane().setContent(grid);
        dialog.setResultConverter(button -> {
            if (button != runButton) {
                return null;
            }
            Map<String, String> values = new HashMap<>();
            fields.forEach((name, field) -> values.put(name, field.getText()));
            return values;
        });
        return dialog.showAndWait().orElse(null);
    }

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
        editorDescription.setText(value(script.getDescription()));
        editorArea.replaceText(value(script.getContent()));
        executionConsole.setText("Console prête.");
        highlightEditor();
    }

    private void highlightEditor() {
        if (editorArea == null || editorLanguage == null || editorLanguage.getValue() == null) {
            return;
        }
        editorArea.setStyleSpans(0, SyntaxHighlighter.compute(editorArea.getText(), editorLanguage.getValue()));
    }

    private void findInEditor(String query) {
        if (query == null || query.isBlank()) {
            return;
        }
        int index = editorArea.getText().toLowerCase().indexOf(query.toLowerCase());
        if (index >= 0) {
            editorArea.selectRange(index, index + query.length());
            editorArea.requestFocus();
        }
    }

    private void replaceInEditor(String query, String replacement) {
        if (query == null || query.isBlank()) {
            return;
        }
        int index = editorArea.getText().toLowerCase().indexOf(query.toLowerCase());
        if (index >= 0) {
            editorArea.replaceText(index, index + query.length(), replacement == null ? "" : replacement);
        }
    }

    private void openEditor(Script script) {
        pendingEditorScript = script;
        showEditor();
    }

    private void importScript(Runnable afterImport) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Importer un script");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Scripts", "*.ps1", "*.bat", "*.cmd", "*.sh", "*.py"));
        File file = chooser.showOpenDialog(window.getRoot().getScene().getWindow());
        if (file == null) {
            return;
        }
        try {
            Script imported = importExportService.importScript(file.toPath());
            refreshData();
            afterImport.run();
            notifyInfo("Import terminé");
            openEditor(imported);
        } catch (IOException | RuntimeException exception) {
            showError("Import impossible", exception.getMessage());
        }
    }

    private void exportScript(Script script) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Exporter le script");
        File directory = chooser.showDialog(window.getRoot().getScene().getWindow());
        if (directory == null) {
            return;
        }
        try {
            Path target = importExportService.exportScript(directory.toPath(), script);
            notifyInfo("Export terminé : " + target.getFileName());
        } catch (IOException exception) {
            showError("Export impossible", exception.getMessage());
        }
    }

    private void exportScriptPdf(Script script) {
        File file = choosePdfFile("Exporter le script en PDF", safeFileName(script.getName()) + ".pdf");
        if (file == null) {
            return;
        }
        try {
            Path target = importExportService.exportScriptPdf(file.toPath(), script);
            notifyInfo("Export PDF terminé : " + target.getFileName());
            window.setFooterStatus("Dernier export PDF : " + target.getFileName());
        } catch (IOException exception) {
            showError("Export PDF impossible", exception.getMessage());
        }
    }

    private void exportLibraryPdf(List<Script> exportedScripts) {
        if (exportedScripts.isEmpty()) {
            notifyWarning("Aucun script à exporter");
            return;
        }
        File file = choosePdfFile("Exporter la bibliothèque en PDF", "bibliotheque-scriptpro.pdf");
        if (file == null) {
            return;
        }
        try {
            Path target = importExportService.exportLibraryPdf(file.toPath(), exportedScripts);
            notifyInfo("Bibliothèque PDF exportée : " + target.getFileName());
            window.setFooterStatus("Dernier export PDF : " + target.getFileName());
        } catch (IOException exception) {
            showError("Export PDF impossible", exception.getMessage());
        }
    }

    private void exportHistoryPdf(List<ExecutionRecord> records) {
        if (records.isEmpty()) {
            notifyWarning("Aucun historique à exporter");
            return;
        }
        File file = choosePdfFile("Exporter l'historique en PDF", "historique-scriptpro.pdf");
        if (file == null) {
            return;
        }
        try {
            Path target = importExportService.exportHistoryPdf(file.toPath(), records);
            notifyInfo("Historique PDF exporté : " + target.getFileName());
            window.setFooterStatus("Dernier export PDF : " + target.getFileName());
        } catch (IOException exception) {
            showError("Export PDF impossible", exception.getMessage());
        }
    }

    private File choosePdfFile(String title, String initialFileName) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        chooser.setInitialFileName(initialFileName);
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Document PDF", "*.pdf"));
        return chooser.showSaveDialog(window.getRoot().getScene().getWindow());
    }

    private String safeFileName(String value) {
        String fileName = value == null || value.isBlank() ? "scriptpro" : value;
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private Script copyTemplate(Script template) {
        Script script = new Script();
        script.setName(template.getName());
        script.setCategory(template.getCategory());
        script.setLanguage(template.getLanguage());
        script.setDescription(template.getDescription());
        script.setContent(template.getContent());
        script.setFavorite(false);
        return script;
    }

    private VBox emptyCard(String title, String message) {
        VBox card = UiFactory.card();
        card.getChildren().addAll(UiFactory.title(title), label(message, "muted-label"));
        return card;
    }

    private String formatExecutionOutput(ExecutionRecord record) {
        return "Script : " + record.getScriptName() + "\n" +
                "Résultat : " + record.getStatus().getDisplayName() + "\n" +
                "Code retour : " + record.getExitCode() + "\n" +
                "Durée : " + DateFormatter.duration(record.getDurationMillis()) + "\n" +
                "Date : " + DateFormatter.format(record.getStartedAt()) + "\n\n" +
                "Sortie standard\n" +
                "----------------\n" +
                value(record.getStdout()) + "\n\n" +
                "Erreurs\n" +
                "----------------\n" +
                value(record.getStderr());
    }

    private void toggleTheme() {
        boolean dark = !settingsService.isDarkTheme();
        settingsService.setDarkTheme(dark);
        window.setDarkMode(dark);
    }

    private boolean confirm(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(content);
        return alert.showAndWait().filter(button -> button == ButtonType.OK).isPresent();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void notifyInfo(String message) {
        if (settingsService.notificationsEnabled()) {
            Notifications.create()
                    .title("ScriptPro")
                    .text(message)
                    .hideAfter(javafx.util.Duration.seconds(3))
                    .showInformation();
        }
    }

    private void notifyWarning(String message) {
        if (settingsService.notificationsEnabled()) {
            Notifications.create()
                    .title("ScriptPro")
                    .text(message)
                    .hideAfter(javafx.util.Duration.seconds(3))
                    .showWarning();
        }
    }

    private <T> TableColumn<T, String> column(String title, Function<T, String> mapper) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(mapper.apply(data.getValue())));
        return column;
    }

    private Optional<Script> selected(TableView<Script> table) {
        return Optional.ofNullable(table.getSelectionModel().getSelectedItem());
    }

    private Label label(String text, String styleClass) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private ScriptLanguage selectedLanguage(String displayName) {
        if (displayName == null || displayName.equals("Tous les langages")) {
            return null;
        }
        for (ScriptLanguage language : ScriptLanguage.values()) {
            if (language.getDisplayName().equals(displayName)) {
                return language;
            }
        }
        return null;
    }

    private String comboText(ComboBox<String> comboBox) {
        String editorText = comboBox.getEditor() == null ? "" : comboBox.getEditor().getText();
        if (editorText != null && !editorText.isBlank()) {
            return editorText.trim();
        }
        return comboBox.getValue() == null ? "Général" : comboBox.getValue();
    }

    private String statusClass(ExecutionStatus status) {
        return switch (status) {
            case SUCCESS -> "status-success";
            case FAILURE -> "status-failure";
            case WARNING -> "status-warning";
        };
    }

    private String value(String text) {
        return text == null ? "" : text;
    }
}
