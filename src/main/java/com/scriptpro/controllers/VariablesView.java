package com.scriptpro.controllers;

import com.scriptpro.models.VariableEntry;
import com.scriptpro.ui.UiFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;

import static com.scriptpro.ui.UiFactory.badgeColumn;
import static com.scriptpro.ui.UiFactory.column;
import static com.scriptpro.ui.UiFactory.label;

/**
 * Gestion des variables dynamiques et de leurs valeurs par défaut.
 *
 * <p>Une variable cochée « Sensible » voit sa valeur masquée partout dans l'interface
 * (tableau, saisie avant exécution, console, historique). Attention : la valeur par défaut
 * reste stockée en clair dans la base locale ; mieux vaut la laisser vide pour un mot de passe.</p>
 *
 * @author LM-Code
 */
final class VariablesView {
    private final ViewContext context;

    /** Variable en cours d'édition, ou {@code null} pour une nouvelle variable. */
    private VariableEntry selected;

    VariablesView(ViewContext context) {
        this.context = context;
    }

    void show() {
        selected = null;
        ObservableList<VariableEntry> variables = FXCollections.observableArrayList(context.variableService.findAll());
        VBox page = new VBox(14);
        TableView<VariableEntry> table = new TableView<>(variables);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getColumns().setAll(List.of(
                column("Nom", VariableEntry::getName),
                column("Valeur par défaut", VariableEntry::displayValue),
                column("Description", VariableEntry::getDescription),
                badgeColumn("Sensible", variable -> variable.isSecret() ? "Sensible" : "Non",
                        variable -> variable.isSecret() ? "badge-red" : "badge-neutral"),
                badgeColumn("Type", variable -> variable.isBuiltIn() ? "Intégrée" : "Personnalisée",
                        variable -> variable.isBuiltIn() ? "badge-blue" : "badge-violet")
        ));

        TextField name = new TextField();
        name.setPromptText("NOM_VARIABLE");
        // Deux champs superposés pour la valeur : texte clair ou masqué selon la case « Sensible ».
        TextField plainValue = new TextField();
        plainValue.setPromptText("Valeur par défaut");
        PasswordField secretValue = new PasswordField();
        secretValue.setPromptText("Valeur par défaut (masquée)");
        secretValue.textProperty().bindBidirectional(plainValue.textProperty());
        CheckBox secret = new CheckBox("Sensible");
        plainValue.visibleProperty().bind(secret.selectedProperty().not());
        secretValue.visibleProperty().bind(secret.selectedProperty());
        StackPane valueField = new StackPane(plainValue, secretValue);
        TextField description = new TextField();
        description.setPromptText("Description");

        Button newVariable = UiFactory.button("Nouvelle", "fas-plus", "ghost-button");
        Button save = UiFactory.button("Sauvegarder", "fas-save", "primary-button");
        Button delete = UiFactory.button("Supprimer", "fas-trash", "danger-button");

        table.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, variable) -> {
            selected = variable;
            if (variable != null) {
                name.setText(variable.getName());
                plainValue.setText(variable.getDefaultValue());
                description.setText(variable.getDescription());
                secret.setSelected(variable.isSecret());
                delete.setDisable(variable.isBuiltIn());
            }
        });
        newVariable.setOnAction(event -> {
            table.getSelectionModel().clearSelection();
            selected = null;
            name.clear();
            plainValue.clear();
            description.clear();
            secret.setSelected(false);
            delete.setDisable(true);
        });
        save.setOnAction(event -> {
            VariableEntry variable = selected == null ? new VariableEntry() : selected;
            variable.setName(name.getText());
            variable.setDefaultValue(plainValue.getText());
            variable.setDescription(description.getText());
            variable.setSecret(secret.isSelected());
            if (selected == null) {
                variable.setBuiltIn(false);
            }
            try {
                context.variableService.save(variable);
            } catch (RuntimeException exception) {
                context.showError("Variable non sauvegardée", exception.getMessage());
                return;
            }
            variables.setAll(context.variableService.findAll());
            // On reste sur la variable enregistrée : un second clic la met à jour au lieu d'en créer une autre.
            variables.stream()
                    .filter(entry -> entry.getId().equals(variable.getId()))
                    .findFirst()
                    .ifPresent(table.getSelectionModel()::select);
            context.notifyInfo("Variable sauvegardée");
        });
        delete.setOnAction(event -> {
            if (selected != null && context.confirm("Supprimer la variable", "Les scripts existants ne sont pas modifiés.")) {
                context.variableService.delete(selected);
                variables.setAll(context.variableService.findAll());
                newVariable.fire();
                context.notifyInfo("Variable supprimée");
            }
        });
        delete.setDisable(true);

        HBox form = new HBox(10, name, valueField, description, secret, newVariable, save, delete);
        form.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(name, Priority.ALWAYS);
        HBox.setHgrow(valueField, Priority.ALWAYS);
        HBox.setHgrow(description, Priority.ALWAYS);
        page.getChildren().addAll(table, form,
                label("Astuce : utilisez {{NOM}} dans un script. Pour un mot de passe, cochez « Sensible » et laissez "
                        + "la valeur par défaut vide : elle sera demandée à chaque exécution et masquée dans l'historique.",
                        "muted-label"));
        VBox.setVgrow(table, Priority.ALWAYS);
        context.setView("variables", "Variables", "Valeurs dynamiques utilisées avant exécution", page);
    }
}
