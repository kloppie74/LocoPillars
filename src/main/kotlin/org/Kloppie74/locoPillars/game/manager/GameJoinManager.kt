package org.Kloppie74.locoPillars.game.manager

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.data.GameState
import java.util.*

class GameJoinManager(
    private val plugin: LocoPillars,
    private val instances: MutableList<GameInstance>,
    private val playerToInstance: MutableMap<UUID, GameInstance>
) {

    fun joinGame(player: Player, gameTypeName: String, gameplayManager: GameplayManager): GameInstance? {
        plugin.logger.info("[GameJoinManager] Player ${player.name} attempting to join game type: $gameTypeName")

        // Check if player is already in a game
        if (playerToInstance.containsKey(player.uniqueId)) {
            plugin.logger.info("[GameJoinManager] Player ${player.name} is already in a game")
            player.sendMessage("${ChatColor.RED}You are already in a game! Use /pillars leave to leave your current game.")
            return null
        }

        val instanceManager = GameInstanceManager(plugin, instances)
        val instance = instanceManager.getAvailableInstance(gameTypeName) ?: run {
            plugin.logger.warning("[GameJoinManager] No available games found for ${player.name} requesting $gameTypeName")
            player.sendMessage("${ChatColor.RED}No available games found for type: $gameTypeName")
            return null
        }

        if (instance.players.size >= instance.getMaxPlayers()) {
            plugin.logger.info("[GameJoinManager] Game instance ${instance.id} is full (${instance.players.size}/${instance.getMaxPlayers()})")
            player.sendMessage("${ChatColor.RED}This game is full!")
            return null
        }

        plugin.logger.info("[GameJoinManager] Adding player ${player.name} to instance ${instance.id}")

        // FIXED: Don't occupy arena immediately - only when game starts
        val wasEmpty = instance.players.isEmpty()

        instance.players.add(player.uniqueId)
        playerToInstance[player.uniqueId] = instance

        val playersAfterJoin = instance.players.size
        plugin.logger.info("[GameJoinManager] Player added to instance, total players: $playersAfterJoin/${instance.getMaxPlayers()}")

        // IMPORTANT: Keep arena available for more players to join
        if (wasEmpty) {
            plugin.logger.info("[GameJoinManager] First player joined, keeping arena available for more players")
            // Don't call plugin.arenaManager.occupyInstance() here!
        }

        // Prepare player for game
        val playerManager = PlayerManager(plugin)
        playerManager.preparePlayerForGame(player)
        plugin.logger.info("[GameJoinManager] Player ${player.name} prepared for game")

        // Teleport player to spawn area
        val spawnArea = instance.arenaInstance.config.getSpawnTeleportLocation(instance.arenaInstance.origin)
        plugin.logger.info("[GameJoinManager] Teleporting ${player.name} to spawn area: $spawnArea")
        player.teleport(spawnArea)
        plugin.logger.info("[GameJoinManager] Player teleported successfully")

        // Send join messages
        broadcastToGame(
            instance,
            "${ChatColor.GREEN}${player.name} joined the game! ($playersAfterJoin/${instance.getMaxPlayers()})"
        )
        player.sendMessage("${ChatColor.GREEN}You joined ${instance.getDisplayName()}!")

        // Check if we can start the game
        if (playersAfterJoin >= instance.getMinPlayers() && instance.state == GameState.WAITING) {
            plugin.logger.info("[GameJoinManager] Minimum players reached ($playersAfterJoin/${instance.getMinPlayers()}), starting lobby countdown")
            gameplayManager.startLobbyCountdown(instance)
        } else {
            plugin.logger.info("[GameJoinManager] Waiting for more players: $playersAfterJoin/${instance.getMinPlayers()} minimum")
        }

        return instance
    }

    private fun broadcastToGame(instance: GameInstance, message: String) {
        val players = getPlayersInGame(instance)
        plugin.logger.info("[GameJoinManager] Broadcasting to ${players.size} players: $message")
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