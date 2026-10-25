package com.scriptpro.repositories;

import com.scriptpro.database.DatabaseManager;
import com.scriptpro.models.VariableEntry;

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
 * Accès SQLite aux variables dynamiques ({@code {{NOM}}}) et à leurs valeurs par défaut.
 *
 * @author LM-Code
 */
public final class VariableRepository {
    private final DatabaseManager databaseManager;

    public VariableRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<VariableEntry> findAll() {
        List<VariableEntry> variables = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT * FROM variables ORDER BY built_in DESC, name")) {
            while (resultSet.next()) {
                variables.add(map(resultSet));
            }
            return variables;
        } catch (SQLException exception) {
            throw new IllegalStateException("Lecture des variables impossible", exception);
        }
    }

    public Optional<VariableEntry> findByName(String name) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM variables WHERE name = ?")) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(map(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Lecture de variable impossible", exception);
        }
    }

    public VariableEntry save(VariableEntry variable) {
        variable.setName(variable.getName().trim().toUpperCase());
        variable.setUpdatedAt(Instant.now());
        if (variable.getId() == null) {
            return insert(variable);
        }
        return update(variable);
    }

    public void delete(long id) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM variables WHERE id = ? AND built_in = 0")) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Suppression de variable impossible", exception);
        }
    }

    private VariableEntry insert(VariableEntry variable) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO variables(name, default_value, description, built_in, updated_at, secret) VALUES(?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            fill(statement, variable);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    variable.setId(keys.getLong(1));
                }
            }
            return variable;
        } catch (SQLException exception) {
            throw new IllegalStateException("Création de variable impossible", exception);
        }
    }

    private VariableEntry update(VariableEntry variable) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE variables SET name = ?, default_value = ?, description = ?, built_in = ?, updated_at = ?, secret = ? WHERE id = ?")) {
            fill(statement, variable);
            statement.setLong(7, variable.getId());
            statement.executeUpdate();
            return variable;
        } catch (SQLException exception) {
            throw new IllegalStateException("Mise à jour de variable impossible", exception);
        }
    }

    private void fill(PreparedStatement statement, VariableEntry variable) throws SQLException {
        statement.setString(1, variable.getName());
        statement.setString(2, value(variable.getDefaultValue()));
        statement.setString(3, value(variable.getDescription()));
        statement.setInt(4, variable.isBuiltIn() ? 1 : 0);
        statement.setString(5, variable.getUpdatedAt().toString());
        statement.setInt(6, variable.isSecret() ? 1 : 0);
    }

    private VariableEntry map(ResultSet resultSet) throws SQLException {
        return new VariableEntry(
                resultSet.getLong("id"),
                resultSet.getString("name"),
                resultSet.getString("default_value"),
                resultSet.getString("description"),
                resultSet.getInt("built_in") == 1,
                resultSet.getInt("secret") == 1,
                Instant.parse(resultSet.getString("updated_at"))
        );
    }

    private String value(String text) {
        return text == null ? "" : text;
    }
}
