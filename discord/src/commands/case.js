const {
    SlashCommandBuilder,
    MessageFlags
} = require("discord.js");

const config = require("../config");
const database = require("../database/database");
const permissionService = require("../permissions/permissionService");

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
 * CaseManager Discord — /case
 * ============================================================
 *
 * Components V2 only.
 *
 * Supported:
 *
 * /case view <id>
 * /case evidence <id>
 * /case timeline <id>
 * /case investigators <id>
 * /case status <id>
 *
 * The Minecraft plugin remains responsible for creating and
 * modifying cases. This Discord layer reads the same database.
 * ============================================================
 */


const command = new SlashCommandBuilder()
    .setName("case")
    .setDescription("View and manage CaseManager moderation cases.")

    .addSubcommand(subcommand =>
        subcommand
            .setName("view")
            .setDescription("View a moderation case.")
            .addIntegerOption(option =>
                option
                    .setName("id")
                    .setDescription("Case ID.")
                    .setRequired(true)
                    .setMinValue(1)
            )
    )

    .addSubcommand(subcommand =>
        subcommand
            .setName("evidence")
            .setDescription("View evidence attached to a case.")
            .addIntegerOption(option =>
                option
                    .setName("id")
                    .setDescription("Case ID.")
                    .setRequired(true)
                    .setMinValue(1)
            )
    )

    .addSubcommand(subcommand =>
        subcommand
            .setName("timeline")
            .setDescription("View the timeline of a case.")
            .addIntegerOption(option =>
                option
                    .setName("id")
                    .setDescription("Case ID.")
                    .setRequired(true)
                    .setMinValue(1)
            )
    )

    .addSubcommand(subcommand =>
        subcommand
            .setName("investigators")
            .setDescription("View investigators assigned to a case.")
            .addIntegerOption(option =>
                option
                    .setName("id")
                    .setDescription("Case ID.")
                    .setRequired(true)
                    .setMinValue(1)
            )
    )

    .addSubcommand(subcommand =>
        subcommand
            .setName("status")
            .setDescription("View the current status of a case.")
            .addIntegerOption(option =>
                option
                    .setName("id")
                    .setDescription("Case ID.")
                    .setRequired(true)
                    .setMinValue(1)
            )
    );


/*
 * ============================================================
 * Main command handler
 * ============================================================
 */

async function execute(interaction) {

    const subcommand =
        interaction.options.getSubcommand();

    /*
     * Server-side permission check.
     *
     * This is deliberately performed before database access.
     */
    if (!interaction.inGuild()) {

        await interaction.reply({
            components: [
                errorPanel(
                    "CaseManager commands can only be used inside a Discord server."
                )
            ],
            flags: MessageFlags.IsComponentsV2 |
                MessageFlags.Ephemeral
        });

        return;
    }


    if (
    !(await permissionService.hasPermission(
        interaction.member,
        "case"
    ))
) {

        await interaction.reply({
            components: [
                errorPanel(
                    "You do not have permission to use CaseManager commands."
                )
            ],
            flags: MessageFlags.IsComponentsV2 |
                MessageFlags.Ephemeral
        });

        return;
    }


    try {

        switch (subcommand) {

            case "view":
                await handleView(interaction);
                break;

            case "evidence":
                await handleEvidence(interaction);
                break;

            case "timeline":
                await handleTimeline(interaction);
                break;

            case "investigators":
                await handleInvestigators(interaction);
                break;

            case "status":
                await handleStatus(interaction);
                break;

            default:

                await interaction.reply({
                    components: [
                        errorPanel(
                            "Unknown CaseManager subcommand."
                        )
                    ],
                    flags:
                        MessageFlags.IsComponentsV2 |
                        MessageFlags.Ephemeral
                });
        }

    } catch (error) {

        console.error(
            "[CaseManager] /case error:",
            error
        );

        const message =
            config.bot.debug
                ? `Database error: ${error.message}`
                : "An unexpected error occurred while processing the case.";

        if (interaction.replied ||
            interaction.deferred) {

            await interaction.editReply({
                components: [
                    errorPanel(message)
                ],
                flags:
                    MessageFlags.IsComponentsV2
            });

        } else {

            await interaction.reply({
                components: [
                    errorPanel(message)
                ],
                flags:
                    MessageFlags.IsComponentsV2 |
                    MessageFlags.Ephemeral
            });
        }
    }
}


/*
 * ============================================================
 * /case view
 * ============================================================
 */

async function handleView(interaction) {

    const caseId =
        interaction.options.getInteger("id", true);

    await interaction.deferReply({
        flags:
            MessageFlags.IsComponentsV2 |
            MessageFlags.Ephemeral
    });

    const caseData =
        await getCase(caseId);

    if (!caseData) {

        await interaction.editReply({
            components: [
                errorPanel(
                    `Case #${caseId} was not found.`
                )
            ]
        });

        return;
    }

    await interaction.editReply({
        components: [
            casePanel(caseData)
        ]
    });
}


/*
 * ============================================================
 * /case evidence
 * ============================================================
 */

async function handleEvidence(interaction) {

    const caseId =
        interaction.options.getInteger("id", true);

    await interaction.deferReply({
        flags:
            MessageFlags.IsComponentsV2 |
            MessageFlags.Ephemeral
    });

    const caseData =
        await getCase(caseId);

    if (!caseData) {

        await interaction.editReply({
            components: [
                errorPanel(
                    `Case #${caseId} was not found.`
                )
            ]
        });

        return;
    }

    const evidence =
        await getCaseEvidence(caseId);

    await interaction.editReply({
        components: [
            caseEvidencePanel(
                caseData,
                evidence
            )
        ]
    });
}


/*
 * ============================================================
 * /case timeline
 * ============================================================
 */

async function handleTimeline(interaction) {

    const caseId =
        interaction.options.getInteger("id", true);

    await interaction.deferReply({
        flags:
            MessageFlags.IsComponentsV2 |
            MessageFlags.Ephemeral
    });

    const caseData =
        await getCase(caseId);

    if (!caseData) {

        await interaction.editReply({
            components: [
                errorPanel(
                    `Case #${caseId} was not found.`
                )
            ]
        });

        return;
    }

    const timeline =
        await getCaseTimeline(caseId);

    await interaction.editReply({
        components: [
            caseTimelinePanel(
                caseData,
                timeline
            )
        ]
    });
}


/*
 * ============================================================
 * /case investigators
 * ============================================================
 */

async function handleInvestigators(interaction) {

    const caseId =
        interaction.options.getInteger("id", true);

    await interaction.deferReply({
        flags:
            MessageFlags.IsComponentsV2 |
            MessageFlags.Ephemeral
    });

    const caseData =
        await getCase(caseId);

    if (!caseData) {

        await interaction.editReply({
            components: [
                errorPanel(
                    `Case #${caseId} was not found.`
                )
            ]
        });

        return;
    }

    const investigators =
        await getCaseInvestigators(caseId);

    await interaction.editReply({
        components: [
            caseStaffPanel(
                caseData,
                investigators
            )
        ]
    });
}


/*
 * ============================================================
 * /case status
 * ============================================================
 */

async function handleStatus(interaction) {

    const caseId =
        interaction.options.getInteger("id", true);

    await interaction.deferReply({
        flags:
            MessageFlags.IsComponentsV2 |
            MessageFlags.Ephemeral
    });

    const caseData =
        await getCase(caseId);

    if (!caseData) {

        await interaction.editReply({
            components: [
                errorPanel(
                    `Case #${caseId} was not found.`
                )
            ]
        });

        return;
    }

    await interaction.editReply({
        components: [
            caseStatusPanel(caseData)
        ]
    });
}


/*
 * ============================================================
 * Database helpers
 * ============================================================
 *
 * These use the shared Supabase PostgreSQL database.
 *
 * The Discord bot does NOT create a second database.
 * ============================================================
 */


/**
 * Get one case.
 */
async function getCase(caseId) {

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

    return mapCase(
        result.rows[0]
    );
}


/**
 * Get evidence.
 */
async function getCaseEvidence(caseId) {

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
        mapEvidence
    );
}


/**
 * Get timeline.
 */
async function getCaseTimeline(caseId) {

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
        mapTimeline
    );
}


/**
 * Get investigators.
 */
async function getCaseInvestigators(caseId) {

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
            id: row.id,
            caseId: row.case_id,
            playerUuid: row.investigator_uuid,
            playerName: row.investigator_name,
            assignedAt: row.assigned_at
        })
    );
}


/*
 * ============================================================
 * Database row mapping
 * ============================================================
 */

function mapCase(row) {

    return {

        id: Number(row.id),

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


function mapEvidence(row) {

    return {

        id: Number(row.id),

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
    };
}


function mapTimeline(row) {

    return {

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
    };
}


module.exports = {
    data: command,
    execute
};
