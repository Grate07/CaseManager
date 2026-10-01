const {
    Client,
    GatewayIntentBits,
    Collection,
    Events
} = require("discord.js");

const config = require("./config");
const database = require("./database/database");


/*
 * ============================================================
 * CaseManager Discord Bot
 * ============================================================
 *
 * Main entry point.
 *
 * Responsibilities:
 * - Create Discord client
 * - Initialize PostgreSQL
 * - Load slash commands
 * - Load interaction events
 * - Validate permission configuration
 * - Log into Discord
 * - Gracefully shut down
 * ============================================================
 */


/*
 * ============================================================
 * Discord Client
 * ============================================================
 */

const client = new Client({

    intents: [
        GatewayIntentBits.Guilds
    ]
});


/*
 * Store slash commands.
 */

client.commands =
    new Collection();


/*
 * ============================================================
 * Load Commands
 * ============================================================
 */

const caseCommand =
    require("./commands/case");

client.commands.set(
    caseCommand.data.name,
    caseCommand
);


/*
 * ============================================================
 * Interaction Handler
 * ============================================================
 *
 * The actual interaction handler will be implemented in:
 *
 * src/events/interactionCreate.js
 *
 * We load it here so the main entry point stays clean.
 * ============================================================
 */

const interactionCreate =
    require("./events/interactionCreate");

client.on(
    Events.InteractionCreate,
    interactionCreate
);


/*
 * ============================================================
 * Discord Ready
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
         * Initialize PostgreSQL.
         */

        database.initialize();


        /*
         * Verify the database connection before
         * attempting to log into Discord.
         */

        await database.testConnection();

        console.log(
            "[CaseManager] PostgreSQL connection successful."
        );


        /*
         * Validate permission configuration.
         */

        const permissionService =
            require("./permissions/permissionService");

        const permissionConfig =
            permissionService.validateConfiguration();


        if (!permissionConfig.valid) {

            console.warn(
                "[CaseManager] Permission configuration warning:"
            );

            for (
                const error
                of permissionConfig.errors
            ) {

                console.warn(
                    `[CaseManager] - ${error}`
                );
            }

        } else {

            console.log(
                `[CaseManager] ${permissionService.getModeDescription()}`
            );
        }


        /*
         * Log into Discord.
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
         * Try to clean up the database if startup
         * partially succeeded.
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
 * Graceful Shutdown
 * ============================================================
 */

let shuttingDown = false;


async function shutdown(
    signal
) {

    if (shuttingDown) {

        return;
    }

    shuttingDown = true;


    console.log(
        `[CaseManager] Received ${signal}. Shutting down...`
    );


    try {

        /*
         * Destroy Discord connection.
         */

        if (client) {

            client.destroy();
        }


        /*
         * Close PostgreSQL pool.
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
 * Process Signals
 * ============================================================
 */

process.once(
    "SIGINT",
    () => shutdown("SIGINT")
);

process.once(
    "SIGTERM",
    () => shutdown("SIGTERM")
);


/*
 * ============================================================
 * Unhandled Errors
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
 * Start
 * ============================================================
 */

start();