package org.Kloppie74.locoPillars.database.tables

import org.Kloppie74.locoPillars.utils.achievements.DatabaseAchievement
import org.Kloppie74.locoPillars.database.DatabaseManager
import java.sql.SQLException
import java.time.LocalDateTime

/**
 * Achievement database table manager.
 * All achievement progress is tracked here per achievement and per player!
 */
class AchievementsTable(private val databaseManager: DatabaseManager) {

    companion object {
        const val ACHIEVEMENT_DEFINITIONS_TABLE = "lp_achievement_definitions"
        const val PLAYER_ACHIEVEMENTS_TABLE = "lp_player_achievements"
        const val ACHIEVEMENT_PROGRESS_TABLE = "lp_achievement_progress"
    }

    fun createTable(): Boolean {
        val definitionsSql = when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" -> createDefinitionsTableMySQL()
            "sqlite", "h2" -> createDefinitionsTableSQLite()
            "postgresql" -> createDefinitionsTablePostgreSQL()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }
        val playerAchievementsSql = when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" -> createPlayerAchievementsTableMySQL()
            "sqlite", "h2" -> createPlayerAchievementsTableSQLite()
            "postgresql" -> createPlayerAchievementsTablePostgreSQL()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }
        val progressSql = when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" -> createProgressTableMySQL()
            "sqlite", "h2" -> createProgressTableSQLite()
            "postgresql" -> createProgressTablePostgreSQL()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        return try {
            databaseManager.executeSync { connection ->
                connection.createStatement().use { it.execute(definitionsSql) }
                connection.createStatement().use { it.execute(playerAchievementsSql) }
                connection.createStatement().use { it.execute(progressSql) }
                true
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to create achievement tables: ${e.message}", e)
        }
    }

    private fun createDefinitionsTableSQLite(): String = """
        CREATE TABLE IF NOT EXISTS $ACHIEVEMENT_DEFINITIONS_TABLE (
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
    """.trimIndent()

    private fun createPlayerAchievementsTableSQLite(): String = """
        CREATE TABLE IF NOT EXISTS $PLAYER_ACHIEVEMENTS_TABLE (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            player_id TEXT NOT NULL,
            achievement_id TEXT NOT NULL,
            unlocked_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            progress_value INTEGER NOT NULL DEFAULT 0,
            is_completed BOOLEAN NOT NULL DEFAULT 1,
            UNIQUE(player_id, achievement_id)
        )
    """.trimIndent()

    private fun createProgressTableSQLite(): String = """
        CREATE TABLE IF NOT EXISTS $ACHIEVEMENT_PROGRESS_TABLE (
            player_id TEXT NOT NULL,
            achievement_id TEXT NOT NULL,
            current_progress INTEGER NOT NULL DEFAULT 0,
            target_progress INTEGER NOT NULL,
            progress_percentage REAL NOT NULL DEFAULT 0.0,
            last_updated DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (player_id, achievement_id)
        )
    """.trimIndent()

    private fun createDefinitionsTableMySQL(): String = """
        CREATE TABLE IF NOT EXISTS $ACHIEVEMENT_DEFINITIONS_TABLE (
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
    """.trimIndent()

    private fun createPlayerAchievementsTableMySQL(): String = """
        CREATE TABLE IF NOT EXISTS $PLAYER_ACHIEVEMENTS_TABLE (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            player_id VARCHAR(256) NOT NULL,
            achievement_id VARCHAR(256) NOT NULL,
            unlocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            progress_value INT NOT NULL DEFAULT 0,
            is_completed BOOLEAN NOT NULL DEFAULT TRUE,
            UNIQUE KEY unique_player_achievement (player_id, achievement_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()

    private fun createProgressTableMySQL(): String = """
        CREATE TABLE IF NOT EXISTS $ACHIEVEMENT_PROGRESS_TABLE (
            player_id VARCHAR(256) NOT NULL,
            achievement_id VARCHAR(256) NOT NULL,
            current_progress INT NOT NULL DEFAULT 0,
            target_progress INT NOT NULL,
            progress_percentage DOUBLE NOT NULL DEFAULT 0.0,
            last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (player_id, achievement_id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()

    private fun createDefinitionsTablePostgreSQL(): String = """
        CREATE TABLE IF NOT EXISTS $ACHIEVEMENT_DEFINITIONS_TABLE (
            achievement_id VARCHAR(256) PRIMARY KEY,
            achievement_name VARCHAR(128) NOT NULL,
            display_name VARCHAR(128) NOT NULL,
            description TEXT NOT NULL,
            category VARCHAR(64) NOT NULL,
            achievement_type VARCHAR(32) NOT NULL,
            target_value INTEGER NULL,
            requirement_type VARCHAR(64) NOT NULL,
            difficulty VARCHAR(32) NOT NULL,
            rarity_percentage DOUBLE PRECISION NOT NULL,
            experience_reward INTEGER NOT NULL,
            coin_reward INTEGER NOT NULL,
            special_rewards TEXT NULL,
            icon_material VARCHAR(32) NOT NULL,
            icon_custom_model_data INTEGER NULL,
            display_order INTEGER NOT NULL,
            is_hidden BOOLEAN NOT NULL,
            is_secret BOOLEAN NOT NULL,
            is_active BOOLEAN NOT NULL,
            unlock_message TEXT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        )
    """.trimIndent()

    private fun createPlayerAchievementsTablePostgreSQL(): String = """
        CREATE TABLE IF NOT EXISTS $PLAYER_ACHIEVEMENTS_TABLE (
            id BIGSERIAL PRIMARY KEY,
            player_id VARCHAR(256) NOT NULL,
            achievement_id VARCHAR(256) NOT NULL,
            unlocked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            progress_value INTEGER NOT NULL DEFAULT 0,
            is_completed BOOLEAN NOT NULL DEFAULT TRUE,
            UNIQUE(player_id, achievement_id)
        )
    """.trimIndent()

    private fun createProgressTablePostgreSQL(): String = """
        CREATE TABLE IF NOT EXISTS $ACHIEVEMENT_PROGRESS_TABLE (
            player_id VARCHAR(256) NOT NULL,
            achievement_id VARCHAR(256) NOT NULL,
            current_progress INTEGER NOT NULL DEFAULT 0,
            target_progress INTEGER NOT NULL,
            progress_percentage DOUBLE PRECISION NOT NULL DEFAULT 0.0,
            last_updated TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            PRIMARY KEY (player_id, achievement_id)
        )
    """.trimIndent()

    /**
     * Cross-DB upsert for achievement definition.
     */
    fun upsertAchievementDefinition(achievement: DatabaseAchievement): Boolean {
        val sql = when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" -> """
                INSERT INTO $ACHIEVEMENT_DEFINITIONS_TABLE (
                    achievement_id, achievement_name, display_name, description, category,
                    achievement_type, target_value, requirement_type, difficulty,
                    rarity_percentage, experience_reward, coin_reward, special_rewards,
                    icon_material, icon_custom_model_data, display_order,
                    is_hidden, is_secret, is_active, unlock_message, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON DUPLICATE KEY UPDATE
                    achievement_name = VALUES(achievement_name),
                    display_name = VALUES(display_name),
                    description = VALUES(description),
                    category = VALUES(category),
                    achievement_type = VALUES(achievement_type),
                    target_value = VALUES(target_value),
                    requirement_type = VALUES(requirement_type),
                    difficulty = VALUES(difficulty),
                    rarity_percentage = VALUES(rarity_percentage),
                    experience_reward = VALUES(experience_reward),
                    coin_reward = VALUES(coin_reward),
                    special_rewards = VALUES(special_rewards),
                    icon_material = VALUES(icon_material),
                    icon_custom_model_data = VALUES(icon_custom_model_data),
                    display_order = VALUES(display_order),
                    is_hidden = VALUES(is_hidden),
                    is_secret = VALUES(is_secret),
                    is_active = VALUES(is_active),
                    unlock_message = VALUES(unlock_message),
                    updated_at = CURRENT_TIMESTAMP
            """.trimIndent()
            "sqlite", "h2" -> """
                INSERT OR REPLACE INTO $ACHIEVEMENT_DEFINITIONS_TABLE (
                    achievement_id, achievement_name, display_name, description, category,
                    achievement_type, target_value, requirement_type, difficulty,
                    rarity_percentage, experience_reward, coin_reward, special_rewards,
                    icon_material, icon_custom_model_data, display_order,
                    is_hidden, is_secret, is_active, unlock_message, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            """.trimIndent()
            "postgresql" -> """
                INSERT INTO $ACHIEVEMENT_DEFINITIONS_TABLE (
                    achievement_id, achievement_name, display_name, description, category,
                    achievement_type, target_value, requirement_type, difficulty,
                    rarity_percentage, experience_reward, coin_reward, special_rewards,
                    icon_material, icon_custom_model_data, display_order,
                    is_hidden, is_secret, is_active, unlock_message, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT (achievement_id) DO UPDATE SET
                    achievement_name = EXCLUDED.achievement_name,
                    display_name = EXCLUDED.display_name,
                    description = EXCLUDED.description,
                    category = EXCLUDED.category,
                    achievement_type = EXCLUDED.achievement_type,
                    target_value = EXCLUDED.target_value,
                    requirement_type = EXCLUDED.requirement_type,
                    difficulty = EXCLUDED.difficulty,
                    rarity_percentage = EXCLUDED.rarity_percentage,
                    experience_reward = EXCLUDED.experience_reward,
                    coin_reward = EXCLUDED.coin_reward,
                    special_rewards = EXCLUDED.special_rewards,
                    icon_material = EXCLUDED.icon_material,
                    icon_custom_model_data = EXCLUDED.icon_custom_model_data,
                    display_order = EXCLUDED.display_order,
                    is_hidden = EXCLUDED.is_hidden,
                    is_secret = EXCLUDED.is_secret,
                    is_active = EXCLUDED.is_active,
                    unlock_message = EXCLUDED.unlock_message,
                    updated_at = CURRENT_TIMESTAMP
            """.trimIndent()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, achievement.achievementId)
                    statement.setString(2, achievement.achievementName)
                    statement.setString(3, achievement.displayName)
                    statement.setString(4, achievement.description)
                    statement.setString(5, achievement.category)
                    statement.setString(6, achievement.achievementType)
                    if (achievement.targetValue != null) statement.setInt(7, achievement.targetValue) else statement.setNull(7, java.sql.Types.INTEGER)
                    statement.setString(8, achievement.requirementType)
                    statement.setString(9, achievement.difficulty)
                    statement.setDouble(10, achievement.rarityPercentage)
                    statement.setInt(11, achievement.experienceReward)
                    statement.setInt(12, achievement.coinReward)
                    statement.setString(13, achievement.specialRewards)
                    statement.setString(14, achievement.iconMaterial)
                    if (achievement.iconCustomModelData != null) statement.setInt(15, achievement.iconCustomModelData) else statement.setNull(15, java.sql.Types.INTEGER)
                    statement.setInt(16, achievement.displayOrder)
                    statement.setBoolean(17, achievement.isHidden)
                    statement.setBoolean(18, achievement.isSecret)
                    statement.setBoolean(19, achievement.isActive)
                    statement.setString(20, achievement.unlockMessage)
                    statement.executeUpdate() > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to upsert achievement definition: ${e.message}", e)
        }
    }

    /**
     * Marks an achievement as unlocked for a player.
     * Also sets progress to target!
     * Cross-db: INSERT IGNORE/ON CONFLICT DO NOTHING for player_achievements,
     *           upsert for achievement_progress.
     */
    fun unlockAchievement(playerId: String, achievementId: String): Boolean {
        val achievement = getAchievementDefinition(achievementId)
        val target = achievement?.targetValue ?: 1
        val sql1 = when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" -> """
                INSERT IGNORE INTO $PLAYER_ACHIEVEMENTS_TABLE (
                    player_id, achievement_id, unlocked_at, is_completed
                ) VALUES (?, ?, CURRENT_TIMESTAMP, 1)
            """.trimIndent()
            "sqlite", "h2" -> """
                INSERT OR IGNORE INTO $PLAYER_ACHIEVEMENTS_TABLE (
                    player_id, achievement_id, unlocked_at, is_completed
                ) VALUES (?, ?, CURRENT_TIMESTAMP, 1)
            """.trimIndent()
            "postgresql" -> """
                INSERT INTO $PLAYER_ACHIEVEMENTS_TABLE (
                    player_id, achievement_id, unlocked_at, is_completed
                ) VALUES (?, ?, CURRENT_TIMESTAMP, 1)
                ON CONFLICT (player_id, achievement_id) DO NOTHING
            """.trimIndent()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        val sql2 = when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" -> """
                INSERT INTO $ACHIEVEMENT_PROGRESS_TABLE (
                    player_id, achievement_id, current_progress, target_progress, progress_percentage, last_updated
                ) VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    current_progress = VALUES(current_progress),
                    target_progress = VALUES(target_progress),
                    progress_percentage = VALUES(progress_percentage),
                    last_updated = VALUES(last_updated)
            """.trimIndent()
            "sqlite", "h2" -> """
                INSERT OR REPLACE INTO $ACHIEVEMENT_PROGRESS_TABLE (
                    player_id, achievement_id, current_progress, target_progress, progress_percentage, last_updated
                ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
            "postgresql" -> """
                INSERT INTO $ACHIEVEMENT_PROGRESS_TABLE (
                    player_id, achievement_id, current_progress, target_progress, progress_percentage, last_updated
                ) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (player_id, achievement_id) DO UPDATE SET
                    current_progress = EXCLUDED.current_progress,
                    target_progress = EXCLUDED.target_progress,
                    progress_percentage = EXCLUDED.progress_percentage,
                    last_updated = EXCLUDED.last_updated
            """.trimIndent()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        return try {
            databaseManager.executeSync { connection ->
                // Unlock achievement
                connection.prepareStatement(sql1).use { statement ->
                    statement.setString(1, playerId)
                    statement.setString(2, achievementId)
                    statement.executeUpdate()
                }
                // Set progress to target
                connection.prepareStatement(sql2).use { statement ->
                    statement.setString(1, playerId)
                    statement.setString(2, achievementId)
                    statement.setInt(3, target)
                    statement.setInt(4, target)
                    statement.setDouble(5, 100.0)
                    statement.setObject(6, LocalDateTime.now())
                    statement.executeUpdate()
                }
                true
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to unlock achievement $achievementId for player $playerId: ${e.message}", e)
        }
    }

    /**
     * Updates progress towards an achievement for a player.
     * Cross-db upsert for achievement_progress.
     */
    fun updateProgress(playerId: String, achievementId: String, currentProgress: Int, targetProgress: Int): Boolean {
        val progressPercentage = if (targetProgress > 0) (currentProgress.toDouble() / targetProgress * 100.0).coerceAtMost(100.0) else 100.0
        val sql = when (databaseManager.getDatabaseType()) {
            "mysql", "mariadb" -> """
                INSERT INTO $ACHIEVEMENT_PROGRESS_TABLE (
                    player_id, achievement_id, current_progress, target_progress, progress_percentage, last_updated
                ) VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    current_progress = VALUES(current_progress),
                    target_progress = VALUES(target_progress),
                    progress_percentage = VALUES(progress_percentage),
                    last_updated = VALUES(last_updated)
            """.trimIndent()
            "sqlite", "h2" -> """
                INSERT OR REPLACE INTO $ACHIEVEMENT_PROGRESS_TABLE (
                    player_id, achievement_id, current_progress, target_progress, progress_percentage, last_updated
                ) VALUES (?, ?, ?, ?, ?, ?)
            """.trimIndent()
            "postgresql" -> """
                INSERT INTO $ACHIEVEMENT_PROGRESS_TABLE (
                    player_id, achievement_id, current_progress, target_progress, progress_percentage, last_updated
                ) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (player_id, achievement_id) DO UPDATE SET
                    current_progress = EXCLUDED.current_progress,
                    target_progress = EXCLUDED.target_progress,
                    progress_percentage = EXCLUDED.progress_percentage,
                    last_updated = EXCLUDED.last_updated
            """.trimIndent()
            else -> throw IllegalArgumentException("Unsupported database type: ${databaseManager.getDatabaseType()}")
        }

        var attempts = 0
        val maxAttempts = 3
        while (attempts < maxAttempts) {
            try {
                return databaseManager.executeSync { connection ->
                    connection.prepareStatement(sql).use { statement ->
                        statement.setString(1, playerId)
                        statement.setString(2, achievementId)
                        statement.setInt(3, currentProgress)
                        statement.setInt(4, targetProgress)
                        statement.setDouble(5, progressPercentage)
                        statement.setObject(6, java.time.LocalDateTime.now())
                        statement.executeUpdate() > 0
                    }
                }
            } catch (e: SQLException) {
                if (e.message?.contains("database is locked", ignoreCase = true) == true || e.message?.contains("SQLITE_BUSY", ignoreCase = true) == true) {
                    attempts++
                    Thread.sleep(50L * attempts) // Exponentiële backoff
                    continue
                }
                throw SQLException("Failed to update achievement progress: ${e.message}", e)
            }
        }
        throw SQLException("Failed to update achievement progress: database is locked after $maxAttempts attempts")
    }

    /**
     * Gets achievement progress for a player on one achievement.
     */
    fun getAchievementProgress(playerId: String, achievementId: String): AchievementProgress? {
        val sql = """
            SELECT current_progress, target_progress, progress_percentage, last_updated
            FROM $ACHIEVEMENT_PROGRESS_TABLE
            WHERE player_id = ? AND achievement_id = ?
        """.trimIndent()
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId)
                    statement.setString(2, achievementId)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            AchievementProgress(
                                currentProgress = resultSet.getInt("current_progress"),
                                targetProgress = resultSet.getInt("target_progress"),
                                progressPercentage = resultSet.getDouble("progress_percentage"),
                                lastUpdated = resultSet.getObject("last_updated", LocalDateTime::class.java)
                            )
                        } else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get achievement progress: ${e.message}", e)
        }
    }

    /**
     * Returns true if player has unlocked (completed) an achievement.
     */
    fun hasPlayerUnlockedAchievement(playerId: String, achievementId: String): Boolean {
        val sql = """
            SELECT 1 FROM $PLAYER_ACHIEVEMENTS_TABLE
            WHERE player_id = ? AND achievement_id = ? AND is_completed = 1
        """.trimIndent()
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId)
                    statement.setString(2, achievementId)
                    statement.executeQuery().use { rs -> rs.next() }
                }
            }
        } catch (e: SQLException) {
            false
        }
    }

    /**
     * Returns all achievement definitions (only active).
     */
    fun getAllAchievements(): List<DatabaseAchievement> {
        val sql = """
            SELECT achievement_id, achievement_name, display_name, description, category,
                   achievement_type, target_value, requirement_type, difficulty,
                   rarity_percentage, experience_reward, coin_reward, special_rewards,
                   icon_material, icon_custom_model_data, display_order,
                   is_hidden, is_secret, is_active, unlock_message
            FROM $ACHIEVEMENT_DEFINITIONS_TABLE
            WHERE is_active = 1
            ORDER BY display_order, achievement_name
        """.trimIndent()
        return try {
            databaseManager.executeSync { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(sql).use { rs ->
                        val result = mutableListOf<DatabaseAchievement>()
                        while (rs.next()) {
                            result.add(
                                DatabaseAchievement(
                                    achievementId = rs.getString("achievement_id"),
                                    achievementName = rs.getString("achievement_name"),
                                    displayName = rs.getString("display_name"),
                                    description = rs.getString("description"),
                                    category = rs.getString("category"),
                                    achievementType = rs.getString("achievement_type"),
                                    targetValue = rs.getObject("target_value") as? Int,
                                    requirementType = rs.getString("requirement_type"),
                                    difficulty = rs.getString("difficulty"),
                                    rarityPercentage = rs.getDouble("rarity_percentage"),
                                    experienceReward = rs.getInt("experience_reward"),
                                    coinReward = rs.getInt("coin_reward"),
                                    specialRewards = rs.getString("special_rewards"),
                                    iconMaterial = rs.getString("icon_material"),
                                    iconCustomModelData = rs.getObject("icon_custom_model_data") as? Int,
                                    displayOrder = rs.getInt("display_order"),
                                    isHidden = rs.getBoolean("is_hidden"),
                                    isSecret = rs.getBoolean("is_secret"),
                                    isActive = rs.getBoolean("is_active"),
                                    unlockMessage = rs.getString("unlock_message")
                                )
                            )
                        }
                        result
                    }
                }
            }
        } catch (e: SQLException) {
            emptyList()
        }
    }

    /** Get achievement definition (for unlockAchievement) */
    fun getAchievementDefinition(achievementId: String): DatabaseAchievement? {
        val sql = """
            SELECT achievement_id, achievement_name, display_name, description, category,
                   achievement_type, target_value, requirement_type, difficulty,
                   rarity_percentage, experience_reward, coin_reward, special_rewards,
                   icon_material, icon_custom_model_data, display_order,
                   is_hidden, is_secret, is_active, unlock_message
            FROM $ACHIEVEMENT_DEFINITIONS_TABLE
            WHERE achievement_id = ?
        """.trimIndent()
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, achievementId)
                    statement.executeQuery().use { rs ->
                        if (rs.next()) {
                            DatabaseAchievement(
                                achievementId = rs.getString("achievement_id"),
                                achievementName = rs.getString("achievement_name"),
                                displayName = rs.getString("display_name"),
                                description = rs.getString("description"),
                                category = rs.getString("category"),
                                achievementType = rs.getString("achievement_type"),
                                targetValue = rs.getObject("target_value") as? Int,
                                requirementType = rs.getString("requirement_type"),
                                difficulty = rs.getString("difficulty"),
                                rarityPercentage = rs.getDouble("rarity_percentage"),
                                experienceReward = rs.getInt("experience_reward"),
                                coinReward = rs.getInt("coin_reward"),
                                specialRewards = rs.getString("special_rewards"),
                                iconMaterial = rs.getString("icon_material"),
                                iconCustomModelData = rs.getObject("icon_custom_model_data") as? Int,
                                displayOrder = rs.getInt("display_order"),
                                isHidden = rs.getBoolean("is_hidden"),
                                isSecret = rs.getBoolean("is_secret"),
                                isActive = rs.getBoolean("is_active"),
                                unlockMessage = rs.getString("unlock_message")
                            )
                        } else null
                    }
                }
            }
        } catch (e: SQLException) {
            null
        }
    }

    /**
     * Returns all achievement IDs unlocked for a player.
     */
    fun getPlayerAchievements(playerId: String): List<String> {
        val sql = """
            SELECT achievement_id FROM $PLAYER_ACHIEVEMENTS_TABLE
            WHERE player_id = ? AND is_completed = 1
        """.trimIndent()
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, playerId)
                    statement.executeQuery().use { rs ->
                        val result = mutableListOf<String>()
                        while (rs.next()) {
                            result.add(rs.getString("achievement_id"))
                        }
                        result
                    }
                }
            }
        } catch (e: SQLException) {
            emptyList()
        }
    }

    fun getPlayerAchievementsForAchievement(achievementId: String): List<String> {
        val sql = """
            SELECT player_id FROM $PLAYER_ACHIEVEMENTS_TABLE
            WHERE achievement_id = ? AND is_completed = 1
        """.trimIndent()
        return try {
            databaseManager.executeSync { connection ->
                connection.prepareStatement(sql).use { statement ->
                    statement.setString(1, achievementId)
                    statement.executeQuery().use { rs ->
                        val result = mutableListOf<String>()
                        while (rs.next()) {
                            result.add(rs.getString("player_id"))
                        }
                        result
                    }
                }
            }
        } catch (e: SQLException) {
            emptyList()
        }
    }

    /**
     * Achievement progress data class.
     */
    data class AchievementProgress(
        val currentProgress: Int,
        val targetProgress: Int,
        val progressPercentage: Double,
        val lastUpdated: LocalDateTime
    )
}