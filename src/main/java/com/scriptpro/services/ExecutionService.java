package com.scriptpro.services;

import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.models.ExecutionStatus;
import com.scriptpro.models.Script;
import com.scriptpro.models.ScriptLanguage;
import com.scriptpro.repositories.ExecutionHistoryRepository;
import javafx.concurrent.Task;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class ExecutionService {
    private final ExecutionHistoryRepository historyRepository;
    private final VariableService variableService;
    private final SettingsService settingsService;
    private final ExecutorService executorService = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "scriptpro-execution");
        thread.setDaemon(true);
        return thread;
    });

    public ExecutionService(ExecutionHistoryRepository historyRepository,
                            VariableService variableService,
                            SettingsService settingsService) {
        this.historyRepository = historyRepository;
        this.variableService = variableService;
        this.settingsService = settingsService;
    }

    public Task<ExecutionRecord> executeAsync(Script script, Map<String, String> variableValues) {
        Task<ExecutionRecord> task = new Task<>() {
            @Override
            protected ExecutionRecord call() {
                return execute(script, variableValues);
            }
        };
        executorService.submit(task);
        return task;
    }

    public void shutdown() {
        executorService.shutdownNow();
    }

    private ExecutionRecord execute(Script script, Map<String, String> variableValues) {
        Instant startedAt = Instant.now();
        Path tempFile = null;
        try {
            String content = variableService.applyVariables(script.getContent(), variableValues);
            tempFile = Files.createTempFile("scriptpro-", script.getLanguage().getExtension());
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);
            List<String> command = commandFor(script.getLanguage(), tempFile);
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.directory(Path.of(".").toAbsolutePath().toFile());
            Process process = processBuilder.start();

            Future<String> stdout = executorService.submit(readStream(process.getInputStream()));
            Future<String> stderr = executorService.submit(readStream(process.getErrorStream()));

            boolean completed = process.waitFor(settingsService.executionTimeoutSeconds(), TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                ExecutionRecord timeoutRecord = record(script, startedAt, ExecutionStatus.FAILURE, -1,
                        output(stdout), "Timeout d'exécution après " + settingsService.executionTimeoutSeconds() + " secondes.");
                return historyRepository.save(timeoutRecord);
            }

            int exitCode = process.exitValue();
            ExecutionStatus status = exitCode == 0 ? ExecutionStatus.SUCCESS : ExecutionStatus.FAILURE;
            ExecutionRecord record = record(script, startedAt, status, exitCode, output(stdout), output(stderr));
            return historyRepository.save(record);
        } catch (Exception exception) {
            ExecutionRecord record = record(script, startedAt, ExecutionStatus.FAILURE, -1, "", exception.getMessage());
            return historyRepository.save(record);
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private List<String> commandFor(ScriptLanguage language, Path file) {
        List<String> command = new ArrayList<>();
        switch (language) {
            case POWERSHELL -> {
                command.add("powershell.exe");
                command.add("-NoProfile");
                command.add("-ExecutionPolicy");
                command.add("Bypass");
                command.add("-File");
                command.add(file.toAbsolutePath().toString());
            }
            case CMD, BATCH -> {
                command.add("cmd.exe");
                command.add("/c");
                command.add(file.toAbsolutePath().toString());
            }
            case BASH -> {
                command.add("bash");
                command.add(file.toAbsolutePath().toString());
            }
            case PYTHON -> {
                command.add("python");
                command.add(file.toAbsolutePath().toString());
            }
        }
        return command;
    }

    private Callable<String> readStream(InputStream inputStream) {
        return () -> new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }

    private String output(Future<String> future) {
        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception exception) {
            return "";
        }
    }

    private ExecutionRecord record(Script script, Instant startedAt, ExecutionStatus status, int exitCode,
                                   String stdout, String stderr) {
        return new ExecutionRecord(
                null,
                script.getId(),
                script.getName(),
                script.getLanguage(),
                status,
                startedAt,
                Duration.between(startedAt, Instant.now()).toMillis(),
                exitCode,
                stdout,
                stderr,
                System.getProperty("user.name", "Administrateur")
        );
    }
}
