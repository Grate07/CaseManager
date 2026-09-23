package me.grate.casemanager;

import me.grate.casemanager.casefile.CaseEvidenceService;
import me.grate.casemanager.casefile.CaseInvestigatorService;
import me.grate.casemanager.casefile.CaseNoteService;
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
    private CaseNoteService caseNoteService;
    private CaseEvidenceService caseEvidenceService;
    private CaseInvestigatorService caseInvestigatorService;

    @Override
    public void onEnable() {

        instance = this;

        saveDefaultConfig();

        getLogger().info(
                "CaseManager is starting..."
        );

        databaseManager =
                new DatabaseManager(this);

        try {

            databaseManager.connect();

            DatabaseTables.createTables(
                    this,
                    databaseManager
            );

            caseTimelineService =
                    new CaseTimelineService(
                            databaseManager
                    );

            caseNoteService =
                    new CaseNoteService(
                            databaseManager,
                            caseTimelineService
                    );

            caseEvidenceService =
                    new CaseEvidenceService(
                            databaseManager,
                            caseTimelineService
                    );

            caseInvestigatorService =
                    new CaseInvestigatorService(
                            databaseManager,
                            caseTimelineService
                    );

            caseService =
                    new CaseService(
                            databaseManager,
                            caseTimelineService
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

        instance = null;

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

    public CaseNoteService getCaseNoteService() {
        return caseNoteService;
    }

    public CaseEvidenceService getCaseEvidenceService() {
        return caseEvidenceService;
    }

    public CaseInvestigatorService getCaseInvestigatorService() {
        return caseInvestigatorService;
    }
}