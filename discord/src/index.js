const {
    Client,
    GatewayIntentBits,
    Collection,
    Events
} = require("discord.js");

const config =
    require("./config");

const database =
    require("./database/database");

const permissionService =
    require("./permissions/permissionService");


/*
 * ============================================================
 * CaseManager Discord Bot
 * ============================================================
 *
 * Main Discord client entry point.
 *
 * Responsibilities:
 *
 * - Create the Discord client
 * - Initialize PostgreSQL
 * - Load slash commands
 * - Register interaction handlers
 * - Validate Discord permission configuration
 * - Start the bot
 * - Gracefully shut everything down
 *
 * CaseManager uses the same PostgreSQL database as the
 * Minecraft plugin.
 *
 * Discord UI uses Components V2.
 * ============================================================
 */


/*
 * ============================================================
 * Discord Client
 * ============================================================
 */

const client =
    new Client({

        intents: [
            GatewayIntentBits.Guilds
        ]

    });


/*
 * ============================================================
 * Command Collection
 * ============================================================
 */

client.commands =
    new Collection();


/*
 * Load /case command.
 */

const caseCommand =
    require("./commands/case");

client.commands.set(
    caseCommand.data.name,
    caseCommand
);


/*
 * Load interaction handler.
 */

const interactionCreate =
    require("./events/interactionCreate");

client.on(
    Events.InteractionCreate,
    interactionCreate
);


/*
 * ============================================================
 * Discord Ready Event
 * ============================================================
 */

client.once(
    Events.ClientReady,
    readyClient => {

        console.log(
            `[CaseManager] Discord bot logged in as ${readyClient.user.tag}`
        );

        console.log(
            `[CaseManager] Connected to ${readyClient.guilds.cache.size} server(s).`
        );

        console.log(
            `[CaseManager] Loaded ${client.commands.size} command(s).`
        );

    }
);
/*
 * ============================================================
 * Startup
 * ============================================================
 */

async function start() {

    try {

        console.log(
            "[CaseManager] Starting Discord bot..."
        );


        /*
         * ----------------------------------------------------
         * Initialize PostgreSQL
         * ----------------------------------------------------
         */

        database.initialize();


        /*
         * ----------------------------------------------------
         * Test PostgreSQL connection
         * ----------------------------------------------------
         */

        await database.testConnection();

        console.log(
            "[CaseManager] PostgreSQL connection successful."
        );


        /*
         * ----------------------------------------------------
         * Load permission configuration
         * ----------------------------------------------------
         *
         * The permission system is database-backed.
         *
         * A guild can use:
         *
         * global
         * or
         * per-command
         *
         * mode.
         *
         * The permission service is asynchronous because
         * settings can come from PostgreSQL.
         * ----------------------------------------------------
         */

        const permissionValidation =
            await validatePermissionConfiguration();


        /*
         * ----------------------------------------------------
         * Display permission configuration status
         * ----------------------------------------------------
         */

        if (
            !permissionValidation.valid
        ) {

            console.warn(
                "[CaseManager] Permission configuration warning:"
            );

            for (
                const error
                of permissionValidation.errors
            ) {

                console.warn(
                    `[CaseManager] - ${error}`
                );

            }

        } else {

            console.log(
                "[CaseManager] Discord permission configuration is valid."
            );

        }


        /*
         * ----------------------------------------------------
         * Login to Discord
         * ----------------------------------------------------
         */

        await client.login(
            config.discord.token
        );

    } catch (error) {

        console.error(
            "[CaseManager] Failed to start:",
            error
        );


        /*
         * Make sure PostgreSQL is closed if startup fails.
         */

        try {

            await database.shutdown();

        } catch (shutdownError) {

            console.error(
                "[CaseManager] Database shutdown error:",
                shutdownError
            );

        }


        process.exitCode = 1;

    }

}


/*
 * ============================================================
 * Permission Configuration Validation
 * ============================================================
 */

async function validatePermissionConfiguration() {

    const guildId =
        config.discord.guildId;


    /*
     * No guild ID means the bot cannot determine which
     * server's database settings should be loaded.
     */

    if (!guildId) {

        return {

            valid: false,

            errors: [
                "DISCORD_GUILD_ID is not configured."
            ]

        };

    }


    try {

        const validation =
            await permissionService.validateGuild(
                guildId
            );


        return validation;

    } catch (error) {

        console.error(
            "[CaseManager] Failed to validate Discord permissions:",
            error
        );


        return {

            valid: false,

            errors: [
                `Could not validate permission settings: ${error.message}`
            ]

        };

    }

}
/*
 * ============================================================
 * Graceful Shutdown
 * ============================================================
 *
 * The bot needs to close both:
 *
 * 1. Discord connection
 * 2. PostgreSQL connection pool
 *
 * before the process exits.
 * ============================================================
 */

let shuttingDown =
    false;


async function shutdown(
    signal
) {

    /*
     * Prevent multiple shutdown handlers from running
     * simultaneously.
     */

    if (
        shuttingDown
    ) {

        return;

    }

    shuttingDown =
        true;


    console.log(
        `[CaseManager] Received ${signal}. Shutting down...`
    );


    try {

        /*
         * ----------------------------------------------------
         * Disconnect Discord
         * ----------------------------------------------------
         */

        if (
            client
        ) {

            client.destroy();

        }


        /*
         * ----------------------------------------------------
         * Close PostgreSQL
         * ----------------------------------------------------
         */

        await database.shutdown();


        console.log(
            "[CaseManager] Shutdown complete."
        );

    } catch (error) {

        console.error(
            "[CaseManager] Shutdown error:",
            error
        );

    } finally {

        process.exit(
            0
        );

    }

}
/*
 * ============================================================
 * Process Signal Handlers
 * ============================================================
 */

process.once(
    "SIGINT",
    () => {

        shutdown(
            "SIGINT"
        );

    }
);


process.once(
    "SIGTERM",
    () => {

        shutdown(
            "SIGTERM"
        );

    }
);


/*
 * ============================================================
 * Error Handling
 * ============================================================
 *
 * These handlers prevent unexpected asynchronous errors from
 * disappearing silently.
 * ============================================================
 */

process.on(
    "unhandledRejection",
    error => {

        console.error(
            "[CaseManager] Unhandled promise rejection:",
            error
        );

    }
);


process.on(
    "uncaughtException",
    error => {

        console.error(
            "[CaseManager] Uncaught exception:",
            error
        );

    }
);
/*
 * ============================================================
 * Startup Invocation
 * ============================================================
 *
 * This must remain at the bottom of the file so all event
 * handlers and functions have already been registered before
 * the bot starts connecting.
 * ============================================================
 */

start();
/*
 * ============================================================
 * END OF discord/src/index.js
 * ============================================================
 *
 * IMPORTANT:
 *
 * Do NOT add:
 *
 * permissionService.validateConfiguration()
 *
 * or:
 *
 * permissionService.getModeDescription()
 *
 * here.
 *
 * The new permission system is database-backed and uses:
 *
 *     await permissionService.validateGuild(...)
 *
 * instead.
 *
 * ============================================================
 */