const {
    MessageFlags,
    StringSelectMenuBuilder,
    StringSelectMenuOptionBuilder,
    ActionRowBuilder,
    RoleSelectMenuBuilder
} = require("discord.js");

const config = require("../config");
const permissionService = require("../permissions/permissionService");
const database = require("../database/database");

const caseMutationService =
    require("../services/caseMutationService");

const {
    casePanel,
    caseEvidencePanel,
    caseTimelinePanel,
    caseStaffPanel,
    caseStatusPanel,
    permissionPanel,
    settingsPanel,
    confirmationPanel,
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
 * CaseManager Discord — Interaction Controller
 * ============================================================
 *
 * Handles:
 *
 *  • Slash commands
 *  • Case buttons
 *  • Case status controls
 *  • Investigator controls
 *  • Permission settings
 *  • Role selection
 *
 * Discord Components V2 is used throughout.
 * ============================================================
 */


/*
 * ============================================================
 * Main interaction handler
 * ============================================================
 */

async function handleInteraction(interaction) {

    try {

        /*
         * Slash commands
         */
        if (interaction.isChatInputCommand()) {

            await handleChatInputCommand(interaction);
            return;
        }


        /*
         * Buttons
         */
        if (interaction.isButton()) {

            await handleButton(interaction);
            return;
        }


        /*
         * Normal string select menus
         */
        if (interaction.isStringSelectMenu()) {

            await handleStringSelect(interaction);
            return;
        }


        /*
         * Discord role select menu
         */
        if (interaction.isRoleSelectMenu()) {

            await handleRoleSelect(interaction);
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
                config.bot.debug
                    ? `Error: ${error.message}`
                    : "An unexpected error occurred."
            )
        );
    }
}


/*
 * ============================================================
 * Slash command handling
 * ============================================================
 */

async function handleChatInputCommand(interaction) {

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

    await command.execute(interaction);
}


/*
 * ============================================================
 * Button handling
 * ============================================================
 */

async function handleButton(interaction) {

    const customId =
        String(interaction.customId || "");


    /*
     * --------------------------------------------------------
     * Case buttons
     * --------------------------------------------------------
     */

    if (customId.startsWith("case:")) {

        await handleCaseButton(interaction);
        return;
    }


    /*
     * --------------------------------------------------------
     * Permission/settings buttons
     * --------------------------------------------------------
     */

    if (customId.startsWith("settings:")) {

        await handleSettingsButton(interaction);
        return;
    }


    /*
     * Unknown button
     */

    await safelyRespond(
        interaction,
        errorPanel(
            "This button is no longer available."
        )
    );
}


/*
 * ============================================================
 * Case button router
 * ============================================================
 */

async function handleCaseButton(interaction) {

    const parts =
        interaction.customId.split(":");


    /*
     * Expected formats:
     *
     * case:view:<id>
     * case:evidence:<id>
     * case:evidence:add:<id>
     * case:timeline:<id>
     * case:investigators:<id>
     * case:assign:<id>
     * case:unassign:<id>
     * case:status:<status>:<id>
     */


    const action =
        parts[1];


    /*
     * --------------------------------------------------------
     * View
     * --------------------------------------------------------
     */

    if (action === "view") {

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

    if (action === "evidence") {

        if (parts[2] === "add") {

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

    if (action === "timeline") {

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

    if (action === "investigators") {

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

    if (action === "assign") {

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
     * Unassign investigator
     * --------------------------------------------------------
     */

    if (action === "unassign") {

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

    if (action === "status") {

        const status =
            normalizeStatus(parts[2]);

        const caseId =
            parseCaseId(parts[3]);

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
        casePanel(caseData)
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
 * Evidence addition
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
     * Actual Discord attachment upload will be handled
     * separately. We deliberately do not insert incomplete
     * evidence into the database here.
     */

    await safelyRespond(
        interaction,
        infoPanel(
            "Evidence upload is not configured yet. " +
            "The case database remains unchanged."
        )
    );
}
/*
 * ============================================================
 * Status mutation
 * ============================================================
 */

async function changeCaseStatus(
    interaction,
    caseId,
    status
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


    if (
        !caseMutationService.isValidStatus(
            status
        )
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                `Invalid case status: ${status}`
            )
        );

        return;
    }


    /*
     * Use the Discord user's identity as the
     * actor recorded in the case timeline.
     *
     * Discord IDs are intentionally stored as actor
     * identifiers here; Minecraft UUIDs remain used
     * by Minecraft-side actions.
     */

    const actorUuid =
        interaction.user.id;

    const actorName =
        interaction.member?.displayName ||
        interaction.user.globalName ||
        interaction.user.username;


    const result =
        await caseMutationService.setCaseStatus(
            caseId,
            status,
            actorUuid,
            actorName
        );


    const updatedCase =
        await caseMutationService.getCase(
            caseId
        );


    if (!result.changed) {

        await safelyRespond(
            interaction,
            infoPanel(
                `Case #${caseId} is already ${formatStatus(status)}.`
            )
        );

        return;
    }


    await safelyRespond(
        interaction,
        successPanel(
            `Case #${caseId} status changed to **${formatStatus(status)}**.`
        )
    );


    /*
     * Keep the status panel available after the mutation.
     *
     * We edit the interaction response rather than creating
     * another message.
     */

    if (updatedCase) {

        await safelyEdit(
            interaction,
            caseStatusPanel(updatedCase)
        );
    }
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
     * N7-Link is responsible for Minecraft ↔ Discord
     * account linking.
     *
     * The exact N7-Link database/API contract has not
     * been supplied yet, so we do NOT invent a schema.
     *
     * This prevents assigning arbitrary Discord users as
     * Minecraft investigators.
     */

    await safelyRespond(
        interaction,
        infoPanel(
            "Investigator assignment requires the N7-Link " +
            "account-link lookup. The N7-Link integration " +
            "will be connected here once its API/schema is available."
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


    if (!investigators.length) {

        await safelyRespond(
            interaction,
            infoPanel(
                `Case #${caseId} has no assigned investigators.`
            )
        );

        return;
    }


    /*
     * Build a String Select menu containing the
     * currently assigned investigators.
     */

    const menu =
        new StringSelectMenuBuilder()
            .setCustomId(
                `case:remove-investigator:${caseId}`
            )
            .setPlaceholder(
                "Select an investigator to remove"
            )
            .setMinValues(1)
            .setMaxValues(1);


    for (const investigator of investigators) {

        menu.addOptions(
            new StringSelectMenuOptionBuilder()
                .setLabel(
                    truncate(
                        investigator.investigatorName ||
                        investigator.playerName ||
                        "Unknown investigator",
                        100
                    )
                )
                .setDescription(
                    truncate(
                        investigator.investigatorUuid ||
                        investigator.playerUuid ||
                        "Unknown UUID",
                        100
                    )
                )
                .setValue(
                    String(
                        investigator.investigatorUuid ||
                        investigator.playerUuid
                    )
                )
        );
    }


    const row =
        new ActionRowBuilder()
            .addComponents(menu);


    await safelyRespond(
        interaction,
        createSelectPanel(
            `Remove Investigator — Case #${caseId}`,
            "Select the investigator you want to remove.",
            [row]
        )
    );
}


/*
 * ============================================================
 * String select handling
 * ============================================================
 */

async function handleStringSelect(
    interaction
) {

    const customId =
        String(interaction.customId || "");


    /*
     * Remove investigator
     */

    if (
        customId.startsWith(
            "case:remove-investigator:"
        )
    ) {

        const parts =
            customId.split(":");

        const caseId =
            parseCaseId(parts[2]);

        await requirePermission(
            interaction,
            "investigators"
        );


        const selected =
            interaction.values?.[0];

        if (!selected) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "No investigator was selected."
                )
            );

            return;
        }


        const actorUuid =
            interaction.user.id;

        const actorName =
            interaction.member?.displayName ||
            interaction.user.globalName ||
            interaction.user.username;


        const result =
            await caseMutationService.removeInvestigator(
                caseId,
                selected,
                actorUuid,
                actorName
            );


        if (!result.removed) {

            await safelyRespond(
                interaction,
                infoPanel(
                    "That investigator is no longer assigned to the case."
                )
            );

            return;
        }


        await safelyRespond(
            interaction,
            successPanel(
                `Investigator **${result.investigatorName}** was removed from Case #${caseId}.`
            )
        );

        return;
    }


    /*
     * Unknown select menu
     */

    await safelyRespond(
        interaction,
        errorPanel(
            "This selection is no longer available."
        )
    );
}


/*
 * ============================================================
 * Role select handling
 * ============================================================
 */

async function handleRoleSelect(
    interaction
) {

    const customId =
        String(interaction.customId || "");


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


    /*
     * Only the server owner or the configured
     * permission-management role may modify roles.
     */

    await requirePermissionManager(
        interaction
    );


    const roleType =
        customId.split(":").pop();


    const selectedRoleId =
        interaction.roles?.first()?.id ||
        null;


    if (!selectedRoleId) {

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
            interaction.guild.id
        );


    applyRoleSetting(
        settings,
        roleType,
        selectedRoleId
    );


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
        permissionRolesPanel(
            updatedSettings
        )
    );
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
     * settings:back
     */

    if (
        interaction.customId ===
        "settings:back"
    ) {

        await requirePermission(
            interaction,
            "settings"
        );

        await safelyRespond(
            interaction,
            settingsPanel()
        );

        return;
    }


    /*
     * settings:permissions
     */

    if (
        interaction.customId ===
        "settings:permissions"
    ) {

        await requirePermissionManager(
            interaction
        );

        await safelyRespond(
            interaction,
            permissionSettingsPanel(
                interaction.guild.id
            )
        );

        return;
    }


    /*
     * Permission mode screen
     */

    if (
        interaction.customId ===
        "settings:permissions:mode"
    ) {

        await requirePermissionManager(
            interaction
        );

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
     * Permission roles screen
     */

    if (
        interaction.customId ===
        "settings:permissions:roles"
    ) {

        await requirePermissionManager(
            interaction
        );

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
     * Refresh permission settings
     */

    if (
        interaction.customId ===
        "settings:permissions:refresh"
    ) {

        await requirePermissionManager(
            interaction
        );

        permissionService.clearCache(
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


    /*
     * Global / per-command mode
     */

    if (
        interaction.customId ===
        "settings:permissions:mode:global"
    ) {

        await updatePermissionMode(
            interaction,
            "global"
        );

        return;
    }


    if (
        interaction.customId ===
        "settings:permissions:mode:per-command"
    ) {

        await updatePermissionMode(
            interaction,
            "per-command"
        );

        return;
    }


    /*
     * Role selection screen
     */

    if (
        customId.startsWith(
            "settings:permissions:role:"
        )
    ) {

        await requirePermissionManager(
            interaction
        );

        const roleType =
            parts[3];

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
     * Clear role
     */

    if (
        customId.startsWith(
            "settings:permissions:clearrole:"
        )
    ) {

        await requirePermissionManager(
            interaction
        );

        const roleType =
            parts[3];

        await clearPermissionRole(
            interaction,
            roleType
        );

        return;
    }


    /*
     * Set-role screen.
     *
     * This displays Discord's native role selector.
     */

    if (
        customId.startsWith(
            "settings:permissions:setrole:"
        )
    ) {

        await requirePermissionManager(
            interaction
        );

        const roleType =
            parts[3];

        await showRoleSelector(
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
 * Permission mode update
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

        throw new Error(
            "Invalid permission mode."
        );
    }


    await requirePermissionManager(
        interaction
    );


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


    applyRoleSetting(
        settings,
        roleType,
        ""
    );


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
        permissionRolesPanel(
            updatedSettings
        )
    );
}


/*
 * ============================================================
 * Native Discord role selector
 * ============================================================
 */

async function showRoleSelector(
    interaction,
    roleType
) {

    const menu =
        new RoleSelectMenuBuilder()
            .setCustomId(
                `settings:permissions:selectrole:${roleType}`
            )
            .setPlaceholder(
                "Select the CaseManager role"
            )
            .setMinValues(1)
            .setMaxValues(1);


    const row =
        new ActionRowBuilder()
            .addComponents(menu);


    await safelyRespond(
        interaction,
        createSelectPanel(
            "Select CaseManager Role",
            `Choose the Discord role for **${formatRoleType(roleType)}**.`,
            [row]
        )
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

    if (!interaction.inGuild()) {

        throw new Error(
            "CaseManager commands can only be used inside a Discord server."
        );
    }


    const allowed =
        await permissionService.hasPermission(
            interaction.member,
            commandName
        );


    if (!allowed) {

        await safelyRespond(
            interaction,
            errorPanel(
                "You do not have permission to perform this CaseManager action."
            )
        );

        throw new InteractionHandledError();
    }
}


/*
 * ============================================================
 * Permission-management enforcement
 * ============================================================
 *
 * Permission configuration is more sensitive than ordinary
 * CaseManager actions.
 *
 * The guild owner can always manage it.
 * Otherwise the configured permissions role is required.
 * ============================================================
 */

async function requirePermissionManager(
    interaction
) {

    if (!interaction.inGuild()) {

        throw new Error(
            "This action can only be used inside a Discord server."
        );
    }


    const allowed =
        await permissionService.canManagePermissions(
            interaction.member
        );


    if (!allowed) {

        await safelyRespond(
            interaction,
            errorPanel(
                "Only the server owner or the configured CaseManager permission manager can change permission settings."
            )
        );

        throw new InteractionHandledError();
    }
}


/*
 * ============================================================
 * Permission role mapping
 * ============================================================
 */

function applyRoleSetting(
    settings,
    roleType,
    roleId
) {

    const normalized =
        String(roleId || "").trim();


    switch (
        String(roleType || "").toLowerCase()
    ) {

        case "global":

            settings.globalRoleId =
                normalized;

            break;


        case "case":

            settings.perCommand.case =
                normalized;

            break;


        case "evidence":

            settings.perCommand.evidence =
                normalized;

            break;


        case "timeline":

            settings.perCommand.timeline =
                normalized;

            break;


        case "investigators":

            settings.perCommand.investigators =
                normalized;

            break;


        case "status":

            settings.perCommand.status =
                normalized;

            break;


        case "settings":

            settings.perCommand.settings =
                normalized;

            break;


        case "permissions":

            settings.perCommand.permissions =
                normalized;

            break;


        default:

            throw new Error(
                `Unknown permission role type: ${roleType}`
            );
    }
}


/*
 * ============================================================
 * Case ID validation
 * ============================================================
 */

function parseCaseId(
    value
) {

    const caseId =
        Number(value);


    if (
        !Number.isInteger(caseId) ||
        caseId <= 0
    ) {

        throw new Error(
            "A valid case ID is required."
        );
    }


    return caseId;
}


/*
 * ============================================================
 * Status validation
 * ============================================================
 */

function normalizeStatus(
    value
) {

    return String(
        value || ""
    )
        .trim()
        .toUpperCase();
}


function formatStatus(
    value
) {

    return String(
        value || "UNKNOWN"
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
 * Role type formatting
 * ============================================================
 */

function formatRoleType(
    roleType
) {

    const labels = {

        global:
            "Global CaseManager",

        case:
            "Case",

        evidence:
            "Evidence",

        timeline:
            "Timeline",

        investigators:
            "Investigators",

        status:
            "Status",

        settings:
            "Settings",

        permissions:
            "Permission Management"
    };


    return (
        labels[
            String(roleType || "")
                .toLowerCase()
        ] ||
        "CaseManager"
    );
}


/*
 * ============================================================
 * Text helpers
 * ============================================================
 */

function truncate(
    value,
    maxLength
) {

    const text =
        String(value || "");


    if (
        text.length <= maxLength
    ) {

        return text;
    }


    if (maxLength <= 3) {

        return text.slice(
            0,
            maxLength
        );
    }


    return (
        text.slice(
            0,
            maxLength - 3
        ) +
        "..."
    );
}


/*
 * ============================================================
 * Components V2 select panel
 * ============================================================
 *
 * We keep this helper local so the interaction controller
 * does not depend on a second UI abstraction.
 * ============================================================
 */

function createSelectPanel(
    title,
    description,
    rows
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
                `## ${title}`
            )
    );


    container.addSeparatorComponents(
        new SeparatorBuilder()
            .setSpacing(
                SeparatorSpacingSize.Small
            )
    );


    container.addTextDisplayComponents(
        new TextDisplayBuilder()
            .setContent(
                description
            )
    );


    for (
        const row of rows || []
    ) {

        container.addActionRowComponents(
            row
        );
    }


    return container;
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

    if (
        interaction.replied ||
        interaction.deferred
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


/*
 * ============================================================
 * Safe interaction edit
 * ============================================================
 */

async function safelyEdit(
    interaction,
    panel
) {

    if (
        interaction.replied ||
        interaction.deferred
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


/*
 * ============================================================
 * Special internal error
 * ============================================================
 *
 * Used when an error has already been displayed to the user.
 * This prevents the outer handler from attempting to send a
 * second response.
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
 * Interaction event listener
 * ============================================================
 */

module.exports = async function interactionCreate(
    interaction
) {

    try {

        await handleInteraction(
            interaction
        );

    } catch (error) {

        /*
         * Permission helpers intentionally throw this after
         * sending their own response.
         */

        if (
            error instanceof
            InteractionHandledError
        ) {

            return;
        }


        console.error(
            "[CaseManager] InteractionCreate failure:",
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