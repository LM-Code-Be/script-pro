package com.scriptpro.repositories;

import com.scriptpro.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class CategoryRepository {
    private final DatabaseManager databaseManager;

    public CategoryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public List<String> findAllNames() {
        List<String> categories = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT name FROM categories ORDER BY name")) {
            while (resultSet.next()) {
                categories.add(resultSet.getString("name"));
            }
            return categories;
        } catch (SQLException exception) {
            throw new IllegalStateException("Lecture des catégories impossible", exception);
        }
    }

    public void saveIfMissing(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR IGNORE INTO categories(name, color, created_at) VALUES(?, ?, ?)")) {
            statement.setString(1, name.trim());
            statement.setString(2, "#2563EB");
            statement.setString(3, Instant.now().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Enregistrement de catégorie impossible", exception);
        }
    }
}
