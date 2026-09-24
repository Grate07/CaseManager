package me.grate.casemanager.integration;

import me.grate.casemanager.CaseManager;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.coreprotect.api.CoreProtectAction;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class CoreProtectIntegration {

    private static final int MIN_API_VERSION = 13;

    private static final int MAX_LOOKUP_LIMIT = 100;

    private static final List<Integer> INVESTIGATION_ACTIONS =
            List.of(
                    CoreProtectAction.BLOCK_BREAK.id(),
                    CoreProtectAction.BLOCK_PLACE.id(),
                    CoreProtectAction.INTERACTION.id(),
                    CoreProtectAction.ENTITY_KILL.id(),
                    CoreProtectAction.ENTITY_SPAWN.id()
            );

    private final CaseManager plugin;

    private CoreProtectAPI api;

    public CoreProtectIntegration(
            CaseManager plugin
    ) {
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

        int apiVersion =
                coreProtectApi.APIVersion();

        if (apiVersion < MIN_API_VERSION) {

            plugin.getLogger().warning(
                    "CoreProtect API version " +
                            apiVersion +
                            " is too old. API v" +
                            MIN_API_VERSION +
                            " or newer is required."
            );

            return false;
        }

        api = coreProtectApi;

        try {

            api.testAPI();

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "CoreProtect API test failed: " +
                            getRootMessage(exception)
            );

            api = null;

            return false;
        }

        plugin.getLogger().info(
                "CoreProtect API v" +
                        apiVersion +
                        " connected successfully."
        );

        return true;
    }

    public boolean isAvailable() {

        return api != null &&
                api.isEnabled() &&
                api.APIVersion() >= MIN_API_VERSION;
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

        int safeTime =
                Math.max(
                        1,
                        timeSeconds
                );

        int safeLimit =
                normalizeLimit(limit);

        return CompletableFuture.supplyAsync(() -> {

            List<String[]> rows =
                    api.performPartialLookup(
                            safeTime,
                            List.of(playerName),
                            null,
                            null,
                            null,
                            INVESTIGATION_ACTIONS,
                            0,
                            null,
                            0,
                            safeLimit
                    );

            return parseRows(rows);
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
                normalizeLimit(limit);

        return CompletableFuture.supplyAsync(() -> {

            List<String[]> rows =
                    api.performPartialLookup(
                            safeTime,
                            null,
                            null,
                            null,
                            null,
                            INVESTIGATION_ACTIONS,
                            safeRadius,
                            searchLocation,
                            0,
                            safeLimit
                    );

            return parseRows(rows);
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
                normalizeLimit(limit);

        return CompletableFuture.supplyAsync(() -> {

            List<String[]> rows =
                    api.performPartialLookup(
                            safeTime,
                            List.of(playerName),
                            null,
                            null,
                            null,
                            INVESTIGATION_ACTIONS,
                            safeRadius,
                            searchLocation,
                            0,
                            safeLimit
                    );

            return parseRows(rows);
        });
    }

    private List<CoreProtectRecord> parseRows(
            List<String[]> rows
    ) {

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

            try {

                CoreProtectAPI.ParseResult result =
                        api.parseResult(row);

                if (result == null) {
                    continue;
                }

                records.add(
                        mapResult(result)
                );

            } catch (Exception exception) {

                plugin.getLogger().warning(
                        "Failed to parse a CoreProtect lookup result: " +
                                getRootMessage(exception)
                );
            }
        }

        return records;
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

            if (record == null) {
                continue;
            }

            builder.append(
                    "\n\n#"
            ).append(
                    index
            );

            builder.append(
                    "\nPlayer: "
            ).append(
                    safeText(record.getPlayer())
            );

            builder.append(
                    "\nAction: "
            ).append(
                    safeText(record.getAction())
            );

            builder.append(
                    "\nAction ID: "
            ).append(
                    record.getActionId()
            );

            builder.append(
                    "\nLocation: "
            ).append(
                    safeText(record.getWorld())
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
                    "\nRolled Back: "
            ).append(
                    record.isRolledBack()
            );

            index++;
        }

        return builder.toString();
    }

    private int normalizeLimit(
            int limit
    ) {

        return Math.max(
                1,
                Math.min(
                        limit,
                        MAX_LOOKUP_LIMIT
                )
        );
    }

    private String safeText(
            String value
    ) {

        if (value == null ||
                value.isBlank()) {

            return "Unknown";
        }

        return value;
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
                    safeValue(player)
            );

            builder.append(
                    "\nAction: "
            ).append(
                    safeValue(action)
            );

            builder.append(
                    "\nAction ID: "
            ).append(
                    actionId
            );

            builder.append(
                    "\nWorld: "
            ).append(
                    safeValue(world)
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

        private static String safeValue(
                String value
        ) {

            if (value == null ||
                    value.isBlank()) {

                return "Unknown";
            }

            return value;
        }
    }
}