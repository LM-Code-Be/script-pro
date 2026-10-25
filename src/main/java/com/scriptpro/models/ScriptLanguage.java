package com.scriptpro.models;

import java.util.Arrays;

public enum ScriptLanguage {
    POWERSHELL("PowerShell", ".ps1"),
    CMD("CMD", ".cmd"),
    BATCH("Batch", ".bat"),
    BASH("Bash", ".sh"),
    PYTHON("Python", ".py");

    private final String displayName;
    private final String extension;

    ScriptLanguage(String displayName, String extension) {
        this.displayName = displayName;
        this.extension = extension;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getExtension() {
        return extension;
    }

    public static ScriptLanguage fromExtension(String fileName) {
        String normalizedName = fileName.toLowerCase();
        return Arrays.stream(values())
                .filter(language -> normalizedName.endsWith(language.extension))
                .findFirst()
                .orElse(POWERSHELL);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
