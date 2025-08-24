package org.Kloppie74.locoPillars.game.manager

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import org.Kloppie74.locoPillars.utils.achievements.AchievementListener
import java.util.*

class GameLeaveManager(
    private val plugin: LocoPillars,
    private val instances: MutableList<GameInstance>,
    private val playerToInstance: MutableMap<UUID, GameInstance>
) {

    private val achievementListener get() = plugin.achievementListener
    private val statsTable get() = plugin.databaseManager.statsTable

    fun leaveGame(player: Player, gameplayManager: GameplayManager): Boolean {
        debugLog("Player ${player.name} attempting to leave game")

        val instance = playerToInstance[player.uniqueId]
        if (instance == null) {
            debugLog("Player ${player.name} is not in any game")
            SpectatorUtils.removeSpectatorStatus(player, plugin)
            return false
        }

        debugLog("Removing player ${player.name} from instance ${instance.id}")

        // Update database stats for leaving
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)

        if (playerId != null) {
            // Set player as inactive
            statsTracker.setActiveStatus(playerId, false)

            // Update last seen
            statsTracker.updateLastSeen(playerId)

            // If game was active and player is leaving, count as quit
            if (instance.state == GameState.ACTIVE) {
                statsTable.incrementGamesQuit(playerId)
                achievementListener.onQuitGame(player)
                debugLog("Recorded game quit for player: ${player.name}")
            }

            // Update arena stats for player leaving
            val arenaName = instance.arenaInstance.config.name
            statsTracker.createOrUpdatePlayerArenaStats(playerId, arenaName)

            debugLog("Updated database stats for player leave: ${player.name}")
        }

        instance.players.remove(player.uniqueId)
        playerToInstance.remove(player.uniqueId)
        debugLog("Player removed, remaining players: ${instance.players.size}")

        val playerManager = PlayerManager(plugin)
        playerManager.restorePlayerState(player)
        SpectatorUtils.removeSpectatorStatus(player, plugin)
        debugLog("Player state restored")

        val lobbySpawn = plugin.configManager.getLobbySpawnLocation()
        if (lobbySpawn != null) {
            player.teleport(lobbySpawn)
        } else {
            player.teleport(player.server.worlds[0].spawnLocation)
        }

        broadcastToGame(
            instance,
            "${ChatColor.YELLOW}${player.name} left the game! (${instance.players.size}/${instance.getMaxPlayers()})"
        )
        player.sendMessage("${ChatColor.GREEN}You left the game!")

        when {
            instance.players.isEmpty() -> {
                debugLog("No players remaining in instance ${instance.id}, resetting instance")

                gameplayManager.cancelCountdown(instance.id)

                instance.state = GameState.WAITING

                plugin.arenaManager.releaseInstance(instance.arenaInstance)
                debugLog("Instance reset to WAITING state and arena released")
            }

            instance.state == GameState.ACTIVE && instance.players.size < 2 -> {
                debugLog("Less than 2 players remaining during active game, ending")
                val winner = Bukkit.getPlayer(instance.players.first())
                if (winner != null) {
                    gameplayManager.endGame(instance, "${winner.name} wins by elimination!", playerToInstance)
                } else {
                    gameplayManager.endGame(instance, "Game ended", playerToInstance)
                }
            }

            instance.state == GameState.STARTING && instance.players.size < instance.getMinPlayers() -> {
                debugLog("Below minimum players during countdown, cancelling start")

                gameplayManager.cancelCountdown(instance.id)

                instance.state = GameState.WAITING
                broadcastToGame(instance, "${ChatColor.RED}Not enough players! Game start cancelled.")
            }

            else -> {
                debugLog("Game continues with ${instance.players.size} players")
            }
        }

        return true
    }

    private fun broadcastToGame(instance: GameInstance, message: String) {
        val players = getPlayersInGame(instance)
        debugLog("Broadcasting to ${players.size} players: $message")
        players.forEach { player ->
            player.sendMessage(message)
        }
    }

    private fun getPlayersInGame(instance: GameInstance): List<Player> {
        return instance.players.mapNotNull { playerId ->
            plugin.server.getPlayer(playerId)?.takeIf { it.isOnline }
        }
    }

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[GameLeaveManager] $message")
        }
    }
}