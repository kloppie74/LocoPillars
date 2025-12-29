package org.Kloppie74.locoPillars.database.tables.achievements

object PlayerAchievementsTable {
    const val TABLE_NAME = "lp_player_achievements"

    const val CREATE_SQL_SQLITE = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            id INTEGER PRIMARY KEY AUTO_INCREMENT,
            player_id TEXT NOT NULL,
            achievement_id TEXT NOT NULL,
            unlocked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            progress_value INTEGER NOT NULL DEFAULT 0,
            is_completed BOOLEAN NOT NULL DEFAULT 1,
            UNIQUE(player_id, achievement_id)
        )
    """

    const val CREATE_SQL_MYSQL = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            player_id VARCHAR(256) NOT NULL,
            achievement_id VARCHAR(256) NOT NULL,
            unlocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            progress_value INT NOT NULL DEFAULT 0,
            is_completed BOOLEAN NOT NULL DEFAULT TRUE,
            UNIQUE KEY unique_player_achievement (player_id, achievement_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """
}