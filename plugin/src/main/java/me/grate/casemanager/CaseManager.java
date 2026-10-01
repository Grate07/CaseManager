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
import me.grate.casemanager.service.MediaEvidenceService;
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

    private StorageManager storageManager;
    private MediaEvidenceService mediaEvidenceService;

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
             * Core Services
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
         * Evidence Storage
         * ========================================================
         *
         * StorageManager selects the configured storage provider.
         *
         * Current provider:
         *
         * Supabase Storage
         *
         * Binary media is stored there while PostgreSQL stores
         * the evidence metadata.
         */

        try {

            storageManager =
                    new StorageManager(this);

            storageManager.initialize();

            /*
             * MediaEvidenceService sits above the storage layer.
             *
             * It coordinates:
             *
             * Case
             *   ↓
             * Storage upload
             *   ↓
             * Evidence metadata
             *   ↓
             * Timeline
             */

            mediaEvidenceService =
                    new MediaEvidenceService(
                            this,
                            caseEvidenceService,
                            storageManager
                    );

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
         * ========================================================
         * Optional Integrations
         * ========================================================
         *
         * CaseManager does not require:
         *
         * - CoreProtect
         * - Vulcan
         * - LiteBans
         * - Floodgate
         *
         * They are detected independently.
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
         * Startup Information
         * ========================================================
         */

        getLogger().info(
                "Database connected successfully."
        );

        getLogger().info(
                "Evidence storage provider: " +
                        storageManager.getProviderName()
        );

        if (storageManager.isAvailable()) {

            getLogger().info(
                    "Evidence storage is available."
            );

        } else {

            getLogger().warning(
                    "Evidence storage is configured but currently unavailable."
            );

            getLogger().warning(
                    "Text-based case evidence will still function."
            );
        }

        getLogger().info(
                "Available integrations: " +
                        evidenceIntegrationService
                                .getAvailableIntegrations()
        );

        /*
         * ========================================================
         * Startup Complete
         * ========================================================
         */

        getLogger().info(
                "CaseManager enabled!"
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
         * ========================================================
         * Integrations
         * ========================================================
         *
         * Stop integrations before closing the database.
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
         * ========================================================
         * Evidence Storage
         * ========================================================
         *
         * StorageManager itself does not own a connection pool,
         * but clearing it here prevents stale references after
         * plugin shutdown.
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
         * ========================================================
         * Database
         * ========================================================
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
         * ========================================================
         * Clear References
         * ========================================================
         */

        evidenceIntegrationService = null;

        integrationManager = null;

        mediaEvidenceService = null;

        storageManager = null;

        caseService = null;

        caseTimelineService = null;

        caseNoteService = null;

        caseEvidenceService = null;

        caseInvestigatorService = null;

        databaseManager = null;

        instance = null;
    }
    /*
     * ============================================================
     * Error Handling
     * ============================================================
     */

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
     * Singleton
     * ============================================================
     */

    public static CaseManager getInstance() {

        return instance;
    }

    /*
     * ============================================================
     * Database
     * ============================================================
     */

    public DatabaseManager getDatabaseManager() {

        return databaseManager;
    }

    /*
     * ============================================================
     * Case Services
     * ============================================================
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
     * ============================================================
     * Evidence Storage
     * ============================================================
     */

    public StorageManager getStorageManager() {

        return storageManager;
    }

    public MediaEvidenceService getMediaEvidenceService() {

        return mediaEvidenceService;
    }

    /*
     * ============================================================
     * Integrations
     * ============================================================
     */

    public IntegrationManager getIntegrationManager() {

        return integrationManager;
    }

    public EvidenceIntegrationService getEvidenceIntegrationService() {

        return evidenceIntegrationService;
    }
}