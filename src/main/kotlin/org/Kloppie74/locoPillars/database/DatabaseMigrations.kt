package org.Kloppie74.locoPillars.database

import org.bukkit.plugin.Plugin
import java.sql.Connection
import java.sql.SQLException
import java.time.LocalDateTime

/**
 * Comprehensive database migration system for LocoPillars.
 * Handles schema updates, data migrations, and version control.
 *
 * @author Kloppie74
 * @since 1.0-SNAPSHOT
 */
class DatabaseMigrations(
    private val databaseManager: DatabaseManager,
    private val plugin: Plugin
) {

    companion object {
        const val MIGRATIONS_TABLE = "lp_migrations"
        const val CURRENT_SCHEMA_VERSION = 5
        const val MIGRATION_LOCK_TIMEOUT = 30000L // 30 seconds
    }

    private val migrations = mutableMapOf<Int, Migration>()

    init {
        registerMigrations()
    }

    /**
     * Creates the migrations tracking table.
     */
    private fun createMigrationsTable(): Boolean {
        val sql = when (databaseManager.getDatabaseType()) {
            "sqlite", "h2" -> createMigrationsTableSQLite()
            "mysql", "mariadb" -> createMigrationsTableMySQL()
            "postgresql" -> createMigrationsTablePostgreSQL()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        return try {
            databaseManager.executeSync { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(sql)
                    true
                }
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to create migrations table: ${e.message}")
            false
        }
    }

    private fun createMigrationsTableSQLite(): String = """
        CREATE TABLE IF NOT EXISTS $MIGRATIONS_TABLE (
            migration_id INTEGER PRIMARY KEY,
            migration_name TEXT NOT NULL,
            migration_description TEXT,
            schema_version INTEGER NOT NULL,
            executed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            execution_time_ms INTEGER NOT NULL DEFAULT 0,
            checksum TEXT NULL,
            success BOOLEAN NOT NULL DEFAULT 1,
            error_message TEXT NULL,
            rollback_sql TEXT NULL,
            created_by TEXT NOT NULL DEFAULT 'system'
        )
    """.trimIndent()

    private fun createMigrationsTableMySQL(): String = """
        CREATE TABLE IF NOT EXISTS $MIGRATIONS_TABLE (
            migration_id INT PRIMARY KEY,
            migration_name VARCHAR(100) NOT NULL,
            migration_description TEXT,
            schema_version INT NOT NULL,
            executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            execution_time_ms INT NOT NULL DEFAULT 0,
            checksum VARCHAR(64) NULL,
            success BOOLEAN NOT NULL DEFAULT TRUE,
            error_message TEXT NULL,
            rollback_sql LONGTEXT NULL,
            created_by VARCHAR(50) NOT NULL DEFAULT 'system',
            
            UNIQUE KEY unique_migration (migration_id),
            INDEX idx_migrations_version (schema_version),
            INDEX idx_migrations_executed (executed_at DESC)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()

    private fun createMigrationsTablePostgreSQL(): String = """
        CREATE TABLE IF NOT EXISTS $MIGRATIONS_TABLE (
            migration_id INTEGER PRIMARY KEY,
            migration_name VARCHAR(100) NOT NULL,
            migration_description TEXT,
            schema_version INTEGER NOT NULL,
            executed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            execution_time_ms INTEGER NOT NULL DEFAULT 0,
            checksum VARCHAR(64) NULL,
            success BOOLEAN NOT NULL DEFAULT TRUE,
            error_message TEXT NULL,
            rollback_sql TEXT NULL,
            created_by VARCHAR(50) NOT NULL DEFAULT 'system'
        );
        
        CREATE INDEX IF NOT EXISTS idx_migrations_version ON $MIGRATIONS_TABLE (schema_version);
        CREATE INDEX IF NOT EXISTS idx_migrations_executed ON $MIGRATIONS_TABLE (executed_at DESC);
    """.trimIndent()

    /**
     * Registers all available migrations.
     */
    private fun registerMigrations() {
        // Migration 1: Initial Schema (automatically handled by table creation)
        migrations[1] = Migration(
            id = 1,
            name = "initial_schema",
            description = "Create initial database schema with all tables",
            schemaVersion = 1,
            sql = "",
            rollbackSql = null
        ) { _, _ ->
            // Tables are created by individual table managers
            true
        }

        // Migration 2: Add player display names and enhance indexes
        migrations[2] = Migration(
            id = 2,
            name = "enhance_player_data",
            description = "Add display names and improve player table indexes",
            schemaVersion = 2,
            sql = getEnhancePlayerDataSQL(),
            rollbackSql = getEnhancePlayerDataRollbackSQL()
        ) { connection, migration ->
            executeEnhancePlayerDataMigration(connection, migration)
        }

        // Migration 3: Add achievement progress tracking
        migrations[3] = Migration(
            id = 3,
            name = "achievement_progress",
            description = "Add achievement progress tracking for progressive achievements",
            schemaVersion = 3,
            sql = getAchievementProgressSQL(),
            rollbackSql = getAchievementProgressRollbackSQL()
        ) { connection, migration ->
            executeAchievementProgressMigration(connection, migration)
        }

        // Migration 4: Arena performance enhancements
        migrations[4] = Migration(
            id = 4,
            name = "arena_performance",
            description = "Add arena performance monitoring and rating systems",
            schemaVersion = 4,
            sql = getArenaPerformanceSQL(),
            rollbackSql = getArenaPerformanceRollbackSQL()
        ) { connection, migration ->
            executeArenaPerformanceMigration(connection, migration)
        }

        // Migration 5: Leaderboard cache optimizations
        migrations[5] = Migration(
            id = 5,
            name = "leaderboard_optimization",
            description = "Optimize leaderboard cache with better indexing and percentiles",
            schemaVersion = 5,
            sql = getLeaderboardOptimizationSQL(),
            rollbackSql = getLeaderboardOptimizationRollbackSQL()
        ) { connection, migration ->
            executeLeaderboardOptimizationMigration(connection, migration)
        }
    }

    /**
     * Runs all pending migrations.
     */
    fun runMigrations(): Boolean {
        plugin.logger.info("Starting database migrations...")

        try {
            // Create migrations table if it doesn't exist
            if (!createMigrationsTable()) {
                plugin.logger.severe("Failed to create migrations table!")
                return false
            }

            // Get current schema version
            val currentVersion = getCurrentSchemaVersion()
            plugin.logger.info("Current schema version: $currentVersion")
            plugin.logger.info("Target schema version: $CURRENT_SCHEMA_VERSION")

            if (currentVersion >= CURRENT_SCHEMA_VERSION) {
                plugin.logger.info("Database is up to date!")
                return true
            }

            // Acquire migration lock
            if (!acquireMigrationLock()) {
                plugin.logger.severe("Could not acquire migration lock! Another migration may be running.")
                return false
            }

            try {
                // Run pending migrations
                val pendingMigrations = migrations.filter { it.key > currentVersion }
                    .toSortedMap()

                plugin.logger.info("Found ${pendingMigrations.size} pending migrations")

                for ((migrationId, migration) in pendingMigrations) {
                    plugin.logger.info("Executing migration $migrationId: ${migration.name}")

                    val startTime = System.currentTimeMillis()
                    val success = executeMigration(migration)
                    val executionTime = System.currentTimeMillis() - startTime

                    // Record migration result
                    recordMigrationResult(migration, success, executionTime, null)

                    if (!success) {
                        plugin.logger.severe("Migration $migrationId failed!")
                        return false
                    }

                    plugin.logger.info("Migration $migrationId completed successfully in ${executionTime}ms")
                }

                plugin.logger.info("All migrations completed successfully!")
                return true

            } finally {
                releaseMigrationLock()
            }

        } catch (e: Exception) {
            plugin.logger.severe("Migration failed with exception: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Executes a single migration.
     */
    private fun executeMigration(migration: Migration): Boolean {
        return try {
            databaseManager.executeSync { connection ->
                // Start transaction
                val autoCommit = connection.autoCommit
                connection.autoCommit = false

                try {
                    // Execute migration
                    val success = migration.execute(connection, migration)

                    if (success) {
                        connection.commit()
                        plugin.logger.info("Migration ${migration.id} committed successfully")
                    } else {
                        connection.rollback()
                        plugin.logger.warning("Migration ${migration.id} rolled back due to failure")
                    }

                    success
                } catch (e: Exception) {
                    connection.rollback()
                    plugin.logger.severe("Migration ${migration.id} failed: ${e.message}")
                    e.printStackTrace()
                    false
                } finally {
                    connection.autoCommit = autoCommit
                }
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to execute migration ${migration.id}: ${e.message}")
            false
        }
    }

    /**
     * Gets the current schema version from the database.
     */
    private fun getCurrentSchemaVersion(): Int {
        val sql = "SELECT COALESCE(MAX(schema_version), 0) as version FROM $MIGRATIONS_TABLE WHERE success = true"

        return try {
            databaseManager.executeSync { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(sql).use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getInt("version")
                        } else 0
                    }
                }
            }
        } catch (e: SQLException) {
            // Migrations table doesn't exist yet
            0
        }
    }

    /**
     * Records the result of a migration.
     */
    private fun recordMigrationResult(
        migration: Migration,
        success: Boolean,
        executionTime: Long,
        errorMessage: String?
    ): Boolean {
        val sql = """
            INSERT INTO $MIGRATIONS_TABLE (
                migration_id, migration_name, migration_description, schema_version,
                executed_at, execution_time_ms, success, error_message, created_by
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setInt(1, migration.id)
                    statement.setString(2, migration.name)
                    statement.setString(3, migration.description)
                    statement.setInt(4, migration.schemaVersion)
                    statement.setObject(5, LocalDateTime.now())
                    statement.setLong(6, executionTime)
                    statement.setBoolean(7, success)
                    statement.setString(8, errorMessage)
                    statement.setString(9, "kloppie74")

                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            plugin.logger.warning("Failed to record migration result: ${e.message}")
            false
        }
    }

    /**
     * Acquires a migration lock to prevent concurrent migrations.
     */
    private fun acquireMigrationLock(): Boolean {
        // Simple implementation - in production you might want distributed locking
        return try {
            val lockFile = plugin.dataFolder.resolve("migration.lock")
            if (lockFile.exists()) {
                val lockTime = lockFile.lastModified()
                val now = System.currentTimeMillis()

                // Check if lock is stale (older than timeout)
                if (now - lockTime > MIGRATION_LOCK_TIMEOUT) {
                    plugin.logger.warning("Removing stale migration lock")
                    lockFile.delete()
                } else {
                    plugin.logger.warning("Migration lock exists and is recent")
                    return false
                }
            }

            // Create lock file
            lockFile.parentFile?.mkdirs()
            lockFile.writeText("Migration started at: ${LocalDateTime.now()}")
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to acquire migration lock: ${e.message}")
            false
        }
    }

    /**
     * Releases the migration lock.
     */
    private fun releaseMigrationLock() {
        try {
            val lockFile = plugin.dataFolder.resolve("migration.lock")
            if (lockFile.exists()) {
                lockFile.delete()
                plugin.logger.info("Migration lock released")
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to release migration lock: ${e.message}")
        }
    }

    // =================== MIGRATION IMPLEMENTATIONS ===================

    private fun getEnhancePlayerDataSQL(): String {
        return when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" -> """
                ALTER TABLE lp_players 
                ADD COLUMN IF NOT EXISTS display_name VARCHAR(32) AFTER player_name;
                
                CREATE INDEX IF NOT EXISTS idx_players_display_name ON lp_players (display_name);
            """.trimIndent()
            "postgresql" -> """
                ALTER TABLE lp_players 
                ADD COLUMN IF NOT EXISTS display_name VARCHAR(32);
                
                CREATE INDEX IF NOT EXISTS idx_players_display_name ON lp_players (display_name);
            """.trimIndent()
            else -> """
                -- SQLite - column already exists in current schema
                CREATE INDEX IF NOT EXISTS idx_players_display_name ON lp_players (display_name);
            """.trimIndent()
        }
    }

    private fun getEnhancePlayerDataRollbackSQL(): String = """
        DROP INDEX IF EXISTS idx_players_display_name;
    """.trimIndent()

    private fun executeEnhancePlayerDataMigration(connection: Connection, migration: Migration): Boolean {
        return try {
            // Check if display_name column exists
            val hasDisplayName = checkColumnExists(connection, "lp_players", "display_name")

            if (!hasDisplayName && databaseManager.getDatabaseType() in listOf("mysql", "mariadb", "postgresql")) {
                connection.createStatement().use { statement ->
                    statement.execute("ALTER TABLE lp_players ADD COLUMN display_name VARCHAR(32)")
                }
                plugin.logger.info("Added display_name column to lp_players")
            }

            // Create index
            connection.createStatement().use { statement ->
                statement.execute("CREATE INDEX IF NOT EXISTS idx_players_display_name ON lp_players (display_name)")
            }

            // Update existing players with display names
            connection.prepareStatement("UPDATE lp_players SET display_name = player_name WHERE display_name IS NULL").use { statement ->
                val updated = statement.executeUpdate()
                plugin.logger.info("Updated $updated players with display names")
            }

            true
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to enhance player data: ${e.message}")
            false
        }
    }

    private fun getAchievementProgressSQL(): String = """
        -- Achievement progress table is created by AchievementsTable
        -- This migration ensures it exists and populates initial data
    """.trimIndent()

    private fun getAchievementProgressRollbackSQL(): String = """
        DROP TABLE IF EXISTS lp_achievement_progress;
    """.trimIndent()

    private fun executeAchievementProgressMigration(connection: Connection, migration: Migration): Boolean {
        return try {
            // Check if achievement progress table exists
            if (!checkTableExists(connection, "lp_achievement_progress")) {
                plugin.logger.info("Achievement progress table doesn't exist - will be created by AchievementsTable")
            }

            // Initialize progress for existing players with progressive achievements
            val sql = """
                INSERT OR IGNORE INTO lp_achievement_progress (player_id, achievement_id, current_progress, target_progress)
                SELECT s.player_id, ad.achievement_id, 
                       CASE ad.requirement_type
                           WHEN 'GAMES_PLAYED' THEN s.games_played
                           WHEN 'WINS' THEN s.games_won
                           WHEN 'KILLS' THEN s.total_kills
                           WHEN 'PILLARS_DESTROYED' THEN s.pillars_destroyed
                           ELSE 0
                       END as current_progress,
                       COALESCE(ad.target_value, 1) as target_progress
                FROM lp_stats s
                CROSS JOIN lp_achievement_definitions ad
                WHERE ad.achievement_type = 'PROGRESSIVE'
                  AND ad.is_active = 1
            """.trimIndent()

            try {
                connection.createStatement().use { statement ->
                    val inserted = statement.executeUpdate(sql)
                    plugin.logger.info("Initialized achievement progress for $inserted entries")
                }
            } catch (e: SQLException) {
                // Table might not exist yet, which is fine
                plugin.logger.info("Skipping achievement progress initialization - tables not ready")
            }

            true
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to set up achievement progress: ${e.message}")
            false
        }
    }

    private fun getArenaPerformanceSQL(): String = """
        -- Arena performance enhancements are handled by ArenaStatsTable
        -- This migration ensures performance monitoring is active
    """.trimIndent()

    private fun getArenaPerformanceRollbackSQL(): String = """
        DROP TABLE IF EXISTS lp_arena_performance;
        ALTER TABLE lp_arena_stats DROP COLUMN IF EXISTS popularity_score;
    """.trimIndent()

    private fun executeArenaPerformanceMigration(connection: Connection, migration: Migration): Boolean {
        return try {
            // Add popularity_score column if it doesn't exist
            if (!checkColumnExists(connection, "lp_arena_stats", "popularity_score")) {
                val alterSql = when (databaseManager.getDatabaseType()) {
                    "mysql", "mariadb" -> "ALTER TABLE lp_arena_stats ADD COLUMN popularity_score DECIMAL(8,4) NOT NULL DEFAULT 0.0"
                    "postgresql" -> "ALTER TABLE lp_arena_stats ADD COLUMN popularity_score DECIMAL(8,4) NOT NULL DEFAULT 0.0"
                    else -> "ALTER TABLE lp_arena_stats ADD COLUMN popularity_score REAL NOT NULL DEFAULT 0.0"
                }

                connection.createStatement().use { statement ->
                    statement.execute(alterSql)
                }
                plugin.logger.info("Added popularity_score column to lp_arena_stats")
            }

            // Calculate initial popularity scores
            val updateSql = """
                UPDATE lp_arena_stats SET popularity_score = (
                    (total_games_played * 0.4) +
                    (total_unique_players * 0.3) +
                    (average_players_per_game * 10 * 0.3)
                ) WHERE total_games_played > 0
            """.trimIndent()

            connection.createStatement().use { statement ->
                val updated = statement.executeUpdate(updateSql)
                plugin.logger.info("Updated popularity scores for $updated arenas")
            }

            true
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to enhance arena performance: ${e.message}")
            false
        }
    }

    private fun getLeaderboardOptimizationSQL(): String = """
        -- Leaderboard cache optimizations
        CREATE INDEX IF NOT EXISTS idx_leaderboard_percentile ON lp_leaderboard_cache (leaderboard_type, percentile DESC);
        CREATE INDEX IF NOT EXISTS idx_leaderboard_normalized ON lp_leaderboard_cache (leaderboard_type, score_normalized DESC);
    """.trimIndent()

    private fun getLeaderboardOptimizationRollbackSQL(): String = """
        DROP INDEX IF EXISTS idx_leaderboard_percentile;
        DROP INDEX IF EXISTS idx_leaderboard_normalized;
    """.trimIndent()

    private fun executeLeaderboardOptimizationMigration(connection: Connection, migration: Migration): Boolean {
        return try {
            // Add new indexes for better leaderboard performance
            connection.createStatement().use { statement ->
                statement.execute("CREATE INDEX IF NOT EXISTS idx_leaderboard_percentile ON lp_leaderboard_cache (leaderboard_type, percentile DESC)")
                statement.execute("CREATE INDEX IF NOT EXISTS idx_leaderboard_normalized ON lp_leaderboard_cache (leaderboard_type, score_normalized DESC)")
            }

            val normalizeSql = when (databaseManager.getDatabaseType()) {
                "sqlite" -> """
                UPDATE lp_leaderboard_cache 
                SET score_normalized = (
                    CASE 
                        WHEN leaderboard_type = 'winrate' THEN rank_value / 100.0
                        WHEN leaderboard_type = 'kd' THEN 
                            CASE 
                                WHEN rank_value / 10.0 < 1.0 THEN rank_value / 10.0
                                ELSE 1.0
                            END
                        ELSE
                            CASE
                                WHEN rank_value / (SELECT MAX(rank_value) FROM lp_leaderboard_cache lc2 WHERE lc2.leaderboard_type = lp_leaderboard_cache.leaderboard_type) < 1.0
                                    THEN rank_value / (SELECT MAX(rank_value) FROM lp_leaderboard_cache lc2 WHERE lc2.leaderboard_type = lp_leaderboard_cache.leaderboard_type)
                                    ELSE 1.0
                            END
                    END
                )
                WHERE score_normalized = 0.0
            """
                else -> """
                UPDATE lp_leaderboard_cache 
                SET score_normalized = (
                    CASE 
                        WHEN leaderboard_type = 'winrate' THEN rank_value / 100.0
                        WHEN leaderboard_type = 'kd' THEN LEAST(rank_value / 10.0, 1.0)
                        ELSE LEAST(rank_value / (SELECT MAX(rank_value) FROM lp_leaderboard_cache lc2 WHERE lc2.leaderboard_type = lp_leaderboard_cache.leaderboard_type), 1.0)
                    END
                )
                WHERE score_normalized = 0.0
            """
            }

            connection.createStatement().use { statement ->
                val updated = statement.executeUpdate(normalizeSql)
                plugin.logger.info("Normalized scores for $updated leaderboard entries")
            }

            plugin.logger.info("Leaderboard optimization migration completed")
            true
        } catch (e: SQLException) {
            plugin.logger.severe("Failed to optimize leaderboards: ${e.message}")
            false
        }
    }

    // =================== UTILITY METHODS ===================

    /**
     * Checks if a table exists in the database.
     */
    private fun checkTableExists(connection: Connection, tableName: String): Boolean {
        return try {
            val metadata = connection.metaData
            val tables = metadata.getTables(null, null, tableName, arrayOf("TABLE"))
            tables.next()
        } catch (e: SQLException) {
            false
        }
    }

    /**
     * Checks if a column exists in a table.
     */
    private fun checkColumnExists(connection: Connection, tableName: String, columnName: String): Boolean {
        return try {
            val metadata = connection.metaData
            val columns = metadata.getColumns(null, null, tableName, columnName)
            columns.next()
        } catch (e: SQLException) {
            false
        }
    }

    /**
     * Gets migration history for debugging.
     */
    fun getMigrationHistory(): List<MigrationRecord> {
        val sql = """
            SELECT migration_id, migration_name, schema_version, executed_at, 
                   execution_time_ms, success, error_message
            FROM $MIGRATIONS_TABLE 
            ORDER BY executed_at DESC
        """.trimIndent()

        return try {
            databaseManager.executeSync { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(sql).use { resultSet ->
                        buildList {
                            while (resultSet.next()) {
                                add(
                                    MigrationRecord(
                                        id = resultSet.getInt("migration_id"),
                                        name = resultSet.getString("migration_name"),
                                        schemaVersion = resultSet.getInt("schema_version"),
                                        executedAt = resultSet.getObject("executed_at", LocalDateTime::class.java),
                                        executionTimeMs = resultSet.getLong("execution_time_ms"),
                                        success = resultSet.getBoolean("success"),
                                        errorMessage = resultSet.getString("error_message")
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            plugin.logger.warning("Failed to get migration history: ${e.message}")
            emptyList()
        }
    }

    /**
     * Validates database integrity after migrations.
     */
    /**
     * Validates database integrity after migrations.
     */
    fun validateDatabaseIntegrity(): Boolean {
        plugin.logger.info("Validating database integrity...")

        val requiredTables = listOf(
            "lp_players", "lp_stats",
            "lp_achievement_definitions", "lp_player_achievements", "lp_achievement_progress",
            "lp_arena_stats", "lp_player_arena_stats", "lp_arena_performance",
            "lp_leaderboard_cache", "lp_leaderboard_metadata", "lp_migrations"
        )

        return try {
            databaseManager.executeSync { connection ->
                val metadata = connection.metaData
                val tables = metadata.getTables(null, null, null, arrayOf("TABLE"))

                val existingTables = mutableSetOf<String>()
                while (tables.next()) {
                    existingTables.add(tables.getString("TABLE_NAME").lowercase())
                }

                val missingTables = requiredTables.filter { it.lowercase() !in existingTables }

                if (missingTables.isNotEmpty()) {
                    plugin.logger.severe("Missing required tables: ${missingTables.joinToString(", ")}")
                    false
                } else {
                    plugin.logger.info("Database integrity validation passed!")
                    true
                }
            }
        } catch (e: SQLException) {
            plugin.logger.severe("Database integrity validation failed: ${e.message}")
            false
        }
    }

    // =================== DATA CLASSES ===================

    data class Migration(
        val id: Int,
        val name: String,
        val description: String,
        val schemaVersion: Int,
        val sql: String,
        val rollbackSql: String?,
        val execute: (Connection, Migration) -> Boolean
    )

    data class MigrationRecord(
        val id: Int,
        val name: String,
        val schemaVersion: Int,
        val executedAt: LocalDateTime,
        val executionTimeMs: Long,
        val success: Boolean,
        val errorMessage: String?
    ) {
        fun getFormattedExecutionTime(): String {
            return when {
                executionTimeMs < 1000 -> "${executionTimeMs}ms"
                executionTimeMs < 60000 -> "${executionTimeMs / 1000.0}s"
                else -> "${executionTimeMs / 60000.0}m"
            }
        }

        fun getStatusIcon(): String {
            return if (success) "✅" else "❌"
        }
    }
}