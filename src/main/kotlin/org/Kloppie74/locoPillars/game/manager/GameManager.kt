package org.Kloppie74.locoPillars.game.manager

import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.arena.ArenaManager
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.events.TimerEvent
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import java.util.*

class GameManager(private val plugin: LocoPillars) {
    private val instances = mutableListOf<GameInstance>()
    private val playerToInstance = mutableMapOf<UUID, GameInstance>()
    lateinit var timerEvent: TimerEvent

    lateinit var instanceManager: GameInstanceManager
    lateinit var joinManager: GameJoinManager
    lateinit var leaveManager: GameLeaveManager
    lateinit var gameplayManager: GameplayManager
    lateinit var playerManager: PlayerManager

    fun initialize() {
        timerEvent = TimerEvent(plugin)
        plugin.logger.info("[GameManager] TimerEvent initialized")

        instanceManager = GameInstanceManager(plugin, instances, plugin.configManager)
        joinManager = GameJoinManager(plugin, instances, playerToInstance)
        leaveManager = GameLeaveManager(plugin, instances, playerToInstance)
        gameplayManager = GameplayManager(plugin, timerEvent, plugin.configManager)
        playerManager = PlayerManager(plugin)

        plugin.logger.info("[GameManager] All sub-managers initialized")
    }

    fun preloadInstances(arenaManager: ArenaManager) {
        instanceManager.preloadInstances(arenaManager)
    }

    fun getAvailableInstance(gameTypeName: String): GameInstance? {
        return instanceManager.getAvailableInstance(gameTypeName)
    }

    fun cancelAutoQueue(playerId: UUID): Boolean = gameplayManager.cancelAutoQueue(playerId)

    /** Take a player out of the post-match spectator phase and send them to lobby now. */
    fun exitPostMatchToLobby(player: Player) = gameplayManager.exitPostMatchToLobby(player)

    fun joinGame(player: Player, gameTypeName: String): GameInstance? {
        gameplayManager.cancelAutoQueue(player.uniqueId) // cancel pending queue on manual join
        clearEliminatedState(player.uniqueId)
        if (!SpectatorUtils.ensurePlayerAlive(player, plugin, "GameManager")) {
            player.sendMessage(MSG.chatColors("&cPlease respawn first and try joining again."))
            return null
        }

        val instance = joinManager.joinGame(player, gameTypeName, gameplayManager)
        if (instance != null) {
            SpectatorUtils.removeSpectatorStatus(player, plugin)
            // Notify tab/visibility systems that this player joined a game
            plugin.teamsListener.onPlayerJoinGame(player)
            plugin.spectateListener.onPlayerGameStatusChange(player)
        }
        return instance
    }

    /**
     * Joins all [partyMemberUUIDs] (including the [leader]) to the same game
     * instance.  After every member joins, tab/visibility systems are refreshed
     * for the whole group.
     */
    fun joinPartyToGame(
        leader: Player,
        partyMemberUUIDs: List<java.util.UUID>,
        gameTypeName: String
    ): org.Kloppie74.locoPillars.game.instance.GameInstance? {
        // Cancel any pending queue for all members
        partyMemberUUIDs.forEach { gameplayManager.cancelAutoQueue(it) }
        partyMemberUUIDs.forEach { clearEliminatedState(it) }

        val instance = joinManager.joinPartyToGame(leader, partyMemberUUIDs, gameTypeName, gameplayManager)
        if (instance != null) {
            // Refresh tab / visibility for every member that joined
            partyMemberUUIDs.mapNotNull { plugin.server.getPlayer(it) }.filter { it.isOnline }.forEach { member ->
                SpectatorUtils.removeSpectatorStatus(member, plugin)
                plugin.teamsListener.onPlayerJoinGame(member)
                plugin.spectateListener.onPlayerGameStatusChange(member)
            }
        }
        return instance
    }

    fun leaveGame(player: Player): Boolean {
        val success = leaveManager.leaveGame(player, gameplayManager)
        clearEliminatedState(player.uniqueId)
        // Notify tab/visibility systems that this player is now in lobby
        plugin.teamsListener.onPlayerLeaveGame(player)
        plugin.spectateListener.onPlayerGameStatusChange(player)
        return success
    }

    fun handlePlayerDeath(player: Player): Boolean {
        val result = gameplayManager.handlePlayerDeath(player, playerToInstance)
        if (result) {
            // Player is now a spectator — update tab visibility so they're filtered correctly
            plugin.teamsListener.onPlayerLeaveGame(player)
        }
        return result
    }

    fun getEliminatedRespawnLocation(uuid: UUID): org.bukkit.Location? {
        return gameplayManager.getEliminatedRespawnLocation(uuid)
    }

    fun handleEliminatedPlayerRespawn(player: Player): Boolean {
        return gameplayManager.handleEliminatedPlayerRespawn(player)
    }

    fun clearEliminatedState(uuid: UUID) {
        gameplayManager.clearEliminatedState(uuid)
    }

    fun endGameByTimer(instance: GameInstance) {
        gameplayManager.endGameByTimer(instance, playerToInstance)
    }

    fun giveItemsToPlayers(instance: GameInstance) {
        gameplayManager.giveItemsToPlayers(instance)
    }

    fun getPlayerInstance(player: Player): GameInstance? {
        return playerToInstance[player.uniqueId]
    }

    /**
     * Returns the game instance in which this player is a spectator (i.e. they were eliminated).
     * Unlike getPlayerInstance(), this looks at the spectators list rather than playerToInstance.
     */
    fun getSpectatorInstance(player: Player): GameInstance? {
        return instances.find { it.spectators.contains(player.uniqueId) }
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
