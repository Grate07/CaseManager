package me.grate.casemanager.integration;

import me.grate.casemanager.CaseManager;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.UUID;

public final class VulcanIntegration {

    private final CaseManager plugin;

    private Object vulcanApi;

    private Method apiMethod;

    private boolean available;

    public VulcanIntegration(
            CaseManager plugin
    ) {
        this.plugin = plugin;
    }

    public void initialize() {

        available = false;
        vulcanApi = null;
        apiMethod = null;

        Plugin vulcan =
                plugin.getServer()
                        .getPluginManager()
                        .getPlugin("Vulcan");

        if (vulcan == null) {

            plugin.getLogger().info(
                    "Vulcan not found. Vulcan integration disabled."
            );

            return;
        }

        if (!vulcan.isEnabled()) {

            plugin.getLogger().warning(
                    "Vulcan is installed but not enabled."
            );

            return;
        }

        try {

            Class<?> vulcanClass =
                    Class.forName(
                            "me.frep.vulcan.api.VulcanAPI"
                    );

            apiMethod =
                    findApiMethod(
                            vulcanClass
                    );

            if (apiMethod == null) {

                plugin.getLogger().warning(
                        "Vulcan API method could not be found."
                );

                return;
            }

            vulcanApi =
                    apiMethod.invoke(
                            null
                    );

            if (vulcanApi == null) {

                plugin.getLogger().warning(
                        "Vulcan returned a null API."
                );

                reset();

                return;
            }

            available = true;

            plugin.getLogger().info(
                    "Vulcan integration enabled."
            );

        } catch (ClassNotFoundException exception) {

            plugin.getLogger().warning(
                    "Vulcan API is not available. " +
                            "Make sure Vulcan's API is enabled."
            );

            reset();

        } catch (
                IllegalAccessException |
                InvocationTargetException exception
        ) {

            plugin.getLogger().warning(
                    "Failed to initialize Vulcan integration: " +
                            getRootMessage(exception)
            );

            reset();

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Unexpected Vulcan integration error: " +
                            exception.getMessage()
            );

            reset();
        }
    }

    public boolean isAvailable() {

        return available &&
                vulcanApi != null;
    }

    public Object getApi() {

        return vulcanApi;
    }

    private Method findApiMethod(
            Class<?> vulcanClass
    ) {

        for (Method method :
                vulcanClass.getMethods()) {

            if (!method.getName()
                    .equals("getInstance")) {

                continue;
            }

            if (method.getParameterCount() != 0) {
                continue;
            }

            return method;
        }

        for (Method method :
                vulcanClass.getMethods()) {

            if (!method.getName()
                    .equals("getAPI")) {

                continue;
            }

            if (method.getParameterCount() != 0) {
                continue;
            }

            return method;
        }

        return null;
    }

    public int getViolationLevel(
            UUID playerUuid
    ) {

        if (!isAvailable() ||
                playerUuid == null) {

            return 0;
        }

        /*
         * Vulcan API versions can expose violation information
         * through different methods. We therefore inspect the
         * available API methods instead of directly depending
         * on one implementation.
         */

        try {

            Method method =
                    findViolationMethod();

            if (method == null) {
                return 0;
            }

            Object result;

            Class<?>[] parameters =
                    method.getParameterTypes();

            if (parameters.length == 1 &&
                    parameters[0] == UUID.class) {

                result =
                        method.invoke(
                                vulcanApi,
                                playerUuid
                        );

            } else {

                return 0;
            }

            if (result instanceof Number number) {

                return number.intValue();
            }

            return 0;

        } catch (
                IllegalAccessException |
                InvocationTargetException exception
        ) {

            plugin.getLogger().warning(
                    "Failed to retrieve Vulcan violation level: " +
                            getRootMessage(exception)
            );

            return 0;

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Unexpected Vulcan violation lookup error: " +
                            exception.getMessage()
            );

            return 0;
        }
    }

    private Method findViolationMethod() {

        if (vulcanApi == null) {
            return null;
        }

        for (Method method :
                vulcanApi.getClass()
                        .getMethods()) {

            String name =
                    method.getName()
                            .toLowerCase();

            if (!name.contains("violation") &&
                    !name.contains("vl")) {

                continue;
            }

            if (method.getParameterCount() != 1) {
                continue;
            }

            if (method.getParameterTypes()[0]
                    != UUID.class) {

                continue;
            }

            if (!Number.class.isAssignableFrom(
                    method.getReturnType()
            ) &&
                    method.getReturnType() != int.class &&
                    method.getReturnType() != long.class) {

                continue;
            }

            return method;
        }

        return null;
    }
    public String getPlayerProfile(
            UUID playerUuid
    ) {

        if (!isAvailable() ||
                playerUuid == null) {

            return null;
        }

        try {

            Method profileMethod =
                    findProfileMethod();

            if (profileMethod == null) {
                return null;
            }

            Object result =
                    profileMethod.invoke(
                            vulcanApi,
                            playerUuid
                    );

            if (result == null) {
                return null;
            }

            return result.toString();

        } catch (
                IllegalAccessException |
                InvocationTargetException exception
        ) {

            plugin.getLogger().warning(
                    "Failed to retrieve Vulcan profile: " +
                            getRootMessage(exception)
            );

            return null;

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Unexpected Vulcan profile error: " +
                            exception.getMessage()
            );

            return null;
        }
    }

    private Method findProfileMethod() {

        if (vulcanApi == null) {
            return null;
        }

        for (Method method :
                vulcanApi.getClass()
                        .getMethods()) {

            String name =
                    method.getName()
                            .toLowerCase();

            if (!name.contains("profile")) {
                continue;
            }

            if (method.getParameterCount() != 1) {
                continue;
            }

            if (method.getParameterTypes()[0]
                    != UUID.class) {

                continue;
            }

            return method;
        }

        return null;
    }

    public String createEvidenceSummary(
            UUID playerUuid,
            String playerName
    ) {

        if (!isAvailable()) {

            return
                    "Vulcan integration is not available.";
        }

        StringBuilder builder =
                new StringBuilder();

        builder.append(
                "Vulcan Anti-Cheat Information"
        );

        if (playerName != null &&
                !playerName.isBlank()) {

            builder.append(
                    "\nPlayer: "
            ).append(playerName);
        }

        if (playerUuid != null) {

            builder.append(
                    "\nUUID: "
            ).append(playerUuid);
        }

        int violationLevel =
                getViolationLevel(
                        playerUuid
                );

        builder.append(
                "\nViolation Level: "
        ).append(violationLevel);

        String profile =
                getPlayerProfile(
                        playerUuid
                );

        if (profile != null &&
                !profile.isBlank()) {

            builder.append(
                    "\nProfile: "
            ).append(profile);
        }

        return builder.toString();
    }

    public boolean hasPlayerData(
            UUID playerUuid
    ) {

        if (!isAvailable() ||
                playerUuid == null) {

            return false;
        }

        return getViolationLevel(
                playerUuid
        ) > 0;
    }

    private String getRootMessage(
            Throwable throwable
    ) {

        Throwable current =
                throwable;

        while (current.getCause() != null) {

            current =
                    current.getCause();
        }

        String message =
                current.getMessage();

        return message == null
                ? current.getClass()
                        .getSimpleName()
                : message;
    }

    private void reset() {

        available = false;

        vulcanApi = null;

        apiMethod = null;
    }
    /**
     * Small immutable object that represents Vulcan information
     * which CaseManager can later attach to a case.
     */
    public static final class VulcanEvidence {

        private final UUID playerUuid;
        private final String playerName;
        private final int violationLevel;
        private final String profile;

        public VulcanEvidence(
                UUID playerUuid,
                String playerName,
                int violationLevel,
                String profile
        ) {

            this.playerUuid =
                    playerUuid;

            this.playerName =
                    playerName;

            this.violationLevel =
                    violationLevel;

            this.profile =
                    profile;
        }

        public UUID getPlayerUuid() {

            return playerUuid;
        }

        public String getPlayerName() {

            return playerName;
        }

        public int getViolationLevel() {

            return violationLevel;
        }

        public String getProfile() {

            return profile;
        }

        public String toEvidenceText() {

            StringBuilder builder =
                    new StringBuilder();

            builder.append(
                    "Vulcan Anti-Cheat Information"
            );

            if (playerName != null &&
                    !playerName.isBlank()) {

                builder.append(
                        "\nPlayer: "
                ).append(playerName);
            }

            if (playerUuid != null) {

                builder.append(
                        "\nUUID: "
                ).append(playerUuid);
            }

            builder.append(
                    "\nViolation Level: "
            ).append(violationLevel);

            if (profile != null &&
                    !profile.isBlank()) {

                builder.append(
                        "\nProfile: "
                ).append(profile);
            }

            return builder.toString();
        }
    }
}