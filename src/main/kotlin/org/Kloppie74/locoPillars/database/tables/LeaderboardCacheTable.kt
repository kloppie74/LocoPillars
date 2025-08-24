package org.Kloppie74.locoPillars.database.tables

import org.Kloppie74.locoPillars.database.DatabaseManager
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDateTime

/**
 * Manages leaderboard cache table for high-performance leaderboard queries.
 * Pre-calculates and caches rankings to avoid expensive real-time calculations.
 *
 * @author kloppie74
 * @since 1.0-SNAPSHOT
 */
class LeaderboardCacheTable(private val databaseManager: DatabaseManager) {

    companion object {
        const val LEADERBOARD_CACHE_TABLE = "lp_leaderboard_cache"
        const val LEADERBOARD_METADATA_TABLE = "lp_leaderboard_metadata"

        // Leaderboard types
        const val TYPE_WINS = "wins"
        const val TYPE_WINRATE = "winrate"
        const val TYPE_KILLS = "kills"
        const val TYPE_KD_RATIO = "kd"
        const val TYPE_LEVEL = "level"
        const val TYPE_PLAYTIME = "playtime"
        const val TYPE_STREAK = "streak"
        const val TYPE_PILLARS = "pillars"
    }

    /**
     * Creates leaderboard cache tables.
     */
    fun createTable(): Boolean {
        val cacheTableSql = when (databaseManager.getDatabaseType()) {
            "sqlite", "h2" -> createCacheTableSQLite()
            "mysql", "mariadb" -> createCacheTableMySQL()
            "postgresql" -> createCacheTablePostgreSQL()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        val metadataTableSql = when (databaseManager.getDatabaseType()) {
            "sqlite", "h2" -> createMetadataTableSQLite()
            "mysql", "mariadb" -> createMetadataTableMySQL()
            "postgresql" -> createMetadataTablePostgreSQL()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        return try {
            databaseManager.executeSync { connection ->
                // Create cache table
                connection.createStatement().use { statement ->
                    statement.execute(cacheTableSql)
                }

                // Create metadata table
                connection.createStatement().use { statement ->
                    statement.execute(metadataTableSql)
                }

                // Initialize metadata
                initializeMetadata(connection)

                true
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to create leaderboard cache tables: ${e.message}", e)
        }
    }

    private fun createCacheTableSQLite(): String = """
        CREATE TABLE IF NOT EXISTS $LEADERBOARD_CACHE_TABLE (
            player_id TEXT NOT NULL,
            player_name TEXT NOT NULL,
            leaderboard_type TEXT NOT NULL,
            
            -- Ranking Information
            rank_position INTEGER NOT NULL,
            rank_value REAL NOT NULL,
            rank_display_value TEXT NOT NULL,
            
            -- Additional Data for Display
            secondary_value REAL NULL,
            secondary_display TEXT NULL,
            tertiary_value REAL NULL,
            tertiary_display TEXT NULL,
            
            -- Calculated Statistics
            percentile REAL NOT NULL DEFAULT 0.0,
            score_normalized REAL NOT NULL DEFAULT 0.0,
            
            -- Change Tracking
            previous_rank INTEGER NULL,
            rank_change INTEGER NOT NULL DEFAULT 0,
            rank_change_direction TEXT NOT NULL DEFAULT 'STABLE', -- 'UP', 'DOWN', 'STABLE', 'NEW'
            
            -- Context Information
            qualifying_games INTEGER NOT NULL DEFAULT 0,
            eligible BOOLEAN NOT NULL DEFAULT 1,
            
            -- Metadata
            last_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            cache_version INTEGER NOT NULL DEFAULT 1,
            
            PRIMARY KEY (player_id, leaderboard_type),
            FOREIGN KEY (player_id) REFERENCES lp_players(player_id) ON DELETE CASCADE
        )
    """.trimIndent()

    private fun createCacheTableMySQL(): String = """
        CREATE TABLE IF NOT EXISTS $LEADERBOARD_CACHE_TABLE (
            player_id VARCHAR(36) NOT NULL,
            player_name VARCHAR(16) NOT NULL,
            leaderboard_type ENUM('wins', 'winrate', 'kills', 'kd', 'level', 'playtime', 'streak', 'pillars') NOT NULL,
            
            -- Ranking Information
            rank_position INT NOT NULL,
            rank_value DECIMAL(15,4) NOT NULL,
            rank_display_value VARCHAR(50) NOT NULL,
            
            -- Additional Data for Display
            secondary_value DECIMAL(15,4) NULL,
            secondary_display VARCHAR(50) NULL,
            tertiary_value DECIMAL(15,4) NULL,
            tertiary_display VARCHAR(50) NULL,
            
            -- Calculated Statistics
            percentile DECIMAL(5,2) NOT NULL DEFAULT 0.0,
            score_normalized DECIMAL(8,6) NOT NULL DEFAULT 0.0,
            
            -- Change Tracking
            previous_rank INT NULL,
            rank_change INT NOT NULL DEFAULT 0,
            rank_change_direction ENUM('UP', 'DOWN', 'STABLE', 'NEW') NOT NULL DEFAULT 'STABLE',
            
            -- Context Information
            qualifying_games INT NOT NULL DEFAULT 0,
            eligible BOOLEAN NOT NULL DEFAULT TRUE,
            
            -- Metadata
            last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            cache_version SMALLINT NOT NULL DEFAULT 1,
            
            PRIMARY KEY (player_id, leaderboard_type),
            FOREIGN KEY (player_id) REFERENCES lp_players(player_id) ON DELETE CASCADE,
            
            -- Optimized indexes for leaderboard queries
            INDEX idx_leaderboard_type_rank (leaderboard_type, rank_position),
            INDEX idx_leaderboard_type_value (leaderboard_type, rank_value DESC),
            INDEX idx_leaderboard_eligible (leaderboard_type, eligible, rank_position),
            INDEX idx_leaderboard_player (player_name),
            INDEX idx_leaderboard_updated (last_updated DESC),
            INDEX idx_leaderboard_percentile (leaderboard_type, percentile DESC)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()

    private fun createCacheTablePostgreSQL(): String = """
        CREATE TABLE IF NOT EXISTS $LEADERBOARD_CACHE_TABLE (
            player_id UUID NOT NULL,
            player_name VARCHAR(16) NOT NULL,
            leaderboard_type VARCHAR(20) NOT NULL,
            
            -- Ranking Information
            rank_position INTEGER NOT NULL,
            rank_value DECIMAL(15,4) NOT NULL,
            rank_display_value VARCHAR(50) NOT NULL,
            
            -- Additional Data for Display
            secondary_value DECIMAL(15,4) NULL,
            secondary_display VARCHAR(50) NULL,
            tertiary_value DECIMAL(15,4) NULL,
            tertiary_display VARCHAR(50) NULL,
            
            -- Calculated Statistics
            percentile DECIMAL(5,2) NOT NULL DEFAULT 0.0,
            score_normalized DECIMAL(8,6) NOT NULL DEFAULT 0.0,
            
            -- Change Tracking
            previous_rank INTEGER NULL,
            rank_change INTEGER NOT NULL DEFAULT 0,
            rank_change_direction VARCHAR(10) NOT NULL DEFAULT 'STABLE',
            
            -- Context Information
            qualifying_games INTEGER NOT NULL DEFAULT 0,
            eligible BOOLEAN NOT NULL DEFAULT TRUE,
            
            -- Metadata
            last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            cache_version SMALLINT NOT NULL DEFAULT 1,
            
            PRIMARY KEY (player_id, leaderboard_type),
            FOREIGN KEY (player_id) REFERENCES lp_players(player_id) ON DELETE CASCADE
        );
        
        -- Create optimized indexes
        CREATE INDEX IF NOT EXISTS idx_leaderboard_type_rank ON $LEADERBOARD_CACHE_TABLE (leaderboard_type, rank_position);
        CREATE INDEX IF NOT EXISTS idx_leaderboard_type_value ON $LEADERBOARD_CACHE_TABLE (leaderboard_type, rank_value DESC);
        CREATE INDEX IF NOT EXISTS idx_leaderboard_eligible ON $LEADERBOARD_CACHE_TABLE (leaderboard_type, eligible, rank_position);
        CREATE INDEX IF NOT EXISTS idx_leaderboard_player ON $LEADERBOARD_CACHE_TABLE (player_name);
        CREATE INDEX IF NOT EXISTS idx_leaderboard_updated ON $LEADERBOARD_CACHE_TABLE (last_updated DESC);
        CREATE INDEX IF NOT EXISTS idx_leaderboard_percentile ON $LEADERBOARD_CACHE_TABLE (leaderboard_type, percentile DESC);
    """.trimIndent()

    private fun createMetadataTableSQLite(): String = """
        CREATE TABLE IF NOT EXISTS $LEADERBOARD_METADATA_TABLE (
            leaderboard_type TEXT PRIMARY KEY,
            
            -- Update Information
            last_full_update DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_incremental_update DATETIME NULL,
            next_scheduled_update DATETIME NULL,
            
            -- Statistics
            total_entries INTEGER NOT NULL DEFAULT 0,
            eligible_entries INTEGER NOT NULL DEFAULT 0,
            qualifying_threshold INTEGER NOT NULL DEFAULT 0,
            
            -- Performance Metrics
            update_duration_ms INTEGER NOT NULL DEFAULT 0,
            cache_hit_rate REAL NOT NULL DEFAULT 0.0,
            query_count_24h INTEGER NOT NULL DEFAULT 0,
            
            -- Configuration
            update_frequency_minutes INTEGER NOT NULL DEFAULT 5,
            auto_update_enabled BOOLEAN NOT NULL DEFAULT 1,
            
            -- Version Control
            cache_version INTEGER NOT NULL DEFAULT 1,
            schema_version INTEGER NOT NULL DEFAULT 1,
            
            -- Metadata
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
    """.trimIndent()

    private fun createMetadataTableMySQL(): String = """
        CREATE TABLE IF NOT EXISTS $LEADERBOARD_METADATA_TABLE (
            leaderboard_type ENUM('wins', 'winrate', 'kills', 'kd', 'level', 'playtime', 'streak', 'pillars') PRIMARY KEY,
            
            -- Update Information
            last_full_update TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_incremental_update TIMESTAMP NULL,
            next_scheduled_update TIMESTAMP NULL,
            
            -- Statistics
            total_entries INT NOT NULL DEFAULT 0,
            eligible_entries INT NOT NULL DEFAULT 0,
            qualifying_threshold INT NOT NULL DEFAULT 0,
            
            -- Performance Metrics
            update_duration_ms INT NOT NULL DEFAULT 0,
            cache_hit_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0,
            query_count_24h INT NOT NULL DEFAULT 0,
            
            -- Configuration
            update_frequency_minutes SMALLINT NOT NULL DEFAULT 5,
            auto_update_enabled BOOLEAN NOT NULL DEFAULT TRUE,
            
            -- Version Control
            cache_version SMALLINT NOT NULL DEFAULT 1,
            schema_version SMALLINT NOT NULL DEFAULT 1,
            
            -- Metadata
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()

    private fun createMetadataTablePostgreSQL(): String = """
        CREATE TABLE IF NOT EXISTS $LEADERBOARD_METADATA_TABLE (
            leaderboard_type VARCHAR(20) PRIMARY KEY,
            
            -- Update Information
            last_full_update TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_incremental_update TIMESTAMP NULL,
            next_scheduled_update TIMESTAMP NULL,
            
            -- Statistics
            total_entries INTEGER NOT NULL DEFAULT 0,
            eligible_entries INTEGER NOT NULL DEFAULT 0,
            qualifying_threshold INTEGER NOT NULL DEFAULT 0,
            
            -- Performance Metrics
            update_duration_ms INTEGER NOT NULL DEFAULT 0,
            cache_hit_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0,
            query_count_24h INTEGER NOT NULL DEFAULT 0,
            
            -- Configuration
            update_frequency_minutes SMALLINT NOT NULL DEFAULT 5,
            auto_update_enabled BOOLEAN NOT NULL DEFAULT TRUE,
            
            -- Version Control
            cache_version SMALLINT NOT NULL DEFAULT 1,
            schema_version SMALLINT NOT NULL DEFAULT 1,
            
            -- Metadata
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
    """.trimIndent()

    /**
     * Initializes leaderboard metadata for all types.
     */
    private fun initializeMetadata(connection: Connection): Boolean {
        val leaderboardTypes = listOf(
            TYPE_WINS, TYPE_WINRATE, TYPE_KILLS, TYPE_KD_RATIO,
            TYPE_LEVEL, TYPE_PLAYTIME, TYPE_STREAK, TYPE_PILLARS
        )

        val sql = when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" ->
                "INSERT IGNORE INTO $LEADERBOARD_METADATA_TABLE (leaderboard_type, qualifying_threshold) VALUES (?, ?)"
            "sqlite", "h2" ->
                "INSERT OR IGNORE INTO $LEADERBOARD_METADATA_TABLE (leaderboard_type, qualifying_threshold) VALUES (?, ?)"
            "postgresql" ->
                "INSERT INTO $LEADERBOARD_METADATA_TABLE (leaderboard_type, qualifying_threshold) VALUES (?, ?) ON CONFLICT DO NOTHING"
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        return try {
            connection.prepareStatement(sql).use { statement ->
                leaderboardTypes.forEach { type ->
                    statement.setString(1, type)
                    statement.setInt(2, getDefaultQualifyingThreshold(type))
                    statement.addBatch()
                }
                statement.executeBatch()
            }
            true
        } catch (e: SQLException) {
            throw SQLException("Failed to initialize leaderboard metadata: ${e.message}", e)
        }
    }

    /**
     * Updates the complete leaderboard cache for all types.
     */
    fun updateCache(): Boolean {
        return try {
            val startTime = System.currentTimeMillis()

            val success = listOf(
                updateWinsLeaderboard(),
                updateWinRateLeaderboard(),
                updateKillsLeaderboard(),
                updateKDRatioLeaderboard(),
                updateLevelLeaderboard(),
                updatePlaytimeLeaderboard(),
                updateWinStreakLeaderboard(),
                updatePillarsLeaderboard()
            ).all { it }

            val duration = System.currentTimeMillis() - startTime

            if (success) {
                updateAllMetadata(duration)
            }

            success
        } catch (e: Exception) {
            throw SQLException("Failed to update leaderboard cache: ${e.message}", e)
        }
    }

    // ===== Cross-DB UPSERT for leaderboard cache =====
    private fun upsertLeaderboardCacheFromSelect(
        selectSql: String,
        leaderboardType: String,
        vararg params: Any
    ): Boolean {
        val upsertSql = when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" -> """
                REPLACE INTO $LEADERBOARD_CACHE_TABLE (
                    player_id, player_name, leaderboard_type, rank_position, rank_value, rank_display_value,
                    secondary_value, secondary_display, tertiary_value, tertiary_display,
                    percentile, qualifying_games, eligible, last_updated, cache_version
                )
                $selectSql
            """.trimIndent()
            "sqlite", "h2" -> """
                INSERT OR REPLACE INTO $LEADERBOARD_CACHE_TABLE (
                    player_id, player_name, leaderboard_type, rank_position, rank_value, rank_display_value,
                    secondary_value, secondary_display, tertiary_value, tertiary_display,
                    percentile, qualifying_games, eligible, last_updated, cache_version
                )
                $selectSql
            """.trimIndent()
            "postgresql" -> """
                INSERT INTO $LEADERBOARD_CACHE_TABLE (
                    player_id, player_name, leaderboard_type, rank_position, rank_value, rank_display_value,
                    secondary_value, secondary_display, tertiary_value, tertiary_display,
                    percentile, qualifying_games, eligible, last_updated, cache_version
                )
                $selectSql
                ON CONFLICT (player_id, leaderboard_type) DO UPDATE SET
                    player_name = EXCLUDED.player_name,
                    rank_position = EXCLUDED.rank_position,
                    rank_value = EXCLUDED.rank_value,
                    rank_display_value = EXCLUDED.rank_display_value,
                    secondary_value = EXCLUDED.secondary_value,
                    secondary_display = EXCLUDED.secondary_display,
                    tertiary_value = EXCLUDED.tertiary_value,
                    tertiary_display = EXCLUDED.tertiary_display,
                    percentile = EXCLUDED.percentile,
                    qualifying_games = EXCLUDED.qualifying_games,
                    eligible = EXCLUDED.eligible,
                    last_updated = EXCLUDED.last_updated,
                    cache_version = EXCLUDED.cache_version
            """.trimIndent()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(upsertSql).use { statement ->
                    params.forEachIndexed { i, value ->
                        when (value) {
                            is String -> statement.setString(i + 1, value)
                            is Int -> statement.setInt(i + 1, value)
                            is LocalDateTime -> statement.setObject(i + 1, value)
                            else -> statement.setObject(i + 1, value)
                        }
                    }
                    statement.executeUpdate()
                    updatePercentiles(leaderboardType)
                    true
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update $leaderboardType leaderboard: ${e.message}", e)
        }
    }

    // ===== LEADERBOARD UPDATE METHODS, using cross-db upsert =====

    private fun updateWinsLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY s.games_won DESC, s.games_played ASC) as rank_position,
                s.games_won as rank_value,
                CAST(s.games_won AS CHAR) as rank_display_value,
                s.games_played as secondary_value,
                CONCAT(s.games_played, ' played') as secondary_display,
                CASE WHEN s.games_played > 0 THEN ROUND((s.games_won * 100.0 / s.games_played), 2) ELSE 0 END as tertiary_value,
                CASE WHEN s.games_played > 0 THEN CONCAT(ROUND((s.games_won * 100.0 / s.games_played), 2), '%') ELSE '0%' END as tertiary_display,
                0.0 as percentile,
                s.games_played as qualifying_games,
                CASE WHEN s.games_played >= ? THEN 1 ELSE 0 END as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            WHERE s.games_won > 0
            ORDER BY s.games_won DESC, s.games_played ASC
        """.trimIndent()
        return upsertLeaderboardCacheFromSelect(
            selectSql,
            TYPE_WINS,
            TYPE_WINS,
            getDefaultQualifyingThreshold(TYPE_WINS),
            LocalDateTime.now()
        )
    }

    private fun updateWinRateLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY win_rate DESC, s.games_won DESC) as rank_position,
                win_rate as rank_value,
                CONCAT(ROUND(win_rate, 2), '%') as rank_display_value,
                s.games_won as secondary_value,
                CONCAT(s.games_won, ' wins') as secondary_display,
                s.games_played as tertiary_value,
                CONCAT(s.games_played, ' played') as tertiary_display,
                0.0 as percentile,
                s.games_played as qualifying_games,
                CASE WHEN s.games_played >= ? THEN 1 ELSE 0 END as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            INNER JOIN (
                SELECT player_id, CASE WHEN games_played > 0 THEN (games_won * 100.0 / games_played) ELSE 0 END as win_rate
                FROM lp_stats
            ) wr ON s.player_id = wr.player_id
            WHERE s.games_played >= ?
            ORDER BY win_rate DESC, s.games_won DESC
        """.trimIndent()
        val threshold = getDefaultQualifyingThreshold(TYPE_WINRATE)
        return upsertLeaderboardCacheFromSelect(
            selectSql,
            TYPE_WINRATE,
            TYPE_WINRATE,
            threshold,
            LocalDateTime.now(),
            threshold
        )
    }

    private fun updateKillsLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY s.total_kills DESC, s.total_deaths ASC) as rank_position,
                s.total_kills as rank_value,
                CAST(s.total_kills AS CHAR) as rank_display_value,
                s.total_deaths as secondary_value,
                CONCAT(s.total_deaths, ' deaths') as secondary_display,
                CASE WHEN s.total_deaths > 0 THEN ROUND((s.total_kills * 1.0 / s.total_deaths), 2) ELSE s.total_kills END as tertiary_value,
                CASE WHEN s.total_deaths > 0 THEN CONCAT(ROUND((s.total_kills * 1.0 / s.total_deaths), 2), ' K/D') ELSE CONCAT(s.total_kills, ' K/D') END as tertiary_display,
                0.0 as percentile,
                s.games_played as qualifying_games,
                1 as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            WHERE s.total_kills > 0
            ORDER BY s.total_kills DESC, s.total_deaths ASC
        """.trimIndent()
        return upsertLeaderboardCacheFromSelect(
            selectSql,
            TYPE_KILLS,
            TYPE_KILLS,
            LocalDateTime.now()
        )
    }

    private fun updateKDRatioLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY kd_ratio DESC, s.total_kills DESC) as rank_position,
                kd_ratio as rank_value,
                ROUND(kd_ratio, 2) as rank_display_value,
                s.total_kills as secondary_value,
                CONCAT(s.total_kills, ' kills') as secondary_display,
                s.total_deaths as tertiary_value,
                CONCAT(s.total_deaths, ' deaths') as tertiary_display,
                0.0 as percentile,
                s.games_played as qualifying_games,
                CASE WHEN s.total_deaths >= ? THEN 1 ELSE 0 END as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            INNER JOIN (
                SELECT player_id, CASE WHEN total_deaths > 0 THEN (total_kills * 1.0 / total_deaths) ELSE total_kills END as kd_ratio
                FROM lp_stats
            ) kd ON s.player_id = kd.player_id
            WHERE s.total_deaths >= ?
            ORDER BY kd_ratio DESC, s.total_kills DESC
        """.trimIndent()
        val threshold = getDefaultQualifyingThreshold(TYPE_KD_RATIO)
        return upsertLeaderboardCacheFromSelect(
            selectSql,
            TYPE_KD_RATIO,
            TYPE_KD_RATIO,
            threshold,
            LocalDateTime.now(),
            threshold
        )
    }

    private fun updateLevelLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY s.level DESC, s.experience DESC) as rank_position,
                s.level as rank_value,
                CONCAT('Level ', s.level) as rank_display_value,
                s.experience as secondary_value,
                CONCAT(s.experience, ' XP') as secondary_display,
                s.games_played as tertiary_value,
                CONCAT(s.games_played, ' games') as tertiary_display,
                0.0 as percentile,
                s.games_played as qualifying_games,
                1 as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            WHERE s.level > 1
            ORDER BY s.level DESC, s.experience DESC
        """.trimIndent()
        return upsertLeaderboardCacheFromSelect(
            selectSql,
            TYPE_LEVEL,
            TYPE_LEVEL,
            LocalDateTime.now()
        )
    }

    private fun updatePlaytimeLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY s.total_playtime DESC, s.games_played DESC) as rank_position,
                s.total_playtime as rank_value,
                CONCAT(ROUND(s.total_playtime / 3600.0, 1), 'h') as rank_display_value,
                s.games_played as secondary_value,
                CONCAT(s.games_played, ' games') as secondary_display,
                CASE WHEN s.games_played > 0 THEN s.total_playtime / s.games_played ELSE 0 END as tertiary_value,
                CASE WHEN s.games_played > 0 THEN CONCAT(ROUND(s.total_playtime / s.games_played / 60.0, 1), 'm avg') ELSE '0m avg' END as tertiary_display,
                0.0 as percentile,
                s.games_played as qualifying_games,
                1 as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            WHERE s.total_playtime > 0
            ORDER BY s.total_playtime DESC, s.games_played DESC
        """.trimIndent()
        return upsertLeaderboardCacheFromSelect(
            selectSql,
            TYPE_PLAYTIME,
            TYPE_PLAYTIME,
            LocalDateTime.now()
        )
    }

    private fun updateWinStreakLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY s.best_win_streak DESC, s.current_win_streak DESC) as rank_position,
                s.best_win_streak as rank_value,
                CONCAT(s.best_win_streak, ' best') as rank_display_value,
                s.current_win_streak as secondary_value,
                CONCAT(s.current_win_streak, ' current') as secondary_display,
                s.games_won as tertiary_value,
                CONCAT(s.games_won, ' wins') as tertiary_display,
                0.0 as percentile,
                s.games_played as qualifying_games,
                CASE WHEN s.games_played >= ? THEN 1 ELSE 0 END as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            WHERE s.best_win_streak > 0
            ORDER BY s.best_win_streak DESC, s.current_win_streak DESC
        """.trimIndent()
        return upsertLeaderboardCacheFromSelect(
            selectSql,
            TYPE_STREAK,
            TYPE_STREAK,
            getDefaultQualifyingThreshold(TYPE_STREAK),
            LocalDateTime.now()
        )
    }

    private fun updatePillarsLeaderboard(): Boolean {
        val selectSql = """
            SELECT 
                p.player_id, p.player_name, ? as leaderboard_type,
                ROW_NUMBER() OVER (ORDER BY s.pillars_destroyed DESC, s.games_played ASC) as rank_position,
                s.pillars_destroyed as rank_value,
                CAST(s.pillars_destroyed AS CHAR) as rank_display_value,
                s.games_played as secondary_value,
                CONCAT(s.games_played, ' games') as secondary_display,
                CASE WHEN s.games_played > 0 THEN ROUND(s.pillars_destroyed * 1.0 / s.games_played, 2) ELSE 0 END as tertiary_value,
                CASE WHEN s.games_played > 0 THEN CONCAT(ROUND(s.pillars_destroyed * 1.0 / s.games_played, 2), ' avg') ELSE '0 avg' END as tertiary_display,
                0.0 as percentile,
                s.games_played as qualifying_games,
                1 as eligible,
                ? as last_updated,
                1 as cache_version
            FROM lp_players p
            INNER JOIN lp_stats s ON p.player_id = s.player_id
            WHERE s.pillars_destroyed > 0
            ORDER BY s.pillars_destroyed DESC, s.games_played ASC
        """.trimIndent()
        return upsertLeaderboardCacheFromSelect(
            selectSql,
            TYPE_PILLARS,
            TYPE_PILLARS,
            LocalDateTime.now()
        )
    }

    /**
     * Updates percentiles for a leaderboard type.
     */
    private fun updatePercentiles(type: String): Boolean {
        val sql = """
            UPDATE $LEADERBOARD_CACHE_TABLE 
            SET percentile = CASE 
                WHEN total_entries > 0 THEN ((total_entries - rank_position + 1) * 100.0 / total_entries)
                ELSE 0 
            END
            FROM (
                SELECT COUNT(*) as total_entries 
                FROM $LEADERBOARD_CACHE_TABLE 
                WHERE leaderboard_type = ? AND eligible = 1
            ) totals
            WHERE leaderboard_type = ? AND eligible = 1
        """.trimIndent()

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, type)
                    statement.setString(2, type)
                    statement.executeUpdate() >= 0
                }
            }
        } catch (e: SQLException) {
            false
        }
    }

    /**
     * Updates metadata for all leaderboard types.
     */
    private fun updateAllMetadata(updateDuration: Long): Boolean {
        val sql = """
            UPDATE $LEADERBOARD_METADATA_TABLE 
            SET last_full_update = ?, 
                update_duration_ms = ?,
                total_entries = (
                    SELECT COUNT(*) FROM $LEADERBOARD_CACHE_TABLE 
                    WHERE leaderboard_type = $LEADERBOARD_METADATA_TABLE.leaderboard_type
                ),
                eligible_entries = (
                    SELECT COUNT(*) FROM $LEADERBOARD_CACHE_TABLE 
                    WHERE leaderboard_type = $LEADERBOARD_METADATA_TABLE.leaderboard_type AND eligible = 1
                ),
                updated_at = ?
        """.trimIndent()

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    val now = LocalDateTime.now()
                    statement.setObject(1, now)
                    statement.setLong(2, updateDuration)
                    statement.setObject(3, now)

                    statement.executeUpdate() >= 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update metadata: ${e.message}", e)
        }
    }

    // =================== LEADERBOARD QUERY METHODS ===================

    fun getTopPlayersByWins(limit: Int, offset: Int): List<LeaderboardEntry> {
        return getLeaderboardEntries(TYPE_WINS, limit, offset, eligibleOnly = true)
    }

    fun getTopPlayersByWinRate(limit: Int, offset: Int, minGames: Int): List<LeaderboardEntry> {
        return getLeaderboardEntries(TYPE_WINRATE, limit, offset, eligibleOnly = true)
    }

    fun getTopPlayersByKills(limit: Int, offset: Int): List<LeaderboardEntry> {
        return getLeaderboardEntries(TYPE_KILLS, limit, offset, eligibleOnly = false)
    }

    fun getTopPlayersByKDRatio(limit: Int, offset: Int, minDeaths: Int): List<LeaderboardEntry> {
        return getLeaderboardEntries(TYPE_KD_RATIO, limit, offset, eligibleOnly = true)
    }

    fun getTopPlayersByLevel(limit: Int, offset: Int): List<LeaderboardEntry> {
        return getLeaderboardEntries(TYPE_LEVEL, limit, offset, eligibleOnly = false)
    }

    fun getTopPlayersByPlaytime(limit: Int, offset: Int): List<LeaderboardEntry> {
        return getLeaderboardEntries(TYPE_PLAYTIME, limit, offset, eligibleOnly = false)
    }

    fun getTopPlayersByWinStreak(limit: Int, offset: Int): List<LeaderboardEntry> {
        return getLeaderboardEntries(TYPE_STREAK, limit, offset, eligibleOnly = true)
    }

    fun getTopPlayersByPillarsDestroyed(limit: Int, offset: Int): List<LeaderboardEntry> {
        return getLeaderboardEntries(TYPE_PILLARS, limit, offset, eligibleOnly = false)
    }

    fun getPlayerRank(playerId: String, leaderboardType: String): Int {
        val sql = """
            SELECT rank_position 
            FROM $LEADERBOARD_CACHE_TABLE 
            WHERE player_id = ? AND leaderboard_type = ?
        """.trimIndent()

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId)
                    statement.setString(2, leaderboardType)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getInt("rank_position")
                        } else 999
                    }
                }
            }
        } catch (e: SQLException) {
            999
        }
    }

    /**
     * Gets leaderboard entries with pagination.
     */
    private fun getLeaderboardEntries(type: String, limit: Int, offset: Int, eligibleOnly: Boolean): List<LeaderboardEntry> {
        val eligibilityFilter = if (eligibleOnly) "AND eligible = 1" else ""
        val sql = """
            SELECT player_id, player_name, rank_position, rank_value, rank_display_value,
                   secondary_value, secondary_display, tertiary_value, tertiary_display,
                   percentile, rank_change, rank_change_direction
            FROM $LEADERBOARD_CACHE_TABLE 
            WHERE leaderboard_type = ? $eligibilityFilter
            ORDER BY rank_position ASC 
            LIMIT ? OFFSET ?
        """.trimIndent()

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, type)
                    statement.setInt(2, limit)
                    statement.setInt(3, offset)
                    statement.executeQuery().use { resultSet ->
                        buildList {
                            while (resultSet.next()) {
                                add(mapResultSetToLeaderboardEntry(resultSet))
                            }
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get $type leaderboard entries: ${e.message}", e)
        }
    }

    private fun mapResultSetToLeaderboardEntry(resultSet: ResultSet): LeaderboardEntry {
        return LeaderboardEntry(
            playerId = resultSet.getString("player_id"),
            playerName = resultSet.getString("player_name"),
            rankPosition = resultSet.getInt("rank_position"),
            rankValue = resultSet.getDouble("rank_value"),
            rankDisplayValue = resultSet.getString("rank_display_value"),
            secondaryValue = resultSet.getObject("secondary_value") as? Double,
            secondaryDisplay = resultSet.getString("secondary_display"),
            tertiaryValue = resultSet.getObject("tertiary_value") as? Double,
            tertiaryDisplay = resultSet.getString("tertiary_display"),
            percentile = resultSet.getDouble("percentile"),
            rankChange = resultSet.getInt("rank_change"),
            rankChangeDirection = resultSet.getString("rank_change_direction")
        )
    }

    private fun getDefaultQualifyingThreshold(type: String): Int {
        return when (type) {
            TYPE_WINS -> 1
            TYPE_WINRATE -> 5
            TYPE_KILLS -> 0
            TYPE_KD_RATIO -> 3
            TYPE_LEVEL -> 0
            TYPE_PLAYTIME -> 0
            TYPE_STREAK -> 3
            TYPE_PILLARS -> 0
            else -> 0
        }
    }

    // =================== DATA CLASSES ===================

    data class LeaderboardEntry(
        val playerId: String,
        val playerName: String,
        val rankPosition: Int,
        val rankValue: Double,
        val rankDisplayValue: String,
        val secondaryValue: Double?,
        val secondaryDisplay: String?,
        val tertiaryValue: Double?,
        val tertiaryDisplay: String?,
        val percentile: Double,
        val rankChange: Int,
        val rankChangeDirection: String
    ) {
        fun getRankChangeIcon(): String {
            return when (rankChangeDirection) {
                "UP" -> "↑"
                "DOWN" -> "↓"
                "NEW" -> "★"
                else -> "="
            }
        }

        fun getFormattedRankChange(): String {
            return when {
                rankChange > 0 && rankChangeDirection == "UP" -> "↑${rankChange}"
                rankChange > 0 && rankChangeDirection == "DOWN" -> "↓${rankChange}"
                rankChangeDirection == "NEW" -> "NEW"
                else -> "="
            }
        }

        fun getPercentileDisplay(): String {
            return "Top ${String.format("%.1f", 100 - percentile)}%"
        }
    }
}