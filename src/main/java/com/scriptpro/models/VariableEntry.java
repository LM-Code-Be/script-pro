package com.scriptpro.models;

import java.time.Instant;

public class VariableEntry {
    private Long id;
    private String name;
    private String defaultValue;
    private String description;
    private boolean builtIn;
    private Instant updatedAt;

    public VariableEntry() {
        this.updatedAt = Instant.now();
    }

    public VariableEntry(Long id, String name, String defaultValue, String description, boolean builtIn, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.defaultValue = defaultValue;
        this.description = description;
        this.builtIn = builtIn;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
