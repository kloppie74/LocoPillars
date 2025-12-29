package org.Kloppie74.locoPillars.database.dao

import org.Kloppie74.locoPillars.database.DatabaseManager
import org.Kloppie74.locoPillars.database.models.*
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDateTime

object ArenaStatsDAO {

    // =================== ARENA STATS CRUD ===================

    fun createOrUpdate(arenaName: String, displayName: String?): Boolean {
        val now = LocalDateTime.now()
        val sql = when (DatabaseManager.getDatabaseType().lowercase()) {
            "mysql", "mariadb" -> """
                INSERT INTO lp_arena_stats (arena_name, arena_display_name, created_at, updated_at)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    arena_display_name = VALUES(arena_display_name),
                    updated_at = VALUES(updated_at)
            """
            "h2" -> """
                MERGE INTO lp_arena_stats (arena_name, arena_display_name, created_at, updated_at)
                KEY(arena_name)
                VALUES (?, ?, ?, ?)
            """
            else -> throw IllegalArgumentException("Unsupported database type: ${DatabaseManager.getDatabaseType()}")
        }

        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, arenaName)
                    ps.setString(2, displayName)
                    ps.setObject(3, now)
                    ps.setObject(4, now)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to create/update arena stats for $arenaName: ${e.message}", e)
        }
    }

    fun findByName(arenaName: String): ArenaStats? {
        val sql = "SELECT * FROM lp_arena_stats WHERE arena_name = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, arenaName)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rowToArenaStats(rs) else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to find arena stats for $arenaName: ${e.message}", e)
        }
    }

    fun findAll(): List<ArenaStats> {
        val sql = "SELECT * FROM lp_arena_stats ORDER BY popularity_score DESC"
        return try {
            val list = mutableListOf<ArenaStats>()
            DatabaseManager.getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(sql).use { rs ->
                        while (rs.next()) {
                            list.add(rowToArenaStats(rs))
                        }
                    }
                }
            }
            list
        } catch (e: SQLException) {
            throw SQLException("Failed to find all arena stats: ${e.message}", e)
        }
    }

    fun updateGameStats(arenaName: String, update: ArenaGameUpdate): Boolean {
        val sql = """
            UPDATE lp_arena_stats SET
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
        """

        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    val now = LocalDateTime.now()
                    ps.setInt(1, if (update.completed) 1 else 0)
                    ps.setInt(2, if (update.cancelled) 1 else 0)
                    ps.setLong(3, update.duration)
                    ps.setInt(4, update.totalKills)
                    ps.setInt(5, update.totalDeaths)
                    ps.setInt(6, update.pillarsDestroyed)
                    ps.setLong(7, update.blocksBroken)
                    ps.setLong(8, update.blocksPlaced)
                    ps.setLong(9, update.duration)
                    ps.setLong(10, update.duration)
                    ps.setLong(11, update.duration)
                    ps.setLong(12, update.duration)
                    ps.setLong(13, update.duration)
                    ps.setInt(14, update.highestKills)
                    ps.setInt(15, update.highestKills)
                    ps.setObject(16, now)
                    ps.setObject(17, now)
                    ps.setString(18, arenaName)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update game stats for arena $arenaName: ${e.message}", e)
        }
    }

    fun updatePerformance(arenaName: String, tps: Double, memoryUsage: Long): Boolean {
        val sql = """
            UPDATE lp_arena_stats SET
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
        """

        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setDouble(1, tps)
                    ps.setDouble(2, tps)
                    ps.setDouble(3, tps)
                    ps.setLong(4, memoryUsage)
                    ps.setLong(5, memoryUsage)
                    ps.setObject(6, LocalDateTime.now())
                    ps.setString(7, arenaName)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update performance for arena $arenaName: ${e.message}", e)
        }
    }

    fun updatePopularityScores(): Boolean {
        val sql = when (DatabaseManager.getDatabaseType().lowercase()) {
            "mysql", "mariadb" -> """
                UPDATE lp_arena_stats SET
                    popularity_score = (
                        (total_games_played * 0.3) +
                        (total_unique_players * 0.2) +
                        (average_players_per_game * 10 * 0.2) +
                        (rating_average * 20 * 0.2) +
                        (CASE WHEN last_played > NOW() - INTERVAL 7 DAY THEN 100 ELSE 0 END * 0.1)
                    ),
                    updated_at = ?
            """
            "h2" -> """
                UPDATE lp_arena_stats SET
                    popularity_score = (
                        (total_games_played * 0.3) +
                        (total_unique_players * 0.2) +
                        (average_players_per_game * 10 * 0.2) +
                        (rating_average * 20 * 0.2) +
                        (CASE WHEN last_played > DATEADD('DAY', -7, CURRENT_TIMESTAMP) THEN 100 ELSE 0 END * 0.1)
                    ),
                    updated_at = ?
            """
            else -> throw IllegalArgumentException("Unsupported database type: ${DatabaseManager.getDatabaseType()}")
        }

        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, LocalDateTime.now())
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated >= 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update popularity scores: ${e.message}", e)
        }
    }

    // =================== PLAYER ARENA STATS CRUD ===================

    fun createOrUpdatePlayerArenaStats(playerId: String, arenaName: String): Boolean {
        val sql = when (DatabaseManager.getDatabaseType().lowercase()) {
            "mysql", "mariadb" -> """
                INSERT IGNORE INTO lp_player_arena_stats (player_id, arena_name, first_played, last_played)
                VALUES (?, ?, ?, ?)
            """
            "h2" -> """
                MERGE INTO lp_player_arena_stats (player_id, arena_name, first_played, last_played)
                KEY(player_id, arena_name)
                VALUES (?, ?, ?, ?)
            """
            else -> throw IllegalArgumentException("Unsupported database type: ${DatabaseManager.getDatabaseType()}")
        }

        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    val now = LocalDateTime.now()
                    ps.setString(1, playerId)
                    ps.setString(2, arenaName)
                    ps.setObject(3, now)
                    ps.setObject(4, now)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated >= 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to create/update player arena stats for $playerId on $arenaName: ${e.message}", e)
        }
    }

    fun findPlayerArenaStats(playerId: String, arenaName: String): PlayerArenaStats? {
        val sql = "SELECT * FROM lp_player_arena_stats WHERE player_id = ? AND arena_name = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.setString(2, arenaName)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rowToPlayerArenaStats(rs) else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to find player arena stats for $playerId on $arenaName: ${e.message}", e)
        }
    }

    fun findAllPlayerArenaStats(playerId: String): List<PlayerArenaStats> {
        val sql = "SELECT * FROM lp_player_arena_stats WHERE player_id = ? ORDER BY games_played DESC"
        return try {
            val list = mutableListOf<PlayerArenaStats>()
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            list.add(rowToPlayerArenaStats(rs))
                        }
                    }
                }
            }
            list
        } catch (e: SQLException) {
            throw SQLException("Failed to find all player arena stats for $playerId: ${e.message}", e)
        }
    }

    fun updatePlayerArenaGameResult(playerId: String, arenaName: String, result: PlayerArenaGameResult): Boolean {
        val sql = """
            UPDATE lp_player_arena_stats SET
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
        """

        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    val now = LocalDateTime.now()
                    ps.setInt(1, if (result.won) 1 else 0)
                    ps.setInt(2, if (result.lost) 1 else 0)
                    ps.setInt(3, if (result.quit) 1 else 0)
                    ps.setLong(4, result.playtime)
                    ps.setInt(5, result.kills)
                    ps.setInt(6, result.deaths)
                    ps.setInt(7, result.assists)
                    ps.setInt(8, result.pillarsDestroyed)
                    ps.setLong(9, result.blocksBroken)
                    ps.setLong(10, result.blocksPlaced)
                    ps.setInt(11, result.placement)
                    ps.setInt(12, result.placement)
                    ps.setInt(13, result.placement)
                    ps.setInt(14, result.placement)
                    ps.setInt(15, result.killstreak)
                    ps.setInt(16, result.killstreak)
                    ps.setInt(17, if (result.won) 1 else 0)
                    ps.setLong(18, result.gameTime)
                    ps.setLong(19, result.gameTime)
                    ps.setLong(20, result.gameTime)
                    ps.setLong(21, result.gameTime)
                    ps.setInt(22, if (result.won) 1 else 0)
                    ps.setInt(23, if (result.won) 1 else 0)
                    ps.setInt(24, if (result.lost) 1 else 0)
                    ps.setInt(25, if (result.perfectGame) 1 else 0)
                    ps.setInt(26, if (result.comebackWin) 1 else 0)
                    ps.setInt(27, if (result.dominantWin) 1 else 0)
                    ps.setObject(28, now)
                    ps.setObject(29, now)
                    ps.setString(30, playerId)
                    ps.setString(31, arenaName)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update player arena game result for $playerId on $arenaName: ${e.message}", e)
        }
    }

    // =================== ARENA PERFORMANCE SAMPLES ===================

    fun recordPerformanceSample(sample: ArenaPerformanceSample): Boolean {
        val sql = """
            INSERT INTO lp_arena_performance (
                arena_name, sample_time, tps, memory_usage, active_players,
                game_state, game_duration, cpu_usage, chunk_count, entity_count,
                player_ping_average, packet_loss_rate, server_version
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, sample.arenaName)
                    ps.setObject(2, sample.sampleTime)
                    ps.setDouble(3, sample.tps)
                    ps.setLong(4, sample.memoryUsage)
                    ps.setInt(5, sample.activePlayers)
                    ps.setString(6, sample.gameState)
                    ps.setObject(7, sample.gameDuration)
                    ps.setObject(8, sample.cpuUsage)
                    ps.setObject(9, sample.chunkCount)
                    ps.setObject(10, sample.entityCount)
                    ps.setObject(11, sample.playerPingAverage)
                    ps.setObject(12, sample.packetLossRate)
                    ps.setString(13, sample.serverVersion)
                    val inserted = ps.executeUpdate()
                    conn.commit()
                    inserted > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to record performance sample for ${sample.arenaName}: ${e.message}", e)
        }
    }

    fun getPerformanceSamples(arenaName: String, limit: Int = 100): List<ArenaPerformanceSample> {
        val sql = """
            SELECT * FROM lp_arena_performance 
            WHERE arena_name = ? 
            ORDER BY sample_time DESC 
            LIMIT ?
        """
        return try {
            val list = mutableListOf<ArenaPerformanceSample>()
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, arenaName)
                    ps.setInt(2, limit)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            list.add(rowToPerformanceSample(rs))
                        }
                    }
                }
            }
            list
        } catch (e: SQLException) {
            throw SQLException("Failed to get performance samples for $arenaName: ${e.message}", e)
        }
    }

    fun incrementGamesPlayed(arenaName: String): Boolean {
        val sql = """
            UPDATE lp_arena_stats 
            SET total_games_played = total_games_played + 1,
                updated_at = ?
            WHERE arena_name = ?
        """
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, LocalDateTime.now())
                    ps.setString(2, arenaName)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to increment games played for arena $arenaName: ${e.message}", e)
        }
    }

    fun incrementPlayerGamesPlayed(playerId: String, arenaName: String): Boolean {
        val sql = """
            UPDATE lp_player_arena_stats 
            SET games_played = games_played + 1,
                updated_at = ?
            WHERE player_id = ? AND arena_name = ?
        """
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, LocalDateTime.now())
                    ps.setString(2, playerId)
                    ps.setString(3, arenaName)
                    val updated = ps.executeUpdate()
                    conn.commit()

                    if (updated == 0) {
                        createOrUpdatePlayerArenaStats(playerId, arenaName)
                        return incrementPlayerGamesPlayed(playerId, arenaName)
                    }

                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to increment player games played for $playerId on arena $arenaName: ${e.message}", e)
        }
    }

    fun incrementPlayerWins(playerId: String, arenaName: String): Boolean {
        val sql = """
            UPDATE lp_player_arena_stats 
            SET games_won = games_won + 1,
                updated_at = ?
            WHERE player_id = ? AND arena_name = ?
        """
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, LocalDateTime.now())
                    ps.setString(2, playerId)
                    ps.setString(3, arenaName)
                    val updated = ps.executeUpdate()
                    conn.commit()

                    if (updated == 0) {
                        createOrUpdatePlayerArenaStats(playerId, arenaName)
                        return incrementPlayerWins(playerId, arenaName)
                    }

                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to increment player wins for $playerId on arena $arenaName: ${e.message}", e)
        }
    }

    // =================== HELPER METHODS ===================

    private fun rowToArenaStats(rs: ResultSet): ArenaStats {
        return ArenaStats(
            arenaName = rs.getString("arena_name"),
            arenaDisplayName = rs.getString("arena_display_name"),
            totalGamesPlayed = rs.getInt("total_games_played"),
            totalGamesCompleted = rs.getInt("total_games_completed"),
            totalGamesCancelled = rs.getInt("total_games_cancelled"),
            totalUniquePlayers = rs.getInt("total_unique_players"),
            averagePlayersPerGame = rs.getDouble("average_players_per_game"),
            peakConcurrentPlayers = rs.getInt("peak_concurrent_players"),
            totalPlaytimeSeconds = rs.getLong("total_playtime_seconds"),
            averageGameDuration = rs.getDouble("average_game_duration"),
            shortestGameDuration = rs.getInt("shortest_game_duration"),
            longestGameDuration = rs.getInt("longest_game_duration"),
            totalKills = rs.getInt("total_kills"),
            totalDeaths = rs.getInt("total_deaths"),
            averageKillsPerGame = rs.getDouble("average_kills_per_game"),
            highestKillsInGame = rs.getInt("highest_kills_in_game"),
            totalPillarsDestroyed = rs.getInt("total_pillars_destroyed"),
            totalBlocksBroken = rs.getLong("total_blocks_broken"),
            totalBlocksPlaced = rs.getLong("total_blocks_placed"),
            averagePillarsPerGame = rs.getDouble("average_pillars_per_game"),
            averageTps = rs.getDouble("average_tps"),
            lowestTps = rs.getDouble("lowest_tps"),
            highestMemoryUsage = rs.getLong("highest_memory_usage"),
            crashCount = rs.getInt("crash_count"),
            popularityScore = rs.getDouble("popularity_score"),
            ratingAverage = rs.getDouble("rating_average"),
            ratingCount = rs.getInt("rating_count"),
            lastPlayed = rs.getObject("last_played", LocalDateTime::class.java),
            gamesToday = rs.getInt("games_today"),
            gamesThisWeek = rs.getInt("games_this_week"),
            gamesThisMonth = rs.getInt("games_this_month"),
            winDistributionVariance = rs.getDouble("win_distribution_variance"),
            averageGameBalance = rs.getDouble("average_game_balance"),
            firstPlayed = rs.getObject("first_played", LocalDateTime::class.java),
            createdAt = rs.getObject("created_at", LocalDateTime::class.java),
            updatedAt = rs.getObject("updated_at", LocalDateTime::class.java)
        )
    }

    private fun rowToPlayerArenaStats(rs: ResultSet): PlayerArenaStats {
        return PlayerArenaStats(
            playerId = rs.getString("player_id"),
            arenaName = rs.getString("arena_name"),
            gamesPlayed = rs.getInt("games_played"),
            gamesWon = rs.getInt("games_won"),
            gamesLost = rs.getInt("games_lost"),
            gamesQuit = rs.getInt("games_quit"),
            totalPlaytimeSeconds = rs.getLong("total_playtime_seconds"),
            bestPlacement = rs.getObject("best_placement") as? Int,
            worstPlacement = rs.getObject("worst_placement") as? Int,
            averagePlacement = rs.getDouble("average_placement"),
            totalKills = rs.getInt("total_kills"),
            totalDeaths = rs.getInt("total_deaths"),
            totalAssists = rs.getInt("total_assists"),
            bestKillstreak = rs.getInt("best_killstreak"),
            pillarsDestroyed = rs.getInt("pillars_destroyed"),
            blocksBroken = rs.getLong("blocks_broken"),
            blocksPlaced = rs.getLong("blocks_placed"),
            fastestWinTime = rs.getObject("fastest_win_time") as? Int,
            longestGameTime = rs.getInt("longest_game_time"),
            currentWinStreak = rs.getInt("current_win_streak"),
            bestWinStreak = rs.getInt("best_win_streak"),
            currentLossStreak = rs.getInt("current_loss_streak"),
            arenaRating = rs.getDouble("arena_rating"),
            ratingPeak = rs.getDouble("rating_peak"),
            ratingChangeTotal = rs.getInt("rating_change_total"),
            perfectGames = rs.getInt("perfect_games"),
            comebackWins = rs.getInt("comeback_wins"),
            dominantWins = rs.getInt("dominant_wins"),
            firstPlayed = rs.getObject("first_played", LocalDateTime::class.java),
            lastPlayed = rs.getObject("last_played", LocalDateTime::class.java),
            favoriteSpawnPosition = rs.getObject("favorite_spawn_position") as? Int,
            createdAt = rs.getObject("created_at", LocalDateTime::class.java),
            updatedAt = rs.getObject("updated_at", LocalDateTime::class.java)
        )
    }

    private fun rowToPerformanceSample(rs: ResultSet): ArenaPerformanceSample {
        return ArenaPerformanceSample(
            id = rs.getLong("id"),
            arenaName = rs.getString("arena_name"),
            sampleTime = rs.getObject("sample_time", LocalDateTime::class.java),
            tps = rs.getDouble("tps"),
            memoryUsage = rs.getLong("memory_usage"),
            activePlayers = rs.getInt("active_players"),
            gameState = rs.getString("game_state"),
            gameDuration = rs.getObject("game_duration") as? Int,
            cpuUsage = rs.getObject("cpu_usage") as? Double,
            chunkCount = rs.getObject("chunk_count") as? Int,
            entityCount = rs.getObject("entity_count") as? Int,
            playerPingAverage = rs.getObject("player_ping_average") as? Double,
            packetLossRate = rs.getObject("packet_loss_rate") as? Double,
            serverVersion = rs.getString("server_version"),
            createdAt = rs.getObject("created_at", LocalDateTime::class.java)
        )
    }
}

// =================== DATA CLASSES (voor updates) ===================

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