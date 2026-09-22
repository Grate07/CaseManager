package me.grate.casemanager.casefile;

import java.time.Instant;
import java.util.UUID;

public final class CaseTimelineEntry {

    private final long id;
    private final long caseId;

    private final UUID actorUuid;
    private final String actorName;

    private final String action;
    private final String details;

    private final Instant createdAt;

    public CaseTimelineEntry(
            long id,
            long caseId,
            UUID actorUuid,
            String actorName,
            String action,
            String details,
            Instant createdAt
    ) {
        this.id = id;
        this.caseId = caseId;
        this.actorUuid = actorUuid;
        this.actorName = actorName;
        this.action = action;
        this.details = details;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public long getCaseId() {
        return caseId;
    }

    public UUID getActorUuid() {
        return actorUuid;
    }

    public String getActorName() {
        return actorName;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
