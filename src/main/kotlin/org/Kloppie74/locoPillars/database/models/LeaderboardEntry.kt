package org.Kloppie74.locoPillars.database.models

import java.time.LocalDateTime

data class LeaderboardEntry(
    val playerId: String,
    val playerName: String,
    val leaderboardType: String,
    val rankPosition: Int,
    val rankValue: Double,
    val rankDisplayValue: String,
    val secondaryValue: Double?,
    val secondaryDisplay: String?,
    val tertiaryValue: Double?,
    val tertiaryDisplay: String?,
    val percentile: Double,
    val scoreNormalized: Double,
    val previousRank: Int?,
    val rankChange: Int,
    val rankChangeDirection: String,
    val qualifyingGames: Int,
    val eligible: Boolean,
    val lastUpdated: LocalDateTime,
    val cacheVersion: Int
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
            rankChange > 0 && rankChangeDirection == "UP" -> "↑$rankChange"
            rankChange > 0 && rankChangeDirection == "DOWN" -> "↓$rankChange"
            rankChangeDirection == "NEW" -> "NEW"
            else -> "="
        }
    }

    fun getPercentileDisplay(): String {
        return "Top ${String.format("%.1f", 100 - percentile)}%"
    }
}

data class LeaderboardMetadata(
    val leaderboardType: String,
    val lastFullUpdate: LocalDateTime,
    val lastIncrementalUpdate: LocalDateTime?,
    val nextScheduledUpdate: LocalDateTime?,
    val totalEntries: Int,
    val eligibleEntries: Int,
    val qualifyingThreshold: Int,
    val updateDurationMs: Int,
    val cacheHitRate: Double,
    val queryCount24h: Int,
    val updateFrequencyMinutes: Int,
    val autoUpdateEnabled: Boolean,
    val cacheVersion: Int,
    val schemaVersion: Int,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)