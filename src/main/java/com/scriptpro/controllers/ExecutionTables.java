package com.scriptpro.controllers;

import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.models.Script;
import com.scriptpro.ui.UiFactory;
import com.scriptpro.utils.DateFormatter;
import com.scriptpro.utils.ExecutionFormatter;
import javafx.scene.control.Button;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;

import java.util.List;
import java.util.Optional;

import static com.scriptpro.ui.UiFactory.badgeColumn;
import static com.scriptpro.ui.UiFactory.column;

/**
 * Composants partagés par les écrans « Exécutions » et « Historique » :
 * tableau des exécutions, zone de détail et bouton « Relancer ».
 *
 * @author LM-Code
 */
final class ExecutionTables {
    private ExecutionTables() {
    }

    static TableView<ExecutionRecord> table() {
        TableView<ExecutionRecord> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().setAll(List.of(
                column("Script", ExecutionRecord::getScriptName),
                badgeColumn("Langage", record -> record.getLanguage().getDisplayName(),
                        record -> UiFactory.languageClass(record.getLanguage())),
                badgeColumn("Résultat", record -> record.getStatus().getDisplayName(),
                        record -> ExecutionFormatter.badgeClass(record.getStatus())),
                column("Date", record -> DateFormatter.format(record.getStartedAt())),
                column("Durée", record -> DateFormatter.duration(record.getDurationMillis())),
                column("Code", record -> String.valueOf(record.getExitCode()))
        ));
        return table;
    }

    /** Zone de détail qui suit la ligne sélectionnée dans le tableau. */
    static TextArea detailsFor(TableView<ExecutionRecord> table) {
        TextArea details = new TextArea();
        details.getStyleClass().add("console");
        details.setEditable(false);
        details.setPrefRowCount(10);
        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, record) ->
                details.setText(record == null ? "" : ExecutionFormatter.format(record)));
        return details;
    }

    /**
     * Bouton qui rouvre le script de l'exécution sélectionnée dans l'éditeur et le relance.
     * Le script actuel est utilisé (s'il a été modifié depuis, c'est la nouvelle version qui tourne).
     */
    static Button relaunchButton(ViewContext context, TableView<ExecutionRecord> table) {
        Button relaunch = UiFactory.button("Relancer", "fas-redo", "success-button");
        relaunch.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        relaunch.setOnAction(event -> {
            ExecutionRecord record = table.getSelectionModel().getSelectedItem();
            if (record == null) {
                return;
            }
            Optional<Script> script = context.scriptService.findById(record.getScriptId());
            if (script.isEmpty()) {
                context.showError("Relance impossible",
                        "Le script « " + record.getScriptName() + " » a été supprimé de la bibliothèque.");
                return;
            }
            context.navigator().runInEditor(script.get());
        });
        return relaunch;
    }
}
