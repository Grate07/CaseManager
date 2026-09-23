package me.grate.casemanager.integration;

import me.grate.casemanager.CaseManager;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IntegrationManager {

    private final CaseManager plugin;

    private final Map<String, Object> integrations =
            new LinkedHashMap<>();

    public IntegrationManager(CaseManager plugin) {
        this.plugin = plugin;
    }

    public void initialize() {

        plugin.getLogger().info(
                "Initializing integrations..."
        );

        detectPlugin(
                "CoreProtect"
        );

        detectPlugin(
                "Vulcan"
        );

        detectPlugin(
                "LiteBans"
        );

        plugin.getLogger().info(
                "Integration detection complete."
        );
    }

    private void detectPlugin(
            String pluginName
    ) {

        Plugin externalPlugin =
                plugin.getServer()
                        .getPluginManager()
                        .getPlugin(pluginName);

        if (externalPlugin == null) {

            plugin.getLogger().info(
                    pluginName +
                            " not found. Integration disabled."
            );

            return;
        }

        if (!externalPlugin.isEnabled()) {

            plugin.getLogger().warning(
                    pluginName +
                            " is installed but not enabled."
            );

            return;
        }

        integrations.put(
                pluginName.toLowerCase(),
                externalPlugin
        );

        plugin.getLogger().info(
                pluginName +
                        " detected. Integration available."
        );
    }

    public boolean isAvailable(
            String pluginName
    ) {

        if (pluginName == null) {
            return false;
        }

        return integrations.containsKey(
                pluginName.toLowerCase()
        );
    }

    public Plugin getPlugin(
            String pluginName
    ) {

        if (pluginName == null) {
            return null;
        }

        Object integration =
                integrations.get(
                        pluginName.toLowerCase()
                );

        if (integration instanceof Plugin plugin) {
            return plugin;
        }

        return null;
    }

    public Map<String, Object> getIntegrations() {

        return Collections.unmodifiableMap(
                integrations
        );
    }

    public void shutdown() {

        integrations.clear();

        plugin.getLogger().info(
                "Integration manager shut down."
        );
    }
}