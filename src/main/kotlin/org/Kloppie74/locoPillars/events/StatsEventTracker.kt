package org.Kloppie74.locoPillars.events

import org.Kloppie74.locoPillars.database.DatabaseManager
import org.Kloppie74.locoPillars.database.dao.PlayerDAO
import org.Kloppie74.locoPillars.database.dao.StatsDAO
import org.Kloppie74.locoPillars.database.dao.VanishDAO
import org.Kloppie74.locoPillars.database.dao.ArenaStatsDAO
import org.Kloppie74.locoPillars.database.models.VanishState
import java.time.LocalDateTime
import java.util.UUID

/**
 * Central event-based stats tracker using DAO pattern.
 *
 * CHANGELOG (v2.0.1):
 * - Added all new PlayerDAO methods
 * - Added arena stats tracking methods
 * - Added bulk operation methods
 * - Improved method organization
 * - Added comprehensive documentation
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
class StatsEventTracker(private val db: DatabaseManager) {

    // =================== PLAYER MANAGEMENT ===================

    /**
     * Creates a new player record with stats.
     */
    fun createPlayer(playerName: String, displayName: String? = null, uuid: String): Boolean {
        return try {
            val player = PlayerDAO.create(playerName, displayName ?: playerName, uuid)
            if (player != null) {
                StatsDAO.create(player.playerId)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Gets player ID by player name.
     */
    fun getPlayerIdByName(playerName: String): String? =
        PlayerDAO.getPlayerId(playerName)

    /**
     * Gets player ID by UUID.
     */
    fun getPlayerIdByUUID(uuid: String): String? {
        return try {
            PlayerDAO.findByUUID(uuid)?.playerId
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Updates player's display name.
     */
    fun updateDisplayName(playerId: String, newDisplayName: String): Boolean =
        PlayerDAO.updateDisplayName(playerId, newDisplayName)

    /**
     * Gets player's display name.
     */
    fun getDisplayName(playerId: String): String? =
        PlayerDAO.getDisplayName(playerId)

    /**
     * Updates last seen timestamp.
     */
    fun updateLastSeen(playerId: String, time: LocalDateTime = LocalDateTime.now()): Boolean =
        PlayerDAO.updateLastSeen(playerId, time)

    /**
     * Gets last seen timestamp.
     */
    fun getLastSeen(playerId: String): LocalDateTime? =
        PlayerDAO.getLastSeen(playerId)

    /**
     * Increments total session count.
     */
    fun incrementSessionCount(playerId: String): Boolean =
        PlayerDAO.incrementSessionCount(playerId)

    /**
     * Gets total sessions.
     */
    fun getTotalSessions(playerId: String): Int =
        PlayerDAO.getTotalSessions(playerId)

    /**
     * Sets player active status.
     */
    fun setActiveStatus(playerId: String, active: Boolean): Boolean =
        PlayerDAO.setActiveStatus(playerId, active)

    /**
     * Checks if player is active.
     */
    fun isPlayerActive(playerId: String): Boolean =
        PlayerDAO.isActive(playerId)

    /**
     * Gets first played timestamp.
     */
    fun getFirstPlayed(playerId: String): LocalDateTime? =
        PlayerDAO.getFirstPlayed(playerId)

    /**
     * Updates first played timestamp.
     */
    fun updateFirstPlayed(playerId: String, firstPlayed: LocalDateTime): Boolean =
        PlayerDAO.updateFirstPlayed(playerId, firstPlayed)

    /**
     * Deletes a player record.
     */
    fun deletePlayer(playerId: String): Boolean =
        PlayerDAO.delete(playerId)

    // =================== PLAYER QUERIES ===================

    /**
     * Gets total number of players.
     */
    fun getTotalPlayersCount(): Int =
        PlayerDAO.getTotalPlayersCount()

    /**
     * Gets all active players.
     */
    fun getAllActivePlayers() =
        PlayerDAO.getAllActivePlayers()

    /**
     * Gets recently active players.
     */
    fun getRecentlyActivePlayers(limit: Int) =
        PlayerDAO.getRecentlyActive(limit)

    /**
     * Searches players by name.
     */
    fun searchPlayersByName(partialName: String) =
        PlayerDAO.searchByName(partialName)

    /**
     * Gets inactive players since a date.
     */
    fun getInactivePlayersSince(since: LocalDateTime) =
        PlayerDAO.getInactiveSince(since)

    // =================== STATS MANAGEMENT ===================

    /**
     * Creates player stats record.
     */
    fun createPlayerStats(playerId: String): Boolean =
        StatsDAO.create(playerId)

    /**
     * Increments games played.
     */
    fun incrementGamesPlayed(playerId: String): Boolean =
        StatsDAO.incrementStat(playerId, "games_played")

    /**
     * Increments games won.
     */
    fun incrementGamesWon(playerId: String): Boolean =
        StatsDAO.incrementStat(playerId, "games_won")

    /**
     * Increments games lost.
     */
    fun incrementGamesLost(playerId: String): Boolean =
        StatsDAO.incrementStat(playerId, "games_lost")

    /**
     * Increments games quit.
     */
    fun incrementGamesQuit(playerId: String): Boolean =
        StatsDAO.incrementStat(playerId, "games_quit")

    /**
     * Adds a kill (updates killstreak).
     */
    fun addKill(playerId: String): Boolean =
        StatsDAO.addKill(playerId)

    /**
     * Adds a death (resets killstreak).
     */
    fun addDeath(playerId: String): Boolean =
        StatsDAO.addDeath(playerId)

    /**
     * Adds an assist.
     */
    fun addAssist(playerId: String): Boolean =
        StatsDAO.addAssist(playerId)

    /**
     * Adds pillar destroyed count.
     */
    fun addPillarDestroyed(playerId: String): Boolean =
        StatsDAO.addPillarsDestroyed(playerId)

    /**
     * Adds block placed count.
     */
    fun addBlockPlaced(playerId: String): Boolean =
        StatsDAO.addBlocksPlaced(playerId)

    /**
     * Adds block broken count.
     */
    fun addBlockBroken(playerId: String): Boolean =
        StatsDAO.addBlocksBroken(playerId)

    /**
     * Adds distance moved.
     */
    fun addDistanceMoved(playerId: String, distance: Double): Boolean =
        StatsDAO.addDistanceMoved(playerId, distance)

    /**
     * Adds playtime in seconds.
     */
    fun addPlayTime(playerId: String, seconds: Long): Boolean =
        StatsDAO.addPlaytime(playerId, seconds)

    /**
     * Adds experience points.
     */
    fun addExperience(playerId: String, experience: Int): Boolean =
        StatsDAO.addExperience(playerId, experience)

    /**
     * Sets experience to exact value.
     */
    fun setExperience(playerId: String, experience: Int): Boolean =
        StatsDAO.updateIntStat(playerId, "experience", experience)

    /**
     * Adds coins.
     */
    fun addCoins(playerId: String, coins: Int): Boolean =
        StatsDAO.addCoins(playerId, coins)

    /**
     * Removes coins.
     */
    fun removeCoins(playerId: String, coins: Int): Boolean =
        StatsDAO.removeCoins(playerId, coins)

    /**
     * Sets coins to exact value.
     */
    fun setCoins(playerId: String, coins: Int): Boolean =
        StatsDAO.updateIntStat(playerId, "coins", coins)

    /**
     * Sets player level.
     */
    fun setLevel(playerId: String, level: Int): Boolean =
        StatsDAO.updateLevel(playerId, level)

    /**
     * Adds a win (updates win streak).
     */
    fun addWin(playerId: String): Boolean =
        StatsDAO.addWin(playerId)

    /**
     * Adds a loss (updates loss streak).
     */
    fun addLoss(playerId: String): Boolean =
        StatsDAO.addLoss(playerId)

    /**
     * Adds damage dealt.
     */
    fun addDamageDealt(playerId: String, damage: Double): Boolean =
        StatsDAO.addDamageDealt(playerId, damage)

    /**
     * Adds damage taken.
     */
    fun addDamageTaken(playerId: String, damage: Double): Boolean =
        StatsDAO.addDamageTaken(playerId, damage)

    /**
     * Adds fall damage taken.
     */
    fun addFallDamage(playerId: String, damage: Double): Boolean =
        StatsDAO.addFallDamage(playerId, damage)

    /**
     * Updates fastest win time.
     */
    fun updateFastestWin(playerId: String, timeInSeconds: Long): Boolean =
        StatsDAO.updateFastestWin(playerId, timeInSeconds)

    /**
     * Updates longest game time.
     */
    fun updateLongestGame(playerId: String, timeInSeconds: Long): Boolean =
        StatsDAO.updateLongestGame(playerId, timeInSeconds)

    /**
     * Adds multi-kill stats.
     */
    fun addDoubleKill(playerId: String): Boolean =
        StatsDAO.addDoubleKill(playerId)

    fun addTripleKill(playerId: String): Boolean =
        StatsDAO.addTripleKill(playerId)

    fun addQuadKill(playerId: String): Boolean =
        StatsDAO.addQuadKill(playerId)

    /**
     * Adds special game stats.
     */
    fun addPerfectGame(playerId: String): Boolean =
        StatsDAO.addPerfectGame(playerId)

    fun addComebackWin(playerId: String): Boolean =
        StatsDAO.addComebackWin(playerId)

    /**
     * Updates average game time.
     */
    fun updateAverageGameTime(playerId: String): Boolean =
        StatsDAO.updateAverageGameTime(playerId)

    // =================== ARENA STATS ===================

    /**
     * Creates or updates player arena stats.
     */
    fun createOrUpdatePlayerArenaStats(playerId: String, arenaName: String): Boolean =
        ArenaStatsDAO.createOrUpdatePlayerArenaStats(playerId, arenaName)

    /**
     * Creates or updates arena stats.
     */
    fun createOrUpdateArenaStats(arenaName: String, displayName: String): Boolean =
        ArenaStatsDAO.createOrUpdate(arenaName, displayName)

    /**
     * Increments arena games played.
     */
    fun incrementArenaGamesPlayed(arenaName: String): Boolean =
        ArenaStatsDAO.incrementGamesPlayed(arenaName)

    /**
     * Increments player arena games played.
     */
    fun incrementPlayerArenaGamesPlayed(playerId: String, arenaName: String): Boolean =
        ArenaStatsDAO.incrementPlayerGamesPlayed(playerId, arenaName)

    /**
     * Increments player arena wins.
     */
    fun incrementPlayerArenaWins(playerId: String, arenaName: String): Boolean =
        ArenaStatsDAO.incrementPlayerWins(playerId, arenaName)

    // =================== VANISH MANAGEMENT ===================

    /**
     * Saves vanish state.
     */
    fun saveVanishState(playerId: UUID, state: VanishState): Boolean =
        VanishDAO.save(playerId, state)

    /**
     * Loads vanish state.
     */
    fun loadVanishState(playerId: UUID): VanishState? =
        VanishDAO.load(playerId)

    /**
     * Sets vanished status.
     */
    fun setVanished(playerId: UUID, vanished: Boolean): Boolean =
        VanishDAO.setVanished(playerId, vanished)

    /**
     * Checks if player is vanished.
     */
    fun isVanished(playerId: UUID): Boolean =
        VanishDAO.isVanished(playerId)

    // =================== STATS GETTERS ===================

    /**
     * Gets integer stat.
     */
    fun getStatInt(playerId: String, column: String): Int =
        StatsDAO.getIntStat(playerId, column)

    /**
     * Gets double stat.
     */
    fun getStatDouble(playerId: String, column: String): Double =
        StatsDAO.getDoubleStat(playerId, column)

    /**
     * Gets long stat.
     */
    fun getStatLong(playerId: String, column: String): Long =
        StatsDAO.getLongStat(playerId, column)

    /**
     * Gets all stats for a player.
     */
    fun getAllStatsForPlayer(playerId: String): Map<String, Any?> =
        StatsDAO.getAllStatsForPlayer(playerId)

    // =================== BULK OPERATIONS ===================

    /**
     * Sets all players to inactive.
     */
    fun setAllPlayersInactive(): Int =
        PlayerDAO.setAllInactive()

    /**
     * Cleans up old inactive players.
     */
    fun cleanupOldPlayers(daysOld: Int): Int =
        PlayerDAO.cleanupOldPlayers(daysOld)

    /**
     * Gets player statistics summary.
     */
    fun getPlayerStatistics(): Map<String, Any> =
        PlayerDAO.getPlayerStatistics()
}