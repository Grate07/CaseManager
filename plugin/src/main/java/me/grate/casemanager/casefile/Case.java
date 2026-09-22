package me.grate.casemanager.casefile;

import java.time.Instant;
import java.util.UUID;

public final class Case {

    private final long id;

    private final UUID targetUuid;
    private final String targetName;

    private final UUID creatorUuid;
    private final String creatorName;

    private final String reason;

    private CaseStatus status;

    private final Instant createdAt;
    private Instant updatedAt;

    public Case(
            long id,
            UUID targetUuid,
            String targetName,
            UUID creatorUuid,
            String creatorName,
            String reason,
            CaseStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = id;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.reason = reason;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public long getId() {
        return id;
    }

    public UUID getTargetUuid() {
        return targetUuid;
    }

    public String getTargetName() {
        return targetName;
    }

    public UUID getCreatorUuid() {
        return creatorUuid;
    }

    public String getCreatorName() {
        return creatorName;
    }

    public String getReason() {
        return reason;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(CaseStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
