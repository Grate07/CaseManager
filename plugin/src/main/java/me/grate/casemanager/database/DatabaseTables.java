package me.grate.casemanager.database;

import me.grate.casemanager.CaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseTables {

    private DatabaseTables() {
    }

    public static void createTables(CaseManager plugin, DatabaseManager database) {
        try (Connection connection = database.getConnection();
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS cases (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    target_uuid VARCHAR(36) NOT NULL,
                    target_name VARCHAR(16),
                    creator_uuid VARCHAR(36) NOT NULL,
                    creator_name VARCHAR(16),
                    reason TEXT NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    INDEX idx_target_uuid (target_uuid),
                    INDEX idx_creator_uuid (creator_uuid),
                    INDEX idx_status (status)
                )
            """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS case_notes (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    case_id BIGINT NOT NULL,
                    author_uuid VARCHAR(36) NOT NULL,
                    author_name VARCHAR(16),
                    content TEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    INDEX idx_case_id (case_id),
                    CONSTRAINT fk_notes_case
                        FOREIGN KEY (case_id)
                        REFERENCES cases(id)
                        ON DELETE CASCADE
                )
            """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS case_evidence (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    case_id BIGINT NOT NULL,
                    added_by_uuid VARCHAR(36) NOT NULL,
                    added_by_name VARCHAR(16),
                    type VARCHAR(32) NOT NULL,
                    content TEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    INDEX idx_evidence_case_id (case_id),
                    CONSTRAINT fk_evidence_case
                        FOREIGN KEY (case_id)
                        REFERENCES cases(id)
                        ON DELETE CASCADE
                )
            """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS case_investigators (
                    case_id BIGINT NOT NULL,
                    investigator_uuid VARCHAR(36) NOT NULL,
                    investigator_name VARCHAR(16),
                    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (case_id, investigator_uuid),
                    CONSTRAINT fk_investigator_case
                        FOREIGN KEY (case_id)
                        REFERENCES cases(id)
                        ON DELETE CASCADE
                )
            """);

            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS case_timeline (
                    id BIGINT NOT NULL AUTO_INCREMENT,
                    case_id BIGINT NOT NULL,
                    actor_uuid VARCHAR(36),
                    actor_name VARCHAR(16),
                    action VARCHAR(64) NOT NULL,
                    details TEXT,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    PRIMARY KEY (id),
                    INDEX idx_timeline_case_id (case_id),
                    CONSTRAINT fk_timeline_case
                        FOREIGN KEY (case_id)
                        REFERENCES cases(id)
                        ON DELETE CASCADE
                )
            """);

            plugin.getLogger().info("Database tables verified.");

        } catch (SQLException exception) {
            plugin.getLogger().severe("Failed to create database tables.");
            exception.printStackTrace();
        }
    }
}
