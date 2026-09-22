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

                statement.executeUpdate();

                try (
                        ResultSet keys =
                                statement.getGeneratedKeys()
                ) {

                    if (!keys.next()) {
                        throw new SQLException(
                                "Failed to retrieve generated case ID."
                        );
                    }

                    long caseId =
                            keys.getLong(1);

                    Instant now =
                            Instant.now();

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
                        "Failed to retrieve case #" + caseId,
                        exception
                );
            }
        });
    }

    public CompletableFuture<List<Case>> getCases(
            int limit
    ) {

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
                        limit
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

    private Case mapCase(
            ResultSet result
    ) throws SQLException {

        UUID targetUuid =
                UUID.fromString(
                        result.getString(
                                "target_uuid"
                        )
                );

        UUID creatorUuid =
                UUID.fromString(
                        result.getString(
                                "creator_uuid"
                        )
                );

        CaseStatus status =
                CaseStatus.valueOf(
                        result.getString(
                                "status"
                        )
                );

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
