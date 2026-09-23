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

                statement.executeUpdate();

                try (
                        ResultSet keys =
                                statement.getGeneratedKeys()
                ) {

                    if (!keys.next()) {
                        throw new SQLException(
                                "Failed to retrieve generated evidence ID."
                        );
                    }

                    long evidenceId =
                            keys.getLong(1);

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
                }

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

                        UUID addedByUuid =
                                UUID.fromString(
                                        result.getString(
                                                "added_by_uuid"
                                        )
                                );

                        Instant createdAt =
                                result.getTimestamp(
                                        "created_at"
                                ).toInstant();

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

                        evidenceList.add(evidence);
                    }
                }

                return evidenceList;

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to retrieve case evidence.",
                        exception
                );
            }
        });
    }
}
