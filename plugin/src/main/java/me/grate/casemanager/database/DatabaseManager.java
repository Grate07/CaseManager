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

        String host = plugin.getConfig().getString(
                "database.host",
                ""
        );

        int port = plugin.getConfig().getInt(
                "database.port",
                5432
        );

        String database = plugin.getConfig().getString(
                "database.name",
                "postgres"
        );

        String username = plugin.getConfig().getString(
                "database.username",
                ""
        );

        String password = plugin.getConfig().getString(
                "database.password",
                ""
        );

        int poolSize = Math.max(
                2,
                Math.min(10, plugin.getConfig().getInt(
                        "database.pool-size",
                        5
                ))
        );

        boolean ssl = plugin.getConfig().getBoolean(
                "database.ssl",
                true
        );

        if (host == null || host.isBlank()) {
            throw new IllegalStateException(
                    "Supabase database host cannot be empty. Copy the Session Pooler host from Supabase Dashboard > Connect."
            );
        }

        if (port <= 0 || port > 65535) {
            throw new IllegalStateException(
                    "Database port must be between 1 and 65535."
            );
        }

        if (database == null || database.isBlank()) {
            throw new IllegalStateException(
                    "Database name cannot be empty."
            );
        }

        if (username == null || username.isBlank()) {
            throw new IllegalStateException(
                    "Supabase database username cannot be empty. Use the username from the Session Pooler connection string."
            );
        }

        if (password == null || password.isBlank() ||
                password.equalsIgnoreCase("CHANGE_ME")) {
            throw new IllegalStateException(
                    "Supabase database password is not configured."
            );
        }

        HikariConfig hikariConfig = new HikariConfig();

        String jdbcUrl =
                "jdbc:postgresql://" +
                        host +
                        ":" +
                        port +
                        "/" +
                        database +
                        "?sslmode=" +
                        (ssl ? "require" : "disable") +
                        "&tcpKeepAlive=true";

        hikariConfig.setJdbcUrl(jdbcUrl);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        hikariConfig.setMaximumPoolSize(poolSize);
        hikariConfig.setMinimumIdle(Math.min(2, poolSize));
        hikariConfig.setPoolName("CaseManager-PostgreSQL");
        hikariConfig.setConnectionTimeout(10_000);
        hikariConfig.setValidationTimeout(5_000);
        hikariConfig.setIdleTimeout(600_000);
        hikariConfig.setMaxLifetime(1_800_000);
        hikariConfig.setLeakDetectionThreshold(0);
        hikariConfig.setConnectionTestQuery("SELECT 1");

        HikariDataSource newDataSource = null;

        try {
            newDataSource = new HikariDataSource(hikariConfig);

            try (Connection connection = newDataSource.getConnection()) {
                if (!connection.isValid(5)) {
                    throw new SQLException(
                            "Database connection validation failed."
                    );
                }
            }

            dataSource = newDataSource;

            plugin.getLogger().info(
                    "Supabase PostgreSQL connection pool initialized successfully."
            );
            plugin.getLogger().info(
                    "Database: " + database
            );

        } catch (Exception exception) {
            if (newDataSource != null && !newDataSource.isClosed()) {
                newDataSource.close();
            }

            dataSource = null;

            throw new IllegalStateException(
                    "Failed to initialize Supabase PostgreSQL connection pool.",
                    exception
            );
        }
    }

    public Connection getConnection() throws SQLException {
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
                    "Supabase PostgreSQL connection pool closed."
            );
        }

        dataSource = null;
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
}
