package org.Kloppie74.locoPillars.game.manager

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.data.GameState
import java.util.*

class GameLeaveManager(
    private val plugin: LocoPillars,
    private val instances: MutableList<GameInstance>,
    private val playerToInstance: MutableMap<UUID, GameInstance>
) {

    fun leaveGame(player: Player, gameplayManager: GameplayManager): Boolean {
        plugin.logger.info("[GameLeaveManager] Player ${player.name} attempting to leave game")

        val instance = playerToInstance[player.uniqueId]
        if (instance == null) {
            plugin.logger.info("[GameLeaveManager] Player ${player.name} is not in any game")
            return false
        }

        plugin.logger.info("[GameLeaveManager] Removing player ${player.name} from instance ${instance.id}")

        instance.players.remove(player.uniqueId)
        playerToInstance.remove(player.uniqueId)
        plugin.logger.info("[GameLeaveManager] Player removed, remaining players: ${instance.players.size}")

        // Restore player state
        val playerManager = PlayerManager(plugin)
        playerManager.restorePlayerState(player)
        plugin.logger.info("[GameLeaveManager] Player state restored")

        // Teleport player back to main world
        val mainWorld = Bukkit.getWorlds().firstOrNull { !it.name.startsWith("pillars_") }
        if (mainWorld != null) {
            player.teleport(mainWorld.spawnLocation)
            plugin.logger.info("[GameLeaveManager] Player ${player.name} teleported to main world")
        } else {
            plugin.logger.warning("[GameLeaveManager] No main world found for teleporting player ${player.name}")
        }

        broadcastToGame(
            instance,
            "${ChatColor.YELLOW}${player.name} left the game! (${instance.players.size}/${instance.getMaxPlayers()})"
        )
        player.sendMessage("${ChatColor.GREEN}You left the game!")

        // Check if game should end or reset
        when {
            instance.players.isEmpty() -> {
                plugin.logger.info("[GameLeaveManager] No players remaining in instance ${instance.id}, resetting instance")

                // Cancel any running countdown
                gameplayManager.cancelCountdown(instance.id)

                // Reset instance state
                instance.state = GameState.WAITING

                // Release the arena so it becomes available again
                plugin.arenaManager.releaseInstance(instance.arenaInstance)
                plugin.logger.info("[GameLeaveManager] Instance reset to WAITING state and arena released")
            }

            instance.state == GameState.ACTIVE && instance.players.size < 2 -> {
                plugin.logger.info("[GameLeaveManager] Less than 2 players remaining during active game, ending")
                val winner = Bukkit.getPlayer(instance.players.first())
                if (winner != null) {
                    gameplayManager.endGame(instance, "${winner.name} wins by elimination!", playerToInstance)
                } else {
                    gameplayManager.endGame(instance, "Game ended", playerToInstance)
                }
            }

            instance.state == GameState.STARTING && instance.players.size < instance.getMinPlayers() -> {
                plugin.logger.info("[GameLeaveManager] Below minimum players during countdown, cancelling start")

                // Cancel countdown
                gameplayManager.cancelCountdown(instance.id)

                instance.state = GameState.WAITING
                broadcastToGame(instance, "${ChatColor.RED}Not enough players! Game start cancelled.")

                // Don't release arena here if there are still players
            }

            else -> {
                plugin.logger.info("[GameLeaveManager] Game continues with ${instance.players.size} players")
            }
        }

        return true
    }

    private fun broadcastToGame(instance: GameInstance, message: String) {
        val players = getPlayersInGame(instance)
        plugin.logger.info("[GameLeaveManager] Broadcasting to ${players.size} players: $message")
        players.forEach { player ->
            player.sendMessage(message)
        }
    }

    private fun getPlayersInGame(instance: GameInstance): List<Player> {
        return instance.players.mapNotNull { playerId ->
            plugin.server.getPlayer(playerId)?.takeIf { it.isOnline }
        }
    }
}