package org.Kloppie74.locoPillars.utils.teams.tab

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.EnumWrappers
import com.comphenix.protocol.wrappers.PlayerInfoData
import com.comphenix.protocol.wrappers.WrappedChatComponent
import com.comphenix.protocol.wrappers.WrappedGameProfile
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.database.dao.VanishDAO
import org.Kloppie74.locoPillars.game.instance.GameInstance
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * ProtocolLib-based TabVisibilityManager that actually controls tab list entries
 * This replaces the old showPlayer/hidePlayer approach with direct packet manipulation
 */
class ProtocolLibTabVisibilityManager(private val plugin: LocoPillars) {

    private val playerTabStates = ConcurrentHashMap<UUID, MutableSet<UUID>>() // viewer -> set of players they can see in tab
    private val isUpdating = ConcurrentHashMap<UUID, Boolean>()
    private val protocolManager = ProtocolLibrary.getProtocolManager()

    init {
        debugLog("ProtocolLib TabVisibilityManager initialized")
    }

    fun updateTabVisibilityForPlayer(player: Player?) {
        if (player == null || !player.isOnline) {
            debugLog("Skipping update for null/offline player")
            return
        }

        val playerUUID = try {
            player.uniqueId ?: return
        } catch (e: Exception) {
            debugLog("[ProtocolTabVisibility] Error getting player UUID for ${player.name}: ${e.message}")
            return
        }

        if (!isPerGameInstanceEnabled()) {
            debugLog("Per-game-instance disabled for ${player.name}, showing all players")
            showAllPlayersInTabToPlayer(player)
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
                debugLog("[ProtocolTabVisibility] Error getting player instance for ${player.name}: ${e.message}")
                return
            }

            debugLog("Updating tab visibility for ${player.name} - Game: ${currentGameInstance?.id ?: "LOBBY"}")

            if (currentGameInstance == null) {
                applyLobbyTabVisibility(player)
            } else {
                applyGameTabVisibility(player, currentGameInstance)
            }

        } catch (e: Exception) {
            debugLog("[ProtocolTabVisibility] Error updating visibility for ${player.name}: ${e.message}")
        } finally {
            isUpdating.remove(playerUUID)
        }
    }

    private fun showAllPlayersInTabToPlayer(player: Player) {
        try {
            val currentlyVisible = playerTabStates.getOrPut(player.uniqueId) { ConcurrentHashMap.newKeySet() }
            val allOnline = Bukkit.getOnlinePlayers().map { it.uniqueId }.toSet()

            // Add missing players to tab
            val toAdd = allOnline - currentlyVisible
            if (toAdd.isNotEmpty()) {
                addPlayersToTab(player, toAdd)
                currentlyVisible.addAll(toAdd)
            }
        } catch (e: Exception) {
            debugLog("[ProtocolTabVisibility] Error in showAllPlayersInTabToPlayer for ${player.name}: ${e.message}")
        }
    }

    private fun applyLobbyTabVisibility(lobbyPlayer: Player) {
        try {
            val onlinePlayers = Bukkit.getOnlinePlayers()?.toList() ?: return
            val currentlyVisible = playerTabStates.getOrPut(lobbyPlayer.uniqueId) { ConcurrentHashMap.newKeySet() }
            val shouldBeVisible = mutableSetOf<UUID>()

            debugLog("Applying lobby tab visibility for ${lobbyPlayer.name} (${onlinePlayers.size} online)")

            onlinePlayers.forEach { otherPlayer ->
                if (otherPlayer == null || otherPlayer == lobbyPlayer || !otherPlayer.isOnline) return@forEach

                try {
                    if (shouldDeferToOtherSystems(lobbyPlayer, otherPlayer)) {
                        // If other systems control this, check current Bukkit visibility
                        if (lobbyPlayer.canSee(otherPlayer)) {
                            shouldBeVisible.add(otherPlayer.uniqueId)
                        }
                        return@forEach
                    }

                    val shouldSee = shouldLobbyPlayerSeeOtherInTab(lobbyPlayer, otherPlayer)
                    if (shouldSee) {
                        shouldBeVisible.add(otherPlayer.uniqueId)
                    }

                    debugLog("Tab visibility: ${lobbyPlayer.name} -> ${otherPlayer.name}: $shouldSee")
                } catch (e: Exception) {
                    debugLog("[ProtocolTabVisibility] Error processing ${otherPlayer.name} for lobby player ${lobbyPlayer.name}: ${e.message}")
                }
            }

            // Calculate changes
            val toAdd = shouldBeVisible - currentlyVisible
            val toRemove = currentlyVisible - shouldBeVisible

            // Apply changes
            if (toAdd.isNotEmpty()) {
                addPlayersToTab(lobbyPlayer, toAdd)
                currentlyVisible.addAll(toAdd)
            }
            if (toRemove.isNotEmpty()) {
                removePlayersFromTab(lobbyPlayer, toRemove)
                currentlyVisible.removeAll(toRemove)
            }

        } catch (e: Exception) {
            debugLog("[ProtocolTabVisibility] Error in applyLobbyTabVisibility for ${lobbyPlayer.name}: ${e.message}")
        }
    }

    private fun applyGameTabVisibility(gamePlayer: Player, gameInstance: GameInstance) {
        try {
            val onlinePlayers = Bukkit.getOnlinePlayers()?.toList() ?: return
            val gamePlayerIds = gameInstance.players?.toSet() ?: emptySet()
            val currentlyVisible = playerTabStates.getOrPut(gamePlayer.uniqueId) { ConcurrentHashMap.newKeySet() }
            val shouldBeVisible = mutableSetOf<UUID>()

            debugLog("Applying game tab visibility for ${gamePlayer.name} in game ${gameInstance.id} (${gamePlayerIds.size} game players)")

            onlinePlayers.forEach { otherPlayer ->
                if (otherPlayer == null || otherPlayer == gamePlayer || !otherPlayer.isOnline) return@forEach

                try {
                    if (shouldDeferToOtherSystems(gamePlayer, otherPlayer)) {
                        // If other systems control this, check current Bukkit visibility
                        if (gamePlayer.canSee(otherPlayer)) {
                            shouldBeVisible.add(otherPlayer.uniqueId)
                        }
                        return@forEach
                    }

                    val shouldSee = shouldGamePlayerSeeOtherInTab(gamePlayer, otherPlayer, gamePlayerIds)
                    if (shouldSee) {
                        shouldBeVisible.add(otherPlayer.uniqueId)
                    }

                    debugLog("Tab visibility: ${gamePlayer.name} -> ${otherPlayer.name}: $shouldSee")
                } catch (e: Exception) {
                    debugLog("[ProtocolTabVisibility] Error processing ${otherPlayer.name} for game player ${gamePlayer.name}: ${e.message}")
                }
            }

            // Calculate changes
            val toAdd = shouldBeVisible - currentlyVisible
            val toRemove = currentlyVisible - shouldBeVisible

            // Apply changes
            if (toAdd.isNotEmpty()) {
                addPlayersToTab(gamePlayer, toAdd)
                currentlyVisible.addAll(toAdd)
            }
            if (toRemove.isNotEmpty()) {
                removePlayersFromTab(gamePlayer, toRemove)
                currentlyVisible.removeAll(toRemove)
            }

        } catch (e: Exception) {
            debugLog("[ProtocolTabVisibility] Error in applyGameTabVisibility for ${gamePlayer.name}: ${e.message}")
        }
    }

    private fun shouldDeferToOtherSystems(viewer: Player, target: Player): Boolean {
        try {
            // Only defer to vanish system when the vanish state has active flags.
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

            // Do NOT defer for spectators — game-instance isolation must apply to dead-in-game players.

            return false
        } catch (e: Exception) {
            debugLog("[ProtocolTabVisibility] Error checking other visibility systems: ${e.message}")
            return false // Do not skip game-instance filtering on error
        }
    }

    private fun shouldLobbyPlayerSeeOtherInTab(lobbyPlayer: Player, otherPlayer: Player): Boolean {
        try {
            val otherInstance = plugin.gameManager?.getPlayerInstance(otherPlayer)
            // Lobby players should only see other lobby players
            val shouldSee = otherInstance == null
            debugLog("Lobby player ${lobbyPlayer.name} should see ${otherPlayer.name}: $shouldSee (other in game: ${otherInstance != null})")
            return shouldSee
        } catch (e: Exception) {
            debugLog("[ProtocolTabVisibility] Error in shouldLobbyPlayerSeeOtherInTab: ${e.message}")
            return false // Default to not visible on error
        }
    }

    private fun shouldGamePlayerSeeOtherInTab(
        gamePlayer: Player,
        otherPlayer: Player,
        gamePlayerIds: Set<UUID>
    ): Boolean {
        try {
            val otherPlayerUUID = otherPlayer.uniqueId ?: return false
            // Game players should only see players in the same game instance
            val shouldSee = gamePlayerIds.contains(otherPlayerUUID)
            debugLog("Game player ${gamePlayer.name} should see ${otherPlayer.name}: $shouldSee (same game: $shouldSee)")
            return shouldSee
        } catch (e: Exception) {
            debugLog("[ProtocolTabVisibility] Error in shouldGamePlayerSeeOtherInTab: ${e.message}")
            return false
        }
    }

    private fun addPlayersToTab(viewer: Player, playersToAdd: Set<UUID>) {
        try {
            if (playersToAdd.isEmpty()) return

            val playerInfoDataList = mutableListOf<PlayerInfoData>()

            playersToAdd.forEach { playerUUID ->
                val targetPlayer = Bukkit.getPlayer(playerUUID)
                if (targetPlayer != null && targetPlayer.isOnline) {
                    val gameProfile = WrappedGameProfile.fromPlayer(targetPlayer)
                    val displayName = WrappedChatComponent.fromText(targetPlayer.displayName)

                    val playerInfoData = PlayerInfoData(
                        gameProfile,
                        targetPlayer.ping,
                        EnumWrappers.NativeGameMode.fromBukkit(targetPlayer.gameMode),
                        displayName
                    )
                    playerInfoDataList.add(playerInfoData)
                }
            }

            if (playerInfoDataList.isNotEmpty()) {
                val packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO)
                packet.playerInfoAction.write(0, EnumWrappers.PlayerInfoAction.ADD_PLAYER)
                packet.playerInfoDataLists.write(0, playerInfoDataList)

                protocolManager.sendServerPacket(viewer, packet)
                debugLog("Added ${playerInfoDataList.size} players to ${viewer.name}'s tab")
            }
        } catch (e: Exception) {
            debugLog("[ProtocolTabVisibility] Error adding players to tab for ${viewer.name}: ${e.message}")
        }
    }

    private fun removePlayersFromTab(viewer: Player, playersToRemove: Set<UUID>) {
        try {
            if (playersToRemove.isEmpty()) return

            val playerInfoDataList = mutableListOf<PlayerInfoData>()

            playersToRemove.forEach { playerUUID ->
                val targetPlayer = Bukkit.getPlayer(playerUUID)
                if (targetPlayer != null) {
                    val gameProfile = WrappedGameProfile.fromPlayer(targetPlayer)
                    val playerInfoData = PlayerInfoData(
                        gameProfile, 0, EnumWrappers.NativeGameMode.SURVIVAL, null
                    )
                    playerInfoDataList.add(playerInfoData)
                }
            }

            // === FIX: Don't send packet if list is empty ===
            if (playerInfoDataList.isEmpty()) return

            val packet = protocolManager.createPacket(PacketType.Play.Server.PLAYER_INFO)
            packet.playerInfoAction.write(0, EnumWrappers.PlayerInfoAction.REMOVE_PLAYER)
            packet.playerInfoDataLists.write(0, playerInfoDataList)

            protocolManager.sendServerPacket(viewer, packet)
            debugLog("Removed ${playerInfoDataList.size} players from ${viewer.name}'s tab")
        } catch (e: Exception) {
            debugLog("[ProtocolTabVisibility] Error removing players from tab for ${viewer.name}: ${e.message}")
        }
    }

    fun updateTabVisibilityForAllPlayers() {
        if (!isPerGameInstanceEnabled()) {
            debugLog("Per-game-instance disabled, showing all players to everyone")
            Bukkit.getOnlinePlayers().forEach { player ->
                if (player.isOnline) {
                    showAllPlayersInTabToPlayer(player)
                }
            }
            return
        }

        val onlinePlayers = try {
            Bukkit.getOnlinePlayers()?.toList() ?: return
        } catch (e: Exception) {
            debugLog("[ProtocolTabVisibility] Error getting online players: ${e.message}")
            return
        }

        debugLog("Updating tab visibility for ${onlinePlayers.size} online players with per-game-instance rules")
        onlinePlayers.forEach { player ->
            if (player != null && player.isOnline) {
                try {
                    updateTabVisibilityForPlayer(player)
                } catch (e: Exception) {
                    debugLog("[ProtocolTabVisibility] Error updating ${player.name}: ${e.message}")
                }
            }
        }
        debugLog("Completed tab visibility update for all players")
    }

    fun forceUpdateAllPlayers() {
        debugLog("Forcing complete tab visibility refresh")
        playerTabStates.clear()

        // First, make sure all players have a fresh tab list with all online players
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.isOnline) {
                initializePlayerTab(player)
            }
        }

        // Then apply the game instance filtering
        updateTabVisibilityForAllPlayers()
    }

    private fun initializePlayerTab(player: Player) {
        try {
            val allOnlineUUIDs = Bukkit.getOnlinePlayers().map { it.uniqueId }.toSet()
            playerTabStates[player.uniqueId] = ConcurrentHashMap.newKeySet<UUID>().apply {
                addAll(allOnlineUUIDs)
            }
            debugLog("Initialized tab state for ${player.name} with ${allOnlineUUIDs.size} players")
        } catch (e: Exception) {
            debugLog("[ProtocolTabVisibility] Error initializing tab for ${player.name}: ${e.message}")
        }
    }

    fun onPlayerJoinGame(player: Player?, gameInstance: GameInstance?) {
        if (player == null || !player.isOnline || gameInstance == null) {
            return
        }
        debugLog("${player.name} joined game ${gameInstance.id}")

        // Clear current state and refresh
        playerTabStates.remove(player.uniqueId)
        updateTabVisibilityForAllPlayers()
    }

    fun onPlayerLeaveGame(player: Player?, gameInstance: GameInstance?) {
        if (player == null || !player.isOnline) {
            return
        }
        debugLog("${player.name} left game and went to LOBBY")

        // Clear current state and refresh
        playerTabStates.remove(player.uniqueId)
        updateTabVisibilityForAllPlayers()
    }

    fun onPlayerJoin(player: Player) {
        try {
            // Initialize with all players visible first
            initializePlayerTab(player)

            // Then update all existing players to see/not see this new player
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                updateTabVisibilityForAllPlayers()
            }, 5L)
        } catch (e: Exception) {
            debugLog("[ProtocolTabVisibility] Error handling player join for ${player.name}: ${e.message}")
        }
    }

    fun onPlayerQuit(player: Player) {
        try {
            playerTabStates.remove(player.uniqueId)

            // Update remaining players in case they shouldn't see this player anymore
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                Bukkit.getOnlinePlayers().forEach { onlinePlayer ->
                    playerTabStates[onlinePlayer.uniqueId]?.remove(player.uniqueId)
                }
            }, 1L)
        } catch (e: Exception) {
            debugLog("[ProtocolTabVisibility] Error handling player quit for ${player.name}: ${e.message}")
        }
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

            val totalTabStates = playerTabStates.values.sumOf { it.size }

            mapOf(
                "totalPlayers" to totalPlayers,
                "playersInGames" to playersInGames,
                "playersInLobby" to playersInLobby,
                "trackedTabStates" to playerTabStates.size,
                "totalVisibilityEntries" to totalTabStates,
                "activeUpdates" to isUpdating.size,
                "perGameInstanceEnabled" to isPerGameInstanceEnabled(),
                "tabManagerEnabled" to (plugin.tabManager?.isEnabled() ?: false),
                "protocolLibEnabled" to true
            )
        } catch (e: Exception) {
            mapOf("error" to "Failed to get stats: ${e.message}")
        }
    }

    fun cleanup() {
        debugLog("Cleaning up ProtocolLib TabVisibilityManager")
        playerTabStates.clear()
        isUpdating.clear()
    }

    private fun debugLog(message: String) {
        try {
            if (plugin.configManager?.debugMode == true) {
                plugin.logger?.info("[ProtocolTabVisibility] $message")
            }
        } catch (_: Exception) {}
    }
}