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

public final class CaseNoteService {

    private final DatabaseManager database;
    private final CaseTimelineService timelineService;

    public CaseNoteService(
            DatabaseManager database,
            CaseTimelineService timelineService
    ) {
        this.database = database;
        this.timelineService = timelineService;
    }

    public CompletableFuture<CaseNote> addNote(
            long caseId,
            UUID authorUuid,
            String authorName,
            String content
    ) {

        if (caseId <= 0) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Case ID must be greater than zero."
                    )
            );
        }

        if (authorUuid == null) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Author UUID cannot be null."
                    )
            );
        }

        if (content == null ||
                content.isBlank()) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Note content cannot be empty."
                    )
            );
        }

        return CompletableFuture.supplyAsync(() -> {

            String sql = """
                    INSERT INTO case_notes
                    (
                        case_id,
                        author_uuid,
                        author_name,
                        content
                    )
                    VALUES (?, ?, ?, ?)
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
                        authorUuid.toString()
                );

                statement.setString(
                        3,
                        authorName
                );

                statement.setString(
                        4,
                        content
                );

                int affected =
                        statement.executeUpdate();

                if (affected != 1) {

                    throw new SQLException(
                            "Note insert affected " +
                                    affected +
                                    " rows."
                    );
                }

                long noteId;

                try (
                        ResultSet keys =
                                statement.getGeneratedKeys()
                ) {

                    if (!keys.next()) {

                        throw new SQLException(
                                "Failed to retrieve generated note ID."
                        );
                    }

                    noteId =
                            keys.getLong(1);
                }

                Instant createdAt =
                        Instant.now();

                CaseNote note =
                        new CaseNote(
                                noteId,
                                caseId,
                                authorUuid,
                                authorName,
                                content,
                                createdAt
                        );

                timelineService.addEntry(
                        caseId,
                        authorUuid,
                        authorName,
                        "NOTE_ADDED",
                        "Note #" +
                                noteId +
                                " added."
                ).join();

                return note;

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to add case note.",
                        exception
                );
            }
        });
    }

    public CompletableFuture<List<CaseNote>> getNotes(
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
                        author_uuid,
                        author_name,
                        content,
                        created_at
                    FROM case_notes
                    WHERE case_id = ?
                    ORDER BY id ASC
                    """;

            List<CaseNote> notes =
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

                        String authorUuidString =
                                result.getString(
                                        "author_uuid"
                                );

                        if (authorUuidString == null ||
                                authorUuidString.isBlank()) {

                            throw new SQLException(
                                    "Note #" +
                                            result.getLong("id") +
                                            " contains a missing author UUID."
                            );
                        }

                        UUID authorUuid;

                        try {

                            authorUuid =
                                    UUID.fromString(
                                            authorUuidString
                                    );

                        } catch (IllegalArgumentException exception) {

                            throw new SQLException(
                                    "Note #" +
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
                                    "Note #" +
                                            result.getLong("id") +
                                            " has no creation timestamp."
                            );
                        }

                        Instant createdAt =
                                timestamp.toInstant();

                        CaseNote note =
                                new CaseNote(
                                        result.getLong("id"),
                                        result.getLong("case_id"),
                                        authorUuid,
                                        result.getString(
                                                "author_name"
                                        ),
                                        result.getString(
                                                "content"
                                        ),
                                        createdAt
                                );

                        notes.add(
                                note
                        );
                    }
                }

                return notes;

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to retrieve notes for case #" +
                                caseId,
                        exception
                );
            }
        });
    }
}