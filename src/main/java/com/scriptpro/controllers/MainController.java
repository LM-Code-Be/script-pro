package com.scriptpro.controllers;

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
import javafx.scene.Parent;

/**
 * Point d'entrée de l'interface : construit la fenêtre, déclare le menu et assure
 * la navigation entre les écrans. Chaque écran vit dans sa propre classe ({@code *View}).
 *
 * @author LM-Code
 */
public final class MainController implements ViewContext.Navigator {
    private final ViewContext context;

    private final DashboardView dashboardView;
    private final LibraryView libraryView;
    private final EditorView editorView;
    private final ExecutionsView executionsView;
    private final FavoritesView favoritesView;
    private final TemplatesView templatesView;
    private final VariablesView variablesView;
    private final HistoryView historyView;
    private final DocumentationView documentationView;
    private final SettingsView settingsView;

    public MainController(ScriptService scriptService,
                          ExecutionService executionService,
                          TemplateService templateService,
                          VariableService variableService,
                          SettingsService settingsService,
                          SecurityService securityService,
                          ImportExportService importExportService,
                          ExecutionHistoryRepository historyRepository,
                          InterpreterLocator interpreterLocator,
                          BackupService backupService) {
        context = new ViewContext(new MainWindow(), scriptService, executionService, templateService,
                variableService, settingsService, securityService, importExportService, historyRepository,
                interpreterLocator, backupService);
        context.setNavigator(this);

        FileActions fileActions = new FileActions(context);
        dashboardView = new DashboardView(context);
        libraryView = new LibraryView(context, fileActions);
        editorView = new EditorView(context, fileActions);
        executionsView = new ExecutionsView(context, fileActions);
        favoritesView = new FavoritesView(context);
        templatesView = new TemplatesView(context);
        variablesView = new VariablesView(context);
        historyView = new HistoryView(context, fileActions);
        documentationView = new DocumentationView(context);
        settingsView = new SettingsView(context);
    }

    public Parent createView() {
        MainWindow window = context.window();
        window.addNavigation("dashboard", "Dashboard", "fas-tachometer-alt", dashboardView::show);
        window.addNavigation("library", "Bibliothèque", "fas-folder-open", () -> libraryView.show(window.getSearchField().getText()));
        window.addNavigation("editor", "Éditeur", "fas-code", editorView::show);
        window.addNavigation("executions", "Exécutions", "fas-terminal", executionsView::show);
        window.addNavigation("favorites", "Favoris", "fas-star", favoritesView::show);
        window.addNavigation("templates", "Modèles", "fas-layer-group", templatesView::show);
        window.addNavigation("variables", "Variables", "fas-sliders-h", variablesView::show);
        window.addNavigation("history", "Historique", "fas-history", historyView::show);
        window.addNavigation("documentation", "Documentation", "fas-book-open", documentationView::show);
        window.addNavigation("settings", "Paramètres", "fas-cog", settingsView::show);
        window.getSearchField().setOnAction(event -> showLibrary(window.getSearchField().getText()));
        window.getThemeButton().setOnAction(event -> toggleTheme());
        window.setDarkMode(context.settingsService.isDarkTheme());
        context.refreshData();
        dashboardView.show();
        return window.getRoot();
    }

    /** Vrai si un script est en cours d'exécution (utilisé pour prévenir avant de quitter). */
    @Override
    public boolean isExecutionRunning() {
        return editorView.isRunning();
    }

    /** Arrête l'exécution en cours, par exemple à la fermeture de l'application. */
    public void stopRunningExecution() {
        editorView.stopExecution();
    }

    @Override
    public void openEditor(Script script) {
        editorView.open(script);
    }

    @Override
    public void runInEditor(Script script) {
        editorView.openAndRun(script);
    }

    @Override
    public void showLibrary(String query) {
        libraryView.show(query);
    }

    private void toggleTheme() {
        boolean dark = !context.settingsService.isDarkTheme();
        context.settingsService.setDarkTheme(dark);
        context.window().setDarkMode(dark);
    }
}
