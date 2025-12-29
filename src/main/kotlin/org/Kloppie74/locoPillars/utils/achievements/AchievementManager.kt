package org.Kloppie74.locoPillars.utils.achievements

import org.Kloppie74.locoPillars.database.dao.AchievementDAO
import org.Kloppie74.locoPillars.database.dao.PlayerDAO
import org.Kloppie74.locoPillars.database.dao.StatsDAO
import org.bukkit.entity.Player
import org.bukkit.Bukkit

/**
 * Achievements Manager - Manages achievement progress and rewards.
 *
 * CHANGELOG (v2.1):
 * - Removed DatabaseManager dependency (uses DatabaseManager object directly)
 * - Replaced achievementsTable calls with AchievementDAO
 * - Replaced statsTable calls with StatsDAO
 * - Added proper error handling with try-catch blocks
 * - Improved logging with ✓/✗/❌ indicators
 * - Added null-safety checks for all database operations
 * - Better separation of concerns (DB vs game logic)
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
class AchievementManager {
    private val achievements: MutableMap<String, DatabaseAchievement> = mutableMapOf()
    private val logger = Bukkit.getLogger()

    fun registerAchievement(achievement: DatabaseAchievement) {
        achievements[achievement.achievementId] = achievement
    }

    fun getAchievement(id: String): DatabaseAchievement? = achievements[id]

    fun getAllAchievements(): Collection<DatabaseAchievement> = achievements.values

    /**
     * Get current progress for a player on a specific achievement.
     */
    fun getAchievementProgress(playerId: String, achievement: DatabaseAchievement): Int {
        return try {
            val progress = AchievementDAO.getProgress(playerId, achievement.achievementId)
            progress?.currentProgress ?: 0
        } catch (ex: Exception) {
            logger.severe("[AchievementManager] ❌ Error getting progress for achievement ${achievement.achievementId}: ${ex.message}")
            0
        }
    }

    /**
     * Called when a player makes progress for a specific achievement.
     * Directly updates per achievement progress in database!
     * newValue: the new progress value for this achievement (not global stat)
     */
    fun onPlayerAchievementProgress(player: Player, achievementId: String, newValue: Int) {
        val achievement = achievements[achievementId]
        if (achievement == null) {
            logger.warning("[AchievementManager] ✗ Achievement not found: $achievementId")
            return
        }

        try {
            val playerId = PlayerDAO.getPlayerId(player.name)
            if (playerId == null) {
                logger.warning("[AchievementManager] ✗ Could not find playerId for ${player.name}")
                return
            }

            val target = achievement.targetValue ?: 1

            // Check if already unlocked
            val unlocked = AchievementDAO.hasPlayerUnlocked(playerId, achievement.achievementId)
            if (unlocked) {
                return // Already unlocked, no need to update
            }

            // Check if target reached
            if (newValue >= target) {
                // Unlock achievement
                val unlockSuccess = AchievementDAO.unlockAchievement(playerId, achievement.achievementId)
                if (unlockSuccess) {
                    logger.info("[AchievementManager] ✓ ${player.name} unlocked achievement: ${achievement.displayName}")
                    handleRewards(player, achievement)
                    player.sendMessage(replaceAmount(achievement.unlockMessage ?: "You unlocked the achievement: ${achievement.displayName}!", target))
                } else {
                    logger.warning("[AchievementManager] ✗ Failed to unlock achievement ${achievement.achievementId} for ${player.name}")
                }
            } else {
                // Update progress
                val progressUpdated = AchievementDAO.updateProgress(playerId, achievement.achievementId, newValue, target)
                if (progressUpdated) {
                    logger.fine("[AchievementManager] ✓ Updated progress for ${player.name} on ${achievement.achievementId}: $newValue/$target")
                } else {
                    logger.warning("[AchievementManager] ✗ Failed to update progress for ${player.name} on ${achievement.achievementId}")
                }
            }

        } catch (ex: Exception) {
            logger.severe("[AchievementManager] ❌ Error updating achievement progress for ${player.name}: ${ex.message}")
            ex.printStackTrace()
        }
    }

    /**
     * Get all achievement IDs unlocked for a player.
     */
    fun getPlayerAchievements(playerId: String): List<String> {
        return try {
            AchievementDAO.getPlayerAchievements(playerId)
        } catch (ex: Exception) {
            logger.severe("[AchievementManager] ❌ Error getting player achievements for $playerId: ${ex.message}")
            emptyList()
        }
    }

    fun replaceAmount(text: String, amount: Int?): String {
        return text.replace("%amount%", amount?.toString() ?: "1")
    }

    /**
     * Handles rewarding the player for unlocking an achievement.
     */
    private fun handleRewards(player: Player, achievement: DatabaseAchievement) {
        val coins = achievement.coinReward
        val xp = achievement.experienceReward

        try {
            val playerId = PlayerDAO.getPlayerId(player.name)
            if (playerId == null) {
                logger.warning("[AchievementManager] ✗ Could not find playerId for ${player.name} when giving rewards")
                return
            }

            // Add coins
            if (coins > 0) {
                val coinsAdded = StatsDAO.addCoins(playerId, coins)
                if (coinsAdded) {
                    player.sendMessage("§6+${coins} coins!")
                    logger.info("[AchievementManager] ✓ Gave $coins coins to ${player.name}")
                } else {
                    logger.warning("[AchievementManager] ✗ Failed to add $coins coins to ${player.name}")
                }
            }

            // Add experience
            if (xp > 0) {
                val xpAdded = StatsDAO.addExperience(playerId, xp)
                if (xpAdded) {
                    player.sendMessage("§b+${xp} XP!")
                    logger.info("[AchievementManager] ✓ Gave $xp XP to ${player.name}")
                } else {
                    logger.warning("[AchievementManager] ✗ Failed to add $xp XP to ${player.name}")
                }
            }

        } catch (ex: Exception) {
            logger.severe("[AchievementManager] ❌ Error adding coins/XP for ${player.name}: ${ex.message}")
            ex.printStackTrace()
        }

        // Execute special reward commands
        val specialRewards = achievement.specialRewards
        if (!specialRewards.isNullOrBlank()) {
            try {
                specialRewards
                    .split(";")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { cmdRaw ->
                        val cmd = cmdRaw.replace("%player%", player.name)
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                            logger.info("[AchievementManager] ✓ Executed reward command for ${player.name}: $cmd")
                        } catch (cmdEx: Exception) {
                            logger.warning("[AchievementManager] ✗ Failed to execute reward command: $cmd (${cmdEx.message})")
                        }
                    }
            } catch (ex: Exception) {
                logger.severe("[AchievementManager] ❌ Error processing special rewards: ${ex.message}")
            }
        }

        // Play sound and show title
        try {
            player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        } catch (ex: Exception) {
            logger.warning("[AchievementManager] ✗ Could not play achievement sound: ${ex.message}")
        }

        try {
            val title = "§aAchievement Unlocked!"
            val subtitle = "§6${achievement.displayName}"
            player.sendTitle(title, subtitle, 10, 60, 10)
            player.sendMessage(achievement.unlockMessage ?: "§aAchievement unlocked: §6${achievement.displayName}")
        } catch (ex: Exception) {
            logger.warning("[AchievementManager] ✗ Could not send achievement title: ${ex.message}")
        }
    }
}