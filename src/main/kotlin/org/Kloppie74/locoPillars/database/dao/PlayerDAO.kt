package org.Kloppie74.locoPillars.database.dao

import org.Kloppie74.locoPillars.database.DatabaseManager
import org.Kloppie74.locoPillars.database.models.Player
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.time.LocalDateTime

/**
 * Data Access Object for player management.
 *
 * CHANGELOG (v2.1):
 * - Fixed H2 AUTO_INCREMENT issue with player_id
 * - Complete CRUD operations for player records
 * - Added all missing getter methods
 * - Added query methods
 * - Added update methods
 * - Added bulk operations
 * - Proper error handling with SQLException throws
 * - Cross-database compatible queries
 * - Comprehensive documentation
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
object PlayerDAO {

    // =================== BASIC CRUD OPERATIONS ===================

    /**
     * Creates a new player record.
     * Returns the created Player object with generated player_id.
     */
    fun create(playerName: String, displayName: String, uuid: String): Player? {
        val sql = """
        INSERT INTO lp_players (player_name, display_name, uuid, created_at, updated_at)
        VALUES (?, ?, ?, ?, ?)
    """
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS).use { ps ->
                    val now = LocalDateTime.now()
                    ps.setString(1, playerName)
                    ps.setString(2, displayName)
                    ps.setString(3, uuid)
                    ps.setObject(4, now)
                    ps.setObject(5, now)

                    val affected = ps.executeUpdate()
                    conn.commit()

                    if (affected > 0) {
                        ps.generatedKeys.use { rs ->
                            if (rs.next()) {
                                val generatedId = rs.getInt(1).toString() // ✅ getInt instead of getString
                                findById(generatedId)
                            } else {
                                findByUUID(uuid)
                            }
                        }
                    } else {
                        null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to create player $playerName: ${e.message}", e)
        }
    }

    /**
     * Finds a player by player_id.
     */
    fun findById(playerId: String): Player? {
        val sql = "SELECT * FROM lp_players WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rowToPlayer(rs) else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to find player by id $playerId: ${e.message}", e)
        }
    }

    /**
     * Finds a player by player_name.
     */
    fun findByName(playerName: String): Player? {
        val sql = "SELECT * FROM lp_players WHERE player_name = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerName)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rowToPlayer(rs) else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to find player by name $playerName: ${e.message}", e)
        }
    }

    /**
     * Finds a player by UUID.
     */
    fun findByUUID(uuid: String): Player? {
        val sql = "SELECT * FROM lp_players WHERE uuid = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, uuid)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rowToPlayer(rs) else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to find player by UUID $uuid: ${e.message}", e)
        }
    }

    /**
     * Deletes a player record by player_id.
     */
    fun delete(playerId: String): Boolean {
        val sql = "DELETE FROM lp_players WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    val affected = ps.executeUpdate()
                    conn.commit()
                    affected > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to delete player $playerId: ${e.message}", e)
        }
    }

    // =================== GETTER METHODS ===================

    /**
     * Gets player_id by player_name.
     */
    fun getPlayerId(playerName: String): String? {
        val sql = "SELECT player_id FROM lp_players WHERE player_name = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerName)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getString("player_id") else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get player ID for $playerName: ${e.message}", e)
        }
    }

    /**
     * Gets display_name by player_id.
     */
    fun getDisplayName(playerId: String): String? {
        val sql = "SELECT display_name FROM lp_players WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getString("display_name") else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get display name for $playerId: ${e.message}", e)
        }
    }

    /**
     * Gets total_sessions by player_id.
     */
    fun getTotalSessions(playerId: String): Int {
        val sql = "SELECT total_sessions FROM lp_players WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getInt("total_sessions") else 0
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get total sessions for $playerId: ${e.message}", e)
        }
    }

    /**
     * Gets first_played timestamp by player_id.
     */
    fun getFirstPlayed(playerId: String): LocalDateTime? {
        val sql = "SELECT first_played FROM lp_players WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getObject("first_played", LocalDateTime::class.java) else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get first played for $playerId: ${e.message}", e)
        }
    }

    /**
     * Gets last_seen timestamp by player_id.
     */
    fun getLastSeen(playerId: String): LocalDateTime? {
        val sql = "SELECT last_seen FROM lp_players WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getObject("last_seen", LocalDateTime::class.java) else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get last seen for $playerId: ${e.message}", e)
        }
    }

    /**
     * Gets is_active status by player_id.
     */
    fun isActive(playerId: String): Boolean {
        val sql = "SELECT is_active FROM lp_players WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getBoolean("is_active") else false
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get active status for $playerId: ${e.message}", e)
        }
    }

    // =================== UPDATE METHODS ===================

    /**
     * Updates display_name for a player.
     */
    fun updateDisplayName(playerId: String, displayName: String): Boolean {
        val sql = "UPDATE lp_players SET display_name = ?, updated_at = ? WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, displayName)
                    ps.setObject(2, LocalDateTime.now())
                    ps.setString(3, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update display name for $playerId: ${e.message}", e)
        }
    }

    /**
     * Updates last_seen timestamp for a player.
     */
    fun updateLastSeen(playerId: String, lastSeen: LocalDateTime): Boolean {
        val sql = "UPDATE lp_players SET last_seen = ?, updated_at = ? WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, lastSeen)
                    ps.setObject(2, LocalDateTime.now())
                    ps.setString(3, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update last seen for $playerId: ${e.message}", e)
        }
    }

    /**
     * Increments total_sessions for a player.
     */
    fun incrementSessionCount(playerId: String): Boolean {
        val sql = "UPDATE lp_players SET total_sessions = total_sessions + 1, updated_at = ? WHERE player_id = ?"
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
            throw SQLException("Failed to increment session count for $playerId: ${e.message}", e)
        }
    }

    /**
     * Sets is_active status for a player.
     */
    fun setActiveStatus(playerId: String, active: Boolean): Boolean {
        val sql = "UPDATE lp_players SET is_active = ?, updated_at = ? WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setBoolean(1, active)
                    ps.setObject(2, LocalDateTime.now())
                    ps.setString(3, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to set active status for $playerId: ${e.message}", e)
        }
    }

    /**
     * Updates first_played timestamp for a player.
     */
    fun updateFirstPlayed(playerId: String, firstPlayed: LocalDateTime): Boolean {
        val sql = "UPDATE lp_players SET first_played = ?, updated_at = ? WHERE player_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, firstPlayed)
                    ps.setObject(2, LocalDateTime.now())
                    ps.setString(3, playerId)
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update first played for $playerId: ${e.message}", e)
        }
    }

    // =================== QUERY METHODS ===================

    /**
     * Gets total number of players in database.
     */
    fun getTotalPlayersCount(): Int {
        val sql = "SELECT COUNT(*) as count FROM lp_players"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(sql).use { rs ->
                        if (rs.next()) rs.getInt("count") else 0
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get total players count: ${e.message}", e)
        }
    }

    /**
     * Gets all active players.
     */
    fun getAllActivePlayers(): List<Player> {
        val sql = "SELECT * FROM lp_players WHERE is_active = 1 ORDER BY last_seen DESC"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(sql).use { rs ->
                        val players = mutableListOf<Player>()
                        while (rs.next()) {
                            players.add(rowToPlayer(rs))
                        }
                        players
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get all active players: ${e.message}", e)
        }
    }

    /**
     * Gets all players.
     */
    fun getAllPlayers(): List<Player> {
        val sql = "SELECT * FROM lp_players ORDER BY created_at DESC"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(sql).use { rs ->
                        val players = mutableListOf<Player>()
                        while (rs.next()) {
                            players.add(rowToPlayer(rs))
                        }
                        players
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get all players: ${e.message}", e)
        }
    }

    /**
     * Gets players with pagination.
     */
    fun getPlayers(limit: Int, offset: Int): List<Player> {
        val sql = "SELECT * FROM lp_players ORDER BY created_at DESC LIMIT ? OFFSET ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, limit)
                    ps.setInt(2, offset)
                    ps.executeQuery().use { rs ->
                        val players = mutableListOf<Player>()
                        while (rs.next()) {
                            players.add(rowToPlayer(rs))
                        }
                        players
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get players with pagination: ${e.message}", e)
        }
    }

    /**
     * Searches players by partial name match.
     */
    fun searchByName(partialName: String): List<Player> {
        val sql = "SELECT * FROM lp_players WHERE player_name LIKE ? OR display_name LIKE ? ORDER BY player_name"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    val searchPattern = "%$partialName%"
                    ps.setString(1, searchPattern)
                    ps.setString(2, searchPattern)
                    ps.executeQuery().use { rs ->
                        val players = mutableListOf<Player>()
                        while (rs.next()) {
                            players.add(rowToPlayer(rs))
                        }
                        players
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to search players by name '$partialName': ${e.message}", e)
        }
    }

    /**
     * Gets recently active players.
     */
    fun getRecentlyActive(limit: Int): List<Player> {
        val sql = "SELECT * FROM lp_players WHERE last_seen IS NOT NULL ORDER BY last_seen DESC LIMIT ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, limit)
                    ps.executeQuery().use { rs ->
                        val players = mutableListOf<Player>()
                        while (rs.next()) {
                            players.add(rowToPlayer(rs))
                        }
                        players
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get recently active players: ${e.message}", e)
        }
    }

    /**
     * Gets players who haven't been seen since a specific date.
     */
    fun getInactiveSince(since: LocalDateTime): List<Player> {
        val sql = "SELECT * FROM lp_players WHERE last_seen < ? ORDER BY last_seen ASC"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, since)
                    ps.executeQuery().use { rs ->
                        val players = mutableListOf<Player>()
                        while (rs.next()) {
                            players.add(rowToPlayer(rs))
                        }
                        players
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get inactive players: ${e.message}", e)
        }
    }

    // =================== BULK OPERATIONS ===================

    /**
     * Sets all players to inactive (useful for server restart).
     */
    fun setAllInactive(): Int {
        val sql = "UPDATE lp_players SET is_active = 0, updated_at = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, LocalDateTime.now())
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to set all players inactive: ${e.message}", e)
        }
    }

    /**
     * Cleans up old player records (older than specified days and no activity).
     */
    fun cleanupOldPlayers(daysOld: Int): Int {
        val sql = when (DatabaseManager.getDatabaseType().lowercase()) {
            "mysql", "mariadb" -> """
                DELETE FROM lp_players 
                WHERE last_seen < DATE_SUB(NOW(), INTERVAL ? DAY)
                AND is_active = 0
            """
            "h2" -> """
                DELETE FROM lp_players 
                WHERE last_seen < DATEADD('DAY', -?, CURRENT_TIMESTAMP)
                AND is_active = 0
            """
            else -> throw IllegalArgumentException("Unsupported database type")
        }

        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setInt(1, daysOld)
                    val deleted = ps.executeUpdate()
                    conn.commit()
                    deleted
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to cleanup old players: ${e.message}", e)
        }
    }

    // =================== STATISTICS METHODS ===================

    /**
     * Gets player statistics summary.
     */
    fun getPlayerStatistics(): Map<String, Any> {
        return try {
            mapOf(
                "total_players" to getTotalPlayersCount(),
                "active_players" to getAllActivePlayers().size,
                "inactive_players" to (getTotalPlayersCount() - getAllActivePlayers().size)
            )
        } catch (e: SQLException) {
            throw SQLException("Failed to get player statistics: ${e.message}", e)
        }
    }

    // =================== HELPER METHODS ===================

    /**
     * Maps ResultSet row to Player object.
     */
    private fun rowToPlayer(rs: ResultSet): Player {
        return Player(
            playerId = rs.getString("player_id"),
            playerName = rs.getString("player_name"),
            displayName = rs.getString("display_name"),
            uuid = rs.getString("uuid"),
            totalSessions = rs.getInt("total_sessions"),
            firstPlayed = rs.getObject("first_played", LocalDateTime::class.java),
            lastSeen = rs.getObject("last_seen", LocalDateTime::class.java),
            isActive = rs.getBoolean("is_active"),
            createdAt = rs.getObject("created_at", LocalDateTime::class.java),
            updatedAt = rs.getObject("updated_at", LocalDateTime::class.java)
        )
    }
}