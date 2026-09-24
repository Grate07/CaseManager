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

public final class CaseEvidenceService {

    private final DatabaseManager database;
    private final CaseTimelineService timelineService;

    public CaseEvidenceService(
            DatabaseManager database,
            CaseTimelineService timelineService
    ) {
        this.database = database;
        this.timelineService = timelineService;
    }

    public CompletableFuture<CaseEvidence> addEvidence(
            long caseId,
            UUID addedByUuid,
            String addedByName,
            String type,
            String content
    ) {

        if (caseId <= 0) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Case ID must be greater than zero."
                    )
            );
        }

        if (addedByUuid == null) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Evidence author UUID cannot be null."
                    )
            );
        }

        if (type == null ||
                type.isBlank()) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Evidence type cannot be empty."
                    )
            );
        }

        if (content == null ||
                content.isBlank()) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Evidence content cannot be empty."
                    )
            );
        }

        return CompletableFuture.supplyAsync(() -> {

            String sql = """
                    INSERT INTO case_evidence
                    (
                        case_id,
                        added_by_uuid,
                        added_by_name,
                        type,
                        content
                    )
                    VALUES (?, ?, ?, ?, ?)
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

                statement.setLong(
                        1,
                        caseId
                );

                statement.setString(
                        2,
                        addedByUuid.toString()
                );

                statement.setString(
                        3,
                        addedByName
                );

                statement.setString(
                        4,
                        type
                );

                statement.setString(
                        5,
                        content
                );

                int affected =
                        statement.executeUpdate();

                if (affected != 1) {

                    throw new SQLException(
                            "Evidence insert affected " +
                                    affected +
                                    " rows."
                    );
                }

                long evidenceId;

                try (
                        ResultSet keys =
                                statement.getGeneratedKeys()
                ) {

                    if (!keys.next()) {

                        throw new SQLException(
                                "Failed to retrieve generated evidence ID."
                        );
                    }

                    evidenceId =
                            keys.getLong(1);
                }

                Instant createdAt =
                        Instant.now();

                CaseEvidence evidence =
                        new CaseEvidence(
                                evidenceId,
                                caseId,
                                addedByUuid,
                                addedByName,
                                type,
                                content,
                                createdAt
                        );

                timelineService.addEntry(
                        caseId,
                        addedByUuid,
                        addedByName,
                        "EVIDENCE_ADDED",
                        "Evidence #" +
                                evidenceId +
                                " added (" +
                                type +
                                ")."
                ).join();

                return evidence;

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to add case evidence.",
                        exception
                );
            }
        });
    }

    public CompletableFuture<List<CaseEvidence>> getEvidence(
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
                        added_by_uuid,
                        added_by_name,
                        type,
                        content,
                        created_at
                    FROM case_evidence
                    WHERE case_id = ?
                    ORDER BY id ASC
                    """;

            List<CaseEvidence> evidenceList =
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
                                        "added_by_uuid"
                                );

                        if (uuidString == null ||
                                uuidString.isBlank()) {

                            throw new SQLException(
                                    "Evidence #" +
                                            result.getLong("id") +
                                            " contains a missing author UUID."
                            );
                        }

                        UUID addedByUuid;

                        try {

                            addedByUuid =
                                    UUID.fromString(
                                            uuidString
                                    );

                        } catch (IllegalArgumentException exception) {

                            throw new SQLException(
                                    "Evidence #" +
                                            result.getLong("id") +
                                            " contains an invalid author UUID.",
                                    exception
                            );
                        }

                        Timestamp timestamp =
                                result.getTimestamp(
                                        "created_at"
                                );

                        if (timestamp == null) {

                            throw new SQLException(
                                    "Evidence #" +
                                            result.getLong("id") +
                                            " has no creation timestamp."
                            );
                        }

                        Instant createdAt =
                                timestamp.toInstant();

                        CaseEvidence evidence =
                                new CaseEvidence(
                                        result.getLong("id"),
                                        result.getLong("case_id"),
                                        addedByUuid,
                                        result.getString(
                                                "added_by_name"
                                        ),
                                        result.getString(
                                                "type"
                                        ),
                                        result.getString(
                                                "content"
                                        ),
                                        createdAt
                                );

                        evidenceList.add(
                                evidence
                        );
                    }
                }

                return evidenceList;

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to retrieve evidence for case #" +
                                caseId,
                        exception
                );
            }
        });
    }
}