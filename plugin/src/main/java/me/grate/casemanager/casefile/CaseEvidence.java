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

    /*
     * Media metadata.
     *
     * These fields describe a file stored externally, such as
     * in Supabase Storage. The actual binary file is NOT stored
     * inside this object or PostgreSQL.
     */
    private final String storagePath;
    private final String originalFilename;
    private final String mimeType;
    private final Long fileSize;

    private final Instant createdAt;

    public CaseEvidence(
            long id,
            long caseId,
            UUID addedByUuid,
            String addedByName,
            String type,
            String content,
            String storagePath,
            String originalFilename,
            String mimeType,
            Long fileSize,
            Instant createdAt
    ) {
        this.id = id;
        this.caseId = caseId;
        this.addedByUuid = addedByUuid;
        this.addedByName = addedByName;
        this.type = type;
        this.content = content;
        this.storagePath = storagePath;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
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

    /**
     * Returns the path of the associated file inside external storage.
     *
     * Example:
     * case-152/screenshot-001.png
     */
    public String getStoragePath() {
        return storagePath;
    }

    /**
     * Returns the original filename supplied by the uploader.
     *
     * Example:
     * screenshot.png
     */
    public String getOriginalFilename() {
        return originalFilename;
    }

    /**
     * Returns the MIME type of the associated file.
     *
     * Example:
     * image/png
     * video/mp4
     */
    public String getMimeType() {
        return mimeType;
    }

    /**
     * Returns the file size in bytes.
     *
     * This is nullable because normal text evidence does not
     * have an associated file.
     */
    public Long getFileSize() {
        return fileSize;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Returns true when this evidence has an associated
     * externally stored file.
     */
    public boolean hasMedia() {
        return storagePath != null && !storagePath.isBlank();
    }
}