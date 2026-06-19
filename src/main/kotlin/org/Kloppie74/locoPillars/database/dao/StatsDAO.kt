package org.Kloppie74.locoPillars.database.dao

import org.Kloppie74.locoPillars.database.DatabaseManager
import org.Kloppie74.locoPillars.database.models.PlayerStats
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDateTime

/**
 * Data Access Object for player statistics.
 *
 * CHANGELOG (v2.0):
 * - Added all missing stat methods (addAssist, addDamageDealt, etc.)
 * - Added updateFastestWin and updateLongestGame methods
 * - Added dedicated methods for specific game stats
 * - Improved error handling with proper SQLException throws
 * - Added comprehensive documentation
 * - Consistent method naming and structure
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
object StatsDAO {

    // =================== BASIC CRUD OPERATIONS ===================

    /**
     * Creates a new stats record for a player with default values.
     */
    fun create(playerId: String): Boolean {
        val sql = """
            INSERT INTO lp_stats (player_id, experience, level, coins, created_at, updated_at)
            VALUES (?, 0, 1, 100, ?, ?)
        """
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    val now = LocalDateTime.now()
                    ps.setString(1, playerId)
                    ps.setObject(2, now)
                    ps.setObject(3, now)
                    val affected = ps.executeUpdate()
                    conn.commit()
                    affected > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to create stats for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Finds stats by player ID.
     */
    fun findById(playerId: String): PlayerStats? {
        val sql = "SELECT * FROM lp_stats WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rowToStats(rs) else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to find stats for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Gets all stats for a player as a map.
     */
    fun getAllStatsForPlayer(playerId: String): Map<String, Any?> {
        val sql = "SELECT * FROM lp_stats WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) {
                            val meta = rs.metaData
                            val map = mutableMapOf<String, Any?>()
                            for (i in 1..meta.columnCount) {
                                map[meta.getColumnLabel(i)] = rs.getObject(i)
                            }
                            map
                        } else {
                            emptyMap()
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get all stats for player $playerId: ${e.message}", e)
        }
    }

    // =================== GENERIC STAT OPERATIONS ===================

    /**
     * Increments a stat column by 1.
     */
    fun incrementStat(playerId: String, column: String): Boolean {
        val sql = "UPDATE lp_stats SET $column = $column + 1, updated_at = ? WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, LocalDateTime.now())
                    ps.setString(2, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to increment $column for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Adds an integer value to a stat column.
     */
    fun addIntStat(playerId: String, column: String, value: Int): Boolean {
        val sql = "UPDATE lp_stats SET $column = $column + ?, updated_at = ? WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, value)
                    ps.setObject(2, LocalDateTime.now())
                    ps.setString(3, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to add $value to $column for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Adds a double value to a stat column.
     */
    fun addDoubleStat(playerId: String, column: String, value: Double): Boolean {
        val sql = "UPDATE lp_stats SET $column = $column + ?, updated_at = ? WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setDouble(1, value)
                    ps.setObject(2, LocalDateTime.now())
                    ps.setString(3, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to add $value to $column for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Updates an integer stat to a specific value.
     */
    fun updateIntStat(playerId: String, column: String, value: Int): Boolean {
        val sql = "UPDATE lp_stats SET $column = ?, updated_at = ? WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, value)
                    ps.setObject(2, LocalDateTime.now())
                    ps.setString(3, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update $column to $value for player $playerId: ${e.message}", e)
        }
    }

    // =================== STAT GETTERS ===================

    fun getIntStat(playerId: String, column: String): Int {
        val sql = "SELECT $column FROM lp_stats WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt(column) else 0
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get $column for player $playerId: ${e.message}", e)
        }
    }

    fun getDoubleStat(playerId: String, column: String): Double {
        val sql = "SELECT $column FROM lp_stats WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getDouble(column) else 0.0
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get $column for player $playerId: ${e.message}", e)
        }
    }

    fun getLongStat(playerId: String, column: String): Long {
        val sql = "SELECT $column FROM lp_stats WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getLong(column) else 0L
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get $column for player $playerId: ${e.message}", e)
        }
    }

    // =================== COMBAT STATS ===================

    /**
     * Adds a kill and updates killstreak.
     */
    fun addKill(playerId: String): Boolean {
        val sql = """
            UPDATE lp_stats 
            SET total_kills = total_kills + 1,
                current_killstreak = current_killstreak + 1,
                best_killstreak = CASE 
                    WHEN current_killstreak + 1 > best_killstreak 
                    THEN current_killstreak + 1 
                    ELSE best_killstreak 
                END,
                updated_at = ?
            WHERE player_id = ?
        """
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, LocalDateTime.now())
                    ps.setString(2, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to add kill for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Adds a death and resets killstreak.
     */
    fun addDeath(playerId: String): Boolean {
        val sql = """
            UPDATE lp_stats 
            SET total_deaths = total_deaths + 1,
                current_killstreak = 0,
                updated_at = ?
            WHERE player_id = ?
        """
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, LocalDateTime.now())
                    ps.setString(2, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to add death for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Adds an assist.
     */
    fun addAssist(playerId: String): Boolean {
        return incrementStat(playerId, "total_assists")
    }

    /**
     * Adds damage dealt.
     */
    fun addDamageDealt(playerId: String, amount: Double): Boolean {
        return addDoubleStat(playerId, "damage_dealt", amount)
    }

    /**
     * Adds damage taken.
     */
    fun addDamageTaken(playerId: String, amount: Double): Boolean {
        return addDoubleStat(playerId, "damage_taken", amount)
    }

    /**
     * Adds fall damage taken.
     */
    fun addFallDamage(playerId: String, amount: Double): Boolean {
        return addDoubleStat(playerId, "fall_damage_taken", amount)
    }

    // =================== GAME RESULT STATS ===================

    /**
     * Adds a win and updates win streak.
     */
    fun addWin(playerId: String): Boolean {
        val sql = """
            UPDATE lp_stats 
            SET current_win_streak = current_win_streak + 1,
                best_win_streak = CASE 
                    WHEN current_win_streak + 1 > best_win_streak 
                    THEN current_win_streak + 1 
                    ELSE best_win_streak 
                END,
                current_loss_streak = 0,
                updated_at = ?
            WHERE player_id = ?
        """
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, LocalDateTime.now())
                    ps.setString(2, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to add win for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Adds a loss and updates loss streak.
     */
    fun addLoss(playerId: String): Boolean {
        val sql = """
            UPDATE lp_stats 
            SET current_loss_streak = current_loss_streak + 1,
                worst_loss_streak = CASE 
                    WHEN current_loss_streak + 1 > worst_loss_streak 
                    THEN current_loss_streak + 1 
                    ELSE worst_loss_streak 
                END,
                current_win_streak = 0,
                updated_at = ?
            WHERE player_id = ?
        """
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, LocalDateTime.now())
                    ps.setString(2, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to add loss for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Updates fastest win time (only if faster than current).
     */
    fun updateFastestWin(playerId: String, timeInSeconds: Long): Boolean {
        val sql = """
            UPDATE lp_stats 
            SET fastest_win_time = CASE 
                WHEN fastest_win_time IS NULL OR ? < fastest_win_time 
                THEN ? 
                ELSE fastest_win_time 
            END,
            updated_at = ?
            WHERE player_id = ?
        """
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setLong(1, timeInSeconds)
                    ps.setLong(2, timeInSeconds)
                    ps.setObject(3, LocalDateTime.now())
                    ps.setString(4, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update fastest win for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Updates longest game time (only if longer than current).
     */
    fun updateLongestGame(playerId: String, timeInSeconds: Long): Boolean {
        val sql = """
            UPDATE lp_stats 
            SET longest_game_time = CASE 
                WHEN ? > longest_game_time 
                THEN ? 
                ELSE longest_game_time 
            END,
            updated_at = ?
            WHERE player_id = ?
        """
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setLong(1, timeInSeconds)
                    ps.setLong(2, timeInSeconds)
                    ps.setObject(3, LocalDateTime.now())
                    ps.setString(4, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update longest game for player $playerId: ${e.message}", e)
        }
    }

    // =================== MULTI-KILL STATS ===================

    /**
     * Adds a double kill.
     */
    fun addDoubleKill(playerId: String): Boolean {
        return incrementStat(playerId, "double_kills")
    }

    /**
     * Adds a triple kill.
     */
    fun addTripleKill(playerId: String): Boolean {
        return incrementStat(playerId, "triple_kills")
    }

    /**
     * Adds a quad kill.
     */
    fun addQuadKill(playerId: String): Boolean {
        return incrementStat(playerId, "quad_kills")
    }

    // =================== SPECIAL GAME STATS ===================

    /**
     * Adds a perfect game.
     */
    fun addPerfectGame(playerId: String): Boolean {
        return incrementStat(playerId, "perfect_games")
    }

    /**
     * Adds a comeback win.
     */
    fun addComebackWin(playerId: String): Boolean {
        return incrementStat(playerId, "comeback_wins")
    }

    // =================== BUILDING STATS ===================

    /**
     * Adds pillars destroyed.
     */
    fun addPillarsDestroyed(playerId: String, amount: Int = 1): Boolean {
        return addIntStat(playerId, "pillars_destroyed", amount)
    }

    /**
     * Adds blocks broken.
     */
    fun addBlocksBroken(playerId: String, amount: Int = 1): Boolean {
        return addIntStat(playerId, "blocks_broken", amount)
    }

    /**
     * Adds blocks placed.
     */
    fun addBlocksPlaced(playerId: String, amount: Int = 1): Boolean {
        return addIntStat(playerId, "blocks_placed", amount)
    }

    // =================== PROGRESSION STATS ===================

    /**
     * Adds experience to a player.
     */
    fun addExperience(playerId: String, amount: Int): Boolean {
        return addIntStat(playerId, "experience", amount)
    }

    /**
     * Updates player level.
     */
    fun updateLevel(playerId: String, level: Int): Boolean {
        return updateIntStat(playerId, "level", level)
    }

    /**
     * Recalculates the expected level from the player's current XP and writes it
     * to the DB if it is higher than the stored level.  Returns the new level when
     * a level-up occurred, or null if the level was already up-to-date.
     *
     * Formula: level = floor(sqrt(experience / 100)) + 1
     *   0 XP → 1 | 100 XP → 2 | 400 XP → 3 | 900 XP → 4 | 1600 XP → 5 …
     */
    fun checkAndApplyLevelUp(playerId: String): Int? {
        return try {
            val stats = findById(playerId) ?: return null
            val expectedLevel = kotlin.math.sqrt(stats.experience.toDouble() / 100).toInt() + 1
            if (expectedLevel > stats.level) {
                updateLevel(playerId, expectedLevel)
                expectedLevel
            } else null
        } catch (_: Exception) { null }
    }

    /**
     * Adds coins to a player.
     */
    fun addCoins(playerId: String, amount: Int): Boolean {
        return addIntStat(playerId, "coins", amount)
    }

    /**
     * Removes coins from a player.
     */
    fun removeCoins(playerId: String, amount: Int): Boolean {
        return addIntStat(playerId, "coins", -amount)
    }

    // =================== TIME STATS ===================

    /**
     * Adds playtime in seconds.
     */
    fun addPlaytime(playerId: String, seconds: Long): Boolean {
        val sql = "UPDATE lp_stats SET total_playtime = total_playtime + ?, updated_at = ? WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setLong(1, seconds)
                    ps.setObject(2, LocalDateTime.now())
                    ps.setString(3, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to add playtime for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Updates average game time.
     */
    fun updateAverageGameTime(playerId: String): Boolean {
        val sql = """
            UPDATE lp_stats 
            SET average_game_time = CASE 
                WHEN games_played > 0 THEN total_playtime / games_played 
                ELSE 0 
            END,
            updated_at = ?
            WHERE player_id = ?
        """
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, LocalDateTime.now())
                    ps.setString(2, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update average game time for player $playerId: ${e.message}", e)
        }
    }

    // =================== MOVEMENT STATS ===================

    /**
     * Adds distance moved.
     */
    fun addDistanceMoved(playerId: String, distance: Double): Boolean {
        return addDoubleStat(playerId, "total_distance_moved", distance)
    }

    // =================== ITEM STATS ===================

    /**
     * Adds items collected.
     */
    fun addItemsCollected(playerId: String, amount: Int = 1): Boolean {
        return addIntStat(playerId, "items_collected", amount)
    }

    /**
     * Adds items crafted.
     */
    fun addItemsCrafted(playerId: String, amount: Int = 1): Boolean {
        return addIntStat(playerId, "items_crafted", amount)
    }

    // =================== TEAM STATS ===================

    /**
     * Adds a team win.
     */
    fun addTeamWin(playerId: String): Boolean {
        return incrementStat(playerId, "team_win")
    }

    /**
     * Adds a team loss.
     */
    fun addTeamLoss(playerId: String): Boolean {
        return incrementStat(playerId, "team_loss")
    }

    // =================== ARENA STATS ===================

    /**
     * Increments arenas played count.
     */
    fun incrementArenasPlayed(playerId: String): Boolean {
        return incrementStat(playerId, "arenas_played")
    }

    // =================== RANKING STATS ===================

    /**
     * Updates global rank.
     */
    fun updateGlobalRank(playerId: String, rank: Int): Boolean {
        val sql = """
            UPDATE lp_stats 
            SET global_rank = ?,
                rank_last_updated = ?,
                updated_at = ?
            WHERE player_id = ?
        """
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    val now = LocalDateTime.now()
                    ps.setInt(1, rank)
                    ps.setObject(2, now)
                    ps.setObject(3, now)
                    ps.setString(4, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update global rank for player $playerId: ${e.message}", e)
        }
    }

    // =================== HELPER METHODS ===================

    private fun rowToStats(rs: ResultSet): PlayerStats {
        return PlayerStats(
            playerId = rs.getString("player_id"),
            gamesPlayed = rs.getInt("games_played"),
            gamesWon = rs.getInt("games_won"),
            gamesLost = rs.getInt("games_lost"),
            gamesQuit = rs.getInt("games_quit"),
            totalKills = rs.getInt("total_kills"),
            totalDeaths = rs.getInt("total_deaths"),
            totalAssists = rs.getInt("total_assists"),
            bestKillstreak = rs.getInt("best_killstreak"),
            currentKillstreak = rs.getInt("current_killstreak"),
            pillarsDestroyed = rs.getInt("pillars_destroyed"),
            blocksBroken = rs.getInt("blocks_broken"),
            blocksPlaced = rs.getInt("blocks_placed"),
            totalDistanceMoved = rs.getDouble("total_distance_moved"),
            totalPlaytime = rs.getLong("total_playtime"),
            longestGameTime = rs.getInt("longest_game_time"),
            fastestWinTime = rs.getObject("fastest_win_time") as? Int,
            averageGameTime = rs.getInt("average_game_time"),
            currentWinStreak = rs.getInt("current_win_streak"),
            bestWinStreak = rs.getInt("best_win_streak"),
            currentLossStreak = rs.getInt("current_loss_streak"),
            worstLossStreak = rs.getInt("worst_loss_streak"),
            experience = rs.getInt("experience"),
            level = rs.getInt("level"),
            coins = rs.getInt("coins"),
            damageDealt = rs.getDouble("damage_dealt"),
            damageTaken = rs.getDouble("damage_taken"),
            fallDamageTaken = rs.getDouble("fall_damage_taken"),
            doubleKills = rs.getInt("double_kills"),
            tripleKills = rs.getInt("triple_kills"),
            quadKills = rs.getInt("quad_kills"),
            perfectGames = rs.getInt("perfect_games"),
            comebackWins = rs.getInt("comeback_wins"),
            itemsCollected = rs.getInt("items_collected"),
            itemsCrafted = rs.getInt("items_crafted"),
            teamWin = rs.getInt("team_win"),
            teamLoss = rs.getInt("team_loss"),
            arenasPlayed = rs.getInt("arenas_played"),
            globalRank = rs.getObject("global_rank") as? Int,
            rankLastUpdated = rs.getObject("rank_last_updated", LocalDateTime::class.java),
            createdAt = rs.getObject("created_at", LocalDateTime::class.java),
            updatedAt = rs.getObject("updated_at", LocalDateTime::class.java)
        )
    }
}