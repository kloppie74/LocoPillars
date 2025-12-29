package org.Kloppie74.locoPillars.database.models

import java.time.LocalDateTime
import java.util.UUID

data class Player(
    val playerId: String,
    val playerName: String,
    val displayName: String?,
    val firstPlayed: LocalDateTime,
    val lastSeen: LocalDateTime,
    val totalSessions: Int,
    val isActive: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val uuid: String
) {
    fun getUUID(): UUID = UUID.fromString(playerId)

    fun getFormattedLastSeen(): String {
        val now = LocalDateTime.now()
        val duration = java.time.Duration.between(lastSeen, now)
        return when {
            duration.toDays() > 0 -> "${duration.toDays()} days ago"
            duration.toHours() > 0 -> "${duration.toHours()} hours ago"
            duration.toMinutes() > 0 -> "${duration.toMinutes()} minutes ago"
            else -> "Just now"
        }
    }
}