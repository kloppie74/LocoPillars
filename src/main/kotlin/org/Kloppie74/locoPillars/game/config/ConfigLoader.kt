package org.Kloppie74.locoPillars.game.config

import org.Kloppie74.locoPillars.game.data.Team
import org.Kloppie74.locoPillars.game.data.PlayerSpawn
import org.Kloppie74.locoPillars.game.data.TeamColor

object ConfigLoader {
    fun parseTeams(spawnLocs: Map<String, Any>): List<Team> {
        return spawnLocs.filterKeys { it.startsWith("Team") }.map { (teamKey, teamData) ->
            val teamMap = teamData as? Map<String, Any> ?: emptyMap()
            val name = (teamMap["Name"] as? String) ?: teamKey
            val colorStr = (teamMap["Color"] as? String) ?: "GRAY"
            val color = runCatching { TeamColor.valueOf(colorStr.uppercase()) }.getOrElse { TeamColor.GRAY }

            val spawns = teamMap.filterKeys { it.startsWith("Player") }.values.map { playerData ->
                val playerMap = playerData as? Map<String, Any> ?: emptyMap()
                PlayerSpawn(
                    relativeX = (playerMap["X"] as? Number)?.toDouble() ?: 0.0,
                    relativeY = (playerMap["Y"] as? Number)?.toDouble() ?: 0.0,
                    relativeZ = (playerMap["Z"] as? Number)?.toDouble() ?: 0.0,
                    yaw = (playerMap["Yaw"] as? Number)?.toFloat() ?: 0f,
                    pitch = (playerMap["Pitch"] as? Number)?.toFloat() ?: 0f,
                    cageRotation = (playerMap["Cage-Rotation"] as? Number)?.toInt() ?: 0
                )
            }

            Team(name, color, spawns)
        }
    }
}