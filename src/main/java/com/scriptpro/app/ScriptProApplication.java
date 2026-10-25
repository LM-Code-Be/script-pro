package com.scriptpro.app;

import com.scriptpro.controllers.MainController;
import com.scriptpro.database.DatabaseManager;
import com.scriptpro.repositories.CategoryRepository;
import com.scriptpro.repositories.ExecutionHistoryRepository;
import com.scriptpro.repositories.ScriptRepository;
import com.scriptpro.repositories.SettingsRepository;
import com.scriptpro.repositories.VariableRepository;
import com.scriptpro.services.BackupService;
import com.scriptpro.services.ExecutionService;
import com.scriptpro.services.ImportExportService;
import com.scriptpro.services.InterpreterLocator;
import com.scriptpro.services.ScriptRunner;
import com.scriptpro.services.ScriptService;
import com.scriptpro.services.SecurityService;
import com.scriptpro.services.SettingsService;
import com.scriptpro.services.TemplateService;
import com.scriptpro.services.VariableService;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * Point d'entrée JavaFX : assemble la base, les dépôts, les services et l'interface.
 *
 * @author LM-Code
 */
public final class ScriptProApplication extends Application {
    private ExecutionService executionService;
    private MainController controller;

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
        SecurityService securityService = new SecurityService(settingsService::customSecurityPatterns);
        TemplateService templateService = new TemplateService();
        ImportExportService importExportService = new ImportExportService(scriptService);
        InterpreterLocator interpreterLocator = new InterpreterLocator();
        BackupService backupService = new BackupService(databaseManager);
        executionService = new ExecutionService(historyRepository, variableService, settingsService,
                new ScriptRunner(interpreterLocator));

        controller = new MainController(
                scriptService,
                executionService,
                templateService,
                variableService,
                settingsService,
                securityService,
                importExportService,
                historyRepository,
                interpreterLocator,
                backupService
        );

        Parent root = controller.createView();
        Scene scene = new Scene(root, 1440, 900);
        scene.getStylesheets().add(getClass().getResource("/styles/app.css").toExternalForm());

        stage.setTitle("ScriptPro");
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/images/scriptpro-icon.png")));
        stage.setMinWidth(1180);
        stage.setMinHeight(760);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            if (!confirmCloseWhileRunning()) {
                event.consume();
            }
        });
        stage.show();
    }

    /**
     * Si un script tourne encore, demande confirmation avant de quitter, puis l'arrête
     * (sinon le processus continuerait en arrière-plan sans être journalisé).
     *
     * @return vrai si la fermeture peut continuer
     */
    private boolean confirmCloseWhileRunning() {
        if (controller == null || !controller.isExecutionRunning()) {
            return true;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Un script est en cours d'exécution. Quitter ScriptPro l'arrêtera immédiatement.");
        alert.setTitle("Exécution en cours");
        alert.setHeaderText("Quitter ScriptPro ?");
        boolean quit = alert.showAndWait().filter(button -> button == ButtonType.OK).isPresent();
        if (quit) {
            controller.stopRunningExecution();
        }
        return quit;
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.stopRunningExecution();
        }
        if (executionService != null) {
            executionService.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
