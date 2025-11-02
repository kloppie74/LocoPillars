package org.Kloppie74.locoPillars.events.listeners

import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.Bukkit
import org.Kloppie74.locoPillars.LocoPillars

class PlayerJoinListener(private val plugin: LocoPillars) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val db = plugin.databaseManager
        val logger = Bukkit.getLogger()

        try {
            db.executeSync { connection ->
                connection.autoCommit = false
                try {
                    // 1. Player aanmaken
                    var playerId = db.playersTable.getPlayerId(player.name)
                    if (playerId == null) {
                        playerId = db.playersTable.createPlayer(player.name, player.displayName, player.uniqueId.toString())
                        logger.info("[JoinListener] Created lp_players record for ${player.name}, id=$playerId")
                    } else {
                        logger.info("[JoinListener] lp_players record exists for ${player.name}, id=$playerId")
                    }

                    // 2. Stats: create if not exists
                    if (playerId != null) {
                        val statsCreated = db.statsTable.createPlayerStats(playerId)
                        if (statsCreated)
                            logger.info("[JoinListener] Ensured lp_stats record exists for $playerId")
                    }

                    // 3. Update last seen & increment session count
                    if (playerId != null) {
                        db.playersTable.updateLastSeen(playerId, java.time.LocalDateTime.now())
                        db.playersTable.incrementSessionCount(playerId)
                        logger.info("[JoinListener] Updated last seen and session count for $playerId")
                    }

                    // 4. Vanish table row
                    db.vanishTable.saveState(player.uniqueId, org.Kloppie74.locoPillars.database.tables.VanishState(
                        vanished = false,
                        hideMe = false,
                        hideOthers = false,
                        showOthers = mutableSetOf()
                    ))
                    logger.info("[JoinListener] Ensured vanish record for ${player.uniqueId}")

                    // 5. Achievements: progress row per achievement
                    val achievements = db.achievementsTable.getAllAchievements()
                    achievements.forEach { achievement ->
                        db.achievementsTable.updateProgress(
                            playerId ?: return@forEach,
                            achievement.achievementId,
                            0,
                            achievement.targetValue ?: 1
                        )
                    }
                    logger.info("[JoinListener] Ensured achievement progress records for $playerId")

                    // 6. Arena stats, etc. (indien nodig)

                    connection.commit()
                } catch (ex: Exception) {
                    connection.rollback()
                    logger.warning("[JoinListener] Failed during join DB setup for ${player.name}: ${ex.message}")
                } finally {
                    connection.autoCommit = true
                }
            }

            // PATCH: Cache playerId direct bij join voor snelle scoreboard updates
            plugin.playerIdCache[event.player.uniqueId] =
                plugin.databaseManager.playersTable.getPlayerId(event.player.name) ?: ""
        } catch (ex: Exception) {
            logger.warning("[JoinListener] Total failure during join DB setup for ${player.name}: ${ex.message}")
        }
    }
}