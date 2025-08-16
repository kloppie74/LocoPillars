package org.Kloppie74.locoPillars.game.manager

import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.arena.ArenaManager
import org.Kloppie74.locoPillars.game.arena.ArenaStatus
import org.Kloppie74.locoPillars.game.data.GameState

class GameInstanceManager(
    private val plugin: LocoPillars,
    private val instances: MutableList<GameInstance>
) {

    fun preloadInstances(arenaManager: ArenaManager) {
        plugin.logger.info("[GameInstanceManager] Starting preload of game instances...")
        plugin.logger.info("[GameInstanceManager] Available arena instances: ${arenaManager.getAllInstances().size}")

        // Create GameInstance wrappers for each ArenaInstance
        arenaManager.getAllInstances().forEach { arenaInstance ->
            plugin.logger.info("[GameInstanceManager] Creating game instance for arena: ${arenaInstance.id}")
            plugin.logger.info("[GameInstanceManager] Arena template: ${arenaInstance.template.name}")
            plugin.logger.info("[GameInstanceManager] Arena status: ${arenaInstance.status}")
            plugin.logger.info("[GameInstanceManager] Arena world: ${arenaInstance.world.name}")
            plugin.logger.info("[GameInstanceManager] Arena origin: ${arenaInstance.origin}")

            val gameInstance = GameInstance(
                id = arenaInstance.id,
                arenaInstance = arenaInstance
            )

            instances.add(gameInstance)
            plugin.logger.info("[GameInstanceManager] Game instance created: ${gameInstance.id}")
        }

        plugin.logger.info("[GameInstanceManager] Preloaded ${instances.size} game instances")
        plugin.logger.info("[GameInstanceManager] Game instances by type:")

        instances.groupBy { it.arenaInstance.template.name }.forEach { (type, instanceList) ->
            plugin.logger.info("[GameInstanceManager]   - $type: ${instanceList.size} instances")
        }
    }

    fun getAvailableInstance(gameTypeName: String): GameInstance? {
        plugin.logger.info("[GameInstanceManager] Looking for available instance of game type: $gameTypeName")

        // FIXED: Allow joining instances in WAITING or STARTING state (as long as they're not full)
        val availableInstances = instances.filter {
            it.arenaInstance.template.name == gameTypeName &&
                    (it.state == GameState.WAITING || it.state == GameState.STARTING) &&
                    (it.arenaInstance.status == ArenaStatus.AVAILABLE ||
                            (it.arenaInstance.status == ArenaStatus.OCCUPIED && it.players.isNotEmpty())) &&
                    it.players.size < it.getMaxPlayers()
        }

        plugin.logger.info("[GameInstanceManager] Found ${availableInstances.size} instances of type $gameTypeName")

        availableInstances.forEach { instance ->
            plugin.logger.info("[GameInstanceManager]   - Instance ${instance.id}: state=${instance.state}, arena_status=${instance.arenaInstance.status}, players=${instance.players.size}/${instance.getMaxPlayers()}")
        }

        // PRIORITY 1: Find instances that already have players but are not full (WAITING or STARTING)
        val instanceWithPlayers = availableInstances
            .filter { it.players.isNotEmpty() && it.players.size < it.getMaxPlayers() }
            .sortedWith(compareBy<GameInstance> { it.state != GameState.STARTING }.thenByDescending { it.players.size })
            .firstOrNull()

        if (instanceWithPlayers != null) {
            plugin.logger.info("[GameInstanceManager] Selected instance WITH PLAYERS: ${instanceWithPlayers.id} (${instanceWithPlayers.players.size}/${instanceWithPlayers.getMaxPlayers()} players, state: ${instanceWithPlayers.state})")
            return instanceWithPlayers
        }

        // PRIORITY 2: Only if no instances with players exist, create a new game (WAITING state only)
        val emptyInstance = availableInstances
            .filter { it.players.isEmpty() && it.state == GameState.WAITING && it.arenaInstance.status == ArenaStatus.AVAILABLE }
            .firstOrNull()

        if (emptyInstance != null) {
            plugin.logger.info("[GameInstanceManager] Selected empty instance: ${emptyInstance.id}")
        } else {
            plugin.logger.warning("[GameInstanceManager] No available instance found for: $gameTypeName")
        }

        return emptyInstance
    }
}