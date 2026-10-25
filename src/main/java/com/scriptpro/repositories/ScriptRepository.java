package com.scriptpro.repositories;

import com.scriptpro.database.DatabaseManager;
import com.scriptpro.models.Script;
import com.scriptpro.models.ScriptLanguage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Accès SQLite aux scripts de la bibliothèque (table {@code scripts} et table de liaison {@code favorites}).
 *
 * @author LM-Code
 */
public final class ScriptRepository {
    private final DatabaseManager databaseManager;

    public ScriptRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<Script> findAll(boolean includeArchived) {
        String sql = includeArchived
                ? "SELECT * FROM scripts ORDER BY updated_at DESC"
                : "SELECT * FROM scripts WHERE archived = 0 ORDER BY updated_at DESC";
        List<Script> scripts = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                scripts.add(map(resultSet));
            }
            return scripts;
        } catch (SQLException exception) {
            throw new IllegalStateException("Lecture des scripts impossible", exception);
        }
    }

    public Optional<Script> findById(long id) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM scripts WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(map(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Lecture du script impossible", exception);
        }
    }

    public Script save(Script script) {
        Instant now = Instant.now();
        if (script.getCreatedAt() == null) {
            script.setCreatedAt(now);
        }
        script.setUpdatedAt(now);
        if (script.getId() == null) {
            return insert(script);
        }
        return update(script);
    }

    public void archive(long id, boolean archived) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE scripts SET archived = ?, updated_at = ? WHERE id = ?")) {
            statement.setInt(1, archived ? 1 : 0);
            statement.setString(2, Instant.now().toString());
            statement.setLong(3, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Archivage du script impossible", exception);
        }
    }

    public void delete(long id) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement favoriteStatement = connection.prepareStatement("DELETE FROM favorites WHERE script_id = ?");
             PreparedStatement scriptStatement = connection.prepareStatement("DELETE FROM scripts WHERE id = ?")) {
            favoriteStatement.setLong(1, id);
            favoriteStatement.executeUpdate();
            scriptStatement.setLong(1, id);
            scriptStatement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Suppression du script impossible", exception);
        }
    }

    private Script insert(Script script) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO scripts(name, category, language, description, content, author, created_at, updated_at, favorite, archived, tags) " +
                             "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            fillStatement(statement, script);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    script.setId(keys.getLong(1));
                }
            }
            syncFavorite(connection, script);
            return script;
        } catch (SQLException exception) {
            throw new IllegalStateException("Création du script impossible", exception);
        }
    }

    private Script update(Script script) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE scripts SET name = ?, category = ?, language = ?, description = ?, content = ?, author = ?, " +
                             "created_at = ?, updated_at = ?, favorite = ?, archived = ?, tags = ? WHERE id = ?")) {
            fillStatement(statement, script);
            statement.setLong(12, script.getId());
            statement.executeUpdate();
            syncFavorite(connection, script);
            return script;
        } catch (SQLException exception) {
            throw new IllegalStateException("Mise à jour du script impossible", exception);
        }
    }

    private void fillStatement(PreparedStatement statement, Script script) throws SQLException {
        statement.setString(1, script.getName().trim());
        statement.setString(2, script.getCategory().trim());
        statement.setString(3, script.getLanguage().name());
        statement.setString(4, value(script.getDescription()));
        statement.setString(5, value(script.getContent()));
        statement.setString(6, value(script.getAuthor()));
        statement.setString(7, script.getCreatedAt().toString());
        statement.setString(8, script.getUpdatedAt().toString());
        statement.setInt(9, script.isFavorite() ? 1 : 0);
        statement.setInt(10, script.isArchived() ? 1 : 0);
        statement.setString(11, script.getTagsText());
    }

    private void syncFavorite(Connection connection, Script script) throws SQLException {
        if (script.getId() == null) {
            return;
        }
        if (script.isFavorite()) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT OR IGNORE INTO favorites(script_id, created_at) VALUES(?, ?)")) {
                statement.setLong(1, script.getId());
                statement.setString(2, Instant.now().toString());
                statement.executeUpdate();
            }
        } else {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM favorites WHERE script_id = ?")) {
                statement.setLong(1, script.getId());
                statement.executeUpdate();
            }
        }
    }

    private Script map(ResultSet resultSet) throws SQLException {
        Script script = new Script(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("category"),
                ScriptLanguage.valueOf(resultSet.getString("language")),
                resultSet.getString("description"),
                resultSet.getString("content"),
                resultSet.getString("author"),
                Instant.parse(resultSet.getString("created_at")),
                Instant.parse(resultSet.getString("updated_at")),
                resultSet.getInt("favorite") == 1,
                resultSet.getInt("archived") == 1
        );
        script.setTagsText(resultSet.getString("tags"));
        return script;
    }

    private String value(String text) {
        return text == null ? "" : text;
    }
}
