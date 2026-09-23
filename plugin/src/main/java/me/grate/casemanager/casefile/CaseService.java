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

public final class CaseService {

    private final DatabaseManager database;
    private final CaseTimelineService timelineService;

    public CaseService(
            DatabaseManager database,
            CaseTimelineService timelineService
    ) {
        this.database = database;
        this.timelineService = timelineService;
    }

    public CompletableFuture<Case> createCase(
            UUID targetUuid,
            String targetName,
            UUID creatorUuid,
            String creatorName,
            String reason
    ) {

        if (targetUuid == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Target UUID cannot be null."
                    )
            );
        }

        if (creatorUuid == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Creator UUID cannot be null."
                    )
            );
        }

        if (reason == null || reason.isBlank()) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Case reason cannot be empty."
                    )
            );
        }

        return CompletableFuture.supplyAsync(() -> {

            String sql = """
                    INSERT INTO cases
                    (
                        target_uuid,
                        target_name,
                        creator_uuid,
                        creator_name,
                        reason,
                        status
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """;

            try (
                    Connection connection =
                            database.getConnection();

                    PreparedStatement statement =
                            connection.prepareStatement(
                                    sql,
                                    java.sql.Statement.RETURN_GENERATED_KEYS
                            )
            ) {

                statement.setString(
                        1,
                        targetUuid.toString()
                );

                statement.setString(
                        2,
                        targetName
                );

                statement.setString(
                        3,
                        creatorUuid.toString()
                );

                statement.setString(
                        4,
                        creatorName
                );

                statement.setString(
                        5,
                        reason
                );

                statement.setString(
                        6,
                        CaseStatus.OPEN.name()
                );

                int affected =
                        statement.executeUpdate();

                if (affected != 1) {
                    throw new SQLException(
                            "Case insert affected an unexpected number of rows: "
                                    + affected
                    );
                }

                long caseId;

                try (
                        ResultSet keys =
                                statement.getGeneratedKeys()
                ) {

                    if (!keys.next()) {
                        throw new SQLException(
                                "Failed to retrieve generated case ID."
                        );
                    }

                    caseId =
                            keys.getLong(1);
                }

                Instant now =
                        Instant.now();

                Case caseFile =
                        new Case(
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

                /*
                 * Record creation in the case timeline.
                 *
                 * This is deliberately waited for here so that
                 * a successfully returned case also has its
                 * creation event recorded.
                 */
                timelineService.addEntry(
                        caseId,
                        creatorUuid,
                        creatorName,
                        "CASE_CREATED",
                        "Case created."
                ).join();

                return caseFile;

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to create case.",
                        exception
                );
            }
        });
    }

    public CompletableFuture<Case> getCase(
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
                        target_uuid,
                        target_name,
                        creator_uuid,
                        creator_name,
                        reason,
                        status,
                        created_at,
                        updated_at
                    FROM cases
                    WHERE id = ?
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

                try (
                        ResultSet result =
                                statement.executeQuery()
                ) {

                    if (!result.next()) {
                        return null;
                    }

                    return mapCase(result);
                }

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to retrieve case #" +
                                caseId,
                        exception
                );
            }
        });
    }

    public CompletableFuture<List<Case>> getCases(
            int limit
    ) {

        if (limit <= 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Case list limit must be greater than zero."
                    )
            );
        }

        /*
         * Prevent accidental requests for an enormous number
         * of rows.
         */
        int safeLimit =
                Math.min(
                        limit,
                        500
                );

        return CompletableFuture.supplyAsync(() -> {

            String sql = """
                    SELECT
                        id,
                        target_uuid,
                        target_name,
                        creator_uuid,
                        creator_name,
                        reason,
                        status,
                        created_at,
                        updated_at
                    FROM cases
                    ORDER BY id DESC
                    LIMIT ?
                    """;

            List<Case> cases =
                    new ArrayList<>();

            try (
                    Connection connection =
                            database.getConnection();

                    PreparedStatement statement =
                            connection.prepareStatement(sql)
            ) {

                statement.setInt(
                        1,
                        safeLimit
                );

                try (
                        ResultSet result =
                                statement.executeQuery()
                ) {

                    while (result.next()) {

                        cases.add(
                                mapCase(result)
                        );
                    }
                }

                return cases;

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to retrieve cases.",
                        exception
                );
            }
        });
    }

    public CompletableFuture<Case> updateStatus(
            long caseId,
            CaseStatus newStatus,
            UUID actorUuid,
            String actorName
    ) {

        if (caseId <= 0) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Case ID must be greater than zero."
                    )
            );
        }

        if (newStatus == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "New case status cannot be null."
                    )
            );
        }

        if (actorUuid == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Actor UUID cannot be null."
                    )
            );
        }

        return CompletableFuture.supplyAsync(() -> {

            String selectSql = """
                    SELECT
                        id,
                        target_uuid,
                        target_name,
                        creator_uuid,
                        creator_name,
                        reason,
                        status,
                        created_at,
                        updated_at
                    FROM cases
                    WHERE id = ?
                    """;

            String updateSql = """
                    UPDATE cases
                    SET status = ?
                    WHERE id = ?
                    """;

            try (
                    Connection connection =
                            database.getConnection();

                    PreparedStatement selectStatement =
                            connection.prepareStatement(
                                    selectSql
                            )
            ) {

                selectStatement.setLong(
                        1,
                        caseId
                );

                Case caseFile;

                try (
                        ResultSet result =
                                selectStatement.executeQuery()
                ) {

                    if (!result.next()) {
                        return null;
                    }

                    caseFile =
                            mapCase(result);
                }

                CaseStatus oldStatus =
                        caseFile.getStatus();

                /*
                 * Nothing needs to be changed when the case is
                 * already using the requested status.
                 */
                if (oldStatus == newStatus) {
                    return caseFile;
                }

                try (
                        PreparedStatement updateStatement =
                                connection.prepareStatement(
                                        updateSql
                                )
                ) {

                    updateStatement.setString(
                            1,
                            newStatus.name()
                    );

                    updateStatement.setLong(
                            2,
                            caseId
                    );

                    int affected =
                            updateStatement.executeUpdate();

                    if (affected != 1) {
                        throw new SQLException(
                                "Case status update affected " +
                                        affected +
                                        " rows."
                        );
                    }
                }

                caseFile.setStatus(
                        newStatus
                );

                timelineService.addEntry(
                        caseId,
                        actorUuid,
                        actorName,
                        "STATUS_CHANGED",
                        "Status changed from " +
                                oldStatus.name() +
                                " to " +
                                newStatus.name() +
                                "."
                ).join();

                return caseFile;

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to update case status.",
                        exception
                );
            }
        });
    }

    private Case mapCase(
            ResultSet result
    ) throws SQLException {

        String targetUuidString =
                result.getString(
                        "target_uuid"
                );

        String creatorUuidString =
                result.getString(
                        "creator_uuid"
                );

        if (targetUuidString == null ||
                creatorUuidString == null) {

            throw new SQLException(
                    "Case contains a null UUID."
            );
        }

        UUID targetUuid;

        UUID creatorUuid;

        try {

            targetUuid =
                    UUID.fromString(
                            targetUuidString
                    );

            creatorUuid =
                    UUID.fromString(
                            creatorUuidString
                    );

        } catch (IllegalArgumentException exception) {

            throw new SQLException(
                    "Case contains an invalid UUID.",
                    exception
            );
        }

        String statusString =
                result.getString(
                        "status"
                );

        if (statusString == null) {

            throw new SQLException(
                    "Case contains a null status."
            );
        }

        CaseStatus status;

        try {

            status =
                    CaseStatus.valueOf(
                            statusString.toUpperCase()
                    );

        } catch (IllegalArgumentException exception) {

            throw new SQLException(
                    "Case contains an invalid status: " +
                            statusString,
                    exception
            );
        }

        if (result.getTimestamp("created_at") == null ||
                result.getTimestamp("updated_at") == null) {

            throw new SQLException(
                    "Case contains a null timestamp."
            );
        }

        Instant createdAt =
                result.getTimestamp(
                        "created_at"
                ).toInstant();

        Instant updatedAt =
                result.getTimestamp(
                        "updated_at"
                ).toInstant();

        return new Case(
                result.getLong("id"),
                targetUuid,
                result.getString("target_name"),
                creatorUuid,
                result.getString("creator_name"),
                result.getString("reason"),
                status,
                createdAt,
                updatedAt
        );
    }
}