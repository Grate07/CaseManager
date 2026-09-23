package me.grate.casemanager.casefile;

import me.grate.casemanager.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CaseTimelineService {

    private final DatabaseManager database;

    public CaseTimelineService(
            DatabaseManager database
    ) {
        this.database = database;
    }

    public CompletableFuture<Void> addEntry(
            long caseId,
            UUID actorUuid,
            String actorName,
            String action,
            String details
    ) {

        if (caseId <= 0) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Case ID must be greater than zero."
                    )
            );
        }

        if (action == null ||
                action.isBlank()) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Timeline action cannot be empty."
                    )
            );
        }

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

            try (
                    Connection connection =
                            database.getConnection();

                    PreparedStatement statement =
                            connection.prepareStatement(sql)
            ) {

                statement.setLong(
                        1,
                        caseId
                );

                if (actorUuid == null) {

                    statement.setNull(
                            2,
                            Types.VARCHAR
                    );

                } else {

                    statement.setString(
                            2,
                            actorUuid.toString()
                    );
                }

                statement.setString(
                        3,
                        actorName
                );

                statement.setString(
                        4,
                        action
                );

                statement.setString(
                        5,
                        details
                );

                int affected =
                        statement.executeUpdate();

                if (affected != 1) {

                    throw new SQLException(
                            "Timeline entry insert affected " +
                                    affected +
                                    " rows."
                    );
                }

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

        if (caseId <= 0) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Case ID must be greater than zero."
                    )
            );
        }

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

            try (
                    Connection connection =
                            database.getConnection();

                    PreparedStatement statement =
                            connection.prepareStatement(sql)
            ) {

                statement.setLong(
                        1,
                        caseId
                );

                try (
                        ResultSet result =
                                statement.executeQuery()
                ) {

                    while (result.next()) {

                        String actorUuidString =
                                result.getString(
                                        "actor_uuid"
                                );

                        UUID actorUuid = null;

                        if (actorUuidString != null &&
                                !actorUuidString.isBlank()) {

                            try {

                                actorUuid =
                                        UUID.fromString(
                                                actorUuidString
                                        );

                            } catch (IllegalArgumentException exception) {

                                throw new SQLException(
                                        "Timeline entry #" +
                                                result.getLong("id") +
                                                " contains an invalid actor UUID.",
                                        exception
                                );
                            }
                        }

                        Timestamp timestamp =
                                result.getTimestamp(
                                        "created_at"
                                );

                        if (timestamp == null) {

                            throw new SQLException(
                                    "Timeline entry #" +
                                            result.getLong("id") +
                                            " has no creation timestamp."
                            );
                        }

                        Instant createdAt =
                                timestamp.toInstant();

                        CaseTimelineEntry entry =
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
                                );

                        entries.add(
                                entry
                        );
                    }
                }

                return entries;

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to retrieve timeline for case #" +
                                caseId,
                        exception
                );
            }
        });
    }
}