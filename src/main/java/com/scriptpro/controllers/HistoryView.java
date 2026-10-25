package com.scriptpro.controllers;

import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.models.ExecutionStatus;
import com.scriptpro.ui.UiFactory;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Historique complet des exécutions, filtrable par date, nom de script et résultat.
 *
 * @author LM-Code
 */
final class HistoryView {
    private static final String ALL_STATUSES = "Tous les résultats";

    private final ViewContext context;
    private final FileActions fileActions;

    HistoryView(ViewContext context, FileActions fileActions) {
        this.context = context;
        this.fileActions = fileActions;
    }

    void show() {
        context.refreshData();
        VBox page = new VBox(14);
        DatePicker dateFilter = new DatePicker();
        dateFilter.setPromptText("Date");
        TextField scriptFilter = new TextField();
        scriptFilter.setPromptText("Nom du script");
        ComboBox<String> statusFilter = new ComboBox<>();
        statusFilter.getItems().add(ALL_STATUSES);
        for (ExecutionStatus status : ExecutionStatus.values()) {
            statusFilter.getItems().add(status.getDisplayName());
        }
        statusFilter.getSelectionModel().selectFirst();

        TableView<ExecutionRecord> table = ExecutionTables.table();
        Runnable filter = () -> {
            String scriptQuery = scriptFilter.getText() == null ? "" : scriptFilter.getText().toLowerCase(Locale.ROOT).trim();
            List<ExecutionRecord> filtered = context.history.stream()
                    .filter(record -> dateFilter.getValue() == null ||
                            record.getStartedAt().atZone(ZoneId.systemDefault()).toLocalDate().equals(dateFilter.getValue()))
                    .filter(record -> scriptQuery.isEmpty() ||
                            record.getScriptName().toLowerCase(Locale.ROOT).contains(scriptQuery))
                    .filter(record -> ALL_STATUSES.equals(statusFilter.getValue()) ||
                            record.getStatus().getDisplayName().equals(statusFilter.getValue()))
                    .toList();
            table.getItems().setAll(filtered);
        };
        dateFilter.valueProperty().addListener((observable, oldValue, newValue) -> filter.run());
        scriptFilter.textProperty().addListener((observable, oldValue, newValue) -> filter.run());
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> filter.run());
        filter.run();

        TextArea details = ExecutionTables.detailsFor(table);
        Button exportPdf = UiFactory.button("Exporter PDF", "fas-file-pdf", "ghost-button");
        exportPdf.setOnAction(event -> fileActions.exportHistoryPdf(new ArrayList<>(table.getItems())));
        HBox toolbar = new HBox(10, dateFilter, scriptFilter, statusFilter,
                ExecutionTables.relaunchButton(context, table), exportPdf);
        HBox.setHgrow(scriptFilter, Priority.ALWAYS);
        page.getChildren().addAll(toolbar, table, details);
        VBox.setVgrow(table, Priority.ALWAYS);
        context.setView("history", "Historique", "Filtrage par date, script et résultat", page);
    }
}
