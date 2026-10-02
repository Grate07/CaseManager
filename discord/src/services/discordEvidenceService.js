const crypto = require("crypto");
const path = require("path");

const database = require("../database/database");


/*
 * ============================================================
 * CaseManager — Discord Evidence Service
 * ============================================================
 *
 * Discord
 *   ↓
 * Attachment
 *   ↓
 * Download
 *   ↓
 * Validate
 *   ↓
 * Supabase Storage
 *   ↓
 * case_evidence
 *
 * This service intentionally uses the existing Supabase
 * PostgreSQL database and Storage bucket.
 * ============================================================
 */


/*
 * ------------------------------------------------------------
 * Configuration
 * ------------------------------------------------------------
 */

const SUPABASE_URL =
    String(
        process.env.CASEMANAGER_SUPABASE_URL ||
        ""
    ).replace(/\/+$/, "");


const SUPABASE_SECRET_KEY =
    String(
        process.env.CASEMANAGER_SUPABASE_SECRET_KEY ||
        ""
    ).trim();


const STORAGE_BUCKET =
    String(
        process.env.CASEMANAGER_EVIDENCE_BUCKET ||
        "case-evidence"
    ).trim();


/*
 * Default limits.
 *
 * These are intentionally conservative. The values can be
 * overridden with environment variables.
 */

const MAX_FILE_SIZE =
    parsePositiveInteger(
        process.env.CASEMANAGER_DISCORD_MAX_EVIDENCE_MB,
        100
    ) * 1024 * 1024;


const MAX_FILES_PER_UPLOAD =
    parsePositiveInteger(
        process.env.CASEMANAGER_DISCORD_MAX_EVIDENCE_FILES,
        5
    );


const DOWNLOAD_TIMEOUT_MS =
    parsePositiveInteger(
        process.env.CASEMANAGER_DISCORD_DOWNLOAD_TIMEOUT_MS,
        60_000
    );


/*
 * Discord CDN attachment URLs are HTTPS URLs.
 * We only permit HTTPS here.
 */

const ALLOWED_PROTOCOL =
    "https:";


/*
 * MIME types accepted by the Discord evidence layer.
 *
 * The Minecraft-side MediaEvidenceService remains the
 * authoritative storage implementation. This list simply
 * prevents obviously unsupported uploads from reaching
 * Storage.
 */

const ALLOWED_MIME_TYPES = new Set([
    /*
     * Images
     */
    "image/png",
    "image/jpeg",
    "image/jpg",
    "image/webp",
    "image/gif",

    /*
     * Video
     */
    "video/mp4",
    "video/webm",
    "video/quicktime",

    /*
     * Text / logs
     */
    "text/plain",
    "text/csv",

    /*
     * Documents
     */
    "application/pdf",

    /*
     * Archives commonly used for exported evidence.
     */
    "application/zip"
]);


/*
 * ============================================================
 * Public API
 * ============================================================
 */


/**
 * Upload Discord attachments as CaseManager evidence.
 *
 * @param {Object} options
 * @param {number} options.caseId
 * @param {Array} options.attachments
 * @param {string} options.description
 * @param {string} options.actorId
 * @param {string} options.actorName
 *
 * @returns {Promise<Array>}
 */
async function uploadEvidence({
    caseId,
    attachments,
    description = "",
    actorId = null,
    actorName = "Discord User"
}) {

    const normalizedCaseId =
        normalizeCaseId(caseId);


    if (
        !Array.isArray(attachments) ||
        attachments.length === 0
    ) {

        throw new Error(
            "No evidence files were uploaded."
        );
    }


    if (
        attachments.length >
        MAX_FILES_PER_UPLOAD
    ) {

        throw new Error(
            `You can upload at most ${MAX_FILES_PER_UPLOAD} evidence files at once.`
        );
    }


    await requireConfiguration();


    /*
     * Make sure the case exists before downloading
     * potentially large attachments.
     */

    const caseResult =
        await database.query(
            `
            SELECT id
            FROM cases
            WHERE id = $1
            LIMIT 1
            `,
            [
                normalizedCaseId
            ]
        );


    if (
        caseResult.rows.length === 0
    ) {

        throw new Error(
            `Case #${normalizedCaseId} was not found.`
        );
    }


    const uploadedEvidence = [];


    try {

        for (
            const attachment of attachments
        ) {

            const evidence =
                await uploadSingleAttachment({
                    caseId:
                        normalizedCaseId,

                    attachment,

                    description,

                    actorId,

                    actorName
                });


            uploadedEvidence.push(
                evidence
            );
        }


        return uploadedEvidence;

    } catch (error) {

        /*
         * If one upload fails, remove any files that were
         * already uploaded during this batch.
         */

        await cleanupUploadedEvidence(
            uploadedEvidence
        );


        throw error;
    }
}


/**
 * Upload one Discord attachment.
 */
async function uploadSingleAttachment({
    caseId,
    attachment,
    description,
    actorId,
    actorName
}) {

    validateAttachment(
        attachment
    );


    const attachmentUrl =
        String(
            attachment.url ||
            attachment.proxyURL ||
            ""
        );


    const response =
        await downloadAttachment(
            attachmentUrl
        );


    const mimeType =
        normalizeMimeType(
            response.mimeType ||
            attachment.contentType
        );


    const fileBuffer =
        response.buffer;


    if (
        fileBuffer.length >
        MAX_FILE_SIZE
    ) {

        throw new Error(
            `${attachment.name || "File"} exceeds the maximum evidence file size.`
        );
    }


    /*
     * If Discord didn't provide a MIME type, infer a safe
     * fallback from the filename.
     */

    const finalMimeType =
        mimeType ||
        inferMimeType(
            attachment.name
        );


    validateMimeType(
        finalMimeType
    );


    const originalFilename =
        sanitizeFilename(
            attachment.name ||
            "evidence"
        );


    const storagePath =
        createStoragePath(
            caseId,
            originalFilename
        );


    /*
     * --------------------------------------------------------
     * Upload to Supabase Storage
     * --------------------------------------------------------
     */

    await uploadToSupabaseStorage(
        storagePath,
        fileBuffer,
        finalMimeType
    );


    try {

        /*
         * ----------------------------------------------------
         * Insert evidence metadata
         * ----------------------------------------------------
         *
         * `content` stores the human-readable description.
         *
         * The storage metadata is kept in the dedicated
         * columns already used by CaseManager.
         */

        const result =
            await database.query(
                `
                INSERT INTO case_evidence (
                    case_id,
                    type,
                    content,
                    storage_path,
                    original_filename,
                    mime_type,
                    file_size,
                    created_by_uuid,
                    created_by_name
                )
                VALUES (
                    $1,
                    $2,
                    $3,
                    $4,
                    $5,
                    $6,
                    $7,
                    $8,
                    $9
                )
                RETURNING
                    id,
                    case_id,
                    type,
                    content,
                    storage_path,
                    original_filename,
                    mime_type,
                    file_size,
                    created_by_uuid,
                    created_by_name,
                    created_at
                `,
                [
                    caseId,
                    getEvidenceType(
                        finalMimeType
                    ),
                    normalizeDescription(
                        description
                    ),
                    storagePath,
                    originalFilename,
                    finalMimeType,
                    fileBuffer.length,
                    actorId,
                    actorName
                ]
            );


        if (
            result.rows.length === 0
        ) {

            throw new Error(
                "Evidence record was not created."
            );
        }


        return mapEvidence(
            result.rows[0]
        );

    } catch (error) {

        /*
         * Database insertion failed, so don't leave an
         * orphaned Storage object behind.
         */

        await deleteFromSupabaseStorage(
            storagePath
        );


        throw error;
    }
}


/*
 * ============================================================
 * Attachment validation
 * ============================================================
 */

function validateAttachment(
    attachment
) {

    if (
        !attachment ||
        typeof attachment !== "object"
    ) {

        throw new Error(
            "Invalid Discord attachment."
        );
    }


    const url =
        String(
            attachment.url ||
            attachment.proxyURL ||
            ""
        );


    if (!url) {

        throw new Error(
            "The Discord attachment does not contain a downloadable URL."
        );
    }


    let parsedUrl;

    try {

        parsedUrl =
            new URL(
                url
            );

    } catch {

        throw new Error(
            "The Discord attachment URL is invalid."
        );
    }


    if (
        parsedUrl.protocol !==
        ALLOWED_PROTOCOL
    ) {

        throw new Error(
            "Only HTTPS Discord attachments are supported."
        );
    }


    const size =
        Number(
            attachment.size || 0
        );


    if (
        Number.isFinite(size) &&
        size > MAX_FILE_SIZE
    ) {

        throw new Error(
            `${attachment.name || "File"} exceeds the maximum evidence file size.`
        );
    }
}


/*
 * ============================================================
 * Download Discord attachment
 * ============================================================
 */

async function downloadAttachment(
    url
) {

    const controller =
        new AbortController();


    const timeout =
        setTimeout(
            () =>
                controller.abort(),
            DOWNLOAD_TIMEOUT_MS
        );


    try {

        const response =
            await fetch(
                url,
                {
                    method: "GET",
                    redirect: "follow",
                    signal:
                        controller.signal
                }
            );


        if (
            !response.ok
        ) {

            throw new Error(
                `Discord attachment download failed with HTTP ${response.status}.`
            );
        }


        const contentLength =
            Number(
                response.headers.get(
                    "content-length"
                ) || 0
            );


        if (
            contentLength >
            MAX_FILE_SIZE
        ) {

            throw new Error(
                "The Discord attachment exceeds the maximum evidence file size."
            );
        }


        const arrayBuffer =
            await response.arrayBuffer();


        const buffer =
            Buffer.from(
                arrayBuffer
            );


        if (
            buffer.length >
            MAX_FILE_SIZE
        ) {

            throw new Error(
                "The downloaded evidence file exceeds the maximum evidence file size."
            );
        }


        return {
            buffer,
            mimeType:
                response.headers.get(
                    "content-type"
                ) || null
        };

    } catch (error) {

        if (
            error?.name ===
            "AbortError"
        ) {

            throw new Error(
                "The Discord evidence download timed out."
            );
        }


        throw error;

    } finally {

        clearTimeout(
            timeout
        );
    }
}


/*
 * ============================================================
 * Supabase Storage upload
 * ============================================================
 */

async function uploadToSupabaseStorage(
    storagePath,
    buffer,
    mimeType
) {

    const url =
        buildStorageObjectUrl(
            storagePath
        );


    const response =
        await fetch(
            url,
            {
                method: "POST",
                headers: {
                    Authorization:
                        `Bearer ${SUPABASE_SECRET_KEY}`,

                    apikey:
                        SUPABASE_SECRET_KEY,

                    "Content-Type":
                        mimeType,

                    "x-upsert":
                        "false"
                },
                body:
                    buffer
            }
        );


    if (
        !response.ok
    ) {

        const errorBody =
            await readResponseBody(
                response
            );


        throw new Error(
            `Supabase Storage upload failed (${response.status}): ${errorBody}`
        );
    }
}


/*
 * ============================================================
 * Supabase Storage delete
 * ============================================================
 */

async function deleteFromSupabaseStorage(
    storagePath
) {

    try {

        const url =
            buildStorageObjectUrl(
                storagePath
            );


        const response =
            await fetch(
                url,
                {
                    method: "DELETE",
                    headers: {
                        Authorization:
                            `Bearer ${SUPABASE_SECRET_KEY}`,

                        apikey:
                            SUPABASE_SECRET_KEY
                    }
                }
            );


        if (
            !response.ok &&
            response.status !== 404
        ) {

            console.error(
                `[CaseManager] Failed to delete Storage object ${storagePath}: HTTP ${response.status}`
            );
        }

    } catch (error) {

        console.error(
            `[CaseManager] Failed to clean up Storage object ${storagePath}:`,
            error
        );
    }
}


/*
 * ============================================================
 * Storage cleanup
 * ============================================================
 */

async function cleanupUploadedEvidence(
    evidence
) {

    if (
        !Array.isArray(evidence)
    ) {
        return;
    }


    for (
        const item of evidence
    ) {

        if (
            item?.storagePath
        ) {

            await deleteFromSupabaseStorage(
                item.storagePath
            );
        }
    }
}


/*
 * ============================================================
 * Storage URL
 * ============================================================
 */

function buildStorageObjectUrl(
    storagePath
) {

    return (
        `${SUPABASE_URL}` +
        `/storage/v1/object/` +
        `${encodeURIComponent(STORAGE_BUCKET)}/` +
        encodeStoragePath(
            storagePath
        )
    );
}


function encodeStoragePath(
    storagePath
) {

    return String(
        storagePath
    )
        .split("/")
        .map(
            encodeURIComponent
        )
        .join("/");
}


/*
 * ============================================================
 * Storage path generation
 * ============================================================
 */

function createStoragePath(
    caseId,
    filename
) {

    const extension =
        path.extname(
            filename
        );


    const safeExtension =
        extension
            ? extension
                .toLowerCase()
                .replace(
                    /[^a-z0-9.]/g,
                    ""
                )
            : "";


    const uniqueId =
        crypto
            .randomUUID()
            .replaceAll(
                "-",
                ""
            );


    return (
        `cases/${caseId}/` +
        `${Date.now()}-${uniqueId}` +
        `${safeExtension}`
    );
}


/*
 * ============================================================
 * Evidence type
 * ============================================================
 */

function getEvidenceType(
    mimeType
) {

    const mime =
        String(
            mimeType || ""
        ).toLowerCase();


    if (
        mime.startsWith(
            "image/"
        )
    ) {

        return "SCREENSHOT";
    }


    if (
        mime.startsWith(
            "video/"
        )
    ) {

        return "VIDEO";
    }


    if (
        mime ===
        "text/plain" ||
        mime ===
        "text/csv"
    ) {

        return "CHAT_LOG";
    }


    /*
     * PDF, ZIP and other uploaded documents are treated
     * as generic evidence.
     */

    return "OTHER";
}


/*
 * ============================================================
 * MIME helpers
 * ============================================================
 */

function normalizeMimeType(
    mimeType
) {

    if (
        !mimeType
    ) {
        return null;
    }


    return String(
        mimeType
    )
        .split(";")[0]
        .trim()
        .toLowerCase();
}


function inferMimeType(
    filename
) {

    const extension =
        path.extname(
            String(
                filename || ""
            )
        )
            .toLowerCase();


    const map = {
        ".png":
            "image/png",

        ".jpg":
            "image/jpeg",

        ".jpeg":
            "image/jpeg",

        ".webp":
            "image/webp",

        ".gif":
            "image/gif",

        ".mp4":
            "video/mp4",

        ".webm":
            "video/webm",

        ".mov":
            "video/quicktime",

        ".txt":
            "text/plain",

        ".csv":
            "text/csv",

        ".pdf":
            "application/pdf",

        ".zip":
            "application/zip"
    };


    return (
        map[extension] ||
        null
    );
}


function validateMimeType(
    mimeType
) {

    if (
        !mimeType
    ) {

        throw new Error(
            "Unable to determine the evidence file type."
        );
    }


    if (
        !ALLOWED_MIME_TYPES.has(
            mimeType
        )
    ) {

        throw new Error(
            `Evidence file type "${mimeType}" is not supported.`
        );
    }
}


/*
 * ============================================================
 * Filename helpers
 * ============================================================
 */

function sanitizeFilename(
    filename
) {

    const value =
        String(
            filename ||
            "evidence"
        )
        .trim()
        .replace(
            /[\/\\:*?"<>|]/g,
            "_"
        )
        .replace(
            /[\u0000-\u001F]/g,
            "_"
        );


    if (
        !value
    ) {

        return "evidence";
    }


    return value.substring(
        0,
        255
    );
}


/*
 * ============================================================
 * Description
 * ============================================================
 */

function normalizeDescription(
    description
) {

    return String(
        description || ""
    )
        .trim()
        .substring(
            0,
            2000
        );
}


/*
 * ============================================================
 * Case ID
 * ============================================================
 */

function normalizeCaseId(
    value
) {

    const caseId =
        Number.parseInt(
            String(value || ""),
            10
        );


    if (
        !Number.isSafeInteger(caseId) ||
        caseId <= 0
    ) {

        throw new Error(
            "Invalid case ID."
        );
    }


    return caseId;
}


/*
 * ============================================================
 * Configuration
 * ============================================================
 */

async function requireConfiguration() {

    const missing = [];


    if (
        !SUPABASE_URL
    ) {

        missing.push(
            "CASEMANAGER_SUPABASE_URL"
        );
    }


    if (
        !SUPABASE_SECRET_KEY
    ) {

        missing.push(
            "CASEMANAGER_SUPABASE_SECRET_KEY"
        );
    }


    if (
        !STORAGE_BUCKET
    ) {

        missing.push(
            "CASEMANAGER_EVIDENCE_BUCKET"
        );
    }


    if (
        missing.length > 0
    ) {

        throw new Error(
            `Discord evidence storage is not configured: ${missing.join(", ")}`
        );
    }
}


/*
 * ============================================================
 * Response helpers
 * ============================================================
 */

async function readResponseBody(
    response
) {

    try {

        const text =
            await response.text();


        return text
            ? text.substring(
                0,
                1000
            )
            : "No response body.";

    } catch {

        return "Unable to read response body.";
    }
}


/*
 * ============================================================
 * Database mapping
 * ============================================================
 */

function mapEvidence(
    row
) {

    return {
        id:
            row.id,

        caseId:
            row.case_id,

        type:
            row.type,

        content:
            row.content,

        storagePath:
            row.storage_path,

        originalFilename:
            row.original_filename,

        mimeType:
            row.mime_type,

        fileSize:
            Number(
                row.file_size || 0
            ),

        createdByUuid:
            row.created_by_uuid,

        createdByName:
            row.created_by_name,

        createdAt:
            row.created_at
    };
}


/*
 * ============================================================
 * Numeric helper
 * ============================================================
 */

function parsePositiveInteger(
    value,
    fallback
) {

    const parsed =
        Number.parseInt(
            String(value || ""),
            10
        );


    if (
        !Number.isFinite(parsed) ||
        parsed <= 0
    ) {

        return fallback;
    }


    return parsed;
}


module.exports = {
    uploadEvidence,
    uploadSingleAttachment,
    validateAttachment,
    createStoragePath,
    getEvidenceType,
    MAX_FILE_SIZE,
    MAX_FILES_PER_UPLOAD
};