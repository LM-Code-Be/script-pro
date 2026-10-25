package com.scriptpro.controllers;

import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.models.ExecutionStatus;
import com.scriptpro.models.Script;
import com.scriptpro.models.ScriptLanguage;
import com.scriptpro.ui.Animations;
import com.scriptpro.ui.UiFactory;
import com.scriptpro.utils.DateFormatter;
import com.scriptpro.utils.ExecutionFormatter;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.LocalTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.scriptpro.ui.UiFactory.label;

/**
 * Écran d'accueil : bannière de bienvenue, indicateurs clés, activité récente,
 * répartition des scripts par langage, taux de réussite et derniers scripts.
 *
 * @author LM-Code
 */
final class DashboardView {
    /** Largeur maximale des barres de répartition, en pixels. */
    private static final double BAR_WIDTH = 360;

    private final ViewContext context;

    DashboardView(ViewContext context) {
        this.context = context;
    }

    void show() {
        context.refreshData();
        VBox page = new VBox(22);
        page.getChildren().addAll(hero(), kpis(),
                twoColumns(activityCard(), languagesCard()),
                twoColumns(successCard(), recentScriptsCard()));
        context.setView("dashboard", "Dashboard", "Vue opérationnelle des scripts, favoris et exécutions", page);
    }

    // ------------------------------------------------------------------ Bannière

    private HBox hero() {
        Label greeting = label(greeting() + " 👋", "hero-greeting");
        Label summary = label(context.scripts.isEmpty()
                ? "Ta bibliothèque est vide : crée ton premier script ou pars d'un modèle."
                : context.scripts.size() + " scripts prêts à l'emploi, " + context.historyRepository.countToday()
                        + " exécutions aujourd'hui.", "hero-summary");
        Button create = UiFactory.button("Nouveau script", "fas-plus", "hero-button");
        create.setOnAction(event -> context.navigator().openEditor(new Script()));
        Button library = UiFactory.button("Bibliothèque", "fas-folder-open", "hero-button-outline");
        library.setOnAction(event -> context.navigator().showLibrary(""));

        HBox actions = new HBox(10, create, library);
        actions.setPadding(new Insets(10, 0, 0, 0));
        VBox texts = new VBox(6, greeting, summary, actions);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label icon = new Label();
        icon.setGraphic(UiFactory.icon("fas-terminal"));
        icon.getStyleClass().add("hero-icon");

        HBox hero = new HBox(texts, spacer, icon);
        hero.getStyleClass().add("hero");
        hero.setAlignment(Pos.CENTER_LEFT);
        return hero;
    }

    /** Salutation selon l'heure : un détail, mais qui rend l'application plus chaleureuse. */
    private static String greeting() {
        int hour = LocalTime.now().getHour();
        return hour < 12 ? "Bonjour" : hour < 18 ? "Bon après-midi" : "Bonsoir";
    }

    // ------------------------------------------------------------------ Indicateurs

    private GridPane kpis() {
        List<Script> scripts = context.scripts;
        long favorites = scripts.stream().filter(Script::isFavorite).count();
        long categories = scripts.stream().map(Script::getCategory).distinct().count();
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(18);
        List<Node> cards = List.of(
                kpiCard("Scripts", scripts.size(), "fas-folder-open", "kpi-blue"),
                kpiCard("Favoris", favorites, "fas-star", "kpi-amber"),
                kpiCard("Exécutions aujourd'hui", context.historyRepository.countToday(), "fas-play", "kpi-green"),
                kpiCard("Échecs", context.historyRepository.countFailures(), "fas-times-circle", "kpi-red"),
                kpiCard("Catégories", categories, "fas-tags", "kpi-violet"),
                kpiCard("Historique", context.history.size(), "fas-history", "kpi-cyan")
        );
        for (int index = 0; index < 3; index++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / 3);
            grid.getColumnConstraints().add(column);
        }
        for (int index = 0; index < cards.size(); index++) {
            grid.add(cards.get(index), index % 3, index / 3);
        }
        return grid;
    }

    /**
     * Carte d'indicateur : pastille d'icône colorée, titre et valeur animée.
     *
     * @param accent classe CSS de la couleur (kpi-blue, kpi-green…)
     */
    private VBox kpiCard(String title, long value, String iconLiteral, String accent) {
        StackPane badge = new StackPane(UiFactory.icon(iconLiteral));
        badge.getStyleClass().add("kpi-icon");
        Label number = label("0", "kpi-value");
        Animations.countUp(number, value);
        VBox texts = new VBox(2, label(title, "kpi-title"), number);
        HBox content = new HBox(16, badge, texts);
        content.setAlignment(Pos.CENTER_LEFT);

        VBox card = UiFactory.card();
        card.getStyleClass().addAll("kpi-card", accent);
        card.getChildren().add(content);
        card.setMaxWidth(Double.MAX_VALUE);
        Animations.hoverScale(card, 1.02);
        return card;
    }

    // ------------------------------------------------------------------ Cartes

    private static GridPane twoColumns(Node left, Node right) {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        for (int index = 0; index < 2; index++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            grid.getColumnConstraints().add(column);
        }
        grid.add(left, 0, 0);
        grid.add(right, 1, 0);
        GridPane.setVgrow(left, Priority.ALWAYS);
        GridPane.setVgrow(right, Priority.ALWAYS);
        return grid;
    }

    private VBox activityCard() {
        VBox card = titledCard("Activité récente", "fas-bolt");
        List<ExecutionRecord> recentRecords = context.historyRepository.findRecent(7);
        if (recentRecords.isEmpty()) {
            card.getChildren().add(label("Aucune exécution enregistrée.", "muted-label"));
        }
        recentRecords.forEach(record -> {
            Label status = UiFactory.badge(record.getStatus().getDisplayName(),
                    ExecutionFormatter.badgeClass(record.getStatus()));
            status.setMinWidth(Region.USE_PREF_SIZE);
            Label name = label(record.getScriptName(), "row-title");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(10, status, name, spacer,
                    label(DateFormatter.format(record.getStartedAt()), "muted-label"));
            row.getStyleClass().add("list-row");
            row.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(row);
        });
        return card;
    }

    /** Une barre colorée par langage, proportionnelle au nombre de scripts. */
    private VBox languagesCard() {
        VBox card = titledCard("Scripts par langage", "fas-chart-bar");
        Map<ScriptLanguage, Long> counts = new EnumMap<>(ScriptLanguage.class);
        context.scripts.forEach(script -> counts.merge(script.getLanguage(), 1L, Long::sum));
        long max = counts.values().stream().mapToLong(Long::longValue).max().orElse(1);
        int index = 0;
        for (ScriptLanguage language : ScriptLanguage.values()) {
            long count = counts.getOrDefault(language, 0L);
            Region bar = new Region();
            bar.getStyleClass().addAll("language-bar", UiFactory.languageClass(language));
            bar.setMinHeight(12);
            Animations.growWidth(bar, Math.max(6, BAR_WIDTH * count / max), Duration.millis(200 + 80L * index++));
            Label name = label(language.getDisplayName(), "row-title");
            name.setMinWidth(92);
            HBox row = new HBox(12, name, bar, label(String.valueOf(count), "muted-label"));
            row.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(row);
        }
        return card;
    }

    /** Taux de réussite sur tout l'historique, avec une barre segmentée par statut. */
    private VBox successCard() {
        VBox card = titledCard("Taux de réussite", "fas-check-circle");
        Map<ExecutionStatus, Long> counts = new EnumMap<>(ExecutionStatus.class);
        context.history.forEach(record -> counts.merge(record.getStatus(), 1L, Long::sum));
        long total = context.history.size();
        long success = counts.getOrDefault(ExecutionStatus.SUCCESS, 0L);
        Label rate = label(total == 0 ? "—" : Math.round(100.0 * success / total) + " %", "success-rate");

        HBox segments = new HBox();
        segments.getStyleClass().add("segment-track");
        HBox legend = new HBox(16);
        for (ExecutionStatus status : ExecutionStatus.values()) {
            long count = counts.getOrDefault(status, 0L);
            if (count == 0) {
                continue;
            }
            Region segment = new Region();
            segment.getStyleClass().addAll("segment", ExecutionFormatter.badgeClass(status));
            segment.setMinHeight(14);
            // Chaque segment occupe la part de la piste correspondant à son nombre d'exécutions
            // (la piste a 6 px de marge intérieure et 3 px d'espace entre segments).
            double share = (double) count / total;
            segment.prefWidthProperty().bind(segments.widthProperty().subtract(6 + 3 * (counts.size() - 1)).multiply(share));
            segment.setMinWidth(8);
            segments.getChildren().add(segment);
            legend.getChildren().add(UiFactory.badge(status.getDisplayName() + " · " + count,
                    ExecutionFormatter.badgeClass(status)));
        }
        card.getChildren().addAll(rate, label("des " + total + " exécutions enregistrées", "muted-label"),
                segments, legend);
        return card;
    }

    private VBox recentScriptsCard() {
        VBox card = titledCard("Derniers scripts", "fas-clock");
        if (context.scripts.isEmpty()) {
            card.getChildren().add(label("Aucun script pour l'instant.", "muted-label"));
        }
        context.scripts.stream().limit(5).forEach(script -> {
            Label name = label(script.getName(), "row-title");
            HBox meta = new HBox(8, UiFactory.languageBadge(script.getLanguage()),
                    label(script.getCategory(), "muted-label"));
            meta.setAlignment(Pos.CENTER_LEFT);
            Button open = UiFactory.button("Ouvrir", "fas-code", "ghost-button");
            open.setOnAction(event -> context.navigator().openEditor(script));
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(12, new VBox(4, name, meta), spacer, open);
            row.getStyleClass().add("list-row");
            row.setAlignment(Pos.CENTER_LEFT);
            card.getChildren().add(row);
        });
        return card;
    }

    private static VBox titledCard(String title, String iconLiteral) {
        VBox card = UiFactory.card();
        HBox header = new HBox(10, UiFactory.icon(iconLiteral), UiFactory.title(title));
        header.getStyleClass().add("card-header");
        header.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().add(header);
        card.setMaxHeight(Double.MAX_VALUE);
        return card;
    }
}
