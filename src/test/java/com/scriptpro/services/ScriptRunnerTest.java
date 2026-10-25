package com.scriptpro.services;

import com.scriptpro.models.ScriptLanguage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'intégration du lanceur de scripts : ils exécutent de vrais processus Windows.
 *
 * @author LM-Code
 */
@EnabledOnOs(OS.WINDOWS)
class ScriptRunnerTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    @TempDir
    Path workingDirectory;

    private final ScriptRunner runner = new ScriptRunner(new InterpreterLocator());

    private ScriptRunner.Result run(ScriptLanguage language, String content) {
        return runner.prepare(language, content, workingDirectory, TIMEOUT, null).run();
    }

    @Test
    void powershellKeepsAccentsInScriptAndOutput() {
        ScriptRunner.Result result = run(ScriptLanguage.POWERSHELL, "Write-Output 'Été à Liège : ça marche'");
        assertEquals(ScriptRunner.Outcome.COMPLETED, result.outcome());
        assertEquals(0, result.exitCode());
        assertEquals("Été à Liège : ça marche", result.stdout().strip());
    }

    @Test
    void powershellPropagatesExitCode() {
        assertEquals(3, run(ScriptLanguage.POWERSHELL, "Write-Output 'avant'\nexit 3").exitCode());
    }

    @Test
    void powershellUncaughtErrorIsAFailure() {
        ScriptRunner.Result result = run(ScriptLanguage.POWERSHELL, "throw 'boum'");
        assertNotEquals(0, result.exitCode());
        assertTrue(result.stderr().contains("boum"), result.stderr());
    }

    @Test
    void cmdKeepsAccentsAndExitCode() {
        ScriptRunner.Result result = run(ScriptLanguage.CMD, "@echo off\necho Café crème\nexit /b 2");
        assertEquals("Café crème", result.stdout().strip());
        assertEquals(2, result.exitCode());
    }

    @Test
    void batchLabelsWorkWithUnixLineEndings() {
        // Les fins de ligne LF seules cassent GOTO dans cmd.exe : ScriptPro les convertit en CRLF.
        ScriptRunner.Result result = run(ScriptLanguage.BATCH, "@echo off\ngoto fin\necho jamais\n:fin\necho ok");
        assertEquals("ok", result.stdout().strip());
    }

    @Test
    void largeOutputIsNotTruncated() {
        ScriptRunner.Result result = run(ScriptLanguage.POWERSHELL, "1..20000 | ForEach-Object { \"ligne $_\" }");
        assertEquals(20000, result.stdout().lines().count());
        assertTrue(result.stdout().strip().endsWith("ligne 20000"));
    }

    @Test
    void liveOutputIsDelivered() {
        StringBuilder live = new StringBuilder();
        runner.prepare(ScriptLanguage.POWERSHELL, "Write-Output 'un'; Write-Output 'deux'", workingDirectory, TIMEOUT,
                (text, error) -> {
                    synchronized (live) {
                        live.append(text);
                    }
                }).run();
        assertTrue(live.toString().contains("un\n") && live.toString().contains("deux"), live.toString());
    }

    @Test
    void timeoutKillsTheScript() {
        long start = System.nanoTime();
        ScriptRunner.Result result = runner.prepare(ScriptLanguage.POWERSHELL, "Start-Sleep -Seconds 60",
                workingDirectory, Duration.ofSeconds(2), null).run();
        assertEquals(ScriptRunner.Outcome.TIMEOUT, result.outcome());
        assertTrue(result.message().contains("Timeout"));
        assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < 20);
    }

    @Test
    void cancelStopsTheScriptAndItsChildren() throws Exception {
        ScriptRunner.Execution execution = runner.prepare(ScriptLanguage.CMD, "ping -n 60 127.0.0.1",
                workingDirectory, TIMEOUT, null);
        CompletableFuture<ScriptRunner.Result> future = CompletableFuture.supplyAsync(execution::run);
        Thread.sleep(1500);
        execution.cancel();
        ScriptRunner.Result result = future.get(20, TimeUnit.SECONDS);
        assertEquals(ScriptRunner.Outcome.CANCELLED, result.outcome());
    }

    @Test
    void scriptRunsInConfiguredWorkingDirectory() throws Exception {
        ScriptRunner.Result result = run(ScriptLanguage.POWERSHELL, "(Get-Location).Path");
        assertEquals(workingDirectory.toRealPath().toString().toLowerCase(), result.stdout().strip().toLowerCase());
    }

    @Test
    void missingInterpreterGivesClearMessage() {
        // PATH vide et aucune variable système : aucun interpréteur ne peut être trouvé.
        ScriptRunner emptyRunner = new ScriptRunner(new InterpreterLocator(Map.of(), true));
        ScriptRunner.Result result = emptyRunner.prepare(ScriptLanguage.PYTHON, "print('x')", workingDirectory, TIMEOUT, null).run();
        assertEquals(ScriptRunner.Outcome.NOT_STARTED, result.outcome());
        assertTrue(result.message().contains("Python est introuvable"));
    }
}
