package com.scriptpro.ui;

import com.scriptpro.models.ScriptLanguage;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Locale;
import java.util.function.Function;

/**
 * Fabrique des composants visuels récurrents, pour garder un style homogène entre les écrans.
 *
 * @author LM-Code
 */
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
        Animations.hoverScale(button, "nav-button".equals(styleClass) ? 1.02 : 1.04);
        return button;
    }

    /** Bouton avec une infobulle (utile pour rappeler un raccourci clavier). */
    public static Button button(String text, String iconLiteral, String styleClass, String tooltip) {
        Button button = button(text, iconLiteral, styleClass);
        button.setTooltip(new Tooltip(tooltip));
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

    public static Label label(String text, String styleClass) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    /** Paragraphe de texte qui revient à la ligne automatiquement. */
    public static Label wrappedLabel(String text) {
        Label label = label(text, "doc-paragraph");
        label.setWrapText(true);
        return label;
    }

    /** Colonne de tableau en lecture seule, alimentée par une fonction d'extraction. */
    public static <T> TableColumn<T, String> column(String title, Function<T, String> mapper) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(mapper.apply(data.getValue())));
        return column;
    }

    /** Petite étiquette colorée aux bords arrondis (statut, langage…). */
    public static Label badge(String text, String colorClass) {
        Label badge = label(text, "badge");
        badge.getStyleClass().add(colorClass);
        return badge;
    }

    /** Classe CSS de couleur associée à un langage : chaque langage garde la même teinte partout. */
    public static String languageClass(ScriptLanguage language) {
        return "lang-" + language.name().toLowerCase(Locale.ROOT);
    }

    /** Badge affichant le nom d'un langage dans sa couleur. */
    public static Label languageBadge(ScriptLanguage language) {
        return badge(language.getDisplayName(), languageClass(language));
    }

    /**
     * Colonne de tableau qui affiche sa valeur sous forme de badge coloré.
     *
     * @param colorClass donne la classe CSS de couleur pour chaque ligne
     */
    public static <T> TableColumn<T, String> badgeColumn(String title, Function<T, String> mapper,
                                                         Function<T, String> colorClass) {
        TableColumn<T, String> column = column(title, mapper);
        column.setCellFactory(ignored -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                T row = empty || getTableRow() == null ? null : getTableRow().getItem();
                setText(null);
                setGraphic(row == null ? null : badge(value, colorClass.apply(row)));
            }
        });
        return column;
    }

    public static Region spacer() {
        Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);
        return region;
    }
}
