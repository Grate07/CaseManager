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

const discordEvidenceService =
    require("../services/discordEvidenceService");

const {
    evidenceUploadModal
} = require("../ui/evidenceModal");

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
 *  • Buttons
 *  • String select menus
 *  • Role select menus
 *  • Modal submissions
 *  • Evidence uploads
 *  • Case status changes
 *  • Investigator controls
 *  • Permission settings
 *
 * Components V2 is used for normal bot responses.
 * ============================================================
 */


/*
 * ============================================================
 * Main interaction router
 * ============================================================
 */

async function handleInteraction(interaction) {

    if (
        interaction.isChatInputCommand()
    ) {

        await handleChatInputCommand(
            interaction
        );

        return;
    }


    if (
        interaction.isModalSubmit()
    ) {

        await handleModalSubmit(
            interaction
        );

        return;
    }


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

        /*
         * case:evidence:add:<caseId>
         */

        if (
            parts[2] === "add"
        ) {

            const caseId =
                parseCaseId(
                    parts[3]
                );


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


        /*
         * case:evidence:<caseId>
         */

        const caseId =
            parseCaseId(
                parts[2]
            );


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
            parseCaseId(
                parts[2]
            );


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
            parseCaseId(
                parts[2]
            );


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
            parseCaseId(
                parts[2]
            );


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
            parseCaseId(
                parts[2]
            );


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
 * Case display
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
 * Evidence display
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
 * Timeline display
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
 * Investigator display
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
 * Begin evidence upload
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
     * Discord modals are sent directly to the user.
     *
     * The modal contains the native Discord File Upload
     * component.
     */

    await interaction.showModal(
        evidenceUploadModal(
            caseId
        )
    );
}
/*
 * ============================================================
 * Modal submissions
 * ============================================================
 */

async function handleModalSubmit(
    interaction
) {

    const customId =
        String(
            interaction.customId || ""
        );


    /*
     * --------------------------------------------------------
     * Evidence upload
     * --------------------------------------------------------
     */

    if (
        customId.startsWith(
            "case:evidence:upload:"
        )
    ) {

        await handleEvidenceUploadModal(
            interaction
        );

        return;
    }


    await safelyRespond(
        interaction,
        errorPanel(
            "This form is no longer available."
        )
    );
}


/*
 * ============================================================
 * Evidence upload modal
 * ============================================================
 */

async function handleEvidenceUploadModal(
    interaction
) {

    const parts =
        interaction.customId.split(":");


    const caseId =
        parseCaseId(
            parts[3]
        );


    await requirePermission(
        interaction,
        "evidence"
    );


    /*
     * Acknowledge the modal before processing files.
     *
     * Storage uploads can take some time, so we defer.
     */

    await interaction.deferReply({
        flags:
            MessageFlags.Ephemeral
    });


    const caseData =
        await caseMutationService.getCase(
            caseId
        );


    if (!caseData) {

        await safelyEdit(
            interaction,
            errorPanel(
                `Case #${caseId} was not found.`
            )
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * Retrieve uploaded files
     * --------------------------------------------------------
     */

    const uploadedFiles =
        interaction.fields.getUploadedFiles(
            "evidence_files"
        );


    const attachments =
        Array.from(
            uploadedFiles.values()
        );


    if (
        attachments.length === 0
    ) {

        await safelyEdit(
            interaction,
            errorPanel(
                "No evidence files were uploaded."
            )
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * Description
     * --------------------------------------------------------
     */

    let description = "";


    try {

        description =
            interaction.fields.getTextInputValue(
                "evidence_description"
            ) || "";

    } catch {

        description = "";
    }


    /*
     * --------------------------------------------------------
     * Upload
     * --------------------------------------------------------
     */

    const actorId =
        interaction.user?.id ||
        null;


    const actorName =
        interaction.user?.globalName ||
        interaction.user?.username ||
        "Discord User";


    let evidence;


    try {

        evidence =
            await discordEvidenceService.uploadEvidence({
                caseId,

                attachments,

                description,

                actorId,

                actorName
            });

    } catch (error) {

        console.error(
            "[CaseManager] Discord evidence upload failed:",
            error
        );


        await safelyEdit(
            interaction,
            errorPanel(
                config.bot.debug
                    ? `Evidence upload failed: ${error.message}`
                    : `Evidence upload failed: ${error.message}`
            )
        );

        return;
    }


    /*
     * --------------------------------------------------------
     * Reload evidence
     * --------------------------------------------------------
     */

    const updatedEvidence =
        await caseMutationService.getEvidence(
            caseId
        );


    const uploadedCount =
        Array.isArray(evidence)
            ? evidence.length
            : 0;


    /*
     * --------------------------------------------------------
     * Return to evidence panel
     * --------------------------------------------------------
     */

    const panel =
        caseEvidencePanel(
            caseData,
            updatedEvidence
        );


    /*
     * Add a short success message above the panel.
     *
     * Components V2 doesn't require an EmbedBuilder.
     */

    if (
        panel instanceof ContainerBuilder
    ) {

        const successText =
            new TextDisplayBuilder()
                .setContent(
                    `### Evidence Added\n` +
                    `${uploadedCount} evidence file(s) were successfully added to Case #${caseId}.`
                );


        const successSeparator =
            new SeparatorBuilder()
                .setSpacing(
                    SeparatorSpacingSize.Small
                );


        panel
            .addTextDisplayComponents(
                successText
            )
            .addSeparatorComponents(
                successSeparator
            );
    }


    await safelyEdit(
        interaction,
        panel
    );
}


/*
 * ============================================================
 * Case status
 * ============================================================
 */

async function changeCaseStatus(
    interaction,
    caseId,
    status
) {

    const normalizedStatus =
        normalizeStatus(
            status
        );


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
        interaction.user?.id ||
        null;


    const actorName =
        interaction.user?.globalName ||
        interaction.user?.username ||
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


    await safelyRespond(
        interaction,
        caseStatusPanel(
            updatedCase
        )
    );
}


/*
 * ============================================================
 * Investigator assignment
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
     * N7-Link integration will resolve:
     *
     * Discord user → linked Minecraft UUID
     *
     * We intentionally do not guess its schema here.
     */

    await safelyRespond(
        interaction,
        infoPanel(
            "Investigator assignment is waiting for the N7-Link integration. No investigator was assigned."
        )
    );
}


/*
 * ============================================================
 * Investigator removal
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
            .setMinValues(1)
            .setMaxValues(1)
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
                new TextDisplayBuilder()
                    .setContent(
                        `## Remove Investigator\n` +
                        `Select an investigator assigned to **Case #${caseId}**.`
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
            interaction.user?.id ||
            null;


        const actorName =
            interaction.user?.globalName ||
            interaction.user?.username ||
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
 * Discord role selector
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
     * --------------------------------------------------------
     * Main permission settings
     * --------------------------------------------------------
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
     * --------------------------------------------------------
     * Permission mode
     * --------------------------------------------------------
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
     * --------------------------------------------------------
     * Permission roles
     * --------------------------------------------------------
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
     * --------------------------------------------------------
     * Refresh
     * --------------------------------------------------------
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
     * --------------------------------------------------------
     * Back
     * --------------------------------------------------------
     */

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


    /*
     * --------------------------------------------------------
     * Global mode
     * --------------------------------------------------------
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
     * --------------------------------------------------------
     * Per-command mode
     * --------------------------------------------------------
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
     * --------------------------------------------------------
     * Set role
     * --------------------------------------------------------
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
     * --------------------------------------------------------
     * Clear role
     * --------------------------------------------------------
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
 * Update permission mode
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
 * Show role selector
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
 * Permission manager enforcement
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
 * Utility helpers
 * ============================================================
 */


/*
 * ------------------------------------------------------------
 * Parse case ID
 * ------------------------------------------------------------
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
 * ------------------------------------------------------------
 * Normalize case status
 * ------------------------------------------------------------
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
 * ------------------------------------------------------------
 * Format status
 * ------------------------------------------------------------
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
 * ------------------------------------------------------------
 * Format role type
 * ------------------------------------------------------------
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
 * ------------------------------------------------------------
 * Truncate text
 * ------------------------------------------------------------
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
 * Generic select panel
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
 * Safe interaction response
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
            interaction.replied ||
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
         * Discord:
         *
         * 10062 = Unknown interaction
         * 40060 = Interaction already acknowledged
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
 * Safe interaction edit
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
 * Internal interaction-handled exception
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
 * Main exported Discord event
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