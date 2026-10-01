const {
    MessageFlags
} = require("discord.js");

const permissionService =
    require("../permissions/permissionService");

const database =
    require("../database/database");

const {
    casePanel,
    caseEvidencePanel,
    caseTimelinePanel,
    caseStaffPanel,
    caseStatusPanel,
    errorPanel,
    infoPanel
} = require("../ui/components");


/*
 * ============================================================
 * CaseManager — Interaction Handler
 * ============================================================
 *
 * Handles:
 *
 * - Slash commands
 * - Case buttons
 * - Components V2 navigation
 *
 * Permission checks are performed server-side.
 * ============================================================
 */


module.exports = async function interactionCreate(
    interaction
) {

    try {

        /*
         * ====================================================
         * Slash Commands
         * ====================================================
         */

        if (interaction.isChatInputCommand()) {

            await handleCommand(
                interaction
            );

            return;
        }


        /*
         * ====================================================
         * Buttons
         * ====================================================
         */

        if (interaction.isButton()) {

            await handleButton(
                interaction
            );

            return;
        }

    } catch (error) {

        console.error(
            "[CaseManager] Interaction error:",
            error
        );

        await safelyRespond(
            interaction,
            errorPanel(
                "An unexpected error occurred while processing this interaction."
            )
        );
    }
};


/*
 * ============================================================
 * Slash command handler
 * ============================================================
 */

async function handleCommand(
    interaction
) {

    if (!interaction.inGuild()) {

        await interaction.reply({
            components: [
                errorPanel(
                    "CaseManager commands can only be used inside a Discord server."
                )
            ],
            flags:
                MessageFlags.IsComponentsV2 |
                MessageFlags.Ephemeral
        });

        return;
    }


    const command =
        interaction.client.commands.get(
            interaction.commandName
        );


    if (!command) {

        await interaction.reply({
            components: [
                errorPanel(
                    "This command is not available."
                )
            ],
            flags:
                MessageFlags.IsComponentsV2 |
                MessageFlags.Ephemeral
        });

        return;
    }


    /*
     * The command itself performs its own permission check.
     *
     * This keeps command-specific permission logic close
     * to the command implementation.
     */

    await command.execute(
        interaction
    );
}


/*
 * ============================================================
 * Button handler
 * ============================================================
 */

async function handleButton(
    interaction
) {

    if (!interaction.inGuild()) {

        await safelyRespond(
            interaction,
            errorPanel(
                "CaseManager buttons can only be used inside a Discord server."
            )
        );

        return;
    }


    /*
     * All CaseManager buttons begin with one of:
     *
     * case:
     * settings:
     *
     * ========================================================
     */


    if (
        interaction.customId.startsWith(
            "case:"
        )
    ) {

        /*
         * Buttons are part of the /case system,
         * therefore they use the same permission.
         */

        if (
            !permissionService.hasPermission(
                interaction.member,
                "case"
            )
        ) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "You do not have permission to use CaseManager."
                )
            );

            return;
        }


        await handleCaseButton(
            interaction
        );

        return;
    }


    /*
     * Settings buttons will be implemented when
     * the settings/permission UI is wired.
     */

    if (
        interaction.customId.startsWith(
            "settings:"
        )
    ) {

        if (
            !permissionService.hasPermission(
                interaction.member,
                "settings"
            )
        ) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "You do not have permission to access CaseManager settings."
                )
            );

            return;
        }


        await safelyRespond(
            interaction,
            infoPanel(
                "Settings",
                "The CaseManager settings panel will be connected here."
            )
        );

        return;
    }
}


/*
 * ============================================================
 * Case buttons
 * ============================================================
 */

async function handleCaseButton(
    interaction
) {

    const parts =
        interaction.customId.split(":");


    /*
     * Example:
     *
     * case:view:15
     *
     * parts:
     *
     * [0] case
     * [1] view
     * [2] 15
     */


    const action =
        parts[1];


    const caseId =
        Number.parseInt(
            parts[2],
            10
        );


    if (
        !Number.isInteger(caseId) ||
        caseId <= 0
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "Invalid case ID."
            )
        );

        return;
    }


    /*
     * ========================================================
     * View
     * ========================================================
     */

    if (action === "view") {

        await showCase(
            interaction,
            caseId
        );

        return;
    }


    /*
     * ========================================================
     * Evidence
     * ========================================================
     */

    if (action === "evidence") {

        await showEvidence(
            interaction,
            caseId
        );

        return;
    }


    /*
     * ========================================================
     * Timeline
     * ========================================================
     */

    if (action === "timeline") {

        await showTimeline(
            interaction,
            caseId
        );

        return;
    }


    /*
     * ========================================================
     * Investigators
     * ========================================================
     */

    if (action === "investigators") {

        await showInvestigators(
            interaction,
            caseId
        );

        return;
    }


    /*
     * ========================================================
     * Assign / Unassign
     * ========================================================
     *
     * Actual player selection will be added later.
     */

    if (
        action === "assign" ||
        action === "unassign"
    ) {

        await safelyRespond(
            interaction,
            infoPanel(
                action === "assign"
                    ? "Assign Investigator"
                    : "Unassign Investigator",
                `Investigator selection for Case #${caseId} will be connected here.`
            )
        );

        return;
    }


    /*
     * ========================================================
     * Add Evidence
     * ========================================================
     */

    if (action === "evidence" &&
        parts[2] === "add") {

        await safelyRespond(
            interaction,
            infoPanel(
                "Add Evidence",
                `The evidence upload form for Case #${caseId} will be connected here.`
            )
        );

        return;
    }


    /*
     * ========================================================
     * Status
     * ========================================================
     *
     * Format:
     *
     * case:status:OPEN:15
     *
     * Therefore status buttons require special parsing.
     */

    if (action === "status") {

        await handleStatusButton(
            interaction,
            parts
        );

        return;
    }


    await safelyRespond(
        interaction,
        errorPanel(
            "Unknown CaseManager button."
        )
    );
}


/*
 * ============================================================
 * Status buttons
 * ============================================================
 */

async function handleStatusButton(
    interaction,
    parts
) {

    /*
     * case:status:OPEN:15
     */

    const status =
        parts[2];


    const caseId =
        Number.parseInt(
            parts[3],
            10
        );


    if (
        !Number.isInteger(caseId) ||
        caseId <= 0
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "Invalid case ID."
            )
        );

        return;
    }


    const allowedStatuses = [
        "OPEN",
        "INVESTIGATING",
        "WAITING_FOR_EVIDENCE",
        "ESCALATED",
        "RESOLVED",
        "CLOSED"
    ];


    if (
        !allowedStatuses.includes(
            status
        )
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "Invalid case status."
            )
        );

        return;
    }


    /*
     * We deliberately do not silently change the database yet.
     *
     * The Minecraft CaseManager service already owns the
     * case-management logic.
     *
     * The Discord status action will call the same database
     * contract once the shared mutation/service layer is wired.
     */

    await safelyRespond(
        interaction,
        infoPanel(
            "Status Change",
            `Case #${caseId} selected for status **${status.replaceAll("_", " ")}**.\n\n` +
            "The shared CaseManager mutation layer will process this action."
        )
    );
}


/*
 * ============================================================
 * Show Case
 * ============================================================
 */

async function showCase(
    interaction,
    caseId
) {

    const caseData =
        await getCase(caseId);


    if (!caseData) {

        await safelyRespond(
            interaction,
            errorPanel(
                `Case #${caseId} was not found.`
            )
        );

        return;
    }


    await safelyRespond(
        interaction,
        casePanel(
            caseData
        )
    );
}


/*
 * ============================================================
 * Show Evidence
 * ============================================================
 */

async function showEvidence(
    interaction,
    caseId
) {

    const caseData =
        await getCase(caseId);


    if (!caseData) {

        await safelyRespond(
            interaction,
            errorPanel(
                `Case #${caseId} was not found.`
            )
        );

        return;
    }


    const evidence =
        await getEvidence(caseId);


    await safelyRespond(
        interaction,
        caseEvidencePanel(
            caseData,
            evidence
        )
    );
}


/*
 * ============================================================
 * Show Timeline
 * ============================================================
 */

async function showTimeline(
    interaction,
    caseId
) {

    const caseData =
        await getCase(caseId);


    if (!caseData) {

        await safelyRespond(
            interaction,
            errorPanel(
                `Case #${caseId} was not found.`
            )
        );

        return;
    }


    const timeline =
        await getTimeline(caseId);


    await safelyRespond(
        interaction,
        caseTimelinePanel(
            caseData,
            timeline
        )
    );
}


/*
 * ============================================================
 * Show Investigators
 * ============================================================
 */

async function showInvestigators(
    interaction,
    caseId
) {

    const caseData =
        await getCase(caseId);


    if (!caseData) {

        await safelyRespond(
            interaction,
            errorPanel(
                `Case #${caseId} was not found.`
            )
        );

        return;
    }


    const investigators =
        await getInvestigators(caseId);


    await safelyRespond(
        interaction,
        caseStaffPanel(
            caseData,
            investigators
        )
    );
}


/*
 * ============================================================
 * Database — Case
 * ============================================================
 */

async function getCase(
    caseId
) {

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
            [caseId]
        );


    if (!result.rows.length) {

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
 * Database — Evidence
 * ============================================================
 */

async function getEvidence(
    caseId
) {

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
            ORDER BY created_at ASC, id ASC
            LIMIT 100
            `,
            [caseId]
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
 * Database — Timeline
 * ============================================================
 */

async function getTimeline(
    caseId
) {

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
            ORDER BY created_at ASC, id ASC
            LIMIT 100
            `,
            [caseId]
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
 * Database — Investigators
 * ============================================================
 */

async function getInvestigators(
    caseId
) {

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
            ORDER BY assigned_at ASC, id ASC
            LIMIT 100
            `,
            [caseId]
        );


    return result.rows.map(
        row => ({

            id:
                Number(row.id),

            caseId:
                Number(row.case_id),

            playerUuid:
                row.investigator_uuid,

            playerName:
                row.investigator_name,

            assignedAt:
                row.assigned_at
        })
    );
}


/*
 * ============================================================
 * Safe interaction response
 * ============================================================
 */

async function safelyRespond(
    interaction,
    panel
) {

    const payload = {

        components: [
            panel
        ],

        flags:
            MessageFlags.IsComponentsV2 |
            MessageFlags.Ephemeral
    };


    if (
        interaction.replied ||
        interaction.deferred
    ) {

        await interaction.editReply({
            components: payload.components
        });

    } else {

        await interaction.reply(
            payload
        );
    }
}