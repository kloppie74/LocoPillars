package org.Kloppie74.locoPillars.game.instance

import org.bukkit.Location
import org.Kloppie74.locoPillars.game.arena.ArenaInstance
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.game.data.Team
import org.Kloppie74.locoPillars.utils.Placeholders
import java.util.*

class GameInstance(
    val id: UUID,
    val arenaInstance: ArenaInstance,
    var state: GameState = GameState.WAITING,
    var players: MutableList<UUID> = mutableListOf(),
    val spectators: MutableList<UUID> = mutableListOf()
) {

    // Enhanced team tracking system
    private val originalTeamAssignments = mutableMapOf<UUID, Team>()
    private val allOriginalPlayers = mutableListOf<UUID>()
    private val teamPlayerCounts = mutableMapOf<Team, MutableList<UUID>>()

    fun trackTeamAssignment(playerId: UUID, team: Team) {
        originalTeamAssignments[playerId] = team
        if (!allOriginalPlayers.contains(playerId)) {
            allOriginalPlayers.add(playerId)
        }

        // Track players per team
        val teamPlayers = teamPlayerCounts.getOrPut(team) { mutableListOf() }
        if (!teamPlayers.contains(playerId)) {
            teamPlayers.add(playerId)
        }
    }

    fun getPlayerTeam(playerId: UUID): Team? {
        return originalTeamAssignments[playerId]
    }

    fun getPlayersInTeam(team: Team): List<UUID> {
        return teamPlayerCounts[team]?.toList() ?: emptyList()
    }

    fun getAlivePlayersInTeam(team: Team): List<UUID> {
        return getPlayersInTeam(team).filter { it in players }
    }

    fun getDeadPlayersInTeam(team: Team): List<UUID> {
        return getPlayersInTeam(team).filter { it !in players }
    }

    fun getAllTeams(): List<Team> {
        return arenaInstance.config.teams
    }

    fun getTeamStatistics(team: Team): TeamStatistics {
        val totalPlayers = getPlayersInTeam(team).size
        val alivePlayers = getAlivePlayersInTeam(team).size
        val deadPlayers = getDeadPlayersInTeam(team).size

        val status = when {
            totalPlayers == 0 -> "Empty"
            alivePlayers == 0 -> "Eliminated"
            alivePlayers == totalPlayers -> "Alive"
            else -> "Partial"
        }

        val statusSymbol = when {
            totalPlayers == 0 -> "⚪"
            alivePlayers == 0 -> "❌"
            alivePlayers == totalPlayers -> "✅"
            else -> "⚠️"
        }

        return TeamStatistics(totalPlayers, alivePlayers, deadPlayers, status, statusSymbol)
    }

    fun getTeamByIdentifier(identifier: String): Team? {
        val teams = getAllTeams()

        return if (identifier.startsWith("team") && identifier.length > 4) {
            // Handle team1, team2, etc.
            val teamNumber = identifier.removePrefix("team").toIntOrNull()
            if (teamNumber != null && teamNumber > 0 && teamNumber <= teams.size) {
                teams[teamNumber - 1] // Convert to 0-based index
            } else null
        } else {
            // Handle actual team names
            teams.find { it.name.lowercase() == identifier.lowercase() }
        }
    }

    fun getRemainingTeams(): List<Team> {
        return getAllTeams().filter { team ->
            getAlivePlayersInTeam(team).isNotEmpty()
        }
    }

    fun isTeamEliminated(team: Team): Boolean {
        return getAlivePlayersInTeam(team).isEmpty() && getPlayersInTeam(team).isNotEmpty()
    }

    fun removePlayerFromTeam(playerId: UUID) {
        val team = originalTeamAssignments[playerId]
        if (team != null) {
            teamPlayerCounts[team]?.remove(playerId)
        }
    }

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
        spectators.clear()

        // Clear team tracking data
        originalTeamAssignments.clear()
        allOriginalPlayers.clear()
        teamPlayerCounts.clear()

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

/**
 * Data class to hold team statistics
 */
data class TeamStatistics(
    val totalPlayers: Int,
    val alivePlayers: Int,
    val deadPlayers: Int,
    val status: String,
    val statusSymbol: String
)