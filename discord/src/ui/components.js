const {
    ContainerBuilder,
    TextDisplayBuilder,
    SeparatorBuilder,
    SeparatorSpacingSize,
    ActionRowBuilder,
    ButtonBuilder,
    ButtonStyle
} = require("discord.js");

const {
    ONYX,
    statusColor,
    statusLabel
} = require("./theme");

/*
 * ============================================================
 * CaseManager — Discord Components V2 UI
 * ============================================================
 *
 * This file contains reusable Components V2 panels.
 *
 * IMPORTANT:
 * - Do NOT use EmbedBuilder here.
 * - These panels are designed to be sent with
 *   MessageFlags.IsComponentsV2.
 * - Onyx is the visual identity of CaseManager.
 * ============================================================
 */


/**
 * Creates a basic Onyx container.
 */
function createContainer(options = {}) {

    const container =
        new ContainerBuilder();

    if (options.accentColor !== undefined) {

        container.setAccentColor(
            options.accentColor
        );
    } else {

        container.setAccentColor(
            ONYX.accent
        );
    }

    return container;
}


/**
 * Adds a text block to a container.
 */
function addText(
    container,
    text
) {

    container.addTextDisplayComponents(
        new TextDisplayBuilder()
            .setContent(text)
    );

    return container;
}


/**
 * Adds a separator.
 */
function addSeparator(
    container,
    spacing = SeparatorSpacingSize.Small
) {

    container.addSeparatorComponents(
        new SeparatorBuilder()
            .setSpacing(spacing)
    );

    return container;
}


/**
 * Creates a standard button.
 */
function createButton(
    customId,
    label,
    style = ButtonStyle.Secondary,
    disabled = false
) {

    return new ButtonBuilder()
        .setCustomId(customId)
        .setLabel(label)
        .setStyle(style)
        .setDisabled(disabled);
}


/**
 * Adds a button row.
 */
function addButtonRow(
    container,
    buttons
) {

    const row =
        new ActionRowBuilder()
            .addComponents(buttons);

    container.addActionRowComponents(row);

    return container;
}


/**
 * Main Case panel.
 */
function casePanel(caseData) {

    const container =
        createContainer({
            accentColor:
                statusColor(caseData.status)
        });

    addText(
        container,
        `# Case #${caseData.id}\n` +
        `**Status:** ${statusLabel(caseData.status)}`
    );

    addSeparator(container);

    addText(
        container,
        `**Target**\n` +
        `${caseData.targetName || "Unknown"}\n` +
        `${caseData.targetUuid || "UUID unavailable"}`
    );

    addSeparator(container);

    addText(
        container,
        `**Created By**\n` +
        `${caseData.creatorName || "Unknown"}\n` +
        `${caseData.creatorUuid || "UUID unavailable"}`
    );

    addSeparator(container);

    addText(
        container,
        `**Reason**\n` +
        `${caseData.reason || "No reason provided"}`
    );

    addSeparator(container);

    addText(
        container,
        `**Created**\n` +
        `${formatDate(caseData.createdAt)}\n\n` +
        `**Updated**\n` +
        `${formatDate(caseData.updatedAt)}`
    );

    addSeparator(container);

    addButtonRow(
        container,
        [
            createButton(
                `case:view:${caseData.id}`,
                "View",
                ButtonStyle.Secondary
            ),

            createButton(
                `case:evidence:${caseData.id}`,
                "Evidence",
                ButtonStyle.Secondary
            ),

            createButton(
                `case:timeline:${caseData.id}`,
                "Timeline",
                ButtonStyle.Secondary
            ),

            createButton(
                `case:investigators:${caseData.id}`,
                "Staff",
                ButtonStyle.Secondary
            )
        ]
    );

    return container;
}


/**
 * Case evidence panel.
 */
function caseEvidencePanel(
    caseData,
    evidence = []
) {

    const container =
        createContainer({
            accentColor: ONYX.accent
        });

    addText(
        container,
        `# Evidence — Case #${caseData.id}`
    );

    addSeparator(container);

    if (!evidence.length) {

        addText(
            container,
            "No evidence has been added to this case."
        );

    } else {

        const lines =
            evidence.map(
                (item, index) => {

                    const type =
                        String(
                            item.type || "OTHER"
                        )
                        .replaceAll(
                            "_",
                            " "
                        );

                    return (
                        `**${index + 1}. ${type}**\n` +
                        `${item.content || "No content"}\n` +
                        `Added by: ${item.addedByName || "Unknown"}`
                    );
                }
            );

        addText(
            container,
            lines.join("\n\n")
        );
    }

    addSeparator(container);

    addButtonRow(
        container,
        [
            createButton(
                `case:evidence:add:${caseData.id}`,
                "Add Evidence",
                ButtonStyle.Primary
            ),

            createButton(
                `case:view:${caseData.id}`,
                "Back",
                ButtonStyle.Secondary
            )
        ]
    );

    return container;
}


/**
 * Case timeline panel.
 */
function caseTimelinePanel(
    caseData,
    timeline = []
) {

    const container =
        createContainer({
            accentColor: ONYX.accent
        });

    addText(
        container,
        `# Timeline — Case #${caseData.id}`
    );

    addSeparator(container);

    if (!timeline.length) {

        addText(
            container,
            "No timeline events have been recorded."
        );

    } else {

        const lines =
            timeline.map(
                (event, index) => {

                    return (
                        `**${index + 1}. ${formatTimelineAction(event.action)}**\n` +
                        `${event.description || "No description"}\n` +
                        `By: ${event.actorName || "Unknown"} • ` +
                        `${formatDate(event.createdAt)}`
                    );
                }
            );

        addText(
            container,
            lines.join("\n\n")
        );
    }

    addSeparator(container);

    addButtonRow(
        container,
        [
            createButton(
                `case:view:${caseData.id}`,
                "Back",
                ButtonStyle.Secondary
            )
        ]
    );

    return container;
}


/**
 * Case investigators/staff panel.
 */
function caseStaffPanel(
    caseData,
    investigators = []
) {

    const container =
        createContainer({
            accentColor: ONYX.accent
        });

    addText(
        container,
        `# Investigators — Case #${caseData.id}`
    );

    addSeparator(container);

    if (!investigators.length) {

        addText(
            container,
            "No investigators are currently assigned."
        );

    } else {

        const lines =
            investigators.map(
                (investigator, index) => {

                    return (
                        `**${index + 1}. ` +
                        `${investigator.playerName || "Unknown"}**\n` +
                        `${investigator.playerUuid || "UUID unavailable"}`
                    );
                }
            );

        addText(
            container,
            lines.join("\n\n")
        );
    }

    addSeparator(container);

    addButtonRow(
        container,
        [
            createButton(
                `case:assign:${caseData.id}`,
                "Assign",
                ButtonStyle.Primary
            ),

            createButton(
                `case:unassign:${caseData.id}`,
                "Unassign",
                ButtonStyle.Secondary
            ),

            createButton(
                `case:view:${caseData.id}`,
                "Back",
                ButtonStyle.Secondary
            )
        ]
    );

    return container;
}


/**
 * Case status panel.
 */
function caseStatusPanel(
    caseData
) {

    const container =
        createContainer({
            accentColor:
                statusColor(caseData.status)
        });

    addText(
        container,
        `# Case Status — #${caseData.id}`
    );

    addSeparator(container);

    addText(
        container,
        `Current status: **${statusLabel(caseData.status)}**`
    );

    addSeparator(container);

    addButtonRow(
        container,
        [
            createButton(
                `case:status:OPEN:${caseData.id}`,
                "Open",
                ButtonStyle.Secondary
            ),

            createButton(
                `case:status:INVESTIGATING:${caseData.id}`,
                "Investigating",
                ButtonStyle.Secondary
            ),

            createButton(
                `case:status:WAITING_FOR_EVIDENCE:${caseData.id}`,
                "Waiting",
                ButtonStyle.Secondary
            )
        ]
    );

    addButtonRow(
        container,
        [
            createButton(
                `case:status:ESCALATED:${caseData.id}`,
                "Escalated",
                ButtonStyle.Danger
            ),

            createButton(
                `case:status:RESOLVED:${caseData.id}`,
                "Resolved",
                ButtonStyle.Success
            ),

            createButton(
                `case:status:CLOSED:${caseData.id}`,
                "Closed",
                ButtonStyle.Secondary
            )
        ]
    );

    return container;
}


/**
 * Permission settings panel.
 */
function permissionPanel(
    permissionInfo
) {

    const container =
        createContainer({
            accentColor: ONYX.accent
        });

    addText(
        container,
        "# CaseManager Permissions"
    );

    addSeparator(container);

    addText(
        container,
        `**Mode:** ${permissionInfo.mode || "global"}\n` +
        `**Configured:** ${permissionInfo.configured ? "Yes" : "No"}\n` +
        `**Role ID:** ${permissionInfo.roleId || "Not configured"}`
    );

    addSeparator(container);

    addText(
        container,
        permissionInfo.description ||
        "Configure which Discord roles can use CaseManager commands."
    );

    addSeparator(container);

    addButtonRow(
        container,
        [
            createButton(
                "settings:permissions",
                "Permission Settings",
                ButtonStyle.Primary
            ),

            createButton(
                "settings:back",
                "Back",
                ButtonStyle.Secondary
            )
        ]
    );

    return container;
}


/**
 * Settings panel.
 */
function settingsPanel(
    settings = {}
) {

    const container =
        createContainer({
            accentColor: ONYX.accent
        });

    addText(
        container,
        "# CaseManager Settings"
    );

    addSeparator(container);

    addText(
        container,
        `**Database:** ${settings.database || "Connected"}\n` +
        `**Permission Mode:** ${settings.permissionMode || "global"}\n` +
        `**Storage:** ${settings.storage || "Configured"}`
    );

    addSeparator(container);

    addButtonRow(
        container,
        [
            createButton(
                "settings:permissions",
                "Permissions",
                ButtonStyle.Primary
            ),

            createButton(
                "settings:storage",
                "Storage",
                ButtonStyle.Secondary
            )
        ]
    );

    return container;
}


/**
 * Confirmation panel.
 */
function confirmationPanel(
    options = {}
) {

    const container =
        createContainer({
            accentColor:
                options.danger
                    ? ONYX.danger
                    : ONYX.warning
        });

    addText(
        container,
        `# ${options.title || "Confirmation"}`
    );

    addSeparator(container);

    addText(
        container,
        options.description ||
        "Are you sure you want to continue?"
    );

    addSeparator(container);

    addButtonRow(
        container,
        [
            createButton(
                options.confirmId || "confirm",
                options.confirmLabel || "Confirm",
                options.danger
                    ? ButtonStyle.Danger
                    : ButtonStyle.Primary
            ),

            createButton(
                options.cancelId || "cancel",
                options.cancelLabel || "Cancel",
                ButtonStyle.Secondary
            )
        ]
    );

    return container;
}


/**
 * Error panel.
 */
function errorPanel(
    message
) {

    const container =
        createContainer({
            accentColor: ONYX.danger
        });

    addText(
        container,
        "# CaseManager Error"
    );

    addSeparator(container);

    addText(
        container,
        message ||
        "An unknown error occurred."
    );

    return container;
}


/**
 * Success panel.
 */
function successPanel(
    title,
    message
) {

    const container =
        createContainer({
            accentColor: ONYX.success
        });

    addText(
        container,
        `# ${title || "Success"}`
    );

    addSeparator(container);

    addText(
        container,
        message ||
        "The operation completed successfully."
    );

    return container;
}


/**
 * Generic information panel.
 */
function infoPanel(
    title,
    message
) {

    const container =
        createContainer({
            accentColor: ONYX.info
        });

    addText(
        container,
        `# ${title || "Information"}`
    );

    addSeparator(container);

    addText(
        container,
        message ||
        "No additional information is available."
    );

    return container;
}


/**
 * Pagination panel.
 */
function paginationPanel(
    title,
    content,
    page,
    totalPages,
    prefix
) {

    const container =
        createContainer({
            accentColor: ONYX.accent
        });

    addText(
        container,
        `# ${title}`
    );

    addSeparator(container);

    addText(
        container,
        content || "No results."
    );

    addSeparator(container);

    const previousDisabled =
        page <= 1;

    const nextDisabled =
        page >= totalPages;

    addButtonRow(
        container,
        [
            createButton(
                `${prefix}:previous:${page}`,
                "Previous",
                ButtonStyle.Secondary,
                previousDisabled
            ),

            createButton(
                `${prefix}:page:${page}`,
                `${page} / ${totalPages}`,
                ButtonStyle.Secondary,
                true
            ),

            createButton(
                `${prefix}:next:${page}`,
                "Next",
                ButtonStyle.Secondary,
                nextDisabled
            )
        ]
    );

    return container;
}


/**
 * Formats a date safely.
 */
function formatDate(value) {

    if (!value) {

        return "Unknown";
    }

    try {

        const date =
            value instanceof Date
                ? value
                : new Date(value);

        if (
            Number.isNaN(
                date.getTime()
            )
        ) {

            return String(value);
        }

        return `<t:${Math.floor(
            date.getTime() / 1000
        )}:f>`;

    } catch {

        return String(value);
    }
}


/**
 * Formats timeline action names.
 */
function formatTimelineAction(
    action
) {

    return String(
        action || "UNKNOWN"
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


module.exports = {

    createContainer,
    addText,
    addSeparator,
    addButtonRow,
    createButton,

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
    infoPanel,

    paginationPanel,

    formatDate,
    formatTimelineAction
};