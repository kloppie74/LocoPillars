package org.Kloppie74.locoPillars.database.tables.LeaderboardCache

object LeaderboardCacheTable {
    const val TABLE_NAME = "lp_leaderboard_cache"

    const val CREATE_SQL_SQLITE = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_id TEXT NOT NULL,
            player_name TEXT NOT NULL,
            leaderboard_type TEXT NOT NULL,
            rank_position INTEGER NOT NULL,
            rank_value REAL NOT NULL,
            rank_display_value TEXT NOT NULL,
            secondary_value REAL,
            secondary_display TEXT,
            tertiary_value REAL,
            tertiary_display TEXT,
            percentile REAL NOT NULL DEFAULT 0.0,
            score_normalized REAL NOT NULL DEFAULT 0.0,
            previous_rank INTEGER,
            rank_change INTEGER NOT NULL DEFAULT 0,
            rank_change_direction TEXT NOT NULL DEFAULT 'STABLE',
            qualifying_games INTEGER NOT NULL DEFAULT 0,
            eligible BOOLEAN NOT NULL DEFAULT 1,
            last_updated DATETIME NOT NULL,
            cache_version INTEGER NOT NULL DEFAULT 1,
            PRIMARY KEY (player_id, leaderboard_type)
        )
    """

    const val CREATE_SQL_MYSQL = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_id VARCHAR(36) NOT NULL,
            player_name VARCHAR(16) NOT NULL,
            leaderboard_type VARCHAR(32) NOT NULL,
            rank_position INT NOT NULL,
            rank_value DOUBLE NOT NULL,
            rank_display_value VARCHAR(64) NOT NULL,
            secondary_value DOUBLE,
            secondary_display VARCHAR(64),
            tertiary_value DOUBLE,
            tertiary_display VARCHAR(64),
            percentile DOUBLE NOT NULL DEFAULT 0.0,
            score_normalized DOUBLE NOT NULL DEFAULT 0.0,
            previous_rank INT,
            rank_change INT NOT NULL DEFAULT 0,
            rank_change_direction VARCHAR(16) NOT NULL DEFAULT 'STABLE',
            qualifying_games INT NOT NULL DEFAULT 0,
            eligible BOOLEAN NOT NULL DEFAULT TRUE,
            last_updated TIMESTAMP NOT NULL,
            cache_version INT NOT NULL DEFAULT 1,
            PRIMARY KEY (player_id, leaderboard_type),
            INDEX idx_leaderboard_type_rank (leaderboard_type, rank_position ASC),
            INDEX idx_leaderboard_percentile (leaderboard_type, percentile DESC),
            INDEX idx_leaderboard_normalized (leaderboard_type, score_normalized DESC)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """
}

