package me.grate.casemanager;

import org.bukkit.plugin.java.JavaPlugin;

public final class CaseManager extends JavaPlugin {

    private static CaseManager instance;

    @Override
    public void onEnable() {
        instance = this;

        getLogger().info("CaseManager is starting...");
        getLogger().info("CaseManager enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("CaseManager disabled!");
    }

    public static CaseManager getInstance() {
        return instance;
    }
}
