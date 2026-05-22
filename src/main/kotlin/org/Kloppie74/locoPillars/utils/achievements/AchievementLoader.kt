package org.Kloppie74.locoPillars.utils.achievements

import org.Kloppie74.locoPillars.database.dao.AchievementDAO
import org.bukkit.plugin.Plugin
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * Loads all achievement .yml files from a folder, validates, registers, and saves to database.
 *
 * CHANGELOG (v2.1):
 * - Removed DatabaseManager dependency (uses DatabaseManager object directly)
 * - Replaced achievementsTable calls with AchievementDAO
 * - Added proper error handling for database operations
 * - Improved logging with ✓/✗/❌ indicators
 * - Better validation and error messages
 * - Added summary statistics at end of loading
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
class AchievementLoader(
    private val plugin: Plugin
) {

    private val defaultAchievements = listOf(
        "first_blood", "apprentice_warrior", "warrior", "gladiator", "executioner",
        "first_win", "rising_star", "champion", "legend", "win_master",
        "miner", "demolisher", "master_demolisher",
        "builder_basics", "architect", "builder_supreme",
        "explorer", "marathon_runner",
        "veteran", "dedicated",
        "double_down", "triple_threat", "quad_domination",
        "comeback_kid", "on_fire", "win_streak_master", "perfect_game_master"
    )

    private fun extractDefaults(folder: File) {
        folder.mkdirs()
        for (name in defaultAchievements) {
            val target = File(folder, "$name.yml")
            if (!target.exists()) {
                val stream = plugin.getResource("Achievements/$name.yml") ?: continue
                try {
                    target.outputStream().use { stream.copyTo(it) }
                } catch (e: Exception) {
                    plugin.logger.warning("[AchievementLoader] Could not extract $name.yml: ${e.message}")
                }
            }
        }
    }

    /**
     * Loads achievements from YAML files in the specified folder.
     */
    fun loadAchievements(folder: File, manager: AchievementManager) {
        extractDefaults(folder)
        if (!folder.exists() || !folder.isDirectory) {
            plugin.logger.severe("[AchievementLoader] ❌ Achievement folder not found: ${folder.absolutePath}")
            return
        }

        plugin.logger.info("[AchievementLoader] Loading achievements from: ${folder.absolutePath}")

        val yaml = Yaml()
        var loaded = 0
        var skipped = 0
        var failed = 0

        val files = folder.listFiles { file -> file.extension.equals("yml", true) }
        if (files.isNullOrEmpty()) {
            plugin.logger.warning("[AchievementLoader] ✗ No achievement files found in folder")
            return
        }

        plugin.logger.info("[AchievementLoader] Found ${files.size} achievement file(s)")

        files.forEach { file ->
            try {
                val data: Map<String, Any> = yaml.load(file.inputStream())

                // Validate required fields
                val achievementId = data["id"]?.toString()
                if (achievementId.isNullOrBlank()) {
                    plugin.logger.warning("[AchievementLoader] ✗ File ${file.name}: Missing 'id' field, skipping")
                    skipped++
                    return@forEach
                }

                // Get trigger from requirements
                val requirements = (data["requirements"] as? Map<*, *>)?.get("target") as? Map<*, *>
                val triggerKey = requirements?.get("id")?.toString()

                if (triggerKey.isNullOrBlank()) {
                    plugin.logger.warning("[AchievementLoader] ✗ Achievement '$achievementId' (${file.name}): Missing trigger id, skipping")
                    skipped++
                    return@forEach
                }

                val trigger = AchievementTrigger.fromYamlKey(triggerKey)
                if (trigger == null) {
                    plugin.logger.warning("[AchievementLoader] ✗ Achievement '$achievementId' (${file.name}): Unknown trigger '$triggerKey', skipping")
                    skipped++
                    return@forEach
                }

                val amount = (requirements["amount"] as? Int) ?: 1

                // Create achievement object
                val achievement = DatabaseAchievement(
                    achievementId = achievementId,
                    achievementName = data["name"]?.toString() ?: achievementId,
                    displayName = data["display_name"]?.toString() ?: achievementId,
                    description = data["description"]?.toString() ?: "",
                    category = data["category"]?.toString() ?: "GENERAL",
                    achievementType = data["type"]?.toString()?.uppercase() ?: "PROGRESS",
                    targetValue = amount,
                    requirementType = trigger.name,
                    difficulty = data["difficulty"]?.toString()?.uppercase() ?: "EASY",
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

                // Register to in-memory manager
                manager.registerAchievement(achievement)

                // Save to database
                try {
                    val saved = AchievementDAO.upsertDefinition(achievement)
                    if (saved) {
                        plugin.logger.info("[AchievementLoader] ✓ Loaded '$achievementId' (${achievement.displayName}) - Trigger: ${trigger.name}, Target: $amount")
                        loaded++
                    } else {
                        plugin.logger.warning("[AchievementLoader] ✗ Failed to save '$achievementId' to database")
                        failed++
                    }
                } catch (dbEx: Exception) {
                    plugin.logger.severe("[AchievementLoader] ❌ Database error saving '$achievementId': ${dbEx.message}")
                    dbEx.printStackTrace()
                    failed++
                }

            } catch (ex: Exception) {
                plugin.logger.severe("[AchievementLoader] ❌ Error loading file ${file.name}: ${ex.message}")
                ex.printStackTrace()
                failed++
            }
        }

        // Summary
        plugin.logger.info("[AchievementLoader] ========================================")
        plugin.logger.info("[AchievementLoader] Achievement Loading Summary:")
        plugin.logger.info("[AchievementLoader]   ✓ Loaded: $loaded")
        plugin.logger.info("[AchievementLoader]   ✗ Skipped (invalid): $skipped")
        plugin.logger.info("[AchievementLoader]   ❌ Failed (errors): $failed")
        plugin.logger.info("[AchievementLoader]   Total files: ${files.size}")
        plugin.logger.info("[AchievementLoader] ========================================")

        if (loaded == 0 && files.isNotEmpty()) {
            plugin.logger.warning("[AchievementLoader] ⚠️ WARNING: No achievements were loaded successfully!")
        }
    }
}