package com.scriptpro.services;

import com.scriptpro.models.ScriptLanguage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Recherche l'interpréteur à utiliser pour chaque langage (PowerShell, cmd, bash, python).
 *
 * <p>La recherche suit le même principe que le terminal Windows : parcours de la variable
 * {@code PATH} en essayant les extensions de {@code PATHEXT}. Pour Bash, Git Bash est préféré
 * à WSL car il comprend directement les chemins Windows. Les résultats sont mis en cache ;
 * {@link #refresh()} force une nouvelle détection (par exemple après l'installation de Python).</p>
 *
 * @author LM-Code
 */
public final class InterpreterLocator {

    /**
     * Interpréteur trouvé sur le poste.
     *
     * @param executable chemin absolu de l'exécutable
     * @param extraArgs  arguments à placer avant le fichier script (ex. {@code -3} pour le lanceur {@code py})
     * @param wsl        vrai si c'est le bash de WSL, qui attend des chemins de type {@code /mnt/c/...}
     */
    public record Interpreter(Path executable, List<String> extraArgs, boolean wsl) {
    }

    private final Map<String, String> environment;
    private final boolean windows;
    private final Map<ScriptLanguage, Optional<Interpreter>> cache = new EnumMap<>(ScriptLanguage.class);

    public InterpreterLocator() {
        this(System.getenv(), System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win"));
    }

    /** Constructeur ouvert aux tests : permet de simuler un autre PATH ou un autre système. */
    InterpreterLocator(Map<String, String> environment, boolean windows) {
        this.environment = environment;
        this.windows = windows;
    }

    public synchronized Optional<Interpreter> find(ScriptLanguage language) {
        return cache.computeIfAbsent(language, this::detect);
    }

    public synchronized void refresh() {
        cache.clear();
    }

    /** Message d'aide affiché quand l'interpréteur d'un langage est absent. */
    public String missingMessage(ScriptLanguage language) {
        return switch (language) {
            case POWERSHELL -> "PowerShell est introuvable sur ce poste (powershell.exe ou pwsh).";
            case CMD, BATCH -> "L'interpréteur de commandes Windows (cmd.exe) est introuvable.";
            case BASH -> "Bash est introuvable. Installez Git for Windows (Git Bash) ou WSL, "
                    + "ou ajoutez bash.exe au PATH, puis relancez ScriptPro.";
            case PYTHON -> "Python est introuvable. Installez-le depuis python.org en cochant "
                    + "« Add python.exe to PATH », puis relancez ScriptPro.";
        };
    }

    private Optional<Interpreter> detect(ScriptLanguage language) {
        return switch (language) {
            case POWERSHELL -> toInterpreter(firstOf(
                    windowsPath("System32\\WindowsPowerShell\\v1.0\\powershell.exe"),
                    onPath("powershell"),
                    onPath("pwsh")), List.of());
            case CMD, BATCH -> toInterpreter(firstOf(
                    fileFromEnv("ComSpec"),
                    onPath("cmd")), List.of());
            case BASH -> detectBash();
            case PYTHON -> detectPython();
        };
    }

    private Optional<Interpreter> detectBash() {
        if (!windows) {
            return toInterpreter(firstOf(onPath("bash")), List.of());
        }
        // Emplacements classiques de Git Bash (installation standard, utilisateur ou Scoop).
        List<Optional<Path>> gitBashCandidates = List.of(
                underEnv("ProgramFiles", "Git\\bin\\bash.exe"),
                underEnv("ProgramFiles(x86)", "Git\\bin\\bash.exe"),
                underEnv("LOCALAPPDATA", "Programs\\Git\\bin\\bash.exe"),
                underEnv("USERPROFILE", "scoop\\apps\\git\\current\\bin\\bash.exe"));
        for (Optional<Path> candidate : gitBashCandidates) {
            if (candidate.isPresent()) {
                return Optional.of(new Interpreter(candidate.get(), List.of(), false));
            }
        }
        // Sinon : le bash du PATH. Celui de System32 est le lanceur WSL.
        return onPath("bash").map(path -> new Interpreter(path, List.of(), isInSystem32(path)));
    }

    private Optional<Interpreter> detectPython() {
        if (windows) {
            // Le lanceur « py » est installé avec Python (python.org) et choisit la dernière version 3.
            Optional<Path> launcher = onPath("py");
            if (launcher.isPresent()) {
                return Optional.of(new Interpreter(launcher.get(), List.of("-3"), false));
            }
        }
        return toInterpreter(firstOf(onPath("python"), onPath("python3")), List.of());
    }

    @SafeVarargs
    private static Optional<Path> firstOf(Optional<Path>... candidates) {
        for (Optional<Path> candidate : candidates) {
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return Optional.empty();
    }

    private static Optional<Interpreter> toInterpreter(Optional<Path> path, List<String> extraArgs) {
        return path.map(executable -> new Interpreter(executable, extraArgs, false));
    }

    /** Cherche une commande dans le PATH, comme le ferait le terminal. */
    Optional<Path> onPath(String command) {
        String pathVariable = env("PATH");
        if (pathVariable == null || pathVariable.isBlank()) {
            return Optional.empty();
        }
        List<String> extensions = executableExtensions(command);
        for (String directory : pathVariable.split(File.pathSeparator)) {
            if (directory.isBlank()) {
                continue;
            }
            for (String extension : extensions) {
                try {
                    Path candidate = Path.of(directory.trim().replace("\"", ""), command + extension);
                    if (Files.isRegularFile(candidate)) {
                        return Optional.of(candidate.toAbsolutePath());
                    }
                } catch (RuntimeException invalidPath) {
                    // Entrée de PATH mal formée : on l'ignore, comme le fait Windows.
                }
            }
        }
        return Optional.empty();
    }

    private List<String> executableExtensions(String command) {
        List<String> extensions = new ArrayList<>();
        if (!windows || command.contains(".")) {
            extensions.add("");
        }
        if (windows) {
            String pathExt = env("PATHEXT");
            String value = pathExt == null || pathExt.isBlank() ? ".COM;.EXE;.BAT;.CMD" : pathExt;
            for (String extension : value.split(";")) {
                if (!extension.isBlank()) {
                    extensions.add(extension.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return extensions;
    }

    private Optional<Path> windowsPath(String relative) {
        return windows ? underEnv("SystemRoot", relative) : Optional.empty();
    }

    private Optional<Path> underEnv(String variable, String relative) {
        String base = env(variable);
        if (base == null || base.isBlank()) {
            return Optional.empty();
        }
        Path candidate = Path.of(base, relative);
        return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    private Optional<Path> fileFromEnv(String variable) {
        String value = env(variable);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        Path candidate = Path.of(value);
        return Files.isRegularFile(candidate) ? Optional.of(candidate) : Optional.empty();
    }

    private boolean isInSystem32(Path path) {
        Path parent = path.getParent();
        return parent != null && parent.getFileName() != null
                && parent.getFileName().toString().equalsIgnoreCase("System32");
    }

    /** Les variables d'environnement Windows ne sont pas sensibles à la casse (Path, PATH…). */
    private String env(String name) {
        String value = environment.get(name);
        if (value != null || !windows) {
            return value;
        }
        for (Map.Entry<String, String> entry : environment.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
