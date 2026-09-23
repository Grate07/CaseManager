package me.grate.casemanager;

import me.grate.casemanager.casefile.CaseEvidenceService;
import me.grate.casemanager.casefile.CaseInvestigatorService;
import me.grate.casemanager.casefile.CaseNoteService;
import me.grate.casemanager.casefile.CaseService;
import me.grate.casemanager.casefile.CaseTimelineService;
import me.grate.casemanager.command.CaseCommand;
import me.grate.casemanager.database.DatabaseManager;
import me.grate.casemanager.database.DatabaseTables;
import me.grate.casemanager.integration.EvidenceIntegrationService;
import me.grate.casemanager.integration.IntegrationManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class CaseManager extends JavaPlugin {

    private static CaseManager instance;

    private DatabaseManager databaseManager;

    private CaseService caseService;
    private CaseTimelineService caseTimelineService;
    private CaseNoteService caseNoteService;
    private CaseEvidenceService caseEvidenceService;
    private CaseInvestigatorService caseInvestigatorService;

    private IntegrationManager integrationManager;
    private EvidenceIntegrationService evidenceIntegrationService;

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

            if (!databaseManager.isConnected()) {

                throw new IllegalStateException(
                        "Database connection pool was not initialized."
                );
            }

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

        /*
         * Initialize optional external integrations.
         *
         * CaseManager does not require any of these plugins.
         */
        integrationManager =
                new IntegrationManager(this);

        integrationManager.initialize();

        /*
         * Create the service that connects external integrations
         * to the CaseManager evidence system.
         */
        evidenceIntegrationService =
                new EvidenceIntegrationService(
                        this,
                        integrationManager,
                        caseEvidenceService
                );

        if (getCommand("case") == null) {

            getLogger().severe(
                    "The /case command is missing from plugin.yml!"
            );

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);

            return;
        }

        CaseCommand caseCommand =
                new CaseCommand(this);

        getCommand("case")
                .setExecutor(caseCommand);

        getCommand("case")
                .setTabCompleter(caseCommand);

        getLogger().info(
                "CaseManager enabled!"
        );

        getLogger().info(
                "Available integrations: " +
                        evidenceIntegrationService
                                .getAvailableIntegrations()
        );
    }

    @Override
    public void onDisable() {

        if (integrationManager != null) {

            integrationManager.shutdown();
        }

        if (databaseManager != null) {

            databaseManager.close();
        }

        evidenceIntegrationService = null;
        integrationManager = null;

        caseService = null;
        caseTimelineService = null;
        caseNoteService = null;
        caseEvidenceService = null;
        caseInvestigatorService = null;

        databaseManager = null;

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

    public IntegrationManager getIntegrationManager() {

        return integrationManager;
    }

    public EvidenceIntegrationService getEvidenceIntegrationService() {

        return evidenceIntegrationService;
    }
}