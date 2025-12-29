package org.Kloppie74.locoPillars.database.dao

import org.Kloppie74.locoPillars.database.DatabaseManager
import org.Kloppie74.locoPillars.database.models.VanishState
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.UUID

object VanishDAO {

    // =================== BASIC CRUD OPERATIONS ===================

    fun create(playerUUID: UUID, state: VanishState): Boolean {
        val sql = """
            INSERT INTO lp_vanish_states (
                player_uuid, vanished, hide_me, hide_others, 
                show_others, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    val now = LocalDateTime.now()
                    ps.setString(1, playerUUID.toString())
                    ps.setBoolean(2, state.vanished)
                    ps.setBoolean(3, state.hideMe)
                    ps.setBoolean(4, state.hideOthers)
                    ps.setString(5, serializeShowOthers(state.showOthers))
                    ps.setObject(6, now)
                    ps.setObject(7, now)
                    val affected = ps.executeUpdate()
                    conn.commit()
                    affected > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to create vanish state for $playerUUID: ${e.message}", e)
        }
    }

    fun load(playerUUID: UUID): VanishState? {
        val sql = "SELECT * FROM lp_vanish_states WHERE player_uuid = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerUUID.toString())
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rowToVanishState(rs) else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to load vanish state for $playerUUID: ${e.message}", e)
        }
    }

    fun save(playerUUID: UUID, state: VanishState): Boolean {
        val sql = when (DatabaseManager.getDatabaseType().lowercase()) {
            "mysql", "mariadb" -> """
                INSERT INTO lp_vanish_states (
                    player_uuid, vanished, hide_me, hide_others, 
                    show_others, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    vanished = VALUES(vanished),
                    hide_me = VALUES(hide_me),
                    hide_others = VALUES(hide_others),
                    show_others = VALUES(show_others),
                    updated_at = VALUES(updated_at)
            """
            "h2" -> """
                MERGE INTO lp_vanish_states (
                    player_uuid, vanished, hide_me, hide_others, 
                    show_others, created_at, updated_at
                ) KEY(player_uuid)
                VALUES (?, ?, ?, ?, ?, ?, ?)
            """
            else -> throw IllegalArgumentException("Unsupported database type: ${DatabaseManager.getDatabaseType()}")
        }

        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    val now = LocalDateTime.now()

                    ps.setString(1, playerUUID.toString())
                    ps.setBoolean(2, state.vanished)
                    ps.setBoolean(3, state.hideMe)
                    ps.setBoolean(4, state.hideOthers)
                    ps.setString(5, serializeShowOthers(state.showOthers))
                    ps.setObject(6, now)
                    ps.setObject(7, now)

                    val affected = ps.executeUpdate()
                    conn.commit()
                    affected > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to save vanish state for $playerUUID: ${e.message}", e)
        }
    }

    fun delete(playerUUID: UUID): Boolean {
        val sql = "DELETE FROM lp_vanish_states WHERE player_uuid = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerUUID.toString())
                    val affected = ps.executeUpdate()
                    conn.commit()
                    affected > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to delete vanish state for $playerUUID: ${e.message}", e)
        }
    }

    // =================== VANISH STATE UPDATES ===================

    fun setVanished(playerUUID: UUID, vanished: Boolean): Boolean {
        val sql = "UPDATE lp_vanish_states SET vanished = ?, updated_at = ? WHERE player_uuid = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setBoolean(1, vanished)
                    ps.setObject(2, LocalDateTime.now())
                    ps.setString(3, playerUUID.toString())
                    val affected = ps.executeUpdate()
                    conn.commit()
                    affected > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to set vanished status for $playerUUID: ${e.message}", e)
        }
    }

    fun setHideMe(playerUUID: UUID, hideMe: Boolean): Boolean {
        val sql = "UPDATE lp_vanish_states SET hide_me = ?, updated_at = ? WHERE player_uuid = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setBoolean(1, hideMe)
                    ps.setObject(2, LocalDateTime.now())
                    ps.setString(3, playerUUID.toString())
                    val affected = ps.executeUpdate()
                    conn.commit()
                    affected > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to set hideMe status for $playerUUID: ${e.message}", e)
        }
    }

    fun setHideOthers(playerUUID: UUID, hideOthers: Boolean): Boolean {
        val sql = "UPDATE lp_vanish_states SET hide_others = ?, updated_at = ? WHERE player_uuid = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setBoolean(1, hideOthers)
                    ps.setObject(2, LocalDateTime.now())
                    ps.setString(3, playerUUID.toString())
                    val affected = ps.executeUpdate()
                    conn.commit()
                    affected > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to set hideOthers status for $playerUUID: ${e.message}", e)
        }
    }

    // =================== SHOW OTHERS MANAGEMENT ===================

    /**
     * Adds a player to the showOthers list.
     */
    fun addShowOther(playerUUID: UUID, targetUUID: UUID): Boolean {
        return try {
            val state = load(playerUUID) ?: VanishState(
                playerId = playerUUID.toString(),
                vanished = false,
                hideMe = false,
                hideOthers = false,
                showOthers = mutableSetOf()
            )

            state.showOthers.add(targetUUID)
            save(playerUUID, state)
        } catch (e: SQLException) {
            throw SQLException("Failed to add showOther for $playerUUID -> $targetUUID: ${e.message}", e)
        }
    }

    /**
     * Removes a player from the showOthers list.
     */
    fun removeShowOther(playerUUID: UUID, targetUUID: UUID): Boolean {
        return try {
            val state = load(playerUUID) ?: return false

            state.showOthers.remove(targetUUID)
            save(playerUUID, state)
        } catch (e: SQLException) {
            throw SQLException("Failed to remove showOther for $playerUUID -> $targetUUID: ${e.message}", e)
        }
    }

    /**
     * Clears all players from the showOthers list.
     */
    fun clearShowOthers(playerUUID: UUID): Boolean {
        val sql = "UPDATE lp_vanish_states SET show_others = '', updated_at = ? WHERE player_uuid = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setObject(1, LocalDateTime.now())
                    ps.setString(2, playerUUID.toString())
                    val affected = ps.executeUpdate()
                    conn.commit()
                    affected > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to clear showOthers for $playerUUID: ${e.message}", e)
        }
    }

    /**
     * Gets the showOthers list for a player.
     */
    fun getShowOthers(playerUUID: UUID): Set<UUID> {
        return try {
            val state = load(playerUUID)
            state?.showOthers ?: emptySet()
        } catch (e: SQLException) {
            throw SQLException("Failed to get showOthers for $playerUUID: ${e.message}", e)
        }
    }

    // =================== QUERY METHODS ===================

    fun isVanished(playerUUID: UUID): Boolean {
        val sql = "SELECT vanished FROM lp_vanish_states WHERE player_uuid = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerUUID.toString())
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getBoolean("vanished") else false
                    }
                }
            }
        } catch (e: SQLException) {
            false
        }
    }

    fun hasHideMe(playerUUID: UUID): Boolean {
        val sql = "SELECT hide_me FROM lp_vanish_states WHERE player_uuid = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerUUID.toString())
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getBoolean("hide_me") else false
                    }
                }
            }
        } catch (e: SQLException) {
            false
        }
    }

    fun hasHideOthers(playerUUID: UUID): Boolean {
        val sql = "SELECT hide_others FROM lp_vanish_states WHERE player_uuid = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerUUID.toString())
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rs.getBoolean("hide_others") else false
                    }
                }
            }
        } catch (e: SQLException) {
            false
        }
    }

    // =================== HELPER METHODS ===================

    private fun serializeShowOthers(showOthers: Set<UUID>): String {
        return showOthers.joinToString(",") { it.toString() }
    }

    private fun deserializeShowOthers(showOthersStr: String?): MutableSet<UUID> {
        if (showOthersStr.isNullOrBlank()) return mutableSetOf()
        return showOthersStr.split(",").mapNotNull {
            try {
                UUID.fromString(it.trim())
            } catch (e: IllegalArgumentException) {
                null
            }
        }.toMutableSet()
    }

    private fun rowToVanishState(rs: ResultSet): VanishState {
        return VanishState(
            playerId = rs.getString("player_uuid"),
            vanished = rs.getBoolean("vanished"),
            hideMe = rs.getBoolean("hide_me"),
            hideOthers = rs.getBoolean("hide_others"),
            showOthers = deserializeShowOthers(rs.getString("show_others"))
        )
    }
}