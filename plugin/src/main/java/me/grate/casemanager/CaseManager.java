package me.grate.casemanager;

import me.grate.casemanager.casefile.CaseService;
import me.grate.casemanager.command.CaseCommand;
import me.grate.casemanager.database.DatabaseManager;
import me.grate.casemanager.database.DatabaseTables;
import org.bukkit.plugin.java.JavaPlugin;

public final class CaseManager extends JavaPlugin {

    private static CaseManager instance;

    private DatabaseManager databaseManager;
    private CaseService caseService;

    @Override
    public void onEnable() {

        instance = this;

        saveDefaultConfig();

        getLogger().info("CaseManager is starting...");

        databaseManager = new DatabaseManager(this);

        try {

            databaseManager.connect();

            DatabaseTables.createTables(
                    this,
                    databaseManager
            );

            caseService = new CaseService(
                    databaseManager
            );

        } catch (Exception exception) {

            getLogger().severe(
                    "Unable to initialize the database."
            );

            exception.printStackTrace();

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);

            return;
        }

        CaseCommand caseCommand =
                new CaseCommand(this);

        if (getCommand("case") != null) {

            getCommand("case")
                    .setExecutor(caseCommand);

            getCommand("case")
                    .setTabCompleter(caseCommand);

        } else {

            getLogger().severe(
                    "The /case command is missing from plugin.yml!"
            );

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);

            return;
        }

        getLogger().info(
                "CaseManager enabled!"
        );
    }

    @Override
    public void onDisable() {

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info(
                "CaseManager disabled!"
        );
    }

    public static CaseManager getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public CaseService getCaseService() {
        return caseService;
    }
}
