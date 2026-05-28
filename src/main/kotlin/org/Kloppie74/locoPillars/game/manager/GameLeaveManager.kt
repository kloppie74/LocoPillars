package org.Kloppie74.locoPillars.game.manager

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import org.Kloppie74.locoPillars.database.dao.PlayerDAO
import org.Kloppie74.locoPillars.database.dao.StatsDAO
import org.Kloppie74.locoPillars.database.dao.ArenaStatsDAO
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.game.rewards.RewardProcessor
import org.bukkit.configuration.file.YamlConfiguration
import java.time.LocalDateTime
import java.util.*

/**
 * Manages player leaving logic for game instances.
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

    fun leaveGame(player: Player, gameplayManager: GameplayManager): Boolean {
        debugLog("Player ${player.name} attempting to leave game")

        val instance = playerToInstance[player.uniqueId]
        if (instance == null) {
            debugLog("Player ${player.name} is not in any game")
            SpectatorUtils.removeSpectatorStatus(player, plugin)
            return false
        }

        debugLog("Removing player ${player.name} from instance ${instance.id}")
        updateDatabaseOnLeave(player, instance)

        instance.players.remove(player.uniqueId)
        playerToInstance.remove(player.uniqueId)
        debugLog("Player removed, remaining players: ${instance.players.size}")

        val playerManager = PlayerManager(plugin)
        playerManager.restorePlayerState(player)
        SpectatorUtils.removeSpectatorStatus(player, plugin)
        debugLog("Player state restored")

        teleportToLobby(player)

        broadcastToGame(instance, MSG.lang("game.leave.broadcast",
            "player" to player.name,
            "current" to instance.players.size.toString(),
            "max" to instance.getMaxPlayers().toString()))
        player.sendMessage(MSG.lang("game.leave.success"))

        val rewardsSection = YamlConfiguration.loadConfiguration(instance.arenaInstance.config.file)
            .getConfigurationSection("Rewards")
        val remainingPlayers = instance.players.mapNotNull { plugin.server.getPlayer(it) }
        RewardProcessor.processLeaveRewards(plugin, player, remainingPlayers, rewardsSection)

        handleGameStateAfterLeave(instance, gameplayManager)
        return true
    }

    private fun updateDatabaseOnLeave(player: Player, instance: GameInstance) {
        try {
            val playerId = PlayerDAO.getPlayerId(player.name)
            if (playerId == null) {
                plugin.logger.warning("[GameLeaveManager] ✗ Could not find playerId for ${player.name}")
                return
            }

            val inactiveSet = PlayerDAO.setActiveStatus(playerId, false)
            if (inactiveSet) debugLog("✓ Set player ${player.name} as inactive")
            else plugin.logger.warning("[GameLeaveManager] ✗ Failed to set inactive status for ${player.name}")

            val lastSeenUpdated = PlayerDAO.updateLastSeen(playerId, LocalDateTime.now())
            if (lastSeenUpdated) debugLog("✓ Updated last seen for ${player.name}")

            if (instance.state == GameState.ACTIVE) {
                val quitIncremented = StatsDAO.incrementStat(playerId, "games_quit")
                if (quitIncremented) {
                    debugLog("✓ Recorded game quit for ${player.name}")
                    achievementListener.onQuitGame(player)
                } else {
                    plugin.logger.warning("[GameLeaveManager] ✗ Failed to record game quit for ${player.name}")
                }
            }

            val arenaName = instance.arenaInstance.config.name
            val arenaStatsUpdated = ArenaStatsDAO.createOrUpdatePlayerArenaStats(playerId, arenaName)
            if (arenaStatsUpdated) debugLog("✓ Updated player arena stats for ${player.name} in $arenaName")

            debugLog("✓ Database stats updated for player leave: ${player.name}")
        } catch (ex: Exception) {
            plugin.logger.severe("[GameLeaveManager] ❌ Error updating database on leave for ${player.name}: ${ex.message}")
            ex.printStackTrace()
        }
    }

    private fun handleGameStateAfterLeave(instance: GameInstance, gameplayManager: GameplayManager) {
        when {
            instance.players.isEmpty() -> {
                debugLog("No players remaining in instance ${instance.id}, resetting instance")
                resetEmptyInstance(instance, gameplayManager)
            }
            // No-lobby mode: state jumps from WAITING straight to ACTIVE when min players
            // reached, and the cage countdown runs while state == ACTIVE / cagesOpen == false.
            // Treat that window like a STARTING countdown: cancel the start instead of declaring
            // a "winner" — the remaining player never actually fought anyone.
            instance.state == GameState.ACTIVE && !instance.cagesOpen &&
                    instance.players.size < instance.getMinPlayers() -> {
                debugLog("Below minimum players during pre-cage window, cancelling start")
                cancelPreCageStart(instance, gameplayManager)
            }
            instance.state == GameState.ACTIVE && instance.players.size < 2 -> {
                debugLog("Less than 2 players remaining during active game, ending game")
                endGameWithWinner(instance, gameplayManager)
            }
            instance.state == GameState.STARTING && instance.players.size < instance.getMinPlayers() -> {
                debugLog("Below minimum players during countdown, cancelling start")
                cancelGameStart(instance, gameplayManager)
            }
            else -> debugLog("Game continues with ${instance.players.size} players")
        }
    }

    /**
     * Cage countdown is running but min players dropped — abort without declaring a winner.
     * Mirrors [cancelGameStart] but also tears down what [startGame] already set up
     * (game timer, voting effect, cages) so the instance is reusable.
     */
    private fun cancelPreCageStart(instance: GameInstance, gameplayManager: GameplayManager) {
        gameplayManager.abortPreCageStart(instance, playerToInstance)
        broadcastToGame(instance, MSG.lang("game.leave.cancelled_start"))
        debugLog("✓ Pre-cage start aborted, reset to WAITING state")
    }

    private fun resetEmptyInstance(instance: GameInstance, gameplayManager: GameplayManager) {
        gameplayManager.cancelCountdown(instance.id)
        instance.state = GameState.WAITING
        plugin.arenaManager.releaseInstance(instance.arenaInstance)
        debugLog("✓ Instance reset to WAITING state and arena released")
    }

    private fun endGameWithWinner(instance: GameInstance, gameplayManager: GameplayManager) {
        val winnerUUID = instance.players.firstOrNull()
        val winner = if (winnerUUID != null) Bukkit.getPlayer(winnerUUID) else null
        if (winner != null) {
            debugLog("✓ Ending game, winner: ${winner.name}")
            gameplayManager.endGame(instance, MSG.lang("game.end.winner_elimination", "winner" to winner.name), playerToInstance)
        } else {
            debugLog("✓ Ending game, no valid winner found")
            gameplayManager.endGame(instance, MSG.lang("game.end.game_ended"), playerToInstance)
        }
    }

    private fun cancelGameStart(instance: GameInstance, gameplayManager: GameplayManager) {
        gameplayManager.cancelCountdown(instance.id)
        instance.state = GameState.WAITING
        broadcastToGame(instance, MSG.lang("game.leave.cancelled_start"))
        debugLog("✓ Game start cancelled, reset to WAITING state")
    }

    private fun teleportToLobby(player: Player) {
        val lobbySpawn = plugin.configManager.getLobbySpawnLocation()
        if (lobbySpawn != null) {
            val teleportSuccess = player.teleport(lobbySpawn)
            if (teleportSuccess) debugLog("✓ Teleported ${player.name} to lobby spawn")
            else {
                plugin.logger.warning("[GameLeaveManager] ✗ Failed to teleport ${player.name} to lobby")
                player.teleport(player.server.worlds[0].spawnLocation)
            }
        } else {
            player.teleport(player.server.worlds[0].spawnLocation)
            debugLog("✓ Teleported ${player.name} to main world spawn (no lobby configured)")
        }
    }

    private fun broadcastToGame(instance: GameInstance, message: String) {
        val players = getPlayersInGame(instance)
        debugLog("Broadcasting to ${players.size} players: $message")
        players.forEach { it.sendMessage(message) }
    }

    private fun getPlayersInGame(instance: GameInstance): List<Player> {
        return instance.players.mapNotNull { playerId ->
            plugin.server.getPlayer(playerId)?.takeIf { it.isOnline }
        }
    }

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) plugin.logger.info("[GameLeaveManager] $message")
    }
}
