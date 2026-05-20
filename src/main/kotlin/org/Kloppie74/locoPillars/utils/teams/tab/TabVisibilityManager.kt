package org.Kloppie74.locoPillars.utils.teams.tab

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.database.dao.VanishDAO
import org.Kloppie74.locoPillars.game.instance.GameInstance
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * TabVisibilityManager die filtering per gameinstance/lobby regelt
 */
class TabVisibilityManager(private val plugin: LocoPillars) {

    private val playerGameStates = ConcurrentHashMap<UUID, UUID?>()
    private val isUpdating = ConcurrentHashMap<UUID, Boolean>()
    private val lastVisibilityStates = ConcurrentHashMap<Pair<UUID, UUID>, Boolean>()

    fun updateTabVisibilityForPlayer(player: Player?) {
        if (player == null || !player.isOnline) {
            debugLog("Skipping update for null/offline player")
            return
        }
        val playerUUID = try {
            player.uniqueId ?: return
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error getting player UUID for ${player.name}: ${e.message}")
            return
        }
        if (!isPerGameInstanceEnabled()) {
            debugLog("Per-game-instance disabled for ${player.name}, showing all players")
            showAllPlayersToPlayer(player)
            return
        }
        if (isUpdating.putIfAbsent(playerUUID, true) != null) {
            debugLog("Update already in progress for ${player.name}")
            return
        }
        try {
            val gameManager = plugin.gameManager ?: return
            val currentGameInstance = try {
                gameManager.getPlayerInstance(player)
            } catch (e: Exception) {
                plugin.logger.warning("[TabVisibility] Error getting player instance for ${player.name}: ${e.message}")
                return
            }
            val currentGameId = currentGameInstance?.id
            val lastKnownGameId = playerGameStates[playerUUID]
            if (currentGameId != null) {
                playerGameStates[playerUUID] = currentGameId
            } else {
                playerGameStates.remove(playerUUID)
            }
            debugLog("Updating tab visibility for ${player.name} - Game: ${currentGameId ?: "LOBBY"}")
            if (currentGameInstance == null) {
                applyLobbyTabVisibility(player)
            } else {
                applyGameTabVisibility(player, currentGameInstance)
            }

        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error updating visibility for ${player.name}: ${e.message}")
        } finally {
            isUpdating.remove(playerUUID)
        }
    }

    private fun showAllPlayersToPlayer(player: Player) {
        try {
            Bukkit.getOnlinePlayers().forEach { otherPlayer ->
                if (otherPlayer != player && otherPlayer.isOnline) {
                    if (!shouldDeferToOtherSystems(player, otherPlayer)) {
                        updatePlayerVisibility(player, otherPlayer, true, "show_all")
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error in showAllPlayersToPlayer for ${player.name}: ${e.message}")
        }
    }

    private fun applyLobbyTabVisibility(lobbyPlayer: Player) {
        try {
            val onlinePlayers = Bukkit.getOnlinePlayers()?.toList() ?: return
            debugLog("Applying lobby tab visibility for ${lobbyPlayer.name} (${onlinePlayers.size} online)")

            onlinePlayers.forEach { otherPlayer ->
                if (otherPlayer == null || otherPlayer == lobbyPlayer || !otherPlayer.isOnline) return@forEach

                try {
                    if (shouldDeferToOtherSystems(lobbyPlayer, otherPlayer)) {
                        debugLog("Deferring ${lobbyPlayer.name} <-> ${otherPlayer.name} to other visibility systems")
                        return@forEach
                    }
                    val shouldSee = shouldLobbyPlayerSeeOtherInTab(lobbyPlayer, otherPlayer)
                    val otherShouldSee = shouldOtherPlayerSeeLobbyPlayerInTab(otherPlayer, lobbyPlayer)
                    updatePlayerVisibility(lobbyPlayer, otherPlayer, shouldSee, "lobby->other")
                    updatePlayerVisibility(otherPlayer, lobbyPlayer, otherShouldSee, "other->lobby")
                    debugLog("Tab visibility: ${lobbyPlayer.name} -> ${otherPlayer.name}: $shouldSee, ${otherPlayer.name} -> ${lobbyPlayer.name}: $otherShouldSee")
                } catch (e: Exception) {
                    plugin.logger.warning("[TabVisibility] Error processing ${otherPlayer.name} for lobby player ${lobbyPlayer.name}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error in applyLobbyTabVisibility for ${lobbyPlayer.name}: ${e.message}")
        }
    }

    private fun applyGameTabVisibility(gamePlayer: Player, gameInstance: GameInstance) {
        try {
            val onlinePlayers = Bukkit.getOnlinePlayers()?.toList() ?: return
            val gamePlayerIds = gameInstance.players?.toSet() ?: emptySet()
            debugLog("Applying game tab visibility for ${gamePlayer.name} in game ${gameInstance.id} (${gamePlayerIds.size} game players)")
            onlinePlayers.forEach { otherPlayer ->
                if (otherPlayer == null || otherPlayer == gamePlayer || !otherPlayer.isOnline) return@forEach

                try {
                    if (shouldDeferToOtherSystems(gamePlayer, otherPlayer)) {
                        debugLog("Deferring ${gamePlayer.name} <-> ${otherPlayer.name} to other visibility systems")
                        return@forEach
                    }
                    val shouldSee = shouldGamePlayerSeeOtherInTab(gamePlayer, otherPlayer, gamePlayerIds)
                    val otherShouldSee = shouldOtherPlayerSeeGamePlayerInTab(otherPlayer, gamePlayer, gamePlayerIds)
                    updatePlayerVisibility(gamePlayer, otherPlayer, shouldSee, "game->other")
                    updatePlayerVisibility(otherPlayer, gamePlayer, otherShouldSee, "other->game")
                    debugLog("Tab visibility: ${gamePlayer.name} -> ${otherPlayer.name}: $shouldSee, ${otherPlayer.name} -> ${gamePlayer.name}: $otherShouldSee")
                } catch (e: Exception) {
                    plugin.logger.warning("[TabVisibility] Error processing ${otherPlayer.name} for game player ${gamePlayer.name}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error in applyGameTabVisibility for ${gamePlayer.name}: ${e.message}")
        }
    }

    private fun shouldDeferToOtherSystems(viewer: Player, target: Player): Boolean {
        try {
            // Only defer to vanish system when the vanish state has active flags.
            // Missing DB entry is not a reason to skip game-instance filtering.
            val viewerVanishState = try {
                VanishDAO.load(viewer.uniqueId)
            } catch (e: Exception) {
                debugLog("Error loading vanish state for ${viewer.name}: ${e.message}")
                null
            }

            val targetVanishState = try {
                VanishDAO.load(target.uniqueId)
            } catch (e: Exception) {
                debugLog("Error loading vanish state for ${target.name}: ${e.message}")
                null
            }

            if (viewerVanishState != null && (viewerVanishState.vanished || viewerVanishState.hideMe ||
                        viewerVanishState.hideOthers || viewerVanishState.showOthers.isNotEmpty())) {
                debugLog("Vanish system active for viewer ${viewer.name} - deferring")
                return true
            }

            if (targetVanishState != null && (targetVanishState.vanished || targetVanishState.hideMe ||
                        targetVanishState.hideOthers || targetVanishState.showOthers.isNotEmpty())) {
                debugLog("Vanish system active for target ${target.name} - deferring")
                return true
            }

            // Do NOT defer for spectators here — game-instance isolation must still apply to
            // dead-in-game players who are tracked in the spectator map.

            return false
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error checking other visibility systems: ${e.message}")
            return false // Do not skip game-instance filtering on error
        }
    }

    private fun shouldLobbyPlayerSeeOtherInTab(lobbyPlayer: Player, otherPlayer: Player): Boolean {
        try {
            val otherInstance = plugin.gameManager?.getPlayerInstance(otherPlayer)
            val shouldSee = otherInstance == null
            debugLog("Lobby player ${lobbyPlayer.name} should see ${otherPlayer.name}: $shouldSee (other in game: ${otherInstance != null})")
            return shouldSee
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error in shouldLobbyPlayerSeeOtherInTab: ${e.message}")
            return true
        }
    }

    private fun shouldOtherPlayerSeeLobbyPlayerInTab(otherPlayer: Player, lobbyPlayer: Player): Boolean {
        try {
            val otherInstance = plugin.gameManager?.getPlayerInstance(otherPlayer)
            val shouldSee = otherInstance == null
            debugLog("Player ${otherPlayer.name} should see lobby player ${lobbyPlayer.name}: $shouldSee (other in game: ${otherInstance != null})")
            return shouldSee
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error in shouldOtherPlayerSeeLobbyPlayerInTab: ${e.message}")
            return true
        }
    }

    private fun shouldGamePlayerSeeOtherInTab(
        gamePlayer: Player,
        otherPlayer: Player,
        gamePlayerIds: Set<UUID>
    ): Boolean {
        try {
            val otherPlayerUUID = otherPlayer.uniqueId ?: return false
            val shouldSee = gamePlayerIds.contains(otherPlayerUUID)
            debugLog("Game player ${gamePlayer.name} should see ${otherPlayer.name}: $shouldSee (same game: $shouldSee)")
            return shouldSee
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error in shouldGamePlayerSeeOtherInTab: ${e.message}")
            return false
        }
    }

    private fun shouldOtherPlayerSeeGamePlayerInTab(
        otherPlayer: Player,
        gamePlayer: Player,
        gamePlayerIds: Set<UUID>
    ): Boolean {
        try {
            val otherInstance = plugin.gameManager?.getPlayerInstance(otherPlayer)
            if (otherInstance == null) {
                debugLog("Lobby player ${otherPlayer.name} should NOT see game player ${gamePlayer.name}")
                return false
            } else {
                val otherPlayerUUID = otherPlayer.uniqueId ?: return false
                val shouldSee = gamePlayerIds.contains(otherPlayerUUID)
                debugLog("Game player ${otherPlayer.name} should see game player ${gamePlayer.name}: $shouldSee (same game: $shouldSee)")
                return shouldSee
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error in shouldOtherPlayerSeeGamePlayerInTab: ${e.message}")
            return false
        }
    }

    private fun updatePlayerVisibility(viewer: Player, target: Player, shouldSee: Boolean, context: String) {
        try {
            val viewerUUID = viewer.uniqueId ?: return
            val targetUUID = target.uniqueId ?: return
            val cacheKey = Pair(viewerUUID, targetUUID)
            val lastState = lastVisibilityStates[cacheKey]
            if (lastState == shouldSee) {
                return
            }
            val canCurrentlySee = viewer.canSee(target)
            if (shouldSee && !canCurrentlySee) {
                viewer.showPlayer(plugin, target)
                lastVisibilityStates[cacheKey] = true
                debugLog("TAB: Showed ${target.name} to ${viewer.name} ($context)")
            } else if (!shouldSee && canCurrentlySee) {
                viewer.hidePlayer(plugin, target)
                lastVisibilityStates[cacheKey] = false
                debugLog("TAB: Hid ${target.name} from ${viewer.name} ($context)")
            } else {
                lastVisibilityStates[cacheKey] = shouldSee
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error updating visibility ${viewer.name} -> ${target.name}: ${e.message}")
        }
    }

    fun updateTabVisibilityForAllPlayers() {
        if (!isPerGameInstanceEnabled()) {
            debugLog("Per-game-instance disabled, showing all players to everyone")
            Bukkit.getOnlinePlayers().forEach { player ->
                if (player.isOnline) {
                    showAllPlayersToPlayer(player)
                }
            }
            return
        }
        val onlinePlayers = try {
            Bukkit.getOnlinePlayers()?.toList() ?: return
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error getting online players: ${e.message}")
            return
        }
        debugLog("Updating tab visibility for ${onlinePlayers.size} online players with per-game-instance rules")
        onlinePlayers.forEach { player ->
            if (player != null && player.isOnline) {
                try {
                    updateTabVisibilityForPlayer(player)
                } catch (e: Exception) {
                    plugin.logger.warning("[TabVisibility] Error updating ${player.name}: ${e.message}")
                }
            }
        }
        debugLog("Completed tab visibility update for all players")
    }

    fun forceUpdateAllPlayers() {
        debugLog("Forcing complete tab visibility refresh")
        playerGameStates.clear()
        lastVisibilityStates.clear()
        updateTabVisibilityForAllPlayers()
    }

    fun onPlayerJoinGame(player: Player?, gameInstance: GameInstance?) {
        if (player == null || !player.isOnline || gameInstance == null) {
            return
        }
        debugLog("${player.name} joined game ${gameInstance.id}")
        val playerUUID = try {
            player.uniqueId ?: return
        } catch (e: Exception) {
            return
        }
        playerGameStates.remove(playerUUID)
        clearPlayerVisibilityCache(playerUUID)
        updateTabVisibilityForAllPlayers()
    }

    fun onPlayerLeaveGame(player: Player?, gameInstance: GameInstance?) {
        if (player == null || !player.isOnline) {
            return
        }
        debugLog("${player.name} left game and went to LOBBY")
        val playerUUID = try {
            player.uniqueId ?: return
        } catch (e: Exception) {
            return
        }
        playerGameStates.remove(playerUUID)
        clearPlayerVisibilityCache(playerUUID)
        updateTabVisibilityForAllPlayers()
    }

    private fun clearPlayerVisibilityCache(playerUUID: UUID) {
        val keysToRemove = lastVisibilityStates.keys.filter {
            it.first == playerUUID || it.second == playerUUID
        }
        keysToRemove.forEach { lastVisibilityStates.remove(it) }
    }

    private fun isPerGameInstanceEnabled(): Boolean {
        return try {
            plugin.tabManager?.isEnabled() == true &&
                    plugin.tabManager?.displayPerGameInstance == true
        } catch (e: Exception) {
            false
        }
    }

    fun getVisibilityStats(): Map<String, Any> {
        return try {
            val onlinePlayers = Bukkit.getOnlinePlayers()
            val totalPlayers = onlinePlayers?.size ?: 0
            val gameManager = plugin.gameManager
            val playersInGames = if (gameManager != null) {
                onlinePlayers?.count { player ->
                    try {
                        player != null && gameManager.getPlayerInstance(player) != null
                    } catch (e: Exception) {
                        false
                    }
                } ?: 0
            } else {
                0
            }
            val playersInLobby = totalPlayers - playersInGames
            mapOf(
                "totalPlayers" to totalPlayers,
                "playersInGames" to playersInGames,
                "playersInLobby" to playersInLobby,
                "trackedGameStates" to playerGameStates.size,
                "cachedVisibilityStates" to lastVisibilityStates.size,
                "activeUpdates" to isUpdating.size,
                "perGameInstanceEnabled" to isPerGameInstanceEnabled(),
                "tabManagerEnabled" to (plugin.tabManager?.isEnabled() ?: false)
            )
        } catch (e: Exception) {
            mapOf("error" to "Failed to get stats: ${e.message}")
        }
    }

    fun cleanup() {
        debugLog("Cleaning up TabVisibilityManager")
        playerGameStates.clear()
        lastVisibilityStates.clear()
        isUpdating.clear()
    }

    private fun debugLog(message: String) {
        try {
            if (plugin.configManager?.debugMode == true) {
                plugin.logger?.info("[TabVisibility] $message")
            }
        } catch (_: Exception) {}
    }

    fun shouldBeVisibleInTab(viewer: Player, target: Player): Boolean {
        if (viewer == target) return true
        val viewerInstance = plugin.gameManager.getPlayerInstance(viewer)
        val targetInstance = plugin.gameManager.getPlayerInstance(target)
        if (viewerInstance == null && targetInstance == null) return true
        if (viewerInstance != null && targetInstance != null && viewerInstance == targetInstance) return true
        return false
    }
}