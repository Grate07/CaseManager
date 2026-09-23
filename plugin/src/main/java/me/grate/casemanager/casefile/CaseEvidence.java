package me.grate.casemanager.casefile;

import java.time.Instant;
import java.util.UUID;

public final class CaseEvidence {

    private final long id;
    private final long caseId;

    private final UUID addedByUuid;
    private final String addedByName;

    private final String type;
    private final String content;

    private final Instant createdAt;

    public CaseEvidence(
            long id,
            long caseId,
            UUID addedByUuid,
            String addedByName,
            String type,
            String content,
            Instant createdAt
    ) {
        this.id = id;
        this.caseId = caseId;
        this.addedByUuid = addedByUuid;
        this.addedByName = addedByName;
        this.type = type;
        this.content = content;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public long getCaseId() {
        return caseId;
    }

    public UUID getAddedByUuid() {
        return addedByUuid;
    }

    public String getAddedByName() {
        return addedByName;
    }

    public String getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
