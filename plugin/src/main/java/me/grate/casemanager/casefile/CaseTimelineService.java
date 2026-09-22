package me.grate.casemanager.casefile;

import me.grate.casemanager.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CaseTimelineService {

    private final DatabaseManager database;

    public CaseTimelineService(DatabaseManager database) {
        this.database = database;
    }

    public CompletableFuture<Void> addEntry(
            long caseId,
            UUID actorUuid,
            String actorName,
            String action,
            String details
    ) {

        return CompletableFuture.runAsync(() -> {

            String sql = """
                    INSERT INTO case_timeline
                    (
                        case_id,
                        actor_uuid,
                        actor_name,
                        action,
                        details
                    )
                    VALUES (?, ?, ?, ?, ?)
                    """;

            try (Connection connection = database.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setLong(1, caseId);
                statement.setString(
                        2,
                        actorUuid == null
                                ? null
                                : actorUuid.toString()
                );
                statement.setString(3, actorName);
                statement.setString(4, action);
                statement.setString(5, details);

                statement.executeUpdate();

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to add timeline entry.",
                        exception
                );
            }
        });
    }

    public CompletableFuture<List<CaseTimelineEntry>> getTimeline(
            long caseId
    ) {

        return CompletableFuture.supplyAsync(() -> {

            String sql = """
                    SELECT
                        id,
                        case_id,
                        actor_uuid,
                        actor_name,
                        action,
                        details,
                        created_at
                    FROM case_timeline
                    WHERE case_id = ?
                    ORDER BY id ASC
                    """;

            List<CaseTimelineEntry> entries =
                    new ArrayList<>();

            try (Connection connection = database.getConnection();
                 PreparedStatement statement =
                         connection.prepareStatement(sql)) {

                statement.setLong(1, caseId);

                try (ResultSet result =
                             statement.executeQuery()) {

                    while (result.next()) {

                        String actorUuidString =
                                result.getString("actor_uuid");

                        UUID actorUuid =
                                actorUuidString == null
                                        ? null
                                        : UUID.fromString(
                                                actorUuidString
                                        );

                        Instant createdAt =
                                result.getTimestamp(
                                        "created_at"
                                ).toInstant();

                        entries.add(
                                new CaseTimelineEntry(
                                        result.getLong("id"),
                                        result.getLong("case_id"),
                                        actorUuid,
                                        result.getString(
                                                "actor_name"
                                        ),
                                        result.getString(
                                                "action"
                                        ),
                                        result.getString(
                                                "details"
                                        ),
                                        createdAt
                                )
                        );
                    }
                }

                return entries;

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to retrieve case timeline.",
                        exception
                );
            }
        });
    }
}
