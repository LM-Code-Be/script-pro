package com.scriptpro.repositories;

import com.scriptpro.database.DatabaseManager;
import com.scriptpro.models.ExecutionRecord;
import com.scriptpro.models.ExecutionStatus;
import com.scriptpro.models.ScriptLanguage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class ExecutionHistoryRepository {
    private final DatabaseManager databaseManager;

    public ExecutionHistoryRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public ExecutionRecord save(ExecutionRecord record) {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO execution_history(script_id, script_name, language, status, started_at, duration_millis, exit_code, stdout, stderr, user_name) " +
                             "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            if (record.getScriptId() == null) {
                statement.setObject(1, null);
            } else {
                statement.setLong(1, record.getScriptId());
            }
            statement.setString(2, record.getScriptName());
            statement.setString(3, record.getLanguage().name());
            statement.setString(4, record.getStatus().name());
            statement.setString(5, record.getStartedAt().toString());
            statement.setLong(6, record.getDurationMillis());
            statement.setInt(7, record.getExitCode());
            statement.setString(8, value(record.getStdout()));
            statement.setString(9, value(record.getStderr()));
            statement.setString(10, value(record.getUserName()));
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (keys.next()) {
                    record.setId(keys.getLong(1));
                }
            }
            return record;
        } catch (SQLException exception) {
            throw new IllegalStateException("Enregistrement de l'exécution impossible", exception);
        }
    }

    public List<ExecutionRecord> findAll() {
        return query("SELECT * FROM execution_history ORDER BY started_at DESC");
    }

    public List<ExecutionRecord> findRecent(int limit) {
        return query("SELECT * FROM execution_history ORDER BY started_at DESC LIMIT " + Math.max(1, limit));
    }

    public long countToday() {
        Instant startOfDay = LocalDate.now()
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM execution_history WHERE started_at >= ?")) {
            statement.setString(1, startOfDay.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Lecture des statistiques impossible", exception);
        }
    }

    public long countFailures() {
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM execution_history WHERE status = ?")) {
            statement.setString(1, ExecutionStatus.FAILURE.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Lecture des échecs impossible", exception);
        }
    }

    private List<ExecutionRecord> query(String sql) {
        List<ExecutionRecord> records = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            while (resultSet.next()) {
                records.add(map(resultSet));
            }
            return records;
        } catch (SQLException exception) {
            throw new IllegalStateException("Lecture de l'historique impossible", exception);
        }
    }

    private ExecutionRecord map(ResultSet resultSet) throws SQLException {
        Long scriptId = resultSet.getObject("script_id") == null ? null : resultSet.getLong("script_id");
        return new ExecutionRecord(
                resultSet.getLong("id"),
                scriptId,
                resultSet.getString("script_name"),
                ScriptLanguage.valueOf(resultSet.getString("language")),
                ExecutionStatus.valueOf(resultSet.getString("status")),
                Instant.parse(resultSet.getString("started_at")),
                resultSet.getLong("duration_millis"),
                resultSet.getInt("exit_code"),
                resultSet.getString("stdout"),
                resultSet.getString("stderr"),
                resultSet.getString("user_name")
        );
    }

    private String value(String text) {
        return text == null ? "" : text;
    }
}
