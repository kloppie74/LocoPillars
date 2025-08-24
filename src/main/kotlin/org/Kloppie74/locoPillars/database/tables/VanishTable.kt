package org.Kloppie74.locoPillars.database.tables

import org.Kloppie74.locoPillars.database.DatabaseManager
import java.sql.SQLException
import java.time.LocalDateTime
import java.util.UUID

class VanishTable(private val databaseManager: DatabaseManager) {
    companion object {
        const val TABLE_NAME = "lp_vanish"
        private const val MAX_DB_WRITE_ATTEMPTS = 5
        private const val DB_WRITE_RETRY_BASE_MS = 50L
    }

    fun createTable(): Boolean {
        val sql = when (databaseManager.getDatabaseType()) {
            "sqlite", "h2" -> """
                CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                    player_id TEXT PRIMARY KEY,
                    vanished BOOLEAN NOT NULL DEFAULT 0,
                    hide_me BOOLEAN NOT NULL DEFAULT 0,
                    hide_others BOOLEAN NOT NULL DEFAULT 0,
                    show_others TEXT, -- CSV UUIDs
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """.trimIndent()
            "mysql", "mariadb" -> """
                CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                    player_id VARCHAR(36) PRIMARY KEY,
                    vanished BOOLEAN NOT NULL DEFAULT 0,
                    hide_me BOOLEAN NOT NULL DEFAULT 0,
                    hide_others BOOLEAN NOT NULL DEFAULT 0,
                    show_others TEXT,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
            """.trimIndent()
            "postgresql" -> """
                CREATE TABLE IF NOT EXISTS $TABLE_NAME (
                    player_id UUID PRIMARY KEY,
                    vanished BOOLEAN NOT NULL DEFAULT FALSE,
                    hide_me BOOLEAN NOT NULL DEFAULT FALSE,
                    hide_others BOOLEAN NOT NULL DEFAULT FALSE,
                    show_others TEXT,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
            """.trimIndent()
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
            throw SQLException("Failed to create vanish table: ${e.message}", e)
        }
    }

    fun loadState(playerId: UUID): VanishState? {
        val sql = "SELECT * FROM $TABLE_NAME WHERE player_id = ?"
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId.toString())
                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            val vanished = rs.getBoolean("vanished")
                            val hideMe = rs.getBoolean("hide_me")
                            val hideOthers = rs.getBoolean("hide_others")
                            val showOthersCsv = rs.getString("show_others")
                            val showOthers = showOthersCsv?.split(",")
                                ?.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
                                ?.toMutableSet() ?: mutableSetOf()
                            VanishState(vanished, hideMe, hideOthers, showOthers)
                        } else null
                    }
                }
            }
        } catch (e: SQLException) {
            null
        }
    }

    fun saveState(playerId: UUID, state: VanishState): Boolean {
        val sql = when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" -> """
                INSERT INTO $TABLE_NAME (player_id, vanished, hide_me, hide_others, show_others, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    vanished = VALUES(vanished),
                    hide_me = VALUES(hide_me),
                    hide_others = VALUES(hide_others),
                    show_others = VALUES(show_others),
                    updated_at = VALUES(updated_at)
            """.trimIndent()
            "sqlite", "h2" -> """
                INSERT INTO $TABLE_NAME (player_id, vanished, hide_me, hide_others, show_others, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_id) DO UPDATE SET
                    vanished = excluded.vanished,
                    hide_me = excluded.hide_me,
                    hide_others = excluded.hide_others,
                    show_others = excluded.show_others,
                    updated_at = excluded.updated_at
            """.trimIndent()
            "postgresql" -> """
                INSERT INTO $TABLE_NAME (player_id, vanished, hide_me, hide_others, show_others, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_id) DO UPDATE SET
                    vanished = EXCLUDED.vanished,
                    hide_me = EXCLUDED.hide_me,
                    hide_others = EXCLUDED.hide_others,
                    show_others = EXCLUDED.show_others,
                    updated_at = EXCLUDED.updated_at
            """.trimIndent()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        val showOthersCsv = if (state.showOthers.isEmpty()) null else state.showOthers.joinToString(",") { it.toString() }
        var attempts = 0
        while (attempts < MAX_DB_WRITE_ATTEMPTS) {
            try {
                return databaseManager.executeSync { connection ->
                    connection.prepareStatement(sql).use { statement ->
                        statement.setString(1, playerId.toString())
                        statement.setBoolean(2, state.vanished)
                        statement.setBoolean(3, state.hideMe)
                        statement.setBoolean(4, state.hideOthers)
                        statement.setString(5, showOthersCsv)
                        statement.setObject(6, LocalDateTime.now())
                        statement.executeUpdate() > 0
                    }
                }
            } catch (e: SQLException) {
                if (isLockedError(e)) {
                    attempts++
                    Thread.sleep(DB_WRITE_RETRY_BASE_MS * attempts)
                    continue
                }
                return false
            }
        }
        return false
    }

    private fun isLockedError(e: SQLException): Boolean {
        val msg = e.message?.lowercase() ?: ""
        return msg.contains("database is locked") || msg.contains("sqlite_busy")
    }
}

data class VanishState(
    var vanished: Boolean,
    var hideMe: Boolean,
    var hideOthers: Boolean,
    var showOthers: MutableSet<UUID>
)