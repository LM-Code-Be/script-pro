package com.scriptpro.controllers;

import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.models.Script;
import com.scriptpro.repositories.ExecutionHistoryRepository;
import com.scriptpro.services.BackupService;
import com.scriptpro.services.ExecutionService;
import com.scriptpro.services.ImportExportService;
import com.scriptpro.services.InterpreterLocator;
import com.scriptpro.services.ScriptService;
import com.scriptpro.services.SecurityService;
import com.scriptpro.services.SettingsService;
import com.scriptpro.services.TemplateService;
import com.scriptpro.services.VariableService;
import com.scriptpro.ui.MainWindow;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollPane;
import javafx.stage.Window;
import org.controlsfx.control.Notifications;

/**
 * Tout ce dont un écran a besoin : services, fenêtre principale, données partagées
 * et petits utilitaires d'interface (confirmations, erreurs, notifications).
 *
 * <p>Les écrans ne se connaissent pas entre eux : pour passer de l'un à l'autre
 * (ex. « ouvrir ce script dans l'éditeur »), ils passent par le {@link Navigator}.</p>
 *
 * @author LM-Code
 */
public final class ViewContext {

    /** Navigation entre écrans, implémentée par {@link MainController}. */
    public interface Navigator {
        void openEditor(Script script);

        /** Ouvre le script dans l'éditeur puis lance immédiatement son exécution. */
        void runInEditor(Script script);

        void showLibrary(String query);

        /** Vrai si un script est en cours d'exécution dans l'éditeur. */
        boolean isExecutionRunning();
    }

    final ScriptService scriptService;
    final ExecutionService executionService;
    final TemplateService templateService;
    final VariableService variableService;
    final SettingsService settingsService;
    final SecurityService securityService;
    final ImportExportService importExportService;
    final ExecutionHistoryRepository historyRepository;
    final InterpreterLocator interpreterLocator;
    final BackupService backupService;

    /** Scripts actifs et historique, rechargés depuis la base par {@link #refreshData()}. */
    final ObservableList<Script> scripts = FXCollections.observableArrayList();
    final ObservableList<ExecutionRecord> history = FXCollections.observableArrayList();

    private final MainWindow window;
    private Navigator navigator;

    ViewContext(MainWindow window,
                ScriptService scriptService,
                ExecutionService executionService,
                TemplateService templateService,
                VariableService variableService,
                SettingsService settingsService,
                SecurityService securityService,
                ImportExportService importExportService,
                ExecutionHistoryRepository historyRepository,
                InterpreterLocator interpreterLocator,
                BackupService backupService) {
        this.window = window;
        this.scriptService = scriptService;
        this.executionService = executionService;
        this.templateService = templateService;
        this.variableService = variableService;
        this.settingsService = settingsService;
        this.securityService = securityService;
        this.importExportService = importExportService;
        this.historyRepository = historyRepository;
        this.interpreterLocator = interpreterLocator;
        this.backupService = backupService;
    }

    void setNavigator(Navigator navigator) {
        this.navigator = navigator;
    }

    Navigator navigator() {
        return navigator;
    }

    MainWindow window() {
        return window;
    }

    /** Fenêtre parente des boîtes de dialogue (choix de fichier, etc.). */
    Window owner() {
        return window.getRoot().getScene().getWindow();
    }

    void refreshData() {
        scripts.setAll(scriptService.findActiveScripts());
        history.setAll(historyRepository.findAll());
        updateQuickStats();
    }

    void updateQuickStats() {
        long favorites = scripts.stream().filter(Script::isFavorite).count();
        window.setQuickStats(scripts.size() + " scripts · " + favorites + " favoris · "
                + historyRepository.countToday() + " exécutions aujourd'hui");
        window.setFooterStatus("SQLite : scriptpro.db · " + scripts.size() + " scripts · " + history.size() + " événements");
    }

    /** Affiche un écran dans la zone centrale et met en surbrillance son entrée de menu. */
    void setView(String navigationKey, String title, String subtitle, Node content) {
        window.setActiveNavigation(navigationKey);
        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.getStyleClass().add("content-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        window.setContent(title, subtitle, scrollPane);
        updateQuickStats();
    }

    boolean confirm(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(content);
        return alert.showAndWait().filter(button -> button == ButtonType.OK).isPresent();
    }

    void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    void notifyInfo(String message) {
        if (settingsService.notificationsEnabled()) {
            Notifications.create()
                    .title("ScriptPro")
                    .text(message)
                    .hideAfter(javafx.util.Duration.seconds(3))
                    .showInformation();
        }
    }

    void notifyWarning(String message) {
        if (settingsService.notificationsEnabled()) {
            Notifications.create()
                    .title("ScriptPro")
                    .text(message)
                    .hideAfter(javafx.util.Duration.seconds(3))
                    .showWarning();
        }
    }
}
