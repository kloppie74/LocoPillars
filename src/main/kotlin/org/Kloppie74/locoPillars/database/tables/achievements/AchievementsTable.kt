package org.Kloppie74.locoPillars.database.tables.achievements

object AchievementDefinitionsTable {
    const val TABLE_NAME = "lp_achievement_definitions"

    const val CREATE_SQL_SQLITE = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            achievement_id TEXT PRIMARY KEY,
            achievement_name TEXT NOT NULL,
            display_name TEXT NOT NULL,
            description TEXT NOT NULL,
            category TEXT NOT NULL,
            achievement_type TEXT NOT NULL,
            target_value INTEGER NULL,
            requirement_type TEXT NOT NULL,
            difficulty TEXT NOT NULL,
            rarity_percentage REAL NOT NULL,
            experience_reward INTEGER NOT NULL,
            coin_reward INTEGER NOT NULL,
            special_rewards TEXT NULL,
            icon_material TEXT NOT NULL,
            icon_custom_model_data INTEGER NULL,
            display_order INTEGER NOT NULL,
            is_hidden BOOLEAN NOT NULL,
            is_secret BOOLEAN NOT NULL,
            is_active BOOLEAN NOT NULL,
            unlock_message TEXT NULL,
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
    """

    const val CREATE_SQL_MYSQL = """
        CREATE TABLE IF NOT EXISTS $TABLE_NAME (
            achievement_id VARCHAR(256) PRIMARY KEY,
            achievement_name VARCHAR(128) NOT NULL,
            display_name VARCHAR(128) NOT NULL,
            description TEXT NOT NULL,
            category VARCHAR(64) NOT NULL,
            achievement_type VARCHAR(32) NOT NULL,
            target_value INT NULL,
            requirement_type VARCHAR(64) NOT NULL,
            difficulty VARCHAR(32) NOT NULL,
            rarity_percentage DOUBLE NOT NULL,
            experience_reward INT NOT NULL,
            coin_reward INT NOT NULL,
            special_rewards TEXT NULL,
            icon_material VARCHAR(32) NOT NULL,
            icon_custom_model_data INT NULL,
            display_order INT NOT NULL,
            is_hidden BOOLEAN NOT NULL,
            is_secret BOOLEAN NOT NULL,
            is_active BOOLEAN NOT NULL,
            unlock_message TEXT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """
}
