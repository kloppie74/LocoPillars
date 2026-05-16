package org.Kloppie74.locoPillars.editor.model

/**
 * Represents a single player spawn point within a team configuration.
 *
 * @param teamKey  YAML key identifying the team (e.g. "Team1").
 * @param teamName Human-readable display name of the team (e.g. "&c[Red]").
 * @param playerKey Sub-key for this spawn position (e.g. "Player1").
 * @param x World X coordinate.
 * @param y World Y coordinate.
 * @param z World Z coordinate.
 * @param yaw Player yaw rotation.
 * @param pitch Player pitch rotation.
 * @param cageRotation Cage rotation value used during countdown phase.
 */
data class SpawnPointData(
    val teamKey: String,
    val teamName: String,
    val playerKey: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float = 0f,
    val pitch: Float = 0f,
    val cageRotation: Int = 0
) {
    /** Returns a short description string used in log/chat messages. */
    fun summary(): String = "$teamKey/$playerKey @ (${x.toInt()}, ${y.toInt()}, ${z.toInt()})"
}
