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

                statement.executeUpdate();

                try (
                        ResultSet keys =
                                statement.getGeneratedKeys()
                ) {

                    if (!keys.next()) {
                        throw new SQLException(
                                "Failed to retrieve generated note ID."
                        );
                    }

                    long noteId =
                            keys.getLong(1);

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
                }

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

                        UUID authorUuid =
                                UUID.fromString(
                                        result.getString(
                                                "author_uuid"
                                        )
                                );

                        Instant createdAt =
                                result.getTimestamp(
                                        "created_at"
                                ).toInstant();

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

                        notes.add(note);
                    }
                }

                return notes;

            } catch (SQLException exception) {

                throw new RuntimeException(
                        "Failed to retrieve case notes.",
                        exception
                );
            }
        });
    }
}
