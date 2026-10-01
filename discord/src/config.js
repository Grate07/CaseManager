require("dotenv").config();

function required(name) {
    const value = process.env[name];

    if (!value || value.trim() === "") {
        throw new Error(`Missing required environment variable: ${name}`);
    }

    return value.trim();
}

function optional(name, fallback = "") {
    const value = process.env[name];

    if (!value || value.trim() === "") {
        return fallback;
    }

    return value.trim();
}

function parseBoolean(value, fallback = false) {
    if (value === undefined || value === null || value === "") {
        return fallback;
    }

    return value.toLowerCase() === "true";
}

function parseInteger(value, fallback) {
    if (value === undefined || value === null || value === "") {
        return fallback;
    }

    const parsed = Number.parseInt(value, 10);

    return Number.isFinite(parsed)
        ? parsed
        : fallback;
}

module.exports = {

    discord: {
        token: required("DISCORD_TOKEN"),
        clientId: required("DISCORD_CLIENT_ID"),
        guildId: required("DISCORD_GUILD_ID")
    },

    database: {
        host: required("DATABASE_HOST"),
        port: parseInteger(
            process.env.DATABASE_PORT,
            5432
        ),
        database: required("DATABASE_NAME"),
        user: required("DATABASE_USER"),
        password: required("DATABASE_PASSWORD"),

        ssl: parseBoolean(
            process.env.DATABASE_SSL,
            true
        ),

        max: parseInteger(
            process.env.DATABASE_POOL_SIZE,
            5
        )
    },

    permissions: {

        /*
         * TWO MODES
         *
         * global
         *   Every CaseManager command requires
         *   the role below.
         *
         * per-command
         *   Individual commands can have their
         *   own role IDs.
         */
        mode: optional(
            "CASEMANAGER_PERMISSION_MODE",
            "global"
        ).toLowerCase(),

        /*
         * OPTION 1:
         *
         * Paste the Discord role ID here.
         *
         * Example:
         * CASEMANAGER_ROLE_ID=123456789012345678
         */
        globalRoleId: optional(
            "CASEMANAGER_ROLE_ID",
            ""
        ),

        /*
         * OPTION 2:
         *
         * Different role for different commands.
         *
         * Leave blank to fall back to
         * the global role.
         */
        perCommand: {
            case: optional(
                "CASEMANAGER_ROLE_CASE",
                ""
            ),

            evidence: optional(
                "CASEMANAGER_ROLE_EVIDENCE",
                ""
            ),

            timeline: optional(
                "CASEMANAGER_ROLE_TIMELINE",
                ""
            ),

            investigators: optional(
                "CASEMANAGER_ROLE_INVESTIGATORS",
                ""
            ),

            status: optional(
                "CASEMANAGER_ROLE_STATUS",
                ""
            ),

            settings: optional(
                "CASEMANAGER_ROLE_SETTINGS",
                ""
            ),

            permissions: optional(
                "CASEMANAGER_ROLE_PERMISSIONS",
                ""
            )
        }
    },

    bot: {
        debug: parseBoolean(
            process.env.BOT_DEBUG,
            false
        )
    }
};