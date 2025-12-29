package org.Kloppie74.locoPillars.database.models

import java.time.LocalDateTime

data class PlayerStats(
    val playerId: String,
    val gamesPlayed: Int,
    val gamesWon: Int,
    val gamesLost: Int,
    val gamesQuit: Int,
    val totalKills: Int,
    val totalDeaths: Int,
    val totalAssists: Int,
    val bestKillstreak: Int,
    val currentKillstreak: Int,
    val pillarsDestroyed: Int,
    val blocksBroken: Int,
    val blocksPlaced: Int,
    val totalDistanceMoved: Double,
    val totalPlaytime: Long,
    val longestGameTime: Int,
    val fastestWinTime: Int?,
    val averageGameTime: Int,
    val currentWinStreak: Int,
    val bestWinStreak: Int,
    val currentLossStreak: Int,
    val worstLossStreak: Int,
    val experience: Int,
    val level: Int,
    val coins: Int,
    val damageDealt: Double,
    val damageTaken: Double,
    val fallDamageTaken: Double,
    val doubleKills: Int,
    val tripleKills: Int,
    val quadKills: Int,
    val perfectGames: Int,
    val comebackWins: Int,
    val itemsCollected: Int,
    val itemsCrafted: Int,
    val teamWin: Int,
    val teamLoss: Int,
    val arenasPlayed: Int,
    val globalRank: Int?,
    val rankLastUpdated: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    fun getWinRate(): Double = if (gamesPlayed > 0) (gamesWon * 100.0 / gamesPlayed) else 0.0
    fun getKDRatio(): Double = if (totalDeaths > 0) (totalKills.toDouble() / totalDeaths) else totalKills.toDouble()
}