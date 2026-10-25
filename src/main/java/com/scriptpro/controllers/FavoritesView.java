package com.scriptpro.controllers;

import com.scriptpro.models.Script;
import com.scriptpro.ui.UiFactory;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.List;

import static com.scriptpro.ui.UiFactory.label;

/**
 * Accès rapide aux scripts marqués comme favoris.
 *
 * @author LM-Code
 */
final class FavoritesView {
    private final ViewContext context;

    FavoritesView(ViewContext context) {
        this.context = context;
    }

    void show() {
        context.refreshData();
        VBox page = new VBox(14);
        List<Script> favorites = context.scriptService.findFavorites();
        if (favorites.isEmpty()) {
            VBox card = UiFactory.card();
            card.getChildren().addAll(UiFactory.title("Aucun favori"),
                    label("Ajoutez des scripts aux favoris pour les retrouver ici.", "muted-label"));
            page.getChildren().add(card);
        } else {
            favorites.forEach(script -> page.getChildren().add(favoriteCard(script)));
        }
        context.setView("favorites", "Favoris", "Accès rapide aux scripts les plus utilisés", page);
    }

    private VBox favoriteCard(Script script) {
        VBox card = UiFactory.card();
        Label name = UiFactory.title(script.getName());
        String tags = script.getTags().isEmpty() ? "" : " · #" + String.join(" #", script.getTags());
        Label meta = label(script.getLanguage().getDisplayName() + " · " + script.getCategory() + tags, "muted-label");
        Label description = label(script.getDescription(), "muted-label");
        Button open = UiFactory.button("Ouvrir", "fas-code", "primary-button");
        Button run = UiFactory.button("Exécuter", "fas-play", "success-button");
        open.setOnAction(event -> context.navigator().openEditor(script));
        run.setOnAction(event -> context.navigator().runInEditor(script));
        card.getChildren().addAll(name, meta, description, new HBox(10, open, run));
        return card;
    }
}
