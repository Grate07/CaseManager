const { Pool } = require("pg");

const config = require("../config");


/*
 * ============================================================
 * CaseManager Discord — PostgreSQL Database
 * ============================================================
 *
 * This connects the Discord bot to the SAME PostgreSQL database
 * used by the Minecraft CaseManager plugin.
 *
 * Supabase PostgreSQL is supported through its pooler connection.
 * ============================================================
 */


class Database {

    constructor() {

        this.pool = null;
        this.initialized = false;
    }


    /**
     * Initialize the PostgreSQL connection pool.
     */
    initialize() {

        if (this.initialized) {

            return;
        }

        this.pool = new Pool({

            host:
                config.database.host,

            port:
                config.database.port,

            database:
                config.database.database,

            user:
                config.database.user,

            password:
                config.database.password,

            max:
                config.database.max,

            ssl:
                config.database.ssl
                    ? {
                        rejectUnauthorized: false
                    }
                    : false,

            idleTimeoutMillis: 30000,

            connectionTimeoutMillis: 10000
        });


        this.pool.on(
            "error",
            error => {

                console.error(
                    "[CaseManager] PostgreSQL pool error:",
                    error
                );
            }
        );


        this.initialized = true;

        console.log(
            "[CaseManager] PostgreSQL pool initialized."
        );
    }


    /**
     * Execute a SQL query.
     *
     * Example:
     *
     * await database.query(
     *     "SELECT * FROM cases WHERE id = $1",
     *     [caseId]
     * );
     */
    async query(
        text,
        parameters = []
    ) {

        this.ensureInitialized();

        return this.pool.query(
            text,
            parameters
        );
    }


    /**
     * Get a client for transactions.
     */
    async getClient() {

        this.ensureInitialized();

        return this.pool.connect();
    }


    /**
     * Test the database connection.
     */
    async testConnection() {

        this.ensureInitialized();

        const result =
            await this.pool.query(
                "SELECT NOW() AS current_time"
            );

        return result.rows[0];
    }


    /**
     * Gracefully close the connection pool.
     */
    async shutdown() {

        if (!this.pool) {

            return;
        }

        try {

            await this.pool.end();

            console.log(
                "[CaseManager] PostgreSQL pool closed."
            );

        } finally {

            this.pool = null;
            this.initialized = false;
        }
    }


    /**
     * Check whether the database layer is initialized.
     */
    isInitialized() {

        return this.initialized &&
            this.pool !== null;
    }


    /**
     * Prevent queries before initialization.
     */
    ensureInitialized() {

        if (!this.initialized ||
            !this.pool) {

            throw new Error(
                "Database has not been initialized."
            );
        }
    }
}


module.exports =
    new Database();