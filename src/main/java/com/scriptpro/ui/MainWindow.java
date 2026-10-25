package com.scriptpro.ui;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MainWindow {
    private final BorderPane root = new BorderPane();
    private final VBox sidebar = new VBox(8);
    private final StackPane contentPane = new StackPane();
    private final TextField searchField = new TextField();
    private final Button themeButton = UiFactory.button("Thème", "fas-moon", "ghost-button");
    private final Label titleLabel = new Label();
    private final Label subtitleLabel = new Label();
    private final Label quickStatsLabel = new Label();
    private final Label footerStatusLabel = new Label("Base locale SQLite prête");
    private final Map<String, Button> navigationButtons = new LinkedHashMap<>();

    public MainWindow() {
        root.getStyleClass().addAll("app-root", "light");
        root.setLeft(buildSidebar());
        root.setTop(buildTopbar());
        root.setCenter(contentPane);
        root.setBottom(buildFooter());
    }

    public Parent getRoot() {
        return root;
    }

    public TextField getSearchField() {
        return searchField;
    }

    public Button getThemeButton() {
        return themeButton;
    }

    public void setQuickStats(String text) {
        quickStatsLabel.setText(text);
    }

    public void setFooterStatus(String text) {
        footerStatusLabel.setText(text);
    }

    public void addNavigation(String key, String text, String iconLiteral, Runnable action) {
        Button button = UiFactory.button(text, iconLiteral, "nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setOnAction(event -> action.run());
        navigationButtons.put(key, button);
        sidebar.getChildren().add(button);
    }

    public void setActiveNavigation(String key) {
        navigationButtons.forEach((navigationKey, button) -> button.getStyleClass().remove("active"));
        Button activeButton = navigationButtons.get(key);
        if (activeButton != null && !activeButton.getStyleClass().contains("active")) {
            activeButton.getStyleClass().add("active");
        }
    }

    public void setContent(String title, String subtitle, Node content) {
        titleLabel.setText(title);
        subtitleLabel.setText(subtitle);
        content.setOpacity(0);
        contentPane.getChildren().setAll(content);
        FadeTransition transition = new FadeTransition(Duration.millis(180), content);
        transition.setFromValue(0);
        transition.setToValue(1);
        transition.play();
    }

    public void setDarkMode(boolean darkMode) {
        root.getStyleClass().removeAll("light", "dark");
        root.getStyleClass().add(darkMode ? "dark" : "light");
        themeButton.setText(darkMode ? "Clair" : "Sombre");
    }

    private VBox buildSidebar() {
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(22, 16, 22, 16));
        sidebar.setPrefWidth(250);

        ImageView logo = new ImageView(new Image(MainWindow.class.getResourceAsStream("/images/scriptpro-icon.png")));
        logo.setFitWidth(48);
        logo.setFitHeight(48);
        logo.setPreserveRatio(true);
        logo.getStyleClass().add("brand-logo");
        Label brand = new Label("ScriptPro");
        brand.getStyleClass().add("brand");
        Label baseline = new Label("Gestionnaire de scripts IT");
        baseline.getStyleClass().add("brand-subtitle");
        VBox brandBox = new VBox(2, brand, baseline);
        HBox brandHeader = new HBox(12, logo, brandBox);
        brandHeader.setAlignment(Pos.CENTER_LEFT);
        brandBox.getStyleClass().add("brand-box");
        sidebar.getChildren().add(brandHeader);
        return sidebar;
    }

    private HBox buildTopbar() {
        HBox topbar = new HBox(16);
        topbar.getStyleClass().add("topbar");
        topbar.setAlignment(Pos.CENTER_LEFT);
        topbar.setPadding(new Insets(18, 24, 18, 24));

        VBox titles = new VBox(3, titleLabel, subtitleLabel);
        titleLabel.getStyleClass().add("page-title");
        subtitleLabel.getStyleClass().add("page-subtitle");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        searchField.setPromptText("Rechercher un script, une catégorie ou un contenu");
        searchField.getStyleClass().add("global-search");
        searchField.setPrefWidth(360);

        quickStatsLabel.getStyleClass().add("quick-stats");
        topbar.getChildren().addAll(titles, spacer, searchField, themeButton, quickStatsLabel);
        return topbar;
    }

    private HBox buildFooter() {
        HBox footer = new HBox(16);
        footer.getStyleClass().add("app-footer");
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(10, 18, 10, 18));

        Label copyright = new Label("© 2026 LM-Code — Développé par Michael — lm-code.be");
        copyright.getStyleClass().add("footer-copyright");
        footerStatusLabel.getStyleClass().add("footer-status");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        footer.getChildren().addAll(copyright, spacer, footerStatusLabel);
        return footer;
    }
}
