package com.scriptpro.database;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;

/**
 * Point d'accès unique à la base SQLite locale : création du schéma, migrations,
 * valeurs par défaut, sauvegarde et restauration.
 *
 * <p>Chaque dépôt ouvre une connexion courte via {@link #getConnection()} : aucune
 * connexion n'est gardée ouverte, ce qui permet notamment la restauration à chaud.</p>
 *
 * @author LM-Code
 */
public final class DatabaseManager {
    private static final Path DEFAULT_DATABASE_PATH = Path.of("scriptpro.db");

    private final Path databasePath;

    public DatabaseManager() {
        this(DEFAULT_DATABASE_PATH);
    }

    /** Permet d'utiliser un autre fichier de base (utile pour les tests). */
    public DatabaseManager(Path databasePath) {
        this.databasePath = databasePath.toAbsolutePath();
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            Path parent = databasePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS categories (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL UNIQUE," +
                        "color TEXT NOT NULL," +
                        "created_at TEXT NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS scripts (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL," +
                        "category TEXT NOT NULL," +
                        "language TEXT NOT NULL," +
                        "description TEXT NOT NULL," +
                        "content TEXT NOT NULL," +
                        "author TEXT NOT NULL," +
                        "created_at TEXT NOT NULL," +
                        "updated_at TEXT NOT NULL," +
                        "favorite INTEGER NOT NULL DEFAULT 0," +
                        "archived INTEGER NOT NULL DEFAULT 0)");
                statement.execute("CREATE TABLE IF NOT EXISTS execution_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "script_id INTEGER," +
                        "script_name TEXT NOT NULL," +
                        "language TEXT NOT NULL," +
                        "status TEXT NOT NULL," +
                        "started_at TEXT NOT NULL," +
                        "duration_millis INTEGER NOT NULL," +
                        "exit_code INTEGER NOT NULL," +
                        "stdout TEXT NOT NULL," +
                        "stderr TEXT NOT NULL," +
                        "user_name TEXT NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS favorites (" +
                        "script_id INTEGER PRIMARY KEY," +
                        "created_at TEXT NOT NULL," +
                        "FOREIGN KEY(script_id) REFERENCES scripts(id) ON DELETE CASCADE)");
                statement.execute("CREATE TABLE IF NOT EXISTS variables (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL UNIQUE," +
                        "default_value TEXT NOT NULL," +
                        "description TEXT NOT NULL," +
                        "built_in INTEGER NOT NULL DEFAULT 0," +
                        "updated_at TEXT NOT NULL)");
                statement.execute("CREATE TABLE IF NOT EXISTS settings (" +
                        "key TEXT PRIMARY KEY," +
                        "value TEXT NOT NULL)");
                migrate(connection);
                seedData(connection);
            }
        } catch (ClassNotFoundException | IOException | SQLException exception) {
            throw new IllegalStateException("Initialisation SQLite impossible", exception);
        }
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

    /**
     * Copie cohérente de la base vers {@code target}, même pendant l'utilisation.
     * {@code VACUUM INTO} produit un fichier compact et refuse d'écraser un fichier existant,
     * d'où la suppression préalable.
     */
    public void backupTo(Path target) throws IOException, SQLException {
        Path absoluteTarget = target.toAbsolutePath();
        if (absoluteTarget.equals(databasePath)) {
            throw new IOException("La sauvegarde ne peut pas remplacer la base active.");
        }
        if (absoluteTarget.getParent() != null) {
            Files.createDirectories(absoluteTarget.getParent());
        }
        Files.deleteIfExists(absoluteTarget);
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + absoluteTarget.toString().replace("'", "''") + "'");
        }
    }

    /**
     * Remplace le contenu de la base active par celui de {@code source}.
     *
     * <p>Le fichier est d'abord contrôlé (doit être une base ScriptPro), puis copié via l'API
     * de sauvegarde SQLite. Le schéma est ensuite remis à niveau, au cas où la sauvegarde
     * proviendrait d'une version plus ancienne de l'application.</p>
     */
    public void restoreFrom(Path source) throws IOException, SQLException {
        Path absoluteSource = source.toAbsolutePath();
        if (!Files.isRegularFile(absoluteSource)) {
            throw new IOException("Fichier de sauvegarde introuvable : " + absoluteSource);
        }
        if (absoluteSource.equals(databasePath)) {
            throw new IOException("Le fichier choisi est déjà la base active.");
        }
        validateBackup(absoluteSource);
        try (Connection connection = getConnection(); Statement statement = connection.createStatement()) {
            // Commande spécifique au pilote sqlite-jdbc (API « online backup » de SQLite).
            statement.executeUpdate("restore from \"" + absoluteSource + "\"");
        }
        initialize();
    }

    /** Vérifie qu'un fichier est bien une base SQLite contenant les tables ScriptPro. */
    private void validateBackup(Path file) throws IOException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + file);
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name IN ('scripts', 'execution_history')")) {
            if (!resultSet.next() || resultSet.getInt(1) < 2) {
                throw new IOException("Ce fichier n'est pas une sauvegarde ScriptPro valide.");
            }
        } catch (SQLException exception) {
            throw new IOException("Ce fichier n'est pas une base SQLite lisible.", exception);
        }
    }

    /**
     * Mises à niveau du schéma pour les bases créées par une version précédente.
     * Chaque étape est idempotente : elle ne fait rien si la colonne existe déjà.
     */
    private void migrate(Connection connection) throws SQLException {
        addColumnIfMissing(connection, "scripts", "tags", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing(connection, "variables", "secret", "INTEGER NOT NULL DEFAULT 0");
    }

    private void addColumnIfMissing(Connection connection, String table, String column, String definition) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet columns = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("name"))) {
                    return;
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    /**
     * Valeurs par défaut d'une nouvelle installation : catégories, variables intégrées
     * et paramètres. Aucun script n'est créé : l'utilisateur démarre avec une bibliothèque
     * vide (les exemples sont proposés dans l'écran Modèles).
     */
    private void seedData(Connection connection) throws SQLException {
        if (isTableEmpty(connection, "categories")) {
            insertCategory(connection, "Administration", "#2563EB");
            insertCategory(connection, "Réseau", "#22C55E");
            insertCategory(connection, "Sécurité", "#EF4444");
            insertCategory(connection, "Maintenance", "#F59E0B");
            insertCategory(connection, "Inventaire", "#7C3AED");
        }
        if (isTableEmpty(connection, "variables")) {
            insertVariable(connection, "IP", "127.0.0.1", "Adresse IP cible", true);
            insertVariable(connection, "USERNAME", System.getProperty("user.name", "administrateur"), "Nom utilisateur", true);
            insertVariable(connection, "HOSTNAME", "localhost", "Nom de machine", true);
            insertVariable(connection, "PATH", "C:\\Temp", "Chemin de travail", true);
            insertVariable(connection, "SERVICE", "Spooler", "Nom de service", true);
        }
        if (isTableEmpty(connection, "settings")) {
            insertSetting(connection, "theme", "light");
            insertSetting(connection, "exportPath", Path.of("exports").toAbsolutePath().toString());
            insertSetting(connection, "executionTimeout", "60");
            insertSetting(connection, "editorFontSize", "14");
            insertSetting(connection, "notifications", "true");
            insertSetting(connection, "preferredTerminal", "PowerShell");
            insertSetting(connection, "workingDirectory", System.getProperty("user.home"));
        }
    }

    private boolean isTableEmpty(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            return resultSet.next() && resultSet.getInt(1) == 0;
        }
    }

    private void insertCategory(Connection connection, String name, String color) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO categories(name, color, created_at) VALUES(?, ?, ?)")) {
            statement.setString(1, name);
            statement.setString(2, color);
            statement.setString(3, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private void insertVariable(Connection connection, String name, String defaultValue, String description, boolean builtIn) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO variables(name, default_value, description, built_in, updated_at) VALUES(?, ?, ?, ?, ?)")) {
            statement.setString(1, name);
            statement.setString(2, defaultValue);
            statement.setString(3, description);
            statement.setInt(4, builtIn ? 1 : 0);
            statement.setString(5, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private void insertSetting(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO settings(key, value) VALUES(?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        }
    }
}
