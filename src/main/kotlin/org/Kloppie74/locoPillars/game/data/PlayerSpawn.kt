package org.Kloppie74.locoPillars.game.data

data class PlayerSpawn(
    val relativeX: Double,
    val relativeY: Double,
    val relativeZ: Double,
    val yaw: Float,
    val pitch: Float,
    val cageRotation: Int
) {
    fun toAbsolute(origin: org.bukkit.Location): org.bukkit.Location {
        val loc = origin.clone()
        loc.x += relativeX
        loc.y += relativeY
        loc.z += relativeZ
        loc.yaw = yaw
        loc.pitch = pitch
        return loc
    }
}