package com.scriptpro.controllers;

import com.scriptpro.models.Script;
import com.scriptpro.ui.UiFactory;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import static com.scriptpro.ui.UiFactory.label;

/**
 * Modèles de scripts prêts à l'emploi, copiables dans la bibliothèque.
 *
 * @author LM-Code
 */
final class TemplatesView {
    private final ViewContext context;

    TemplatesView(ViewContext context) {
        this.context = context;
    }

    void show() {
        VBox page = new VBox(14);
        for (Script template : context.templateService.templates()) {
            VBox card = UiFactory.card();
            TextArea preview = new TextArea(template.getContent());
            preview.setEditable(false);
            preview.setPrefRowCount(4);
            Button create = UiFactory.button("Créer depuis ce modèle", "fas-plus", "primary-button");
            create.setOnAction(event -> {
                Script script = copyTemplate(template);
                context.scriptService.save(script);
                context.notifyInfo("Modèle ajouté à la bibliothèque");
                context.navigator().openEditor(script);
            });
            card.getChildren().addAll(
                    new HBox(10, UiFactory.title(template.getName()),
                            label(template.getLanguage().getDisplayName() + " · " + template.getCategory(), "pill")),
                    label(template.getDescription(), "muted-label"),
                    preview,
                    create);
            page.getChildren().add(card);
        }
        context.setView("templates", "Modèles", "Bibliothèque de scripts prêts à adapter", page);
    }

    private static Script copyTemplate(Script template) {
        Script script = new Script();
        script.setName(template.getName());
        script.setCategory(template.getCategory());
        script.setLanguage(template.getLanguage());
        script.setDescription(template.getDescription());
        script.setContent(template.getContent());
        script.setTags(template.getTags());
        script.setFavorite(false);
        return script;
    }
}
