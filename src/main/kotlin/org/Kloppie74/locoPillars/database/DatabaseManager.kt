package org.Kloppie74.locoPillars.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.Kloppie74.locoPillars.database.tables.*
import org.Kloppie74.locoPillars.database.DatabaseConfig
import org.Kloppie74.locoPillars.database.events.StatsEventTracker
import org.bukkit.plugin.Plugin
import java.sql.Connection
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ScheduledFuture

class DatabaseManager(
    private val plugin: Plugin,
    private val config: DatabaseConfig
) {

    private var dataSource: HikariDataSource? = null
    private var executor: ScheduledExecutorService? = null
    private var maintenanceTask: ScheduledFuture<*>? = null

    lateinit var playersTable: PlayersTable
        private set
    lateinit var statsTable: StatsTable
        private set
    lateinit var achievementsTable: AchievementsTable
        private set
    lateinit var arenaStatsTable: ArenaStatsTable
        private set
    lateinit var leaderboardCache: LeaderboardCacheTable
        private set
    lateinit var vanishTable: VanishTable
        private set
    lateinit var StatsEventTracker: StatsEventTracker
        private set

    lateinit var migrations: DatabaseMigrations

    private var isConnected = false
    private var initialized = false

    /**
     * Initializes the database connection and tables.
     */
    fun initialize(): Boolean {
        plugin.logger.info("Initializing database connection...")

        // 1. CREATE CONNECTION POOL - POOLSIZE=1 VOOR SQLITE TIJDENS INIT
        if (!createConnectionPool(initialSetup = true)) {
            plugin.logger.severe("Failed to create database connection pool!")
            cleanup()
            return false
        }

        // 2. TEST DE VERBINDING
        if (!testConnection()) {
            plugin.logger.severe("Database connection test failed!")
            cleanup()
            return false
        }

        // 3. INITIALISEER TABLE MANAGERS
        initializeTableManagers()

        // 4. SYNCHRONOUS EN IN VOLGORDE ALLE TABELLEN AANMAKEN
        if (!createTablesSync()) {
            plugin.logger.severe("Failed to create database tables (synchronously)!")
            cleanup()
            return false
        }

        // 5. INITIALISEER MIGRATIES EN RUN ZE SYNC
        migrations = DatabaseMigrations(this, plugin)
        if (!migrations.runMigrations()) {
            plugin.logger.severe("Database migrations failed!")
            cleanup()
            return false
        }

        // 6. VALIDATEER DATABASE INTEGRITEIT
        if (!migrations.validateDatabaseIntegrity()) {
            plugin.logger.severe("Database integrity validation failed!")
            cleanup()
            return false
        }

        // 7. Nu is alles klaar: pool mag groter worden als SQLite
        if (getDatabaseType() == "sqlite") {
            updatePoolSizeAfterInit()
        }

        isConnected = true
        initialized = true

        plugin.logger.info("Database initialized successfully!")
        plugin.logger.info("Database type: ${getDatabaseType()}")
        plugin.logger.info("Connection pool size: ${config.maxPoolSize}")

        // 8. MAINTENANCE PAS STARTEN NA INIT!
        startMaintenanceTask()

        return true
    }

    /**
     * Creates the HikariCP connection pool.
     * Voor SQLite: poolsize=1 bij init, mag later omhoog!
     */
    private fun createConnectionPool(initialSetup: Boolean = false): Boolean {
        return try {
            val hikariConfig = HikariConfig().apply {
                when (config.type.lowercase()) {
                    "sqlite" -> {
                        driverClassName = "org.sqlite.JDBC"
                        jdbcUrl = "jdbc:sqlite:${plugin.dataFolder}/${config.database}.db"
                        addDataSourceProperty("foreign_keys", "true")
                        addDataSourceProperty("journal_mode", "WAL")
                        addDataSourceProperty("synchronous", "NORMAL")
                        addDataSourceProperty("cache_size", "10000")
                        addDataSourceProperty("temp_store", "MEMORY")
                        // Belangrijk: poolsize=1 tijdens init, daarna mag poolsize omhoog
                        maximumPoolSize = if (initialSetup) 1 else config.maxPoolSize
                        minimumIdle = 1
                    }

                    "h2" -> {
                        driverClassName = "org.h2.Driver"
                        jdbcUrl = "jdbc:h2:${plugin.dataFolder}/${config.database};MODE=MySQL;DATABASE_TO_LOWER=TRUE"
                        username = config.username
                        password = config.password
                        maximumPoolSize = config.maxPoolSize
                        minimumIdle = config.minPoolSize
                        addDataSourceProperty("cachePrepStmts", "true")
                        addDataSourceProperty("prepStmtCacheSize", "250")
                        addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
                    }

                    "mysql" -> {
                        driverClassName = "com.mysql.cj.jdbc.Driver"
                        jdbcUrl = "jdbc:mysql://${config.host}:${config.port}/${config.database}?useSSL=${config.useSSL}&allowPublicKeyRetrieval=true&serverTimezone=UTC"
                        username = config.username
                        password = config.password
                        maximumPoolSize = config.maxPoolSize
                        minimumIdle = config.minPoolSize
                        addDataSourceProperty("cachePrepStmts", "true")
                        addDataSourceProperty("prepStmtCacheSize", "250")
                        addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
                        addDataSourceProperty("useServerPrepStmts", "true")
                        addDataSourceProperty("useLocalSessionState", "true")
                        addDataSourceProperty("rewriteBatchedStatements", "true")
                        addDataSourceProperty("cacheResultSetMetadata", "true")
                        addDataSourceProperty("cacheServerConfiguration", "true")
                        addDataSourceProperty("elideSetAutoCommits", "true")
                        addDataSourceProperty("maintainTimeStats", "false")
                    }

                    "mariadb" -> {
                        driverClassName = "org.mariadb.jdbc.Driver"
                        jdbcUrl = "jdbc:mariadb://${config.host}:${config.port}/${config.database}?useSSL=${config.useSSL}&serverTimezone=UTC"
                        username = config.username
                        password = config.password
                        maximumPoolSize = config.maxPoolSize
                        minimumIdle = config.minPoolSize
                        addDataSourceProperty("cachePrepStmts", "true")
                        addDataSourceProperty("prepStmtCacheSize", "250")
                        addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
                        addDataSourceProperty("useServerPrepStmts", "true")
                        addDataSourceProperty("rewriteBatchedStatements", "true")
                    }

                    "postgresql" -> {
                        driverClassName = "org.postgresql.Driver"
                        jdbcUrl = "jdbc:postgresql://${config.host}:${config.port}/${config.database}?ssl=${config.useSSL}&serverTimezone=UTC"
                        username = config.username
                        password = config.password
                        maximumPoolSize = config.maxPoolSize
                        minimumIdle = config.minPoolSize
                        addDataSourceProperty("cachePrepStmts", "true")
                        addDataSourceProperty("prepStmtCacheSize", "250")
                        addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
                        addDataSourceProperty("reWriteBatchedInserts", "true")
                        addDataSourceProperty("ApplicationName", "LocoPillars")
                    }

                    else -> {
                        plugin.logger.severe("Unsupported database type: ${config.type}")
                        return false
                    }
                }

                poolName = "LocoPillars-Pool"
                connectionTimeout = config.connectionTimeoutMs
                idleTimeout = config.idleTimeoutMs
                maxLifetime = config.maxLifetimeMs
                leakDetectionThreshold = config.leakDetectionThresholdMs
                connectionTestQuery = "SELECT 1"
                isAutoCommit = true
                isIsolateInternalQueries = true
                isAllowPoolSuspension = false
                isReadOnly = false
                isRegisterMbeans = false
            }

            dataSource?.close()
            dataSource = HikariDataSource(hikariConfig)

            plugin.logger.info("HikariCP connection pool created successfully")
            plugin.logger.info("Pool configuration: Database: ${config.type}, Maximum pool size: ${hikariConfig.maximumPoolSize}")

            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to create connection pool: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    /**
     * Na init: poolsize omhoog voor SQLite
     */
    private fun updatePoolSizeAfterInit() {
        try {
            val ds = dataSource ?: return
            if (ds.maximumPoolSize == config.maxPoolSize) return
            plugin.logger.info("Increasing SQLite connection pool size to ${config.maxPoolSize}")
            ds.maximumPoolSize = config.maxPoolSize
        } catch (e: Exception) {
            plugin.logger.warning("Failed to adjust pool size after init: ${e.message}")
        }
    }

    /**
     * Test de verbinding
     */
    private fun testConnection(): Boolean {
        return try {
            getConnection().use { connection ->
                connection.isValid(5)
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Database connection test failed: ${e.message}")
            false
        }
    }

    /**
     * Initialiseer alle TableManagers
     */
    private fun initializeTableManagers() {
        playersTable = PlayersTable(this)
        statsTable = StatsTable(this)
        achievementsTable = AchievementsTable(this)
        arenaStatsTable = ArenaStatsTable(this)
        leaderboardCache = LeaderboardCacheTable(this)
        vanishTable = VanishTable(this)
        StatsEventTracker = StatsEventTracker(this)
        plugin.logger.info("Table managers initialized")
    }

    /**
     * Maak ALLE tabellen SYNCHRONOUS en in VASTE VOLGORDE aan!
     */
    private fun createTablesSync(): Boolean {
        plugin.logger.info("Creating all database tables synchronously and in fixed order...")
        val tableResults = mutableListOf<Pair<String, Boolean>>()
        try {
            tableResults += "players" to playersTable.createTable()
            tableResults += "stats" to statsTable.createTable()
            tableResults += "achievements" to achievementsTable.createTable()
            tableResults += "arena_stats" to arenaStatsTable.createTable()
            tableResults += "leaderboard_cache" to leaderboardCache.createTable()
            tableResults += "vanish" to vanishTable.createTable()
        } catch (e: Exception) {
            plugin.logger.severe("Table creation failed: ${e.message}")
            e.printStackTrace()
            return false
        }
        val failed = tableResults.filterNot { it.second }
        if (failed.isNotEmpty()) {
            plugin.logger.severe("Failed tables: ${failed.joinToString(", ") { it.first }}")
            return false
        }
        plugin.logger.info("All tables created successfully!")
        return true
    }

    /**
     * Start maintenance pas NA init!
     */
    private fun startMaintenanceTask() {
        if (!initialized) {
            plugin.logger.warning("Maintenance task not started: DB not initialized!")
            return
        }
        try {
            if (executor == null) {
                executor = Executors.newScheduledThreadPool(2) { runnable ->
                    Thread(runnable, "LocoPillars-DB-Maintenance").apply { isDaemon = true }
                }
            }
            val maintenanceRunnable = Runnable {
                try {
                    plugin.logger.fine("Starting database maintenance...")
                    val startTime = System.currentTimeMillis()
                    if (::leaderboardCache.isInitialized) leaderboardCache.updateCache()
                    if (config.cleanupEnabled) cleanupOldRecords()
                    if (::arenaStatsTable.isInitialized) arenaStatsTable.updatePopularityScores()
                    val duration = System.currentTimeMillis() - startTime
                    plugin.logger.fine("Database maintenance completed in ${duration}ms")
                } catch (e: Exception) {
                    plugin.logger.warning("Database maintenance failed: ${e.message}")
                }
            }
            maintenanceTask = executor!!.scheduleWithFixedDelay(
                maintenanceRunnable, 5L, 5L, TimeUnit.MINUTES
            )
            plugin.logger.info("Database maintenance task started (runs every 5 minutes)")
        } catch (e: Exception) {
            plugin.logger.warning("Failed to start maintenance task: ${e.message}")
        }
    }

    /**
     * Clean up oude records
     */
    fun cleanupOldRecords() {
        try {
            if (!config.cleanupEnabled) return
            val cutoffDate = LocalDateTime.now().minusDays(config.cleanupDays.toLong())
            var totalCleaned = 0
            if (::arenaStatsTable.isInitialized) {
                executeSync { connection ->
                    val sql = "DELETE FROM lp_arena_performance WHERE created_at < ?"
                    connection.prepareStatement(sql).use { statement ->
                        statement.setObject(1, cutoffDate)
                        val cleaned = statement.executeUpdate()
                        totalCleaned += cleaned
                        plugin.logger.fine("Cleaned $cleaned old performance records")
                    }
                }
            }
            if (totalCleaned > 0) {
                plugin.logger.info("Database cleanup completed: removed $totalCleaned old records")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Database cleanup failed: ${e.message}")
        }
    }

    /**
     * Connection ophalen
     */
    fun getConnection(): Connection {
        val ds = dataSource ?: throw SQLException("Database not initialized")
        return ds.connection
    }

    /**
     * Sync operatie
     */
    fun <T> executeSync(operation: (Connection) -> T): T {
        return getConnection().use { connection -> operation(connection) }
    }

    /**
     * Async operatie (pas NA init gebruiken!)
     */
    fun <T> executeAsync(operation: (Connection) -> T): CompletableFuture<T> {
        if (!initialized) throw IllegalStateException("Database not initialized")
        return CompletableFuture.supplyAsync({
            executeSync(operation)
        }, executor ?: Executors.newCachedThreadPool())
    }

    fun getDatabaseType(): String = config.type.lowercase()
    fun isConnected(): Boolean = isConnected && dataSource?.isClosed == false

    fun getPoolStats(): Map<String, Any> {
        val ds = dataSource ?: return emptyMap()
        return mapOf(
            "active_connections" to ds.hikariPoolMXBean.activeConnections,
            "idle_connections" to ds.hikariPoolMXBean.idleConnections,
            "total_connections" to ds.hikariPoolMXBean.totalConnections,
            "threads_awaiting_connection" to ds.hikariPoolMXBean.threadsAwaitingConnection,
            "pool_name" to (ds.poolName ?: "Unknown")
        )
    }

    fun getDatabaseMetrics(): Map<String, Any> {
        return try {
            val startTime = System.currentTimeMillis()
            executeSync { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT 1").use { }
                }
            }
            val responseTime = System.currentTimeMillis() - startTime
            val poolStats = getPoolStats()
            mapOf(
                "database_type" to getDatabaseType(),
                "connection_response_time_ms" to responseTime,
                "is_connected" to isConnected(),
                "pool_stats" to poolStats
            )
        } catch (e: Exception) {
            return mapOf(
                "database_type" to getDatabaseType(),
                "is_connected" to false,
                "error" to (e.message ?: "Unknown error")
            )
        }
    }

    /**
     * Shutdown
     */
    fun shutdown() {
        plugin.logger.info("Shutting down database manager...")
        try {
            maintenanceTask?.cancel(false)
            executor?.let { exec ->
                exec.shutdown()
                try {
                    if (!exec.awaitTermination(10, TimeUnit.SECONDS)) {
                        plugin.logger.warning("Executor didn't terminate gracefully, forcing shutdown...")
                        exec.shutdownNow()
                    }
                } catch (e: InterruptedException) {
                    exec.shutdownNow()
                    Thread.currentThread().interrupt()
                }
            }
            dataSource?.close()
            isConnected = false
            initialized = false
            plugin.logger.info("Database manager shut down successfully")
        } catch (e: Exception) {
            plugin.logger.warning("Error during database shutdown: ${e.message}")
        }
    }

    /**
     * Cleanup bij init failures
     */
    private fun cleanup() {
        try {
            dataSource?.close()
            executor?.shutdown()
            isConnected = false
            initialized = false
        } catch (e: Exception) {
            plugin.logger.warning("Error during cleanup: ${e.message}")
        }
    }

    /**
     * Forceer reconnect
     */
    fun reconnect(): Boolean {
        plugin.logger.info("Attempting to reconnect to database...")
        try {
            dataSource?.close()
            if (createConnectionPool(initialSetup = false) && testConnection()) {
                isConnected = true
                plugin.logger.info("Database reconnection successful!")
                return true
            }
        } catch (e: Exception) {
            plugin.logger.severe("Database reconnection failed: ${e.message}")
        }
        isConnected = false
        return false
    }

    /**
     * Health check
     */
    fun validateHealth(): Boolean {
        return try {
            if (!isConnected()) {
                plugin.logger.warning("Database health check failed: not connected")
                return false
            }
            val startTime = System.currentTimeMillis()
            executeSync { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT 1").use { it.next() }
                }
            }
            val responseTime = System.currentTimeMillis() - startTime
            if (responseTime > 5000) {
                plugin.logger.warning("Database health check: slow response time (${responseTime}ms)")
            }
            true
        } catch (e: Exception) {
            plugin.logger.warning("Database health check failed: ${e.message}")
            false
        }
    }
}