package com.scriptpro.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

public final class UiFactory {
    private UiFactory() {
    }

    public static FontIcon icon(String literal) {
        FontIcon icon = new FontIcon(literal);
        icon.getStyleClass().add("font-icon");
        return icon;
    }

    public static Button button(String text, String iconLiteral, String styleClass) {
        Button button = new Button(text);
        if (iconLiteral != null && !iconLiteral.isBlank()) {
            button.setGraphic(icon(iconLiteral));
        }
        button.getStyleClass().add(styleClass);
        return button;
    }

    public static VBox card() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(18));
        return card;
    }

    public static Label title(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    public static Region spacer() {
        Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);
        return region;
    }
}
