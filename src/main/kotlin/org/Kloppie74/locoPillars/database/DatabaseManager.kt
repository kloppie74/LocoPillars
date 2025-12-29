package org.Kloppie74.locoPillars.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.database.tables.*
import org.Kloppie74.locoPillars.database.dao.LeaderboardCacheDAO
import org.Kloppie74.locoPillars.database.tables.ArenaStats.ArenaStatsTable
import org.Kloppie74.locoPillars.database.tables.ArenaStats.PlayerArenaStatsTable
import org.Kloppie74.locoPillars.database.tables.LeaderboardCache.ArenaPerformanceTable
import org.Kloppie74.locoPillars.database.tables.LeaderboardCache.LeaderboardCacheTable
import org.Kloppie74.locoPillars.database.tables.LeaderboardCache.LeaderboardMetadataTable
import org.Kloppie74.locoPillars.database.tables.achievements.AchievementDefinitionsTable
import org.Kloppie74.locoPillars.database.tables.achievements.AchievementProgressTable
import org.Kloppie74.locoPillars.database.tables.achievements.PlayerAchievementsTable
import java.io.File
import java.sql.Connection
import java.sql.SQLException

/**
 * DatabaseManager - Central database connection and table management.
 *
 * CHANGELOG (v2.0):
 * - Fixed initialization order (instance set BEFORE connect)
 * - Singleton pattern with object
 * - HikariCP connection pooling
 * - Supports H2, MySQL, MariaDB only
 * - Uses Table objects for SQL definitions
 * - Proper driver loading and verification
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
object DatabaseManager {

    private var dataSource: HikariDataSource? = null
    private lateinit var plugin: LocoPillars
    private lateinit var config: DatabaseConfig
    val migrations: DatabaseMigrations by lazy { DatabaseMigrations(this, plugin) }

    /**
     * Initialize HikariCP datasource and create tables.
     */
    fun initialize(pluginInstance: LocoPillars, dbConfig: DatabaseConfig) {
        plugin = pluginInstance
        config = dbConfig

        try {
            plugin.logger.info("[LocoPillars] Initializing ${config.type.uppercase()} database with HikariCP...")

            val hikariConfig = HikariConfig().apply {
                when (config.type.lowercase()) {
                    "h2" -> {
                        val dbFile = File(plugin.dataFolder, config.database)
                        plugin.logger.info("[LocoPillars] H2 database file: ${dbFile.absolutePath}.mv.db")

                        // Try to load H2 driver
                        val driverClass = "org.h2.Driver"
                        var driverLoaded = false
                        try {
                            Class.forName(driverClass, true, this@DatabaseManager::class.java.classLoader)
                            driverLoaded = true
                            plugin.logger.info("[LocoPillars] ✓ Loaded H2 driver via plugin classloader")
                        } catch (e: ClassNotFoundException) {
                            try {
                                Class.forName(driverClass, true, Thread.currentThread().contextClassLoader)
                                driverLoaded = true
                                plugin.logger.info("[LocoPillars] ✓ Loaded H2 driver via thread context classloader")
                            } catch (e2: ClassNotFoundException) {
                                plugin.logger.severe("[LocoPillars] ❌ H2 driver class not found (org.h2.Driver).")
                                plugin.logger.severe("[LocoPillars] Please include H2 dependency in your build!")
                                throw e2
                            }
                        }

                        jdbcUrl = "jdbc:h2:${dbFile.absolutePath};DB_CLOSE_DELAY=-1;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
                        username = "sa"
                        password = ""
                    }
                    "mysql" -> {
                        plugin.logger.info("[LocoPillars] Connecting to MySQL: ${config.host}:${config.port}/${config.database}")
                        jdbcUrl = "jdbc:mysql://${config.host}:${config.port}/${config.database}?useSSL=${config.useSSL}&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                        driverClassName = "com.mysql.cj.jdbc.Driver"
                        username = config.username
                        password = config.password
                    }
                    "mariadb" -> {
                        plugin.logger.info("[LocoPillars] Connecting to MariaDB: ${config.host}:${config.port}/${config.database}")
                        jdbcUrl = "jdbc:mariadb://${config.host}:${config.port}/${config.database}?useSSL=${config.useSSL}"
                        driverClassName = "org.mariadb.jdbc.Driver"
                        username = config.username
                        password = config.password
                    }
                    else -> {
                        throw IllegalArgumentException("Unsupported database type: ${config.type}. Only H2, MySQL, and MariaDB are supported.")
                    }
                }

                // Connection pool settings
                maximumPoolSize = config.maxPoolSize
                minimumIdle = config.minPoolSize
                connectionTimeout = config.connectionTimeoutMs
                idleTimeout = config.idleTimeoutMs
                maxLifetime = config.maxLifetimeMs
                leakDetectionThreshold = config.leakDetectionThresholdMs

                // Performance settings
                isAutoCommit = false
                addDataSourceProperty("cachePrepStmts", "true")
                addDataSourceProperty("prepStmtCacheSize", "250")
                addDataSourceProperty("prepStmtCacheSqlLimit", "2048")

                // Connection validation
                connectionTestQuery = "SELECT 1"
                validationTimeout = 5000
            }

            dataSource = HikariDataSource(hikariConfig)
            plugin.logger.info("[LocoPillars] ✓ HikariCP datasource created!")

            // Test connection
            getConnection().use { conn ->
                if (!conn.isValid(5)) {
                    throw SQLException("Database connection validation failed")
                }
                plugin.logger.info("[LocoPillars] ✓ Database connection validated")
            }

            // Create tables
            createTables()

            // Initialize leaderboard metadata
            try {
                LeaderboardCacheDAO.initializeMetadata()
                plugin.logger.info("[LocoPillars] ✓ Leaderboard metadata initialized")
            } catch (e: Exception) {
                plugin.logger.warning("[LocoPillars] ✗ Leaderboard metadata initialization failed: ${e.message}")
            }

            // Run migrations
            try {
                if (migrations.runMigrations()) {
                    plugin.logger.info("[LocoPillars] ✓ Database migrations completed!")
                }
            } catch (e: Exception) {
                plugin.logger.warning("[LocoPillars] ✗ Migration warning: ${e.message}")
            }

            plugin.logger.info("[LocoPillars] ✓ Database initialized successfully!")

        } catch (e: Exception) {
            plugin.logger.severe("[LocoPillars] ❌ Failed to initialize database!")
            plugin.logger.severe("[LocoPillars] Database type: ${config.type}")
            e.printStackTrace()
            try { dataSource?.close() } catch (_: Exception) {}
            dataSource = null
            throw RuntimeException("Database initialization failed", e)
        }
    }

    /**
     * Creates all database tables.
     */
    private fun createTables() {
        try {
            plugin.logger.info("[LocoPillars] Creating database tables...")

            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    val dbType = config.type.lowercase()

                    // Determine which SQL to use based on database type
                    val createSQL = when (dbType) {
                        "h2", "sqlite" -> listOf(
                            PlayersTable.CREATE_SQL_SQLITE,
                            StatsTable.CREATE_SQL_SQLITE,
                            VanishTable.CREATE_SQL_SQLITE,
                            AchievementDefinitionsTable.CREATE_SQL_SQLITE,
                            PlayerAchievementsTable.CREATE_SQL_SQLITE,
                            AchievementProgressTable.CREATE_SQL_SQLITE,
                            ArenaStatsTable.CREATE_SQL_SQLITE,
                            PlayerArenaStatsTable.CREATE_SQL_SQLITE,
                            ArenaPerformanceTable.CREATE_SQL_SQLITE,
                            LeaderboardCacheTable.CREATE_SQL_SQLITE,
                            LeaderboardMetadataTable.CREATE_SQL_SQLITE
                        )
                        "mysql", "mariadb" -> listOf(
                            PlayersTable.CREATE_SQL_MYSQL,
                            StatsTable.CREATE_SQL_MYSQL,
                            VanishTable.CREATE_SQL_MYSQL,
                            AchievementDefinitionsTable.CREATE_SQL_MYSQL,
                            PlayerAchievementsTable.CREATE_SQL_MYSQL,
                            AchievementProgressTable.CREATE_SQL_MYSQL,
                            ArenaStatsTable.CREATE_SQL_MYSQL,
                            PlayerArenaStatsTable.CREATE_SQL_MYSQL,
                            ArenaPerformanceTable.CREATE_SQL_MYSQL,
                            LeaderboardCacheTable.CREATE_SQL_MYSQL,
                            LeaderboardMetadataTable.CREATE_SQL_MYSQL
                        )
                        else -> throw IllegalArgumentException("Unsupported database type: $dbType")
                    }

                    // Execute each CREATE TABLE statement
                    createSQL.forEachIndexed { index, sql ->
                        try {
                            stmt.executeUpdate(sql)
                            plugin.logger.info("[LocoPillars] ✓ Created/verified table ${index + 1}/${createSQL.size}")
                        } catch (e: SQLException) {
                            plugin.logger.warning("[LocoPillars] ✗ Table ${index + 1} error: ${e.message}")
                        }
                    }

                    // Create indexes separately
                    try {
                        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_players_name ON lp_players(player_name)")
                        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_players_active ON lp_players(is_active)")
                        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_stats_wins ON lp_stats(games_won DESC)")
                        stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_stats_level ON lp_stats(level DESC)")
                        plugin.logger.info("[LocoPillars] ✓ Created indexes")
                    } catch (e: SQLException) {
                        plugin.logger.warning("[LocoPillars] ✗ Some indexes may already exist")
                    }
                }
                conn.commit()
            }

            plugin.logger.info("[LocoPillars] ✓ Database tables created/verified successfully!")

        } catch (e: SQLException) {
            plugin.logger.severe("[LocoPillars] ❌ Error creating tables: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Gets a database connection from the pool.
     */
    fun getConnection(): Connection {
        val ds = dataSource ?: throw IllegalStateException("Database not initialized")
        return ds.connection
    }

    /**
     * Gets the database type.
     */
    fun getDatabaseType(): String = config.type

    /**
     * Executes a synchronous database operation with transaction support.
     */
    fun <T> executeSync(block: (Connection) -> T): T {
        return getConnection().use { conn ->
            try {
                val result = block(conn)
                conn.commit()
                result
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }
    }

    /**
     * Checks if database is connected.
     */
    fun isConnected(): Boolean {
        return try {
            dataSource?.connection?.use { it.isValid(2) } ?: false
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets connection pool statistics.
     */
    fun getPoolStats(): Map<String, Any> {
        return try {
            val ds = dataSource
            if (ds != null && !ds.isClosed) {
                mapOf(
                    "max_pool_size" to config.maxPoolSize,
                    "min_pool_size" to config.minPoolSize,
                    "is_connected" to isConnected(),
                    "is_closed" to ds.isClosed,
                    "database_type" to config.type
                )
            } else {
                mapOf("error" to "DataSource not initialized")
            }
        } catch (e: Exception) {
            mapOf("error" to (e.message ?: "Unknown error"))
        }
    }

    /**
     * Closes the database connection pool.
     */
    fun close() {
        plugin.logger.info("[LocoPillars] Closing database connection...")

        try {
            // H2 specific shutdown
            if (config.type.lowercase() == "h2") {
                try {
                    val conn = runCatching { getConnection() }.getOrNull()
                    if (conn != null) {
                        conn.createStatement().use { stmt ->
                            stmt.execute("SHUTDOWN")
                        }
                        conn.close()
                        plugin.logger.info("[LocoPillars] ✓ H2 database SHUTDOWN executed!")
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("[LocoPillars] ✗ H2 SHUTDOWN exception (may already be closed): ${e.message}")
                }
            }

            // Close HikariCP pool
            dataSource?.close()
            plugin.logger.info("[LocoPillars] ✓ Database connection pool closed")

        } catch (e: Exception) {
            plugin.logger.severe("[LocoPillars] ❌ Error closing database: ${e.message}")
            e.printStackTrace()
        } finally {
            dataSource = null
        }
    }
}