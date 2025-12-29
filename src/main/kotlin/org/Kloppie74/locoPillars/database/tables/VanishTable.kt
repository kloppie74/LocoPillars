package org.Kloppie74.locoPillars.database.tables

object VanishTable {
    const val TABLE_NAME = "lp_vanish_states"

    const val CREATE_SQL_SQLITE = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_uuid TEXT PRIMARY KEY,
            vanished BOOLEAN NOT NULL DEFAULT 0,
            hide_me BOOLEAN NOT NULL DEFAULT 0,
            hide_others BOOLEAN NOT NULL DEFAULT 0,
            show_others TEXT,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
    """

    const val CREATE_SQL_MYSQL = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_uuid VARCHAR(36) PRIMARY KEY,
            vanished BOOLEAN NOT NULL DEFAULT 0,
            hide_me BOOLEAN NOT NULL DEFAULT 0,
            hide_others BOOLEAN NOT NULL DEFAULT 0,
            show_others TEXT,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """
}