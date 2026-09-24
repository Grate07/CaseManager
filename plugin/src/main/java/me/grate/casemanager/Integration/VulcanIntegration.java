package me.grate.casemanager.integration;

import me.grate.casemanager.CaseManager;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

public final class VulcanIntegration {

    private final CaseManager plugin;

    private Plugin vulcanPlugin;

    private boolean available;

    public VulcanIntegration(
            CaseManager plugin
    ) {
        this.plugin = plugin;
    }

    public void initialize() {

        reset();

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

        vulcanPlugin =
                vulcan;

        /*
         * Vulcan's official documentation requires
         * enable-api: true for plugins using its Developer API.
         *
         * The actual Developer API classes are distributed
         * separately by Vulcan and are not publicly documented
         * on the official wiki.
         *
         * Therefore we do not guess API methods here.
         */
        available =
                isVulcanApiEnabled();

        if (available) {

            plugin.getLogger().info(
                    "Vulcan detected and its Developer API " +
                            "is enabled."
            );

        } else {

            plugin.getLogger().warning(
                    "Vulcan is installed, but its Developer API " +
                            "is not enabled. Set enable-api: true " +
                            "in Vulcan's configuration."
            );
        }
    }

    public boolean isAvailable() {

        return available &&
                vulcanPlugin != null &&
                vulcanPlugin.isEnabled();
    }

    public Plugin getPlugin() {

        return vulcanPlugin;
    }

    /*
     * Vulcan's official API documentation states that
     * enable-api: true is required.
     *
     * We intentionally do not attempt to inspect or modify
     * Vulcan's internal configuration through reflection.
     *
     * If the API library is added to the build later, this
     * method can be replaced with the official API initialization.
     */
    private boolean isVulcanApiEnabled() {

        /*
         * We cannot reliably determine the value of
         * enable-api without using Vulcan's private
         * implementation or its separately distributed
         * Developer API.
         *
         * Return false until the official API dependency
         * is installed and wired into CaseManager.
         */
        return false;
    }

    public Object getApi() {

        /*
         * No undocumented API object is exposed.
         *
         * This deliberately returns null until the official
         * Vulcan Developer API is integrated.
         */
        return null;
    }

    public int getViolationLevel(
            UUID playerUuid
    ) {

        /*
         * Not available until the official Vulcan Developer
         * API is installed and its documented methods are wired.
         */
        return 0;
    }

    public String getPlayerProfile(
            UUID playerUuid
    ) {

        /*
         * Not available until the official Vulcan Developer
         * API is installed and its documented methods are wired.
         */
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
            ).append(
                    playerName
            );
        }

        if (playerUuid != null) {

            builder.append(
                    "\nUUID: "
            ).append(
                    playerUuid
            );
        }

        builder.append(
                "\nDeveloper API data collection is " +
                        "not yet configured."
        );

        return builder.toString();
    }

    public boolean hasPlayerData(
            UUID playerUuid
    ) {

        return false;
    }

    private void reset() {

        available = false;

        vulcanPlugin = null;
    }

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
                ).append(
                        playerName
                );
            }

            if (playerUuid != null) {

                builder.append(
                        "\nUUID: "
                ).append(
                        playerUuid
                );
            }

            builder.append(
                    "\nViolation Level: "
            ).append(
                    violationLevel
            );

            if (profile != null &&
                    !profile.isBlank()) {

                builder.append(
                        "\nProfile: "
                ).append(
                        profile
                );
            }

            return builder.toString();
        }
    }
}