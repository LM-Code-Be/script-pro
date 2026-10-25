package com.scriptpro.models;

/**
 * Résultat final d'une exécution de script, tel qu'il est stocké dans l'historique.
 *
 * <p>Le nom de la constante est persisté en base : ne pas renommer une valeur existante.</p>
 *
 * @author LM-Code
 */
public enum ExecutionStatus {
    SUCCESS("Succès"),
    FAILURE("Échec"),
    WARNING("Avertissement"),
    /** Exécution interrompue volontairement par l'utilisateur (bouton « Arrêter »). */
    CANCELLED("Arrêté");

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
