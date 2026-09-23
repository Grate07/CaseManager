package me.grate.casemanager.casefile;

import java.time.Instant;
import java.util.UUID;

public final class CaseInvestigator {

    private final long caseId;

    private final UUID investigatorUuid;
    private final String investigatorName;

    private final Instant assignedAt;

    public CaseInvestigator(
            long caseId,
            UUID investigatorUuid,
            String investigatorName,
            Instant assignedAt
    ) {
        this.caseId = caseId;
        this.investigatorUuid = investigatorUuid;
        this.investigatorName = investigatorName;
        this.assignedAt = assignedAt;
    }

    public long getCaseId() {
        return caseId;
    }

    public UUID getInvestigatorUuid() {
        return investigatorUuid;
    }

    public String getInvestigatorName() {
        return investigatorName;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}