const {
    MessageFlags,
    ActionRowBuilder,
    RoleSelectMenuBuilder,
    StringSelectMenuBuilder,
    ContainerBuilder,
    TextDisplayBuilder,
    SeparatorBuilder,
    SeparatorSpacingSize
} = require("discord.js");

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
    errorPanel,
    successPanel,
    infoPanel,
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
 * CaseManager Interaction Handler
 * ============================================================
 */

module.exports = async function interactionCreate(interaction) {

    try {

        if (interaction.isChatInputCommand()) {
            await handleCommand(interaction);
            return;
        }

        if (interaction.isButton()) {
            await handleButton(interaction);
            return;
        }

        if (interaction.isStringSelectMenu()) {
            await handleStringSelect(interaction);
            return;
        }

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

async function handleCommand(interaction) {

    if (!interaction.inGuild()) {

        await safelyRespond(
            interaction,
            errorPanel(
                "CaseManager commands can only be used inside a Discord server."
            )
        );

        return;
    }

    const command =
        interaction.client.commands?.get(
            interaction.commandName
        );

    if (!command) {

        await safelyRespond(
            interaction,
            errorPanel(
                "This command is not available."
            )
        );

        return;
    }

    await command.execute(interaction);
}


/*
 * ============================================================
 * Button router
 * ============================================================
 */

async function handleButton(interaction) {

    if (!interaction.inGuild()) {

        await safelyRespond(
            interaction,
            errorPanel(
                "CaseManager controls can only be used inside a Discord server."
            )
        );

        return;
    }

    const customId =
        interaction.customId;

    if (customId.startsWith("case:")) {
        await handleCaseButton(interaction);
        return;
    }

    if (customId.startsWith("settings:")) {
        await handleSettingsButton(interaction);
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

async function handleCaseButton(interaction) {

    const parts =
        interaction.customId.split(":");

    const action =
        parts[1];


    /*
     * --------------------------------------------------------
     * VIEW
     * case:view:<id>
     * --------------------------------------------------------
     */

    if (action === "view") {

        if (
            !(await checkPermission(
                interaction,
                "case"
            ))
        ) {
            return;
        }

        const caseId =
            parseCaseId(parts[2]);

        if (!caseId) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "Invalid case ID."
                )
            );

            return;
        }

        await deferComponents(interaction);

        await showCase(
            interaction,
            caseId
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * EVIDENCE
     * case:evidence:<id>
     * case:evidence:add:<id>
     * --------------------------------------------------------
     */

    if (action === "evidence") {

        if (
            !(await checkPermission(
                interaction,
                "evidence"
            ))
        ) {
            return;
        }

        if (parts[2] === "add") {

            const caseId =
                parseCaseId(parts[3]);

            if (!caseId) {

                await safelyRespond(
                    interaction,
                    errorPanel(
                        "Invalid case ID."
                    )
                );

                return;
            }

            await deferComponents(interaction);

            await handleAddEvidence(
                interaction,
                caseId
            );

            return;
        }

        const caseId =
            parseCaseId(parts[2]);

        if (!caseId) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "Invalid case ID."
                )
            );

            return;
        }

        await deferComponents(interaction);

        await showEvidence(
            interaction,
            caseId
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * TIMELINE
     * case:timeline:<id>
     * --------------------------------------------------------
     */

    if (action === "timeline") {

        if (
            !(await checkPermission(
                interaction,
                "timeline"
            ))
        ) {
            return;
        }

        const caseId =
            parseCaseId(parts[2]);

        if (!caseId) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "Invalid case ID."
                )
            );

            return;
        }

        await deferComponents(interaction);

        await showTimeline(
            interaction,
            caseId
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * INVESTIGATORS
     * case:investigators:<id>
     * --------------------------------------------------------
     */

    if (action === "investigators") {

        if (
            !(await checkPermission(
                interaction,
                "investigators"
            ))
        ) {
            return;
        }

        const caseId =
            parseCaseId(parts[2]);

        if (!caseId) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "Invalid case ID."
                )
            );

            return;
        }

        await deferComponents(interaction);

        await showInvestigators(
            interaction,
            caseId
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * ASSIGN
     * case:assign:<id>
     * --------------------------------------------------------
     */

    if (action === "assign") {

        if (
            !(await checkPermission(
                interaction,
                "investigators"
            ))
        ) {
            return;
        }

        const caseId =
            parseCaseId(parts[2]);

        if (!caseId) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "Invalid case ID."
                )
            );

            return;
        }

        await deferComponents(interaction);

        await handleAssignInvestigator(
            interaction,
            caseId
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * UNASSIGN
     * case:unassign:<id>
     * --------------------------------------------------------
     */

    if (action === "unassign") {

        if (
            !(await checkPermission(
                interaction,
                "investigators"
            ))
        ) {
            return;
        }

        const caseId =
            parseCaseId(parts[2]);

        if (!caseId) {

            await safelyRespond(
                interaction,
                errorPanel(
                    "Invalid case ID."
                )
            );

            return;
        }

        await deferComponents(interaction);

        await handleUnassignInvestigator(
            interaction,
            caseId
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * STATUS
     * case:status:<STATUS>:<id>
     * --------------------------------------------------------
     */

    if (action === "status") {

        if (
            !(await checkPermission(
                interaction,
                "status"
            ))
        ) {
            return;
        }

        const status =
            String(parts[2] || "")
                .trim()
                .toUpperCase();

        const caseId =
            parseCaseId(parts[3]);

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

        await deferComponents(interaction);

        await handleStatusChange(
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
 * Case view
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
 * Evidence
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

    await safelyRespond(
        interaction,
        infoPanel(
            "Add Evidence",
            `Case #${caseId} is ready for the evidence upload flow.`
        )
    );
}


/*
 * ============================================================
 * Timeline
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
 * Investigators
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
 * Status mutation
 * ============================================================
 */

async function handleStatusChange(
    interaction,
    caseId,
    status
) {

    try {

        const result =
            await caseMutationService.setCaseStatus(
                caseId,
                status,
                interaction.user.id,
                getActorName(interaction)
            );

        if (!result.changed) {

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

    } catch (error) {

        console.error(
            "[CaseManager] Status mutation error:",
            error
        );

        await safelyRespond(
            interaction,
            errorPanel(
                error.message ||
                "Failed to change the case status."
            )
        );
    }
}


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
     * N7-Link integration will resolve the Discord user to
     * the linked Minecraft UUID/name.
     *
     * We intentionally do not invent the N7-Link schema.
     */

    await safelyRespond(
        interaction,
        infoPanel(
            "Assign Investigator",
            `Case #${caseId} is ready for investigator assignment. The N7-Link account resolver needs to be connected before a Discord member can be converted into a Minecraft investigator UUID.`
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

    if (investigators.length === 0) {

        await safelyRespond(
            interaction,
            infoPanel(
                "Investigators",
                `Case #${caseId} currently has no assigned investigators.`
            )
        );

        return;
    }

    const options =
        investigators
            .slice(0, 25)
            .map(investigator => ({

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

            }));

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
            .addOptions(options);

    const row =
        new ActionRowBuilder()
            .addComponents(menu);

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

async function handleStringSelect(
    interaction
) {

    const customId =
        interaction.customId;

    if (
        !customId.startsWith(
            "case:remove-investigator:"
        )
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "Unknown CaseManager selection."
            )
        );

        return;
    }

    if (
        !(await checkPermission(
            interaction,
            "investigators"
        ))
    ) {
        return;
    }

    const parts =
        customId.split(":");

    const caseId =
        parseCaseId(parts[2]);

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
        interaction.values?.[0];

    if (!investigatorUuid) {

        await safelyRespond(
            interaction,
            errorPanel(
                "No investigator was selected."
            )
        );

        return;
    }

    await deferComponents(interaction);

    try {

        const result =
            await caseMutationService.removeInvestigator(
                caseId,
                investigatorUuid,
                interaction.user.id,
                getActorName(interaction)
            );

        if (!result.removed) {

            await safelyRespond(
                interaction,
                infoPanel(
                    "Investigator",
                    "That investigator is no longer assigned to this case."
                )
            );

            return;
        }

        const updatedCase =
            await caseMutationService.getCase(
                caseId
            );

        const investigators =
            await caseMutationService.getInvestigators(
                caseId
            );

        await safelyRespond(
            interaction,
            caseStaffPanel(
                updatedCase,
                investigators
            )
        );

    } catch (error) {

        console.error(
            "[CaseManager] Investigator removal error:",
            error
        );

        await safelyRespond(
            interaction,
            errorPanel(
                error.message ||
                "Failed to remove the investigator."
            )
        );
    }
}


/*
 * ============================================================
 * Settings router
 * ============================================================
 */

async function handleSettingsButton(
    interaction
) {

    const customId =
        interaction.customId;


    /*
     * Permission-management settings have their own
     * permission check.
     */

    if (
        customId === "settings:permissions" ||
        customId.startsWith("settings:permissions:")
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

        await deferComponents(interaction);

        await handlePermissionSettings(
            interaction
        );

        return;
    }


    /*
     * Normal settings use the settings permission.
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

    await deferComponents(interaction);


    if (
        customId === "settings:back"
    ) {

        await safelyRespond(
            interaction,
            settingsPanel()
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
 * Permission settings
 * ============================================================
 */

async function handlePermissionSettings(
    interaction
) {

    const customId =
        interaction.customId;


    if (
        customId === "settings:permissions" ||
        customId === "settings:permissions:refresh"
    ) {

        await safelyRespond(
            interaction,
            permissionSettingsPanel(
                interaction.guild.id
            )
        );

        return;
    }


    if (
        customId === "settings:permissions:mode"
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


    if (
        customId === "settings:permissions:roles"
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
            !isValidRoleType(roleType)
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
     * settings:permissions:setrole:<type>
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
     * settings:permissions:clearrole:<type>
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
        !isValidRoleType(roleType)
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
            .addComponents(selector);

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

    if (!interaction.inGuild()) {

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
                "Unknown permission role selector."
            )
        );

        return;
    }


    const roleType =
        parts[3];


    if (
        !isValidRoleType(roleType)
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
        interaction.values?.[0];


    if (!roleId) {

        await safelyRespond(
            interaction,
            errorPanel(
                "No role was selected."
            )
        );

        return;
    }


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


    if (
        role.id === interaction.guild.id
    ) {

        await safelyRespond(
            interaction,
            errorPanel(
                "The @everyone role cannot be used as a CaseManager permission role."
            )
        );

        return;
    }


    if (role.managed) {

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


    if (roleType === "global") {

        settings.globalRoleId =
            role.id;

    } else {

        if (!settings.perCommand) {
            settings.perCommand = {};
        }

        settings.perCommand[roleType] =
            role.id;
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
        !isValidRoleType(roleType)
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


    if (roleType === "global") {

        settings.globalRoleId =
            "";

    } else {

        if (!settings.perCommand) {
            settings.perCommand = {};
        }

        settings.perCommand[roleType] =
            "";
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
 * Permission check
 * ============================================================
 */

async function checkPermission(
    interaction,
    commandName
) {

    const allowed =
        await permissionService.hasPermission(
            interaction.member,
            commandName
        );


    if (allowed) {
        return true;
    }


    await safelyRespond(
        interaction,
        errorPanel(
            `You do not have permission to use the CaseManager ${commandName} controls.`
        )
    );


    return false;
}


/*
 * ============================================================
 * Helpers
 * ============================================================
 */

function parseCaseId(value) {

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


function getActorName(interaction) {

    return (
        interaction.member?.displayName ||
        interaction.user?.globalName ||
        interaction.user?.username ||
        "Unknown Discord User"
    );
}


function formatStatus(status) {

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


function isValidRoleType(roleType) {

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
        settings.perCommand?.[roleType] ||
        ""
    );
}


/*
 * ============================================================
 * Investigator removal panel
 * ============================================================
 */

function createInvestigatorRemovalPanel(
    caseData,
    row
) {

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
 * Role selector panel
 * ============================================================
 */

function createRoleSelectorPanel(
    roleType,
    currentRole,
    selectorRow
) {

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


    const title =
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
                `# ${title}\n` +
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
 * Components V2 defer
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
 * Safe Components V2 response
 * ============================================================
 */

async function safelyRespond(
    interaction,
    panel
) {

    if (!panel) {

        panel =
            errorPanel(
                "No response panel was generated."
            );
    }


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