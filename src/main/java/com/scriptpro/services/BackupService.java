package com.scriptpro.services;

import com.scriptpro.database.DatabaseManager;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Sauvegarde et restauration de la base ScriptPro (scripts, historique, variables, paramètres).
 *
 * @author LM-Code
 */
public final class BackupService {
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final DatabaseManager databaseManager;

    public BackupService(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /** Nom proposé par défaut dans la boîte de dialogue, ex. « scriptpro-sauvegarde-20261025-143000.db ». */
    public String suggestedFileName() {
        return "scriptpro-sauvegarde-" + LocalDateTime.now().format(FILE_STAMP) + ".db";
    }

    public Path backup(Path target) throws IOException {
        try {
            databaseManager.backupTo(target);
            return target;
        } catch (SQLException exception) {
            throw new IOException("Sauvegarde impossible : " + exception.getMessage(), exception);
        }
    }

    /**
     * Restaure une sauvegarde. Par précaution, la base actuelle est d'abord copiée dans
     * le sous-dossier {@code sauvegardes} pour pouvoir revenir en arrière.
     *
     * @return chemin de la copie de sécurité de la base remplacée
     */
    public Path restore(Path source) throws IOException {
        Path databasePath = databaseManager.getDatabasePath();
        Path safetyCopy = databasePath.resolveSibling("sauvegardes")
                .resolve("scriptpro-avant-restauration-" + LocalDateTime.now().format(FILE_STAMP) + ".db");
        backup(safetyCopy);
        try {
            databaseManager.restoreFrom(source);
            return safetyCopy;
        } catch (SQLException exception) {
            throw new IOException("Restauration impossible : " + exception.getMessage(), exception);
        }
    }
}
