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
import me.grate.casemanager.storage.StorageManager;
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

    private StorageManager storageManager;

    @Override
    public void onEnable() {

        instance = this;

        saveDefaultConfig();

        getLogger().info(
                "CaseManager is starting..."
        );

        /*
         * =========================================================
         * DATABASE
         * =========================================================
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
             * =====================================================
             * CASE SERVICES
             * =====================================================
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
         * =========================================================
         * EVIDENCE STORAGE
         * =========================================================
         */

        try {

            storageManager =
                    new StorageManager(this);

            storageManager.initialize();

        } catch (Exception exception) {

            getLogger().severe(
                    "Failed to initialize evidence storage."
            );

            exception.printStackTrace();

            cleanup();

            getServer()
                    .getPluginManager()
                    .disablePlugin(this);

            return;
        }


        /*
         * =========================================================
         * OPTIONAL INTEGRATIONS
         * =========================================================
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
         * =========================================================
         * /CASE COMMAND
         * =========================================================
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
         * =========================================================
         * STARTUP COMPLETE
         * =========================================================
         */

        getLogger().info(
                "CaseManager enabled!"
        );

        getLogger().info(
                "Evidence storage provider: " +
                        storageManager.getProviderName()
        );

        getLogger().info(
                "Evidence storage available: " +
                        storageManager.isAvailable()
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

    private void cleanup() {

        /*
         * =========================================================
         * STORAGE
         * =========================================================
         */

        if (storageManager != null) {

            try {

                storageManager.shutdown();

            } catch (Exception exception) {

                getLogger().warning(
                        "Failed to shut down evidence storage: " +
                                getRootMessage(exception)
                );
            }
        }


        /*
         * =========================================================
         * INTEGRATIONS
         * =========================================================
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
         * =========================================================
         * DATABASE
         * =========================================================
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
         * =========================================================
         * CLEAR REFERENCES
         * =========================================================
         */

        storageManager = null;

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
     * =========================================================
     * STATIC ACCESS
     * =========================================================
     */

    public static CaseManager getInstance() {

        return instance;
    }


    /*
     * =========================================================
     * DATABASE
     * =========================================================
     */

    public DatabaseManager getDatabaseManager() {

        return databaseManager;
    }


    /*
     * =========================================================
     * CASE SERVICES
     * =========================================================
     */

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


    /*
     * =========================================================
     * INTEGRATIONS
     * =========================================================
     */

    public IntegrationManager getIntegrationManager() {

        return integrationManager;
    }

    public EvidenceIntegrationService
    getEvidenceIntegrationService() {

        return evidenceIntegrationService;
    }


    /*
     * =========================================================
     * STORAGE
     * =========================================================
     */

    public StorageManager getStorageManager() {

        return storageManager;
    }
}