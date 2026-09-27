package me.grate.casemanager.integration;

import me.grate.casemanager.CaseManager;

import java.lang.reflect.Method;
import java.util.UUID;

public final class FloodgateIntegration {

    private final CaseManager plugin;

    private Object floodgateApi;

    private Method isFloodgatePlayerMethod;
    private Method getPlayerMethod;

    private boolean available;

    public FloodgateIntegration(
            CaseManager plugin
    ) {
        this.plugin = plugin;
    }

    /**
     * Initializes the Floodgate API.
     *
     * Reflection is intentionally used so Floodgate remains
     * an optional dependency. CaseManager can therefore run
     * normally on servers without Floodgate.
     */
    public boolean initialize() {

        available = false;

        floodgateApi = null;
        isFloodgatePlayerMethod = null;
        getPlayerMethod = null;

        try {

            Class<?> apiClass =
                    Class.forName(
                            "org.geysermc.floodgate.api.FloodgateApi"
                    );

            Method getInstanceMethod =
                    apiClass.getMethod(
                            "getInstance"
                    );

            floodgateApi =
                    getInstanceMethod.invoke(null);

            if (floodgateApi == null) {

                plugin.getLogger().warning(
                        "Floodgate API returned null."
                );

                return false;
            }

            isFloodgatePlayerMethod =
                    apiClass.getMethod(
                            "isFloodgatePlayer",
                            UUID.class
                    );

            /*
             * getPlayer(UUID) exists in the current Floodgate
             * API and can be useful for future Bedrock metadata.
             *
             * It is optional here because CaseManager only
             * requires isFloodgatePlayer for platform detection.
             */
            try {

                getPlayerMethod =
                        apiClass.getMethod(
                                "getPlayer",
                                UUID.class
                        );

            } catch (NoSuchMethodException ignored) {

                getPlayerMethod = null;
            }

            available = true;

            plugin.getLogger().info(
                    "Floodgate API initialized successfully."
            );

            return true;

        } catch (ClassNotFoundException exception) {

            plugin.getLogger().info(
                    "Floodgate API was not found."
            );

            return false;

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Failed to initialize Floodgate API: " +
                            getRootMessage(exception)
            );

            return false;
        }
    }

    /**
     * Returns whether Floodgate was detected and its API
     * initialized successfully.
     */
    public boolean isAvailable() {

        return available;
    }

    /**
     * Checks whether a UUID belongs to a Floodgate Bedrock
     * player.
     *
     * Floodgate can perform this check even when the player
     * is not currently online.
     */
    public boolean isBedrockPlayer(
            UUID uuid
    ) {

        if (!available ||
                floodgateApi == null ||
                isFloodgatePlayerMethod == null ||
                uuid == null) {

            return false;
        }

        try {

            Object result =
                    isFloodgatePlayerMethod.invoke(
                            floodgateApi,
                            uuid
                    );

            return result instanceof Boolean &&
                    (Boolean) result;

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Failed to determine Floodgate player " +
                            "platform: " +
                            getRootMessage(exception)
            );

            return false;
        }
    }

    /**
     * Returns the platform of the supplied UUID.
     *
     * Possible values:
     *
     * BEDROCK
     * JAVA
     * UNKNOWN
     */
    public PlayerPlatform getPlayerPlatform(
            UUID uuid
    ) {

        if (uuid == null) {

            return PlayerPlatform.UNKNOWN;
        }

        if (!available) {

            return PlayerPlatform.UNKNOWN;
        }

        return isBedrockPlayer(uuid)
                ? PlayerPlatform.BEDROCK
                : PlayerPlatform.JAVA;
    }

    /**
     * Convenience method for checking whether a player is
     * a Java Edition player.
     */
    public boolean isJavaPlayer(
            UUID uuid
    ) {

        return getPlayerPlatform(uuid)
                == PlayerPlatform.JAVA;
    }

    /**
     * Returns the underlying Floodgate player object when
     * available.
     *
     * This method is intentionally returned as Object so
     * CaseManager does not need a compile-time Floodgate
     * dependency.
     *
     * Returns null when unavailable.
     */
    public Object getFloodgatePlayer(
            UUID uuid
    ) {

        if (!available ||
                floodgateApi == null ||
                getPlayerMethod == null ||
                uuid == null) {

            return null;
        }

        try {

            return getPlayerMethod.invoke(
                    floodgateApi,
                    uuid
            );

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Failed to retrieve Floodgate player: " +
                            getRootMessage(exception)
            );

            return null;
        }
    }

    public String getProviderName() {

        return "Floodgate";
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

    public enum PlayerPlatform {

        JAVA,

        BEDROCK,

        UNKNOWN
    }
}