package org.Kloppie74.locoPillars.game.manager

import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.arena.ArenaManager
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.events.TimerEvent
import java.util.*

class GameManager(private val plugin: LocoPillars) {
    private val instances = mutableListOf<GameInstance>()
    private val playerToInstance = mutableMapOf<UUID, GameInstance>()
    private lateinit var timerEvent: TimerEvent

    // Sub-managers
    private lateinit var instanceManager: GameInstanceManager
    private lateinit var joinManager: GameJoinManager
    private lateinit var leaveManager: GameLeaveManager
    private lateinit var gameplayManager: GameplayManager
    private lateinit var playerManager: PlayerManager

    fun initialize() {
        timerEvent = TimerEvent(plugin)
        plugin.logger.info("[GameManager] TimerEvent initialized")

        // Initialize sub-managers
        instanceManager = GameInstanceManager(plugin, instances)
        joinManager = GameJoinManager(plugin, instances, playerToInstance)
        leaveManager = GameLeaveManager(plugin, instances, playerToInstance)
        gameplayManager = GameplayManager(plugin, timerEvent)
        playerManager = PlayerManager(plugin)

        plugin.logger.info("[GameManager] All sub-managers initialized")
    }

    fun preloadInstances(arenaManager: ArenaManager) {
        instanceManager.preloadInstances(arenaManager)
    }

    fun getAvailableInstance(gameTypeName: String): GameInstance? {
        return instanceManager.getAvailableInstance(gameTypeName)
    }

    fun joinGame(player: Player, gameTypeName: String): GameInstance? {
        return joinManager.joinGame(player, gameTypeName, gameplayManager)
    }

    fun leaveGame(player: Player): Boolean {
        return leaveManager.leaveGame(player, gameplayManager)
    }

    fun handlePlayerDeath(player: Player): Boolean {
        return gameplayManager.handlePlayerDeath(player, playerToInstance, playerManager)
    }

    // Called by TimerEvent when game timer expires
    fun endGameByTimer(instance: GameInstance) {
        gameplayManager.endGameByTimer(instance, playerToInstance, playerManager)
    }

    // Called by TimerEvent to give items to players
    fun giveItemsToPlayers(instance: GameInstance) {
        gameplayManager.giveItemsToPlayers(instance)
    }

    fun getPlayerInstance(player: Player): GameInstance? {
        return playerToInstance[player.uniqueId]
    }

    fun getAllInstances(): List<GameInstance> = instances

    fun getInstancesByGameType(gameTypeName: String): List<GameInstance> {
        return instances.filter { it.arenaInstance.template.name == gameTypeName }
    }

    fun getAvailableInstances(): List<GameInstance> {
        return instances.filter { it.canJoin() }
    }

    fun getActiveGames(): List<GameInstance> {
        return instances.filter { it.state == GameState.ACTIVE }
    }

    fun cleanup() {
        plugin.logger.info("[GameManager] Starting cleanup...")

        if (::timerEvent.isInitialized) {
            timerEvent.cleanup()
            plugin.logger.info("[GameManager] TimerEvent cleanup completed")
        }

        if (::gameplayManager.isInitialized) {
            gameplayManager.cleanup()
        }

        plugin.logger.info("[GameManager] GameManager cleanup completed")
    }
}