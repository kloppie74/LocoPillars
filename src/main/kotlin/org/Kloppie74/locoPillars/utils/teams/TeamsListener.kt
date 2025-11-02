package org.Kloppie74.locoPillars.utils.teams

import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.scheduler.BukkitRunnable
import org.Kloppie74.locoPillars.LocoPillars

/**
 * TeamsListener die TAB, scoreboard, teams en ProtocolLib altijd correct synchroniseert.
 * Scoreboard en tab worden altijd up-to-date gehouden, ook bij join/quit/worldchange/gamechange.
 */
class TeamsListener(
    private val plugin: LocoPillars
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player ?: return
        try {
            val playerUUID = player.uniqueId ?: return
            debugLog("Processing join for ${player.name}")
            object : BukkitRunnable() {
                override fun run() {
                    if (!player.isOnline) {
                        debugLog("Player ${player.name} went offline before delayed join handling")
                        return
                    }

                    // Initialize scoreboard first
                    plugin.teamManager.getPlayerScoreboard(player)

                    // Update tab team for all players
                    plugin.teamManager.createTabTeam(player,
                        plugin.tabManager.getPrefix(player),
                        plugin.tabManager.getSuffix(player)
                    )

                    // Update sidebar teams for this player
                    if (plugin.scoreboardManager.isEnabled()) {
                        plugin.scoreboardManager.updatePlayerScoreboard(player)
                    }

                    // TabManager formatting
                    if (plugin.tabManager.isEnabled()) {
                        plugin.tabManager.onPlayerJoin(player)
                        plugin.teamManager.updateAllTabTeams()
                    }

                    // ProtocolLib tab visibility
                    if (plugin.tabManager.displayPerGameInstance) {
                        if (plugin.isProtocolLibAvailable()) {
                            plugin.getProtocolLibTabManager()?.onPlayerJoin(player)
                            debugLog("Updated ProtocolLib tab visibility for ${player.name}")
                        } else if (plugin.tabVisibilityManager != null) {
                            plugin.tabVisibilityManager.updateTabVisibilityForAllPlayers()
                        }
                    }

                    debugLog("Completed join handling for ${player.name}")
                }
            }.runTaskLater(plugin, 10L)
        } catch (e: Exception) {
            plugin.logger.warning("[TeamsListener] Error handling player join for ${player.name}: ${e.message}")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player ?: return
        try {
            val playerUUID = player.uniqueId ?: return
            debugLog("Processing quit for ${player.name}")

            // Cleanup tab/scoreboard/teams
            if (plugin.tabManager.isEnabled()) {
                plugin.tabManager.onPlayerQuit(player)
            }
            if (plugin.scoreboardManager.isEnabled()) {
                plugin.scoreboardManager.onPlayerQuit(player)
            }
            plugin.teamManager.cleanupPlayer(player)

            // ProtocolLib tab visibility cleanup
            if (plugin.isProtocolLibAvailable()) {
                plugin.getProtocolLibTabManager()?.onPlayerQuit(player)
            }

            // Delayed updates for remaining players
            plugin.server.scheduler.runTaskLater(plugin, Runnable {
                try {
                    if (plugin.tabManager.isEnabled()) {
                        plugin.teamManager.updateAllTabTeams()
                    }
                    if (plugin.tabManager.displayPerGameInstance) {
                        if (plugin.isProtocolLibAvailable()) {
                            plugin.getProtocolLibTabManager()?.updateTabVisibilityForAllPlayers()
                        } else if (plugin.tabVisibilityManager != null) {
                            plugin.tabVisibilityManager.updateTabVisibilityForAllPlayers()
                        }
                    }
                    debugLog("Completed quit cleanup for ${player.name}")
                } catch (e: Exception) {
                    plugin.logger.warning("[TeamsListener] Error in delayed quit handling for ${player.name}: ${e.message}")
                }
            }, 5L)
        } catch (e: Exception) {
            plugin.logger.warning("[TeamsListener] Error handling player quit for ${player.name}: ${e.message}")
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player ?: return
        try {
            val playerUUID = player.uniqueId ?: return
            debugLog("Processing world change for ${player.name} (${event.from.name} -> ${player.world.name})")
            object : BukkitRunnable() {
                override fun run() {
                    if (!player.isOnline) {
                        debugLog("Player ${player.name} went offline before delayed world change handling")
                        return
                    }

                    // Update tab team for all players
                    plugin.teamManager.createTabTeam(player,
                        plugin.tabManager.getPrefix(player),
                        plugin.tabManager.getSuffix(player)
                    )

                    // Update sidebar teams for this player
                    if (plugin.scoreboardManager.isEnabled()) {
                        plugin.scoreboardManager.updatePlayerScoreboard(player)
                    }

                    // TabManager formatting
                    if (plugin.tabManager.isEnabled()) {
                        plugin.tabManager.updatePlayerTab(player)
                        if (plugin.tabManager.displayPerGameInstance) {
                            if (plugin.isProtocolLibAvailable()) {
                                plugin.getProtocolLibTabManager()?.updateTabVisibilityForAllPlayers()
                            } else if (plugin.tabVisibilityManager != null) {
                                plugin.tabVisibilityManager.updateTabVisibilityForAllPlayers()
                            }
                        }
                    }
                    debugLog("Handled world change for ${player.name}")
                }
            }.runTaskLater(plugin, 3L)
        } catch (e: Exception) {
            plugin.logger.warning("[TeamsListener] Error handling world change for ${player.name}: ${e.message}")
        }
    }

    fun onPlayerJoinGame(player: Player) {
        if (player == null) return
        try {
            debugLog("${player.name} joined a game instance")

            // Tab team for all players
            plugin.teamManager.createTabTeam(player,
                plugin.tabManager.getPrefix(player),
                plugin.tabManager.getSuffix(player)
            )

            // Sidebar teams for this player
            if (plugin.scoreboardManager.isEnabled()) {
                plugin.scoreboardManager.updatePlayerScoreboard(player)
            }

            // TabManager game join
            if (plugin.tabManager.isEnabled()) {
                plugin.tabManager.onPlayerJoinGame(player)
                plugin.teamManager.updateAllTabTeams()

                if (plugin.tabManager.displayPerGameInstance) {
                    if (plugin.isProtocolLibAvailable()) {
                        val gameInstance = plugin.gameManager.getPlayerInstance(player)
                        plugin.getProtocolLibTabManager()?.onPlayerJoinGame(player, gameInstance)
                        debugLog("Updated ProtocolLib tab visibility for ${player.name} joining game")
                    } else if (plugin.tabVisibilityManager != null) {
                        val gameInstance = plugin.gameManager.getPlayerInstance(player)
                        plugin.tabVisibilityManager.onPlayerJoinGame(player, gameInstance)
                    }
                }
            }

            // ProtocolLib tab update
            GlobalVisibilityTicker.queueUpdateWithPriority(player, "PROTOCOL_TAB_UPDATE", "joined_game")

        } catch (e: Exception) {
            plugin.logger.warning("[TeamsListener] Error handling player join game for ${player.name}: ${e.message}")
        }
    }

    fun onPlayerLeaveGame(player: Player) {
        if (player == null) return
        try {
            debugLog("${player.name} left a game instance")
            val gameInstance = plugin.gameManager.getPlayerInstance(player)

            // Tab team for all players
            plugin.teamManager.createTabTeam(player,
                plugin.tabManager.getPrefix(player),
                plugin.tabManager.getSuffix(player)
            )

            // Sidebar teams for this player
            if (plugin.scoreboardManager.isEnabled()) {
                plugin.scoreboardManager.updatePlayerScoreboard(player)
            }

            // TabManager game leave
            if (plugin.tabManager.isEnabled()) {
                plugin.tabManager.onPlayerLeaveGame(player)
                plugin.teamManager.updateAllTabTeams()

                if (plugin.tabManager.displayPerGameInstance) {
                    if (plugin.isProtocolLibAvailable()) {
                        plugin.getProtocolLibTabManager()?.onPlayerLeaveGame(player, gameInstance)
                        debugLog("Updated ProtocolLib tab visibility for ${player.name} leaving game")
                    } else if (plugin.tabVisibilityManager != null) {
                        plugin.tabVisibilityManager.onPlayerLeaveGame(player, gameInstance)
                    }
                }
            }

            // ProtocolLib tab update
            GlobalVisibilityTicker.queueUpdateWithPriority(player, "PROTOCOL_TAB_UPDATE", "left_game_to_lobby")

        } catch (e: Exception) {
            plugin.logger.warning("[TeamsListener] Error handling player leave game for ${player.name}: ${e.message}")
        }
    }

    private fun forceUpdateAllSystems() {
        try {
            debugLog("Force updating all systems")

            plugin.teamManager.forceRefreshAllTeams()
            if (plugin.tabManager.isEnabled()) {
                plugin.tabManager.forceUpdateAllPlayers()
                if (plugin.tabManager.displayPerGameInstance) {
                    if (plugin.isProtocolLibAvailable()) {
                        plugin.getProtocolLibTabManager()?.forceUpdateAllPlayers()
                        debugLog("Force updated ProtocolLib tab visibility")
                    } else if (plugin.tabVisibilityManager != null) {
                        plugin.tabVisibilityManager.forceUpdateAllPlayers()
                    }
                }
            }

            if (plugin.scoreboardManager.isEnabled()) {
                plugin.scoreboardManager.forceUpdateAllPlayers()
            }

            // Queue global update through GlobalVisibilityTicker
            GlobalVisibilityTicker.queueGlobalUpdate()

            debugLog("Completed force update of all systems")
        } catch (e: Exception) {
            plugin.logger.warning("[TeamsListener] Error in forceUpdateAllSystems: ${e.message}")
        }
    }

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[TeamsListener] $message")
        }
    }
}