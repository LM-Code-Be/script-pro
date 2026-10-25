package com.scriptpro.controllers;

import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.models.Script;
import com.scriptpro.ui.UiFactory;
import com.scriptpro.utils.DateFormatter;
import com.scriptpro.utils.ExecutionFormatter;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

import static com.scriptpro.ui.UiFactory.label;

/**
 * Écran d'accueil : indicateurs clés, activité récente et derniers scripts modifiés.
 *
 * @author LM-Code
 */
final class DashboardView {
    private final ViewContext context;

    DashboardView(ViewContext context) {
        this.context = context;
    }

    void show() {
        context.refreshData();
        VBox page = new VBox(22);
        page.getChildren().addAll(kpis(), content());
        context.setView("dashboard", "Dashboard", "Vue opérationnelle des scripts, favoris et exécutions", page);
    }

    private GridPane kpis() {
        List<Script> scripts = context.scripts;
        long favorites = scripts.stream().filter(Script::isFavorite).count();
        long categories = scripts.stream().map(Script::getCategory).distinct().count();
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(18);
        List<Node> cards = List.of(
                kpiCard("Scripts", String.valueOf(scripts.size()), "fas-folder-open"),
                kpiCard("Favoris", String.valueOf(favorites), "fas-star"),
                kpiCard("Exécutions aujourd'hui", String.valueOf(context.historyRepository.countToday()), "fas-play"),
                kpiCard("Échecs", String.valueOf(context.historyRepository.countFailures()), "fas-times-circle"),
                kpiCard("Catégories", String.valueOf(categories), "fas-tags"),
                kpiCard("Historique", String.valueOf(context.history.size()), "fas-history")
        );
        for (int index = 0; index < cards.size(); index++) {
            Node card = cards.get(index);
            GridPane.setHgrow(card, Priority.ALWAYS);
            grid.add(card, index % 3, index / 3);
        }
        return grid;
    }

    private VBox kpiCard(String title, String value, String iconLiteral) {
        VBox card = UiFactory.card();
        HBox header = new HBox(8, UiFactory.icon(iconLiteral), label(title, "kpi-title"));
        header.setAlignment(Pos.CENTER_LEFT);
        card.getChildren().addAll(header, label(value, "kpi-value"));
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private HBox content() {
        VBox activity = UiFactory.card();
        activity.getChildren().add(UiFactory.title("Activité récente"));
        List<ExecutionRecord> recentRecords = context.historyRepository.findRecent(7);
        if (recentRecords.isEmpty()) {
            activity.getChildren().add(label("Aucune exécution enregistrée.", "muted-label"));
        } else {
            recentRecords.forEach(record -> activity.getChildren().add(executionRow(record)));
        }

        VBox recentScripts = UiFactory.card();
        recentScripts.getChildren().add(UiFactory.title("Derniers scripts utilisés"));
        context.scripts.stream().limit(7).forEach(script -> recentScripts.getChildren().add(scriptRow(script)));

        HBox content = new HBox(18, activity, recentScripts);
        HBox.setHgrow(activity, Priority.ALWAYS);
        HBox.setHgrow(recentScripts, Priority.ALWAYS);
        return content;
    }

    private HBox executionRow(ExecutionRecord record) {
        Label status = label(record.getStatus().getDisplayName(), ExecutionFormatter.statusClass(record.getStatus()));
        Label text = label(record.getScriptName() + " · " + DateFormatter.format(record.getStartedAt()), "muted-label");
        HBox row = new HBox(10, status, text);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox scriptRow(Script script) {
        Label name = label(script.getName(), "section-title");
        name.setStyle("-fx-font-size: 14px;");
        Label meta = label(script.getLanguage().getDisplayName() + " · " + script.getCategory(), "muted-label");
        Button open = UiFactory.button("Ouvrir", "fas-code", "ghost-button");
        open.setOnAction(event -> context.navigator().openEditor(script));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox row = new HBox(12, new VBox(2, name, meta), spacer, open);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
