const {
    MessageFlags,
    ActionRowBuilder,
    RoleSelectMenuBuilder
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

const {
    permissionSettingsPanel,
    permissionModePanel,
    permissionRolesPanel,
    roleSelectionPanel,
    savePermissionSettings
} = require("../ui/settings");


/*
 * ============================================================
 * CaseManager — Interaction Handler
 * ============================================================
 *
 * Handles:
 *
 * - Slash commands
 * - Case buttons
 * - Permission settings
 * - Components V2 navigation
 * - Discord role selection
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

        if (
            interaction.isChatInputCommand()
        ) {

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

        if (
            interaction.isButton()
        ) {

            await handleButton(
                interaction
            );

            return;
        }


        /*
         * ====================================================
         * Role Select Menus
         * ====================================================
         */

        if (
            interaction.isRoleSelectMenu()
        ) {

            await handleRoleSelect(
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

    if (
        !interaction.inGuild()
    ) {

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

    if (
        !interaction.inGuild()
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "CaseManager buttons can only be used inside a Discord server."
            )
        );

        return;
    }


    /*
     * ========================================================
     * Case buttons
     * ========================================================
     */

    if (
        interaction.customId.startsWith(
            "case:"
        )
    ) {

        const allowed =
            await permissionService.hasPermission(
                interaction.member,
                "case"
            );


        if (!allowed) {

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
     * ========================================================
     * Settings buttons
     * ========================================================
     */

    if (
        interaction.customId.startsWith(
            "settings:"
        )
    ) {

        const allowed =
            await permissionService.canManagePermissions(
                interaction.member,
            );


        if (!allowed) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "You do not have permission to access CaseManager settings."
                )
            );

            return;
        }


        await handleSettingsButton(
            interaction
        );

        return;
    }
}


/*
 * ============================================================
 * Settings button router
 * ============================================================
 */

async function handleSettingsButton(
    interaction
) {

    const parts =
        interaction.customId.split(":");


    /*
     * settings:permissions
     */

    if (
        interaction.customId ===
        "settings:permissions"
    ) {

        const panel =
            await permissionSettingsPanel(
                interaction.guild.id
            );


        await safelyRespond(
            interaction,
            panel
        );

        return;
    }


    /*
     * settings:permissions:refresh
     */

    if (
        interaction.customId ===
        "settings:permissions:refresh"
    ) {

        const panel =
            await permissionSettingsPanel(
                interaction.guild.id
            );


        await safelyRespond(
            interaction,
            panel
        );

        return;
    }


    /*
     * settings:permissions:mode
     */

    if (
        interaction.customId ===
        "settings:permissions:mode"
    ) {

        const settings =
            await permissionService.getSettings(
                interaction.guild.id
            );


        await safelyRespond(
            interaction,
            permissionModePanel(
                settings
            )
        );

        return;
    }


    /*
     * settings:permissions:mode:global
     */

    if (
        interaction.customId ===
        "settings:permissions:mode:global"
    ) {

        await changePermissionMode(
            interaction,
            "global"
        );

        return;
    }


    /*
     * settings:permissions:mode:per-command
     */

    if (
        interaction.customId ===
        "settings:permissions:mode:per-command"
    ) {

        await changePermissionMode(
            interaction,
            "per-command"
        );

        return;
    }


    /*
     * settings:permissions:roles
     */

    if (
        interaction.customId ===
        "settings:permissions:roles"
    ) {

        const settings =
            await permissionService.getSettings(
                interaction.guild.id
            );


        await safelyRespond(
            interaction,
            permissionRolesPanel(
                settings
            )
        );

        return;
    }


    /*
     * settings:permissions:role:<type>
     */

    if (
        parts[0] === "settings" &&
        parts[1] === "permissions" &&
        parts[2] === "role"
    ) {

        const roleType =
            parts[3];


        if (
            !isValidRoleType(
                roleType
            )
        ) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "Invalid permission role type."
                )
            );

            return;
        }


        const settings =
            await permissionService.getSettings(
                interaction.guild.id
            );


        await safelyRespond(
            interaction,
            roleSelectionPanel(
                roleType,
                settings
            )
        );

        return;
    }
    /*
     * ========================================================
     * Set role
     * ========================================================
     *
     * Opens the Discord role selector.
     */

    if (
        parts[0] === "settings" &&
        parts[1] === "permissions" &&
        parts[2] === "setrole"
    ) {

        const roleType =
            parts[3];


        if (
            !isValidRoleType(
                roleType
            )
        ) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "Invalid permission role type."
                )
            );

            return;
        }


        const selector =
            new RoleSelectMenuBuilder()
                .setCustomId(
                    `settings:permissions:selectrole:${roleType}`
                )
                .setPlaceholder(
                    "Select a Discord role"
                )
                .setMinValues(1)
                .setMaxValues(1);


        const row =
            new ActionRowBuilder()
                .addComponents(
                    selector
                );


        const currentSettings =
            await permissionService.getSettings(
                interaction.guild.id
            );


        const currentRole =
            getRoleId(
                roleType,
                currentSettings
            );


        await safelyRespond(
            interaction,
            createRoleSelectorPanel(
                roleType,
                currentRole,
                row
            )
        );

        return;
    }


    /*
     * ========================================================
     * Clear role
     * ========================================================
     */

    if (
        parts[0] === "settings" &&
        parts[1] === "permissions" &&
        parts[2] === "clearrole"
    ) {

        const roleType =
            parts[3];


        if (
            !isValidRoleType(
                roleType
            )
        ) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "Invalid permission role type."
                )
            );

            return;
        }


        await clearPermissionRole(
            interaction,
            roleType
        );

        return;
    }


    /*
     * ========================================================
     * Settings back
     * ========================================================
     */

    if (
        interaction.customId ===
        "settings:back"
    ) {

        const settings =
            await permissionService.getSettings(
                interaction.guild.id
            );


        await safelyRespond(
            interaction,
            permissionSettingsPanel(
                interaction.guild.id
            )
        );

        return;
    }


    await safelyRespond(
        interaction,
        errorPanel(
            "Unknown CaseManager settings action."
        )
    );
}


/*
 * ============================================================
 * Change permission mode
 * ============================================================
 */

async function changePermissionMode(
    interaction,
    mode
) {

    const settings =
        await permissionService.getSettings(
            interaction.guild.id
        );


    settings.mode =
        mode;


    await savePermissionSettings(
        interaction.guild.id,
        settings,
        interaction.user.id
    );


    const updatedSettings =
        await permissionService.getSettings(
            interaction.guild.id
        );


    await safelyRespond(
        interaction,
        permissionSettingsPanel(
            interaction.guild.id
        )
    );


    console.log(
        `[CaseManager] ${interaction.user.tag} changed permission mode for guild ${interaction.guild.id} to ${updatedSettings.mode}.`
    );
}


/*
 * ============================================================
 * Valid role types
 * ============================================================
 */

function isValidRoleType(
    roleType
) {

    return [
        "global",
        "case",
        "evidence",
        "timeline",
        "investigators",
        "status",
        "settings",
        "permissions"
    ].includes(
        String(roleType || "")
            .toLowerCase()
    );
}


/*
 * ============================================================
 * Get configured role
 * ============================================================
 */

function getRoleId(
    roleType,
    settings
) {

    if (
        roleType === "global"
    ) {

        return (
            settings.globalRoleId ||
            ""
        );
    }


    return (
        settings.perCommand?.[roleType] ||
        ""
    );
}
/*
 * ============================================================
 * Create role selector panel
 * ============================================================
 */

function createRoleSelectorPanel(
    roleType,
    currentRole,
    selectorRow
) {

    const roleNames = {

        global:
            "Global CaseManager Role",

        case:
            "Case Command Role",

        evidence:
            "Evidence Command Role",

        timeline:
            "Timeline Command Role",

        investigators:
            "Investigators Command Role",

        status:
            "Status Command Role",

        settings:
            "Settings Command Role",

        permissions:
            "Permissions Command Role"
    };


    const name =
        roleNames[roleType] ||
        "CaseManager Role";


    const current =
        currentRole
            ? `<@&${currentRole}>`
            : "Not configured";


    /*
     * We use a small Components V2 container here rather
     * than an EmbedBuilder.
     *
     * The selector itself is placed inside an ActionRow.
     */

    const {
        ContainerBuilder,
        TextDisplayBuilder,
        SeparatorBuilder,
        SeparatorSpacingSize
    } = require("discord.js");


    const container =
        new ContainerBuilder();


    container.addTextDisplayComponents(
        new TextDisplayBuilder()
            .setContent(
                `# ${name}\n` +
                `Current role: ${current}\n\n` +
                "Select the Discord role you want to use."
            )
    );


    container.addSeparatorComponents(
        new SeparatorBuilder()
            .setSpacing(
                SeparatorSpacingSize.Small
            )
    );


    container.addActionRowComponents(
        selectorRow
    );


    return container;
}


/*
 * ============================================================
 * Handle role selector
 * ============================================================
 */

async function handleRoleSelect(
    interaction
) {

    if (
        !interaction.inGuild()
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "This control can only be used inside a Discord server."
            )
        );

        return;
    }


    /*
     * Permission check again.
     *
     * Never rely on the fact that the user reached the selector
     * from the settings panel.
     */

    const allowed =
        await permissionService.hasPermission(
            interaction.member,
            "settings"
        );


    if (!allowed) {

        await safelyRespond(
            interaction,
            errorPanel(
                "You do not have permission to modify CaseManager settings."
            )
        );

        return;
    }


    const parts =
        interaction.customId.split(":");


    /*
     * Expected:
     *
     * settings:permissions:selectrole:case
     */

    if (
        parts[0] !== "settings" ||
        parts[1] !== "permissions" ||
        parts[2] !== "selectrole"
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "Unknown role selector."
            )
        );

        return;
    }


    const roleType =
        parts[3];


    if (
        !isValidRoleType(
            roleType
        )
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "Invalid permission role type."
            )
        );

        return;
    }


    const roleId =
        interaction.values[0];


    if (
        !roleId
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "No Discord role was selected."
            )
        );

        return;
    }


    /*
     * Prevent the bot from assigning itself or managed roles
     * as CaseManager permission roles.
     */

    const role =
        interaction.guild.roles.cache.get(
            roleId
        );


    if (!role) {

        await safelyRespond(
            interaction,
            errorPanel(
                "The selected Discord role could not be found."
            )
        );

        return;
    }


    if (
        role.managed
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "Managed/integration roles cannot be used as CaseManager permission roles."
            )
        );

        return;
    }


    const settings =
        await permissionService.getSettings(
            interaction.guild.id
        );


    if (
        roleType === "global"
    ) {

        settings.globalRoleId =
            role.id;

    } else {

        if (
            !settings.perCommand
        ) {

            settings.perCommand = {};
        }


        settings.perCommand[
            roleType
        ] = role.id;
    }


    await savePermissionSettings(
        interaction.guild.id,
        settings,
        interaction.user.id
    );


    console.log(
        `[CaseManager] ${interaction.user.tag} configured ${roleType} permission role as ${role.id} in guild ${interaction.guild.id}.`
    );


    await safelyRespond(
        interaction,
        permissionSettingsPanel(
            interaction.guild.id
        )
    );
}
/*
 * ============================================================
 * Clear permission role
 * ============================================================
 */

async function clearPermissionRole(
    interaction,
    roleType
) {

    const settings =
        await permissionService.getSettings(
            interaction.guild.id
        );


    if (
        roleType === "global"
    ) {

        settings.globalRoleId =
            "";

    } else {

        if (
            !settings.perCommand
        ) {

            settings.perCommand = {};
        }


        settings.perCommand[
            roleType
        ] = "";
    }


    await savePermissionSettings(
        interaction.guild.id,
        settings,
        interaction.user.id
    );


    console.log(
        `[CaseManager] ${interaction.user.tag} cleared ${roleType} permission role in guild ${interaction.guild.id}.`
    );


    await safelyRespond(
        interaction,
        permissionSettingsPanel(
            interaction.guild.id
        )
    );
}


/*
 * ============================================================
 * Case button router
 * ============================================================
 */

async function handleCaseButton(
    interaction
) {

    const parts =
        interaction.customId.split(":");


    const action =
        parts[1];


    /*
     * --------------------------------------------------------
     * View
     * --------------------------------------------------------
     */

    if (
        action === "view"
    ) {

        const caseId =
            parseCaseId(
                parts[2]
            );


        if (!caseId) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "Invalid case ID."
                )
            );

            return;
        }


        await showCase(
            interaction,
            caseId
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * Evidence
     * --------------------------------------------------------
     */

    if (
        action === "evidence"
    ) {

        /*
         * case:evidence:add:15
         */

        if (
            parts[2] === "add"
        ) {

            const caseId =
                parseCaseId(
                    parts[3]
                );


            if (!caseId) {

                await safelyRespond(
                    interaction,
                    errorPanel(
                        "Invalid case ID."
                    )
                );

                return;
            }


            await handleAddEvidence(
                interaction,
                caseId
            );

            return;
        }


        /*
         * case:evidence:15
         */

        const caseId =
            parseCaseId(
                parts[2]
            );


        if (!caseId) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "Invalid case ID."
                )
            );

            return;
        }


        await showEvidence(
            interaction,
            caseId
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * Timeline
     * --------------------------------------------------------
     */

    if (
        action === "timeline"
    ) {

        const caseId =
            parseCaseId(
                parts[2]
            );


        if (!caseId) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "Invalid case ID."
                )
            );

            return;
        }


        await showTimeline(
            interaction,
            caseId
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * Investigators
     * --------------------------------------------------------
     */

    if (
        action === "investigators"
    ) {

        const caseId =
            parseCaseId(
                parts[2]
            );


        if (!caseId) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "Invalid case ID."
                )
            );

            return;
        }


        await showInvestigators(
            interaction,
            caseId
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * Assign / Unassign
     * --------------------------------------------------------
     */

    if (
        action === "assign" ||
        action === "unassign"
    ) {

        const caseId =
            parseCaseId(
                parts[2]
            );


        if (!caseId) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "Invalid case ID."
                )
            );

            return;
        }


        await handleInvestigatorAction(
            interaction,
            action,
            caseId
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * Status
     * --------------------------------------------------------
     */

    if (
        action === "status"
    ) {

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
 * Case ID parser
 * ============================================================
 */

function parseCaseId(
    value
) {

    const parsed =
        Number.parseInt(
            value,
            10
        );


    if (
        !Number.isInteger(parsed) ||
        parsed <= 0
    ) {

        return null;
    }


    return parsed;
}


/*
 * ============================================================
 * Add Evidence
 * ============================================================
 */

async function handleAddEvidence(
    interaction,
    caseId
) {

    const caseData =
        await getCase(
            caseId
        );


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
        infoPanel(
            "Add Evidence",
            `The evidence upload form for Case #${caseId} will be connected here.`
        )
    );
}


/*
 * ============================================================
 * Investigator actions
 * ============================================================
 */

async function handleInvestigatorAction(
    interaction,
    action,
    caseId
) {

    const caseData =
        await getCase(
            caseId
        );


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
        infoPanel(
            action === "assign"
                ? "Assign Investigator"
                : "Unassign Investigator",

            `Investigator selection for Case #${caseId} will be connected here.`
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

    const status =
        String(
            parts[2] || ""
        ).toUpperCase();


    const caseId =
        parseCaseId(
            parts[3]
        );


    const allowedStatuses = [
        "OPEN",
        "INVESTIGATING",
        "WAITING_FOR_EVIDENCE",
        "ESCALATED",
        "RESOLVED",
        "CLOSED"
    ];


    if (!caseId) {

        await safelyRespond(
            interaction,
            errorPanel(
                "Invalid case ID."
            )
        );

        return;
    }


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


    const caseData =
        await getCase(
            caseId
        );


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
        infoPanel(
            "Status Change",
            `Case #${caseId} selected for status **${status.replaceAll("_", " ")}**.\n\n` +
            "The shared CaseManager mutation layer will process this action."
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


    if (
        !result.rows.length
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

            components:
                payload.components

        });

    } else {

        await interaction.reply(
            payload
        );
    }
}


/*
 * ============================================================
 * END OF interactionCreate.js
 * ============================================================
 */