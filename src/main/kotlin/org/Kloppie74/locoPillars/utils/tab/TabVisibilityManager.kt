package org.Kloppie74.locoPillars.utils.tab

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class TabVisibilityManager(private val plugin: LocoPillars) {

    // Thread-safe tracking of player game states
    private val playerGameStates = ConcurrentHashMap<UUID, UUID?>() // Player UUID -> Game Instance ID
    private val isUpdating = ConcurrentHashMap<UUID, Boolean>() // Prevent concurrent updates per player

    /**
     * Updates tab visibility for a specific player based on their current game state
     */
    fun updateTabVisibilityForPlayer(player: Player?) {
        // Enhanced null safety check for player
        if (player == null) {
            debugLog("Skipping update for null player")
            return
        }

        if (!player.isOnline) {
            debugLog("Skipping update for offline player: ${player.name}")
            return
        }

        // Get UUID safely with additional null checks
        val playerUUID = try {
            val uuid = player.uniqueId
            if (uuid == null) {
                plugin.logger.warning("[TabVisibility] Player UUID is null for ${player.name}")
                return
            }
            uuid
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error getting player UUID for ${player.name}: ${e.message}")
            e.printStackTrace()
            return
        }

        // Quick exit if feature is disabled - with null safety
        val tabUtils = plugin.tabUtils
        if (tabUtils == null) {
            plugin.logger.warning("[TabVisibility] TabUtils is null")
            return
        }

        if (!tabUtils.isEnabled() || !tabUtils.displayPerGameInstance) {
            showAllPlayersToPlayer(player)
            return
        }

        // Prevent concurrent updates for the same player
        if (isUpdating.putIfAbsent(playerUUID, true) != null) {
            debugLog("Update already in progress for ${player.name}")
            return
        }

        try {
            // Add comprehensive null safety checks
            val gameManager = plugin.gameManager
            if (gameManager == null) {
                plugin.logger.warning("[TabVisibility] GameManager is null, showing all players to ${player.name}")
                showAllPlayersToPlayer(player)
                return
            }

            val currentGameInstance = try {
                gameManager.getPlayerInstance(player)
            } catch (e: Exception) {
                plugin.logger.warning("[TabVisibility] Error getting player instance for ${player.name}: ${e.message}")
                e.printStackTrace()
                // If we can't get the instance, assume lobby
                showAllPlayersToPlayer(player)
                return
            }

            val currentGameId = currentGameInstance?.id
            val lastKnownGameId = playerGameStates[playerUUID]

            // Always update state to track changes properly
            // Note: ConcurrentHashMap doesn't accept null values, so we use a special marker
            if (currentGameId != null) {
                playerGameStates[playerUUID] = currentGameId
            } else {
                // Player is in lobby, remove from map (or use special marker)
                playerGameStates.remove(playerUUID)
            }

            if (currentGameInstance == null) {
                // KRITIEK: Player is in LOBBY - show ALL online players and make sure ALL players can see this player
                debugLog("${player.name} is in LOBBY - showing all online players and making player visible to everyone")

                // Show all players to this lobby player
                showAllPlayersToPlayer(player)

                // KRITIEK: Make sure ALL other players can see this lobby player
                makePlayerVisibleToEveryone(player)

                val onlineCount = Bukkit.getOnlinePlayers()?.size ?: 0
                debugLog("${player.name} -> Lobby (showing all $onlineCount players, visible to everyone)")

            } else {
                // Player is in a game - only show players from the same game instance
                debugLog("${player.name} is in GAME ${currentGameInstance.id} - showing only game players")
                showOnlyGamePlayersToPlayer(player, currentGameInstance)

                // KRITIEK: Also update visibility for all other players because this player joined/is in a game
                updateVisibilityForAllOtherPlayers(player, currentGameInstance)

                val playerCount = currentGameInstance.players?.size ?: 0
                debugLog("${player.name} -> Game ${currentGameInstance.id} (showing $playerCount players)")
            }

        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error updating visibility for ${player.name}: ${e.message}")
            plugin.logger.warning("[TabVisibility] Exception details: ${e.javaClass.simpleName}")

            // Print stack trace for debugging
            e.printStackTrace()

            // Fallback: show all players if there's any error
            try {
                showAllPlayersToPlayer(player)
                makePlayerVisibleToEveryone(player)
                debugLog("Applied fallback visibility for ${player.name}")
            } catch (fallbackError: Exception) {
                plugin.logger.severe("[TabVisibility] Even fallback failed for ${player.name}: ${fallbackError.message}")
                fallbackError.printStackTrace()
            }
        } finally {
            // Safe removal using the UUID we got earlier
            try {
                isUpdating.remove(playerUUID)
            } catch (e: Exception) {
                plugin.logger.warning("[TabVisibility] Error removing update lock for ${player.name}: ${e.message}")
            }
        }
    }

    /**
     * KRITIEK: Makes a player visible to ALL other online players
     * This is used when a player is in the lobby
     */
    private fun makePlayerVisibleToEveryone(lobbyPlayer: Player) {
        try {
            val onlinePlayers = Bukkit.getOnlinePlayers()
            if (onlinePlayers == null) {
                debugLog("Online players collection is null in makePlayerVisibleToEveryone")
                return
            }

            var madeVisibleCount = 0
            onlinePlayers.forEach { otherPlayer ->
                if (otherPlayer != null && otherPlayer != lobbyPlayer && otherPlayer.isOnline) {
                    try {
                        if (!otherPlayer.canSee(lobbyPlayer)) {
                            otherPlayer.showPlayer(plugin, lobbyPlayer)
                            madeVisibleCount++
                            debugLog("Made ${lobbyPlayer.name} visible to ${otherPlayer.name} (lobby player)")
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("[TabVisibility] Error making ${lobbyPlayer.name} visible to ${otherPlayer.name}: ${e.message}")
                    }
                }
            }

            debugLog("Made ${lobbyPlayer.name} visible to $madeVisibleCount other players")

        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error in makePlayerVisibleToEveryone for ${lobbyPlayer.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * KRITIEK: Updates visibility for all other players when a player joins/leaves a game
     */
    private fun updateVisibilityForAllOtherPlayers(changedPlayer: Player, gameInstance: GameInstance) {
        try {
            val onlinePlayers = Bukkit.getOnlinePlayers()
            if (onlinePlayers == null) return

            val gameManager = plugin.gameManager ?: return

            onlinePlayers.forEach { otherPlayer ->
                if (otherPlayer != null && otherPlayer != changedPlayer && otherPlayer.isOnline) {
                    try {
                        val otherGameInstance = gameManager.getPlayerInstance(otherPlayer)

                        if (otherGameInstance == null) {
                            // Other player is in lobby - should NOT see the game player
                            if (otherPlayer.canSee(changedPlayer)) {
                                otherPlayer.hidePlayer(plugin, changedPlayer)
                                debugLog("Hid game player ${changedPlayer.name} from lobby player ${otherPlayer.name}")
                            }
                        } else if (otherGameInstance.id == gameInstance.id) {
                            // Other player is in SAME game - should see the game player
                            if (!otherPlayer.canSee(changedPlayer)) {
                                otherPlayer.showPlayer(plugin, changedPlayer)
                                debugLog("Showed same-game player ${changedPlayer.name} to ${otherPlayer.name}")
                            }
                        } else {
                            // Other player is in DIFFERENT game - should NOT see the game player
                            if (otherPlayer.canSee(changedPlayer)) {
                                otherPlayer.hidePlayer(plugin, changedPlayer)
                                debugLog("Hid different-game player ${changedPlayer.name} from ${otherPlayer.name}")
                            }
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("[TabVisibility] Error updating visibility between ${changedPlayer.name} and ${otherPlayer.name}: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error in updateVisibilityForAllOtherPlayers: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Updates tab visibility for all online players
     * CRITICAL: Always updates ALL online players, not just specific game instances
     */
    fun updateTabVisibilityForAllPlayers() {
        val tabUtils = plugin.tabUtils
        if (tabUtils == null || !tabUtils.isEnabled() || !tabUtils.displayPerGameInstance) {
            // If per-game-instance is disabled, ensure all players can see each other
            ensureAllPlayersVisible()
            return
        }

        val onlinePlayers = try {
            val players = Bukkit.getOnlinePlayers()
            if (players == null) {
                plugin.logger.warning("[TabVisibility] getOnlinePlayers() returned null")
                return
            }
            players.toList()
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error getting online players: ${e.message}")
            return
        }

        debugLog("Updating tab visibility for ${onlinePlayers.size} online players")

        // CRITICAL: Update ALL online players, regardless of their game state
        onlinePlayers.forEach { player ->
            if (player != null && player.isOnline) {
                try {
                    updateTabVisibilityForPlayer(player)
                } catch (e: Exception) {
                    plugin.logger.warning("[TabVisibility] Error updating ${player.name}: ${e.message}")
                    plugin.logger.warning("[TabVisibility] Exception for ${player.name}: ${e.javaClass.simpleName}")
                }
            } else {
                debugLog("Skipping null or offline player in bulk update")
            }
        }

        debugLog("Completed visibility update for all ${onlinePlayers.size} online players")
    }

    /**
     * Forces a complete refresh of all player visibility states
     */
    fun forceUpdateAllPlayers() {
        debugLog("Forcing complete visibility refresh for all players")

        // Clear the cache to force updates
        playerGameStates.clear()

        // Update all players
        updateTabVisibilityForAllPlayers()
    }

    /**
     * Handles when a player joins a game instance
     */
    fun onPlayerJoinGame(player: Player?, gameInstance: GameInstance?) {
        if (player == null || !player.isOnline || gameInstance == null) {
            debugLog("Invalid parameters for onPlayerJoinGame - player: ${player?.name ?: "null"}, game: ${gameInstance?.id ?: "null"}")
            return
        }

        debugLog("${player.name} joined game ${gameInstance.id}")

        // Get UUID safely
        val playerUUID = try {
            val uuid = player.uniqueId
            if (uuid == null) {
                debugLog("Player UUID is null in onPlayerJoinGame for ${player.name}")
                return
            }
            uuid
        } catch (e: Exception) {
            debugLog("Error getting player UUID in onPlayerJoinGame: ${e.message}")
            return
        }

        // Clear cached state to force update
        playerGameStates.remove(playerUUID)

        // Update visibility immediately
        updateTabVisibilityForPlayer(player)

        debugLog("Completed visibility update after ${player.name} joined game ${gameInstance.id}")
    }

    /**
     * Handles when a player leaves a game instance (goes to lobby)
     */
    fun onPlayerLeaveGame(player: Player?, gameInstance: GameInstance?) {
        if (player == null || !player.isOnline) {
            debugLog("Invalid player for onPlayerLeaveGame - player: ${player?.name ?: "null"}")
            return
        }

        debugLog("${player.name} left game ${gameInstance?.id ?: "unknown"} and went to LOBBY")

        // Get UUID safely
        val playerUUID = try {
            val uuid = player.uniqueId
            if (uuid == null) {
                debugLog("Player UUID is null in onPlayerLeaveGame for ${player.name}")
                return
            }
            uuid
        } catch (e: Exception) {
            debugLog("Error getting player UUID in onPlayerLeaveGame: ${e.message}")
            return
        }

        // Clear cached state
        playerGameStates.remove(playerUUID)

        // Update visibility immediately - player is now in lobby
        updateTabVisibilityForPlayer(player)

        debugLog("Completed visibility update after ${player.name} left game and went to lobby")
    }

    /**
     * Shows all online players to the specified player
     * This is used when a player is in the lobby or when per-game-instance is disabled
     */
    private fun showAllPlayersToPlayer(player: Player?) {
        if (player == null || !player.isOnline) {
            debugLog("Cannot show players to null or offline player")
            return
        }

        try {
            val onlinePlayers = Bukkit.getOnlinePlayers()
            if (onlinePlayers == null) {
                debugLog("Online players collection is null")
                return
            }

            val onlinePlayersList = onlinePlayers.toList()
            debugLog("Showing ${onlinePlayersList.size} online players to ${player.name} (lobby mode)")

            var shownCount = 0
            onlinePlayersList.forEach { otherPlayer ->
                if (otherPlayer != null && otherPlayer != player && otherPlayer.isOnline) {
                    try {
                        val canCurrentlySee = player.canSee(otherPlayer)
                        if (!canCurrentlySee) {
                            player.showPlayer(plugin, otherPlayer)
                            shownCount++
                            debugLog("Showed ${otherPlayer.name} to ${player.name}")
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("[TabVisibility] Error showing ${otherPlayer.name} to ${player.name}: ${e.message}")
                    }
                } else {
                    debugLog("Skipping null, offline, or same player: ${otherPlayer?.name ?: "null"}")
                }
            }

            debugLog("Successfully showed $shownCount players to ${player.name} (total online: ${onlinePlayersList.size})")
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error in showAllPlayersToPlayer for ${player.name}: ${e.message}")
            plugin.logger.warning("[TabVisibility] Exception details: ${e.javaClass.simpleName}")
            e.printStackTrace()
        }
    }

    /**
     * Shows only players from the same game instance to the specified player
     * Also hides players who are NOT in the same game instance
     */
    private fun showOnlyGamePlayersToPlayer(player: Player?, gameInstance: GameInstance?) {
        if (player == null || !player.isOnline || gameInstance == null) {
            plugin.logger.warning("[TabVisibility] Invalid parameters for showOnlyGamePlayersToPlayer")
            return
        }

        val allowedPlayerIds = try {
            val players = gameInstance.players
            if (players == null) {
                debugLog("Game instance ${gameInstance.id} has null players list")
                emptySet<UUID>()
            } else {
                players.toSet()
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error getting players from game instance: ${e.message}")
            emptySet<UUID>()
        }

        debugLog("Game ${gameInstance.id} has ${allowedPlayerIds.size} players. Updating visibility for ${player.name}")

        try {
            val onlinePlayers = Bukkit.getOnlinePlayers()
            if (onlinePlayers == null) {
                debugLog("Online players collection is null")
                return
            }

            val onlinePlayersList = onlinePlayers.toList()
            var shownCount = 0
            var hiddenCount = 0

            // Check ALL online players and update visibility accordingly
            onlinePlayersList.forEach { otherPlayer ->
                if (otherPlayer != null && otherPlayer != player && otherPlayer.isOnline) {
                    try {
                        val otherPlayerUUID = try {
                            val uuid = otherPlayer.uniqueId
                            if (uuid == null) {
                                plugin.logger.warning("[TabVisibility] UUID is null for player ${otherPlayer.name}")
                                return@forEach
                            }
                            uuid
                        } catch (e: Exception) {
                            plugin.logger.warning("[TabVisibility] Error getting UUID for ${otherPlayer.name}: ${e.message}")
                            return@forEach
                        }

                        val shouldShow = allowedPlayerIds.contains(otherPlayerUUID)
                        val canCurrentlySee = player.canSee(otherPlayer)

                        if (shouldShow && !canCurrentlySee) {
                            player.showPlayer(plugin, otherPlayer)
                            shownCount++
                            debugLog("Showed ${otherPlayer.name} to ${player.name} (same game)")
                        } else if (!shouldShow && canCurrentlySee) {
                            player.hidePlayer(plugin, otherPlayer)
                            hiddenCount++
                            debugLog("Hid ${otherPlayer.name} from ${player.name} (different game/lobby)")
                        }
                    } catch (e: Exception) {
                        plugin.logger.warning("[TabVisibility] Error updating visibility of ${otherPlayer.name} for ${player.name}: ${e.message}")
                    }
                }
            }

            debugLog("Game visibility update for ${player.name}: showed $shownCount, hid $hiddenCount (total online: ${onlinePlayersList.size})")
        } catch (e: Exception) {
            plugin.logger.warning("[TabVisibility] Error in showOnlyGamePlayersToPlayer for ${player.name}: ${e.message}")
            plugin.logger.warning("[TabVisibility] Exception details: ${e.javaClass.simpleName}")
            e.printStackTrace()
        }
    }

    /**
     * Ensures all players can see each other (used when per-game-instance is disabled)
     */
    private fun ensureAllPlayersVisible() {
        try {
            val onlinePlayers = Bukkit.getOnlinePlayers()
            if (onlinePlayers == null) {
                debugLog("Online players collection is null in ensureAllPlayersVisible")
                return
            }

            val onlinePlayersList = onlinePlayers.toList()

            onlinePlayersList.forEach { player ->
                if (player != null && player.isOnline) {
                    onlinePlayersList.forEach { otherPlayer ->
                        if (otherPlayer != null && player != otherPlayer && otherPlayer.isOnline && !player.canSee(otherPlayer)) {
                            try {
                                player.showPlayer(plugin, otherPlayer)
                            } catch (e: Exception) {
                                debugLog("Error ensuring ${otherPlayer.name} is visible to ${player.name}: ${e.message}")
                            }
                        }
                    }
                }
            }
            debugLog("Ensured all players are visible to each other")
        } catch (e: Exception) {
            debugLog("Error in ensureAllPlayersVisible: ${e.message}")
        }
    }

    /**
     * Gets the current visibility statistics for debugging
     */
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
            val tabUtils = plugin.tabUtils

            mapOf(
                "totalPlayers" to totalPlayers,
                "playersInGames" to playersInGames,
                "playersInLobby" to playersInLobby,
                "trackedStates" to playerGameStates.size,
                "activeUpdates" to isUpdating.size,
                "perGameInstanceEnabled" to (tabUtils?.displayPerGameInstance ?: false)
            )
        } catch (e: Exception) {
            debugLog("Error getting visibility stats: ${e.message}")
            mapOf(
                "error" to "Failed to get stats: ${e.message}"
            )
        }
    }

    /**
     * Cleanup method called on plugin disable
     */
    fun cleanup() {
        debugLog("Cleaning up TabVisibilityManager")

        try {
            // Show all players to everyone before cleanup
            val tabUtils = plugin.tabUtils
            if (tabUtils?.isEnabled() == true) {
                ensureAllPlayersVisible()
            }
        } catch (e: Exception) {
            debugLog("Error during cleanup visibility reset: ${e.message}")
        }

        playerGameStates.clear()
        isUpdating.clear()
    }

    private fun debugLog(message: String) {
        try {
            val configManager = plugin.configManager
            if (configManager?.debugMode == true) {
                val logger = plugin.logger
                logger?.info("[TabVisibility] $message")
            }
        } catch (e: Exception) {
            // Silently ignore debug log errors, but try to log to console
            try {
                System.err.println("[LocoPillars-TabVisibility] Debug log error: ${e.message}")
                System.err.println("[LocoPillars-TabVisibility] Original message: $message")
            } catch (ignored: Exception) {
                // Complete silence if even System.err fails
            }
        }
    }
}