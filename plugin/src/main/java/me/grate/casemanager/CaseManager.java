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
import org.bukkit.command.PluginCommand;
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

        /*
         * ========================================================
         * Database
         * ========================================================
         */

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

            /*
             * ====================================================
             * Services
             * ====================================================
             */

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

            getLogger().severe(
                    "CaseManager will now be disabled."
            );

            exception.printStackTrace();

            cleanup();

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);

            return;
        }

        /*
         * ========================================================
         * Optional Integrations
         * ========================================================
         *
         * CaseManager does not require:
         *
         * - CoreProtect
         * - Vulcan
         * - LiteBans
         *
         * They are detected and initialized independently.
         */

        try {

            integrationManager =
                    new IntegrationManager(this);

            integrationManager.initialize();

            evidenceIntegrationService =
                    new EvidenceIntegrationService(
                            this,
                            integrationManager,
                            caseEvidenceService
                    );

        } catch (Exception exception) {

            getLogger().severe(
                    "Failed to initialize optional integrations."
            );

            exception.printStackTrace();

            cleanup();

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);

            return;
        }

        /*
         * ========================================================
         * /case Command
         * ========================================================
         */

        PluginCommand casePluginCommand =
                getCommand("case");

        if (casePluginCommand == null) {

            getLogger().severe(
                    "The /case command is missing from plugin.yml!"
            );

            cleanup();

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);

            return;
        }

        CaseCommand caseCommand =
                new CaseCommand(this);

        casePluginCommand.setExecutor(
                caseCommand
        );

        casePluginCommand.setTabCompleter(
                caseCommand
        );

        /*
         * ========================================================
         * Startup Complete
         * ========================================================
         */

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

        cleanup();

        getLogger().info(
                "CaseManager disabled!"
        );
    }

    /*
     * ============================================================
     * Cleanup
     * ============================================================
     */

    private void cleanup() {

        /*
         * Shutdown integrations first.
         */
        if (integrationManager != null) {

            try {

                integrationManager.shutdown();

            } catch (Exception exception) {

                getLogger().warning(
                        "Failed to shut down integrations: " +
                                getRootMessage(exception)
                );
            }
        }

        /*
         * Close the database after integrations have stopped.
         */
        if (databaseManager != null) {

            try {

                databaseManager.close();

            } catch (Exception exception) {

                getLogger().warning(
                        "Failed to close database connection pool: " +
                                getRootMessage(exception)
                );
            }
        }

        /*
         * Clear references.
         */
        evidenceIntegrationService = null;

        integrationManager = null;

        caseService = null;

        caseTimelineService = null;

        caseNoteService = null;

        caseEvidenceService = null;

        caseInvestigatorService = null;

        databaseManager = null;

        instance = null;
    }

    private String getRootMessage(
            Throwable throwable
    ) {

        if (throwable == null) {

            return "Unknown error";
        }

        Throwable current =
                throwable;

        while (current.getCause() != null) {

            current =
                    current.getCause();
        }

        String message =
                current.getMessage();

        if (message == null ||
                message.isBlank()) {

            return current
                    .getClass()
                    .getSimpleName();
        }

        return message;
    }

    /*
     * ============================================================
     * Getters
     * ============================================================
     */

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