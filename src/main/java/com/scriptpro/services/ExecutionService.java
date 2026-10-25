package com.scriptpro.services;

import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.models.ExecutionStatus;
import com.scriptpro.models.Script;
import com.scriptpro.repositories.ExecutionHistoryRepository;
import javafx.concurrent.Task;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestration d'une exécution depuis l'interface : substitution des variables, lancement
 * en arrière-plan via {@link ScriptRunner}, masquage des valeurs sensibles et enregistrement
 * du résultat dans l'historique.
 *
 * @author LM-Code
 */
public final class ExecutionService {
    private final ExecutionHistoryRepository historyRepository;
    private final VariableService variableService;
    private final SettingsService settingsService;
    private final ScriptRunner scriptRunner;
    private final ExecutorService executorService = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "scriptpro-execution");
        thread.setDaemon(true);
        return thread;
    });

    public ExecutionService(ExecutionHistoryRepository historyRepository,
                            VariableService variableService,
                            SettingsService settingsService,
                            ScriptRunner scriptRunner) {
        this.historyRepository = historyRepository;
        this.variableService = variableService;
        this.settingsService = settingsService;
        this.scriptRunner = scriptRunner;
    }

    /**
     * Exécution en cours : la tâche JavaFX fournit le résultat final, {@link #cancel()} arrête le script.
     */
    public static final class ExecutionHandle {
        private final Task<ExecutionRecord> task;
        private final ScriptRunner.Execution execution;

        private ExecutionHandle(Task<ExecutionRecord> task, ScriptRunner.Execution execution) {
            this.task = task;
            this.execution = execution;
        }

        public Task<ExecutionRecord> task() {
            return task;
        }

        public void cancel() {
            execution.cancel();
        }
    }

    /**
     * Lance le script en arrière-plan.
     *
     * @param variableValues valeurs saisies pour les variables {@code {{NOM}}}
     * @param liveOutput     reçoit la sortie au fil de l'eau, déjà masquée ; appelé hors du thread JavaFX
     */
    public ExecutionHandle executeAsync(Script script, Map<String, String> variableValues,
                                        ScriptRunner.OutputListener liveOutput) {
        String content = variableService.applyVariables(script.getContent(), variableValues);
        List<String> secrets = variableService.secretValues(variableValues);
        ScriptRunner.OutputListener maskedOutput = (text, error) ->
                liveOutput.onOutput(VariableService.mask(text, secrets), error);

        ScriptRunner.Execution execution = scriptRunner.prepare(
                script.getLanguage(),
                content,
                settingsService.workingDirectory(),
                Duration.ofSeconds(settingsService.executionTimeoutSeconds()),
                maskedOutput);

        Task<ExecutionRecord> task = new Task<>() {
            @Override
            protected ExecutionRecord call() {
                Instant startedAt = Instant.now();
                ScriptRunner.Result result = execution.run();
                if (!result.message().isBlank()) {
                    // Message de ScriptPro (timeout, arrêt, interpréteur absent…) visible aussi en direct.
                    maskedOutput.onOutput("\n" + result.message() + "\n", true);
                }
                return historyRepository.save(toRecord(script, startedAt, result, secrets));
            }
        };
        executorService.submit(task);
        return new ExecutionHandle(task, execution);
    }

    public void shutdown() {
        executorService.shutdownNow();
    }

    private ExecutionRecord toRecord(Script script, Instant startedAt, ScriptRunner.Result result, List<String> secrets) {
        ExecutionStatus status = switch (result.outcome()) {
            case COMPLETED -> result.exitCode() == 0 ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILURE;
            case CANCELLED -> ExecutionStatus.CANCELLED;
            case TIMEOUT, NOT_STARTED -> ExecutionStatus.FAILURE;
        };
        // Le message de ScriptPro (timeout, interpréteur absent…) est ajouté à la fin des erreurs.
        String stderr = result.stderr();
        if (!result.message().isBlank()) {
            stderr = stderr.isBlank() ? result.message() : stderr.stripTrailing() + "\n\n" + result.message();
        }
        return new ExecutionRecord(
                null,
                script.getId(),
                script.getName(),
                script.getLanguage(),
                status,
                startedAt,
                Duration.between(startedAt, Instant.now()).toMillis(),
                result.exitCode(),
                VariableService.mask(result.stdout(), secrets),
                VariableService.mask(stderr, secrets),
                System.getProperty("user.name", "Administrateur")
        );
    }
}
