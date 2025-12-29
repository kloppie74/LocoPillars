package org.Kloppie74.locoPillars.database.tables.LeaderboardCache

object LeaderboardMetadataTable {
    const val TABLE_NAME = "lp_leaderboard_metadata"

    const val CREATE_SQL_SQLITE = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            leaderboard_type TEXT PRIMARY KEY,
            last_full_update DATETIME,
            last_incremental_update DATETIME,
            next_scheduled_update DATETIME,
            total_entries INTEGER NOT NULL DEFAULT 0,
            eligible_entries INTEGER NOT NULL DEFAULT 0,
            qualifying_threshold INTEGER NOT NULL DEFAULT 0,
            update_duration_ms INTEGER NOT NULL DEFAULT 0,
            cache_hit_rate REAL NOT NULL DEFAULT 0.0,
            query_count_24h INTEGER NOT NULL DEFAULT 0,
            update_frequency_minutes INTEGER NOT NULL DEFAULT 60,
            auto_update_enabled BOOLEAN NOT NULL DEFAULT 1,
            cache_version INTEGER NOT NULL DEFAULT 1,
            schema_version INTEGER NOT NULL DEFAULT 1,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
    """

    const val CREATE_SQL_MYSQL = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            leaderboard_type VARCHAR(32) PRIMARY KEY,
            last_full_update TIMESTAMP NULL,
            last_incremental_update TIMESTAMP NULL,
            next_scheduled_update TIMESTAMP NULL,
            total_entries INT NOT NULL DEFAULT 0,
            eligible_entries INT NOT NULL DEFAULT 0,
            qualifying_threshold INT NOT NULL DEFAULT 0,
            update_duration_ms INT NOT NULL DEFAULT 0,
            cache_hit_rate DOUBLE NOT NULL DEFAULT 0.0,
            query_count_24h INT NOT NULL DEFAULT 0,
            update_frequency_minutes INT NOT NULL DEFAULT 60,
            auto_update_enabled BOOLEAN NOT NULL DEFAULT TRUE,
            cache_version INT NOT NULL DEFAULT 1,
            schema_version INT NOT NULL DEFAULT 1,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            INDEX idx_leaderboard_meta_update (last_full_update DESC)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """
}