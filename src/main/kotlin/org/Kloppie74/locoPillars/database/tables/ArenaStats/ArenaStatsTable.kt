package org.Kloppie74.locoPillars.database.tables.ArenaStats

object ArenaStatsTable {
    const val TABLE_NAME = "lp_arena_stats"

    const val CREATE_SQL_SQLITE = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            arena_name TEXT PRIMARY KEY,
            arena_display_name TEXT,
            total_games_played INTEGER NOT NULL DEFAULT 0,
            total_games_completed INTEGER NOT NULL DEFAULT 0,
            total_games_cancelled INTEGER NOT NULL DEFAULT 0,
            total_unique_players INTEGER NOT NULL DEFAULT 0,
            average_players_per_game REAL NOT NULL DEFAULT 0.0,
            peak_concurrent_players INTEGER NOT NULL DEFAULT 0,
            total_playtime_seconds INTEGER NOT NULL DEFAULT 0,
            average_game_duration REAL NOT NULL DEFAULT 0.0,
            shortest_game_duration INTEGER NOT NULL DEFAULT 999999,
            longest_game_duration INTEGER NOT NULL DEFAULT 0,
            total_kills INTEGER NOT NULL DEFAULT 0,
            total_deaths INTEGER NOT NULL DEFAULT 0,
            average_kills_per_game REAL NOT NULL DEFAULT 0.0,
            highest_kills_in_game INTEGER NOT NULL DEFAULT 0,
            total_pillars_destroyed INTEGER NOT NULL DEFAULT 0,
            total_blocks_broken INTEGER NOT NULL DEFAULT 0,
            total_blocks_placed INTEGER NOT NULL DEFAULT 0,
            average_pillars_per_game REAL NOT NULL DEFAULT 0.0,
            average_tps REAL NOT NULL DEFAULT 20.0,
            lowest_tps REAL NOT NULL DEFAULT 20.0,
            highest_memory_usage INTEGER NOT NULL DEFAULT 0,
            crash_count INTEGER NOT NULL DEFAULT 0,
            popularity_score REAL NOT NULL DEFAULT 0.0,
            rating_average REAL NOT NULL DEFAULT 0.0,
            rating_count INTEGER NOT NULL DEFAULT 0,
            last_played DATETIME,
            games_today INTEGER NOT NULL DEFAULT 0,
            games_this_week INTEGER NOT NULL DEFAULT 0,
            games_this_month INTEGER NOT NULL DEFAULT 0,
            win_distribution_variance REAL NOT NULL DEFAULT 0.0,
            average_game_balance REAL NOT NULL DEFAULT 0.0,
            first_played DATETIME,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
    """

    const val CREATE_SQL_MYSQL = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            arena_name VARCHAR(64) PRIMARY KEY,
            arena_display_name VARCHAR(128),
            total_games_played INT NOT NULL DEFAULT 0,
            total_games_completed INT NOT NULL DEFAULT 0,
            total_games_cancelled INT NOT NULL DEFAULT 0,
            total_unique_players INT NOT NULL DEFAULT 0,
            average_players_per_game DOUBLE NOT NULL DEFAULT 0.0,
            peak_concurrent_players INT NOT NULL DEFAULT 0,
            total_playtime_seconds BIGINT NOT NULL DEFAULT 0,
            average_game_duration DOUBLE NOT NULL DEFAULT 0.0,
            shortest_game_duration INT NOT NULL DEFAULT 999999,
            longest_game_duration INT NOT NULL DEFAULT 0,
            total_kills INT NOT NULL DEFAULT 0,
            total_deaths INT NOT NULL DEFAULT 0,
            average_kills_per_game DOUBLE NOT NULL DEFAULT 0.0,
            highest_kills_in_game INT NOT NULL DEFAULT 0,
            total_pillars_destroyed INT NOT NULL DEFAULT 0,
            total_blocks_broken BIGINT NOT NULL DEFAULT 0,
            total_blocks_placed BIGINT NOT NULL DEFAULT 0,
            average_pillars_per_game DOUBLE NOT NULL DEFAULT 0.0,
            average_tps DOUBLE NOT NULL DEFAULT 20.0,
            lowest_tps DOUBLE NOT NULL DEFAULT 20.0,
            highest_memory_usage BIGINT NOT NULL DEFAULT 0,
            crash_count INT NOT NULL DEFAULT 0,
            popularity_score DOUBLE NOT NULL DEFAULT 0.0,
            rating_average DOUBLE NOT NULL DEFAULT 0.0,
            rating_count INT NOT NULL DEFAULT 0,
            last_played TIMESTAMP NULL,
            games_today INT NOT NULL DEFAULT 0,
            games_this_week INT NOT NULL DEFAULT 0,
            games_this_month INT NOT NULL DEFAULT 0,
            win_distribution_variance DOUBLE NOT NULL DEFAULT 0.0,
            average_game_balance DOUBLE NOT NULL DEFAULT 0.0,
            first_played TIMESTAMP NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            INDEX idx_arena_popularity (popularity_score DESC),
            INDEX idx_arena_last_played (last_played DESC)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """
}
