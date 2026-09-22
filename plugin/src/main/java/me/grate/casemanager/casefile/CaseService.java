package me.grate.casemanager.casefile;

import me.grate.casemanager.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CaseService {

    private final DatabaseManager database;

    public CaseService(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Case> createCase(
            UUID targetUuid,
            String targetName,
            UUID creatorUuid,
            String creatorName,
            String reason
    ) {

        return CompletableFuture.supplyAsync(() -> {

            String sql = """
                INSERT INTO cases
                (target_uuid, target_name, creator_uuid, creator_name, reason, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

            try (Connection connection = database.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

                statement.setString(1, targetUuid.toString());
                statement.setString(2, targetName);

                statement.setString(3, creatorUuid.toString());
                statement.setString(4, creatorName);

                statement.setString(5, reason);
                statement.setString(6, CaseStatus.OPEN.name());

                statement.executeUpdate();

                try (ResultSet keys = statement.getGeneratedKeys()) {

                    if (!keys.next()) {
                        throw new SQLException("Failed to retrieve generated case ID.");
                    }

                    long caseId = keys.getLong(1);

                    Instant now = Instant.now();

                    return new Case(
                            caseId,
                            targetUuid,
                            targetName,
                            creatorUuid,
                            creatorName,
                            reason,
                            CaseStatus.OPEN,
                            now,
                            now
                    );
                }

            } catch (SQLException exception) {
                throw new RuntimeException("Failed to create case.", exception);
            }
        });
    }
}
