package org.Kloppie74.locoPillars.utils.achievements

import org.Kloppie74.locoPillars.database.DatabaseManager
import org.bukkit.entity.Player
import org.bukkit.Bukkit
import org.bukkit.Sound

/**
 * Achievements Manager - ONLY uses AchievementsTable for progress!
 */
class AchievementManager(
    val databaseManager: DatabaseManager
) {
    private val achievements: MutableMap<String, DatabaseAchievement> = mutableMapOf()

    fun registerAchievement(achievement: DatabaseAchievement) {
        achievements[achievement.achievementId] = achievement
    }

    fun getAchievement(id: String): DatabaseAchievement? = achievements[id]

    fun getAllAchievements(): Collection<DatabaseAchievement> = achievements.values

    /**
     * Get current progress for a player on a specific achievement.
     * Always from AchievementsTable progress!
     */
    fun getAchievementProgress(playerId: String, achievement: DatabaseAchievement): Int {
        val progress = databaseManager.achievementsTable.getAchievementProgress(playerId, achievement.achievementId)
        return progress?.currentProgress ?: 0
    }

    /**
     * Called when a player makes progress for a specific achievement.
     * Directly updates per achievement progress in AchievementsTable!
     * newValue: the new progress value for this achievement (not global stat)
     */
    fun onPlayerAchievementProgress(player: Player, achievementId: String, newValue: Int) {
        val achievement = achievements[achievementId] ?: return
        val playerId = player.uniqueId.toString()
        val target = achievement.targetValue ?: 1
        val unlocked = databaseManager.achievementsTable.hasPlayerUnlockedAchievement(playerId, achievement.achievementId)
        if (unlocked) return
        if (newValue >= target) {
            databaseManager.achievementsTable.unlockAchievement(playerId, achievement.achievementId)
            handleRewards(player, achievement)
            player.sendMessage(replaceAmount(achievement.unlockMessage ?: "You unlocked the achievement: ${achievement.displayName}!", target))
        } else {
            databaseManager.achievementsTable.updateProgress(playerId, achievement.achievementId, newValue, target)
        }
    }

    /**
     * Get all achievement IDs unlocked for a player.
     */
    fun getPlayerAchievements(playerId: String): List<String> {
        return databaseManager.achievementsTable.getPlayerAchievements(playerId)
    }

    fun replaceAmount(text: String, amount: Int?): String {
        return text.replace("%amount%", amount?.toString() ?: "1")
    }

    private fun handleRewards(player: Player, achievement: DatabaseAchievement) {
        val coins = achievement.coinReward
        val xp = achievement.experienceReward
        val playerId = player.uniqueId.toString()

        // Coins en XP daadwerkelijk toevoegen in StatsTable (database)
        try {
            if (coins > 0) {
                databaseManager.statsTable.addCoins(playerId, coins)
                player.sendMessage("§6+${coins} coins!")
            }
            if (xp > 0) {
                databaseManager.statsTable.addExperience(playerId, xp)
                player.sendMessage("§b+${xp} XP!")
            }
        } catch (ex: Exception) {
            Bukkit.getLogger().warning("Could not add coins/XP for achievement rewards: ${ex.message}")
        }

        // Commands uit YAML rewards
        val specialRewards = achievement.specialRewards
        if (!specialRewards.isNullOrBlank()) {
            specialRewards
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { cmdRaw ->
                    val cmd = cmdRaw.replace("%player%", player.name)
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                    } catch (ex: Exception) {
                        Bukkit.getLogger().warning("Failed custom reward command: $cmd (${ex.message})")
                    }
                }
        }

        // Sound en titel
        try {
            player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f)
        } catch (ex: Exception) {
            Bukkit.getLogger().warning("Could not play achievement sound: ${ex.message}")
        }
        val title = "§aAchievement Unlocked!"
        val subtitle = "§6${achievement.displayName}"
        player.sendTitle(title, subtitle, 10, 60, 10)
        player.sendMessage(achievement.unlockMessage ?: "§aAchievement unlocked: §6${achievement.displayName}")
    }
}