package me.grate.casemanager.integration;

import me.grate.casemanager.CaseManager;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class LiteBansIntegration {

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

        available = false;

        liteBansDatabase = null;
        databaseGetMethod = null;

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
                            exception.getMessage()
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

        if (limit <= 0) {
            limit = 25;
        }

        if (limit > 100) {
            limit = 100;
        }

        String uuid =
                playerUuid.toString();

        /*
         * LiteBans exposes the bans table through its API.
         *
         * The API documentation uses {bans} as the table placeholder,
         * which is resolved by LiteBans' Database API.
         */

        String query =
                "SELECT * FROM {bans} " +
                "WHERE uuid = ? " +
                "ORDER BY time DESC " +
                "LIMIT ?";

        List<LiteBansPunishment> punishments =
                new ArrayList<>();

        try (
                PreparedStatement statement =
                        prepareStatement(
                                query
                        )
        ) {

            statement.setString(
                    1,
                    uuid
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

                    punishments.add(
                            readPunishment(
                                    result
                            )
                    );
                }
            }

            return punishments;

        } catch (SQLException exception) {

            plugin.getLogger().warning(
                    "LiteBans punishment lookup failed: " +
                            exception.getMessage()
            );

            return Collections.emptyList();

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Unexpected LiteBans database error: " +
                            exception.getMessage()
            );

            return Collections.emptyList();
        }
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
                        prepareStatement(
                                query
                        )
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
                            exception.getMessage()
            );

            return false;

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Unexpected LiteBans ban check error: " +
                            exception.getMessage()
            );

            return false;
        }
    }
    public List<LiteBansPunishment> getBanHistory(
            UUID playerUuid,
            int limit
    ) {

        if (!isAvailable() ||
                playerUuid == null) {

            return Collections.emptyList();
        }

        if (limit <= 0) {
            limit = 25;
        }

        if (limit > 100) {
            limit = 100;
        }

        String query =
                "SELECT * FROM {bans} " +
                "WHERE uuid = ? " +
                "ORDER BY time DESC " +
                "LIMIT ?";

        List<LiteBansPunishment> punishments =
                new ArrayList<>();

        try (
                PreparedStatement statement =
                        prepareStatement(
                                query
                        )
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

                    punishments.add(
                            readPunishment(
                                    result
                            )
                    );
                }
            }

            return punishments;

        } catch (SQLException exception) {

            plugin.getLogger().warning(
                    "LiteBans ban history lookup failed: " +
                            exception.getMessage()
            );

            return Collections.emptyList();

        } catch (Exception exception) {

            plugin.getLogger().warning(
                    "Unexpected LiteBans ban history error: " +
                            exception.getMessage()
            );

            return Collections.emptyList();
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
            ).append(playerName);
        }

        if (playerUuid != null) {

            builder.append(
                    "\nUUID: "
            ).append(playerUuid);
        }

        builder.append(
                "\nEntries: "
        ).append(history.size());

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

            builder.append(
                    "\n\n#"
            ).append(index);

            builder.append(
                    "\nType: "
            ).append(
                    punishment.getType()
            );

            if (punishment.getReason() != null) {

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

    private PreparedStatement prepareStatement(
            String query
    )
            throws SQLException {

        /*
         * LiteBans' Database API exposes a prepareStatement method.
         * We access it through reflection so CaseManager doesn't need
         * LiteBans on its compile classpath.
         */

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
            ResultSet result
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
                null;

        if (bannedByUuid != null &&
                !bannedByUuid.isBlank()) {

            try {

                executorUuid =
                        UUID.fromString(
                                bannedByUuid
                        );

            } catch (IllegalArgumentException ignored) {

                /*
                 * Some LiteBans entries can contain non-UUID
                 * executor identifiers. Keep the UUID null.
                 */
            }
        }

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
                detectPunishmentType(
                        result
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

    private String detectPunishmentType(
            ResultSet result
    ) {

        String[] possibleColumns = {
                "type",
                "punishment_type",
                "action"
        };

        for (
                String column :
                possibleColumns
        ) {

            try {

                String value =
                        result.getString(
                                column
                        );

                if (value != null &&
                        !value.isBlank()) {

                    return value;
                }

            } catch (SQLException ignored) {

                /*
                 * Column doesn't exist in this LiteBans schema.
                 */
            }
        }

        /*
         * The bans table represents bans, so use BAN when the
         * schema doesn't expose a separate type column.
         */

        return "BAN";
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

            this.id = id;

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
            ).append(id);

            builder.append(
                    "\nType: "
            ).append(type);

            if (reason != null &&
                    !reason.isBlank()) {

                builder.append(
                        "\nReason: "
                ).append(reason);
            }

            if (executorUuid != null) {

                builder.append(
                        "\nExecutor UUID: "
                ).append(executorUuid);
            }

            builder.append(
                    "\nTime: "
            ).append(time);

            builder.append(
                    "\nUntil: "
            ).append(until);

            builder.append(
                    "\nActive: "
            ).append(active);

            return builder.toString();
        }
    }
}