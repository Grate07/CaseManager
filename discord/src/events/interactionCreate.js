const {
    MessageFlags,
    ActionRowBuilder,
    RoleSelectMenuBuilder,
    StringSelectMenuBuilder
} = require("discord.js");

const permissionService =
    require("../permissions/permissionService");

const database =
    require("../database/database");

const caseMutationService =
    require("../services/caseMutationService");

const {
    casePanel,
    caseEvidencePanel,
    caseTimelinePanel,
    caseStaffPanel,
    caseStatusPanel,
    errorPanel,
    infoPanel,
    successPanel,
    settingsPanel
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
 * - Status changes
 * - Investigator assignment
 * - Investigator removal
 * - Permission settings
 * - Discord role selectors
 *
 * Components V2 is used for all UI responses.
 * ============================================================
 */


module.exports = async function interactionCreate(
    interaction
) {

    try {

        /*
         * ----------------------------------------------------
         * Slash command
         * ----------------------------------------------------
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
         * ----------------------------------------------------
         * Buttons
         * ----------------------------------------------------
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
         * ----------------------------------------------------
         * Role select
         * ----------------------------------------------------
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
 * Slash commands
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
 * Button router
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
                "CaseManager controls can only be used inside a Discord server."
            )
        );

        return;
    }


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


    if (
        interaction.customId.startsWith(
            "settings:"
        )
    ) {

        await handleSettingsButton(
            interaction
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
     * Defer immediately because database operations can take
     * longer than Discord's initial interaction window.
     */

    await deferComponents(
        interaction
    );


    /*
     * --------------------------------------------------------
     * View case
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
         * IMPORTANT:
         *
         * case:evidence:add:<id>
         *
         * must be handled before
         *
         * case:evidence:<id>
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
     * Assign investigator
     * --------------------------------------------------------
     */

    if (
        action === "assign"
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


        await handleAssignInvestigator(
            interaction,
            caseId
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * Unassign investigator
     * --------------------------------------------------------
     */

    if (
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


        await handleUnassignInvestigator(
            interaction,
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
 * View case
 * ============================================================
 */

async function showCase(
    interaction,
    caseId
) {

    const caseData =
        await caseMutationService.getCase(
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
        casePanel(
            caseData
        )
    );
}
/*
 * ============================================================
 * Show evidence
 * ============================================================
 */

async function showEvidence(
    interaction,
    caseId
) {

    const caseData =
        await caseMutationService.getCase(
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


    const evidence =
        await caseMutationService.getEvidence(
            caseId
        );


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
 * Show timeline
 * ============================================================
 */

async function showTimeline(
    interaction,
    caseId
) {

    const caseData =
        await caseMutationService.getCase(
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


    const timeline =
        await caseMutationService.getTimeline(
            caseId
        );


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
 * Show investigators
 * ============================================================
 */

async function showInvestigators(
    interaction,
    caseId
) {

    const caseData =
        await caseMutationService.getCase(
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


    const investigators =
        await caseMutationService.getInvestigators(
            caseId
        );


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
 * Add evidence
 * ============================================================
 */

async function handleAddEvidence(
    interaction,
    caseId
) {

    const caseData =
        await caseMutationService.getCase(
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


    /*
     * Evidence upload UI will be connected after the core
     * mutation system is complete.
     */

    await safelyRespond(
        interaction,
        infoPanel(
            "Add Evidence",
            `Evidence upload for Case #${caseId} is ready for the next storage/upload integration step.`
        )
    );
}


/*
 * ============================================================
 * Status button
 * ============================================================
 */

async function handleStatusButton(
    interaction,
    parts
) {

    const status =
        String(
            parts[2] || ""
        )
            .trim()
            .toUpperCase();


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


    if (
        !caseMutationService.isValidStatus(
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


    const result =
        await caseMutationService.setCaseStatus(
            caseId,
            status,
            interaction.user.id,
            getActorName(
                interaction
            )
        );


    if (
        !result.changed
    ) {

        await showCaseStatus(
            interaction,
            caseId,
            `Case #${caseId} is already ${formatStatus(status)}.`
        );

        return;
    }


    await showCaseStatus(
        interaction,
        caseId,
        `Case status changed from ${formatStatus(result.oldStatus)} to ${formatStatus(result.newStatus)}.`
    );
}


/*
 * ============================================================
 * Show status panel
 * ============================================================
 */

async function showCaseStatus(
    interaction,
    caseId,
    message
) {

    const caseData =
        await caseMutationService.getCase(
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
        caseStatusPanel(
            caseData,
            message
        )
    );
}


/*
 * ============================================================
 * Assign investigator
 * ============================================================
 */

async function handleAssignInvestigator(
    interaction,
    caseId
) {

    const caseData =
        await caseMutationService.getCase(
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


    /*
     * For the first version, investigators are selected from
     * Discord members.
     *
     * We use a StringSelectMenu because the actual investigator
     * may be a linked Minecraft player rather than a Discord
     * user. The next linking layer will resolve the Discord
     * member to the Minecraft UUID.
     */

    await safelyRespond(
        interaction,
        infoPanel(
            "Assign Investigator",
            `Investigator assignment for Case #${caseId} is connected to the mutation service.`
        )
    );
}
/*
 * ============================================================
 * Unassign investigator
 * ============================================================
 */

async function handleUnassignInvestigator(
    interaction,
    caseId
) {

    const caseData =
        await caseMutationService.getCase(
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


    const investigators =
        await caseMutationService.getInvestigators(
            caseId
        );


    if (
        investigators.length === 0
    ) {

        await safelyRespond(
            interaction,
            infoPanel(
                "Investigators",
                `Case #${caseId} currently has no assigned investigators.`
            )
        );

        return;
    }


    /*
     * Create a select menu from currently assigned
     * investigators.
     */

    const options =
        investigators
            .slice(0, 25)
            .map(
                investigator => ({

                    label:
                        truncate(
                            investigator.investigatorName ||
                            investigator.investigatorUuid,
                            100
                        ),

                    value:
                        investigator.investigatorUuid,

                    description:
                        "Remove this investigator from the case."
                })
            );


    const menu =
        new StringSelectMenuBuilder()
            .setCustomId(
                `case:remove-investigator:${caseId}`
            )
            .setPlaceholder(
                "Select an investigator to remove"
            )
            .setMinValues(1)
            .setMaxValues(1)
            .addOptions(
                options
            );


    const row =
        new ActionRowBuilder()
            .addComponents(
                menu
            );


    await safelyRespond(
        interaction,
        createInvestigatorRemovalPanel(
            caseData,
            row
        )
    );
}


/*
 * ============================================================
 * Investigator removal select
 * ============================================================
 */

async function handleInvestigatorRemovalSelect(
    interaction
) {

    const parts =
        interaction.customId.split(":");


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


    const investigatorUuid =
        interaction.values[0];


    if (!investigatorUuid) {

        await safelyRespond(
            interaction,
            errorPanel(
                "No investigator was selected."
            )
        );

        return;
    }


    const investigators =
        await caseMutationService.getInvestigators(
            caseId
        );


    const investigator =
        investigators.find(
            entry =>
                entry.investigatorUuid ===
                investigatorUuid
        );


    if (!investigator) {

        await safelyRespond(
            interaction,
            errorPanel(
                "That investigator is no longer assigned to this case."
            )
        );

        return;
    }


    const result =
        await caseMutationService.removeInvestigator(
            caseId,
            investigatorUuid,
            interaction.user.id,
            getActorName(
                interaction
            )
        );


    if (
        !result.removed
    ) {

        await safelyRespond(
            interaction,
            infoPanel(
                "Investigator",
                "That investigator was already removed from the case."
            )
        );

        return;
    }


    const updatedCase =
        await caseMutationService.getCase(
            caseId
        );


    const updatedInvestigators =
        await caseMutationService.getInvestigators(
            caseId
        );


    await safelyRespond(
        interaction,
        caseStaffPanel(
            updatedCase,
            updatedInvestigators
        )
    );
}


/*
 * ============================================================
 * Create investigator removal panel
 * ============================================================
 */

function createInvestigatorRemovalPanel(
    caseData,
    row
) {

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
                `# Remove Investigator\n` +
                `Case #${caseData.id}\n\n` +
                "Select the investigator you want to remove."
            )
    );


    container.addSeparatorComponents(
        new SeparatorBuilder()
            .setSpacing(
                SeparatorSpacingSize.Small
            )
    );


    container.addActionRowComponents(
        row
    );


    return container;
}


/*
 * ============================================================
 * Investigator removal select router
 * ============================================================
 */

async function handleStringSelect(
    interaction
) {

    if (
        !interaction.customId.startsWith(
            "case:remove-investigator:"
        )
    ) {

        return false;
    }


    const allowed =
        await permissionService.hasPermission(
            interaction.member,
            "case"
        );


    if (!allowed) {

        await safelyRespond(
            interaction,
            errorPanel(
                "You do not have permission to modify CaseManager cases."
            )
        );

        return true;
    }


    await deferComponents(
        interaction
    );


    await handleInvestigatorRemovalSelect(
        interaction
    );


    return true;
}
/*
 * ============================================================
 * Settings button router
 * ============================================================
 */

async function handleSettingsButton(
    interaction
) {

    const customId =
        interaction.customId;


    /*
     * Permission management gets its own permission.
     */

    if (
        customId.startsWith(
            "settings:permissions"
        )
    ) {

        const allowed =
            await permissionService.canManagePermissions(
                interaction.member
            );


        if (!allowed) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "You do not have permission to manage CaseManager permissions."
                )
            );

            return;
        }


        await handlePermissionSettings(
            interaction
        );

        return;
    }


    /*
     * General settings.
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
                "You do not have permission to access CaseManager settings."
            )
        );

        return;
    }


    await deferComponents(
        interaction
    );


    if (
        customId ===
        "settings:back"
    ) {

        await safelyRespond(
            interaction,
            settingsPanel()
        );

        return;
    }


    if (
        customId ===
        "settings:storage"
    ) {

        await safelyRespond(
            interaction,
            infoPanel(
                "Evidence Storage",
                "Supabase Storage configuration will be connected here."
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
 * Permission settings router
 * ============================================================
 */

async function handlePermissionSettings(
    interaction
) {

    await deferComponents(
        interaction
    );


    const customId =
        interaction.customId;


    /*
     * Main permission panel
     */

    if (
        customId ===
        "settings:permissions" ||
        customId ===
        "settings:permissions:refresh"
    ) {

        await safelyRespond(
            interaction,
            permissionSettingsPanel(
                interaction.guild.id
            )
        );

        return;
    }


    /*
     * Mode panel
     */

    if (
        customId ===
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
     * Global mode
     */

    if (
        customId ===
        "settings:permissions:mode:global"
    ) {

        await changePermissionMode(
            interaction,
            "global"
        );

        return;
    }


    /*
     * Per-command mode
     */

    if (
        customId ===
        "settings:permissions:mode:per-command"
    ) {

        await changePermissionMode(
            interaction,
            "per-command"
        );

        return;
    }


    /*
     * Role list
     */

    if (
        customId ===
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


    const parts =
        customId.split(":");


    /*
     * Role details
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
                    "Invalid permission role."
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
     * Set role
     */

    if (
        parts[0] === "settings" &&
        parts[1] === "permissions" &&
        parts[2] === "setrole"
    ) {

        await showRoleSelector(
            interaction,
            parts[3]
        );

        return;
    }


    /*
     * Clear role
     */

    if (
        parts[0] === "settings" &&
        parts[1] === "permissions" &&
        parts[2] === "clearrole"
    ) {

        await clearPermissionRole(
            interaction,
            parts[3]
        );

        return;
    }


    await safelyRespond(
        interaction,
        errorPanel(
            "Unknown permission settings action."
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


    await safelyRespond(
        interaction,
        permissionSettingsPanel(
            interaction.guild.id
        )
    );
}
/*
 * ============================================================
 * Role selector
 * ============================================================
 */

async function showRoleSelector(
    interaction,
    roleType
) {

    if (
        !isValidRoleType(
            roleType
        )
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "Invalid permission role."
            )
        );

        return;
    }


    const settings =
        await permissionService.getSettings(
            interaction.guild.id
        );


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


    await safelyRespond(
        interaction,
        createRoleSelectorPanel(
            roleType,
            getRoleId(
                roleType,
                settings
            ),
            row
        )
    );
}


/*
 * ============================================================
 * Role selector interaction
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


    const allowed =
        await permissionService.canManagePermissions(
            interaction.member
        );


    if (!allowed) {

        await safelyRespond(
            interaction,
            errorPanel(
                "You do not have permission to manage CaseManager permissions."
            )
        );

        return;
    }


    const parts =
        interaction.customId.split(":");


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
                "Invalid permission role."
            )
        );

        return;
    }


    const roleId =
        interaction.values[0];


    const role =
        interaction.guild.roles.cache.get(
            roleId
        );


    if (!role) {

        await safelyRespond(
            interaction,
            errorPanel(
                "The selected role could not be found."
            )
        );

        return;
    }


    /*
     * @everyone cannot be used as a permission role.
     */

    if (
        role.id ===
        interaction.guild.id
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "The @everyone role cannot be used as a CaseManager permission role."
            )
        );

        return;
    }


    /*
     * Managed/integration roles should not be used.
     */

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

    if (
        !isValidRoleType(
            roleType
        )
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "Invalid permission role."
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


    await safelyRespond(
        interaction,
        permissionSettingsPanel(
            interaction.guild.id
        )
    );
}


/*
 * ============================================================
 * Helpers
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


function getActorName(
    interaction
) {

    return (
        interaction.member?.displayName ||
        interaction.user?.globalName ||
        interaction.user?.username ||
        "Unknown Discord User"
    );
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
        )
        .replace(
            /\b\w/g,
            character =>
                character.toUpperCase()
        );
}


function truncate(
    value,
    maxLength
) {

    const text =
        String(
            value || ""
        );


    if (
        text.length <= maxLength
    ) {

        return text;
    }


    return (
        text.slice(
            0,
            maxLength - 3
        ) +
        "..."
    );
}


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
        String(
            roleType || ""
        ).toLowerCase()
    );
}


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
        settings.perCommand?.[
            roleType
        ] ||
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

    const {
        ContainerBuilder,
        TextDisplayBuilder,
        SeparatorBuilder,
        SeparatorSpacingSize
    } = require("discord.js");


    const names = {

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
            "Permission Management Role"
    };


    const name =
        names[roleType] ||
        "CaseManager Role";


    const current =
        currentRole
            ? `<@&${currentRole}>`
            : "Not configured";


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
 * Create removal panel
 * ============================================================
 */

function createInvestigatorRemovalPanel(
    caseData,
    row
) {

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
                `# Remove Investigator\n` +
                `Case #${caseData.id}\n\n` +
                "Select the investigator you want to remove."
            )

    );


    container.addSeparatorComponents(

        new SeparatorBuilder()
            .setSpacing(
                SeparatorSpacingSize.Small
            )

    );


    container.addActionRowComponents(
        row
    );


    return container;
}


/*
 * ============================================================
 * Deferred Components V2 response
 * ============================================================
 */

async function deferComponents(
    interaction
) {

    if (
        interaction.replied ||
        interaction.deferred
    ) {

        return;
    }


    await interaction.deferReply({

        flags:
            MessageFlags.IsComponentsV2 |
            MessageFlags.Ephemeral

    });
}


/*
 * ============================================================
 * Safe response
 * ============================================================
 */

async function safelyRespond(
    interaction,
    panel
) {

    if (
        interaction.deferred ||
        interaction.replied
    ) {

        await interaction.editReply({

            components: [
                panel
            ]

        });

        return;
    }


    await interaction.reply({

        components: [
            panel
        ],

        flags:
            MessageFlags.IsComponentsV2 |
            MessageFlags.Ephemeral
    });
}