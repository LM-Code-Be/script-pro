package com.scriptpro.controllers;

import com.scriptpro.models.Script;
import com.scriptpro.models.ScriptLanguage;
import com.scriptpro.ui.UiFactory;
import com.scriptpro.utils.DateFormatter;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.scriptpro.ui.UiFactory.badgeColumn;
import static com.scriptpro.ui.UiFactory.column;

/**
 * Bibliothèque : liste filtrable des scripts actifs et actions de gestion (CRUD, import, exports).
 *
 * @author LM-Code
 */
final class LibraryView {
    private static final String ALL_LANGUAGES = "Tous les langages";
    private static final String ALL_CATEGORIES = "Toutes les catégories";
    private static final String ALL_TAGS = "Tous les tags";

    private final ViewContext context;
    private final FileActions fileActions;

    LibraryView(ViewContext context, FileActions fileActions) {
        this.context = context;
        this.fileActions = fileActions;
    }

    void show(String initialQuery) {
        context.refreshData();
        VBox page = new VBox(16);

        TextField queryField = new TextField(initialQuery == null ? "" : initialQuery);
        queryField.setPromptText("Recherche instantanée (nom, contenu, tags…)");
        ComboBox<String> languageFilter = new ComboBox<>();
        languageFilter.getItems().add(ALL_LANGUAGES);
        for (ScriptLanguage language : ScriptLanguage.values()) {
            languageFilter.getItems().add(language.getDisplayName());
        }
        languageFilter.getSelectionModel().selectFirst();

        ComboBox<String> categoryFilter = new ComboBox<>();
        categoryFilter.getItems().add(ALL_CATEGORIES);
        categoryFilter.getItems().addAll(context.scriptService.categories());
        categoryFilter.getSelectionModel().selectFirst();

        ComboBox<String> tagFilter = new ComboBox<>();
        tagFilter.getItems().add(ALL_TAGS);
        tagFilter.getItems().addAll(context.scriptService.tags());
        tagFilter.getSelectionModel().selectFirst();

        CheckBox favoritesOnly = new CheckBox("Favoris");
        DatePicker modifiedAfter = new DatePicker();
        modifiedAfter.setPromptText("Modifié après");

        TableView<Script> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.getColumns().setAll(List.of(
                column("Nom", Script::getName),
                badgeColumn("Langage", script -> script.getLanguage().getDisplayName(),
                        script -> UiFactory.languageClass(script.getLanguage())),
                column("Catégorie", Script::getCategory),
                column("Tags", Script::getTagsText),
                column("Auteur", Script::getAuthor),
                column("Mis à jour", script -> DateFormatter.format(script.getUpdatedAt())),
                badgeColumn("Favori", script -> script.isFavorite() ? "★ Favori" : "—",
                        script -> script.isFavorite() ? "badge-amber" : "badge-neutral")
        ));
        table.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2
                    && table.getSelectionModel().getSelectedItem() != null) {
                context.navigator().openEditor(table.getSelectionModel().getSelectedItem());
            }
        });

        Runnable applyFilter = () -> {
            ScriptLanguage language = languageFromDisplayName(languageFilter.getValue());
            String category = ALL_CATEGORIES.equals(categoryFilter.getValue()) ? "" : categoryFilter.getValue();
            String tag = ALL_TAGS.equals(tagFilter.getValue()) ? "" : tagFilter.getValue();
            List<Script> filtered = context.scriptService
                    .search(queryField.getText(), language, category, tag, favoritesOnly.isSelected())
                    .stream()
                    .filter(script -> modifiedAfter.getValue() == null ||
                            !script.getUpdatedAt().atZone(ZoneId.systemDefault()).toLocalDate().isBefore(modifiedAfter.getValue()))
                    .toList();
            table.getItems().setAll(filtered);
        };

        queryField.textProperty().addListener((observable, oldValue, newValue) -> applyFilter.run());
        languageFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyFilter.run());
        categoryFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyFilter.run());
        tagFilter.valueProperty().addListener((observable, oldValue, newValue) -> applyFilter.run());
        favoritesOnly.selectedProperty().addListener((observable, oldValue, newValue) -> applyFilter.run());
        modifiedAfter.valueProperty().addListener((observable, oldValue, newValue) -> applyFilter.run());
        applyFilter.run();

        HBox filters = new HBox(12, queryField, languageFilter, categoryFilter, tagFilter, favoritesOnly, modifiedAfter);
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

        add.setOnAction(event -> context.navigator().openEditor(new Script()));
        edit.setOnAction(event -> selected(table).ifPresent(context.navigator()::openEditor));
        duplicate.setOnAction(event -> selected(table).ifPresent(script -> {
            context.scriptService.duplicate(script);
            context.refreshData();
            applyFilter.run();
            context.notifyInfo("Script dupliqué");
        }));
        archive.setOnAction(event -> selected(table).ifPresent(script -> {
            if (context.confirm("Archiver le script", "Le script reste en base mais disparaît des vues actives.")) {
                context.scriptService.archive(script);
                context.refreshData();
                applyFilter.run();
                context.notifyInfo("Script archivé");
            }
        }));
        delete.setOnAction(event -> selected(table).ifPresent(script -> {
            if (context.confirm("Supprimer le script", "La suppression est définitive.")) {
                context.scriptService.delete(script);
                context.refreshData();
                applyFilter.run();
                context.notifyInfo("Script supprimé");
            }
        }));
        importButton.setOnAction(event -> fileActions.importScript().ifPresent(context.navigator()::openEditor));
        exportButton.setOnAction(event -> selected(table).ifPresent(fileActions::exportScript));
        exportPdfButton.setOnAction(event -> selected(table).ifPresent(fileActions::exportScriptPdf));
        exportLibraryPdfButton.setOnAction(event -> fileActions.exportLibraryPdf(new ArrayList<>(table.getItems())));

        HBox actions = new HBox(10, add, edit, duplicate, archive, delete, importButton, exportButton,
                exportPdfButton, exportLibraryPdfButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        page.getChildren().addAll(filters, actions, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        context.setView("library", "Bibliothèque", "CRUD, recherche, filtres, import et export", page);
    }

    private static Optional<Script> selected(TableView<Script> table) {
        return Optional.ofNullable(table.getSelectionModel().getSelectedItem());
    }

    private static ScriptLanguage languageFromDisplayName(String displayName) {
        for (ScriptLanguage language : ScriptLanguage.values()) {
            if (language.getDisplayName().equals(displayName)) {
                return language;
            }
        }
        return null;
    }
}
