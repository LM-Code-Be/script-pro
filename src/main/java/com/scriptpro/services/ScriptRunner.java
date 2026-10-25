package com.scriptpro.services;

import com.scriptpro.models.ScriptLanguage;
import com.scriptpro.utils.ConsoleCharsets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Lance un script dans un processus séparé et collecte sa sortie.
 *
 * <p>Cette classe ne dépend pas de JavaFX ni de la base de données : elle peut être testée
 * seule. Elle gère :</p>
 * <ul>
 *     <li>l'encodage (UTF-8 imposé à chaque interpréteur, avec repli OEM au décodage) ;</li>
 *     <li>la sortie en direct, transmise ligne par ligne à un {@link OutputListener} ;</li>
 *     <li>l'arrêt manuel ({@link Execution#cancel()}) et le timeout, qui tuent aussi les processus enfants ;</li>
 *     <li>l'absence d'interpréteur, signalée par un message clair au lieu d'une erreur système.</li>
 * </ul>
 *
 * @author LM-Code
 */
public final class ScriptRunner {
    /** Au-delà, la sortie n'est plus conservée (protection mémoire et base de données). */
    static final int MAX_CAPTURED_CHARS = 2_000_000;
    /** Temps laissé aux flux pour se vider après la fin du processus principal. */
    private static final long STREAM_DRAIN_SECONDS = 3;

    /** Reçoit la sortie au fil de l'eau. Appelé depuis un thread de lecture, jamais depuis JavaFX. */
    @FunctionalInterface
    public interface OutputListener {
        void onOutput(String text, boolean error);

        OutputListener NONE = (text, error) -> { };
    }

    public enum Outcome {
        /** Le processus s'est terminé seul ; voir le code retour. */
        COMPLETED,
        /** Le délai maximal a été dépassé : le processus a été tué. */
        TIMEOUT,
        /** L'utilisateur a demandé l'arrêt. */
        CANCELLED,
        /** Le processus n'a pas pu démarrer (interpréteur absent, fichier temporaire impossible…). */
        NOT_STARTED
    }

    /**
     * Résultat brut d'une exécution.
     *
     * @param message explication lisible pour les cas autres que {@link Outcome#COMPLETED} (peut être vide)
     */
    public record Result(Outcome outcome, int exitCode, String stdout, String stderr, String message) {
    }

    private final InterpreterLocator interpreterLocator;

    public ScriptRunner(InterpreterLocator interpreterLocator) {
        this.interpreterLocator = interpreterLocator;
    }

    public InterpreterLocator getInterpreterLocator() {
        return interpreterLocator;
    }

    /** Prépare une exécution ; rien n'est lancé avant l'appel à {@link Execution#run()}. */
    public Execution prepare(ScriptLanguage language, String content, Path workingDirectory,
                             Duration timeout, OutputListener listener) {
        return new Execution(language, content, workingDirectory, timeout,
                listener == null ? OutputListener.NONE : listener);
    }

    /**
     * Une exécution unique. {@link #run()} bloque le thread appelant jusqu'à la fin ;
     * {@link #cancel()} peut être appelé depuis n'importe quel autre thread.
     */
    public final class Execution {
        private final ScriptLanguage language;
        private final String content;
        private final Path workingDirectory;
        private final Duration timeout;
        private final OutputListener listener;
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private volatile Process process;

        private Execution(ScriptLanguage language, String content, Path workingDirectory,
                          Duration timeout, OutputListener listener) {
            this.language = language;
            this.content = content == null ? "" : content;
            this.workingDirectory = workingDirectory;
            this.timeout = timeout;
            this.listener = listener;
        }

        /** Demande l'arrêt : le processus et tous ses enfants sont tués. */
        public void cancel() {
            cancelled.set(true);
            Process running = process;
            if (running != null) {
                killTree(running);
            }
        }

        public boolean isCancelled() {
            return cancelled.get();
        }

        public Result run() {
            Optional<InterpreterLocator.Interpreter> interpreter = interpreterLocator.find(language);
            if (interpreter.isEmpty()) {
                return notStarted(interpreterLocator.missingMessage(language));
            }
            if (cancelled.get()) {
                return new Result(Outcome.CANCELLED, -1, "", "", "Exécution annulée avant le démarrage.");
            }

            Path scriptFile = null;
            try {
                scriptFile = Files.createTempFile("scriptpro-", language.getExtension());
                Files.write(scriptFile, ScriptFiles.encode(language, content));

                ProcessBuilder builder = new ProcessBuilder(ScriptFiles.command(language, interpreter.get(), scriptFile));
                builder.directory(workingDirectory.toFile());
                ScriptFiles.configureEnvironment(language, builder.environment());
                Process started = builder.start();
                process = started;
                // Aucun clavier n'est relié au script : un Read-Host ou input() reçoit « fin de fichier »
                // au lieu de bloquer jusqu'au timeout.
                started.getOutputStream().close();
                if (cancelled.get()) {
                    killTree(started);
                }
                return waitForCompletion(started);
            } catch (IOException exception) {
                return notStarted("Lancement impossible : " + exception.getMessage());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                cancel();
                return new Result(Outcome.CANCELLED, -1, "", "", "Exécution interrompue.");
            } finally {
                process = null;
                deleteQuietly(scriptFile);
            }
        }

        private Result waitForCompletion(Process started) throws InterruptedException {
            Charset fallback = ConsoleCharsets.oem();
            OutputBuffer stdout = new OutputBuffer();
            OutputBuffer stderr = new OutputBuffer();
            Thread outPump = startPump(started.getInputStream(), stdout, false, fallback);
            Thread errPump = startPump(started.getErrorStream(), stderr, true, fallback);

            boolean finished = started.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            Outcome outcome;
            String message = "";
            if (cancelled.get()) {
                outcome = Outcome.CANCELLED;
                message = "Exécution arrêtée par l'utilisateur.";
            } else if (!finished) {
                killTree(started);
                outcome = Outcome.TIMEOUT;
                message = "Timeout d'exécution après " + timeout.toSeconds() + " secondes : le script a été arrêté.";
            } else {
                outcome = Outcome.COMPLETED;
            }
            started.waitFor(STREAM_DRAIN_SECONDS, TimeUnit.SECONDS);

            // Un programme lancé par le script (ex. « start notepad ») peut garder la sortie ouverte :
            // on n'attend pas indéfiniment la fin des flux.
            outPump.join(TimeUnit.SECONDS.toMillis(STREAM_DRAIN_SECONDS));
            errPump.join(TimeUnit.SECONDS.toMillis(STREAM_DRAIN_SECONDS));
            if (outPump.isAlive() || errPump.isAlive()) {
                String note = "Sortie incomplète : un processus lancé par le script garde la console ouverte.";
                message = message.isEmpty() ? note : message + " " + note;
            }

            int exitCode = started.isAlive() ? -1 : started.exitValue();
            if (outcome != Outcome.COMPLETED) {
                exitCode = -1;
            }
            return new Result(outcome, exitCode, stdout.text(), stderr.text(), message);
        }

        private Thread startPump(InputStream stream, OutputBuffer buffer, boolean error, Charset fallback) {
            Thread thread = new Thread(new StreamPump(stream, fallback, text -> {
                if (buffer.append(text)) {
                    listener.onOutput(text, error);
                }
            }), error ? "scriptpro-stderr" : "scriptpro-stdout");
            thread.setDaemon(true);
            thread.start();
            return thread;
        }

        private Result notStarted(String message) {
            return new Result(Outcome.NOT_STARTED, -1, "", "", message);
        }
    }

    /** Tue un processus et toute sa descendance (sinon les enfants survivent sous Windows). */
    static void killTree(Process process) {
        List<ProcessHandle> descendants = new ArrayList<>();
        try {
            process.descendants().forEach(descendants::add);
        } catch (RuntimeException ignored) {
            // Énumération impossible (droits) : on tue au moins le processus principal.
        }
        process.destroyForcibly();
        descendants.forEach(ProcessHandle::destroyForcibly);
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // Fichier encore verrouillé par un processus enfant : Windows le nettoiera dans %TEMP%.
        }
    }

    /** Accumule la sortie d'un flux en respectant {@link #MAX_CAPTURED_CHARS}. */
    private static final class OutputBuffer {
        private final StringBuilder builder = new StringBuilder();
        private long droppedChars;

        /** @return vrai si le texte a été conservé (et doit donc être affiché). */
        synchronized boolean append(String text) {
            if (builder.length() + text.length() <= MAX_CAPTURED_CHARS) {
                builder.append(text);
                return true;
            }
            droppedChars += text.length();
            return false;
        }

        synchronized String text() {
            if (droppedChars == 0) {
                return builder.toString();
            }
            return builder + "\n[… sortie tronquée : " + droppedChars + " caractères non conservés]";
        }
    }

    /**
     * Lit un flux d'octets et le découpe en lignes avant de les décoder une par une,
     * pour pouvoir choisir l'encodage ligne par ligne (voir {@link ConsoleCharsets}).
     */
    private record StreamPump(InputStream stream, Charset fallback, java.util.function.Consumer<String> sink)
            implements Runnable {
        /** Une ligne plus longue est transmise par morceaux pour garder l'affichage réactif. */
        private static final int MAX_LINE_BYTES = 8192;

        @Override
        public void run() {
            ByteArrayOutputStream line = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            try (InputStream input = stream) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    for (int index = 0; index < read; index++) {
                        line.write(buffer[index]);
                        if (buffer[index] == '\n' || line.size() >= MAX_LINE_BYTES) {
                            flush(line);
                        }
                    }
                }
            } catch (IOException closed) {
                // Flux fermé (processus tué) : on transmet ce qui a déjà été lu.
            }
            flush(line);
        }

        private void flush(ByteArrayOutputStream line) {
            if (line.size() == 0) {
                return;
            }
            String text = ConsoleCharsets.decode(line.toByteArray(), line.size(), fallback).replace("\r\n", "\n");
            line.reset();
            sink.accept(text);
        }
    }

    /**
     * Règles propres à chaque langage : écriture du fichier temporaire, ligne de commande
     * et variables d'environnement. Regroupées ici pour que les choix d'encodage soient lisibles.
     */
    static final class ScriptFiles {
        private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

        private ScriptFiles() {
        }

        static byte[] encode(ScriptLanguage language, String content) {
            return switch (language) {
                // Windows PowerShell 5.1 lit un .ps1 sans BOM en ANSI : le BOM garantit l'UTF-8.
                case POWERSHELL -> concat(UTF8_BOM, content.getBytes(StandardCharsets.UTF_8));
                // cmd.exe lit le fichier ligne par ligne dans la page de code courante : on la passe
                // en UTF-8 dès la première ligne. Les fins de ligne CRLF évitent les bugs de GOTO/labels.
                case CMD, BATCH -> ("@chcp 65001 >nul\r\n" + toCrLf(content)).getBytes(StandardCharsets.UTF_8);
                // Bash refuse les retours chariot Windows (« $'\r': command not found »).
                case BASH -> content.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8);
                case PYTHON -> content.getBytes(StandardCharsets.UTF_8);
            };
        }

        static List<String> command(ScriptLanguage language, InterpreterLocator.Interpreter interpreter, Path file) {
            List<String> command = new ArrayList<>();
            command.add(interpreter.executable().toString());
            command.addAll(interpreter.extraArgs());
            String path = file.toAbsolutePath().toString();
            switch (language) {
                case POWERSHELL -> {
                    command.addAll(List.of("-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command"));
                    // La sortie est forcée en UTF-8 avant d'appeler le script ; « exit $LASTEXITCODE »
                    // conserve le code retour (un « exit 3 » dans le script donne bien 3).
                    command.add("[Console]::OutputEncoding = [System.Text.Encoding]::UTF8; "
                            + "$OutputEncoding = [System.Text.Encoding]::UTF8; "
                            + "& '" + path.replace("'", "''") + "'; exit $LASTEXITCODE");
                }
                // /d : ignore les scripts AutoRun du registre, qui pourraient polluer la sortie.
                case CMD, BATCH -> command.addAll(List.of("/d", "/c", path));
                case BASH -> command.add(interpreter.wsl() ? toWslPath(file) : path.replace('\\', '/'));
                case PYTHON -> command.add(path);
            }
            return command;
        }

        static void configureEnvironment(ScriptLanguage language, Map<String, String> environment) {
            switch (language) {
                case PYTHON -> {
                    environment.put("PYTHONIOENCODING", "utf-8");
                    environment.put("PYTHONUTF8", "1");
                    // Sans cela, Python garde sa sortie en mémoire et rien ne s'affiche en direct.
                    environment.put("PYTHONUNBUFFERED", "1");
                }
                case BASH -> environment.putIfAbsent("LANG", "C.UTF-8");
                default -> {
                }
            }
        }

        /** C:\Users\x\script.sh → /mnt/c/Users/x/script.sh (format attendu par le bash de WSL). */
        static String toWslPath(Path file) {
            String path = file.toAbsolutePath().toString().replace('\\', '/');
            if (path.length() > 2 && path.charAt(1) == ':') {
                return "/mnt/" + Character.toLowerCase(path.charAt(0)) + path.substring(2);
            }
            return path;
        }

        private static String toCrLf(String content) {
            return content.replace("\r\n", "\n").replace("\n", "\r\n");
        }

        private static byte[] concat(byte[] first, byte[] second) {
            byte[] result = new byte[first.length + second.length];
            System.arraycopy(first, 0, result, 0, first.length);
            System.arraycopy(second, 0, result, first.length, second.length);
            return result;
        }
    }
}
