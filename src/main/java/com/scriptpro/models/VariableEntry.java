package com.scriptpro.models;

import java.time.Instant;

/**
 * Variable dynamique utilisable dans un script sous la forme {@code {{NOM}}}.
 *
 * <p>Une variable « sensible » (mot de passe, jeton…) est saisie dans un champ masqué
 * et sa valeur est remplacée par {@link #MASK} dans la console et l'historique.</p>
 *
 * @author LM-Code
 */
public class VariableEntry {
    /** Texte affiché à la place d'une valeur sensible. */
    public static final String MASK = "••••••";

    private Long id;
    private String name;
    private String defaultValue;
    private String description;
    private boolean builtIn;
    private boolean secret;
    private Instant updatedAt;

    public VariableEntry() {
        this.updatedAt = Instant.now();
    }

    public VariableEntry(Long id, String name, String defaultValue, String description, boolean builtIn,
                         boolean secret, Instant updatedAt) {
        this.id = id;
        this.name = name;
        this.defaultValue = defaultValue;
        this.description = description;
        this.builtIn = builtIn;
        this.secret = secret;
        this.updatedAt = updatedAt;
    }

    /** Valeur par défaut prête à afficher : masquée si la variable est sensible. */
    public String displayValue() {
        if (secret && defaultValue != null && !defaultValue.isEmpty()) {
            return MASK;
        }
        return defaultValue;
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

    public boolean isSecret() {
        return secret;
    }

    public void setSecret(boolean secret) {
        this.secret = secret;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
