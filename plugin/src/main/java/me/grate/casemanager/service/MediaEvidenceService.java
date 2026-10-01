package me.grate.casemanager.service;

import me.grate.casemanager.CaseManager;
import me.grate.casemanager.casefile.Case;
import me.grate.casemanager.casefile.CaseEvidence;
import me.grate.casemanager.casefile.CaseEvidenceService;
import me.grate.casemanager.storage.EvidenceStorage;
import me.grate.casemanager.storage.StorageManager;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class MediaEvidenceService {

    private static final Set<String> IMAGE_MIME_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/gif",
            "image/webp"
    );

    private static final Set<String> VIDEO_MIME_TYPES = Set.of(
            "video/mp4",
            "video/webm",
            "video/quicktime",
            "video/x-msvideo",
            "video/mpeg",
            "video/ogg"
    );

    private static final int DEFAULT_MAX_IMAGE_SIZE_MB = 10;
    private static final int DEFAULT_MAX_VIDEO_SIZE_MB = 100;

    private static final long DEFAULT_SIGNED_URL_EXPIRY_SECONDS = 3600L;

    private final CaseManager plugin;
    private final CaseEvidenceService caseEvidenceService;
    private final StorageManager storageManager;

    public MediaEvidenceService(
            CaseManager plugin,
            CaseEvidenceService caseEvidenceService,
            StorageManager storageManager
    ) {

        if (plugin == null) {
            throw new IllegalArgumentException(
                    "Plugin cannot be null."
            );
        }

        if (caseEvidenceService == null) {
            throw new IllegalArgumentException(
                    "CaseEvidenceService cannot be null."
            );
        }

        if (storageManager == null) {
            throw new IllegalArgumentException(
                    "StorageManager cannot be null."
            );
        }

        this.plugin = plugin;
        this.caseEvidenceService = caseEvidenceService;
        this.storageManager = storageManager;
    }

    /**
     * Uploads an image or video and attaches it to a case.
     *
     * The binary file is stored in the configured EvidenceStorage.
     * Only metadata is stored in the case_evidence database row.
     */
    public CompletableFuture<CaseEvidence> uploadMediaEvidence(
            long caseId,
            UUID addedByUuid,
            String addedByName,
            String type,
            String originalFilename,
            String mimeType,
            byte[] data
    ) {

        ValidationResult validation =
                validateUpload(
                        caseId,
                        addedByUuid,
                        addedByName,
                        type,
                        originalFilename,
                        mimeType,
                        data
                );

        if (!validation.valid()) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            validation.message()
                    )
            );
        }

        String normalizedMimeType =
                normalizeMimeType(mimeType);

        String normalizedType =
                normalizeEvidenceType(type);

        long maximumSize =
                getMaximumFileSize(
                        normalizedMimeType
                );

        if (data.length > maximumSize) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "The uploaded file is too large. " +
                                    "Maximum allowed size is " +
                                    formatBytes(maximumSize) +
                                    "."
                    )
            );
        }

        if (!storageManager.isAvailable()) {

            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "Evidence storage is not available."
                    )
            );
        }

        return plugin.getCaseService()
                .getCase(caseId)
                .thenCompose(caseFile -> {

                    if (caseFile == null) {

                        return CompletableFuture.failedFuture(
                                new IllegalArgumentException(
                                        "Case #" +
                                                caseId +
                                                " does not exist."
                                )
                        );
                    }

                    String storagePath =
                            createStoragePath(
                                    caseFile,
                                    normalizedType,
                                    originalFilename
                            );

                    EvidenceStorage storage =
                            storageManager.getStorage();

                    return storage.upload(
                                    storagePath,
                                    data,
                                    normalizedMimeType
                            )
                            .thenCompose(uploadResult ->

                                    saveEvidenceMetadata(
                                            caseFile,
                                            addedByUuid,
                                            addedByName,
                                            normalizedType,
                                            originalFilename,
                                            normalizedMimeType,
                                            uploadResult
                                    )
                            )
                            .exceptionallyCompose(exception ->

                                    rollbackUpload(
                                            storage,
                                            storagePath,
                                            exception
                                    )
                            );
                });
    }

    private CompletableFuture<CaseEvidence> saveEvidenceMetadata(
            Case caseFile,
            UUID addedByUuid,
            String addedByName,
            String type,
            String originalFilename,
            String mimeType,
            EvidenceStorage.StorageUploadResult uploadResult
    ) {

        return caseEvidenceService.addMediaEvidence(
                caseFile.getId(),
                addedByUuid,
                addedByName,
                type,
                createEvidenceContent(
                        originalFilename,
                        mimeType
                ),
                uploadResult.path(),
                originalFilename,
                mimeType,
                uploadResult.fileSize()
        );
    }
    /**
     * If the database operation fails after the storage upload,
     * remove the uploaded object so we do not leave orphaned files
     * inside Supabase Storage.
     */
    private CompletableFuture<CaseEvidence> rollbackUpload(
            EvidenceStorage storage,
            String storagePath,
            Throwable originalException
    ) {

        Throwable actualException =
                unwrapException(originalException);

        return storage.delete(
                        storagePath
                )
                .handle(
                        (deleted, deleteException) -> {

                            if (deleteException != null) {

                                plugin.getLogger().warning(
                                        "Failed to roll back uploaded " +
                                                "evidence file '" +
                                                storagePath +
                                                "': " +
                                                getRootMessage(
                                                        deleteException
                                                )
                                );
                            }

                            return (CaseEvidence) null;
                        }
                )
                .thenCompose(
                        ignored ->
                                CompletableFuture.failedFuture(
                                        actualException
                                )
                );
    }

    /**
     * Creates a signed URL for private evidence.
     *
     * This does not expose the permanent Supabase storage path.
     */
    public CompletableFuture<String> createSignedUrl(
            CaseEvidence evidence
    ) {

        if (evidence == null) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Evidence cannot be null."
                    )
            );
        }

        if (!evidence.hasMedia()) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "The supplied evidence does not contain media."
                    )
            );
        }

        if (!storageManager.isAvailable()) {

            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "Evidence storage is not available."
                    )
            );
        }

        long expiry =
                Math.max(
                        1L,
                        plugin.getConfig().getLong(
                                "storage.signed-url-expiry-seconds",
                                DEFAULT_SIGNED_URL_EXPIRY_SECONDS
                        )
                );

        return storageManager
                .getStorage()
                .createSignedUrl(
                        evidence.getStoragePath(),
                        expiry
                );
    }

    /**
     * Returns a public URL when the configured storage provider
     * supports public objects.
     *
     * This should normally NOT be used for private moderation evidence.
     */
    public String getPublicUrl(
            CaseEvidence evidence
    ) {

        if (evidence == null ||
                !evidence.hasMedia()) {

            return null;
        }

        if (!storageManager.isAvailable()) {

            return null;
        }

        return storageManager
                .getStorage()
                .getPublicUrl(
                        evidence.getStoragePath()
                );
    }

    /**
     * Deletes the physical media object from storage.
     *
     * The database evidence record is intentionally not deleted here.
     * Evidence-record deletion will be handled by the evidence lifecycle
     * service so that database/timeline cleanup remains centralized.
     */
    public CompletableFuture<Boolean> deleteStoredMedia(
            CaseEvidence evidence
    ) {

        if (evidence == null) {

            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Evidence cannot be null."
                    )
            );
        }

        if (!evidence.hasMedia()) {

            return CompletableFuture.completedFuture(
                    true
            );
        }

        if (!storageManager.isAvailable()) {

            return CompletableFuture.failedFuture(
                    new IllegalStateException(
                            "Evidence storage is not available."
                    )
            );
        }

        return storageManager
                .getStorage()
                .delete(
                        evidence.getStoragePath()
                );
    }
    /**
     * Checks whether the supplied MIME type is an image supported
     * by CaseManager.
     */
    public boolean isSupportedImage(
            String mimeType
    ) {

        if (mimeType == null) {
            return false;
        }

        return IMAGE_MIME_TYPES.contains(
                normalizeMimeType(mimeType)
        );
    }

    /**
     * Checks whether the supplied MIME type is a video supported
     * by CaseManager.
     */
    public boolean isSupportedVideo(
            String mimeType
    ) {

        if (mimeType == null) {
            return false;
        }

        return VIDEO_MIME_TYPES.contains(
                normalizeMimeType(mimeType)
        );
    }

    /**
     * Checks whether the MIME type is supported by the media
     * evidence system.
     */
    public boolean isSupportedMimeType(
            String mimeType
    ) {

        return isSupportedImage(mimeType)
                || isSupportedVideo(mimeType);
    }

    /**
     * Returns the configured maximum size for the supplied MIME type.
     */
    public long getMaximumFileSize(
            String mimeType
    ) {

        String normalized =
                normalizeMimeType(mimeType);

        if (IMAGE_MIME_TYPES.contains(normalized)) {

            long megabytes =
                    Math.max(
                            1L,
                            plugin.getConfig().getLong(
                                    "storage.max-image-size-mb",
                                    DEFAULT_MAX_IMAGE_SIZE_MB
                            )
                    );

            return megabytesToBytes(
                    megabytes
            );
        }

        if (VIDEO_MIME_TYPES.contains(normalized)) {

            long megabytes =
                    Math.max(
                            1L,
                            plugin.getConfig().getLong(
                                    "storage.max-video-size-mb",
                                    DEFAULT_MAX_VIDEO_SIZE_MB
                            )
                    );

            return megabytesToBytes(
                    megabytes
            );
        }

        return 0L;
    }

    private long megabytesToBytes(
            long megabytes
    ) {

        if (megabytes <= 0) {

            return 0L;
        }

        long max =
                Long.MAX_VALUE / 1024L / 1024L;

        if (megabytes > max) {

            return Long.MAX_VALUE;
        }

        return megabytes *
                1024L *
                1024L;
    }

    private String createStoragePath(
            Case caseFile,
            String type,
            String originalFilename
    ) {

        String safeFilename =
                sanitizeFilename(
                        originalFilename
                );

        String mediaType =
                type.toLowerCase(
                        Locale.ROOT
                );

        return "cases/" +
                caseFile.getId() +
                "/media/" +
                mediaType +
                "/" +
                UUID.randomUUID() +
                "-" +
                safeFilename;
    }
    private ValidationResult validateUpload(
            long caseId,
            UUID addedByUuid,
            String addedByName,
            String type,
            String originalFilename,
            String mimeType,
            byte[] data
    ) {

        if (caseId <= 0) {

            return invalid(
                    "Case ID must be greater than zero."
            );
        }

        if (addedByUuid == null) {

            return invalid(
                    "Evidence author UUID cannot be null."
            );
        }

        if (addedByName == null ||
                addedByName.isBlank()) {

            return invalid(
                    "Evidence author name cannot be empty."
            );
        }

        if (type == null ||
                type.isBlank()) {

            return invalid(
                    "Evidence type cannot be empty."
            );
        }

        String normalizedType =
                normalizeEvidenceType(type);

        if (!normalizedType.equals("SCREENSHOT") &&
                !normalizedType.equals("VIDEO")) {

            return invalid(
                    "Media evidence type must be SCREENSHOT or VIDEO."
            );
        }

        if (originalFilename == null ||
                originalFilename.isBlank()) {

            return invalid(
                    "Original filename cannot be empty."
            );
        }

        if (mimeType == null ||
                mimeType.isBlank()) {

            return invalid(
                    "MIME type cannot be empty."
            );
        }

        String normalizedMimeType =
                normalizeMimeType(mimeType);

        if (!isSupportedMimeType(
                normalizedMimeType
        )) {

            return invalid(
                    "Unsupported media MIME type: " +
                            normalizedMimeType
            );
        }

        if (normalizedType.equals("SCREENSHOT") &&
                !isSupportedImage(
                        normalizedMimeType
                )) {

            return invalid(
                    "SCREENSHOT evidence must use an image MIME type."
            );
        }

        if (normalizedType.equals("VIDEO") &&
                !isSupportedVideo(
                        normalizedMimeType
                )) {

            return invalid(
                    "VIDEO evidence must use a video MIME type."
            );
        }

        if (data == null ||
                data.length == 0) {

            return invalid(
                    "Uploaded file cannot be empty."
            );
        }

        return valid();
    }

    private String normalizeEvidenceType(
            String type
    ) {

        return type
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }

    private String normalizeMimeType(
            String mimeType
    ) {

        String normalized =
                mimeType
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        int semicolonIndex =
                normalized.indexOf(';');

        if (semicolonIndex >= 0) {

            normalized =
                    normalized.substring(
                            0,
                            semicolonIndex
                    );
        }

        return normalized.trim();
    }

    private String sanitizeFilename(
            String filename
    ) {

        String sanitized =
                filename
                        .replace('\\', '_')
                        .replace('/', '_')
                        .replaceAll(
                                "[^a-zA-Z0-9._-]",
                                "_"
                        );

        while (sanitized.contains("..")) {

            sanitized =
                    sanitized.replace(
                            "..",
                            "."
                    );
        }

        if (sanitized.isBlank()) {

            sanitized =
                    "evidence";
        }

        if (sanitized.length() > 120) {

            int extensionIndex =
                    sanitized.lastIndexOf('.');

            if (extensionIndex > 0 &&
                    extensionIndex < sanitized.length() - 1) {

                String extension =
                        sanitized.substring(
                                extensionIndex
                        );

                int maximumBaseLength =
                        120 -
                                extension.length();

                sanitized =
                        sanitized.substring(
                                0,
                                Math.max(
                                        1,
                                        maximumBaseLength
                                )
                        ) +
                                extension;

            } else {

                sanitized =
                        sanitized.substring(
                                0,
                                120
                        );
            }
        }

        return sanitized;
    }
    private String createEvidenceContent(
            String originalFilename,
            String mimeType
    ) {

        /*
         * The actual binary file lives in Supabase Storage.
         *
         * The content field remains useful for displaying a short
         * description in places that expect normal evidence content.
         *
         * The storage_path column is the authoritative media location.
         */
        return "Uploaded media: " +
                originalFilename +
                " (" +
                mimeType +
                ")";
    }

    private ValidationResult valid() {

        return new ValidationResult(
                true,
                null
        );
    }

    private ValidationResult invalid(
            String message
    ) {

        return new ValidationResult(
                false,
                message
        );
    }

    private Throwable unwrapException(
            Throwable throwable
    ) {

        if (throwable == null) {

            return new RuntimeException(
                    "Unknown media evidence error."
            );
        }

        Throwable current =
                throwable;

        while (
                current.getCause() != null
                        &&
                (
                                current instanceof java.util.concurrent.CompletionException
                                        ||
                                current instanceof java.util.concurrent.ExecutionException
                        )
        ) {

            current =
                    current.getCause();
        }

        return current;
    }

    private String getRootMessage(
            Throwable throwable
    ) {

        if (throwable == null) {

            return "Unknown error";
        }

        Throwable current =
                throwable;

        while (current.getCause() != null) {

            current =
                    current.getCause();
        }

        String message =
                current.getMessage();

        if (message == null ||
                message.isBlank()) {

            return current
                    .getClass()
                    .getSimpleName();
        }

        return message;
    }

    private String formatBytes(
            long bytes
    ) {

        if (bytes < 1024L) {

            return bytes + " B";
        }

        if (bytes < 1024L * 1024L) {

            return String.format(
                    Locale.ROOT,
                    "%.2f KB",
                    bytes / 1024.0
            );
        }

        if (bytes < 1024L * 1024L * 1024L) {

            return String.format(
                    Locale.ROOT,
                    "%.2f MB",
                    bytes /
                            (1024.0 * 1024.0)
            );
        }

        return String.format(
                Locale.ROOT,
                "%.2f GB",
                bytes /
                        (1024.0 * 1024.0 * 1024.0)
        );
    }

    public boolean isStorageAvailable() {

        return storageManager.isAvailable();
    }

    public String getStorageProviderName() {

        return storageManager.getProviderName();
    }

    public EvidenceStorage getStorage() {

        return storageManager.getStorage();
    }

    private record ValidationResult(
            boolean valid,
            String message
    ) {
    }
}