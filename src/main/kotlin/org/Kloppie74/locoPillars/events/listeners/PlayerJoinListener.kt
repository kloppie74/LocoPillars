package org.Kloppie74.locoPillars.events.listeners

import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.Bukkit
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.database.dao.*
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.database.models.VanishState
import java.time.LocalDateTime

/**
 * Handles player join events and ensures all database records exist.
 * Uses the new DAO pattern for clean database operations.
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
class PlayerJoinListener(private val plugin: LocoPillars) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val logger = plugin.logger

        try {
            debugLog("Processing join for ${player.name} (${player.uniqueId})")

            // === 1. CREATE OR UPDATE PLAYER RECORD ===
            var playerId = PlayerDAO.getPlayerId(player.name)
            if (playerId == null) {
                val playerRecord = PlayerDAO.create(
                    playerName = player.name,
                    displayName = player.displayName,
                    uuid = player.uniqueId.toString()
                )

                if (playerRecord != null) {
                    playerId = playerRecord.playerId
                    debugLog("Created new player record for ${player.name} (ID: $playerId)")
                } else {
                    logger.warning("[JoinListener] Failed to create player record for ${player.name}")
                    return
                }
            } else {
                debugLog("Player record exists for ${player.name} (ID: $playerId)")
            }

            // === 2. ENSURE STATS RECORD EXISTS ===
            val statsExist = StatsDAO.findById(playerId) != null
            if (!statsExist) {
                val statsCreated = StatsDAO.create(playerId)
                if (!statsCreated) {
                    logger.warning("[JoinListener] Failed to create stats record for $playerId")
                }
            }

            // === 3. UPDATE PLAYER ACTIVITY ===
            PlayerDAO.updateLastSeen(playerId, LocalDateTime.now())
            PlayerDAO.incrementSessionCount(playerId)

            // === 4. ENSURE VANISH STATE ===
            val existingVanishState = VanishDAO.load(player.uniqueId)
            if (existingVanishState == null) {
                val defaultState = VanishState(
                    playerId = playerId,
                    vanished = false,
                    hideMe = false,
                    hideOthers = false,
                    showOthers = mutableSetOf()
                )
                val vanishSaved = VanishDAO.save(player.uniqueId, defaultState)
                if (!vanishSaved) {
                    logger.warning("[JoinListener] Failed to create vanish state for ${player.uniqueId}")
                }
            }

            // === 5. INITIALIZE ACHIEVEMENT PROGRESS ===
            val achievements = AchievementDAO.getAllDefinitions()
            var achievementsInitialized = 0
            var achievementsFailed = 0

            achievements.forEach { achievement ->
                val hasUnlocked = AchievementDAO.hasPlayerUnlocked(playerId, achievement.achievementId)
                if (!hasUnlocked) {
                    val existingProgress = AchievementDAO.getProgress(playerId, achievement.achievementId)
                    if (existingProgress == null) {
                        val initialized = AchievementDAO.updateProgress(
                            playerId = playerId,
                            achievementId = achievement.achievementId,
                            currentProgress = 0,
                            targetProgress = achievement.targetValue ?: 1
                        )
                        if (initialized) achievementsInitialized++ else achievementsFailed++
                    }
                }
            }

            if (achievementsFailed > 0) {
                logger.warning("[JoinListener] Failed to initialize $achievementsFailed achievement(s) for $playerId")
            }

            // === 6. CACHE PLAYER ID FOR PERFORMANCE ===
            plugin.playerIdCache[player.uniqueId] = playerId

            debugLog("Completed join setup for ${player.name} (achievements: $achievementsInitialized initialized)")

            // === 7. UPDATE NOTIFICATION ===
            if (plugin.config.getBoolean("update-checker.notify-on-join", true)) {
                plugin.updateChecker.notifyPlayer(player)
            }

        } catch (ex: Exception) {
            logger.severe("[JoinListener] CRITICAL ERROR during join setup for ${player.name}: ${ex.message}")
            ex.printStackTrace()

            player.sendMessage(MSG.lang("player_join.setup_error"))
            player.sendMessage(MSG.lang("player_join.setup_error_contact"))
        }
    }

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) plugin.logger.info("[JoinListener] $message")
    }
}
