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

        coreProtectIntegration = null;
        vulcanIntegration = null;
        liteBansIntegration = null;

        initializeCoreProtect();
        initializeVulcan();
        initializeLiteBans();

        plugin.getLogger().info(
                "Integration initialization complete."
        );
    }

    private void initializeCoreProtect() {

        boolean enabled =
                plugin.getConfig().getBoolean(
                        "integrations.coreprotect.enabled",
                        true
                );

        if (!enabled) {

            plugin.getLogger().info(
                    "CoreProtect integration is disabled by configuration."
            );

            return;
        }

        if (!detectPlugin("CoreProtect")) {
            return;
        }

        try {

            coreProtectIntegration =
                    new CoreProtectIntegration(
                            plugin
                    );

            boolean initialized =
                    coreProtectIntegration.initialize();

            if (initialized) {

                plugin.getLogger().info(
                        "CoreProtect integration enabled."
                );

            } else {

                plugin.getLogger().warning(
                        "CoreProtect was detected, but its API is unavailable."
                );

                coreProtectIntegration = null;
            }

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Failed to initialize CoreProtect integration: " +
                            getExceptionMessage(exception)
            );

            coreProtectIntegration = null;
        }
    }

    private void initializeVulcan() {

        boolean enabled =
                plugin.getConfig().getBoolean(
                        "integrations.vulcan.enabled",
                        true
                );

        if (!enabled) {

            plugin.getLogger().info(
                    "Vulcan integration is disabled by configuration."
            );

            return;
        }

        if (!detectPlugin("Vulcan")) {
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

                vulcanIntegration = null;
            }

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Failed to initialize Vulcan integration: " +
                            getExceptionMessage(exception)
            );

            vulcanIntegration = null;
        }
    }

    private void initializeLiteBans() {

        boolean enabled =
                plugin.getConfig().getBoolean(
                        "integrations.litebans.enabled",
                        true
                );

        if (!enabled) {

            plugin.getLogger().info(
                    "LiteBans integration is disabled by configuration."
            );

            return;
        }

        if (!detectPlugin("LiteBans")) {
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

                liteBansIntegration = null;
            }

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Failed to initialize LiteBans integration: " +
                            getExceptionMessage(exception)
            );

            liteBansIntegration = null;
        }
    }

    private boolean detectPlugin(
            String pluginName
    ) {

        if (pluginName == null ||
                pluginName.isBlank()) {

            return false;
        }

        Plugin externalPlugin =
                plugin.getServer()
                        .getPluginManager()
                        .getPlugin(pluginName);

        if (externalPlugin == null) {

            plugin.getLogger().info(
                    pluginName +
                            " not found. Integration unavailable."
            );

            return false;
        }

        if (!externalPlugin.isEnabled()) {

            plugin.getLogger().warning(
                    pluginName +
                            " is installed but not enabled."
            );

            return false;
        }

        detectedPlugins.put(
                pluginName.toLowerCase(),
                externalPlugin
        );

        plugin.getLogger().info(
                pluginName +
                        " detected."
        );

        return true;
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

        return isPluginDetected(
                pluginName
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

    private String getExceptionMessage(
            Throwable throwable
    ) {

        if (throwable == null) {
            return "Unknown error";
        }

        Throwable current =
                throwable;

        while (current.getCause() != null) {
            current = current.getCause();
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
}