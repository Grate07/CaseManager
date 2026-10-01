const {
    ContainerBuilder,
    TextDisplayBuilder,
    SeparatorBuilder,
    SeparatorSpacingSize,
    ActionRowBuilder,
    ButtonBuilder,
    ButtonStyle
} = require("discord.js");

const permissionService =
    require("../permissions/permissionService");

const {
    ONYX
} = require("./theme");


/*
 * ============================================================
 * CaseManager — Discord Settings UI
 * ============================================================
 *
 * Components V2 permission-management interface.
 *
 * Supports:
 *
 * - Global permission mode
 * - Per-command permission mode
 * - Global role display
 * - Per-command role display
 *
 * Database saving is handled by permissionService.
 * ============================================================
 */


/*
 * ============================================================
 * Settings Panel
 * ============================================================
 */

async function permissionSettingsPanel(
    guildId
) {

    const settings =
        await permissionService.getSettings(
            guildId
        );


    const container =
        new ContainerBuilder();


    /*
     * --------------------------------------------------------
     * Header
     * --------------------------------------------------------
     */

    container.addTextDisplayComponents(
        new TextDisplayBuilder()
            .setContent(
                "# CaseManager Permissions\n" +
                "Manage which Discord roles can use CaseManager."
            )
    );


    container.addSeparatorComponents(
        new SeparatorBuilder()
            .setSpacing(
                SeparatorSpacingSize.Small
            )
    );


    /*
     * --------------------------------------------------------
     * Current mode
     * --------------------------------------------------------
     */

    const modeLabel =
        settings.mode === "per-command"
            ? "Per-command"
            : "Global";


    container.addTextDisplayComponents(
        new TextDisplayBuilder()
            .setContent(
                `**Permission Mode**\n${modeLabel}`
            )
    );


    /*
     * --------------------------------------------------------
     * Global role
     * --------------------------------------------------------
     */

    const globalRole =
        settings.globalRoleId
            ? `<@&${settings.globalRoleId}>`
            : "Not configured";


    container.addTextDisplayComponents(
        new TextDisplayBuilder()
            .setContent(
                `**Global Role**\n${globalRole}`
            )
    );


    /*
     * --------------------------------------------------------
     * Per-command roles
     * --------------------------------------------------------
     */

    if (
        settings.mode === "per-command"
    ) {

        container.addSeparatorComponents(
            new SeparatorBuilder()
                .setSpacing(
                    SeparatorSpacingSize.Small
                )
        );


        container.addTextDisplayComponents(
            new TextDisplayBuilder()
                .setContent(
                    buildCommandRolesText(
                        settings
                    )
                )
        );
    }


    /*
     * --------------------------------------------------------
     * Controls
     * --------------------------------------------------------
     */

    container.addSeparatorComponents(
        new SeparatorBuilder()
            .setSpacing(
                SeparatorSpacingSize.Small
            )
    );


    container.addActionRowComponents(
        createPermissionButtons(
            settings
        )
    );


    return container;
}


/*
 * ============================================================
 * Command role text
 * ============================================================
 */

function buildCommandRolesText(
    settings
) {

    const roles =
        settings.perCommand || {};


    return [
        "**Command Roles**",

        formatRole(
            "Case",
            roles.case
        ),

        formatRole(
            "Evidence",
            roles.evidence
        ),

        formatRole(
            "Timeline",
            roles.timeline
        ),

        formatRole(
            "Investigators",
            roles.investigators
        ),

        formatRole(
            "Status",
            roles.status
        ),

        formatRole(
            "Settings",
            roles.settings
        ),

        formatRole(
            "Permissions",
            roles.permissions
        )
    ].join("\n");
}


/*
 * ============================================================
 * Role formatter
 * ============================================================
 */

function formatRole(
    name,
    roleId
) {

    if (
        roleId &&
        String(roleId).trim() !== ""
    ) {

        return `${name}: <@&${roleId}>`;

    }

    return `${name}: Not configured`;
}
/*
 * ============================================================
 * Permission buttons
 * ============================================================
 */

function createPermissionButtons(
    settings
) {

    const modeButton =
        new ButtonBuilder()
            .setCustomId(
                "settings:permissions:mode"
            )
            .setLabel(
                "Change Mode"
            )
            .setStyle(
                ButtonStyle.Secondary
            );


    const rolesButton =
        new ButtonBuilder()
            .setCustomId(
                "settings:permissions:roles"
            )
            .setLabel(
                "Configure Roles"
            )
            .setStyle(
                ButtonStyle.Primary
            );


    const refreshButton =
        new ButtonBuilder()
            .setCustomId(
                "settings:permissions:refresh"
            )
            .setLabel(
                "Refresh"
            )
            .setStyle(
                ButtonStyle.Secondary
            );


    const backButton =
        new ButtonBuilder()
            .setCustomId(
                "settings:back"
            )
            .setLabel(
                "Back"
            )
            .setStyle(
                ButtonStyle.Secondary
            );


    return new ActionRowBuilder()
        .addComponents(
            modeButton,
            rolesButton,
            refreshButton,
            backButton
        );
}


/*
 * ============================================================
 * Permission mode panel
 * ============================================================
 */

function permissionModePanel(
    settings
) {

    const container =
        new ContainerBuilder();


    container.addTextDisplayComponents(
        new TextDisplayBuilder()
            .setContent(
                "# Permission Mode\n" +
                "Choose how CaseManager Discord permissions are handled."
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
                "**Global Mode**\n" +
                "One Discord role controls all CaseManager commands.\n\n" +

                "**Per-command Mode**\n" +
                "Different Discord roles can control different CaseManager commands."
            )
    );


    container.addSeparatorComponents(
        new SeparatorBuilder()
            .setSpacing(
                SeparatorSpacingSize.Small
            )
    );


    const globalButton =
        new ButtonBuilder()
            .setCustomId(
                "settings:permissions:mode:global"
            )
            .setLabel(
                "Global Mode"
            )
            .setStyle(
                settings.mode === "global"
                    ? ButtonStyle.Primary
                    : ButtonStyle.Secondary
            );


    const perCommandButton =
        new ButtonBuilder()
            .setCustomId(
                "settings:permissions:mode:per-command"
            )
            .setLabel(
                "Per-command Mode"
            )
            .setStyle(
                settings.mode === "per-command"
                    ? ButtonStyle.Primary
                    : ButtonStyle.Secondary
            );


    const backButton =
        new ButtonBuilder()
            .setCustomId(
                "settings:permissions"
            )
            .setLabel(
                "Back"
            )
            .setStyle(
                ButtonStyle.Secondary
            );


    container.addActionRowComponents(
        new ActionRowBuilder()
            .addComponents(
                globalButton,
                perCommandButton,
                backButton
            )
    );


    return container;
}
/*
 * ============================================================
 * Role configuration panel
 * ============================================================
 *
 * This panel displays the roles currently stored in the
 * database.
 *
 * Actual role selection/editing will use Discord components
 * in the interaction handler.
 * ============================================================
 */

function permissionRolesPanel(
    settings
) {

    const container =
        new ContainerBuilder();


    container.addTextDisplayComponents(
        new TextDisplayBuilder()
            .setContent(
                "# CaseManager Roles\n" +
                "Choose which CaseManager permission role you want to configure."
            )
    );


    container.addSeparatorComponents(
        new SeparatorBuilder()
            .setSpacing(
                SeparatorSpacingSize.Small
            )
    );


    /*
     * Global role.
     */

    const globalButton =
        new ButtonBuilder()
            .setCustomId(
                "settings:permissions:role:global"
            )
            .setLabel(
                "Global Role"
            )
            .setStyle(
                ButtonStyle.Primary
            );


    /*
     * Command roles.
     */

    const caseButton =
        new ButtonBuilder()
            .setCustomId(
                "settings:permissions:role:case"
            )
            .setLabel(
                "Case"
            )
            .setStyle(
                ButtonStyle.Secondary
            );


    const evidenceButton =
        new ButtonBuilder()
            .setCustomId(
                "settings:permissions:role:evidence"
            )
            .setLabel(
                "Evidence"
            )
            .setStyle(
                ButtonStyle.Secondary
            );


    const timelineButton =
        new ButtonBuilder()
            .setCustomId(
                "settings:permissions:role:timeline"
            )
            .setLabel(
                "Timeline"
            )
            .setStyle(
                ButtonStyle.Secondary
            );


    const investigatorsButton =
        new ButtonBuilder()
            .setCustomId(
                "settings:permissions:role:investigators"
            )
            .setLabel(
                "Investigators"
            )
            .setStyle(
                ButtonStyle.Secondary
            );


    const statusButton =
        new ButtonBuilder()
            .setCustomId(
                "settings:permissions:role:status"
            )
            .setLabel(
                "Status"
            )
            .setStyle(
                ButtonStyle.Secondary
            );


    const settingsButton =
        new ButtonBuilder()
            .setCustomId(
                "settings:permissions:role:settings"
            )
            .setLabel(
                "Settings"
            )
            .setStyle(
                ButtonStyle.Secondary
            );


    const permissionsButton =
        new ButtonBuilder()
            .setCustomId(
                "settings:permissions:role:permissions"
            )
            .setLabel(
                "Permissions"
            )
            .setStyle(
                ButtonStyle.Secondary
            );


    /*
     * Discord ActionRows support a maximum of five buttons.
     */

    container.addActionRowComponents(
        new ActionRowBuilder()
            .addComponents(
                globalButton,
                caseButton,
                evidenceButton,
                timelineButton,
                investigatorsButton
            )
    );


    container.addActionRowComponents(
        new ActionRowBuilder()
            .addComponents(
                statusButton,
                settingsButton,
                permissionsButton,
                new ButtonBuilder()
                    .setCustomId(
                        "settings:permissions"
                    )
                    .setLabel(
                        "Back"
                    )
                    .setStyle(
                        ButtonStyle.Secondary
                    )
            )
    );


    return container;
}
/*
 * ============================================================
 * Role selection information panel
 * ============================================================
 */

function roleSelectionPanel(
    roleType,
    settings
) {

    const container =
        new ContainerBuilder();


    const labels = {

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


    const label =
        labels[roleType] ||
        "CaseManager Role";


    container.addTextDisplayComponents(
        new TextDisplayBuilder()
            .setContent(
                `# ${label}\n` +
                "Use the role configuration controls to assign a Discord role to this permission."
            )
    );


    container.addSeparatorComponents(
        new SeparatorBuilder()
            .setSpacing(
                SeparatorSpacingSize.Small
            )
    );


    const currentRole =
        getRoleId(
            roleType,
            settings
        );


    container.addTextDisplayComponents(
        new TextDisplayBuilder()
            .setContent(
                currentRole
                    ? `**Current Role:** <@&${currentRole}>`
                    : "**Current Role:** Not configured"
            )
    );


    container.addSeparatorComponents(
        new SeparatorBuilder()
            .setSpacing(
                SeparatorSpacingSize.Small
            )
    );


    container.addActionRowComponents(
        new ActionRowBuilder()
            .addComponents(

                new ButtonBuilder()
                    .setCustomId(
                        `settings:permissions:setrole:${roleType}`
                    )
                    .setLabel(
                        "Set Role"
                    )
                    .setStyle(
                        ButtonStyle.Primary
                    ),

                new ButtonBuilder()
                    .setCustomId(
                        `settings:permissions:clearrole:${roleType}`
                    )
                    .setLabel(
                        "Clear Role"
                    )
                    .setStyle(
                        ButtonStyle.Danger
                    ),

                new ButtonBuilder()
                    .setCustomId(
                        "settings:permissions:roles"
                    )
                    .setLabel(
                        "Back"
                    )
                    .setStyle(
                        ButtonStyle.Secondary
                    )
            )
    );


    return container;
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

        return settings.globalRoleId || "";

    }


    return (
        settings.perCommand?.[roleType] ||
        ""
    );
}


/*
 * ============================================================
 * Save helper
 * ============================================================
 *
 * This is intentionally exported so the interaction handler
 * can save permission settings through the existing
 * permissionService.
 * ============================================================
 */

async function savePermissionSettings(
    guildId,
    settings,
    updatedBy
) {

    await permissionService.saveSettings(
        guildId,
        settings,
        updatedBy
    );


    return permissionService.getSettings(
        guildId
    );
}


/*
 * ============================================================
 * Exports
 * ============================================================
 */

module.exports = {

    permissionSettingsPanel,

    permissionModePanel,

    permissionRolesPanel,

    roleSelectionPanel,

    savePermissionSettings

};