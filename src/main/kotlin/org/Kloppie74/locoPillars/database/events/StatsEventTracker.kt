package org.Kloppie74.locoPillars.database.events

import org.Kloppie74.locoPillars.database.DatabaseManager
import org.Kloppie74.locoPillars.database.tables.ArenaStatsTable
import org.Kloppie74.locoPillars.database.tables.AchievementsTable
import java.time.LocalDateTime
import java.util.UUID

/**
 * Central event-based stats tracker for LocoPillars.
 * Provides easy stat updating methods for ALL game/database events/settings/statistics.
 * This class acts as the single update/increment/set entry point for ALL database tables (except GamesTable).
 * Every stat and setting can be updated via this file - add a method here for every stat in any table you want to update!
 *
 * Usage:
 *   - Call StatsEventTracker.updateXxxStat(...), StatsEventTracker.setYyySetting(...), StatsEventTracker.incrementZzz(...)
 *   - All table managers are available via this tracker.
 *
 * @author Kloppie74
 * @since 1.0-SNAPSHOT
 */
class StatsEventTracker(val db: DatabaseManager) {

    // ====== PLAYERS TABLE ======
    /**
     * Creates a player record in the database using a specific UUID.
     * Use player.uniqueId.toString() from Bukkit for correct playerId!
     */
    fun createPlayer(playerName: String, displayName: String? = null, uuid: String): String? =
        db.playersTable.createPlayer(playerName, displayName, uuid)

    fun updateDisplayName(playerId: String, newDisplayName: String): Boolean =
        db.playersTable.updateDisplayName(playerId, newDisplayName)
    fun updateLastSeen(playerId: String, time: LocalDateTime = LocalDateTime.now()): Boolean =
        db.playersTable.updateLastSeen(playerId, time)
    fun incrementSessionCount(playerId: String): Boolean =
        db.playersTable.incrementSessionCount(playerId)
    fun setActiveStatus(playerId: String, active: Boolean): Boolean =
        db.playersTable.setActiveStatus(playerId, active)
    fun deletePlayer(playerId: String): Boolean =
        db.playersTable.deletePlayer(playerId)

    // ====== STATS TABLE ======
    fun createPlayerStats(playerId: String): Boolean =
        db.statsTable.createPlayerStats(playerId)
    fun incrementGamesPlayed(playerId: String): Boolean =
        db.statsTable.incrementGamesPlayed(playerId)
    fun incrementGamesWon(playerId: String): Boolean =
        db.statsTable.incrementGamesWon(playerId)
    fun incrementGamesLost(playerId: String): Boolean =
        db.statsTable.incrementGamesLost(playerId)
    fun incrementGamesQuit(playerId: String): Boolean =
        db.statsTable.incrementGamesQuit(playerId)

    fun addKill(playerId: String): Boolean =
        db.statsTable.addKill(playerId)
    fun addDeath(playerId: String): Boolean =
        db.statsTable.addDeath(playerId)
    fun addAssist(playerId: String): Boolean =
        db.statsTable.addAssist(playerId)

    fun addPillarDestroyed(playerId: String): Boolean =
        db.statsTable.addPillarDestroyed(playerId)
    fun addBlockPlaced(playerId: String): Boolean =
        db.statsTable.addBlockPlaced(playerId)
    fun addBlockBroken(playerId: String): Boolean =
        db.statsTable.addBlockBroken(playerId)
    fun addDistanceMoved(playerId: String, distance: Double): Boolean =
        db.statsTable.addDistanceMoved(playerId, distance)

    fun addPlayTime(playerId: String, seconds: Long): Boolean =
        db.statsTable.addPlayTime(playerId, seconds)
    fun updateLongestGame(playerId: String, gameTime: Long): Boolean =
        db.statsTable.updateLongestGame(playerId, gameTime)
    fun updateFastestWin(playerId: String, winTime: Long): Boolean =
        db.statsTable.updateFastestWin(playerId, winTime)
    fun updateAverageGameTime(playerId: String, avgTime: Long): Boolean =
        db.statsTable.updateIntStat(playerId, "average_game_time", avgTime.toInt())

    fun addWin(playerId: String): Boolean =
        db.statsTable.addWin(playerId)
    fun addLoss(playerId: String): Boolean =
        db.statsTable.addLoss(playerId)
    fun setCurrentWinStreak(playerId: String, streak: Int): Boolean =
        db.statsTable.updateIntStat(playerId, "current_win_streak", streak)
    fun setBestWinStreak(playerId: String, streak: Int): Boolean =
        db.statsTable.updateIntStat(playerId, "best_win_streak", streak)
    fun setCurrentLossStreak(playerId: String, streak: Int): Boolean =
        db.statsTable.updateIntStat(playerId, "current_loss_streak", streak)
    fun setWorstLossStreak(playerId: String, streak: Int): Boolean =
        db.statsTable.updateIntStat(playerId, "worst_loss_streak", streak)

    fun addExperience(playerId: String, experience: Int): Boolean =
        db.statsTable.addExperience(playerId, experience)
    fun setExperience(playerId: String, experience: Int): Boolean =
        db.statsTable.updateExperience(playerId, experience)
    fun addCoins(playerId: String, coins: Int): Boolean =
        db.statsTable.addCoins(playerId, coins)
    fun setCoins(playerId: String, coins: Int): Boolean =
        db.statsTable.updateCoins(playerId, coins)
    fun setLevel(playerId: String, level: Int): Boolean =
        db.statsTable.updateIntStat(playerId, "level", level)

    fun addDamageDealt(playerId: String, amount: Double): Boolean =
        db.statsTable.addDamageDealt(playerId, amount)
    fun addDamageTaken(playerId: String, amount: Double): Boolean =
        db.statsTable.addDamageTaken(playerId, amount)
    fun addFallDamage(playerId: String, amount: Double): Boolean =
        db.statsTable.addFallDamage(playerId, amount)

    fun addDoubleKill(playerId: String): Boolean =
        db.statsTable.addDoubleKill(playerId)
    fun addTripleKill(playerId: String): Boolean =
        db.statsTable.addTripleKill(playerId)
    fun addQuadKill(playerId: String): Boolean =
        db.statsTable.addQuadKill(playerId)
    fun addPerfectGame(playerId: String): Boolean =
        db.statsTable.addPerfectGame(playerId)
    fun addComebackWin(playerId: String): Boolean =
        db.statsTable.addComebackWin(playerId)

    // ====== ARENA STATS TABLE ======
    fun createOrUpdateArenaStats(arenaName: String, displayName: String?): Boolean =
        db.arenaStatsTable.createOrUpdateArenaStats(arenaName, displayName)
    fun updateArenaGameStats(arenaName: String, update: ArenaStatsTable.ArenaGameUpdate): Boolean =
        db.arenaStatsTable.updateArenaGameStats(arenaName, update)
    fun updateArenaPerformance(arenaName: String, tps: Double, memoryUsage: Long): Boolean =
        db.arenaStatsTable.updateArenaPerformance(arenaName, tps, memoryUsage)
    fun recordPerformanceSample(sample: ArenaStatsTable.ArenaPerformanceSample): Boolean =
        db.arenaStatsTable.recordPerformanceSample(sample)
    fun updatePlayerArenaGameResult(playerId: String, arenaName: String, result: ArenaStatsTable.PlayerArenaGameResult): Boolean =
        db.arenaStatsTable.updatePlayerArenaGameResult(playerId, arenaName, result)
    fun createOrUpdatePlayerArenaStats(playerId: String, arenaName: String): Boolean =
        db.arenaStatsTable.createOrUpdatePlayerArenaStats(playerId, arenaName)

    // ====== LEADERBOARD CACHE TABLE ======
    fun updateLeaderboards(): Boolean =
        db.leaderboardCache.updateCache()

    // ====== VANISH TABLE ======
    fun saveVanishState(playerId: java.util.UUID, state: org.Kloppie74.locoPillars.database.tables.VanishState): Boolean =
        db.vanishTable.saveState(playerId, state)
    fun loadVanishState(playerId: java.util.UUID): org.Kloppie74.locoPillars.database.tables.VanishState? =
        db.vanishTable.loadState(playerId)

    // ====== GENERAL DATABASE MAINTENANCE ======
    fun initializeAllTables(): Boolean {
        val success = db.playersTable.createTable() &&
                db.statsTable.createTable() &&
                db.achievementsTable.createTable() &&
                db.arenaStatsTable.createTable() &&
                db.leaderboardCache.createTable() &&
                db.vanishTable.createTable()
        return success
    }

    fun cleanupOldRecords() = db.cleanupOldRecords()
    fun validateDatabase() = db.migrations.validateDatabaseIntegrity()

    // ====== GENERIC COLUMN UPDATE: For any stat/setting, set by column name ======
    fun setStatInt(playerId: String, column: String, value: Int): Boolean =
        db.statsTable.updateIntStat(playerId, column, value)
    fun addStatDouble(playerId: String, column: String, value: Double): Boolean =
        db.statsTable.addDoubleStat(playerId, column, value)
    fun setStatLong(playerId: String, column: String, value: Long): Boolean =
        db.statsTable.updateIntStat(playerId, column, value.toInt())

    // ====== GETTERS (for placeholders, metrics, etc) ======
    fun getStatInt(playerId: String, column: String): Int =
        db.statsTable.getIntStat(playerId, column)
    fun getStatDouble(playerId: String, column: String): Double =
        db.statsTable.getDoubleStat(playerId, column)
    fun getStatLong(playerId: String, column: String): Long =
        db.statsTable.getLongStat(playerId, column)
    fun getStatNullableLong(playerId: String, column: String): Long? =
        db.statsTable.getNullableLongStat(playerId, column)

    // ====== Utility: Get playerId by name (for events using player name only) ======
    fun getPlayerIdByName(playerName: String): String? =
        db.playersTable.getPlayerId(playerName)
}