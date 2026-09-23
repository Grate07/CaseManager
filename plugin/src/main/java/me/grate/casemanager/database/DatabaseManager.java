package me.grate.casemanager.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.grate.casemanager.CaseManager;

import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseManager {

    private final CaseManager plugin;

    private HikariDataSource dataSource;

    public DatabaseManager(CaseManager plugin) {
        this.plugin = plugin;
    }

    public void connect() {

        if (isConnected()) {
            plugin.getLogger().warning(
                    "Database connection pool is already initialized."
            );
            return;
        }

        String host =
                plugin.getConfig().getString(
                        "database.host",
                        "localhost"
                );

        int port =
                plugin.getConfig().getInt(
                        "database.port",
                        3306
                );

        String database =
                plugin.getConfig().getString(
                        "database.name",
                        "casemanager"
                );

        String username =
                plugin.getConfig().getString(
                        "database.username",
                        "root"
                );

        String password =
                plugin.getConfig().getString(
                        "database.password",
                        ""
                );

        int poolSize =
                Math.max(
                        2,
                        plugin.getConfig().getInt(
                                "database.pool-size",
                                10
                        )
                );

        boolean ssl =
                plugin.getConfig().getBoolean(
                        "database.ssl",
                        false
                );

        if (host == null || host.isBlank()) {
            throw new IllegalStateException(
                    "Database host cannot be empty."
            );
        }

        if (database == null || database.isBlank()) {
            throw new IllegalStateException(
                    "Database name cannot be empty."
            );
        }

        if (username == null || username.isBlank()) {
            throw new IllegalStateException(
                    "Database username cannot be empty."
            );
        }

        if (password == null) {
            password = "";
        }

        HikariConfig hikariConfig =
                new HikariConfig();

        String jdbcUrl =
                "jdbc:mysql://" +
                        host +
                        ":" +
                        port +
                        "/" +
                        database +
                        "?useSSL=" +
                        ssl +
                        "&serverTimezone=UTC" +
                        "&characterEncoding=utf8" +
                        "&useUnicode=true" +
                        "&autoReconnect=true";

        hikariConfig.setJdbcUrl(
                jdbcUrl
        );

        hikariConfig.setUsername(
                username
        );

        hikariConfig.setPassword(
                password
        );

        hikariConfig.setMaximumPoolSize(
                poolSize
        );

        hikariConfig.setMinimumIdle(
                Math.min(2, poolSize)
        );

        hikariConfig.setPoolName(
                "CaseManager-MySQL"
        );

        hikariConfig.setConnectionTimeout(
                10_000
        );

        hikariConfig.setValidationTimeout(
                5_000
        );

        hikariConfig.setIdleTimeout(
                600_000
        );

        hikariConfig.setMaxLifetime(
                1_800_000
        );

        hikariConfig.setLeakDetectionThreshold(
                0
        );

        hikariConfig.setConnectionTestQuery(
                "SELECT 1"
        );

        HikariDataSource newDataSource = null;

        try {

            newDataSource =
                    new HikariDataSource(
                            hikariConfig
                    );

            try (Connection connection =
                         newDataSource.getConnection()) {

                if (!connection.isValid(5)) {

                    throw new SQLException(
                            "Database connection validation failed."
                    );
                }
            }

            dataSource =
                    newDataSource;

            plugin.getLogger().info(
                    "MySQL connection pool initialized successfully."
            );

            plugin.getLogger().info(
                    "Database: " +
                            database
            );

        } catch (Exception exception) {

            if (newDataSource != null &&
                    !newDataSource.isClosed()) {

                newDataSource.close();
            }

            dataSource = null;

            throw new IllegalStateException(
                    "Failed to initialize MySQL connection pool.",
                    exception
            );
        }
    }

    public Connection getConnection()
            throws SQLException {

        if (!isConnected()) {

            throw new SQLException(
                    "Database connection pool is not initialized."
            );
        }

        return dataSource.getConnection();
    }

    public void close() {

        if (dataSource == null) {
            return;
        }

        if (!dataSource.isClosed()) {

            dataSource.close();

            plugin.getLogger().info(
                    "MySQL connection pool closed."
            );
        }

        dataSource = null;
    }

    public boolean isConnected() {

        return dataSource != null &&
                !dataSource.isClosed();
    }
}