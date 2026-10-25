package com.scriptpro.database;

import com.scriptpro.models.Script;
import com.scriptpro.models.ScriptLanguage;
import com.scriptpro.models.VariableEntry;
import com.scriptpro.repositories.CategoryRepository;
import com.scriptpro.repositories.ExecutionHistoryRepository;
import com.scriptpro.repositories.ScriptRepository;
import com.scriptpro.repositories.VariableRepository;
import com.scriptpro.services.BackupService;
import com.scriptpro.services.ScriptService;
import com.scriptpro.services.VariableService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests de la couche SQLite : migration, tags, variables sensibles, sauvegarde et restauration.
 * Chaque test travaille sur une base temporaire, jamais sur scriptpro.db.
 *
 * @author LM-Code
 */
class DatabaseManagerTest {
    @TempDir
    Path directory;

    private DatabaseManager newDatabase(String name) {
        DatabaseManager databaseManager = new DatabaseManager(directory.resolve(name));
        databaseManager.initialize();
        return databaseManager;
    }

    @Test
    void migratesDatabaseCreatedByPreviousVersion() throws Exception {
        Path file = directory.resolve("ancienne.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement()) {
            // Schéma de la version 1.0, sans les colonnes « tags » et « secret ».
            statement.execute("CREATE TABLE scripts (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, "
                    + "category TEXT NOT NULL, language TEXT NOT NULL, description TEXT NOT NULL, content TEXT NOT NULL, "
                    + "author TEXT NOT NULL, created_at TEXT NOT NULL, updated_at TEXT NOT NULL, "
                    + "favorite INTEGER NOT NULL DEFAULT 0, archived INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE variables (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, "
                    + "default_value TEXT NOT NULL, description TEXT NOT NULL, built_in INTEGER NOT NULL DEFAULT 0, "
                    + "updated_at TEXT NOT NULL)");
            statement.execute("INSERT INTO scripts(name, category, language, description, content, author, created_at, updated_at) "
                    + "VALUES('Ancien', 'Général', 'POWERSHELL', '', 'Get-Date', 'moi', '2025-01-01T00:00:00Z', '2025-01-01T00:00:00Z')");
        }

        DatabaseManager databaseManager = new DatabaseManager(file);
        databaseManager.initialize();

        List<Script> scripts = new ScriptRepository(databaseManager).findAll(false);
        Script old = scripts.stream().filter(script -> script.getName().equals("Ancien")).findFirst().orElseThrow();
        assertTrue(old.getTags().isEmpty());
        assertFalse(new VariableRepository(databaseManager).findAll().isEmpty());
    }

    @Test
    void newDatabaseStartsWithoutScriptsOrHistory() {
        DatabaseManager databaseManager = newDatabase("neuve.db");
        assertTrue(new ScriptRepository(databaseManager).findAll(true).isEmpty());
        assertTrue(new ExecutionHistoryRepository(databaseManager).findAll().isEmpty());
        assertFalse(new CategoryRepository(databaseManager).findAllNames().isEmpty());
    }

    @Test
    void savesAndSearchesTags() {
        DatabaseManager databaseManager = newDatabase("tags.db");
        ScriptService scriptService = new ScriptService(new ScriptRepository(databaseManager), new CategoryRepository(databaseManager));
        Script script = new Script();
        script.setName("Purge des logs");
        script.setLanguage(ScriptLanguage.POWERSHELL);
        script.setTagsText("Maintenance, URGENT, maintenance");
        scriptService.save(script);

        Script reloaded = scriptService.findById(script.getId()).orElseThrow();
        assertEquals(List.of("maintenance", "urgent"), reloaded.getTags());
        assertTrue(scriptService.tags().contains("urgent"));
        assertEquals(1, scriptService.search("", null, "", "urgent", false).size());
        assertEquals(1, scriptService.search("urgent", null, "", "", false).size());
    }

    @Test
    void secretVariablesAreMasked() {
        DatabaseManager databaseManager = newDatabase("variables.db");
        VariableService variableService = new VariableService(new VariableRepository(databaseManager));
        VariableEntry password = new VariableEntry();
        password.setName("mot_de_passe");
        password.setDefaultValue("");
        password.setDescription("Mot de passe admin");
        password.setSecret(true);
        variableService.save(password);

        assertTrue(variableService.findAllByName().get("MOT_DE_PASSE").isSecret());
        List<String> secrets = variableService.secretValues(Map.of("MOT_DE_PASSE", "S3cret!", "IP", "10.0.0.1"));
        assertEquals(List.of("S3cret!"), secrets);
        assertEquals("connexion avec •••••• sur 10.0.0.1",
                VariableService.mask("connexion avec S3cret! sur 10.0.0.1", secrets));
    }

    @Test
    void rejectsInvalidVariableName() {
        VariableService variableService = new VariableService(new VariableRepository(newDatabase("noms.db")));
        VariableEntry variable = new VariableEntry();
        variable.setName("nom avec espaces");
        assertThrows(IllegalArgumentException.class, () -> variableService.save(variable));
        variable.setName("  ");
        assertThrows(IllegalArgumentException.class, () -> variableService.save(variable));
    }

    @Test
    void backupThenRestoreBringsBackData() throws IOException {
        DatabaseManager databaseManager = newDatabase("principale.db");
        ScriptRepository repository = new ScriptRepository(databaseManager);
        BackupService backupService = new BackupService(databaseManager);
        int initialCount = repository.findAll(true).size();

        Path backup = backupService.backup(directory.resolve("export").resolve("sauvegarde.db"));
        assertTrue(Files.size(backup) > 0);

        Script added = new Script();
        added.setName("Ajouté après la sauvegarde");
        added.setCategory("Général");
        repository.save(added);
        assertEquals(initialCount + 1, repository.findAll(true).size());

        Path safetyCopy = backupService.restore(backup);
        assertEquals(initialCount, repository.findAll(true).size());
        assertTrue(Files.exists(safetyCopy), "La base remplacée doit être conservée");
    }

    @Test
    void refusesToRestoreAnInvalidFile() throws IOException {
        DatabaseManager databaseManager = newDatabase("cible.db");
        Script existing = new Script();
        existing.setName("Script existant");
        existing.setCategory("Général");
        new ScriptRepository(databaseManager).save(existing);
        Path notADatabase = Files.writeString(directory.resolve("faux.db"), "ceci n'est pas une base");
        assertThrows(IOException.class, () -> databaseManager.restoreFrom(notADatabase));
        assertFalse(new ScriptRepository(databaseManager).findAll(true).isEmpty(), "La base active doit rester intacte");
    }
}
