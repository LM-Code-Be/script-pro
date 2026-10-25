package com.scriptpro.database;

import com.scriptpro.models.ScriptLanguage;

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

public final class DatabaseManager {
    private static final Path DATABASE_PATH = Path.of("scriptpro.db");

    public void initialize() {
        try {
            Class.forName("org.sqlite.JDBC");
            Path parent = DATABASE_PATH.toAbsolutePath().getParent();
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
                seedData(connection);
            }
        } catch (ClassNotFoundException | IOException | SQLException exception) {
            throw new IllegalStateException("Initialisation SQLite impossible", exception);
        }
    }

    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + DATABASE_PATH.toAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
        }
        return connection;
    }

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
        }
        if (isTableEmpty(connection, "scripts")) {
            insertScript(connection,
                    "Inventaire système PowerShell",
                    "Inventaire",
                    ScriptLanguage.POWERSHELL,
                    "Collecte les informations principales du poste Windows.",
                    "Get-ComputerInfo | Select-Object CsName, WindowsProductName, WindowsVersion, OsArchitecture\n" +
                            "Get-CimInstance Win32_OperatingSystem | Select-Object LastBootUpTime, FreePhysicalMemory",
                    true);
            insertScript(connection,
                    "Diagnostic réseau",
                    "Réseau",
                    ScriptLanguage.CMD,
                    "Affiche la configuration IP et teste une cible.",
                    "ipconfig /all\nping {{IP}}",
                    true);
            insertScript(connection,
                    "Services arrêtés",
                    "Administration",
                    ScriptLanguage.POWERSHELL,
                    "Liste les services configurés en automatique mais non démarrés.",
                    "Get-Service | Where-Object { $_.StartType -eq 'Automatic' -and $_.Status -ne 'Running' } | Select-Object Name, Status, StartType",
                    false);
            insertScript(connection,
                    "Contrôle disque Bash",
                    "Maintenance",
                    ScriptLanguage.BASH,
                    "Affiche l'utilisation disque et les volumes montés.",
                    "df -h\nmount | column -t",
                    false);
            insertScript(connection,
                    "Inventaire Python",
                    "Inventaire",
                    ScriptLanguage.PYTHON,
                    "Affiche quelques informations système via Python.",
                    "import platform\nimport socket\n\nprint(platform.platform())\nprint(socket.gethostname())",
                    false);
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

    private void insertScript(Connection connection, String name, String category, ScriptLanguage language,
                              String description, String content, boolean favorite) throws SQLException {
        String now = Instant.now().toString();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO scripts(name, category, language, description, content, author, created_at, updated_at, favorite, archived) " +
                        "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, category);
            statement.setString(3, language.name());
            statement.setString(4, description);
            statement.setString(5, content);
            statement.setString(6, System.getProperty("user.name", "Administrateur"));
            statement.setString(7, now);
            statement.setString(8, now);
            statement.setInt(9, favorite ? 1 : 0);
            statement.executeUpdate();
            if (favorite) {
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        try (PreparedStatement favoriteStatement = connection.prepareStatement(
                                "INSERT INTO favorites(script_id, created_at) VALUES(?, ?)")) {
                            favoriteStatement.setLong(1, keys.getLong(1));
                            favoriteStatement.setString(2, now);
                            favoriteStatement.executeUpdate();
                        }
                    }
                }
            }
        }
    }
}
