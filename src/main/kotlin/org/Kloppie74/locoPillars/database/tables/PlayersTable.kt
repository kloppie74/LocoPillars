package org.Kloppie74.locoPillars.database.tables

object PlayersTable {
    const val TABLE_NAME = "lp_players"

    const val CREATE_SQL_SQLITE = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_id INTEGER PRIMARY KEY AUTO_INCREMENT,
            player_name TEXT NOT NULL UNIQUE,
            display_name TEXT,
            uuid TEXT NOT NULL UNIQUE,
            first_played DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_seen DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            total_sessions INTEGER NOT NULL DEFAULT 0,
            is_active BOOLEAN NOT NULL DEFAULT 1,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
    """

    const val CREATE_SQL_MYSQL = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_id INT AUTO_INCREMENT PRIMARY KEY,
            player_name VARCHAR(16) NOT NULL UNIQUE,
            display_name VARCHAR(32),
            uuid VARCHAR(36) NOT NULL UNIQUE,
            first_played TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            total_sessions INT NOT NULL DEFAULT 0,
            is_active BOOLEAN NOT NULL DEFAULT TRUE,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            INDEX idx_player_name (player_name),
            INDEX idx_uuid (uuid),
            INDEX idx_last_seen (last_seen),
            INDEX idx_is_active (is_active)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """
}