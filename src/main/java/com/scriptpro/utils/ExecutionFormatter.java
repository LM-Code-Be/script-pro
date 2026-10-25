package com.scriptpro.utils;

import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.models.ExecutionStatus;

/**
 * Mise en forme texte d'une exécution, pour la console de l'éditeur et les vues d'historique.
 *
 * @author LM-Code
 */
public final class ExecutionFormatter {
    private ExecutionFormatter() {
    }

    public static String format(ExecutionRecord record) {
        return "Script : " + record.getScriptName() + "\n" +
                "Résultat : " + record.getStatus().getDisplayName() + "\n" +
                "Code retour : " + record.getExitCode() + "\n" +
                "Durée : " + DateFormatter.duration(record.getDurationMillis()) + "\n" +
                "Date : " + DateFormatter.format(record.getStartedAt()) + "\n\n" +
                "Sortie standard\n" +
                "----------------\n" +
                value(record.getStdout()) + "\n\n" +
                "Erreurs\n" +
                "----------------\n" +
                value(record.getStderr());
    }

    /** Classe CSS de la pastille de statut. */
    public static String statusClass(ExecutionStatus status) {
        return switch (status) {
            case SUCCESS -> "status-success";
            case FAILURE -> "status-failure";
            case WARNING, CANCELLED -> "status-warning";
        };
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
