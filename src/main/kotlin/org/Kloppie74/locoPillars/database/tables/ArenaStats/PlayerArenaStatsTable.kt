package org.Kloppie74.locoPillars.database.tables.ArenaStats

object PlayerArenaStatsTable {
    const val TABLE_NAME = "lp_player_arena_stats"

    const val CREATE_SQL_SQLITE = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_id TEXT NOT NULL,
            arena_name TEXT NOT NULL,
            games_played INTEGER NOT NULL DEFAULT 0,
            games_won INTEGER NOT NULL DEFAULT 0,
            games_lost INTEGER NOT NULL DEFAULT 0,
            games_quit INTEGER NOT NULL DEFAULT 0,
            total_playtime_seconds INTEGER NOT NULL DEFAULT 0,
            best_placement INTEGER,
            worst_placement INTEGER,
            average_placement REAL NOT NULL DEFAULT 0.0,
            total_kills INTEGER NOT NULL DEFAULT 0,
            total_deaths INTEGER NOT NULL DEFAULT 0,
            total_assists INTEGER NOT NULL DEFAULT 0,
            best_killstreak INTEGER NOT NULL DEFAULT 0,
            pillars_destroyed INTEGER NOT NULL DEFAULT 0,
            blocks_broken INTEGER NOT NULL DEFAULT 0,
            blocks_placed INTEGER NOT NULL DEFAULT 0,
            fastest_win_time INTEGER,
            longest_game_time INTEGER NOT NULL DEFAULT 0,
            current_win_streak INTEGER NOT NULL DEFAULT 0,
            best_win_streak INTEGER NOT NULL DEFAULT 0,
            current_loss_streak INTEGER NOT NULL DEFAULT 0,
            arena_rating REAL NOT NULL DEFAULT 1000.0,
            rating_peak REAL NOT NULL DEFAULT 1000.0,
            rating_change_total INTEGER NOT NULL DEFAULT 0,
            perfect_games INTEGER NOT NULL DEFAULT 0,
            comeback_wins INTEGER NOT NULL DEFAULT 0,
            dominant_wins INTEGER NOT NULL DEFAULT 0,
            first_played DATETIME,
            last_played DATETIME,
            favorite_spawn_position INTEGER,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (player_id, arena_name)
        )
    """

    const val CREATE_SQL_MYSQL = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_id VARCHAR(36) NOT NULL,
            arena_name VARCHAR(64) NOT NULL,
            games_played INT NOT NULL DEFAULT 0,
            games_won INT NOT NULL DEFAULT 0,
            games_lost INT NOT NULL DEFAULT 0,
            games_quit INT NOT NULL DEFAULT 0,
            total_playtime_seconds BIGINT NOT NULL DEFAULT 0,
            best_placement INT,
            worst_placement INT,
            average_placement DOUBLE NOT NULL DEFAULT 0.0,
            total_kills INT NOT NULL DEFAULT 0,
            total_deaths INT NOT NULL DEFAULT 0,
            total_assists INT NOT NULL DEFAULT 0,
            best_killstreak INT NOT NULL DEFAULT 0,
            pillars_destroyed INT NOT NULL DEFAULT 0,
            blocks_broken BIGINT NOT NULL DEFAULT 0,
            blocks_placed BIGINT NOT NULL DEFAULT 0,
            fastest_win_time INT,
            longest_game_time INT NOT NULL DEFAULT 0,
            current_win_streak INT NOT NULL DEFAULT 0,
            best_win_streak INT NOT NULL DEFAULT 0,
            current_loss_streak INT NOT NULL DEFAULT 0,
            arena_rating DOUBLE NOT NULL DEFAULT 1000.0,
            rating_peak DOUBLE NOT NULL DEFAULT 1000.0,
            rating_change_total INT NOT NULL DEFAULT 0,
            perfect_games INT NOT NULL DEFAULT 0,
            comeback_wins INT NOT NULL DEFAULT 0,
            dominant_wins INT NOT NULL DEFAULT 0,
            first_played TIMESTAMP NULL,
            last_played TIMESTAMP NULL,
            favorite_spawn_position INT,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
            PRIMARY KEY (player_id, arena_name),
            INDEX idx_player_arena_rating (arena_rating DESC),
            INDEX idx_player_arena_wins (games_won DESC)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """
}