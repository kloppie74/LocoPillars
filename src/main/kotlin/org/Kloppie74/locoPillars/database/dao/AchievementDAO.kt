package org.Kloppie74.locoPillars.database.dao

import org.Kloppie74.locoPillars.database.DatabaseManager
import org.Kloppie74.locoPillars.database.models.*
import org.Kloppie74.locoPillars.utils.achievements.DatabaseAchievement
import java.sql.ResultSet
import java.sql.SQLException
import java.time.LocalDateTime

object AchievementDAO {

    fun upsertDefinition(achievement: DatabaseAchievement): Boolean {
        val sql = when (DatabaseManager.getDatabaseType().lowercase()) {
            "mysql", "mariadb" -> """
                INSERT INTO lp_achievement_definitions (
                    achievement_id, achievement_name, display_name, description, category,
                    achievement_type, target_value, requirement_type, difficulty,
                    rarity_percentage, experience_reward, coin_reward, special_rewards,
                    icon_material, icon_custom_model_data, display_order,
                    is_hidden, is_secret, is_active, unlock_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    achievement_name = VALUES(achievement_name),
                    display_name = VALUES(display_name),
                    description = VALUES(description),
                    updated_at = CURRENT_TIMESTAMP
            """
            "h2" -> """
                MERGE INTO lp_achievement_definitions (
                    achievement_id, achievement_name, display_name, description, category,
                    achievement_type, target_value, requirement_type, difficulty,
                    rarity_percentage, experience_reward, coin_reward, special_rewards,
                    icon_material, icon_custom_model_data, display_order,
                    is_hidden, is_secret, is_active, unlock_message
                ) KEY(achievement_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """
            else -> throw IllegalArgumentException("Unsupported database type: ${DatabaseManager.getDatabaseType()}")
        }

        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, achievement.achievementId)
                    ps.setString(2, achievement.achievementName)
                    ps.setString(3, achievement.displayName)
                    ps.setString(4, achievement.description)
                    ps.setString(5, achievement.category)
                    ps.setString(6, achievement.achievementType)
                    if (achievement.targetValue != null) ps.setInt(7, achievement.targetValue)
                    else ps.setNull(7, java.sql.Types.INTEGER)
                    ps.setString(8, achievement.requirementType)
                    ps.setString(9, achievement.difficulty)
                    ps.setDouble(10, achievement.rarityPercentage)
                    ps.setInt(11, achievement.experienceReward)
                    ps.setInt(12, achievement.coinReward)
                    ps.setString(13, achievement.specialRewards)
                    ps.setString(14, achievement.iconMaterial)
                    if (achievement.iconCustomModelData != null) ps.setInt(15, achievement.iconCustomModelData)
                    else ps.setNull(15, java.sql.Types.INTEGER)
                    ps.setInt(16, achievement.displayOrder)
                    ps.setBoolean(17, achievement.isHidden)
                    ps.setBoolean(18, achievement.isSecret)
                    ps.setBoolean(19, achievement.isActive)
                    ps.setString(20, achievement.unlockMessage)

                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to upsert achievement ${achievement.achievementId}: ${e.message}", e)
        }
    }

    fun unlockAchievement(playerId: String, achievementId: String): Boolean {
        val definition = findDefinitionById(achievementId)
        val target = definition?.targetValue ?: 1

        val sql1 = when (DatabaseManager.getDatabaseType().lowercase()) {
            "mysql", "mariadb" -> "INSERT IGNORE INTO lp_player_achievements (player_id, achievement_id) VALUES (?, ?)"
            "h2" -> "MERGE INTO lp_player_achievements (player_id, achievement_id) KEY(player_id, achievement_id) VALUES (?, ?)"
            else -> throw IllegalArgumentException("Unsupported DB")
        }

        val sql2 = when (DatabaseManager.getDatabaseType().lowercase()) {
            "mysql", "mariadb" -> """
                INSERT INTO lp_achievement_progress (player_id, achievement_id, current_progress, target_progress, progress_percentage, last_updated)
                VALUES (?, ?, ?, ?, 100.0, ?)
                ON DUPLICATE KEY UPDATE current_progress = VALUES(current_progress), progress_percentage = 100.0
            """
            "h2" -> """
                MERGE INTO lp_achievement_progress (player_id, achievement_id, current_progress, target_progress, progress_percentage, last_updated)
                KEY(player_id, achievement_id)
                VALUES (?, ?, ?, ?, 100.0, ?)
            """
            else -> throw IllegalArgumentException("Unsupported DB")
        }

        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql1).use { ps ->
                    ps.setString(1, playerId)
                    ps.setString(2, achievementId)
                    ps.executeUpdate()
                }
                conn.prepareStatement(sql2).use { ps ->
                    ps.setString(1, playerId)
                    ps.setString(2, achievementId)
                    ps.setInt(3, target)
                    ps.setInt(4, target)
                    ps.setObject(5, LocalDateTime.now())
                    ps.executeUpdate()
                }
                conn.commit()
                true
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to unlock achievement $achievementId for player $playerId: ${e.message}", e)
        }
    }

    fun updateProgress(playerId: String, achievementId: String, currentProgress: Int, targetProgress: Int): Boolean {
        val progressPercentage = if (targetProgress > 0)
            (currentProgress.toDouble() / targetProgress * 100.0).coerceAtMost(100.0)
        else 100.0

        val sql = when (DatabaseManager.getDatabaseType().lowercase()) {
            "mysql", "mariadb" -> """
                INSERT INTO lp_achievement_progress (player_id, achievement_id, current_progress, target_progress, progress_percentage, last_updated)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    current_progress = VALUES(current_progress),
                    target_progress = VALUES(target_progress),
                    progress_percentage = VALUES(progress_percentage),
                    last_updated = VALUES(last_updated)
            """
            "h2" -> """
                MERGE INTO lp_achievement_progress (player_id, achievement_id, current_progress, target_progress, progress_percentage, last_updated)
                KEY(player_id, achievement_id)
                VALUES (?, ?, ?, ?, ?, ?)
            """
            else -> throw IllegalArgumentException("Unsupported DB")
        }

        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.setString(2, achievementId)
                    ps.setInt(3, currentProgress)
                    ps.setInt(4, targetProgress)
                    ps.setDouble(5, progressPercentage)
                    ps.setObject(6, LocalDateTime.now())
                    val updated = ps.executeUpdate()
                    conn.commit()
                    updated > 0
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to update progress for achievement $achievementId: ${e.message}", e)
        }
    }

    fun getProgress(playerId: String, achievementId: String): AchievementProgress? {
        val sql = "SELECT * FROM lp_achievement_progress WHERE player_id = ? AND achievement_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.setString(2, achievementId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rowToProgress(rs) else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to get progress for achievement $achievementId: ${e.message}", e)
        }
    }

    fun hasPlayerUnlocked(playerId: String, achievementId: String): Boolean {
        val sql = "SELECT 1 FROM lp_player_achievements WHERE player_id = ? AND achievement_id = ? AND is_completed = 1"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.setString(2, achievementId)
                    ps.executeQuery().use { rs ->
                        rs.next()
                    }
                }
            }
        } catch (e: SQLException) {
            false
        }
    }

    fun getAllDefinitions(): List<DatabaseAchievement> {
        val sql = "SELECT * FROM lp_achievement_definitions WHERE is_active = 1 ORDER BY display_order, achievement_name"
        return try {
            val list = mutableListOf<DatabaseAchievement>()
            DatabaseManager.getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(sql).use { rs ->
                        while (rs.next()) {
                            list.add(rowToDatabaseAchievement(rs))
                        }
                    }
                }
            }
            list
        } catch (e: SQLException) {
            throw SQLException("Failed to get all achievement definitions: ${e.message}", e)
        }
    }

    fun findDefinitionById(achievementId: String): DatabaseAchievement? {
        val sql = "SELECT * FROM lp_achievement_definitions WHERE achievement_id = ?"
        return try {
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, achievementId)
                    ps.executeQuery().use { rs ->
                        if (rs.next()) rowToDatabaseAchievement(rs) else null
                    }
                }
            }
        } catch (e: SQLException) {
            throw SQLException("Failed to find achievement definition $achievementId: ${e.message}", e)
        }
    }

    fun getPlayerAchievements(playerId: String): List<String> {
        val sql = "SELECT achievement_id FROM lp_player_achievements WHERE player_id = ? AND is_completed = 1"
        return try {
            val list = mutableListOf<String>()
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, playerId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            list.add(rs.getString("achievement_id"))
                        }
                    }
                }
            }
            list
        } catch (e: SQLException) {
            throw SQLException("Failed to get player achievements for $playerId: ${e.message}", e)
        }
    }

    fun getPlayerAchievementsForAchievement(achievementId: String): List<String> {
        val sql = "SELECT player_id FROM lp_player_achievements WHERE achievement_id = ? AND is_completed = 1"
        return try {
            val result = mutableListOf<String>()
            DatabaseManager.getConnection().use { conn ->
                conn.prepareStatement(sql).use { ps ->
                    ps.setString(1, achievementId)
                    ps.executeQuery().use { rs ->
                        while (rs.next()) {
                            result.add(rs.getString("player_id"))
                        }
                    }
                }
            }
            result
        } catch (e: SQLException) {
            throw SQLException("Failed to get players for achievement $achievementId: ${e.message}", e)
        }
    }

    private fun rowToProgress(rs: ResultSet): AchievementProgress {
        return AchievementProgress(
            playerId = rs.getString("player_id"),
            achievementId = rs.getString("achievement_id"),
            currentProgress = rs.getInt("current_progress"),
            targetProgress = rs.getInt("target_progress"),
            progressPercentage = rs.getDouble("progress_percentage"),
            lastUpdated = rs.getObject("last_updated", LocalDateTime::class.java)
        )
    }

    private fun rowToDatabaseAchievement(rs: ResultSet): DatabaseAchievement {
        return DatabaseAchievement(
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
    }
}