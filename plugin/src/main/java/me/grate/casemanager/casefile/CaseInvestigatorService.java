package me.grate.casemanager.casefile;

import me.grate.casemanager.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CaseInvestigatorService {

    private final DatabaseManager database;
    private final CaseTimelineService timelineService;

    public CaseInvestigatorService(
            DatabaseManager database,
            CaseTimelineService timelineService
    ) {
        this.database = database;
        this.timelineService = timelineService;
    }

    public CompletableFuture<CaseInvestigator> assignInvestigator(
            long caseId,
            UUID investigatorUuid,
            String investigatorName,
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

        if (investigatorUuid == null) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Investigator UUID cannot be null."
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

        if (investigatorName == null ||
                investigatorName.isBlank()) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Investigator name cannot be empty."
                    )
            );
        }

        return CompletableFuture.supplyAsync(() -> {

            String sql = """
                    INSERT INTO case_investigators
                    (
                        case_id,
                        investigator_uuid,
                        investigator_name
                    )
                    VALUES (?, ?, ?)
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

                statement.setString(
                        2,
                        investigatorUuid.toString()
                );

                statement.setString(
                        3,
                        investigatorName
                );

                int affected =
                        statement.executeUpdate();

                if (affected != 1) {

                    throw new SQLException(
                            "Investigator assignment affected " +
                                    affected +
                                    " rows."
                    );
                }

                CaseInvestigator investigator =
                        new CaseInvestigator(
                                caseId,
                                investigatorUuid,
                                investigatorName,
                                Instant.now()
                        );

                timelineService.addEntry(
                        caseId,
                        actorUuid,
                        actorName,
                        "INVESTIGATOR_ASSIGNED",
                        "Investigator " +
                                investigatorName +
                                " was assigned."
                ).join();

                return investigator;

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to assign investigator.",
                        exception
                );
            }
        });
    }

    public CompletableFuture<Boolean> unassignInvestigator(
            long caseId,
            UUID investigatorUuid,
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

        if (investigatorUuid == null) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Investigator UUID cannot be null."
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

            String investigatorName = null;

            String findSql = """
                    SELECT investigator_name
                    FROM case_investigators
                    WHERE case_id = ?
                    AND investigator_uuid = ?
                    """;

            String deleteSql = """
                    DELETE FROM case_investigators
                    WHERE case_id = ?
                    AND investigator_uuid = ?
                    """;

            try (
                    Connection connection =
                            database.getConnection();

                    PreparedStatement findStatement =
                            connection.prepareStatement(findSql)
            ) {

                findStatement.setLong(
                        1,
                        caseId
                );

                findStatement.setString(
                        2,
                        investigatorUuid.toString()
                );

                try (
                        ResultSet result =
                                findStatement.executeQuery()
                ) {

                    if (!result.next()) {
                        return false;
                    }

                    investigatorName =
                            result.getString(
                                    "investigator_name"
                            );
                }

                try (
                        PreparedStatement deleteStatement =
                                connection.prepareStatement(
                                        deleteSql
                                )
                ) {

                    deleteStatement.setLong(
                            1,
                            caseId
                    );

                    deleteStatement.setString(
                            2,
                            investigatorUuid.toString()
                    );

                    int affected =
                            deleteStatement.executeUpdate();

                    if (affected != 1) {
                        return false;
                    }
                }

                String displayName =
                        investigatorName == null ||
                                investigatorName.isBlank()
                                ? investigatorUuid.toString()
                                : investigatorName;

                timelineService.addEntry(
                        caseId,
                        actorUuid,
                        actorName,
                        "INVESTIGATOR_UNASSIGNED",
                        "Investigator " +
                                displayName +
                                " was unassigned."
                ).join();

                return true;

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to unassign investigator.",
                        exception
                );
            }
        });
    }

    public CompletableFuture<List<CaseInvestigator>> getInvestigators(
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
                        case_id,
                        investigator_uuid,
                        investigator_name,
                        assigned_at
                    FROM case_investigators
                    WHERE case_id = ?
                    ORDER BY assigned_at ASC
                    """;

            List<CaseInvestigator> investigators =
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

                        String uuidString =
                                result.getString(
                                        "investigator_uuid"
                                );

                        if (uuidString == null ||
                                uuidString.isBlank()) {

                            throw new SQLException(
                                    "Investigator record contains a missing UUID."
                            );
                        }

                        UUID investigatorUuid;

                        try {

                            investigatorUuid =
                                    UUID.fromString(
                                            uuidString
                                    );

                        } catch (IllegalArgumentException exception) {

                            throw new SQLException(
                                    "Investigator record contains an invalid UUID.",
                                    exception
                            );
                        }

                        Timestamp timestamp =
                                result.getTimestamp(
                                        "assigned_at"
                                );

                        if (timestamp == null) {

                            throw new SQLException(
                                    "Investigator record contains a missing assignment timestamp."
                            );
                        }

                        Instant assignedAt =
                                timestamp.toInstant();

                        CaseInvestigator investigator =
                                new CaseInvestigator(
                                        result.getLong(
                                                "case_id"
                                        ),
                                        investigatorUuid,
                                        result.getString(
                                                "investigator_name"
                                        ),
                                        assignedAt
                                );

                        investigators.add(
                                investigator
                        );
                    }
                }

                return investigators;

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to retrieve investigators for case #" +
                                caseId,
                        exception
                );
            }
        });
    }
}