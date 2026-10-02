const {
    MessageFlags,
    StringSelectMenuBuilder,
    StringSelectMenuOptionBuilder,
    ActionRowBuilder,
    RoleSelectMenuBuilder,
    ContainerBuilder,
    TextDisplayBuilder,
    SeparatorBuilder,
    SeparatorSpacingSize
} = require("discord.js");

const config = require("../config");
const permissionService =
    require("../permissions/permissionService");

const caseMutationService =
    require("../services/caseMutationService");

const {
    casePanel,
    caseEvidencePanel,
    caseTimelinePanel,
    caseStaffPanel,
    caseStatusPanel,
    settingsPanel,
    errorPanel,
    successPanel,
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
 * CaseManager — Discord Interaction Controller
 * ============================================================
 *
 * Handles:
 *
 *  • Slash commands
 *  • Case buttons
 *  • Case status changes
 *  • Investigator controls
 *  • Permission settings
 *  • Discord role selection
 *
 * Components V2 only.
 * ============================================================
 */


/*
 * ============================================================
 * Main handler
 * ============================================================
 */

async function handleInteraction(interaction) {

    try {

        if (
            interaction.isChatInputCommand()
        ) {

            await handleChatInputCommand(
                interaction
            );

            return;
        }


        /*
         * RoleSelectMenu must be checked separately.
         */

        if (
            interaction.isRoleSelectMenu()
        ) {

            await handleRoleSelect(
                interaction
            );

            return;
        }


        if (
            interaction.isStringSelectMenu()
        ) {

            await handleStringSelect(
                interaction
            );

            return;
        }


        if (
            interaction.isButton()
        ) {

            await handleButton(
                interaction
            );

            return;
        }

    } catch (error) {

        if (
            error instanceof
            InteractionHandledError
        ) {

            return;
        }


        console.error(
            "[CaseManager] Interaction error:",
            error
        );


        await safelyRespond(
            interaction,
            errorPanel(
                config.bot.debug
                    ? `Error: ${error.message}`
                    : "An unexpected error occurred."
            )
        );
    }
}


/*
 * ============================================================
 * Slash commands
 * ============================================================
 */

async function handleChatInputCommand(
    interaction
) {

    const command =
        interaction.client.commands?.get(
            interaction.commandName
        );


    if (!command) {

        await safelyRespond(
            interaction,
            errorPanel(
                "That command is not available."
            )
        );

        return;
    }


    await command.execute(
        interaction
    );
}


/*
 * ============================================================
 * Buttons
 * ============================================================
 */

async function handleButton(
    interaction
) {

    const customId =
        String(
            interaction.customId || ""
        );


    if (
        customId.startsWith("case:")
    ) {

        await handleCaseButton(
            interaction
        );

        return;
    }


    if (
        customId.startsWith("settings:")
    ) {

        await handleSettingsButton(
            interaction
        );

        return;
    }


    await safelyRespond(
        interaction,
        errorPanel(
            "This button is no longer available."
        )
    );
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
            parseCaseId(parts[2]);


        await requirePermission(
            interaction,
            "case"
        );


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

        if (
            parts[2] === "add"
        ) {

            const caseId =
                parseCaseId(parts[3]);


            await requirePermission(
                interaction,
                "evidence"
            );


            await beginEvidenceAdd(
                interaction,
                caseId
            );

            return;
        }


        const caseId =
            parseCaseId(parts[2]);


        await requirePermission(
            interaction,
            "evidence"
        );


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
            parseCaseId(parts[2]);


        await requirePermission(
            interaction,
            "timeline"
        );


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
            parseCaseId(parts[2]);


        await requirePermission(
            interaction,
            "investigators"
        );


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
            parseCaseId(parts[2]);


        await requirePermission(
            interaction,
            "investigators"
        );


        await beginInvestigatorAssignment(
            interaction,
            caseId
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * Remove investigator
     * --------------------------------------------------------
     */

    if (
        action === "unassign"
    ) {

        const caseId =
            parseCaseId(parts[2]);


        await requirePermission(
            interaction,
            "investigators"
        );


        await beginInvestigatorRemoval(
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

        const status =
            normalizeStatus(
                parts[2]
            );


        const caseId =
            parseCaseId(
                parts[3]
            );


        await requirePermission(
            interaction,
            "status"
        );


        await changeCaseStatus(
            interaction,
            caseId,
            status
        );

        return;
    }


    await safelyRespond(
        interaction,
        errorPanel(
            "Unknown CaseManager case action."
        )
    );
}


/*
 * ============================================================
 * Show case
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
 * Evidence upload placeholder
 * ============================================================
 */

async function beginEvidenceAdd(
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
     * We intentionally do not create a fake evidence record.
     *
     * The final implementation will connect Discord
     * attachments to MediaEvidenceService.
     */

    await safelyRespond(
        interaction,
        infoPanel(
            "Discord evidence upload is not connected yet. " +
            "No database record was created."
        )
    );
}
/*
 * ============================================================
 * Change case status
 * ============================================================
 */

async function changeCaseStatus(
    interaction,
    caseId,
    status
) {

    const normalizedStatus =
        normalizeStatus(status);


    if (!normalizedStatus) {

        await safelyRespond(
            interaction,
            errorPanel(
                "Invalid case status."
            )
        );

        return;
    }


    const actorUuid =
        interaction.user?.id || null;

    const actorName =
        interaction.user?.username ||
        interaction.user?.globalName ||
        "Discord User";


    const updatedCase =
        await caseMutationService.changeStatus(
            caseId,
            normalizedStatus,
            actorUuid,
            actorName
        );


    if (!updatedCase) {

        await safelyRespond(
            interaction,
            errorPanel(
                `Case #${caseId} was not found.`
            )
        );

        return;
    }


    /*
     * Show the updated case status directly.
     * Do not send a temporary success panel and then
     * overwrite it.
     */

    await safelyRespond(
        interaction,
        caseStatusPanel(
            updatedCase
        )
    );
}


/*
 * ============================================================
 * Begin investigator assignment
 * ============================================================
 */

async function beginInvestigatorAssignment(
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
     * Investigator assignment requires a Minecraft UUID.
     *
     * N7-Link is responsible for the Minecraft ↔ Discord
     * account relationship, but its final database/API
     * contract has not yet been added to CaseManager.
     *
     * Therefore we do not guess its schema here.
     */

    await safelyRespond(
        interaction,
        infoPanel(
            "Investigator assignment is waiting for the " +
            "N7-Link integration. No investigator was assigned."
        )
    );
}


/*
 * ============================================================
 * Begin investigator removal
 * ============================================================
 */

async function beginInvestigatorRemoval(
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
        !investigators ||
        investigators.length === 0
    ) {

        await safelyRespond(
            interaction,
            infoPanel(
                `Case #${caseId} has no assigned investigators.`
            )
        );

        return;
    }


    const options =
        investigators
            .slice(0, 25)
            .map(
                investigator =>
                    new StringSelectMenuOptionBuilder()
                        .setLabel(
                            truncate(
                                investigator.playerName ||
                                investigator.playerUuid ||
                                "Unknown Investigator",
                                100
                            )
                        )
                        .setDescription(
                            truncate(
                                investigator.playerUuid ||
                                "Unknown UUID",
                                100
                            )
                        )
                        .setValue(
                            String(
                                investigator.playerUuid
                            )
                        )
            );


    const selectMenu =
        new StringSelectMenuBuilder()
            .setCustomId(
                `case:remove-investigator:${caseId}`
            )
            .setPlaceholder(
                "Select an investigator to remove"
            )
            .addOptions(
                options
            );


    const row =
        new ActionRowBuilder()
            .addComponents(
                selectMenu
            );


    const container =
        new ContainerBuilder()
            .addTextDisplayComponents(
                new TextDisplayBuilder().setContent(
                    `## Remove Investigator\n` +
                    `Select an investigator assigned to ` +
                    `**Case #${caseId}**.`
                )
            )
            .addSeparatorComponents(
                new SeparatorBuilder()
                    .setSpacing(
                        SeparatorSpacingSize.Small
                    )
            )
            .addActionRowComponents(
                row
            );


    await safelyRespond(
        interaction,
        container
    );
}


/*
 * ============================================================
 * String select menus
 * ============================================================
 */

async function handleStringSelect(
    interaction
) {

    const customId =
        String(
            interaction.customId || ""
        );


    /*
     * --------------------------------------------------------
     * Remove investigator
     * --------------------------------------------------------
     */

    if (
        customId.startsWith(
            "case:remove-investigator:"
        )
    ) {

        const parts =
            customId.split(":");


        const caseId =
            parseCaseId(
                parts[2]
            );


        await requirePermission(
            interaction,
            "investigators"
        );


        const selectedUuid =
            interaction.values?.[0];


        if (!selectedUuid) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "No investigator was selected."
                )
            );

            return;
        }


        const actorUuid =
            interaction.user?.id || null;

        const actorName =
            interaction.user?.username ||
            interaction.user?.globalName ||
            "Discord User";


        const removed =
            await caseMutationService.unassignInvestigator(
                caseId,
                selectedUuid,
                actorUuid,
                actorName
            );


        if (!removed) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "That investigator is no longer assigned to this case."
                )
            );

            return;
        }


        const updatedInvestigators =
            await caseMutationService.getInvestigators(
                caseId
            );


        const caseData =
            await caseMutationService.getCase(
                caseId
            );


        await safelyRespond(
            interaction,
            caseStaffPanel(
                caseData,
                updatedInvestigators
            )
        );

        return;
    }


    await safelyRespond(
        interaction,
        errorPanel(
            "This selection menu is no longer available."
        )
    );
}


/*
 * ============================================================
 * Role selection
 * ============================================================
 */

async function handleRoleSelect(
    interaction
) {

    const customId =
        String(
            interaction.customId || ""
        );


    if (
        !customId.startsWith(
            "settings:permissions:selectrole:"
        )
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "This role selector is no longer available."
            )
        );

        return;
    }


    await requirePermissionManager(
        interaction
    );


    const parts =
        customId.split(":");


    const roleType =
        parts[3];


    const allowedRoleTypes = [
        "global",
        "case",
        "evidence",
        "timeline",
        "investigators",
        "status",
        "settings",
        "permissions"
    ];


    if (
        !allowedRoleTypes.includes(
            roleType
        )
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "Invalid CaseManager role type."
            )
        );

        return;
    }


    const selectedRole =
        interaction.roles?.first();


    if (!selectedRole) {

        await safelyRespond(
            interaction,
            errorPanel(
                "No role was selected."
            )
        );

        return;
    }


    const settings =
        await permissionService.getSettings(
            interaction.guildId
        );


    applyRoleSetting(
        settings,
        roleType,
        selectedRole.id
    );


    await savePermissionSettings(
        interaction.guildId,
        settings,
        interaction.user.id
    );


    const updatedSettings =
        await permissionService.getSettings(
            interaction.guildId
        );


    await safelyRespond(
        interaction,
        permissionRolesPanel(
            updatedSettings
        )
    );
}


/*
 * ============================================================
 * Settings buttons
 * ============================================================
 */

async function handleSettingsButton(
    interaction
) {

    await requirePermissionManager(
        interaction
    );


    const customId =
        String(
            interaction.customId || ""
        );


    /*
     * Main settings page
     */

    if (
        customId === "settings:permissions"
    ) {

        await safelyRespond(
            interaction,
            permissionSettingsPanel(
                interaction.guildId
            )
        );

        return;
    }


    /*
     * Permission mode menu
     */

    if (
        customId ===
        "settings:permissions:mode"
    ) {

        const settings =
            await permissionService.getSettings(
                interaction.guildId
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
     * Permission role menu
     */

    if (
        customId ===
        "settings:permissions:roles"
    ) {

        const settings =
            await permissionService.getSettings(
                interaction.guildId
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
     * Refresh permission settings
     */

    if (
        customId ===
        "settings:permissions:refresh"
    ) {

        permissionService.clearCache(
            interaction.guildId
        );


        await safelyRespond(
            interaction,
            permissionSettingsPanel(
                interaction.guildId
            )
        );

        return;
    }


    /*
     * Back from settings
     */

    if (
        customId === "settings:back"
    ) {

        await safelyRespond(
            interaction,
            settingsPanel()
        );

        return;
    }


    /*
     * Back to permission settings
     */

    if (
        customId ===
        "settings:permissions"
    ) {

        await safelyRespond(
            interaction,
            permissionSettingsPanel(
                interaction.guildId
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

        await updatePermissionMode(
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

        await updatePermissionMode(
            interaction,
            "per-command"
        );

        return;
    }


    /*
     * Role configuration
     */

    if (
        customId.startsWith(
            "settings:permissions:setrole:"
        )
    ) {

        const roleType =
            customId.substring(
                "settings:permissions:setrole:"
                    .length
            );


        await showRoleSelector(
            interaction,
            roleType
        );

        return;
    }


    /*
     * Clear role
     */

    if (
        customId.startsWith(
            "settings:permissions:clearrole:"
        )
    ) {

        const roleType =
            customId.substring(
                "settings:permissions:clearrole:"
                    .length
            );


        await clearPermissionRole(
            interaction,
            roleType
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

async function updatePermissionMode(
    interaction,
    mode
) {

    if (
        mode !== "global" &&
        mode !== "per-command"
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "Invalid permission mode."
            )
        );

        return;
    }


    const settings =
        await permissionService.getSettings(
            interaction.guildId
        );


    settings.mode =
        mode;


    await savePermissionSettings(
        interaction.guildId,
        settings,
        interaction.user.id
    );


    const updatedSettings =
        await permissionService.getSettings(
            interaction.guildId
        );


    await safelyRespond(
        interaction,
        permissionModePanel(
            updatedSettings
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

    const allowedRoleTypes = [
        "global",
        "case",
        "evidence",
        "timeline",
        "investigators",
        "status",
        "settings",
        "permissions"
    ];


    if (
        !allowedRoleTypes.includes(
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
            interaction.guildId
        );


    applyRoleSetting(
        settings,
        roleType,
        ""
    );


    await savePermissionSettings(
        interaction.guildId,
        settings,
        interaction.user.id
    );


    const updatedSettings =
        await permissionService.getSettings(
            interaction.guildId
        );


    await safelyRespond(
        interaction,
        permissionRolesPanel(
            updatedSettings
        )
    );
}


/*
 * ============================================================
 * Show Discord role selector
 * ============================================================
 */

async function showRoleSelector(
    interaction,
    roleType
) {

    const allowedRoleTypes = [
        "global",
        "case",
        "evidence",
        "timeline",
        "investigators",
        "status",
        "settings",
        "permissions"
    ];


    if (
        !allowedRoleTypes.includes(
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
            interaction.guildId
        );


    const panel =
        roleSelectionPanel(
            roleType,
            settings
        );


    /*
     * The settings panel tells the user what
     * role is being configured.
     *
     * We append the native Discord RoleSelectMenu.
     */

    const roleSelector =
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
                roleSelector
            );


    if (
        panel instanceof ContainerBuilder
    ) {

        panel.addActionRowComponents(
            row
        );

    } else {

        await safelyRespond(
            interaction,
            errorPanel(
                "Unable to create the role selector."
            )
        );

        return;
    }


    await safelyRespond(
        interaction,
        panel
    );
}
/*
 * ============================================================
 * Permission enforcement
 * ============================================================
 */

async function requirePermission(
    interaction,
    commandName
) {

    if (
        !interaction.guild
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "This action can only be used inside a server."
            )
        );

        throw new InteractionHandledError();
    }


    const allowed =
        await permissionService.hasPermission(
            interaction.member,
            commandName
        );


    if (allowed) {
        return true;
    }


    const permissionInfo =
        await permissionService.getPermissionInfo(
            interaction.guildId,
            commandName
        );


    let message =
        "You do not have permission to use this CaseManager action.";


    if (
        permissionInfo.configured
    ) {

        message +=
            `\nRequired role: <@&${permissionInfo.roleId}>`;

    } else {

        message +=
            "\nNo CaseManager role has been configured for this action.";
    }


    await safelyRespond(
        interaction,
        errorPanel(
            message
        )
    );


    throw new InteractionHandledError();
}


/*
 * ============================================================
 * Permission-manager enforcement
 * ============================================================
 */

async function requirePermissionManager(
    interaction
) {

    if (
        !interaction.guild
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "This action can only be used inside a server."
            )
        );

        throw new InteractionHandledError();
    }


    const allowed =
        await permissionService.canManagePermissions(
            interaction.member
        );


    if (allowed) {
        return true;
    }


    await safelyRespond(
        interaction,
        errorPanel(
            "You do not have permission to manage CaseManager permissions."
        )
    );


    throw new InteractionHandledError();
}


/*
 * ============================================================
 * Apply role setting
 * ============================================================
 */

function applyRoleSetting(
    settings,
    roleType,
    roleId
) {

    const normalizedRoleId =
        typeof roleId === "string"
            ? roleId.trim()
            : "";


    if (
        roleType === "global"
    ) {

        settings.globalRoleId =
            normalizedRoleId;

        return;
    }


    if (
        !settings.perCommand
    ) {

        settings.perCommand = {};
    }


    const allowedRoleTypes = [
        "case",
        "evidence",
        "timeline",
        "investigators",
        "status",
        "settings",
        "permissions"
    ];


    if (
        allowedRoleTypes.includes(
            roleType
        )
    ) {

        settings.perCommand[roleType] =
            normalizedRoleId;
    }
}


/*
 * ============================================================
 * Parse case ID
 * ============================================================
 */

function parseCaseId(
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
 * Normalize status
 * ============================================================
 */

function normalizeStatus(
    value
) {

    const status =
        String(
            value || ""
        )
            .trim()
            .toUpperCase();


    const validStatuses = [
        "OPEN",
        "INVESTIGATING",
        "WAITING_FOR_EVIDENCE",
        "ESCALATED",
        "RESOLVED",
        "CLOSED"
    ];


    if (
        !validStatuses.includes(
            status
        )
    ) {

        return null;
    }


    return status;
}


/*
 * ============================================================
 * Format status
 * ============================================================
 */

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
        .toLowerCase()
        .replace(
            /\b\w/g,
            character =>
                character.toUpperCase()
        );
}


/*
 * ============================================================
 * Format role type
 * ============================================================
 */

function formatRoleType(
    roleType
) {

    return String(
        roleType || ""
    )
        .replaceAll(
            "_",
            " "
        )
        .replace(
            /-/g,
            " "
        )
        .toLowerCase()
        .replace(
            /\b\w/g,
            character =>
                character.toUpperCase()
        );
}


/*
 * ============================================================
 * Truncate text
 * ============================================================
 */

function truncate(
    value,
    maxLength
) {

    const text =
        String(
            value ?? ""
        );


    if (
        text.length <= maxLength
    ) {

        return text;
    }


    if (
        maxLength <= 3
    ) {

        return text.substring(
            0,
            maxLength
        );
    }


    return (
        text.substring(
            0,
            maxLength - 3
        ) +
        "..."
    );
}


/*
 * ============================================================
 * Create generic select panel
 * ============================================================
 */

function createSelectPanel(
    title,
    description,
    selectMenu
) {

    const container =
        new ContainerBuilder()
            .addTextDisplayComponents(
                new TextDisplayBuilder()
                    .setContent(
                        `## ${title}\n${description}`
                    )
            )
            .addSeparatorComponents(
                new SeparatorBuilder()
                    .setSpacing(
                        SeparatorSpacingSize.Small
                    )
            );


    if (
        selectMenu
    ) {

        const row =
            new ActionRowBuilder()
                .addComponents(
                    selectMenu
                );


        container.addActionRowComponents(
            row
        );
    }


    return container;
}


/*
 * ============================================================
 * Safely respond
 * ============================================================
 */

async function safelyRespond(
    interaction,
    payload
) {

    if (!interaction) {
        return;
    }


    try {

        if (
            interaction.replied
        ) {

            await interaction.editReply(
                payload
            );

            return;
        }


        if (
            interaction.deferred
        ) {

            await interaction.editReply(
                payload
            );

            return;
        }


        await interaction.reply(
            payload
        );

    } catch (error) {

        /*
         * Discord may reject a second response if the
         * interaction was already acknowledged elsewhere.
         *
         * Avoid throwing another error from the error handler.
         */

        if (
            error?.code === 10062 ||
            error?.code === 40060
        ) {

            return;
        }


        console.error(
            "[CaseManager] Failed to respond to interaction:",
            error
        );
    }
}


/*
 * ============================================================
 * Safely edit
 * ============================================================
 */

async function safelyEdit(
    interaction,
    payload
) {

    if (!interaction) {
        return;
    }


    try {

        await interaction.editReply(
            payload
        );

    } catch (error) {

        if (
            error?.code === 10062 ||
            error?.code === 40060
        ) {

            return;
        }


        console.error(
            "[CaseManager] Failed to edit interaction:",
            error
        );
    }
}


/*
 * ============================================================
 * Internal handled-interaction error
 * ============================================================
 */

class InteractionHandledError
    extends Error {

    constructor() {

        super(
            "Interaction has already been handled."
        );

        this.name =
            "InteractionHandledError";
    }
}


/*
 * ============================================================
 * Discord event export
 * ============================================================
 */

module.exports =
    async function interactionCreate(
        interaction
    ) {

        try {

            await handleInteraction(
                interaction
            );

        } catch (error) {

            /*
             * Permission handlers deliberately throw this
             * after sending their response so execution stops.
             */

            if (
                error instanceof
                InteractionHandledError
            ) {

                return;
            }


            console.error(
                "[CaseManager] Unhandled interaction error:",
                error
            );


            await safelyRespond(
                interaction,
                errorPanel(
                    config.bot.debug
                        ? `Error: ${error.message}`
                        : "An unexpected error occurred."
                )
            );
        }
    };