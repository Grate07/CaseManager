package me.grate.casemanager.integration;

import me.grate.casemanager.CaseManager;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CoreProtectIntegration {

    private final CaseManager plugin;

    private Object coreProtectApi;

    private Method performLookupMethod;
    private Method parseResultMethod;

    private boolean available;

    public CoreProtectIntegration(
            CaseManager plugin
    ) {
        this.plugin = plugin;
    }

    public void initialize() {

        available = false;
        coreProtectApi = null;
        performLookupMethod = null;
        parseResultMethod = null;

        Plugin coreProtect =
                plugin.getServer()
                        .getPluginManager()
                        .getPlugin("CoreProtect");

        if (coreProtect == null) {

            plugin.getLogger().info(
                    "CoreProtect not found. CoreProtect integration disabled."
            );

            return;
        }

        if (!coreProtect.isEnabled()) {

            plugin.getLogger().warning(
                    "CoreProtect is installed but not enabled."
            );

            return;
        }

        try {

            Class<?> coreProtectClass =
                    Class.forName(
                            "net.coreprotect.CoreProtect"
                    );

            if (!coreProtectClass.isInstance(
                    coreProtect
            )) {

                plugin.getLogger().warning(
                        "Detected CoreProtect plugin does not expose the expected API."
                );

                return;
            }

            Method getApiMethod =
                    coreProtectClass.getMethod(
                            "getAPI"
                    );

            coreProtectApi =
                    getApiMethod.invoke(
                            coreProtect
                    );

            if (coreProtectApi == null) {

                plugin.getLogger().warning(
                        "CoreProtect returned a null API."
                );

                return;
            }

            Class<?> apiClass =
                    coreProtectApi.getClass();

            Method apiVersionMethod =
                    apiClass.getMethod(
                            "APIVersion"
                    );

            Object versionObject =
                    apiVersionMethod.invoke(
                            coreProtectApi
                    );

            int apiVersion =
                    versionObject instanceof Number
                            ? ((Number) versionObject).intValue()
                            : -1;

            if (apiVersion < 13) {

                plugin.getLogger().warning(
                        "CoreProtect API version " +
                                apiVersion +
                                " detected. API version 13 or newer is required."
                );

                reset();

                return;
            }

            Method enabledMethod =
                    apiClass.getMethod(
                            "isEnabled"
                    );

            Object enabledObject =
                    enabledMethod.invoke(
                            coreProtectApi
                    );

            boolean apiEnabled =
                    enabledObject instanceof Boolean &&
                            (Boolean) enabledObject;

            if (!apiEnabled) {

                plugin.getLogger().warning(
                        "CoreProtect API is disabled."
                );

                reset();

                return;
            }

            performLookupMethod =
                    findPerformLookupMethod(
                            apiClass
                    );

            parseResultMethod =
                    findParseResultMethod(
                            apiClass
                    );

            if (performLookupMethod == null) {

                plugin.getLogger().warning(
                        "Could not find CoreProtect performLookup API method."
                );

                reset();

                return;
            }

            if (parseResultMethod == null) {

                plugin.getLogger().warning(
                        "Could not find CoreProtect parseResult API method."
                );

                reset();

                return;
            }

            available = true;

            plugin.getLogger().info(
                    "CoreProtect integration enabled. API version " +
                            apiVersion +
                            "."
            );

        } catch (ClassNotFoundException exception) {

            plugin.getLogger().warning(
                    "CoreProtect API classes were not found."
            );

            reset();

        } catch (
                NoSuchMethodException |
                IllegalAccessException |
                InvocationTargetException exception
        ) {

            plugin.getLogger().warning(
                    "Failed to initialize CoreProtect integration: " +
                            exception.getMessage()
            );

            reset();
        }
    }

    public boolean isAvailable() {

        return available &&
                coreProtectApi != null &&
                performLookupMethod != null &&
                parseResultMethod != null;
    }

    public Object getApi() {

        return coreProtectApi;
    }

    public List<CoreProtectRecord> lookupPlayerHistory(
            String playerName,
            int timeSeconds,
            int limit
    ) {

        if (!isAvailable()) {
            return Collections.emptyList();
        }

        if (playerName == null ||
                playerName.isBlank()) {

            return Collections.emptyList();
        }

        if (timeSeconds <= 0) {
            timeSeconds = 3600;
        }

        if (limit <= 0) {
            limit = 100;
        }

        try {

            List<?> rows =
                    invokePerformLookup(
                            timeSeconds,
                            playerName
                    );

            if (rows == null ||
                    rows.isEmpty()) {

                return Collections.emptyList();
            }

            List<CoreProtectRecord> records =
                    new ArrayList<>();

            int count = 0;

            for (Object rowObject : rows) {

                if (rowObject == null) {
                    continue;
                }

                if (!(rowObject instanceof String[] row)) {
                    continue;
                }

                CoreProtectRecord record =
                        parseRow(row);

                if (record == null) {
                    continue;
                }

                records.add(record);

                count++;

                if (count >= limit) {
                    break;
                }
            }

            return records;

        } catch (
                IllegalAccessException |
                InvocationTargetException exception
        ) {

            plugin.getLogger().warning(
                    "CoreProtect lookup failed: " +
                            getRootMessage(exception)
            );

            return Collections.emptyList();

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Unexpected CoreProtect lookup error: " +
                            exception.getMessage()
            );

            return Collections.emptyList();
        }
    }

    public List<CoreProtectRecord> lookupLocation(
            Location location,
            int timeSeconds,
            int radius,
            int limit
    ) {

        if (!isAvailable()) {
            return Collections.emptyList();
        }

        if (location == null ||
                location.getWorld() == null) {

            return Collections.emptyList();
        }

        if (timeSeconds <= 0) {
            timeSeconds = 3600;
        }

        if (radius < 0) {
            radius = 0;
        }

        if (limit <= 0) {
            limit = 100;
        }

        try {

            List<?> rows =
                    invokePerformLookup(
                            timeSeconds,
                            null,
                            radius,
                            location
                    );

            if (rows == null ||
                    rows.isEmpty()) {

                return Collections.emptyList();
            }

            List<CoreProtectRecord> records =
                    new ArrayList<>();

            int count = 0;

            for (Object rowObject : rows) {

                if (rowObject == null) {
                    continue;
                }

                if (!(rowObject instanceof String[] row)) {
                    continue;
                }

                CoreProtectRecord record =
                        parseRow(row);

                if (record == null) {
                    continue;
                }

                records.add(record);

                count++;

                if (count >= limit) {
                    break;
                }
            }

            return records;

        } catch (
                IllegalAccessException |
                InvocationTargetException exception
        ) {

            plugin.getLogger().warning(
                    "CoreProtect location lookup failed: " +
                            getRootMessage(exception)
            );

            return Collections.emptyList();

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Unexpected CoreProtect lookup error: " +
                            exception.getMessage()
            );

            return Collections.emptyList();
        }
    }
    private CoreProtectRecord parseRow(
            String[] row
    ) {

        try {

            Object parseResult =
                    parseResultMethod.invoke(
                            coreProtectApi,
                            (Object) row
                    );

            if (parseResult == null) {
                return null;
            }

            Class<?> resultClass =
                    parseResult.getClass();

            String player =
                    readString(
                            resultClass,
                            parseResult,
                            "getPlayer"
                    );

            String action =
                    readString(
                            resultClass,
                            parseResult,
                            "getActionString"
                    );

            String world =
                    readString(
                            resultClass,
                            parseResult,
                            "worldName"
                    );

            Integer x =
                    readInteger(
                            resultClass,
                            parseResult,
                            "getX"
                    );

            Integer y =
                    readInteger(
                            resultClass,
                            parseResult,
                            "getY"
                    );

            Integer z =
                    readInteger(
                            resultClass,
                            parseResult,
                            "getZ"
                    );

            Long timestamp =
                    readLong(
                            resultClass,
                            parseResult,
                            "getTimestamp"
                    );

            Integer actionId =
                    readInteger(
                            resultClass,
                            parseResult,
                            "getActionId"
                    );

            String material =
                    readMaterial(
                            resultClass,
                            parseResult
                    );

            String entityType =
                    readEntityType(
                            resultClass,
                            parseResult
                    );

            Boolean rolledBack =
                    readBoolean(
                            resultClass,
                            parseResult,
                            "isRolledBack"
                    );

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

        } catch (
                IllegalAccessException |
                InvocationTargetException exception
        ) {

            plugin.getLogger().warning(
                    "Failed to parse CoreProtect result: " +
                            getRootMessage(exception)
            );

            return null;

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Unexpected CoreProtect parsing error: " +
                            exception.getMessage()
            );

            return null;
        }
    }

    private Method findPerformLookupMethod(
            Class<?> apiClass
    ) {

        for (Method method :
                apiClass.getMethods()) {

            if (!method.getName()
                    .equals("performLookup")) {

                continue;
            }

            Class<?>[] parameters =
                    method.getParameterTypes();

            if (parameters.length != 8) {
                continue;
            }

            if (parameters[0] != int.class) {
                continue;
            }

            if (!List.class.isAssignableFrom(
                    parameters[1]
            )) {
                continue;
            }

            if (!List.class.isAssignableFrom(
                    parameters[2]
            )) {
                continue;
            }

            if (!List.class.isAssignableFrom(
                    parameters[3]
            )) {
                continue;
            }

            if (!List.class.isAssignableFrom(
                    parameters[4]
            )) {
                continue;
            }

            if (!List.class.isAssignableFrom(
                    parameters[5]
            )) {
                continue;
            }

            if (parameters[6] != int.class) {
                continue;
            }

            if (!Location.class.isAssignableFrom(
                    parameters[7]
            )) {
                continue;
            }

            return method;
        }

        return null;
    }

    private Method findParseResultMethod(
            Class<?> apiClass
    ) {

        for (Method method :
                apiClass.getMethods()) {

            if (!method.getName()
                    .equals("parseResult")) {

                continue;
            }

            Class<?>[] parameters =
                    method.getParameterTypes();

            if (parameters.length != 1) {
                continue;
            }

            if (parameters[0] != String[].class) {
                continue;
            }

            return method;
        }

        return null;
    }

    private List<?> invokePerformLookup(
            int timeSeconds,
            String playerName
    )
            throws InvocationTargetException,
            IllegalAccessException {

        List<String> users =
                playerName == null
                        ? null
                        : Collections.singletonList(
                                playerName
                        );

        return invokePerformLookup(
                timeSeconds,
                users,
                null,
                0,
                null
        );
    }

    private List<?> invokePerformLookup(
            int timeSeconds,
            String playerName,
            int radius,
            Location location
    )
            throws InvocationTargetException,
            IllegalAccessException {

        List<String> users =
                playerName == null
                        ? null
                        : Collections.singletonList(
                                playerName
                        );

        return invokePerformLookup(
                timeSeconds,
                users,
                null,
                radius,
                location
        );
    }

    private List<?> invokePerformLookup(
            int timeSeconds,
            List<String> users,
            List<String> excludedUsers,
            int radius,
            Location location
    )
            throws InvocationTargetException,
            IllegalAccessException {

        Object result =
                performLookupMethod.invoke(
                        coreProtectApi,
                        timeSeconds,
                        users,
                        excludedUsers,
                        null,
                        null,
                        null,
                        radius,
                        location
                );

        if (result instanceof List<?> list) {
            return list;
        }

        return Collections.emptyList();
    }

    private String readString(
            Class<?> resultClass,
            Object result,
            String methodName
    )
            throws Exception {

        Method method =
                resultClass.getMethod(
                        methodName
                );

        Object value =
                method.invoke(
                        result
                );

        return value == null
                ? null
                : String.valueOf(value);
    }

    private Integer readInteger(
            Class<?> resultClass,
            Object result,
            String methodName
    )
            throws Exception {

        Method method =
                resultClass.getMethod(
                        methodName
                );

        Object value =
                method.invoke(
                        result
                );

        if (value instanceof Number number) {
            return number.intValue();
        }

        return null;
    }

    private Long readLong(
            Class<?> resultClass,
            Object result,
            String methodName
    )
            throws Exception {

        Method method =
                resultClass.getMethod(
                        methodName
                );

        Object value =
                method.invoke(
                        result
                );

        if (value instanceof Number number) {
            return number.longValue();
        }

        return null;
    }

    private Boolean readBoolean(
            Class<?> resultClass,
            Object result,
            String methodName
    )
            throws Exception {

        Method method =
                resultClass.getMethod(
                        methodName
                );

        Object value =
                method.invoke(
                        result
                );

        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        return null;
    }

    private String readMaterial(
            Class<?> resultClass,
            Object result
    ) {

        try {

            Method method =
                    resultClass.getMethod(
                            "getType"
                    );

            Object value =
                    method.invoke(
                            result
                    );

            return value == null
                    ? null
                    : value.toString();

        } catch (Exception ignored) {

            return null;
        }
    }

    private String readEntityType(
            Class<?> resultClass,
            Object result
    ) {

        try {

            Method method =
                    resultClass.getMethod(
                            "getEntityType"
                    );

            Object value =
                    method.invoke(
                            result
                    );

            return value == null
                    ? null
                    : value.toString();

        } catch (Exception ignored) {

            return null;
        }
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

        coreProtectApi = null;

        performLookupMethod = null;

        parseResultMethod = null;
    }
    public static final class CoreProtectRecord {

        private final String player;
        private final String action;
        private final Integer actionId;

        private final String world;

        private final Integer x;
        private final Integer y;
        private final Integer z;

        private final Long timestamp;

        private final String material;
        private final String entityType;

        private final Boolean rolledBack;

        public CoreProtectRecord(
                String player,
                String action,
                Integer actionId,
                String world,
                Integer x,
                Integer y,
                Integer z,
                Long timestamp,
                String material,
                String entityType,
                Boolean rolledBack
        ) {

            this.player = player;
            this.action = action;
            this.actionId = actionId;

            this.world = world;

            this.x = x;
            this.y = y;
            this.z = z;

            this.timestamp = timestamp;

            this.material = material;
            this.entityType = entityType;

            this.rolledBack = rolledBack;
        }

        public String getPlayer() {

            return player;
        }

        public String getAction() {

            return action;
        }

        public Integer getActionId() {

            return actionId;
        }

        public String getWorld() {

            return world;
        }

        public Integer getX() {

            return x;
        }

        public Integer getY() {

            return y;
        }

        public Integer getZ() {

            return z;
        }

        public Long getTimestamp() {

            return timestamp;
        }

        public String getMaterial() {

            return material;
        }

        public String getEntityType() {

            return entityType;
        }

        public Boolean isRolledBack() {

            return rolledBack;
        }

        public String toEvidenceText() {

            StringBuilder builder =
                    new StringBuilder();

            builder.append(
                    "CoreProtect investigation result"
            );

            if (player != null) {

                builder.append(
                        "\nPlayer: "
                ).append(player);
            }

            if (action != null) {

                builder.append(
                        "\nAction: "
                ).append(action);
            }

            if (actionId != null) {

                builder.append(
                        "\nAction ID: "
                ).append(actionId);
            }

            if (material != null) {

                builder.append(
                        "\nMaterial: "
                ).append(material);
            }

            if (entityType != null) {

                builder.append(
                        "\nEntity: "
                ).append(entityType);
            }

            if (world != null) {

                builder.append(
                        "\nWorld: "
                ).append(world);
            }

            if (x != null &&
                    y != null &&
                    z != null) {

                builder.append(
                        "\nLocation: "
                ).append(x)
                        .append(", ")
                        .append(y)
                        .append(", ")
                        .append(z);
            }

            if (timestamp != null) {

                builder.append(
                        "\nTimestamp: "
                ).append(timestamp);
            }

            if (rolledBack != null) {

                builder.append(
                        "\nRolled back: "
                ).append(rolledBack);
            }

            return builder.toString();
        }
    }
}