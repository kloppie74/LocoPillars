package org.Kloppie74.locoPillars.database.tables

import org.Kloppie74.locoPillars.database.DatabaseManager
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDateTime

/**
 * Manages arena-specific statistics in the database.
 * Tracks performance metrics for each arena and player combinations.
 *
 * @author Kloppie74
 * @since 1.0-SNAPSHOT
 */
class ArenaStatsTable(private val databaseManager: DatabaseManager) {

    companion object {
        const val ARENA_STATS_TABLE = "lp_arena_stats"
        const val PLAYER_ARENA_STATS_TABLE = "lp_player_arena_stats"
        const val ARENA_PERFORMANCE_TABLE = "lp_arena_performance"
    }

    /**
     * Creates all arena statistics tables.
     */
    fun createTable(): Boolean {
        val arenaStatsTableSql = when (databaseManager.getDatabaseType()) {
            "sqlite", "h2" -> createArenaStatsTableSQLite()
            "mysql", "mariadb" -> createArenaStatsTableMySQL()
            "postgresql" -> createArenaStatsTablePostgreSQL()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        val playerArenaStatsTableSql = when (databaseManager.getDatabaseType()) {
            "sqlite", "h2" -> createPlayerArenaStatsTableSQLite()
            "mysql", "mariadb" -> createPlayerArenaStatsTableMySQL()
            "postgresql" -> createPlayerArenaStatsTablePostgreSQL()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        val arenaPerformanceTableSql = when (databaseManager.getDatabaseType()) {
            "sqlite", "h2" -> createArenaPerformanceTableSQLite()
            "mysql", "mariadb" -> createArenaPerformanceTableMySQL()
            "postgresql" -> createArenaPerformanceTablePostgreSQL()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        return try {
            databaseManager.executeSync { connection ->
                // Create arena statistics table
                connection.createStatement().use { statement ->
                    statement.execute(arenaStatsTableSql)
                }

                // Create player arena statistics table
                connection.createStatement().use { statement ->
                    statement.execute(playerArenaStatsTableSql)
                }

                // Create arena performance table
                connection.createStatement().use { statement ->
                    statement.execute(arenaPerformanceTableSql)
                }

                true
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to create arena stats tables: ${e.message}", e)
        }
    }

    private fun createArenaStatsTableSQLite(): String = """
        CREATE TABLE IF NOT EXISTS $ARENA_STATS_TABLE (
            arena_name TEXT PRIMARY KEY,
            arena_display_name TEXT,
            
            -- Game Statistics
            total_games_played INTEGER NOT NULL DEFAULT 0,
            total_games_completed INTEGER NOT NULL DEFAULT 0,
            total_games_cancelled INTEGER NOT NULL DEFAULT 0,
            
            -- Player Statistics
            total_unique_players INTEGER NOT NULL DEFAULT 0,
            average_players_per_game REAL NOT NULL DEFAULT 0.0,
            peak_concurrent_players INTEGER NOT NULL DEFAULT 0,
            
            -- Time Statistics
            total_playtime_seconds INTEGER NOT NULL DEFAULT 0,
            average_game_duration REAL NOT NULL DEFAULT 0.0,
            shortest_game_duration INTEGER NOT NULL DEFAULT 999999,
            longest_game_duration INTEGER NOT NULL DEFAULT 0,
            
            -- Combat Statistics
            total_kills INTEGER NOT NULL DEFAULT 0,
            total_deaths INTEGER NOT NULL DEFAULT 0,
            average_kills_per_game REAL NOT NULL DEFAULT 0.0,
            highest_kills_in_game INTEGER NOT NULL DEFAULT 0,
            
            -- Building Statistics
            total_pillars_destroyed INTEGER NOT NULL DEFAULT 0,
            total_blocks_broken INTEGER NOT NULL DEFAULT 0,
            total_blocks_placed INTEGER NOT NULL DEFAULT 0,
            average_pillars_per_game REAL NOT NULL DEFAULT 0.0,
            
            -- Performance Metrics
            average_tps REAL NOT NULL DEFAULT 20.0,
            lowest_tps REAL NOT NULL DEFAULT 20.0,
            highest_memory_usage INTEGER NOT NULL DEFAULT 0,
            crash_count INTEGER NOT NULL DEFAULT 0,
            
            -- Popularity Metrics
            popularity_score REAL NOT NULL DEFAULT 0.0,
            rating_average REAL NOT NULL DEFAULT 0.0,
            rating_count INTEGER NOT NULL DEFAULT 0,
            
            -- Activity Metrics
            last_played DATETIME NULL,
            games_today INTEGER NOT NULL DEFAULT 0,
            games_this_week INTEGER NOT NULL DEFAULT 0,
            games_this_month INTEGER NOT NULL DEFAULT 0,
            
            -- Balance Metrics
            win_distribution_variance REAL NOT NULL DEFAULT 0.0,
            average_game_balance REAL NOT NULL DEFAULT 0.0,
            
            -- Metadata
            first_played DATETIME NULL,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
    """.trimIndent()

    private fun createArenaStatsTableMySQL(): String = """
        CREATE TABLE IF NOT EXISTS $ARENA_STATS_TABLE (
            arena_name VARCHAR(50) PRIMARY KEY,
            arena_display_name VARCHAR(100),
            
            -- Game Statistics
            total_games_played INT NOT NULL DEFAULT 0,
            total_games_completed INT NOT NULL DEFAULT 0,
            total_games_cancelled INT NOT NULL DEFAULT 0,
            
            -- Player Statistics
            total_unique_players INT NOT NULL DEFAULT 0,
            average_players_per_game DECIMAL(5,2) NOT NULL DEFAULT 0.0,
            peak_concurrent_players TINYINT NOT NULL DEFAULT 0,
            
            -- Time Statistics
            total_playtime_seconds BIGINT NOT NULL DEFAULT 0,
            average_game_duration DECIMAL(8,2) NOT NULL DEFAULT 0.0,
            shortest_game_duration INT NOT NULL DEFAULT 999999,
            longest_game_duration INT NOT NULL DEFAULT 0,
            
            -- Combat Statistics
            total_kills INT NOT NULL DEFAULT 0,
            total_deaths INT NOT NULL DEFAULT 0,
            average_kills_per_game DECIMAL(5,2) NOT NULL DEFAULT 0.0,
            highest_kills_in_game TINYINT NOT NULL DEFAULT 0,
            
            -- Building Statistics
            total_pillars_destroyed INT NOT NULL DEFAULT 0,
            total_blocks_broken BIGINT NOT NULL DEFAULT 0,
            total_blocks_placed BIGINT NOT NULL DEFAULT 0,
            average_pillars_per_game DECIMAL(5,2) NOT NULL DEFAULT 0.0,
            
            -- Performance Metrics
            average_tps DECIMAL(5,2) NOT NULL DEFAULT 20.0,
            lowest_tps DECIMAL(5,2) NOT NULL DEFAULT 20.0,
            highest_memory_usage BIGINT NOT NULL DEFAULT 0,
            crash_count INT NOT NULL DEFAULT 0,
            
            -- Popularity Metrics
            popularity_score DECIMAL(8,4) NOT NULL DEFAULT 0.0,
            rating_average DECIMAL(3,2) NOT NULL DEFAULT 0.0,
            rating_count INT NOT NULL DEFAULT 0,
            
            -- Activity Metrics
            last_played TIMESTAMP NULL,
            games_today INT NOT NULL DEFAULT 0,
            games_this_week INT NOT NULL DEFAULT 0,
            games_this_month INT NOT NULL DEFAULT 0,
            
            -- Balance Metrics
            win_distribution_variance DECIMAL(8,4) NOT NULL DEFAULT 0.0,
            average_game_balance DECIMAL(5,2) NOT NULL DEFAULT 0.0,
            
            -- Metadata
            first_played TIMESTAMP NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            
            -- Indexes for performance
            INDEX idx_arena_stats_popularity (popularity_score DESC),
            INDEX idx_arena_stats_games (total_games_played DESC),
            INDEX idx_arena_stats_rating (rating_average DESC),
            INDEX idx_arena_stats_last_played (last_played DESC),
            INDEX idx_arena_stats_activity (games_today DESC, games_this_week DESC)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()

    private fun createArenaStatsTablePostgreSQL(): String = """
        CREATE TABLE IF NOT EXISTS $ARENA_STATS_TABLE (
            arena_name VARCHAR(50) PRIMARY KEY,
            arena_display_name VARCHAR(100),
            
            -- Game Statistics
            total_games_played INTEGER NOT NULL DEFAULT 0,
            total_games_completed INTEGER NOT NULL DEFAULT 0,
            total_games_cancelled INTEGER NOT NULL DEFAULT 0,
            
            -- Player Statistics
            total_unique_players INTEGER NOT NULL DEFAULT 0,
            average_players_per_game DECIMAL(5,2) NOT NULL DEFAULT 0.0,
            peak_concurrent_players SMALLINT NOT NULL DEFAULT 0,
            
            -- Time Statistics
            total_playtime_seconds BIGINT NOT NULL DEFAULT 0,
            average_game_duration DECIMAL(8,2) NOT NULL DEFAULT 0.0,
            shortest_game_duration INTEGER NOT NULL DEFAULT 999999,
            longest_game_duration INTEGER NOT NULL DEFAULT 0,
            
            -- Combat Statistics
            total_kills INTEGER NOT NULL DEFAULT 0,
            total_deaths INTEGER NOT NULL DEFAULT 0,
            average_kills_per_game DECIMAL(5,2) NOT NULL DEFAULT 0.0,
            highest_kills_in_game SMALLINT NOT NULL DEFAULT 0,
            
            -- Building Statistics
            total_pillars_destroyed INTEGER NOT NULL DEFAULT 0,
            total_blocks_broken BIGINT NOT NULL DEFAULT 0,
            total_blocks_placed BIGINT NOT NULL DEFAULT 0,
            average_pillars_per_game DECIMAL(5,2) NOT NULL DEFAULT 0.0,
            
            -- Performance Metrics
            average_tps DECIMAL(5,2) NOT NULL DEFAULT 20.0,
            lowest_tps DECIMAL(5,2) NOT NULL DEFAULT 20.0,
            highest_memory_usage BIGINT NOT NULL DEFAULT 0,
            crash_count INTEGER NOT NULL DEFAULT 0,
            
            -- Popularity Metrics
            popularity_score DECIMAL(8,4) NOT NULL DEFAULT 0.0,
            rating_average DECIMAL(3,2) NOT NULL DEFAULT 0.0,
            rating_count INTEGER NOT NULL DEFAULT 0,
            
            -- Activity Metrics
            last_played TIMESTAMP NULL,
            games_today INTEGER NOT NULL DEFAULT 0,
            games_this_week INTEGER NOT NULL DEFAULT 0,
            games_this_month INTEGER NOT NULL DEFAULT 0,
            
            -- Balance Metrics
            win_distribution_variance DECIMAL(8,4) NOT NULL DEFAULT 0.0,
            average_game_balance DECIMAL(5,2) NOT NULL DEFAULT 0.0,
            
            -- Metadata
            first_played TIMESTAMP NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
        
        -- Create indexes
        CREATE INDEX IF NOT EXISTS idx_arena_stats_popularity ON $ARENA_STATS_TABLE (popularity_score DESC);
        CREATE INDEX IF NOT EXISTS idx_arena_stats_games ON $ARENA_STATS_TABLE (total_games_played DESC);
        CREATE INDEX IF NOT EXISTS idx_arena_stats_rating ON $ARENA_STATS_TABLE (rating_average DESC);
        CREATE INDEX IF NOT EXISTS idx_arena_stats_last_played ON $ARENA_STATS_TABLE (last_played DESC);
        CREATE INDEX IF NOT EXISTS idx_arena_stats_activity ON $ARENA_STATS_TABLE (games_today DESC, games_this_week DESC);
    """.trimIndent()

    private fun createPlayerArenaStatsTableSQLite(): String = """
        CREATE TABLE IF NOT EXISTS $PLAYER_ARENA_STATS_TABLE (
            player_id TEXT NOT NULL,
            arena_name TEXT NOT NULL,
            
            -- Game Statistics
            games_played INTEGER NOT NULL DEFAULT 0,
            games_won INTEGER NOT NULL DEFAULT 0,
            games_lost INTEGER NOT NULL DEFAULT 0,
            games_quit INTEGER NOT NULL DEFAULT 0,
            
            -- Performance Statistics
            total_playtime_seconds INTEGER NOT NULL DEFAULT 0,
            best_placement INTEGER NULL,
            worst_placement INTEGER NULL,
            average_placement REAL NOT NULL DEFAULT 0.0,
            
            -- Combat Statistics
            total_kills INTEGER NOT NULL DEFAULT 0,
            total_deaths INTEGER NOT NULL DEFAULT 0,
            total_assists INTEGER NOT NULL DEFAULT 0,
            best_killstreak INTEGER NOT NULL DEFAULT 0,
            
            -- Building Statistics
            pillars_destroyed INTEGER NOT NULL DEFAULT 0,
            blocks_broken INTEGER NOT NULL DEFAULT 0,
            blocks_placed INTEGER NOT NULL DEFAULT 0,
            
            -- Time Records
            fastest_win_time INTEGER NULL,
            longest_game_time INTEGER NOT NULL DEFAULT 0,
            
            -- Win Streaks
            current_win_streak INTEGER NOT NULL DEFAULT 0,
            best_win_streak INTEGER NOT NULL DEFAULT 0,
            current_loss_streak INTEGER NOT NULL DEFAULT 0,
            
            -- Experience and Rating
            arena_rating REAL NOT NULL DEFAULT 1000.0,
            rating_peak REAL NOT NULL DEFAULT 1000.0,
            rating_change_total INTEGER NOT NULL DEFAULT 0,
            
            -- Special Achievements in Arena
            perfect_games INTEGER NOT NULL DEFAULT 0,
            comeback_wins INTEGER NOT NULL DEFAULT 0,
            dominant_wins INTEGER NOT NULL DEFAULT 0,
            
            -- Activity Tracking
            first_played DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_played DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            favorite_spawn_position INTEGER NULL,
            
            -- Metadata
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            
            PRIMARY KEY (player_id, arena_name),
            FOREIGN KEY (player_id) REFERENCES lp_players(player_id) ON DELETE CASCADE
        )
    """.trimIndent()

    private fun createPlayerArenaStatsTableMySQL(): String = """
        CREATE TABLE IF NOT EXISTS $PLAYER_ARENA_STATS_TABLE (
            player_id VARCHAR(36) NOT NULL,
            arena_name VARCHAR(50) NOT NULL,
            
            -- Game Statistics
            games_played INT NOT NULL DEFAULT 0,
            games_won INT NOT NULL DEFAULT 0,
            games_lost INT NOT NULL DEFAULT 0,
            games_quit INT NOT NULL DEFAULT 0,
            
            -- Performance Statistics
            total_playtime_seconds BIGINT NOT NULL DEFAULT 0,
            best_placement TINYINT NULL,
            worst_placement TINYINT NULL,
            average_placement DECIMAL(4,2) NOT NULL DEFAULT 0.0,
            
            -- Combat Statistics
            total_kills INT NOT NULL DEFAULT 0,
            total_deaths INT NOT NULL DEFAULT 0,
            total_assists INT NOT NULL DEFAULT 0,
            best_killstreak TINYINT NOT NULL DEFAULT 0,
            
            -- Building Statistics
            pillars_destroyed INT NOT NULL DEFAULT 0,
            blocks_broken BIGINT NOT NULL DEFAULT 0,
            blocks_placed BIGINT NOT NULL DEFAULT 0,
            
            -- Time Records
            fastest_win_time INT NULL,
            longest_game_time INT NOT NULL DEFAULT 0,
            
            -- Win Streaks
            current_win_streak INT NOT NULL DEFAULT 0,
            best_win_streak INT NOT NULL DEFAULT 0,
            current_loss_streak INT NOT NULL DEFAULT 0,
            
            -- Experience and Rating
            arena_rating DECIMAL(8,2) NOT NULL DEFAULT 1000.0,
            rating_peak DECIMAL(8,2) NOT NULL DEFAULT 1000.0,
            rating_change_total INT NOT NULL DEFAULT 0,
            
            -- Special Achievements in Arena
            perfect_games INT NOT NULL DEFAULT 0,
            comeback_wins INT NOT NULL DEFAULT 0,
            dominant_wins INT NOT NULL DEFAULT 0,
            
            -- Activity Tracking
            first_played TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_played TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            favorite_spawn_position TINYINT NULL,
            
            -- Metadata
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            
            PRIMARY KEY (player_id, arena_name),
            FOREIGN KEY (player_id) REFERENCES lp_players(player_id) ON DELETE CASCADE,
            
            -- Indexes
            INDEX idx_player_arena_player (player_id),
            INDEX idx_player_arena_arena (arena_name),
            INDEX idx_player_arena_rating (arena_rating DESC),
            INDEX idx_player_arena_wins (games_won DESC),
            INDEX idx_player_arena_winrate (games_won, games_played),
            INDEX idx_player_arena_last_played (last_played DESC)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()

    private fun createPlayerArenaStatsTablePostgreSQL(): String = """
        CREATE TABLE IF NOT EXISTS $PLAYER_ARENA_STATS_TABLE (
            player_id UUID NOT NULL,
            arena_name VARCHAR(50) NOT NULL,
            
            -- Game Statistics
            games_played INTEGER NOT NULL DEFAULT 0,
            games_won INTEGER NOT NULL DEFAULT 0,
            games_lost INTEGER NOT NULL DEFAULT 0,
            games_quit INTEGER NOT NULL DEFAULT 0,
            
            -- Performance Statistics
            total_playtime_seconds BIGINT NOT NULL DEFAULT 0,
            best_placement SMALLINT NULL,
            worst_placement SMALLINT NULL,
            average_placement DECIMAL(4,2) NOT NULL DEFAULT 0.0,
            
            -- Combat Statistics
            total_kills INTEGER NOT NULL DEFAULT 0,
            total_deaths INTEGER NOT NULL DEFAULT 0,
            total_assists INTEGER NOT NULL DEFAULT 0,
            best_killstreak SMALLINT NOT NULL DEFAULT 0,
            
            -- Building Statistics
            pillars_destroyed INTEGER NOT NULL DEFAULT 0,
            blocks_broken BIGINT NOT NULL DEFAULT 0,
            blocks_placed BIGINT NOT NULL DEFAULT 0,
            
            -- Time Records
            fastest_win_time INTEGER NULL,
            longest_game_time INTEGER NOT NULL DEFAULT 0,
            
            -- Win Streaks
            current_win_streak INTEGER NOT NULL DEFAULT 0,
            best_win_streak INTEGER NOT NULL DEFAULT 0,
            current_loss_streak INTEGER NOT NULL DEFAULT 0,
            
            -- Experience and Rating
            arena_rating DECIMAL(8,2) NOT NULL DEFAULT 1000.0,
            rating_peak DECIMAL(8,2) NOT NULL DEFAULT 1000.0,
            rating_change_total INTEGER NOT NULL DEFAULT 0,
            
            -- Special Achievements in Arena
            perfect_games INTEGER NOT NULL DEFAULT 0,
            comeback_wins INTEGER NOT NULL DEFAULT 0,
            dominant_wins INTEGER NOT NULL DEFAULT 0,
            
            -- Activity Tracking
            first_played TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_played TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            favorite_spawn_position SMALLINT NULL,
            
            -- Metadata
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            
            PRIMARY KEY (player_id, arena_name),
            FOREIGN KEY (player_id) REFERENCES lp_players(player_id) ON DELETE CASCADE
        );
        
        -- Create indexes
        CREATE INDEX IF NOT EXISTS idx_player_arena_player ON $PLAYER_ARENA_STATS_TABLE (player_id);
        CREATE INDEX IF NOT EXISTS idx_player_arena_arena ON $PLAYER_ARENA_STATS_TABLE (arena_name);
        CREATE INDEX IF NOT EXISTS idx_player_arena_rating ON $PLAYER_ARENA_STATS_TABLE (arena_rating DESC);
        CREATE INDEX IF NOT EXISTS idx_player_arena_wins ON $PLAYER_ARENA_STATS_TABLE (games_won DESC);
        CREATE INDEX IF NOT EXISTS idx_player_arena_winrate ON $PLAYER_ARENA_STATS_TABLE (games_won, games_played);
        CREATE INDEX IF NOT EXISTS idx_player_arena_last_played ON $PLAYER_ARENA_STATS_TABLE (last_played DESC);
    """.trimIndent()

    private fun createArenaPerformanceTableSQLite(): String = """
        CREATE TABLE IF NOT EXISTS $ARENA_PERFORMANCE_TABLE (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            arena_name TEXT NOT NULL,
            
            -- Performance Sample Data
            sample_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            tps REAL NOT NULL,
            memory_usage INTEGER NOT NULL,
            active_players INTEGER NOT NULL DEFAULT 0,
            
            -- Game State at Sample Time
            game_state TEXT NULL,
            game_duration INTEGER NULL,
            
            -- Server Load Metrics
            cpu_usage REAL NULL,
            chunk_count INTEGER NULL,
            entity_count INTEGER NULL,
            
            -- Network Metrics
            player_ping_average REAL NULL,
            packet_loss_rate REAL NULL,
            
            -- Metadata
            server_version TEXT NULL,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            
            FOREIGN KEY (arena_name) REFERENCES lp_arena_stats(arena_name) ON DELETE CASCADE
        )
    """.trimIndent()

    private fun createArenaPerformanceTableMySQL(): String = """
        CREATE TABLE IF NOT EXISTS $ARENA_PERFORMANCE_TABLE (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            arena_name VARCHAR(50) NOT NULL,
            
            -- Performance Sample Data
            sample_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            tps DECIMAL(5,2) NOT NULL,
            memory_usage BIGINT NOT NULL,
            active_players TINYINT NOT NULL DEFAULT 0,
            
            -- Game State at Sample Time
            game_state ENUM('WAITING', 'COUNTDOWN', 'CAGE_COUNTDOWN', 'ACTIVE', 'ENDED') NULL,
            game_duration INT NULL,
            
            -- Server Load Metrics
            cpu_usage DECIMAL(5,2) NULL,
            chunk_count INT NULL,
            entity_count INT NULL,
            
            -- Network Metrics
            player_ping_average DECIMAL(6,2) NULL,
            packet_loss_rate DECIMAL(5,4) NULL,
            
            -- Metadata
            server_version VARCHAR(50) NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            
            -- Foreign Key
            FOREIGN KEY (arena_name) REFERENCES lp_arena_stats(arena_name) ON DELETE CASCADE,
            
            -- Indexes
            INDEX idx_arena_performance_arena (arena_name),
            INDEX idx_arena_performance_time (sample_time DESC),
            INDEX idx_arena_performance_tps (tps),
            INDEX idx_arena_performance_memory (memory_usage DESC)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()

    private fun createArenaPerformanceTablePostgreSQL(): String = """
        CREATE TABLE IF NOT EXISTS $ARENA_PERFORMANCE_TABLE (
            id BIGSERIAL PRIMARY KEY,
            arena_name VARCHAR(50) NOT NULL,
            
            -- Performance Sample Data
            sample_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            tps DECIMAL(5,2) NOT NULL,
            memory_usage BIGINT NOT NULL,
            active_players SMALLINT NOT NULL DEFAULT 0,
            
            -- Game State at Sample Time
            game_state VARCHAR(20) NULL,
            game_duration INTEGER NULL,
            
            -- Server Load Metrics
            cpu_usage DECIMAL(5,2) NULL,
            chunk_count INTEGER NULL,
            entity_count INTEGER NULL,
            
            -- Network Metrics
            player_ping_average DECIMAL(6,2) NULL,
            packet_loss_rate DECIMAL(5,4) NULL,
            
            -- Metadata
            server_version VARCHAR(50) NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            
            -- Foreign Key
            FOREIGN KEY (arena_name) REFERENCES lp_arena_stats(arena_name) ON DELETE CASCADE
        );
        
        -- Create indexes
        CREATE INDEX IF NOT EXISTS idx_arena_performance_arena ON $ARENA_PERFORMANCE_TABLE (arena_name);
        CREATE INDEX IF NOT EXISTS idx_arena_performance_time ON $ARENA_PERFORMANCE_TABLE (sample_time DESC);
        CREATE INDEX IF NOT EXISTS idx_arena_performance_tps ON $ARENA_PERFORMANCE_TABLE (tps);
        CREATE INDEX IF NOT EXISTS idx_arena_performance_memory ON $ARENA_PERFORMANCE_TABLE (memory_usage DESC);
    """.trimIndent()

    /**
     * Creates or updates arena statistics (UPSERT cross-db).
     */
    fun createOrUpdateArenaStats(arenaName: String, displayName: String?): Boolean {
        val now = LocalDateTime.now()
        val sql = when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" -> """
                INSERT INTO $ARENA_STATS_TABLE (arena_name, arena_display_name, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    arena_display_name = VALUES(arena_display_name),
                    updated_at = VALUES(updated_at)
            """.trimIndent()
            "sqlite", "h2" -> """
                INSERT OR REPLACE INTO $ARENA_STATS_TABLE (arena_name, arena_display_name, created_at, updated_at)
                VALUES (?, ?, COALESCE((SELECT created_at FROM $ARENA_STATS_TABLE WHERE arena_name = ?), ?), ?)
            """.trimIndent()
            "postgresql" -> """
                INSERT INTO $ARENA_STATS_TABLE (arena_name, arena_display_name, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (arena_name) DO UPDATE SET
                    arena_display_name = EXCLUDED.arena_display_name,
                    updated_at = EXCLUDED.updated_at
            """.trimIndent()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, arenaName)
                    statement.setString(2, displayName)
                    if (databaseManager.getDatabaseType() == "sqlite" || databaseManager.getDatabaseType() == "h2") {
                        statement.setString(3, arenaName)
                        statement.setObject(4, now)
                        statement.setObject(5, now)
                    } else {
                        statement.setObject(3, now)
                        statement.setObject(4, now)
                    }
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to create/update arena stats for $arenaName: ${e.message}", e)
        }
    }

    /**
     * Updates arena game completion statistics.
     * (Compatible for all DBs - only UPDATE)
     */
    fun updateArenaGameStats(arenaName: String, gameStats: ArenaGameUpdate): Boolean {
        val sql = """
            UPDATE $ARENA_STATS_TABLE SET
                total_games_played = total_games_played + 1,
                total_games_completed = total_games_completed + ?,
                total_games_cancelled = total_games_cancelled + ?,
                total_playtime_seconds = total_playtime_seconds + ?,
                total_kills = total_kills + ?,
                total_deaths = total_deaths + ?,
                total_pillars_destroyed = total_pillars_destroyed + ?,
                total_blocks_broken = total_blocks_broken + ?,
                total_blocks_placed = total_blocks_placed + ?,
                shortest_game_duration = CASE 
                    WHEN ? > 0 AND ? < shortest_game_duration THEN ? 
                    ELSE shortest_game_duration 
                END,
                longest_game_duration = CASE 
                    WHEN ? > longest_game_duration THEN ? 
                    ELSE longest_game_duration 
                END,
                highest_kills_in_game = CASE 
                    WHEN ? > highest_kills_in_game THEN ? 
                    ELSE highest_kills_in_game 
                END,
                last_played = ?,
                games_today = games_today + 1,
                games_this_week = games_this_week + 1,
                games_this_month = games_this_month + 1,
                updated_at = ?
            WHERE arena_name = ?
        """.trimIndent()

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    val now = LocalDateTime.now()
                    statement.setInt(1, if (gameStats.completed) 1 else 0)
                    statement.setInt(2, if (gameStats.cancelled) 1 else 0)
                    statement.setLong(3, gameStats.duration)
                    statement.setInt(4, gameStats.totalKills)
                    statement.setInt(5, gameStats.totalDeaths)
                    statement.setInt(6, gameStats.pillarsDestroyed)
                    statement.setLong(7, gameStats.blocksBroken)
                    statement.setLong(8, gameStats.blocksPlaced)
                    statement.setLong(9, gameStats.duration)
                    statement.setLong(10, gameStats.duration)
                    statement.setLong(11, gameStats.duration)
                    statement.setLong(12, gameStats.duration)
                    statement.setLong(13, gameStats.duration)
                    statement.setInt(14, gameStats.highestKills)
                    statement.setInt(15, gameStats.highestKills)
                    statement.setObject(16, now)
                    statement.setObject(17, now)
                    statement.setString(18, arenaName)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update arena game stats for $arenaName: ${e.message}", e)
        }
    }

    /**
     * Updates arena performance metrics.
     */
    fun updateArenaPerformance(arenaName: String, tps: Double, memoryUsage: Long): Boolean {
        val sql = """
            UPDATE $ARENA_STATS_TABLE SET
                average_tps = (average_tps + ?) / 2,
                lowest_tps = CASE 
                    WHEN ? < lowest_tps THEN ? 
                    ELSE lowest_tps 
                END,
                highest_memory_usage = CASE 
                    WHEN ? > highest_memory_usage THEN ? 
                    ELSE highest_memory_usage 
                END,
                updated_at = ?
            WHERE arena_name = ?
        """.trimIndent()

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setDouble(1, tps)
                    statement.setDouble(2, tps)
                    statement.setDouble(3, tps)
                    statement.setLong(4, memoryUsage)
                    statement.setLong(5, memoryUsage)
                    statement.setObject(6, LocalDateTime.now())
                    statement.setString(7, arenaName)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update arena performance for $arenaName: ${e.message}", e)
        }
    }

    /**
     * Records detailed performance sample.
     */
    fun recordPerformanceSample(sample: ArenaPerformanceSample): Boolean {
        val sql = """
            INSERT INTO $ARENA_PERFORMANCE_TABLE (
                arena_name, sample_time, tps, memory_usage, active_players,
                game_state, game_duration, cpu_usage, chunk_count, entity_count,
                player_ping_average, packet_loss_rate, server_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent()

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, sample.arenaName)
                    statement.setObject(2, sample.sampleTime)
                    statement.setDouble(3, sample.tps)
                    statement.setLong(4, sample.memoryUsage)
                    statement.setInt(5, sample.activePlayers)
                    statement.setString(6, sample.gameState)
                    statement.setObject(7, sample.gameDuration)
                    statement.setObject(8, sample.cpuUsage)
                    statement.setObject(9, sample.chunkCount)
                    statement.setObject(10, sample.entityCount)
                    statement.setObject(11, sample.playerPingAverage)
                    statement.setObject(12, sample.packetLossRate)
                    statement.setString(13, sample.serverVersion)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to record performance sample for ${sample.arenaName}: ${e.message}", e)
        }
    }

    // =================== PLAYER ARENA STATISTICS METHODS ===================

    /**
     * Creates or updates player arena statistics (cross-db UPSERT).
     */
    fun createOrUpdatePlayerArenaStats(playerId: String, arenaName: String): Boolean {
        val sql = when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" -> """
                INSERT IGNORE INTO $PLAYER_ARENA_STATS_TABLE (player_id, arena_name, first_played, last_played)
                VALUES (?, ?, ?, ?)
            """.trimIndent()
            "sqlite", "h2" -> """
                INSERT OR IGNORE INTO $PLAYER_ARENA_STATS_TABLE (player_id, arena_name, first_played, last_played)
                VALUES (?, ?, ?, ?)
            """.trimIndent()
            "postgresql" -> """
                INSERT INTO $PLAYER_ARENA_STATS_TABLE (player_id, arena_name, first_played, last_played)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (player_id, arena_name) DO NOTHING
            """.trimIndent()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    val now = LocalDateTime.now()
                    statement.setString(1, playerId)
                    statement.setString(2, arenaName)
                    statement.setObject(3, now)
                    statement.setObject(4, now)
                    statement.executeUpdate() >= 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to create/update player arena stats: ${e.message}", e)
        }
    }

    /**
     * Updates player arena game results.
     * (Compatible for all DBs)
     */
    fun updatePlayerArenaGameResult(
        playerId: String,
        arenaName: String,
        gameResult: PlayerArenaGameResult
    ): Boolean {
        val sql = """
            UPDATE $PLAYER_ARENA_STATS_TABLE SET
                games_played = games_played + 1,
                games_won = games_won + ?,
                games_lost = games_lost + ?,
                games_quit = games_quit + ?,
                total_playtime_seconds = total_playtime_seconds + ?,
                total_kills = total_kills + ?,
                total_deaths = total_deaths + ?,
                total_assists = total_assists + ?,
                pillars_destroyed = pillars_destroyed + ?,
                blocks_broken = blocks_broken + ?,
                blocks_placed = blocks_placed + ?,
                best_placement = CASE 
                    WHEN best_placement IS NULL OR ? < best_placement THEN ? 
                    ELSE best_placement 
                END,
                worst_placement = CASE 
                    WHEN worst_placement IS NULL OR ? > worst_placement THEN ? 
                    ELSE worst_placement 
                END,
                best_killstreak = CASE 
                    WHEN ? > best_killstreak THEN ? 
                    ELSE best_killstreak 
                END,
                fastest_win_time = CASE 
                    WHEN ? = 1 AND (fastest_win_time IS NULL OR ? < fastest_win_time) THEN ? 
                    ELSE fastest_win_time 
                END,
                longest_game_time = CASE 
                    WHEN ? > longest_game_time THEN ? 
                    ELSE longest_game_time 
                END,
                current_win_streak = CASE 
                    WHEN ? = 1 THEN current_win_streak + 1 
                    ELSE 0 
                END,
                best_win_streak = CASE 
                    WHEN ? = 1 AND current_win_streak + 1 > best_win_streak THEN current_win_streak + 1 
                    ELSE best_win_streak 
                END,
                current_loss_streak = CASE 
                    WHEN ? = 0 THEN current_loss_streak + 1 
                    ELSE 0 
                END,
                perfect_games = perfect_games + ?,
                comeback_wins = comeback_wins + ?,
                dominant_wins = dominant_wins + ?,
                last_played = ?,
                updated_at = ?
            WHERE player_id = ? AND arena_name = ?
        """.trimIndent()

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    val now = LocalDateTime.now()
                    statement.setInt(1, if (gameResult.won) 1 else 0)
                    statement.setInt(2, if (gameResult.lost) 1 else 0)
                    statement.setInt(3, if (gameResult.quit) 1 else 0)
                    statement.setLong(4, gameResult.playtime)
                    statement.setInt(5, gameResult.kills)
                    statement.setInt(6, gameResult.deaths)
                    statement.setInt(7, gameResult.assists)
                    statement.setInt(8, gameResult.pillarsDestroyed)
                    statement.setLong(9, gameResult.blocksBroken)
                    statement.setLong(10, gameResult.blocksPlaced)
                    statement.setInt(11, gameResult.placement)
                    statement.setInt(12, gameResult.placement)
                    statement.setInt(13, gameResult.placement)
                    statement.setInt(14, gameResult.placement)
                    statement.setInt(15, gameResult.killstreak)
                    statement.setInt(16, gameResult.killstreak)
                    statement.setInt(17, if (gameResult.won) 1 else 0)
                    statement.setLong(18, gameResult.gameTime)
                    statement.setLong(19, gameResult.gameTime)
                    statement.setLong(20, gameResult.gameTime)
                    statement.setLong(21, gameResult.gameTime)
                    statement.setInt(22, if (gameResult.won) 1 else 0)
                    statement.setInt(23, if (gameResult.won) 1 else 0)
                    statement.setInt(24, if (gameResult.lost) 1 else 0)
                    statement.setInt(25, if (gameResult.perfectGame) 1 else 0)
                    statement.setInt(26, if (gameResult.comebackWin) 1 else 0)
                    statement.setInt(27, if (gameResult.dominantWin) 1 else 0)
                    statement.setObject(28, now)
                    statement.setObject(29, now)
                    statement.setString(30, playerId)
                    statement.setString(31, arenaName)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update player arena game result: ${e.message}", e)
        }
    }

    // ... All SELECT methods unchanged (compatible for all DBs) ...

    /**
     * Calculates and updates popularity scores for all arenas.
     * SQLITE uses 'datetime', others use CURRENT_TIMESTAMP
     */
    fun updatePopularityScores(): Boolean {
        val sql = when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" -> """
            UPDATE $ARENA_STATS_TABLE SET
                popularity_score = (
                    (total_games_played * 0.3) +
                    (total_unique_players * 0.2) +
                    (average_players_per_game * 10 * 0.2) +
                    (rating_average * 20 * 0.2) +
                    (CASE WHEN last_played > NOW() - INTERVAL 7 DAY THEN 100 ELSE 0 END * 0.1)
                ),
                updated_at = ?
        """.trimIndent()
            "postgresql" -> """
            UPDATE $ARENA_STATS_TABLE SET
                popularity_score = (
                    (total_games_played * 0.3) +
                    (total_unique_players * 0.2) +
                    (average_players_per_game * 10 * 0.2) +
                    (rating_average * 20 * 0.2) +
                    (CASE WHEN last_played > CURRENT_TIMESTAMP - INTERVAL '7 days' THEN 100 ELSE 0 END * 0.1)
                ),
                updated_at = ?
        """.trimIndent()
            "sqlite", "h2" -> """
            UPDATE $ARENA_STATS_TABLE SET
                popularity_score = (
                    (total_games_played * 0.3) +
                    (total_unique_players * 0.2) +
                    (average_players_per_game * 10 * 0.2) +
                    (rating_average * 20 * 0.2) +
                    (CASE WHEN last_played > datetime('now', '-7 days') THEN 100 ELSE 0 END * 0.1)
                ),
                updated_at = ?
        """.trimIndent()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setObject(1, LocalDateTime.now())
                    statement.executeUpdate() >= 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update popularity scores: ${e.message}", e)
        }
    }

    private fun mapResultSetToArenaStatsRecord(resultSet: ResultSet): ArenaStatsRecord {
        return ArenaStatsRecord(
            arenaName = resultSet.getString("arena_name"),
            arenaDisplayName = resultSet.getString("arena_display_name"),
            totalGamesPlayed = resultSet.getInt("total_games_played"),
            totalUniquePlayers = resultSet.getInt("total_unique_players"),
            averagePlayersPerGame = resultSet.getDouble("average_players_per_game"),
            ratingAverage = resultSet.getDouble("rating_average"),
            popularityScore = resultSet.getDouble("popularity_score"),
            lastPlayed = resultSet.getObject("last_played", LocalDateTime::class.java)
        )
    }

    // =================== DATA CLASSES ===================

    data class ArenaGameUpdate(
        val completed: Boolean,
        val cancelled: Boolean,
        val duration: Long,
        val totalKills: Int,
        val totalDeaths: Int,
        val pillarsDestroyed: Int,
        val blocksBroken: Long,
        val blocksPlaced: Long,
        val highestKills: Int
    )

    data class PlayerArenaGameResult(
        val won: Boolean,
        val lost: Boolean,
        val quit: Boolean,
        val placement: Int,
        val kills: Int,
        val deaths: Int,
        val assists: Int,
        val killstreak: Int,
        val pillarsDestroyed: Int,
        val blocksBroken: Long,
        val blocksPlaced: Long,
        val playtime: Long,
        val gameTime: Long,
        val perfectGame: Boolean,
        val comebackWin: Boolean,
        val dominantWin: Boolean
    )

    data class ArenaPerformanceSample(
        val arenaName: String,
        val sampleTime: LocalDateTime,
        val tps: Double,
        val memoryUsage: Long,
        val activePlayers: Int,
        val gameState: String?,
        val gameDuration: Int?,
        val cpuUsage: Double?,
        val chunkCount: Int?,
        val entityCount: Int?,
        val playerPingAverage: Double?,
        val packetLossRate: Double?,
        val serverVersion: String?
    )

    data class ArenaStatsRecord(
        val arenaName: String,
        val arenaDisplayName: String?,
        val totalGamesPlayed: Int,
        val totalUniquePlayers: Int,
        val averagePlayersPerGame: Double,
        val ratingAverage: Double,
        val popularityScore: Double,
        val lastPlayed: LocalDateTime?
    )
}