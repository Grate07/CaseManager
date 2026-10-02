const database =
    require("../database/database");


/*
 * ============================================================
 * CaseManager Discord — Case Mutation Service
 * ============================================================
 *
 * Responsible for database mutations performed through Discord.
 *
 * Discord
 *   ↓
 * interactionCreate.js
 *   ↓
 * CaseMutationService
 *   ↓
 * Supabase PostgreSQL
 *
 * All mutations use PostgreSQL transactions whenever multiple
 * database operations must remain consistent.
 * ============================================================
 */


const VALID_STATUSES = Object.freeze([

    "OPEN",

    "INVESTIGATING",

    "WAITING_FOR_EVIDENCE",

    "ESCALATED",

    "RESOLVED",

    "CLOSED"

]);


/*
 * ============================================================
 * Status helpers
 * ============================================================
 */

function isValidStatus(
    status
) {

    return VALID_STATUSES.includes(
        String(status || "")
            .trim()
            .toUpperCase()
    );
}


function normalizeStatus(
    status
) {

    return String(
        status || ""
    )
        .trim()
        .toUpperCase();
}


function formatStatus(
    status
) {

    return String(
        status || "UNKNOWN"
    )
        .replaceAll(
            "_",
            " "
        );
}


/*
 * ============================================================
 * Case ID validation
 * ============================================================
 */

function validateCaseId(
    caseId
) {

    const parsed =
        Number(caseId);


    if (
        !Number.isInteger(parsed) ||
        parsed <= 0
    ) {

        throw new Error(
            "A valid case ID is required."
        );
    }


    return parsed;
}


/*
 * ============================================================
 * Investigator validation
 * ============================================================
 */

function validateInvestigator(
    investigatorUuid,
    investigatorName
) {

    if (
        !investigatorUuid ||
        typeof investigatorUuid !== "string"
    ) {

        throw new Error(
            "Investigator UUID is required."
        );
    }


    if (
        !investigatorName ||
        typeof investigatorName !== "string"
    ) {

        throw new Error(
            "Investigator name is required."
        );
    }


    if (
        investigatorName.trim() === ""
    ) {

        throw new Error(
            "Investigator name cannot be empty."
        );
    }
}


/*
 * ============================================================
 * Get case
 * ============================================================
 */

async function getCase(
    caseId
) {

    const normalizedId =
        validateCaseId(
            caseId
        );


    const result =
        await database.query(
            `
            SELECT
                id,
                target_uuid,
                target_name,
                creator_uuid,
                creator_name,
                reason,
                status,
                created_at,
                updated_at
            FROM cases
            WHERE id = $1
            LIMIT 1
            `,
            [
                normalizedId
            ]
        );


    if (
        result.rows.length === 0
    ) {

        return null;
    }


    const row =
        result.rows[0];


    return {

        id:
            Number(row.id),

        targetUuid:
            row.target_uuid,

        targetName:
            row.target_name,

        creatorUuid:
            row.creator_uuid,

        creatorName:
            row.creator_name,

        reason:
            row.reason,

        status:
            row.status,

        createdAt:
            row.created_at,

        updatedAt:
            row.updated_at
    };
}


/*
 * ============================================================
 * Require case
 * ============================================================
 */

async function requireCase(
    caseId
) {

    const caseData =
        await getCase(
            caseId
        );


    if (!caseData) {

        throw new Error(
            `Case #${caseId} was not found.`
        );
    }


    return caseData;
}
/*
 * ============================================================
 * Change case status
 * ============================================================
 */

async function changeStatus(
    caseId,
    status,
    actorUuid,
    actorName
) {

    const normalizedId =
        validateCaseId(
            caseId
        );


    const normalizedStatus =
        normalizeStatus(
            status
        );


    if (
        !isValidStatus(
            normalizedStatus
        )
    ) {

        throw new Error(
            `Invalid case status: ${normalizedStatus}`
        );
    }


    const client =
        await database.getClient();


    try {

        await client.query(
            "BEGIN"
        );


        const existing =
            await client.query(
                `
                SELECT
                    id,
                    status
                FROM cases
                WHERE id = $1
                FOR UPDATE
                `,
                [
                    normalizedId
                ]
            );


        if (
            existing.rows.length === 0
        ) {

            throw new Error(
                `Case #${normalizedId} was not found.`
            );
        }


        const oldStatus =
            existing.rows[0].status;


        /*
         * Nothing to change.
         */

        if (
            String(oldStatus || "")
                .toUpperCase() ===
            normalizedStatus
        ) {

            await client.query(
                "ROLLBACK"
            );


            return {

                changed: false,

                caseId:
                    normalizedId,

                oldStatus,

                newStatus:
                    normalizedStatus
            };
        }


        await client.query(
            `
            UPDATE cases
            SET
                status = $1,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = $2
            `,
            [
                normalizedStatus,
                normalizedId
            ]
        );


        await insertTimeline(
            client,
            {

                caseId:
                    normalizedId,

                actorUuid,

                actorName,

                action:
                    "STATUS_CHANGED",

                description:
                    `Case status changed from ${formatStatus(oldStatus)} to ${formatStatus(normalizedStatus)}.`
            }
        );


        await client.query(
            "COMMIT"
        );


        return {

            changed: true,

            caseId:
                normalizedId,

            oldStatus,

            newStatus:
                normalizedStatus
        };

    } catch (error) {

        try {

            await client.query(
                "ROLLBACK"
            );

        } catch {
            // Ignore rollback failure.
        }


        throw error;

    } finally {

        client.release();
    }
}


/*
 * ============================================================
 * Public status wrapper
 * ============================================================
 */

async function setCaseStatus(
    caseId,
    status,
    actorUuid,
    actorName
) {

    return changeStatus(
        caseId,
        status,
        actorUuid,
        actorName
    );
}
/*
 * ============================================================
 * Assign investigator
 * ============================================================
 */

async function assignInvestigator(
    caseId,
    investigatorUuid,
    investigatorName,
    actorUuid,
    actorName
) {

    const normalizedId =
        validateCaseId(
            caseId
        );


    validateInvestigator(
        investigatorUuid,
        investigatorName
    );


    /*
     * Make sure the case exists before opening the transaction.
     */

    await requireCase(
        normalizedId
    );


    const client =
        await database.getClient();


    try {

        await client.query(
            "BEGIN"
        );


        /*
         * Lock the case while modifying its investigators.
         */

        const caseResult =
            await client.query(
                `
                SELECT
                    id
                FROM cases
                WHERE id = $1
                FOR UPDATE
                `,
                [
                    normalizedId
                ]
            );


        if (
            caseResult.rows.length === 0
        ) {

            throw new Error(
                `Case #${normalizedId} was not found.`
            );
        }


        /*
         * Check for an existing assignment.
         */

        const existing =
            await client.query(
                `
                SELECT
                    id,
                    investigator_uuid,
                    investigator_name,
                    assigned_at
                FROM case_investigators
                WHERE case_id = $1
                  AND investigator_uuid = $2
                LIMIT 1
                `,
                [
                    normalizedId,
                    investigatorUuid
                ]
            );


        if (
            existing.rows.length > 0
        ) {

            await client.query(
                "ROLLBACK"
            );


            return {

                assigned: false,

                alreadyAssigned: true,

                caseId:
                    normalizedId,

                investigatorUuid,

                investigatorName:
                    existing.rows[0]
                        .investigator_name
            };
        }


        const inserted =
            await client.query(
                `
                INSERT INTO case_investigators (
                    case_id,
                    investigator_uuid,
                    investigator_name,
                    assigned_at
                )
                VALUES (
                    $1,
                    $2,
                    $3,
                    CURRENT_TIMESTAMP
                )
                RETURNING
                    id,
                    assigned_at
                `,
                [
                    normalizedId,

                    investigatorUuid,

                    investigatorName.trim()
                ]
            );


        await insertTimeline(
            client,
            {

                caseId:
                    normalizedId,

                actorUuid,

                actorName,

                action:
                    "INVESTIGATOR_ASSIGNED",

                description:
                    `Investigator ${investigatorName.trim()} was assigned to the case.`
            }
        );


        await client.query(
            "COMMIT"
        );


        return {

            assigned: true,

            alreadyAssigned: false,

            caseId:
                normalizedId,

            investigatorUuid,

            investigatorName:
                investigatorName.trim(),

            assignmentId:
                Number(
                    inserted.rows[0].id
                ),

            assignedAt:
                inserted.rows[0]
                    .assigned_at
        };

    } catch (error) {

        try {

            await client.query(
                "ROLLBACK"
            );

        } catch {
            // Ignore rollback failure.
        }


        throw error;

    } finally {

        client.release();
    }
}


/*
 * ============================================================
 * Public assignment wrapper
 * ============================================================
 */

async function addInvestigator(
    caseId,
    investigatorUuid,
    investigatorName,
    actorUuid,
    actorName
) {

    return assignInvestigator(
        caseId,
        investigatorUuid,
        investigatorName,
        actorUuid,
        actorName
    );
}
/*
 * ============================================================
 * Unassign investigator
 * ============================================================
 */

async function unassignInvestigator(
    caseId,
    investigatorUuid,
    actorUuid,
    actorName
) {

    const normalizedId =
        validateCaseId(
            caseId
        );


    if (
        !investigatorUuid ||
        typeof investigatorUuid !== "string"
    ) {

        throw new Error(
            "Investigator UUID is required."
        );
    }


    await requireCase(
        normalizedId
    );


    const client =
        await database.getClient();


    try {

        await client.query(
            "BEGIN"
        );


        const existing =
            await client.query(
                `
                SELECT
                    id,
                    investigator_uuid,
                    investigator_name
                FROM case_investigators
                WHERE case_id = $1
                  AND investigator_uuid = $2
                LIMIT 1
                FOR UPDATE
                `,
                [
                    normalizedId,
                    investigatorUuid
                ]
            );


        if (
            existing.rows.length === 0
        ) {

            await client.query(
                "ROLLBACK"
            );


            return {

                removed: false,

                wasAssigned: false,

                caseId:
                    normalizedId,

                investigatorUuid
            };
        }


        const investigatorName =
            existing.rows[0]
                .investigator_name;


        await client.query(
            `
            DELETE FROM case_investigators
            WHERE id = $1
            `,
            [
                existing.rows[0].id
            ]
        );


        await insertTimeline(
            client,
            {

                caseId:
                    normalizedId,

                actorUuid,

                actorName,

                action:
                    "INVESTIGATOR_UNASSIGNED",

                description:
                    `Investigator ${investigatorName} was removed from the case.`
            }
        );


        await client.query(
            "COMMIT"
        );


        return {

            removed: true,

            wasAssigned: true,

            caseId:
                normalizedId,

            investigatorUuid,

            investigatorName
        };

    } catch (error) {

        try {

            await client.query(
                "ROLLBACK"
            );

        } catch {
            // Ignore rollback failure.
        }


        throw error;

    } finally {

        client.release();
    }
}


/*
 * ============================================================
 * Public removal wrapper
 * ============================================================
 */

async function removeInvestigator(
    caseId,
    investigatorUuid,
    actorUuid,
    actorName
) {

    return unassignInvestigator(
        caseId,
        investigatorUuid,
        actorUuid,
        actorName
    );
}


/*
 * ============================================================
 * Is investigator assigned?
 * ============================================================
 */

async function isInvestigatorAssigned(
    caseId,
    investigatorUuid
) {

    const normalizedId =
        validateCaseId(
            caseId
        );


    if (
        !investigatorUuid
    ) {

        return false;
    }


    const result =
        await database.query(
            `
            SELECT
                1
            FROM case_investigators
            WHERE case_id = $1
              AND investigator_uuid = $2
            LIMIT 1
            `,
            [
                normalizedId,
                investigatorUuid
            ]
        );


    return (
        result.rows.length > 0
    );
}
/*
 * ============================================================
 * Get case status
 * ============================================================
 */

async function getCaseStatus(
    caseId
) {

    const normalizedId =
        validateCaseId(
            caseId
        );


    const result =
        await database.query(
            `
            SELECT
                id,
                status
            FROM cases
            WHERE id = $1
            LIMIT 1
            `,
            [
                normalizedId
            ]
        );


    if (
        result.rows.length === 0
    ) {

        return null;
    }


    return {

        caseId:
            Number(
                result.rows[0].id
            ),

        status:
            result.rows[0].status
    };
}


/*
 * ============================================================
 * Get investigators
 * ============================================================
 */

async function getInvestigators(
    caseId
) {

    const normalizedId =
        validateCaseId(
            caseId
        );


    const result =
        await database.query(
            `
            SELECT
                id,
                case_id,
                investigator_uuid,
                investigator_name,
                assigned_at
            FROM case_investigators
            WHERE case_id = $1
            ORDER BY
                assigned_at ASC,
                id ASC
            LIMIT 100
            `,
            [
                normalizedId
            ]
        );


    return result.rows.map(
        row => ({

            id:
                Number(row.id),

            caseId:
                Number(row.case_id),

            investigatorUuid:
                row.investigator_uuid,

            investigatorName:
                row.investigator_name,

            assignedAt:
                row.assigned_at
        })
    );
}


/*
 * ============================================================
 * Get timeline
 * ============================================================
 */

async function getTimeline(
    caseId
) {

    const normalizedId =
        validateCaseId(
            caseId
        );


    const result =
        await database.query(
            `
            SELECT
                id,
                case_id,
                actor_uuid,
                actor_name,
                action,
                description,
                created_at
            FROM case_timeline
            WHERE case_id = $1
            ORDER BY
                created_at ASC,
                id ASC
            LIMIT 100
            `,
            [
                normalizedId
            ]
        );


    return result.rows.map(
        row => ({

            id:
                Number(row.id),

            caseId:
                Number(row.case_id),

            actorUuid:
                row.actor_uuid,

            actorName:
                row.actor_name,

            action:
                row.action,

            description:
                row.description,

            createdAt:
                row.created_at
        })
    );
}


/*
 * ============================================================
 * Get evidence
 * ============================================================
 */

async function getEvidence(
    caseId
) {

    const normalizedId =
        validateCaseId(
            caseId
        );


    const result =
        await database.query(
            `
            SELECT
                id,
                case_id,
                added_by_uuid,
                added_by_name,
                type,
                content,
                storage_path,
                original_filename,
                mime_type,
                file_size,
                created_at
            FROM case_evidence
            WHERE case_id = $1
            ORDER BY
                created_at ASC,
                id ASC
            LIMIT 100
            `,
            [
                normalizedId
            ]
        );


    return result.rows.map(
        row => ({

            id:
                Number(row.id),

            caseId:
                Number(row.case_id),

            addedByUuid:
                row.added_by_uuid,

            addedByName:
                row.added_by_name,

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
                row.file_size,

            createdAt:
                row.created_at
        })
    );
}


/*
 * ============================================================
 * Insert timeline entry
 * ============================================================
 */

async function insertTimeline(
    client,
    {
        caseId,
        actorUuid,
        actorName,
        action,
        description
    }
) {

    await client.query(
        `
        INSERT INTO case_timeline (
            case_id,
            actor_uuid,
            actor_name,
            action,
            description,
            created_at
        )
        VALUES (
            $1,
            $2,
            $3,
            $4,
            $5,
            CURRENT_TIMESTAMP
        )
        `,
        [

            caseId,

            actorUuid || null,

            actorName || null,

            action,

            description
        ]
    );
}
/*
 * ============================================================
 * Export public API
 * ============================================================
 */

module.exports = {

    /*
     * Constants
     */

    VALID_STATUSES,


    /*
     * Helpers
     */

    isValidStatus,

    normalizeStatus,


    /*
     * Cases
     */

    getCase,

    requireCase,

    getCaseStatus,


    /*
     * Status
     */

    changeStatus,

    setCaseStatus,


    /*
     * Investigators
     */

    assignInvestigator,

    addInvestigator,

    unassignInvestigator,

    removeInvestigator,

    isInvestigatorAssigned,

    getInvestigators,


    /*
     * Read-only case data
     */

    getTimeline,

    getEvidence
};