package me.grate.casemanager.integration;

import me.grate.casemanager.CaseManager;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CoreProtectIntegration {

    private final CaseManager plugin;

    private CoreProtectAPI api;

    public CoreProtectIntegration(CaseManager plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {

        api = null;

        Plugin externalPlugin =
                plugin.getServer()
                        .getPluginManager()
                        .getPlugin("CoreProtect");

        if (!(externalPlugin instanceof CoreProtect coreProtect)) {

            plugin.getLogger().info(
                    "CoreProtect API plugin was not found."
            );

            return false;
        }

        if (!externalPlugin.isEnabled()) {

            plugin.getLogger().warning(
                    "CoreProtect is installed but not enabled."
            );

            return false;
        }

        CoreProtectAPI coreProtectApi =
                coreProtect.getAPI();

        if (coreProtectApi == null) {

            plugin.getLogger().warning(
                    "CoreProtect returned a null API."
            );

            return false;
        }

        if (!coreProtectApi.isEnabled()) {

            plugin.getLogger().warning(
                    "CoreProtect API is disabled in CoreProtect configuration."
            );

            return false;
        }

        if (coreProtectApi.APIVersion() < 13) {

            plugin.getLogger().warning(
                    "CoreProtect API version " +
                            coreProtectApi.APIVersion() +
                            " is too old. API v13 or newer is required."
            );

            return false;
        }

        api = coreProtectApi;

        plugin.getLogger().info(
                "CoreProtect API v" +
                        api.APIVersion() +
                        " connected."
        );

        return true;
    }

    public boolean isAvailable() {

        return api != null &&
                api.isEnabled() &&
                api.APIVersion() >= 13;
    }

    public CoreProtectAPI getApi() {

        return api;
    }

    public CompletableFuture<List<CoreProtectRecord>> lookupPlayerHistory(
            String playerName,
            int timeSeconds,
            int limit
    ) {

        if (!isAvailable() ||
                playerName == null ||
                playerName.isBlank()) {

            return CompletableFuture.completedFuture(
                    Collections.emptyList()
            );
        }

        int safeLimit =
                Math.max(
                        1,
                        Math.min(
                                limit,
                                100
                        )
                );

        int safeTime =
                Math.max(
                        1,
                        timeSeconds
                );

        return CompletableFuture.supplyAsync(() -> {

            List<String[]> rows =
                    api.performPartialLookup(
                            safeTime,
                            List.of(playerName),
                            null,
                            null,
                            null,
                            null,
                            0,
                            null,
                            0,
                            safeLimit
                    );

            if (rows == null ||
                    rows.isEmpty()) {

                return Collections.emptyList();
            }

            List<CoreProtectRecord> records =
                    new ArrayList<>();

            for (String[] row : rows) {

                if (row == null) {
                    continue;
                }

                CoreProtectAPI.ParseResult result =
                        api.parseResult(row);

                if (result == null) {
                    continue;
                }

                records.add(
                        mapResult(result)
                );
            }

            return records;
        });
    }

    public CompletableFuture<List<CoreProtectRecord>> lookupLocation(
            Location location,
            int timeSeconds,
            int radius,
            int limit
    ) {

        if (!isAvailable() ||
                location == null ||
                location.getWorld() == null) {

            return CompletableFuture.completedFuture(
                    Collections.emptyList()
            );
        }

        Location searchLocation =
                location.clone();

        int safeTime =
                Math.max(
                        1,
                        timeSeconds
                );

        int safeRadius =
                Math.max(
                        0,
                        radius
                );

        int safeLimit =
                Math.max(
                        1,
                        Math.min(
                                limit,
                                100
                        )
                );

        return CompletableFuture.supplyAsync(() -> {

            List<String[]> rows =
                    api.performPartialLookup(
                            safeTime,
                            null,
                            null,
                            null,
                            null,
                            null,
                            safeRadius,
                            searchLocation,
                            0,
                            safeLimit
                    );

            if (rows == null ||
                    rows.isEmpty()) {

                return Collections.emptyList();
            }

            List<CoreProtectRecord> records =
                    new ArrayList<>();

            for (String[] row : rows) {

                if (row == null) {
                    continue;
                }

                CoreProtectAPI.ParseResult result =
                        api.parseResult(row);

                if (result == null) {
                    continue;
                }

                records.add(
                        mapResult(result)
                );
            }

            return records;
        });
    }
    public CompletableFuture<List<CoreProtectRecord>> lookupPlayerAtLocation(
            String playerName,
            Location location,
            int timeSeconds,
            int radius,
            int limit
    ) {

        if (!isAvailable() ||
                playerName == null ||
                playerName.isBlank() ||
                location == null ||
                location.getWorld() == null) {

            return CompletableFuture.completedFuture(
                    Collections.emptyList()
            );
        }

        Location searchLocation =
                location.clone();

        int safeTime =
                Math.max(
                        1,
                        timeSeconds
                );

        int safeRadius =
                Math.max(
                        0,
                        radius
                );

        int safeLimit =
                Math.max(
                        1,
                        Math.min(
                                limit,
                                100
                        )
                );

        return CompletableFuture.supplyAsync(() -> {

            List<String[]> rows =
                    api.performPartialLookup(
                            safeTime,
                            List.of(playerName),
                            null,
                            null,
                            null,
                            null,
                            safeRadius,
                            searchLocation,
                            0,
                            safeLimit
                    );

            if (rows == null ||
                    rows.isEmpty()) {

                return Collections.emptyList();
            }

            List<CoreProtectRecord> records =
                    new ArrayList<>();

            for (String[] row : rows) {

                if (row == null) {
                    continue;
                }

                CoreProtectAPI.ParseResult result =
                        api.parseResult(row);

                if (result == null) {
                    continue;
                }

                records.add(
                        mapResult(result)
                );
            }

            return records;
        });
    }

    private CoreProtectRecord mapResult(
            CoreProtectAPI.ParseResult result
    ) {

        String player =
                result.getPlayer();

        String action =
                result.getActionString();

        int actionId =
                result.getActionId();

        String world =
                result.worldName();

        int x =
                result.getX();

        int y =
                result.getY();

        int z =
                result.getZ();

        long timestamp =
                result.getTimestamp();

        String material = null;

        if (result.getType() != null) {

            material =
                    result.getType()
                            .name();
        }

        String entityType = null;

        if (result.getEntityType() != null) {

            entityType =
                    result.getEntityType()
                            .name();
        }

        boolean rolledBack =
                result.isRolledBack();

        return new CoreProtectRecord(
                player,
                action,
                actionId,
                world,
                x,
                y,
                z,
                timestamp,
                material,
                entityType,
                rolledBack
        );
    }

    public String createEvidenceSummary(
            List<CoreProtectRecord> records
    ) {

        if (records == null ||
                records.isEmpty()) {

            return "CoreProtect: No matching history found.";
        }

        StringBuilder builder =
                new StringBuilder();

        builder.append(
                "CoreProtect Investigation Results"
        );

        builder.append(
                "\nRecords: "
        ).append(
                records.size()
        );

        int index = 1;

        for (
                CoreProtectRecord record :
                records
        ) {

            builder.append(
                    "\n\n#"
            ).append(index);

            builder.append(
                    "\nPlayer: "
            ).append(
                    record.getPlayer()
            );

            builder.append(
                    "\nAction: "
            ).append(
                    record.getAction()
            );

            builder.append(
                    "\nLocation: "
            ).append(
                    record.getWorld()
            ).append(
                    " "
            ).append(
                    record.getX()
            ).append(
                    ", "
            ).append(
                    record.getY()
            ).append(
                    ", "
            ).append(
                    record.getZ()
            );

            if (record.getMaterial() != null) {

                builder.append(
                        "\nMaterial: "
                ).append(
                        record.getMaterial()
                );
            }

            if (record.getEntityType() != null) {

                builder.append(
                        "\nEntity: "
                ).append(
                        record.getEntityType()
                );
            }

            builder.append(
                    "\nTimestamp: "
            ).append(
                    record.getTimestamp()
            );

            builder.append(
                    "\nRolled back: "
            ).append(
                    record.isRolledBack()
            );

            index++;
        }

        return builder.toString();
    }
    public static final class CoreProtectRecord {

        private final String player;
        private final String action;
        private final int actionId;

        private final String world;

        private final int x;
        private final int y;
        private final int z;

        private final long timestamp;

        private final String material;
        private final String entityType;

        private final boolean rolledBack;

        public CoreProtectRecord(
                String player,
                String action,
                int actionId,
                String world,
                int x,
                int y,
                int z,
                long timestamp,
                String material,
                String entityType,
                boolean rolledBack
        ) {

            this.player =
                    player;

            this.action =
                    action;

            this.actionId =
                    actionId;

            this.world =
                    world;

            this.x =
                    x;

            this.y =
                    y;

            this.z =
                    z;

            this.timestamp =
                    timestamp;

            this.material =
                    material;

            this.entityType =
                    entityType;

            this.rolledBack =
                    rolledBack;
        }

        public String getPlayer() {

            return player;
        }

        public String getAction() {

            return action;
        }

        public int getActionId() {

            return actionId;
        }

        public String getWorld() {

            return world;
        }

        public int getX() {

            return x;
        }

        public int getY() {

            return y;
        }

        public int getZ() {

            return z;
        }

        public long getTimestamp() {

            return timestamp;
        }

        public String getMaterial() {

            return material;
        }

        public String getEntityType() {

            return entityType;
        }

        public boolean isRolledBack() {

            return rolledBack;
        }

        public String toEvidenceText() {

            StringBuilder builder =
                    new StringBuilder();

            builder.append(
                    "CoreProtect Record"
            );

            builder.append(
                    "\nPlayer: "
            ).append(
                    player
            );

            builder.append(
                    "\nAction: "
            ).append(
                    action
            );

            builder.append(
                    "\nAction ID: "
            ).append(
                    actionId
            );

            builder.append(
                    "\nWorld: "
            ).append(
                    world
            );

            builder.append(
                    "\nCoordinates: "
            ).append(
                    x
            ).append(
                    ", "
            ).append(
                    y
            ).append(
                    ", "
            ).append(
                    z
            );

            if (material != null) {

                builder.append(
                        "\nMaterial: "
                ).append(
                        material
                );
            }

            if (entityType != null) {

                builder.append(
                        "\nEntity Type: "
                ).append(
                        entityType
                );
            }

            builder.append(
                    "\nTimestamp: "
            ).append(
                    timestamp
            );

            builder.append(
                    "\nRolled Back: "
            ).append(
                    rolledBack
            );

            return builder.toString();
        }
    }
}