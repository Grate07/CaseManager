package me.grate.casemanager;

import me.grate.casemanager.database.DatabaseManager;
import me.grate.casemanager.database.DatabaseTables;
import org.bukkit.plugin.java.JavaPlugin;

public final class CaseManager extends JavaPlugin {

    private static CaseManager instance;

    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        getLogger().info("CaseManager is starting...");

        databaseManager = new DatabaseManager(this);

        try {
            databaseManager.connect();
            DatabaseTables.createTables(this, databaseManager);
        } catch (Exception exception) {
            getLogger().severe("Unable to connect to MySQL.");
            exception.printStackTrace();

            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getLogger().info("CaseManager enabled!");
    }

    @Override
    public void onDisable() {

        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("CaseManager disabled!");
    }

    public static CaseManager getInstance() {
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
