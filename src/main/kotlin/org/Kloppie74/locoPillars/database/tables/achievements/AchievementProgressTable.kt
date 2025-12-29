package org.Kloppie74.locoPillars.database.tables.achievements

object AchievementProgressTable {
    const val TABLE_NAME = "lp_achievement_progress"

    const val CREATE_SQL_SQLITE = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_id TEXT NOT NULL,
            achievement_id TEXT NOT NULL,
            current_progress INTEGER NOT NULL DEFAULT 0,
            target_progress INTEGER NOT NULL,
            progress_percentage REAL NOT NULL DEFAULT 0.0,
            last_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (player_id, achievement_id)
        )
    """

    const val CREATE_SQL_MYSQL = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            player_id VARCHAR(256) NOT NULL,
            achievement_id VARCHAR(256) NOT NULL,
            current_progress INT NOT NULL DEFAULT 0,
            target_progress INT NOT NULL,
            progress_percentage DOUBLE NOT NULL DEFAULT 0.0,
            last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (player_id, achievement_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """
}