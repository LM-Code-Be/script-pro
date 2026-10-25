package com.scriptpro.controllers;

import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.ui.UiFactory;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;

/**
 * Liste des exécutions récentes avec le détail de la sortie de chacune.
 *
 * @author LM-Code
 */
final class ExecutionsView {
    private final ViewContext context;
    private final FileActions fileActions;

    ExecutionsView(ViewContext context, FileActions fileActions) {
        this.context = context;
        this.fileActions = fileActions;
    }

    void show() {
        context.refreshData();
        VBox page = new VBox(14);
        TableView<ExecutionRecord> table = ExecutionTables.table();
        TextArea details = ExecutionTables.detailsFor(table);
        table.getItems().setAll(context.history);
        if (!context.history.isEmpty()) {
            table.getSelectionModel().selectFirst();
        }
        Button refresh = UiFactory.button("Actualiser", "fas-sync", "ghost-button");
        Button exportPdf = UiFactory.button("Exporter PDF", "fas-file-pdf", "ghost-button");
        refresh.setOnAction(event -> {
            context.refreshData();
            table.getItems().setAll(context.history);
        });
        exportPdf.setOnAction(event -> fileActions.exportHistoryPdf(new ArrayList<>(table.getItems())));
        page.getChildren().addAll(new HBox(10, ExecutionTables.relaunchButton(context, table), refresh, exportPdf),
                table, details);
        VBox.setVgrow(table, Priority.ALWAYS);
        context.setView("executions", "Exécutions", "Sorties, erreurs, code retour et durée", page);
    }
}
