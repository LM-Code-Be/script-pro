package com.scriptpro.services;

import com.scriptpro.repositories.SettingsRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Préférences de l'application, persistées dans la table {@code settings}.
 * Chaque accesseur fournit une valeur par défaut sûre si le réglage est absent ou invalide.
 *
 * @author LM-Code
 */
public final class SettingsService {
    private final SettingsRepository settingsRepository;

    public SettingsService(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public Map<String, String> findAll() {
        return settingsRepository.findAll();
    }

    public String get(String key, String fallback) {
        return settingsRepository.get(key).orElse(fallback);
    }

    public void set(String key, String value) {
        settingsRepository.set(key, value);
    }

    public boolean isDarkTheme() {
        return "dark".equalsIgnoreCase(get("theme", "light"));
    }

    public void setDarkTheme(boolean darkTheme) {
        set("theme", darkTheme ? "dark" : "light");
    }

    public int executionTimeoutSeconds() {
        return parseInt(get("executionTimeout", "60"), 60);
    }

    public int editorFontSize() {
        return parseInt(get("editorFontSize", "14"), 14);
    }

    public boolean notificationsEnabled() {
        return Boolean.parseBoolean(get("notifications", "true"));
    }

    /**
     * Dossier dans lequel les scripts sont lancés (chemins relatifs, fichiers créés…).
     * Si le dossier configuré n'existe plus, le dossier personnel de l'utilisateur est utilisé.
     */
    public Path workingDirectory() {
        Path home = Path.of(System.getProperty("user.home"));
        try {
            Path configured = Path.of(get("workingDirectory", home.toString()));
            return Files.isDirectory(configured) ? configured : home;
        } catch (RuntimeException invalidPath) {
            return home;
        }
    }

    /** Expressions régulières de sécurité ajoutées par l'utilisateur (une par ligne). */
    public List<String> customSecurityPatterns() {
        return Arrays.stream(get("securityPatterns", "").split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }

    public void setCustomSecurityPatterns(List<String> patterns) {
        set("securityPatterns", String.join("\n", patterns));
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
