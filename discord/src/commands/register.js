const {
    REST,
    Routes
} = require("discord.js");

const config =
    require("../config");

const caseCommand =
    require("./case");


/*
 * ============================================================
 * CaseManager — Slash Command Registration
 * ============================================================
 *
 * Registers CaseManager commands to the configured Discord
 * server.
 *
 * This uses guild registration so changes appear quickly while
 * the bot is being developed.
 * ============================================================
 */


const commands = [
    caseCommand.data.toJSON()
];


async function registerCommands() {

    console.log(
        "[CaseManager] Registering Discord slash commands..."
    );


    const rest =
        new REST({
            version: "10"
        }).setToken(
            config.discord.token
        );


    try {

        const registered =
            await rest.put(
                Routes.applicationGuildCommands(
                    config.discord.clientId,
                    config.discord.guildId
                ),
                {
                    body: commands
                }
            );


        console.log(
            `[CaseManager] Successfully registered ${registered.length} slash command(s).`
        );


        for (
            const command
            of registered
        ) {

            console.log(
                `[CaseManager] Registered /${command.name}`
            );
        }


    } catch (error) {

        console.error(
            "[CaseManager] Failed to register slash commands:",
            error
        );

        process.exitCode = 1;
    }
}


registerCommands();