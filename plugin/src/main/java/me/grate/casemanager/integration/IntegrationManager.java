package me.grate.casemanager.integration;

import me.grate.casemanager.CaseManager;
import org.bukkit.plugin.Plugin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class IntegrationManager {

    private final CaseManager plugin;

    private final Map<String, Plugin> detectedPlugins =
            new LinkedHashMap<>();

    private CoreProtectIntegration coreProtectIntegration;
    private VulcanIntegration vulcanIntegration;
    private LiteBansIntegration liteBansIntegration;

    public IntegrationManager(CaseManager plugin) {
        this.plugin = plugin;
    }

    public void initialize() {

        plugin.getLogger().info(
                "Initializing integrations..."
        );

        detectedPlugins.clear();

        detectPlugin("CoreProtect");
        detectPlugin("Vulcan");
        detectPlugin("LiteBans");

        initializeCoreProtect();
        initializeVulcan();
        initializeLiteBans();

        plugin.getLogger().info(
                "Integration initialization complete."
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

        detectedPlugins.put(
                pluginName.toLowerCase(),
                externalPlugin
        );

        plugin.getLogger().info(
                pluginName +
                        " detected."
        );
    }

    private void initializeCoreProtect() {

        if (!isPluginDetected("CoreProtect")) {
            return;
        }

        try {

            coreProtectIntegration =
                    new CoreProtectIntegration(
                            plugin
                    );

            if (coreProtectIntegration.isAvailable()) {

                plugin.getLogger().info(
                        "CoreProtect integration enabled."
                );

            } else {

                plugin.getLogger().warning(
                        "CoreProtect was detected, but its API is unavailable."
                );
            }

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Failed to initialize CoreProtect integration: " +
                            exception.getMessage()
            );

            coreProtectIntegration = null;
        }
    }

    private void initializeVulcan() {

        if (!isPluginDetected("Vulcan")) {
            return;
        }

        try {

            vulcanIntegration =
                    new VulcanIntegration(
                            plugin
                    );

            vulcanIntegration.initialize();

            if (vulcanIntegration.isAvailable()) {

                plugin.getLogger().info(
                        "Vulcan integration enabled."
                );

            } else {

                plugin.getLogger().warning(
                        "Vulcan was detected, but its API is unavailable."
                );
            }

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Failed to initialize Vulcan integration: " +
                            exception.getMessage()
            );

            vulcanIntegration = null;
        }
    }

    private void initializeLiteBans() {

        if (!isPluginDetected("LiteBans")) {
            return;
        }

        try {

            liteBansIntegration =
                    new LiteBansIntegration(
                            plugin
                    );

            liteBansIntegration.initialize();

            if (liteBansIntegration.isAvailable()) {

                plugin.getLogger().info(
                        "LiteBans integration enabled."
                );

            } else {

                plugin.getLogger().warning(
                        "LiteBans was detected, but its API is unavailable."
                );
            }

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Failed to initialize LiteBans integration: " +
                            exception.getMessage()
            );

            liteBansIntegration = null;
        }
    }

    private boolean isPluginDetected(
            String pluginName
    ) {

        if (pluginName == null) {
            return false;
        }

        return detectedPlugins.containsKey(
                pluginName.toLowerCase()
        );
    }

    public boolean isAvailable(
            String pluginName
    ) {

        if (pluginName == null) {
            return false;
        }

        return detectedPlugins.containsKey(
                pluginName.toLowerCase()
        );
    }

    public Plugin getPlugin(
            String pluginName
    ) {

        if (pluginName == null) {
            return null;
        }

        return detectedPlugins.get(
                pluginName.toLowerCase()
        );
    }

    public CoreProtectIntegration getCoreProtect() {

        return coreProtectIntegration;
    }

    public VulcanIntegration getVulcan() {

        return vulcanIntegration;
    }

    public LiteBansIntegration getLiteBans() {

        return liteBansIntegration;
    }

    public boolean isCoreProtectAvailable() {

        return coreProtectIntegration != null &&
                coreProtectIntegration.isAvailable();
    }

    public boolean isVulcanAvailable() {

        return vulcanIntegration != null &&
                vulcanIntegration.isAvailable();
    }

    public boolean isLiteBansAvailable() {

        return liteBansIntegration != null &&
                liteBansIntegration.isAvailable();
    }

    public Map<String, Plugin> getDetectedPlugins() {

        return Collections.unmodifiableMap(
                detectedPlugins
        );
    }

    public void shutdown() {

        detectedPlugins.clear();

        coreProtectIntegration = null;
        vulcanIntegration = null;
        liteBansIntegration = null;

        plugin.getLogger().info(
                "Integration manager shut down."
        );
    }
}