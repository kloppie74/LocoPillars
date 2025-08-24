package org.Kloppie74.locoPillars.utils.tab

import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.scheduler.BukkitRunnable
import org.Kloppie74.locoPillars.LocoPillars

class TabListener(private val plugin: LocoPillars, private val tabUtils: TabUtils) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Null safety check
        if (player == null) {
            plugin.logger.warning("[TabListener] Player is null in join event")
            return
        }

        try {
            // Get UUID safely
            val playerUUID = try {
                player.uniqueId
            } catch (e: Exception) {
                plugin.logger.warning("[TabListener] Error getting player UUID in join event: ${e.message}")
                return
            }

            if (playerUUID == null) {
                plugin.logger.warning("[TabListener] Player UUID is null in join event")
                return
            }

            // Update tab for the joining player after a short delay to ensure full initialization
            object : BukkitRunnable() {
                override fun run() {
                    try {
                        // Double-check player is still online
                        if (!player.isOnline) {
                            debugLog("Player ${player.name} went offline before delayed join handling")
                            return
                        }

                        // Let TabUtils handle the join logic
                        tabUtils.onPlayerJoin(player)

                        // CRITICAL: Update tabs for ALL other players since online count changed
                        // and new player affects visibility for everyone
                        if (tabUtils.isEnabled()) {
                            tabUtils.forceUpdateAllPlayers()

                            // Also force visibility update if per-game-instance is enabled
                            if (tabUtils.displayPerGameInstance) {
                                plugin.tabVisibilityManager.forceUpdateAllPlayers()
                            }
                        }

                        debugLog("Completed join handling for ${player.name} - ALL players updated")
                    } catch (e: Exception) {
                        plugin.logger.warning("[TabListener] Error in delayed join handling for ${player.name}: ${e.message}")
                    }
                }
            }.runTaskLater(plugin, 10L) // Wait 10 ticks for player to fully load

        } catch (e: Exception) {
            plugin.logger.warning("[TabListener] Error handling player join for ${player.name}: ${e.message}")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        // Null safety check
        if (player == null) {
            plugin.logger.warning("[TabListener] Player is null in quit event")
            return
        }

        try {
            // Get UUID safely before player becomes invalid
            val playerUUID = try {
                player.uniqueId
            } catch (e: Exception) {
                plugin.logger.warning("[TabListener] Error getting player UUID in quit event: ${e.message}")
                return
            }

            if (playerUUID == null) {
                plugin.logger.warning("[TabListener] Player UUID is null in quit event")
                return
            }

            // Let TabUtils handle the quit logic
            tabUtils.onPlayerQuit(player)

            // CRITICAL: Update ALL remaining players after a short delay
            // because online count changed and visibility may have changed
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                try {
                    if (tabUtils.isEnabled()) {
                        // Force complete update for all remaining players
                        tabUtils.forceUpdateAllPlayers()

                        // Also force visibility update if per-game-instance is enabled
                        if (tabUtils.displayPerGameInstance) {
                            plugin.tabVisibilityManager.forceUpdateAllPlayers()
                        }
                    }

                    debugLog("Completed quit handling for ${player.name} - ALL remaining players updated")
                } catch (e: Exception) {
                    plugin.logger.warning("[TabListener] Error in delayed quit handling for ${player.name}: ${e.message}")
                }
            }, 5L)

        } catch (e: Exception) {
            plugin.logger.warning("[TabListener] Error handling player quit for ${player.name}: ${e.message}")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player

        // Null safety check
        if (player == null) {
            plugin.logger.warning("[TabListener] Player is null in world change event")
            return
        }

        try {
            // Get UUID safely
            val playerUUID = try {
                player.uniqueId
            } catch (e: Exception) {
                plugin.logger.warning("[TabListener] Error getting player UUID in world change event: ${e.message}")
                return
            }

            if (playerUUID == null) {
                plugin.logger.warning("[TabListener] Player UUID is null in world change event")
                return
            }

            // Update tab when player changes worlds (could indicate game state change)
            object : BukkitRunnable() {
                override fun run() {
                    try {
                        // Double-check player is still online
                        if (!player.isOnline) {
                            debugLog("Player ${player.name} went offline before delayed world change handling")
                            return
                        }

                        // Force update this player's tab since world change might indicate game state change
                        tabUtils.updateTabForPlayer(player)

                        // Update visibility if per-game-instance is enabled
                        if (tabUtils.displayPerGameInstance) {
                            plugin.tabVisibilityManager.updateTabVisibilityForPlayer(player)
                        }

                        debugLog("Handled world change for ${player.name} (${event.from.name} -> ${player.world.name})")
                    } catch (e: Exception) {
                        plugin.logger.warning("[TabListener] Error in delayed world change handling for ${player.name}: ${e.message}")
                    }
                }
            }.runTaskLater(plugin, 3L) // Small delay to ensure world change is complete

        } catch (e: Exception) {
            plugin.logger.warning("[TabListener] Error handling world change for ${player.name}: ${e.message}")
        }
    }

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[TabListener] $message")
        }
    }
}