const ONYX = Object.freeze({

    /*
     * Discord's color value used by any
     * components that accept an accent color.
     */
    accent: 0x202225,

    dark: 0x111214,

    darker: 0x0B0C0E,

    lightText: 0xF2F3F5,

    mutedText: 0x949BA4,

    success: 0x57F287,

    warning: 0xFEE75C,

    danger: 0xED4245,

    info: 0x5865F2
});


function statusColor(status) {

    switch (
        String(status || "")
            .toUpperCase()
    ) {

        case "OPEN":
            return ONYX.info;

        case "INVESTIGATING":
            return ONYX.warning;

        case "WAITING_FOR_EVIDENCE":
            return ONYX.warning;

        case "ESCALATED":
            return ONYX.danger;

        case "RESOLVED":
            return ONYX.success;

        case "CLOSED":
            return ONYX.dark;

        default:
            return ONYX.accent;
    }
}


function statusLabel(status) {

    return String(status || "UNKNOWN")
        .replaceAll("_", " ")
        .toLowerCase()
        .replace(
            /\b\w/g,
            character =>
                character.toUpperCase()
        );
}


module.exports = {
    ONYX,
    statusColor,
    statusLabel
};