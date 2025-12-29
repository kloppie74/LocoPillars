package org.Kloppie74.locoPillars.utils.vanish

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import org.bukkit.boss.BossBar
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.teams.GlobalVisibilityTicker
import org.Kloppie74.locoPillars.database.dao.VanishDAO
import java.util.*

/**
 * Handles vanish/hideMe/hideOthers bossbar display.
 * All visibility updates are now handled by GlobalVisibilityTicker.
 *
 * CHANGELOG (v2.0):
 * - Replaced VanishManager.loadState() with VanishDAO.load()
 * - Replaced VanishManager.saveState() with VanishDAO.save()
 * - Replaced VanishManager.getState() with VanishDAO.load()
 * - Removed VanishManager.clearCache() (no longer needed with DAO)
 * - Added proper error handling for database operations
 * - Improved null-safety checks
 * - Better logging for errors
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
class VanishListener(private val plugin: LocoPillars) : Listener {

    private val bossBars: MutableMap<UUID, BossBar> = mutableMapOf()

    // ===== PLAYER EVENTS =====

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        try {
            // Load or create vanish state
            val existingState = VanishDAO.load(player.uniqueId)
            if (existingState == null) {
                // Create default vanish state for new player
                val defaultState = org.Kloppie74.locoPillars.database.models.VanishState(
                    playerId = player.uniqueId.toString(),
                    vanished = false,
                    hideMe = false,
                    hideOthers = false,
                    showOthers = mutableSetOf()
                )
                VanishDAO.create(player.uniqueId, defaultState)
                plugin.logger.info("[VanishListener] ✓ Created default vanish state for ${player.name}")
            } else {
                plugin.logger.info("[VanishListener] ✓ Loaded vanish state for ${player.name}")
            }
        } catch (e: Exception) {
            plugin.logger.severe("[VanishListener] ❌ Error loading vanish state for ${player.name}: ${e.message}")
            e.printStackTrace()
        }

        // Queue visibility update with slight delay to ensure all systems are ready
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            GlobalVisibilityTicker.queueUpdate(player)
            updateBossBar(player)
        }, 5L)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        try {
            // Load current state and save it (in case it changed during session)
            val state = VanishDAO.load(player.uniqueId)
            if (state != null) {
                VanishDAO.save(player.uniqueId, state)
                plugin.logger.info("[VanishListener] ✓ Saved vanish state for ${player.name}")
            }
        } catch (e: Exception) {
            plugin.logger.severe("[VanishListener] ❌ Error saving vanish state for ${player.name}: ${e.message}")
            e.printStackTrace()
        }

        // Remove bossbar
        removeBossBar(player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        // Queue update with small delay to let world change complete
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            GlobalVisibilityTicker.queueUpdate(event.player)
            updateBossBar(event.player)
        }, 2L)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        // Queue update after teleport completes
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            GlobalVisibilityTicker.queueUpdate(event.player)
            updateBossBar(event.player)
        }, 1L)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerGameModeChange(event: PlayerGameModeChangeEvent) {
        // Queue update after game mode change
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            GlobalVisibilityTicker.queueUpdate(event.player)
            updateBossBar(event.player)
        }, 1L)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        // Queue update after respawn
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            GlobalVisibilityTicker.queueUpdate(event.player)
            updateBossBar(event.player)
        }, 2L)
    }

    // ===== BOSSBAR MANAGEMENT =====

    /**
     * Displays a bossbar if the player is vanished, hidden, or hiding others.
     * Only one bossbar, all info in one line.
     */
    fun updateBossBar(player: Player) {
        try {
            // Load vanish state from database
            val state = VanishDAO.load(player.uniqueId)

            if (state == null) {
                // No vanish state found, remove any existing bossbar
                removeBossBar(player)
                return
            }

            val showBar = state.vanished || state.hideMe || state.hideOthers

            // Remove bar if not needed
            if (!showBar) {
                removeBossBar(player)
                return
            }

            val bossBar = bossBars.getOrPut(player.uniqueId) {
                Bukkit.createBossBar("LocoPillars Visibility", BarColor.BLUE, BarStyle.SOLID)
            }

            val textParts = mutableListOf<String>()
            var color = BarColor.GREEN
            var progress = 1.0

            if (state.vanished) {
                textParts.add("§c§lVanished")
                color = BarColor.RED
                progress = 1.0
            }
            if (state.hideMe) {
                textParts.add("§e§lHidden from others")
                if (!state.vanished) {
                    color = BarColor.YELLOW
                    progress = 0.7
                }
            }
            if (state.hideOthers) {
                textParts.add("§6§lHiding other players")
                if (!state.vanished && !state.hideMe) {
                    color = BarColor.BLUE
                    progress = 0.5
                }
            }

            val title = textParts.joinToString(" §8| ")
            bossBar.setTitle(title)
            bossBar.setColor(color)
            bossBar.setProgress(progress)

            bossBar.removePlayer(player)
            bossBar.addPlayer(player)

        } catch (e: Exception) {
            plugin.logger.warning("[VanishListener] ✗ Error updating bossbar for ${player.name}: ${e.message}")
            removeBossBar(player)
        }
    }

    /**
     * Removes bossbar for a player.
     */
    fun removeBossBar(player: Player) {
        try {
            bossBars[player.uniqueId]?.let { bossBar ->
                bossBar.removePlayer(player)
                bossBar.removeAll()
            }
            bossBars.remove(player.uniqueId)
        } catch (e: Exception) {
            plugin.logger.warning("[VanishListener] ✗ Error removing bossbar for ${player.name}: ${e.message}")
        }
    }

    /**
     * Utility: update bossbar for all online players.
     */
    fun updateAllBossBars() {
        Bukkit.getOnlinePlayers().forEach { player ->
            try {
                updateBossBar(player)
            } catch (e: Exception) {
                plugin.logger.warning("[VanishListener] ✗ Error updating bossbar for ${player.name}: ${e.message}")
            }
        }
    }

    /**
     * Cleanup: remove all bossbars (useful on plugin disable).
     */
    fun cleanup() {
        try {
            bossBars.values.forEach { bossBar ->
                bossBar.removeAll()
            }
            bossBars.clear()
            plugin.logger.info("[VanishListener] ✓ Cleaned up all bossbars")
        } catch (e: Exception) {
            plugin.logger.warning("[VanishListener] ✗ Error during cleanup: ${e.message}")
        }
    }
}