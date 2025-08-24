package org.Kloppie74.locoPillars.utils.achievements

import org.Kloppie74.locoPillars.database.DatabaseManager
import org.bukkit.plugin.Plugin
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Loads all achievement .yml files from a folder, validates, registers, and saves to database.
 */
class AchievementLoader(
    private val plugin: Plugin,
    private val databaseManager: DatabaseManager
) {

    fun loadAchievements(folder: File, manager: AchievementManager) {
        if (!folder.exists() || !folder.isDirectory) {
            plugin.logger.severe("Achievement folder not found: ${folder.absolutePath}")
            return
        }

        val yaml = Yaml()
        var loaded = 0
        var skipped = 0

        folder.listFiles { file -> file.extension.equals("yml", true) }?.forEach { file ->
            try {
                val data: Map<String, Any> = yaml.load(file.inputStream())
                val requirements = (data["requirements"] as? Map<*, *>)?.get("target") as? Map<*, *>
                val triggerKey = requirements?.get("id")?.toString() ?: ""
                val amount = (requirements?.get("amount") as? Int) ?: 1

                val trigger = AchievementTrigger.fromYamlKey(triggerKey)
                if (trigger == null) {
                    plugin.logger.warning("[Achievements] Achievement '${data["id"]}' (${file.name}) has unknown trigger '$triggerKey'. Skipping!")
                    skipped++
                    return@forEach
                }

                val achievement = DatabaseAchievement(
                    achievementId = data["id"].toString(),
                    achievementName = data["name"].toString(),
                    displayName = data["display_name"].toString(),
                    description = data["description"].toString(),
                    category = data["category"].toString(),
                    achievementType = data["type"].toString().uppercase(),
                    targetValue = amount,
                    requirementType = trigger.name,
                    difficulty = data["difficulty"].toString().uppercase(),
                    rarityPercentage = (data["rarity"] as? Double) ?: 100.0,
                    experienceReward = (data["rewards"] as? Map<*, *>)?.get("experience") as? Int ?: 0,
                    coinReward = (data["rewards"] as? Map<*, *>)?.get("coins") as? Int ?: 0,
                    specialRewards = null,
                    iconMaterial = (data["icon"] as? Map<*, *>)?.get("material")?.toString() ?: "GOLD_INGOT",
                    iconCustomModelData = (data["icon"] as? Map<*, *>)?.get("custom_model_data") as? Int,
                    displayOrder = (data["display_order"] as? Int) ?: 0,
                    isHidden = data["hidden"] as? Boolean ?: false,
                    isSecret = data["secret"] as? Boolean ?: false,
                    isActive = data["active"] as? Boolean ?: true,
                    unlockMessage = data["unlock_message"]?.toString()
                )

                // Register to manager
                manager.registerAchievement(achievement)

                // Save to database
                val ok = databaseManager.achievementsTable.upsertAchievementDefinition(achievement)
                if (ok) {
                    plugin.logger.info("[Achievements] Loaded '${achievement.achievementId}' (${achievement.displayName})")
                    loaded++
                } else {
                    plugin.logger.warning("[Achievements] Failed to save '${achievement.achievementId}'!")
                }
            } catch (ex: Exception) {
                plugin.logger.severe("[Achievements] Error loading file ${file.name}: ${ex.message}")
                skipped++
            }
        }

        plugin.logger.info("[Achievements] $loaded achievements loaded, $skipped skipped (invalid trigger or error).")
    }
}