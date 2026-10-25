package com.scriptpro.models;

public enum ExecutionStatus {
    SUCCESS("Succès"),
    FAILURE("Échec"),
    WARNING("Avertissement");

    private final String displayName;

    ExecutionStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
