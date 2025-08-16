package org.Kloppie74.locoPillars.game.data

data class Team(
    val name: String,
    val color: TeamColor,
    val spawns: List<PlayerSpawn>
)