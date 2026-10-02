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


/*
 * ============================================================
 * CaseManager — Discord Settings UI
 * ============================================================
 *
 * Components V2 only.
 *
 * Supports:
 *
 *  • Global permission mode
 *  • Per-command permission mode
 *  • Global role
 *  • Per-command roles
 *  • Role selection
 *  • Role clearing
 *
 * Actual persistence is handled by permissionService.
 * ============================================================
 */


/*
 * ============================================================
 * Permission settings panel
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


    container.addSeparatorComponents(
        new SeparatorBuilder()
            .setSpacing(
                SeparatorSpacingSize.Small
            )
    );


    container.addActionRowComponents(
        createPermissionButtons()
    );


    return container;
}


/*
 * ============================================================
 * Command role display
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
 * Main permission buttons
 * ============================================================
 */

function createPermissionButtons() {

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
 * Permission roles panel
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
     * Show current role summary.
     */

    container.addTextDisplayComponents(
        new TextDisplayBuilder()
            .setContent(
                buildRoleSummary(settings)
            )
    );


    container.addSeparatorComponents(
        new SeparatorBuilder()
            .setSpacing(
                SeparatorSpacingSize.Small
            )
    );


    /*
     * First row.
     */

    container.addActionRowComponents(
        new ActionRowBuilder()
            .addComponents(

                createRoleButton(
                    "global",
                    "Global",
                    ButtonStyle.Primary
                ),

                createRoleButton(
                    "case",
                    "Case"
                ),

                createRoleButton(
                    "evidence",
                    "Evidence"
                ),

                createRoleButton(
                    "timeline",
                    "Timeline"
                ),

                createRoleButton(
                    "investigators",
                    "Investigators"
                )
            )
    );


    /*
     * Second row.
     */

    container.addActionRowComponents(
        new ActionRowBuilder()
            .addComponents(

                createRoleButton(
                    "status",
                    "Status"
                ),

                createRoleButton(
                    "settings",
                    "Settings"
                ),

                createRoleButton(
                    "permissions",
                    "Permissions"
                ),

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
 * Role button
 * ============================================================
 */

function createRoleButton(
    roleType,
    label,
    style = ButtonStyle.Secondary
) {

    return new ButtonBuilder()
        .setCustomId(
            `settings:permissions:role:${roleType}`
        )
        .setLabel(
            label
        )
        .setStyle(
            style
        );
}


/*
 * ============================================================
 * Role summary
 * ============================================================
 */

function buildRoleSummary(
    settings
) {

    const global =
        settings.globalRoleId
            ? `<@&${settings.globalRoleId}>`
            : "Not configured";


    const roles =
        settings.perCommand || {};


    return [
        `**Global:** ${global}`,
        `**Case:** ${formatRoleValue(roles.case)}`,
        `**Evidence:** ${formatRoleValue(roles.evidence)}`,
        `**Timeline:** ${formatRoleValue(roles.timeline)}`,
        `**Investigators:** ${formatRoleValue(roles.investigators)}`,
        `**Status:** ${formatRoleValue(roles.status)}`,
        `**Settings:** ${formatRoleValue(roles.settings)}`,
        `**Permissions:** ${formatRoleValue(roles.permissions)}`
    ].join("\n");
}


function formatRoleValue(
    roleId
) {

    return roleId &&
        String(roleId).trim() !== ""

        ? `<@&${roleId}>`

        : "Not configured";
}


/*
 * ============================================================
 * Individual role configuration panel
 * ============================================================
 */

function roleSelectionPanel(
    roleType,
    settings
) {

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
            "Permission Management Role"
    };


    const label =
        labels[
            String(roleType || "")
                .toLowerCase()
        ] ||
        "CaseManager Role";


    const currentRole =
        getRoleId(
            roleType,
            settings
        );


    const container =
        new ContainerBuilder();


    container.addTextDisplayComponents(
        new TextDisplayBuilder()
            .setContent(
                `# ${label}\n` +
                "Configure the Discord role used for this permission."
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
                        "Select Role"
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
        String(roleType || "")
            .toLowerCase() === "global"
    ) {

        return settings.globalRoleId || "";
    }


    return (
        settings.perCommand?.[
            String(roleType || "")
                .toLowerCase()
        ] ||
        ""
    );
}


/*
 * ============================================================
 * Save permission settings
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