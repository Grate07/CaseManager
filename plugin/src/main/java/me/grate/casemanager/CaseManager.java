package me.grate.casemanager;

import me.grate.casemanager.casefile.CaseService;
import me.grate.casemanager.casefile.CaseTimelineService;
import me.grate.casemanager.command.CaseCommand;
import me.grate.casemanager.database.DatabaseManager;
import me.grate.casemanager.database.DatabaseTables;
import org.bukkit.plugin.java.JavaPlugin;

public final class CaseManager extends JavaPlugin {

    private static CaseManager instance;

    private DatabaseManager databaseManager;
    private CaseService caseService;
    private CaseTimelineService caseTimelineService;

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

            caseTimelineService =
                    new CaseTimelineService(
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

        if (getCommand("case") == null) {

            getLogger().severe(
                    "The /case command is missing from plugin.yml!"
            );

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);

            return;
        }

        getCommand("case")
                .setExecutor(caseCommand);

        getCommand("case")
                .setTabCompleter(caseCommand);

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

    public CaseTimelineService getCaseTimelineService() {
        return caseTimelineService;
    }
}
