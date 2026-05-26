package org.Kloppie74.locoPillars.cosmetics.database

import org.Kloppie74.locoPillars.cosmetics.engine.CosmeticType
import org.Kloppie74.locoPillars.database.DatabaseManager
import java.sql.SQLException
import java.time.LocalDateTime

/**
 * Handles all cosmetic persistence:
 *   - which cosmetics a player owns
 *   - which cosmetic a player has selected per type
 */
object CosmeticDAO {

    // ── Owned ─────────────────────────────────────────────────────────────────

    /** Returns true when the player already owns this cosmetic. */
    fun owns(playerId: String, cosmeticId: String, type: CosmeticType): Boolean {
        val sql = "SELECT 1 FROM lp_cosmetic_owned WHERE player_id=? AND cosmetic_id=? AND cosmetic_type=?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.setString(2, cosmeticId)
                    ps.setString(3, type.name)
                    ps.executeQuery().use { rs -> rs.next() }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("CosmeticDAO.owns failed: ${e.message}", e)
        }
    }

    /** Grants a cosmetic to a player. No-op if already owned. */
    fun grant(playerId: String, cosmeticId: String, type: CosmeticType): Boolean {
        if (owns(playerId, cosmeticId, type)) return false
        val sql = "INSERT INTO lp_cosmetic_owned (player_id, cosmetic_id, cosmetic_type, acquired_at) VALUES (?,?,?,?)"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.setString(2, cosmeticId)
                    ps.setString(3, type.name)
                    ps.setObject(4, LocalDateTime.now())
                    val rows = ps.executeUpdate()
                    conn.commit()
                    rows > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("CosmeticDAO.grant failed: ${e.message}", e)
        }
    }

    /** Returns all cosmetic IDs owned by a player for a specific type. */
    fun getOwned(playerId: String, type: CosmeticType): Set<String> {
        val sql = "SELECT cosmetic_id FROM lp_cosmetic_owned WHERE player_id=? AND cosmetic_type=?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.setString(2, type.name)
                    ps.executeQuery().use { rs ->
                        val ids = mutableSetOf<String>()
                        while (rs.next()) ids.add(rs.getString("cosmetic_id"))
                        ids
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("CosmeticDAO.getOwned failed: ${e.message}", e)
        }
    }

    /** Returns all cosmetic IDs owned by a player across all types. */
    fun getAllOwned(playerId: String): Map<CosmeticType, Set<String>> {
        val sql = "SELECT cosmetic_id, cosmetic_type FROM lp_cosmetic_owned WHERE player_id=?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.executeQuery().use { rs ->
                        val result = mutableMapOf<CosmeticType, MutableSet<String>>()
                        while (rs.next()) {
                            val typeName = rs.getString("cosmetic_type")
                            val type = runCatching { CosmeticType.valueOf(typeName) }.getOrNull() ?: continue
                            result.getOrPut(type) { mutableSetOf() }.add(rs.getString("cosmetic_id"))
                        }
                        result
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("CosmeticDAO.getAllOwned failed: ${e.message}", e)
        }
    }

    // ── Selected ──────────────────────────────────────────────────────────────

    /** Returns the currently selected cosmetic ID for a player+type, or null if none. */
    fun getSelected(playerId: String, type: CosmeticType): String? {
        val sql = "SELECT cosmetic_id FROM lp_cosmetic_selected WHERE player_id=? AND cosmetic_type=?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.setString(2, type.name)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getString("cosmetic_id") else null }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("CosmeticDAO.getSelected failed: ${e.message}", e)
        }
    }

    /** Sets the active cosmetic for a player+type (upsert). */
    fun setSelected(playerId: String, type: CosmeticType, cosmeticId: String): Boolean {
        val sql = """
            INSERT INTO lp_cosmetic_selected (player_id, cosmetic_type, cosmetic_id)
            VALUES (?, ?, ?)
            ON DUPLICATE KEY UPDATE cosmetic_id = VALUES(cosmetic_id)
        """
        val sqlSqlite = """
            INSERT OR REPLACE INTO lp_cosmetic_selected (player_id, cosmetic_type, cosmetic_id)
            VALUES (?, ?, ?)
        """
        return try {
            DatabaseManager.getConnection().use { conn ->
                val isMysql = DatabaseManager.getDatabaseType().lowercase() in listOf("mysql", "mariadb")
                conn.prepareStatement(if (isMysql) sql else sqlSqlite).use { ps ->
                    ps.setString(1, playerId)
                    ps.setString(2, type.name)
                    ps.setString(3, cosmeticId)
                    val rows = ps.executeUpdate()
                    conn.commit()
                    rows > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("CosmeticDAO.setSelected failed: ${e.message}", e)
        }
    }

    /** Clears the active cosmetic selection for a player+type. */
    fun clearSelected(playerId: String, type: CosmeticType): Boolean {
        val sql = "DELETE FROM lp_cosmetic_selected WHERE player_id=? AND cosmetic_type=?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.setString(2, type.name)
                    val rows = ps.executeUpdate()
                    conn.commit()
                    rows > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("CosmeticDAO.clearSelected failed: ${e.message}", e)
        }
    }

    /** Returns all selections for a player as a map of type → cosmetic id. */
    fun getAllSelections(playerId: String): Map<CosmeticType, String> {
        val sql = "SELECT cosmetic_type, cosmetic_id FROM lp_cosmetic_selected WHERE player_id=?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.executeQuery().use { rs ->
                        val result = mutableMapOf<CosmeticType, String>()
                        while (rs.next()) {
                            val type = runCatching { CosmeticType.valueOf(rs.getString("cosmetic_type")) }.getOrNull() ?: continue
                            result[type] = rs.getString("cosmetic_id")
                        }
                        result
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("CosmeticDAO.getAllSelections failed: ${e.message}", e)
        }
    }
}
