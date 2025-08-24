package org.Kloppie74.locoPillars.database.tables

import org.Kloppie74.locoPillars.database.DatabaseManager
import org.Kloppie74.locoPillars.events.LevelXPEvent
import java.sql.SQLException
import java.time.LocalDateTime
import org.bukkit.Bukkit

/**
 * Manages the player statistics table in the database.
 * Stores comprehensive game statistics for each player.
 *
 * @author Kloppie74
 * @since 1.0-SNAPSHOT
 */
class StatsTable(private val databaseManager: DatabaseManager) {

    companion object {
        const val TABLE_NAME = "lp_stats"
    }

    /**
     * Creates the stats table if it doesn't exist.
     */
    fun createTable(): Boolean {
        val sql = when (databaseManager.getDatabaseType()) {
            "sqlite", "h2" -> createTableSQLite()
            "mysql", "mariadb" -> createTableMySQL()
            "postgresql" -> createTablePostgreSQL()
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
            throw SQLException("Failed to create stats table: ${e.message}", e)
        }
    }

    private fun createTableSQLite(): String = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_id TEXT PRIMARY KEY,
            -- Game Statistics
            games_played INTEGER NOT NULL DEFAULT 0,
            games_won INTEGER NOT NULL DEFAULT 0,
            games_lost INTEGER NOT NULL DEFAULT 0,
            games_quit INTEGER NOT NULL DEFAULT 0,
            
            -- Combat Statistics
            total_kills INTEGER NOT NULL DEFAULT 0,
            total_deaths INTEGER NOT NULL DEFAULT 0,
            total_assists INTEGER NOT NULL DEFAULT 0,
            best_killstreak INTEGER NOT NULL DEFAULT 0,
            current_killstreak INTEGER NOT NULL DEFAULT 0,
            
            -- Building Statistics
            pillars_destroyed INTEGER NOT NULL DEFAULT 0,
            blocks_broken INTEGER NOT NULL DEFAULT 0,
            blocks_placed INTEGER NOT NULL DEFAULT 0,
            total_distance_moved REAL NOT NULL DEFAULT 0.0,
            
            -- Time Statistics (in seconds)
            total_playtime INTEGER NOT NULL DEFAULT 0,
            longest_game_time INTEGER NOT NULL DEFAULT 0,
            fastest_win_time INTEGER NULL,
            average_game_time INTEGER NOT NULL DEFAULT 0,
            
            -- Win Streak Statistics
            current_win_streak INTEGER NOT NULL DEFAULT 0,
            best_win_streak INTEGER NOT NULL DEFAULT 0,
            current_loss_streak INTEGER NOT NULL DEFAULT 0,
            worst_loss_streak INTEGER NOT NULL DEFAULT 0,
            
            -- Experience and Level
            experience INTEGER NOT NULL DEFAULT 0,
            level INTEGER NOT NULL DEFAULT 1,
            coins INTEGER NOT NULL DEFAULT 100,
            
            -- Damage Statistics
            damage_dealt REAL NOT NULL DEFAULT 0.0,
            damage_taken REAL NOT NULL DEFAULT 0.0,
            fall_damage_taken REAL NOT NULL DEFAULT 0.0,
            
            -- Special Statistics
            double_kills INTEGER NOT NULL DEFAULT 0,
            triple_kills INTEGER NOT NULL DEFAULT 0,
            quad_kills INTEGER NOT NULL DEFAULT 0,
            perfect_games INTEGER NOT NULL DEFAULT 0,
            comeback_wins INTEGER NOT NULL DEFAULT 0,

            -- EXTENDED ACHIEVEMENT TRIGGERS
            items_collected INTEGER NOT NULL DEFAULT 0,
            items_crafted INTEGER NOT NULL DEFAULT 0,
            team_win INTEGER NOT NULL DEFAULT 0,
            team_loss INTEGER NOT NULL DEFAULT 0,
            arenas_played INTEGER NOT NULL DEFAULT 0,
            
            -- Rankings (cached for performance)
            global_rank INTEGER NULL,
            rank_last_updated DATETIME NULL,
            
            -- Metadata
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            
            FOREIGN KEY (player_id) REFERENCES lp_players(player_id) ON DELETE CASCADE
        )
    """.trimIndent()

    private fun createTableMySQL(): String = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_id VARCHAR(36) PRIMARY KEY,
            -- Game Statistics
            games_played INT NOT NULL DEFAULT 0,
            games_won INT NOT NULL DEFAULT 0,
            games_lost INT NOT NULL DEFAULT 0,
            games_quit INT NOT NULL DEFAULT 0,
            
            -- Combat Statistics
            total_kills INT NOT NULL DEFAULT 0,
            total_deaths INT NOT NULL DEFAULT 0,
            total_assists INT NOT NULL DEFAULT 0,
            best_killstreak INT NOT NULL DEFAULT 0,
            current_killstreak INT NOT NULL DEFAULT 0,
            
            -- Building Statistics
            pillars_destroyed INT NOT NULL DEFAULT 0,
            blocks_broken INT NOT NULL DEFAULT 0,
            blocks_placed INT NOT NULL DEFAULT 0,
            total_distance_moved DOUBLE NOT NULL DEFAULT 0.0,
            
            -- Time Statistics (in seconds)
            total_playtime BIGINT NOT NULL DEFAULT 0,
            longest_game_time INT NOT NULL DEFAULT 0,
            fastest_win_time INT NULL,
            average_game_time INT NOT NULL DEFAULT 0,
            
            -- Win Streak Statistics
            current_win_streak INT NOT NULL DEFAULT 0,
            best_win_streak INT NOT NULL DEFAULT 0,
            current_loss_streak INT NOT NULL DEFAULT 0,
            worst_loss_streak INT NOT NULL DEFAULT 0,
            
            -- Experience and Level
            experience INT NOT NULL DEFAULT 0,
            level INT NOT NULL DEFAULT 1,
            coins INT NOT NULL DEFAULT 100,
            
            -- Damage Statistics
            damage_dealt DOUBLE NOT NULL DEFAULT 0.0,
            damage_taken DOUBLE NOT NULL DEFAULT 0.0,
            fall_damage_taken DOUBLE NOT NULL DEFAULT 0.0,
            
            -- Special Statistics
            double_kills INT NOT NULL DEFAULT 0,
            triple_kills INT NOT NULL DEFAULT 0,
            quad_kills INT NOT NULL DEFAULT 0,
            perfect_games INT NOT NULL DEFAULT 0,
            comeback_wins INT NOT NULL DEFAULT 0,

            -- EXTENDED ACHIEVEMENT TRIGGERS
            items_collected INT NOT NULL DEFAULT 0,
            items_crafted INT NOT NULL DEFAULT 0,
            team_win INT NOT NULL DEFAULT 0,
            team_loss INT NOT NULL DEFAULT 0,
            arenas_played INT NOT NULL DEFAULT 0,
            
            -- Rankings (cached for performance)
            global_rank INT NULL,
            rank_last_updated TIMESTAMP NULL,
            
            -- Metadata
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            
            -- Foreign Key
            FOREIGN KEY (player_id) REFERENCES lp_players(player_id) ON DELETE CASCADE,
            
            -- Indexes for performance
            INDEX idx_stats_wins (games_won DESC),
            INDEX idx_stats_winrate (games_won, games_played),
            INDEX idx_stats_kills (total_kills DESC),
            INDEX idx_stats_kd (total_kills, total_deaths),
            INDEX idx_stats_level (level DESC, experience DESC),
            INDEX idx_stats_playtime (total_playtime DESC),
            INDEX idx_stats_streak (current_win_streak DESC),
            INDEX idx_stats_pillars (pillars_destroyed DESC),
            INDEX idx_stats_rank (global_rank)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()

    private fun createTablePostgreSQL(): String = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_id UUID PRIMARY KEY,
            -- Game Statistics
            games_played INTEGER NOT NULL DEFAULT 0,
            games_won INTEGER NOT NULL DEFAULT 0,
            games_lost INTEGER NOT NULL DEFAULT 0,
            games_quit INTEGER NOT NULL DEFAULT 0,
            
            -- Combat Statistics
            total_kills INTEGER NOT NULL DEFAULT 0,
            total_deaths INTEGER NOT NULL DEFAULT 0,
            total_assists INTEGER NOT NULL DEFAULT 0,
            best_killstreak INTEGER NOT NULL DEFAULT 0,
            current_killstreak INTEGER NOT NULL DEFAULT 0,
            
            -- Building Statistics
            pillars_destroyed INTEGER NOT NULL DEFAULT 0,
            blocks_broken INTEGER NOT NULL DEFAULT 0,
            blocks_placed INTEGER NOT NULL DEFAULT 0,
            total_distance_moved DOUBLE PRECISION NOT NULL DEFAULT 0.0,
            
            -- Time Statistics (in seconds)
            total_playtime BIGINT NOT NULL DEFAULT 0,
            longest_game_time INTEGER NOT NULL DEFAULT 0,
            fastest_win_time INTEGER NULL,
            average_game_time INTEGER NOT NULL DEFAULT 0,
            
            -- Win Streak Statistics
            current_win_streak INTEGER NOT NULL DEFAULT 0,
            best_win_streak INTEGER NOT NULL DEFAULT 0,
            current_loss_streak INTEGER NOT NULL DEFAULT 0,
            worst_loss_streak INTEGER NOT NULL DEFAULT 0,
            
            -- Experience and Level
            experience INTEGER NOT NULL DEFAULT 0,
            level INTEGER NOT NULL DEFAULT 1,
            coins INTEGER NOT NULL DEFAULT 100,
            
            -- Damage Statistics
            damage_dealt DOUBLE PRECISION NOT NULL DEFAULT 0.0,
            damage_taken DOUBLE PRECISION NOT NULL DEFAULT 0.0,
            fall_damage_taken DOUBLE PRECISION NOT NULL DEFAULT 0.0,
            
            -- Special Statistics
            double_kills INTEGER NOT NULL DEFAULT 0,
            triple_kills INTEGER NOT NULL DEFAULT 0,
            quad_kills INTEGER NOT NULL DEFAULT 0,
            perfect_games INTEGER NOT NULL DEFAULT 0,
            comeback_wins INTEGER NOT NULL DEFAULT 0,

            -- EXTENDED ACHIEVEMENT TRIGGERS
            items_collected INTEGER NOT NULL DEFAULT 0,
            items_crafted INTEGER NOT NULL DEFAULT 0,
            team_win INTEGER NOT NULL DEFAULT 0,
            team_loss INTEGER NOT NULL DEFAULT 0,
            arenas_played INTEGER NOT NULL DEFAULT 0,
            
            -- Rankings (cached for performance)
            global_rank INTEGER NULL,
            rank_last_updated TIMESTAMP NULL,
            
            -- Metadata
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            
            -- Foreign Key
            FOREIGN KEY (player_id) REFERENCES lp_players(player_id) ON DELETE CASCADE
        );
        
        -- Create indexes for performance
        CREATE INDEX IF NOT EXISTS idx_stats_wins ON $TABLE_NAME (games_won DESC);
        CREATE INDEX IF NOT EXISTS idx_stats_winrate ON $TABLE_NAME (games_won, games_played);
        CREATE INDEX IF NOT EXISTS idx_stats_kills ON $TABLE_NAME (total_kills DESC);
        CREATE INDEX IF NOT EXISTS idx_stats_kd ON $TABLE_NAME (total_kills, total_deaths);
        CREATE INDEX IF NOT EXISTS idx_stats_level ON $TABLE_NAME (level DESC, experience DESC);
        CREATE INDEX IF NOT EXISTS idx_stats_playtime ON $TABLE_NAME (total_playtime DESC);
        CREATE INDEX IF NOT EXISTS idx_stats_streak ON $TABLE_NAME (current_win_streak DESC);
        CREATE INDEX IF NOT EXISTS idx_stats_pillars ON $TABLE_NAME (pillars_destroyed DESC);
        CREATE INDEX IF NOT EXISTS idx_stats_rank ON $TABLE_NAME (global_rank);
    """.trimIndent()

    /**
     * Creates initial stats record for a new player.
     * Cross-db upsert logic for duplicate player_id.
     */
    fun createPlayerStats(playerId: String): Boolean {
        val now = LocalDateTime.now()
        val sql = when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" -> """
                INSERT INTO $TABLE_NAME (player_id, experience, level, coins, created_at, updated_at)
                VALUES (?, 0, 1, 100, ?, ?)
                ON DUPLICATE KEY UPDATE
                    experience = experience,
                    level = level,
                    coins = coins,
                    updated_at = updated_at
            """.trimIndent()
            "sqlite", "h2" -> """
                INSERT OR IGNORE INTO $TABLE_NAME (player_id, experience, level, coins, created_at, updated_at)
                VALUES (?, 0, 1, 100, ?, ?)
            """.trimIndent()
            "postgresql" -> """
                INSERT INTO $TABLE_NAME (player_id, experience, level, coins, created_at, updated_at)
                VALUES (?, 0, 1, 100, ?, ?)
                ON CONFLICT (player_id) DO NOTHING
            """.trimIndent()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId)
                    statement.setObject(2, now)
                    statement.setObject(3, now)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to create stats for player $playerId: ${e.message}", e)
        }
    }

    // =================== GAME STATISTICS ===================

    fun getGamesPlayed(playerId: String): Int = getIntStat(playerId, "games_played")
    fun getGamesWon(playerId: String): Int = getIntStat(playerId, "games_won")
    fun getGamesLost(playerId: String): Int = getIntStat(playerId, "games_lost")
    fun getGamesQuit(playerId: String): Int = getIntStat(playerId, "games_quit")

    fun incrementGamesPlayed(playerId: String): Boolean = incrementStat(playerId, "games_played")
    fun incrementGamesWon(playerId: String): Boolean = incrementStat(playerId, "games_won")
    fun incrementGamesLost(playerId: String): Boolean = incrementStat(playerId, "games_lost")
    fun incrementGamesQuit(playerId: String): Boolean = incrementStat(playerId, "games_quit")

    fun getItemsCollected(playerId: String): Int = getIntStat(playerId, "items_collected")
    fun getItemsCrafted(playerId: String): Int = getIntStat(playerId, "items_crafted")
    fun getTeamWin(playerId: String): Int = getIntStat(playerId, "team_win")
    fun getTeamLoss(playerId: String): Int = getIntStat(playerId, "team_loss")
    fun getArenasPlayed(playerId: String): Int = getIntStat(playerId, "arenas_played")

    fun addItemsCollected(playerId: String, amount: Int = 1): Boolean = addIntStat(playerId, "items_collected", amount)
    fun addItemsCrafted(playerId: String, amount: Int = 1): Boolean = addIntStat(playerId, "items_crafted", amount)
    fun addTeamWin(playerId: String, amount: Int = 1): Boolean = addIntStat(playerId, "team_win", amount)
    fun addTeamLoss(playerId: String, amount: Int = 1): Boolean = addIntStat(playerId, "team_loss", amount)
    fun addArenasPlayed(playerId: String, amount: Int = 1): Boolean = addIntStat(playerId, "arenas_played", amount)

    private fun addIntStat(playerId: String, column: String, value: Int): Boolean {
        val sql = "UPDATE $TABLE_NAME SET $column = $column + ?, updated_at = ? WHERE player_id = ?"
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setInt(1, value)
                    statement.setObject(2, LocalDateTime.now())
                    statement.setString(3, playerId)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to add $column for player $playerId: ${e.message}", e)
        }
    }

    // =================== COMBAT STATISTICS ===================

    fun getTotalKills(playerId: String): Int = getIntStat(playerId, "total_kills")
    fun getTotalDeaths(playerId: String): Int = getIntStat(playerId, "total_deaths")
    fun getTotalAssists(playerId: String): Int = getIntStat(playerId, "total_assists")
    fun getBestKillstreak(playerId: String): Int = getIntStat(playerId, "best_killstreak")
    fun getCurrentKillstreak(playerId: String): Int = getIntStat(playerId, "current_killstreak")

    fun addKill(playerId: String): Boolean {
        val sql = """
            UPDATE $TABLE_NAME 
            SET total_kills = total_kills + 1,
                current_killstreak = current_killstreak + 1,
                best_killstreak = CASE 
                    WHEN current_killstreak + 1 > best_killstreak 
                    THEN current_killstreak + 1 
                    ELSE best_killstreak 
                END,
                updated_at = ?
            WHERE player_id = ?
        """.trimIndent()

        return updateWithTimestamp(sql, playerId)
    }

    fun addDeath(playerId: String): Boolean {
        val sql = """
            UPDATE $TABLE_NAME 
            SET total_deaths = total_deaths + 1,
                current_killstreak = 0,
                updated_at = ?
            WHERE player_id = ?
        """.trimIndent()

        return updateWithTimestamp(sql, playerId)
    }

    fun addAssist(playerId: String): Boolean = incrementStat(playerId, "total_assists")

    // =================== BUILDING STATISTICS ===================

    fun getPillarsDestroyed(playerId: String): Int = getIntStat(playerId, "pillars_destroyed")
    fun getBlocksBroken(playerId: String): Int = getIntStat(playerId, "blocks_broken")
    fun getBlocksPlaced(playerId: String): Int = getIntStat(playerId, "blocks_placed")
    fun getTotalDistanceMoved(playerId: String): Double = getDoubleStat(playerId, "total_distance_moved")

    fun addPillarDestroyed(playerId: String): Boolean = incrementStat(playerId, "pillars_destroyed")
    fun addBlockBroken(playerId: String): Boolean = incrementStat(playerId, "blocks_broken")
    fun addBlockPlaced(playerId: String): Boolean = incrementStat(playerId, "blocks_placed")

    fun addDistanceMoved(playerId: String, distance: Double): Boolean {
        val sql = "UPDATE $TABLE_NAME SET total_distance_moved = total_distance_moved + ?, updated_at = ? WHERE player_id = ?"

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setDouble(1, distance)
                    statement.setObject(2, LocalDateTime.now())
                    statement.setString(3, playerId)

                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to add distance moved for player $playerId: ${e.message}", e)
        }
    }

    // =================== TIME STATISTICS ===================

    fun getTotalPlayTime(playerId: String): Long = getLongStat(playerId, "total_playtime")
    fun getLongestGame(playerId: String): Long = getLongStat(playerId, "longest_game_time").toLong()
    fun getFastestWin(playerId: String): Long? = getNullableLongStat(playerId, "fastest_win_time")
    fun getAverageGameTime(playerId: String): Long = getLongStat(playerId, "average_game_time").toLong()

    fun addPlayTime(playerId: String, seconds: Long): Boolean {
        val sql = "UPDATE $TABLE_NAME SET total_playtime = total_playtime + ?, updated_at = ? WHERE player_id = ?"

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, seconds)
                    statement.setObject(2, LocalDateTime.now())
                    statement.setString(3, playerId)

                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to add play time for player $playerId: ${e.message}", e)
        }
    }

    fun updateLongestGame(playerId: String, gameTime: Long): Boolean {
        val sql = """
            UPDATE $TABLE_NAME 
            SET longest_game_time = CASE 
                WHEN ? > longest_game_time THEN ? 
                ELSE longest_game_time 
            END,
            updated_at = ?
            WHERE player_id = ?
        """.trimIndent()

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, gameTime)
                    statement.setLong(2, gameTime)
                    statement.setObject(3, LocalDateTime.now())
                    statement.setString(4, playerId)

                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update longest game for player $playerId: ${e.message}", e)
        }
    }

    fun updateFastestWin(playerId: String, winTime: Long): Boolean {
        val sql = """
            UPDATE $TABLE_NAME 
            SET fastest_win_time = CASE 
                WHEN fastest_win_time IS NULL OR ? < fastest_win_time THEN ? 
                ELSE fastest_win_time 
            END,
            updated_at = ?
            WHERE player_id = ?
        """.trimIndent()

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setLong(1, winTime)
                    statement.setLong(2, winTime)
                    statement.setObject(3, LocalDateTime.now())
                    statement.setString(4, playerId)

                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update fastest win for player $playerId: ${e.message}", e)
        }
    }

    // =================== WIN STREAK STATISTICS ===================

    fun getCurrentWinStreak(playerId: String): Int = getIntStat(playerId, "current_win_streak")
    fun getBestWinStreak(playerId: String): Int = getIntStat(playerId, "best_win_streak")
    fun getCurrentLossStreak(playerId: String): Int = getIntStat(playerId, "current_loss_streak")
    fun getWorstLossStreak(playerId: String): Int = getIntStat(playerId, "worst_loss_streak")

    fun addWin(playerId: String): Boolean {
        val sql = """
            UPDATE $TABLE_NAME 
            SET current_win_streak = current_win_streak + 1,
                best_win_streak = CASE 
                    WHEN current_win_streak + 1 > best_win_streak 
                    THEN current_win_streak + 1 
                    ELSE best_win_streak 
                END,
                current_loss_streak = 0,
                updated_at = ?
            WHERE player_id = ?
        """.trimIndent()

        return updateWithTimestamp(sql, playerId)
    }

    fun addLoss(playerId: String): Boolean {
        val sql = """
            UPDATE $TABLE_NAME 
            SET current_loss_streak = current_loss_streak + 1,
                worst_loss_streak = CASE 
                    WHEN current_loss_streak + 1 > worst_loss_streak 
                    THEN current_loss_streak + 1 
                    ELSE worst_loss_streak 
                END,
                current_win_streak = 0,
                updated_at = ?
            WHERE player_id = ?
        """.trimIndent()

        return updateWithTimestamp(sql, playerId)
    }

    // =================== EXPERIENCE AND LEVEL ===================

    fun getPlayerLevel(playerId: String): Int = getIntStat(playerId, "level")
    fun getPlayerExperience(playerId: String): Int = getIntStat(playerId, "experience")
    fun getPlayerCoins(playerId: String): Int = getIntStat(playerId, "coins")

    fun updateExperience(playerId: String, experience: Int): Boolean = updateIntStat(playerId, "experience", experience)
    fun updateCoins(playerId: String, coins: Int): Boolean = updateIntStat(playerId, "coins", coins)

    /**
     * Add experience and fire LevelXPEvent.
     * Does NOT handle level-up logic, only XP addition!
     */
    fun addExperience(playerId: String, experience: Int): Boolean {
        val logger = Bukkit.getLogger()
        try {
            val oldXP = getPlayerExperience(playerId)
            logger.info("[StatsTable] Adding XP: $experience to player $playerId (oldXP=$oldXP)")
            val sql = "UPDATE $TABLE_NAME SET experience = experience + ?, updated_at = ? WHERE player_id = ?"
            val updated = databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setInt(1, experience)
                    statement.setObject(2, LocalDateTime.now())
                    statement.setString(3, playerId)
                    statement.executeUpdate() > 0
                }
            }
            if (updated) {
                val newXP = getPlayerExperience(playerId)
                logger.info("[StatsTable] XP updated for $playerId: $oldXP + $experience = $newXP")
                logger.info("[StatsTable] Firing LevelXPEvent for $playerId: newXP=$newXP, added=$experience")
                //Bukkit.getPluginManager().callEvent(LevelXPEvent(playerId, newXP, experience))
            } else {
                logger.warning("[StatsTable] XP update for $playerId failed (updated=false)!")
            }
            return updated
        } catch (e: SQLException) {
            Bukkit.getLogger().warning("[StatsTable] Failed to add experience for player $playerId: ${e.message}")
            throw SQLException("Failed to add experience for player $playerId: ${e.message}", e)
        }
    }


    fun addCoins(playerId: String, coins: Int): Boolean {
        val sql = "UPDATE $TABLE_NAME SET coins = coins + ?, updated_at = ? WHERE player_id = ?"

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setInt(1, coins)
                    statement.setObject(2, LocalDateTime.now())
                    statement.setString(3, playerId)

                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to add coins for player $playerId: ${e.message}", e)
        }
    }

    // =================== DAMAGE STATISTICS ===================

    fun getDamageDealt(playerId: String): Double = getDoubleStat(playerId, "damage_dealt")
    fun getDamageTaken(playerId: String): Double = getDoubleStat(playerId, "damage_taken")
    fun getFallDamageTaken(playerId: String): Double = getDoubleStat(playerId, "fall_damage_taken")

    fun addDamageDealt(playerId: String, damage: Double): Boolean = addDoubleStat(playerId, "damage_dealt", damage)
    fun addDamageTaken(playerId: String, damage: Double): Boolean = addDoubleStat(playerId, "damage_taken", damage)
    fun addFallDamage(playerId: String, damage: Double): Boolean = addDoubleStat(playerId, "fall_damage_taken", damage)

    // =================== SPECIAL STATISTICS ===================

    fun getDoubleKills(playerId: String): Int = getIntStat(playerId, "double_kills")
    fun getTripleKills(playerId: String): Int = getIntStat(playerId, "triple_kills")
    fun getQuadKills(playerId: String): Int = getIntStat(playerId, "quad_kills")
    fun getPerfectGames(playerId: String): Int = getIntStat(playerId, "perfect_games")
    fun getComebackWins(playerId: String): Int = getIntStat(playerId, "comeback_wins")

    fun addDoubleKill(playerId: String): Boolean = incrementStat(playerId, "double_kills")
    fun addTripleKill(playerId: String): Boolean = incrementStat(playerId, "triple_kills")
    fun addQuadKill(playerId: String): Boolean = incrementStat(playerId, "quad_kills")
    fun addPerfectGame(playerId: String): Boolean = incrementStat(playerId, "perfect_games")
    fun addComebackWin(playerId: String): Boolean = incrementStat(playerId, "comeback_wins")

    // =================== COUNT METHODS ===================

    fun getTotalPlayersWithMinGames(minGames: Int): Int {
        val sql = "SELECT COUNT(*) as total FROM $TABLE_NAME WHERE games_played >= ?"

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setInt(1, minGames)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getInt("total")
                        } else 0
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get players with min games count: ${e.message}", e)
        }
    }

    fun getTotalPlayersWithMinDeaths(minDeaths: Int): Int {
        val sql = "SELECT COUNT(*) as total FROM $TABLE_NAME WHERE total_deaths >= ?"

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setInt(1, minDeaths)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getInt("total")
                        } else 0
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get players with min deaths count: ${e.message}", e)
        }
    }

    // =================== HELPER METHODS ===================

    fun getIntStat(playerId: String, column: String): Int {
        val sql = "SELECT $column FROM $TABLE_NAME WHERE player_id = ?"

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getInt(column)
                        } else {
                            // Create stats record if it doesn't exist
                            createPlayerStats(playerId)
                            0
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get $column for player $playerId: ${e.message}", e)
        }
    }

    fun getLongStat(playerId: String, column: String): Long {
        val sql = "SELECT $column FROM $TABLE_NAME WHERE player_id = ?"

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getLong(column)
                        } else {
                            createPlayerStats(playerId)
                            0L
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get $column for player $playerId: ${e.message}", e)
        }
    }

    fun getNullableLongStat(playerId: String, column: String): Long? {
        val sql = "SELECT $column FROM $TABLE_NAME WHERE player_id = ?"

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            val value = resultSet.getLong(column)
                            if (resultSet.wasNull()) null else value
                        } else {
                            createPlayerStats(playerId)
                            null
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get $column for player $playerId: ${e.message}", e)
        }
    }

    fun getDoubleStat(playerId: String, column: String): Double {
        val sql = "SELECT $column FROM $TABLE_NAME WHERE player_id = ?"

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getDouble(column)
                        } else {
                            createPlayerStats(playerId)
                            0.0
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get $column for player $playerId: ${e.message}", e)
        }
    }

    private fun incrementStat(playerId: String, column: String): Boolean {
        val sql = "UPDATE $TABLE_NAME SET $column = $column + 1, updated_at = ? WHERE player_id = ?"
        return updateWithTimestamp(sql, playerId)
    }

    fun updateIntStat(playerId: String, column: String, value: Int): Boolean {
        val sql = "UPDATE $TABLE_NAME SET $column = ?, updated_at = ? WHERE player_id = ?"

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setInt(1, value)
                    statement.setObject(2, LocalDateTime.now())
                    statement.setString(3, playerId)

                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update $column for player $playerId: ${e.message}", e)
        }
    }

    fun addDoubleStat(playerId: String, column: String, value: Double): Boolean {
        val sql = "UPDATE $TABLE_NAME SET $column = $column + ?, updated_at = ? WHERE player_id = ?"

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setDouble(1, value)
                    statement.setObject(2, LocalDateTime.now())
                    statement.setString(3, playerId)

                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to add $column for player $playerId: ${e.message}", e)
        }
    }

    private fun updateWithTimestamp(sql: String, playerId: String): Boolean {
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setObject(1, LocalDateTime.now())
                    statement.setString(2, playerId)

                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to execute update for player $playerId: ${e.message}", e)
        }
    }

    private fun isPrimaryKeyViolation(e: SQLException): Boolean {
        return when (databaseManager.getDatabaseType()) {
            "sqlite" -> e.message?.contains("PRIMARY KEY constraint failed") == true
            "mysql", "mariadb" -> e.errorCode == 1062
            "postgresql" -> e.sqlState == "23505"
            "h2" -> e.errorCode == 23505
            else -> false
        }
    }

    fun getAllStatsForPlayer(playerId: String): Map<String, Any?> {
        val sql = "SELECT * FROM $TABLE_NAME WHERE player_id = ?"
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId)
                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            val meta = rs.metaData
                            val map = mutableMapOf<String, Any?>()
                            for (i in 1..meta.columnCount) {
                                map[meta.getColumnLabel(i)] = rs.getObject(i)
                            }
                            map
                        } else emptyMap()
                    }
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}