package org.Kloppie74.locoPillars.game.manager

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import org.Kloppie74.locoPillars.database.dao.PlayerDAO
import org.Kloppie74.locoPillars.database.dao.StatsDAO
import org.Kloppie74.locoPillars.database.dao.ArenaStatsDAO
import java.time.LocalDateTime
import java.util.*

/**
 * Manages player leaving logic for game instances.
 * Updated to use DAO pattern for database operations.
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
class GameLeaveManager(
    private val plugin: LocoPillars,
    private val instances: MutableList<GameInstance>,
    private val playerToInstance: MutableMap<UUID, GameInstance>
) {

    private val achievementListener get() = plugin.achievementListener

    /**
     * Main method to remove a player from a game instance.
     * Handles all cleanup, database updates, and game state checks.
     */
    fun leaveGame(player: Player, gameplayManager: GameplayManager): Boolean {
        debugLog("Player ${player.name} attempting to leave game")

        // === 1. CHECK IF PLAYER IS IN A GAME ===
        val instance = playerToInstance[player.uniqueId]
        if (instance == null) {
            debugLog("Player ${player.name} is not in any game")
            SpectatorUtils.removeSpectatorStatus(player, plugin)
            return false
        }

        debugLog("Removing player ${player.name} from instance ${instance.id}")

        // === 2. UPDATE DATABASE STATS ===
        updateDatabaseOnLeave(player, instance)

        // === 3. REMOVE PLAYER FROM INSTANCE ===
        instance.players.remove(player.uniqueId)
        playerToInstance.remove(player.uniqueId)
        debugLog("Player removed, remaining players: ${instance.players.size}")

        // === 4. RESTORE PLAYER STATE ===
        val playerManager = PlayerManager(plugin)
        playerManager.restorePlayerState(player)
        SpectatorUtils.removeSpectatorStatus(player, plugin)
        debugLog("Player state restored")

        // === 5. TELEPORT TO LOBBY ===
        teleportToLobby(player)

        // === 6. BROADCAST LEAVE MESSAGE ===
        broadcastToGame(
            instance,
            "${ChatColor.YELLOW}${player.name} left the game! (${instance.players.size}/${instance.getMaxPlayers()})"
        )
        player.sendMessage("${ChatColor.GREEN}You left the game!")

        // === 7. HANDLE GAME STATE AFTER LEAVE ===
        handleGameStateAfterLeave(instance, gameplayManager)

        return true
    }

    // =================== DATABASE OPERATIONS ===================

    /**
     * Updates database stats when a player leaves a game.
     */
    private fun updateDatabaseOnLeave(player: Player, instance: GameInstance) {
        try {
            val playerId = PlayerDAO.getPlayerId(player.name)
            if (playerId == null) {
                plugin.logger.warning("[GameLeaveManager] ✗ Could not find playerId for ${player.name}")
                return
            }

            // Set player as inactive
            val inactiveSet = PlayerDAO.setActiveStatus(playerId, false)
            if (inactiveSet) {
                debugLog("✓ Set player ${player.name} as inactive")
            } else {
                plugin.logger.warning("[GameLeaveManager] ✗ Failed to set inactive status for ${player.name}")
            }

            // Update last seen timestamp
            val lastSeenUpdated = PlayerDAO.updateLastSeen(playerId, LocalDateTime.now())
            if (lastSeenUpdated) {
                debugLog("✓ Updated last seen for ${player.name}")
            }

            // If game was active, count as quit
            if (instance.state == GameState.ACTIVE) {
                val quitIncremented = StatsDAO.incrementStat(playerId, "games_quit")
                if (quitIncremented) {
                    debugLog("✓ Recorded game quit for ${player.name}")

                    // Trigger achievement listener
                    achievementListener.onQuitGame(player)
                } else {
                    plugin.logger.warning("[GameLeaveManager] ✗ Failed to record game quit for ${player.name}")
                }
            }

            // Update player arena stats (ensure record exists)
            val arenaName = instance.arenaInstance.config.name
            val arenaStatsUpdated = ArenaStatsDAO.createOrUpdatePlayerArenaStats(playerId, arenaName)
            if (arenaStatsUpdated) {
                debugLog("✓ Updated player arena stats for ${player.name} in $arenaName")
            }

            debugLog("✓ Database stats updated for player leave: ${player.name}")

        } catch (ex: Exception) {
            plugin.logger.severe("[GameLeaveManager] ❌ Error updating database on leave for ${player.name}: ${ex.message}")
            ex.printStackTrace()
        }
    }

    // =================== GAME STATE HANDLING ===================

    /**
     * Handles game state changes after a player leaves.
     */
    private fun handleGameStateAfterLeave(instance: GameInstance, gameplayManager: GameplayManager) {
        when {
            // Case 1: No players remaining - Reset instance
            instance.players.isEmpty() -> {
                debugLog("No players remaining in instance ${instance.id}, resetting instance")
                resetEmptyInstance(instance, gameplayManager)
            }

            // Case 2: Active game with less than 2 players - End game
            instance.state == GameState.ACTIVE && instance.players.size < 2 -> {
                debugLog("Less than 2 players remaining during active game, ending game")
                endGameWithWinner(instance, gameplayManager)
            }

            // Case 3: Starting countdown with too few players - Cancel start
            instance.state == GameState.STARTING && instance.players.size < instance.getMinPlayers() -> {
                debugLog("Below minimum players during countdown, cancelling start")
                cancelGameStart(instance, gameplayManager)
            }

            // Case 4: Game continues normally
            else -> {
                debugLog("Game continues with ${instance.players.size} players")
            }
        }
    }

    /**
     * Resets an empty game instance.
     */
    private fun resetEmptyInstance(instance: GameInstance, gameplayManager: GameplayManager) {
        // Cancel any active countdown
        gameplayManager.cancelCountdown(instance.id)

        // Reset instance state
        instance.state = GameState.WAITING

        // Release arena
        plugin.arenaManager.releaseInstance(instance.arenaInstance)

        debugLog("✓ Instance reset to WAITING state and arena released")
    }

    /**
     * Ends the game with the remaining player as winner.
     */
    private fun endGameWithWinner(instance: GameInstance, gameplayManager: GameplayManager) {
        val winnerUUID = instance.players.firstOrNull()
        val winner = if (winnerUUID != null) Bukkit.getPlayer(winnerUUID) else null

        if (winner != null) {
            debugLog("✓ Ending game, winner: ${winner.name}")
            gameplayManager.endGame(
                instance,
                "${winner.name} wins by elimination!",
                playerToInstance
            )
        } else {
            debugLog("✓ Ending game, no valid winner found")
            gameplayManager.endGame(
                instance,
                "Game ended",
                playerToInstance
            )
        }
    }

    /**
     * Cancels the game start countdown.
     */
    private fun cancelGameStart(instance: GameInstance, gameplayManager: GameplayManager) {
        // Cancel countdown
        gameplayManager.cancelCountdown(instance.id)

        // Reset to waiting state
        instance.state = GameState.WAITING

        // Notify remaining players
        broadcastToGame(
            instance,
            "${ChatColor.RED}Not enough players! Game start cancelled."
        )

        debugLog("✓ Game start cancelled, reset to WAITING state")
    }

    // =================== UTILITY METHODS ===================

    /**
     * Teleports player to lobby spawn location.
     */
    private fun teleportToLobby(player: Player) {
        val lobbySpawn = plugin.configManager.getLobbySpawnLocation()

        if (lobbySpawn != null) {
            val teleportSuccess = player.teleport(lobbySpawn)
            if (teleportSuccess) {
                debugLog("✓ Teleported ${player.name} to lobby spawn")
            } else {
                plugin.logger.warning("[GameLeaveManager] ✗ Failed to teleport ${player.name} to lobby")
                // Fallback to main world spawn
                player.teleport(player.server.worlds[0].spawnLocation)
            }
        } else {
            // No lobby spawn configured, use main world spawn
            player.teleport(player.server.worlds[0].spawnLocation)
            debugLog("✓ Teleported ${player.name} to main world spawn (no lobby configured)")
        }
    }

    /**
     * Broadcasts a message to all players in a game instance.
     */
    private fun broadcastToGame(instance: GameInstance, message: String) {
        val players = getPlayersInGame(instance)
        debugLog("Broadcasting to ${players.size} players: $message")
        players.forEach { player ->
            player.sendMessage(message)
        }
    }

    /**
     * Gets all online players in a game instance.
     */
    private fun getPlayersInGame(instance: GameInstance): List<Player> {
        return instance.players.mapNotNull { playerId ->
            plugin.server.getPlayer(playerId)?.takeIf { it.isOnline }
        }
    }

    /**
     * Logs debug message if debug mode is enabled.
     */
    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[GameLeaveManager] $message")
        }
    }
}