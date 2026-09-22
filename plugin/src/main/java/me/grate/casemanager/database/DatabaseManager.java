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
        String host = plugin.getConfig().getString("database.host");
        int port = plugin.getConfig().getInt("database.port");
        String database = plugin.getConfig().getString("database.name");
        String username = plugin.getConfig().getString("database.username");
        String password = plugin.getConfig().getString("database.password");

        int poolSize = plugin.getConfig().getInt("database.pool-size", 10);
        boolean ssl = plugin.getConfig().getBoolean("database.ssl", false);

        HikariConfig config = new HikariConfig();

        config.setJdbcUrl(
                "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=" + ssl
                        + "&serverTimezone=UTC"
        );

        config.setUsername(username);
        config.setPassword(password);

        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);

        config.setPoolName("CaseManager-MySQL");

        config.setConnectionTimeout(10000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        dataSource = new HikariDataSource(config);

        plugin.getLogger().info("MySQL connection pool initialized.");
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized.");
        }

        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL connection pool closed.");
        }
    }

    public boolean isConnected() {
        return dataSource != null && !dataSource.isClosed();
    }
}
