package me.grate.casemanager.casefile;

import java.time.Instant;
import java.util.UUID;

public final class CaseNote {

    private final long id;
    private final long caseId;

    private final UUID authorUuid;
    private final String authorName;

    private final String content;

    private final Instant createdAt;

    public CaseNote(
            long id,
            long caseId,
            UUID authorUuid,
            String authorName,
            String content,
            Instant createdAt
    ) {
        this.id = id;
        this.caseId = caseId;
        this.authorUuid = authorUuid;
        this.authorName = authorName;
        this.content = content;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public long getCaseId() {
        return caseId;
    }

    public UUID getAuthorUuid() {
        return authorUuid;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
