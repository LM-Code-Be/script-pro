package com.scriptpro.app;

import com.scriptpro.controllers.MainController;
import com.scriptpro.database.DatabaseManager;
import com.scriptpro.repositories.CategoryRepository;
import com.scriptpro.repositories.ExecutionHistoryRepository;
import com.scriptpro.repositories.ScriptRepository;
import com.scriptpro.repositories.SettingsRepository;
import com.scriptpro.repositories.VariableRepository;
import com.scriptpro.services.ExecutionService;
import com.scriptpro.services.ImportExportService;
import com.scriptpro.services.ScriptService;
import com.scriptpro.services.SecurityService;
import com.scriptpro.services.SettingsService;
import com.scriptpro.services.TemplateService;
import com.scriptpro.services.VariableService;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public final class ScriptProApplication extends Application {
    private ExecutionService executionService;

    @Override
    public void start(Stage stage) {
        DatabaseManager databaseManager = new DatabaseManager();
        databaseManager.initialize();

        ScriptRepository scriptRepository = new ScriptRepository(databaseManager);
        CategoryRepository categoryRepository = new CategoryRepository(databaseManager);
        ExecutionHistoryRepository historyRepository = new ExecutionHistoryRepository(databaseManager);
        VariableRepository variableRepository = new VariableRepository(databaseManager);
        SettingsRepository settingsRepository = new SettingsRepository(databaseManager);

        ScriptService scriptService = new ScriptService(scriptRepository, categoryRepository);
        VariableService variableService = new VariableService(variableRepository);
        SettingsService settingsService = new SettingsService(settingsRepository);
        SecurityService securityService = new SecurityService();
        TemplateService templateService = new TemplateService();
        ImportExportService importExportService = new ImportExportService(scriptService);
        executionService = new ExecutionService(historyRepository, variableService, settingsService);

        MainController controller = new MainController(
                scriptService,
                executionService,
                templateService,
                variableService,
                settingsService,
                securityService,
                importExportService,
                historyRepository
        );

        Parent root = controller.createView();
        Scene scene = new Scene(root, 1440, 900);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

        stage.setTitle("ScriptPro");
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/images/scriptpro-icon.png")));
        stage.setMinWidth(1180);
        stage.setMinHeight(760);
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (executionService != null) {
            executionService.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
