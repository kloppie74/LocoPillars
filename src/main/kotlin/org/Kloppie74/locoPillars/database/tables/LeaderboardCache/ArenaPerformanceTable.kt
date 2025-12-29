package org.Kloppie74.locoPillars.database.tables.LeaderboardCache

object ArenaPerformanceTable {
    const val TABLE_NAME = "lp_arena_performance"

    const val CREATE_SQL_SQLITE = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            id INTEGER PRIMARY KEY AUTO_INCREMENT,
            arena_name TEXT NOT NULL,
            sample_time DATETIME NOT NULL,
            tps REAL NOT NULL,
            memory_usage INTEGER NOT NULL,
            active_players INTEGER NOT NULL,
            game_state TEXT NOT NULL,
            game_duration INTEGER,
            cpu_usage REAL,
            chunk_count INTEGER,
            entity_count INTEGER,
            player_ping_average REAL,
            packet_loss_rate REAL,
            server_version TEXT,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
    """

    const val CREATE_SQL_MYSQL = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            arena_name VARCHAR(64) NOT NULL,
            sample_time TIMESTAMP NOT NULL,
            tps DOUBLE NOT NULL,
            memory_usage BIGINT NOT NULL,
            active_players INT NOT NULL,
            game_state VARCHAR(32) NOT NULL,
            game_duration INT,
            cpu_usage DOUBLE,
            chunk_count INT,
            entity_count INT,
            player_ping_average DOUBLE,
            packet_loss_rate DOUBLE,
            server_version VARCHAR(32),
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            INDEX idx_arena_perf_time (arena_name, sample_time DESC),
            INDEX idx_arena_perf_tps (tps ASC)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """
}