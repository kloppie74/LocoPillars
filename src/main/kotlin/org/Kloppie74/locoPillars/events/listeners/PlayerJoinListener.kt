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
        val logger = Bukkit.getLogger()

        try {
            logger.info("[JoinListener] Processing join for ${player.name} (${player.uniqueId})")

            // === 1. CREATE OR UPDATE PLAYER RECORD ===
            var playerId = PlayerDAO.getPlayerId(player.name)
            if (playerId == null) {
                // Player doesn't exist, create new record
                val playerRecord = PlayerDAO.create(
                    playerName = player.name,
                    displayName = player.displayName,
                    uuid = player.uniqueId.toString()
                )

                if (playerRecord != null) {
                    playerId = playerRecord.playerId
                    logger.info("[JoinListener] ✓ Created new player record for ${player.name} (ID: $playerId)")
                } else {
                    logger.warning("[JoinListener] ✗ Failed to create player record for ${player.name}")
                    return
                }
            } else {
                logger.info("[JoinListener] ✓ Player record exists for ${player.name} (ID: $playerId)")
            }

            // === 2. ENSURE STATS RECORD EXISTS ===
            val statsExist = StatsDAO.findById(playerId) != null
            if (!statsExist) {
                val statsCreated = StatsDAO.create(playerId)
                if (statsCreated) {
                    logger.info("[JoinListener] ✓ Created stats record for $playerId")
                } else {
                    logger.warning("[JoinListener] ✗ Failed to create stats record for $playerId")
                }
            } else {
                logger.info("[JoinListener] ✓ Stats record exists for $playerId")
            }

            // === 3. UPDATE PLAYER ACTIVITY ===
            val lastSeenUpdated = PlayerDAO.updateLastSeen(playerId, LocalDateTime.now())
            val sessionIncremented = PlayerDAO.incrementSessionCount(playerId)

            if (lastSeenUpdated && sessionIncremented) {
                logger.info("[JoinListener] ✓ Updated last seen and session count for $playerId")
            } else {
                logger.warning("[JoinListener] ✗ Failed to update activity for $playerId")
            }

            // === 4. ENSURE VANISH STATE ===
            val existingVanishState = VanishDAO.load(player.uniqueId)
            if (existingVanishState == null) {
                // Create default vanish state
                val defaultState = VanishState(
                    playerId = playerId,
                    vanished = false,
                    hideMe = false,
                    hideOthers = false,
                    showOthers = mutableSetOf()
                )

                val vanishSaved = VanishDAO.save(player.uniqueId, defaultState)
                if (vanishSaved) {
                    logger.info("[JoinListener] ✓ Created vanish state for ${player.uniqueId}")
                } else {
                    logger.warning("[JoinListener] ✗ Failed to create vanish state for ${player.uniqueId}")
                }
            } else {
                logger.info("[JoinListener] ✓ Vanish state exists for ${player.uniqueId}")
            }

            // === 5. INITIALIZE ACHIEVEMENT PROGRESS ===
            val achievements = AchievementDAO.getAllDefinitions()
            var achievementsInitialized = 0
            var achievementsFailed = 0

            achievements.forEach { achievement ->
                // Only initialize if player hasn't unlocked it yet
                val hasUnlocked = AchievementDAO.hasPlayerUnlocked(playerId, achievement.achievementId)
                if (!hasUnlocked) {
                    val existingProgress = AchievementDAO.getProgress(playerId, achievement.achievementId)
                    if (existingProgress == null) {
                        // Initialize progress to 0
                        val initialized = AchievementDAO.updateProgress(
                            playerId = playerId,
                            achievementId = achievement.achievementId,
                            currentProgress = 0,
                            targetProgress = achievement.targetValue ?: 1
                        )

                        if (initialized) {
                            achievementsInitialized++
                        } else {
                            achievementsFailed++
                        }
                    }
                }
            }

            if (achievementsInitialized > 0) {
                logger.info("[JoinListener] ✓ Initialized $achievementsInitialized achievement progress records for $playerId")
            }
            if (achievementsFailed > 0) {
                logger.warning("[JoinListener] ✗ Failed to initialize $achievementsFailed achievement progress records for $playerId")
            }

            // === 6. CACHE PLAYER ID FOR PERFORMANCE ===
            // This allows quick lookups without database queries
            plugin.playerIdCache[player.uniqueId] = playerId
            logger.info("[JoinListener] ✓ Cached playerId for ${player.uniqueId}")

            // === 7. OPTIONAL: LOAD PLAYER STATS FOR SCOREBOARD ===
            // Pre-load stats into memory if you have a stats cache system
            val playerStats = StatsDAO.findById(playerId)
            if (playerStats != null) {
                logger.info("[JoinListener] ✓ Loaded stats for ${player.name}: Level ${playerStats.level}, ${playerStats.experience} XP")
            }

            logger.info("[JoinListener] ✅ Successfully completed join setup for ${player.name}")

        } catch (ex: Exception) {
            logger.severe("[JoinListener] ❌ CRITICAL ERROR during join setup for ${player.name}")
            logger.severe("[JoinListener] Error: ${ex.message}")
            ex.printStackTrace()

            player.sendMessage(MSG.lang("player_join.setup_error"))
            player.sendMessage(MSG.lang("player_join.setup_error_contact"))
        }
    }
}