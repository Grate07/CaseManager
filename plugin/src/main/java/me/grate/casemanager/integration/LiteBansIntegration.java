package me.grate.casemanager.integration;

import me.grate.casemanager.CaseManager;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class LiteBansIntegration {

    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;

    private final CaseManager plugin;

    private Object liteBansDatabase;

    private Method databaseGetMethod;

    private boolean available;

    public LiteBansIntegration(
            CaseManager plugin
    ) {
        this.plugin = plugin;
    }

    public void initialize() {

        reset();

        Plugin liteBans =
                plugin.getServer()
                        .getPluginManager()
                        .getPlugin("LiteBans");

        if (liteBans == null) {

            plugin.getLogger().info(
                    "LiteBans not found. LiteBans integration disabled."
            );

            return;
        }

        if (!liteBans.isEnabled()) {

            plugin.getLogger().warning(
                    "LiteBans is installed but not enabled."
            );

            return;
        }

        try {

            Class<?> databaseClass =
                    Class.forName(
                            "litebans.api.Database"
                    );

            databaseGetMethod =
                    findDatabaseGetMethod(
                            databaseClass
                    );

            if (databaseGetMethod == null) {

                plugin.getLogger().warning(
                        "LiteBans Database.get() API method could not be found."
                );

                return;
            }

            liteBansDatabase =
                    databaseGetMethod.invoke(
                            null
                    );

            if (liteBansDatabase == null) {

                plugin.getLogger().warning(
                        "LiteBans returned a null database API."
                );

                reset();

                return;
            }

            /*
             * Verify that the documented prepareStatement()
             * method is available before declaring the integration
             * usable.
             */
            if (!hasPrepareStatementMethod()) {

                plugin.getLogger().warning(
                        "LiteBans Database API does not expose " +
                                "prepareStatement(String)."
                );

                reset();

                return;
            }

            available = true;

            plugin.getLogger().info(
                    "LiteBans integration enabled."
            );

        } catch (ClassNotFoundException exception) {

            plugin.getLogger().warning(
                    "LiteBans API classes were not found."
            );

            reset();

        } catch (
                IllegalAccessException |
                InvocationTargetException exception
        ) {

            plugin.getLogger().warning(
                    "Failed to initialize LiteBans integration: " +
                            getRootMessage(exception)
            );

            reset();

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Unexpected LiteBans integration error: " +
                            getRootMessage(exception)
            );

            reset();
        }
    }

    public boolean isAvailable() {

        return available &&
                liteBansDatabase != null;
    }

    public Object getDatabaseApi() {

        return liteBansDatabase;
    }

    public List<LiteBansPunishment> getPunishmentHistory(
            UUID playerUuid,
            int limit
    ) {

        if (!isAvailable() ||
                playerUuid == null) {

            return Collections.emptyList();
        }

        int safeLimit =
                normalizeLimit(limit);

        List<LiteBansPunishment> punishments =
                new ArrayList<>();

        collectTable(
                punishments,
                "bans",
                playerUuid,
                safeLimit
        );

        collectTable(
                punishments,
                "mutes",
                playerUuid,
                safeLimit
        );

        collectTable(
                punishments,
                "warnings",
                playerUuid,
                safeLimit
        );

        collectTable(
                punishments,
                "kicks",
                playerUuid,
                safeLimit
        );

        punishments.sort(
                (first, second) ->
                        Long.compare(
                                second.getTime(),
                                first.getTime()
                        )
        );

        if (punishments.size() > safeLimit) {

            return new ArrayList<>(
                    punishments.subList(
                            0,
                            safeLimit
                    )
            );
        }

        return punishments;
    }

    public List<LiteBansPunishment> getBanHistory(
            UUID playerUuid,
            int limit
    ) {

        if (!isAvailable() ||
                playerUuid == null) {

            return Collections.emptyList();
        }

        int safeLimit =
                normalizeLimit(limit);

        List<LiteBansPunishment> punishments =
                new ArrayList<>();

        collectTable(
                punishments,
                "bans",
                playerUuid,
                safeLimit
        );

        punishments.sort(
                (first, second) ->
                        Long.compare(
                                second.getTime(),
                                first.getTime()
                        )
        );

        return punishments;
    }

    public boolean isBanned(
            UUID playerUuid
    ) {

        if (!isAvailable() ||
                playerUuid == null) {

            return false;
        }

        String query =
                "SELECT COUNT(*) " +
                "FROM {bans} " +
                "WHERE uuid = ? " +
                "AND active = 1";

        try (
                PreparedStatement statement =
                        prepareStatement(query)
        ) {

            statement.setString(
                    1,
                    playerUuid.toString()
            );

            try (
                    ResultSet result =
                            statement.executeQuery()
            ) {

                if (!result.next()) {
                    return false;
                }

                return result.getLong(1) > 0;
            }

        } catch (SQLException exception) {

            plugin.getLogger().warning(
                    "LiteBans ban check failed: " +
                            getRootMessage(exception)
            );

            return false;

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Unexpected LiteBans ban check error: " +
                            getRootMessage(exception)
            );

            return false;
        }
    }

    public String createEvidenceSummary(
            UUID playerUuid,
            String playerName,
            int limit
    ) {

        if (!isAvailable()) {

            return
                    "LiteBans integration is not available.";
        }

        List<LiteBansPunishment> history =
                getPunishmentHistory(
                        playerUuid,
                        limit
                );

        StringBuilder builder =
                new StringBuilder();

        builder.append(
                "LiteBans Punishment History"
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
                "\nEntries: "
        ).append(
                history.size()
        );

        if (history.isEmpty()) {

            builder.append(
                    "\nNo punishment history found."
            );

            return builder.toString();
        }

        int index = 1;

        for (
                LiteBansPunishment punishment :
                history
        ) {

            if (punishment == null) {
                continue;
            }

            builder.append(
                    "\n\n#"
            ).append(
                    index
            );

            builder.append(
                    "\nType: "
            ).append(
                    punishment.getType()
            );

            if (punishment.getReason() != null &&
                    !punishment.getReason().isBlank()) {

                builder.append(
                        "\nReason: "
                ).append(
                        punishment.getReason()
                );
            }

            if (punishment.getExecutorUuid() != null) {

                builder.append(
                        "\nExecutor UUID: "
                ).append(
                        punishment.getExecutorUuid()
                );
            }

            builder.append(
                    "\nTime: "
            ).append(
                    punishment.getTime()
            );

            builder.append(
                    "\nUntil: "
            ).append(
                    punishment.getUntil()
            );

            builder.append(
                    "\nActive: "
            ).append(
                    punishment.isActive()
            );

            index++;
        }

        return builder.toString();
    }

    private void collectTable(
            List<LiteBansPunishment> destination,
            String table,
            UUID playerUuid,
            int limit
    ) {

        if (destination == null ||
                table == null ||
                playerUuid == null) {

            return;
        }

        String query =
                "SELECT * FROM {" +
                        table +
                        "} " +
                        "WHERE uuid = ? " +
                        "ORDER BY time DESC " +
                        "LIMIT ?";

        try (
                PreparedStatement statement =
                        prepareStatement(query)
        ) {

            statement.setString(
                    1,
                    playerUuid.toString()
            );

            statement.setInt(
                    2,
                    limit
            );

            try (
                    ResultSet result =
                            statement.executeQuery()
            ) {

                while (result.next()) {

                    try {

                        destination.add(
                                readPunishment(
                                        result,
                                        table
                                )
                        );

                    } catch (SQLException exception) {

                        plugin.getLogger().warning(
                                "Failed to read a LiteBans " +
                                        table +
                                        " record: " +
                                        getRootMessage(exception)
                        );
                    }
                }
            }

        } catch (SQLException exception) {

            /*
             * Some LiteBans installations may not expose every
             * punishment table depending on their version/config.
             *
             * A failure in one table must not prevent the other
             * punishment tables from being collected.
             */
            plugin.getLogger().warning(
                    "LiteBans " +
                            table +
                            " lookup failed: " +
                            getRootMessage(exception)
            );

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Unexpected LiteBans " +
                            table +
                            " lookup error: " +
                            getRootMessage(exception)
            );
        }
    }

    private PreparedStatement prepareStatement(
            String query
    )
            throws SQLException {

        if (liteBansDatabase == null) {

            throw new SQLException(
                    "LiteBans database API is unavailable."
            );
        }

        try {

            Method method =
                    liteBansDatabase
                            .getClass()
                            .getMethod(
                                    "prepareStatement",
                                    String.class
                            );

            Object result =
                    method.invoke(
                            liteBansDatabase,
                            query
                    );

            if (!(result instanceof PreparedStatement statement)) {

                throw new SQLException(
                        "LiteBans did not return a PreparedStatement."
                );
            }

            return statement;

        } catch (
                IllegalAccessException |
                InvocationTargetException |
                NoSuchMethodException exception
        ) {

            throw new SQLException(
                    "Unable to access LiteBans Database API.",
                    exception
            );
        }
    }

    private LiteBansPunishment readPunishment(
            ResultSet result,
            String table
    )
            throws SQLException {

        String reason =
                readNullableString(
                        result,
                        "reason"
                );

        String bannedByUuid =
                readNullableString(
                        result,
                        "banned_by_uuid"
                );

        UUID executorUuid =
                parseUuid(
                        bannedByUuid
                );

        long time =
                readLong(
                        result,
                        "time"
                );

        long until =
                readLong(
                        result,
                        "until"
                );

        long id =
                readLong(
                        result,
                        "id"
                );

        boolean active =
                readBoolean(
                        result,
                        "active"
                );

        String type =
                determineType(
                        result,
                        table
                );

        return new LiteBansPunishment(
                id,
                type,
                reason,
                executorUuid,
                time,
                until,
                active
        );
    }

    private String determineType(
            ResultSet result,
            String table
    ) {

        String explicitType =
                readNullableString(
                        result,
                        "type"
                );

        if (explicitType != null &&
                !explicitType.isBlank()) {

            return explicitType;
        }

        String punishmentType =
                readNullableString(
                        result,
                        "punishment_type"
                );

        if (punishmentType != null &&
                !punishmentType.isBlank()) {

            return punishmentType;
        }

        if (table == null) {
            return "UNKNOWN";
        }

        return switch (table.toLowerCase()) {

            case "bans" ->
                    "BAN";

            case "mutes" ->
                    "MUTE";

            case "warnings" ->
                    "WARN";

            case "kicks" ->
                    "KICK";

            default ->
                    table.toUpperCase();
        };
    }

    private UUID parseUuid(
            String value
    ) {

        if (value == null ||
                value.isBlank()) {

            return null;
        }

        try {

            return UUID.fromString(
                    value
            );

        } catch (IllegalArgumentException ignored) {

            return null;
        }
    }

    private String readNullableString(
            ResultSet result,
            String column
    ) {

        try {

            return result.getString(
                    column
            );

        } catch (SQLException ignored) {

            return null;
        }
    }

    private long readLong(
            ResultSet result,
            String column
    ) {

        try {

            return result.getLong(
                    column
            );

        } catch (SQLException ignored) {

            return 0L;
        }
    }

    private boolean readBoolean(
            ResultSet result,
            String column
    ) {

        try {

            return result.getBoolean(
                    column
            );

        } catch (SQLException ignored) {

            return false;
        }
    }

    private int normalizeLimit(
            int limit
    ) {

        if (limit <= 0) {
            return DEFAULT_LIMIT;
        }

        return Math.min(
                limit,
                MAX_LIMIT
        );
    }

    private Method findDatabaseGetMethod(
            Class<?> databaseClass
    ) {

        for (
                Method method :
                databaseClass.getMethods()
        ) {

            if (!method.getName()
                    .equals("get")) {

                continue;
            }

            if (method.getParameterCount() != 0) {
                continue;
            }

            return method;
        }

        return null;
    }

    private boolean hasPrepareStatementMethod() {

        if (liteBansDatabase == null) {
            return false;
        }

        try {

            liteBansDatabase
                    .getClass()
                    .getMethod(
                            "prepareStatement",
                            String.class
                    );

            return true;

        } catch (NoSuchMethodException exception) {

            return false;
        }
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

    private void reset() {

        available = false;

        liteBansDatabase = null;

        databaseGetMethod = null;
    }

    public static final class LiteBansPunishment {

        private final long id;

        private final String type;
        private final String reason;

        private final UUID executorUuid;

private final long time;
        private final long until;

        private final boolean active;

        public LiteBansPunishment(
                long id,
                String type,
                String reason,
                UUID executorUuid,
                long time,
                long until,
                boolean active
        ) {

            this.id =
                    id;

            this.type =
                    type;

            this.reason =
                    reason;

            this.executorUuid =
                    executorUuid;

            this.time =
                    time;

            this.until =
                    until;

            this.active =
                    active;
        }

        public long getId() {

            return id;
        }

        public String getType() {

            return type;
        }

        public String getReason() {

            return reason;
        }

        public UUID getExecutorUuid() {

            return executorUuid;
        }

        public long getTime() {

            return time;
        }

        public long getUntil() {

            return until;
        }

        public boolean isActive() {

            return active;
        }

        public String toEvidenceText() {

            StringBuilder builder =
                    new StringBuilder();

            builder.append(
                    "LiteBans Punishment"
            );

            builder.append(
                    "\nID: "
            ).append(
                    id
            );

            builder.append(
                    "\nType: "
            ).append(
                    type
            );

            if (reason != null &&
                    !reason.isBlank()) {

                builder.append(
                        "\nReason: "
                ).append(
                        reason
                );
            }

            if (executorUuid != null) {

                builder.append(
                        "\nExecutor UUID: "
                ).append(
                        executorUuid
                );
            }

            builder.append(
                    "\nTime: "
            ).append(
                    time
            );

            builder.append(
                    "\nUntil: "
            ).append(
                    until
            );

            builder.append(
                    "\nActive: "
            ).append(
                    active
            );

            return builder.toString();
        }
    }
}