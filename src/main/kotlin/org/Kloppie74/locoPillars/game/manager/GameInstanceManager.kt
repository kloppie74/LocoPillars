package org.Kloppie74.locoPillars.game.manager

import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.arena.ArenaManager
import org.Kloppie74.locoPillars.game.arena.ArenaStatus
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.config.ConfigManager

class GameInstanceManager(
    private val plugin: LocoPillars,
    private val instances: MutableList<GameInstance>,
    private val configManager: ConfigManager // <-- Toegevoegd
) {

    private fun debugLog(message: String) {
        if (configManager.debugMode) {
            plugin.logger.info("[LocoPillars] [GameInstanceManager] $message")
        }
    }

    fun preloadInstances(arenaManager: ArenaManager) {
        debugLog("Starting preload of game instances...")
        debugLog("Available arena instances: ${arenaManager.getAllInstances().size}")

        arenaManager.getAllInstances().forEach { arenaInstance ->
            debugLog("Creating game instance for arena: ${arenaInstance.id}")
            debugLog("Arena template: ${arenaInstance.template.name}")
            debugLog("Arena status: ${arenaInstance.status}")
            debugLog("Arena world: ${arenaInstance.world.name}")
            debugLog("Arena origin: ${arenaInstance.origin}")

            val gameInstance = GameInstance(
                id = arenaInstance.id,
                arenaInstance = arenaInstance
            )

            instances.add(gameInstance)
            debugLog("Game instance created: ${gameInstance.id}")
        }

        debugLog("Preloaded ${instances.size} game instances")
        debugLog("Game instances by type:")

        instances.groupBy { it.arenaInstance.template.name }.forEach { (type, instanceList) ->
            debugLog("  - $type: ${instanceList.size} instances")
        }
    }

    fun getAvailableInstance(gameTypeName: String): GameInstance? {
        debugLog("Looking for available instance of game type: $gameTypeName")

        val availableInstances = instances.filter {
            it.arenaInstance.template.name == gameTypeName &&
                    (it.state == GameState.WAITING || it.state == GameState.STARTING) &&
                    (it.arenaInstance.status == ArenaStatus.AVAILABLE ||
                            (it.arenaInstance.status == ArenaStatus.OCCUPIED && it.players.isNotEmpty())) &&
                    it.players.size < it.getMaxPlayers()
        }

        debugLog("Found ${availableInstances.size} instances of type $gameTypeName")

        availableInstances.forEach { instance ->
            debugLog("  - Instance ${instance.id}: state=${instance.state}, arena_status=${instance.arenaInstance.status}, players=${instance.players.size}/${instance.getMaxPlayers()}")
        }

        val instanceWithPlayers = availableInstances
            .filter { it.players.isNotEmpty() && it.players.size < it.getMaxPlayers() }
            .sortedWith(compareBy<GameInstance> { it.state != GameState.STARTING }.thenByDescending { it.players.size })
            .firstOrNull()

        if (instanceWithPlayers != null) {
            debugLog("Selected instance WITH PLAYERS: ${instanceWithPlayers.id} (${instanceWithPlayers.players.size}/${instanceWithPlayers.getMaxPlayers()} players, state: ${instanceWithPlayers.state})")
            return instanceWithPlayers
        }

        val emptyInstance = availableInstances
            .filter { it.players.isEmpty() && it.state == GameState.WAITING && it.arenaInstance.status == ArenaStatus.AVAILABLE }
            .firstOrNull()

        if (emptyInstance != null) {
            debugLog("Selected empty instance: ${emptyInstance.id}")
        } else {
            debugLog("No available instance found for: $gameTypeName")
        }

        return emptyInstance
    }

    /**
     * Like [getAvailableInstance] but the returned instance must have at least
     * [minFreeSlots] open player slots.  Used by the party join flow so that all
     * party members can be placed in a single game.
     */
    fun getAvailableInstanceWithSlots(gameTypeName: String, minFreeSlots: Int): GameInstance? {
        debugLog("Looking for instance of type $gameTypeName with at least $minFreeSlots free slots")

        val candidates = instances.filter {
            it.arenaInstance.template.name == gameTypeName &&
            (it.state == GameState.WAITING || it.state == GameState.STARTING) &&
            (it.arenaInstance.status == ArenaStatus.AVAILABLE ||
                    (it.arenaInstance.status == ArenaStatus.OCCUPIED && it.players.isNotEmpty())) &&
            (it.getMaxPlayers() - it.players.size) >= minFreeSlots
        }

        debugLog("Found ${candidates.size} candidate(s) with ≥$minFreeSlots free slots")

        // Prefer instances that already have players (pack players together), but must have enough room
        return candidates
            .filter { it.players.isNotEmpty() }
            .sortedWith(compareBy<GameInstance> { it.state != GameState.STARTING }
                .thenByDescending { it.players.size })
            .firstOrNull()
            ?: candidates.filter { it.players.isEmpty() && it.state == GameState.WAITING }.firstOrNull()
    }
}