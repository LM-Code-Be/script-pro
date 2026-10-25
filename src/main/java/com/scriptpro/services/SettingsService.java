package com.scriptpro.services;

import com.scriptpro.repositories.SettingsRepository;

import java.util.Map;

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

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
