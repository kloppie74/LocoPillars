package org.Kloppie74.locoPillars.game.instance

import org.bukkit.Location
import org.Kloppie74.locoPillars.game.arena.ArenaInstance
import org.Kloppie74.locoPillars.game.data.GameState
import java.util.*

class GameInstance(
    val id: UUID,
    val arenaInstance: ArenaInstance,
    var state: GameState = GameState.WAITING,
    var players: MutableList<UUID> = mutableListOf()
) {
    fun getTeamSpawns(): Map<String, List<Location>> {
        // Returns per team a list of absolute spawn locations for this instance
        val origin = arenaInstance.origin
        return arenaInstance.config.teams.associate { team ->
            team.name to team.spawns.map { it.toAbsolute(origin) }
        }
    }

    fun getMaxPlayers(): Int = arenaInstance.config.getMaxPlayers()
    fun getMinPlayers(): Int = arenaInstance.config.getMinPlayers()
    fun getDisplayName(): String = arenaInstance.config.getDisplayName()

    fun reset() {
        state = GameState.RESETTING
        players.clear()
        // ArenaManager will handle the actual arena reset
        state = GameState.WAITING
    }

    // FIXED: Allow joining during STARTING state as well
    fun canJoin(): Boolean {
        return (state == GameState.WAITING || state == GameState.STARTING) &&
                players.size < getMaxPlayers() &&
                (arenaInstance.status == org.Kloppie74.locoPillars.game.arena.ArenaStatus.AVAILABLE ||
                        arenaInstance.status == org.Kloppie74.locoPillars.game.arena.ArenaStatus.OCCUPIED)
    }

    fun canStart(): Boolean {
        return state == GameState.WAITING && players.size >= getMinPlayers()
    }
}