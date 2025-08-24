package org.Kloppie74.locoPillars.database.tables

import org.Kloppie74.locoPillars.database.DatabaseManager
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.*

/**
 * Manages the players table in the database.
 * Stores basic player information and registration data.
 */
class PlayersTable(private val databaseManager: DatabaseManager) {

    companion object {
        const val TABLE_NAME = "lp_players"
        private const val MAX_DB_WRITE_ATTEMPTS = 5
        private const val DB_WRITE_RETRY_BASE_MS = 50L
    }

    /**
     * Creates the players table if it doesn't exist.
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
            throw SQLException("Failed to create players table: ${e.message}", e)
        }
    }

    private fun createTableSQLite(): String = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_id TEXT PRIMARY KEY,
            player_name TEXT NOT NULL UNIQUE,
            display_name TEXT,
            first_played DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_seen DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            total_sessions INTEGER NOT NULL DEFAULT 0,
            is_active BOOLEAN NOT NULL DEFAULT 1,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
    """.trimIndent()

    private fun createTableMySQL(): String = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_id VARCHAR(36) PRIMARY KEY,
            player_name VARCHAR(16) NOT NULL UNIQUE,
            display_name VARCHAR(32),
            first_played TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            total_sessions INT NOT NULL DEFAULT 0,
            is_active BOOLEAN NOT NULL DEFAULT TRUE,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            INDEX idx_player_name (player_name),
            INDEX idx_last_seen (last_seen),
            INDEX idx_is_active (is_active)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()

    private fun createTablePostgreSQL(): String = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_id UUID PRIMARY KEY,
            player_name VARCHAR(16) NOT NULL UNIQUE,
            display_name VARCHAR(32),
            first_played TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            total_sessions INTEGER NOT NULL DEFAULT 0,
            is_active BOOLEAN NOT NULL DEFAULT TRUE,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        );
        CREATE INDEX IF NOT EXISTS idx_players_name ON $TABLE_NAME (player_name);
        CREATE INDEX IF NOT EXISTS idx_players_last_seen ON $TABLE_NAME (last_seen);
        CREATE INDEX IF NOT EXISTS idx_players_active ON $TABLE_NAME (is_active);
    """.trimIndent()

    /**
     * Creates a new player record in the database, with retry on SQLITE_BUSY/locked.
     * Handles cross-db unique violation detection.
     */
    fun createPlayer(playerName: String, displayName: String? = null, uuid: String): String? {
        val playerId = uuid
        val sql = when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" -> """
                INSERT INTO $TABLE_NAME (player_id, player_name, display_name, first_played, last_seen, total_sessions)
                VALUES (?, ?, ?, ?, ?, 1)
            """.trimIndent()
            "sqlite", "h2", "postgresql" -> """
                INSERT INTO $TABLE_NAME (player_id, player_name, display_name, first_played, last_seen, total_sessions)
                VALUES (?, ?, ?, ?, ?, 1)
            """.trimIndent()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        var attempts = 0
        while (attempts < MAX_DB_WRITE_ATTEMPTS) {
            try {
                return databaseManager.executeSync { connection ->
                    val now = LocalDateTime.now()
                    connection.prepareStatement(sql).use { statement ->
                        statement.setString(1, playerId)
                        statement.setString(2, playerName)
                        statement.setString(3, displayName ?: playerName)
                        statement.setObject(4, now)
                        statement.setObject(5, now)
                        val rowsAffected = statement.executeUpdate()
                        if (rowsAffected > 0) playerId else null
                    }
                }
            } catch (e: SQLException) {
                if (isLockedError(e)) {
                    attempts++
                    Thread.sleep(DB_WRITE_RETRY_BASE_MS * attempts)
                    continue
                }
                if (isUniqueConstraintViolation(e)) {
                    return getPlayerId(playerName)
                }
                throw SQLException("Failed to create player $playerName: ${e.message}", e)
            }
        }
        throw SQLException("Failed to create player $playerName: database is locked after $MAX_DB_WRITE_ATTEMPTS attempts")
    }

    /**
     * Gets a player's ID by their username.
     */
    fun getPlayerId(playerName: String): String? {
        val sql = "SELECT player_id FROM $TABLE_NAME WHERE player_name = ?"
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerName)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getString("player_id")
                        } else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get player ID for $playerName: ${e.message}", e)
        }
    }

    /**
     * Gets a player's username by their ID.
     */
    fun getPlayerName(playerId: String): String? {
        val sql = "SELECT player_name FROM $TABLE_NAME WHERE player_id = ?"
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getString("player_name")
                        } else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get player name for ID $playerId: ${e.message}", e)
        }
    }

    /**
     * Gets a player's display name.
     */
    fun getDisplayName(playerId: String): String? {
        val sql = "SELECT display_name FROM $TABLE_NAME WHERE player_id = ?"
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getString("display_name")
                        } else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get display name for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Updates a player's display name, with retry on locked.
     */
    fun updateDisplayName(playerId: String, displayName: String): Boolean {
        val sql = "UPDATE $TABLE_NAME SET display_name = ?, updated_at = ? WHERE player_id = ?"
        var attempts = 0
        while (attempts < MAX_DB_WRITE_ATTEMPTS) {
            try {
                return databaseManager.executeSync { connection ->
                    connection.prepareStatement(sql).use { statement ->
                        statement.setString(1, displayName)
                        statement.setObject(2, LocalDateTime.now())
                        statement.setString(3, playerId)
                        statement.executeUpdate() > 0
                    }
                }
            } catch (e: SQLException) {
                if (isLockedError(e)) {
                    attempts++
                    Thread.sleep(DB_WRITE_RETRY_BASE_MS * attempts)
                    continue
                }
                throw SQLException("Failed to update display name for player $playerId: ${e.message}", e)
            }
        }
        throw SQLException("Failed to update display name for player $playerId: database is locked after $MAX_DB_WRITE_ATTEMPTS attempts")
    }

    /**
     * Updates a player's last seen timestamp, with retry on locked.
     */
    fun updateLastSeen(playerId: String, lastSeen: LocalDateTime): Boolean {
        val sql = "UPDATE $TABLE_NAME SET last_seen = ?, updated_at = ? WHERE player_id = ?"
        var attempts = 0
        while (attempts < MAX_DB_WRITE_ATTEMPTS) {
            try {
                return databaseManager.executeSync { connection ->
                    connection.prepareStatement(sql).use { statement ->
                        statement.setObject(1, lastSeen)
                        statement.setObject(2, LocalDateTime.now())
                        statement.setString(3, playerId)
                        statement.executeUpdate() > 0
                    }
                }
            } catch (e: SQLException) {
                if (isLockedError(e)) {
                    attempts++
                    Thread.sleep(DB_WRITE_RETRY_BASE_MS * attempts)
                    continue
                }
                throw SQLException("Failed to update last seen for player $playerId: ${e.message}", e)
            }
        }
        throw SQLException("Failed to update last seen for player $playerId: database is locked after $MAX_DB_WRITE_ATTEMPTS attempts")
    }

    /**
     * Increments a player's session count, with retry on locked.
     */
    fun incrementSessionCount(playerId: String): Boolean {
        val sql = "UPDATE $TABLE_NAME SET total_sessions = total_sessions + 1, updated_at = ? WHERE player_id = ?"
        var attempts = 0
        while (attempts < MAX_DB_WRITE_ATTEMPTS) {
            try {
                return databaseManager.executeSync { connection ->
                    connection.prepareStatement(sql).use { statement ->
                        statement.setObject(1, LocalDateTime.now())
                        statement.setString(2, playerId)
                        statement.executeUpdate() > 0
                    }
                }
            } catch (e: SQLException) {
                if (isLockedError(e)) {
                    attempts++
                    Thread.sleep(DB_WRITE_RETRY_BASE_MS * attempts)
                    continue
                }
                throw SQLException("Failed to increment session count for player $playerId: ${e.message}", e)
            }
        }
        throw SQLException("Failed to increment session count for player $playerId: database is locked after $MAX_DB_WRITE_ATTEMPTS attempts")
    }

    /**
     * Gets a player's first played timestamp.
     */
    fun getFirstPlayed(playerId: String): LocalDateTime? {
        val sql = "SELECT first_played FROM $TABLE_NAME WHERE player_id = ?"
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getObject("first_played", LocalDateTime::class.java)
                        } else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get first played for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Gets a player's last seen timestamp.
     */
    fun getLastSeen(playerId: String): LocalDateTime? {
        val sql = "SELECT last_seen FROM $TABLE_NAME WHERE player_id = ?"
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getObject("last_seen", LocalDateTime::class.java)
                        } else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get last seen for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Gets a player's total session count.
     */
    fun getTotalSessions(playerId: String): Int {
        val sql = "SELECT total_sessions FROM $TABLE_NAME WHERE player_id = ?"
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getInt("total_sessions")
                        } else 0
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get total sessions for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Checks if a player exists in the database.
     */
    fun playerExists(playerName: String): Boolean {
        return getPlayerId(playerName) != null
    }

    /**
     * Sets a player's active status, with retry on locked.
     */
    fun setActiveStatus(playerId: String, isActive: Boolean): Boolean {
        val sql = "UPDATE $TABLE_NAME SET is_active = ?, updated_at = ? WHERE player_id = ?"
        var attempts = 0
        while (attempts < MAX_DB_WRITE_ATTEMPTS) {
            try {
                return databaseManager.executeSync { connection ->
                    connection.prepareStatement(sql).use { statement ->
                        statement.setBoolean(1, isActive)
                        statement.setObject(2, LocalDateTime.now())
                        statement.setString(3, playerId)
                        statement.executeUpdate() > 0
                    }
                }
            } catch (e: SQLException) {
                if (isLockedError(e)) {
                    attempts++
                    Thread.sleep(DB_WRITE_RETRY_BASE_MS * attempts)
                    continue
                }
                throw SQLException("Failed to set active status for player $playerId: ${e.message}", e)
            }
        }
        throw SQLException("Failed to set active status for player $playerId: database is locked after $MAX_DB_WRITE_ATTEMPTS attempts")
    }

    /**
     * Gets all active players (paginated).
     */
    fun getActivePlayers(limit: Int = 50, offset: Int = 0): List<PlayerRecord> {
        val sql = """
            SELECT player_id, player_name, display_name, first_played, last_seen, total_sessions, is_active
            FROM $TABLE_NAME 
            WHERE is_active = true 
            ORDER BY last_seen DESC 
            LIMIT ? OFFSET ?
        """.trimIndent()
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setInt(1, limit)
                    statement.setInt(2, offset)
                    statement.executeQuery().use { resultSet ->
                        buildList {
                            while (resultSet.next()) {
                                add(mapResultSetToPlayerRecord(resultSet))
                            }
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get active players: ${e.message}", e)
        }
    }

    /**
     * Gets the total number of players.
     */
    fun getTotalPlayersCount(): Int {
        val sql = "SELECT COUNT(*) as total FROM $TABLE_NAME"
        return try {
            databaseManager.executeSync { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(sql).use { resultSet ->
                        if (resultSet.next()) {
                            resultSet.getInt("total")
                        } else 0
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get total players count: ${e.message}", e)
        }
    }

    /**
     * Gets recently active players.
     */
    fun getRecentlyActivePlayers(hours: Int = 24, limit: Int = 20): List<PlayerRecord> {
        val sql = """
            SELECT player_id, player_name, display_name, first_played, last_seen, total_sessions, is_active
            FROM $TABLE_NAME 
            WHERE last_seen >= ? AND is_active = true
            ORDER BY last_seen DESC 
            LIMIT ?
        """.trimIndent()
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    val cutoffTime = LocalDateTime.now().minusHours(hours.toLong())
                    statement.setObject(1, cutoffTime)
                    statement.setInt(2, limit)
                    statement.executeQuery().use { resultSet ->
                        buildList {
                            while (resultSet.next()) {
                                add(mapResultSetToPlayerRecord(resultSet))
                            }
                        }
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get recently active players: ${e.message}", e)
        }
    }

    /**
     * Deletes a player from the database (GDPR compliance), with retry on locked.
     */
    fun deletePlayer(playerId: String): Boolean {
        val sql = "DELETE FROM $TABLE_NAME WHERE player_id = ?"
        var attempts = 0
        while (attempts < MAX_DB_WRITE_ATTEMPTS) {
            try {
                return databaseManager.executeSync { connection ->
                    connection.prepareStatement(sql).use { statement ->
                        statement.setString(1, playerId)
                        statement.executeUpdate() > 0
                    }
                }
            } catch (e: SQLException) {
                if (isLockedError(e)) {
                    attempts++
                    Thread.sleep(DB_WRITE_RETRY_BASE_MS * attempts)
                    continue
                }
                throw SQLException("Failed to delete player $playerId: ${e.message}", e)
            }
        }
        throw SQLException("Failed to delete player $playerId: database is locked after $MAX_DB_WRITE_ATTEMPTS attempts")
    }

    /**
     * Maps a ResultSet row to a PlayerRecord.
     */
    private fun mapResultSetToPlayerRecord(resultSet: ResultSet): PlayerRecord {
        return PlayerRecord(
            playerId = resultSet.getString("player_id"),
            playerName = resultSet.getString("player_name"),
            displayName = resultSet.getString("display_name"),
            firstPlayed = resultSet.getObject("first_played", LocalDateTime::class.java),
            lastSeen = resultSet.getObject("last_seen", LocalDateTime::class.java),
            totalSessions = resultSet.getInt("total_sessions"),
            isActive = resultSet.getBoolean("is_active")
        )
    }

    /**
     * Checks if an SQLException is a unique constraint violation.
     */
    private fun isUniqueConstraintViolation(e: SQLException): Boolean {
        return when (databaseManager.getDatabaseType()) {
            "sqlite" -> e.message?.contains("UNIQUE constraint failed") == true
            "mysql", "mariadb" -> e.errorCode == 1062
            "postgresql" -> e.sqlState == "23505"
            "h2" -> e.errorCode == 23505
            else -> false
        }
    }

    /**
     * Checks if an SQLException is a locked error (SQLITE_BUSY, database is locked).
     */
    private fun isLockedError(e: SQLException): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("database is locked") || msg.contains("sqlite_busy")
    }

    /**
     * Data class representing a player record.
     */
    data class PlayerRecord(
        val playerId: String,
        val playerName: String,
        val displayName: String?,
        val firstPlayed: LocalDateTime,
        val lastSeen: LocalDateTime,
        val totalSessions: Int,
        val isActive: Boolean
    ) {
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

        fun getDaysSinceFirstPlayed(): Long {
            return java.time.Duration.between(firstPlayed, LocalDateTime.now()).toDays()
        }

        fun isRecentlyActive(hours: Int = 24): Boolean {
            val cutoff = LocalDateTime.now().minusHours(hours.toLong())
            return lastSeen.isAfter(cutoff)
        }
    }
}