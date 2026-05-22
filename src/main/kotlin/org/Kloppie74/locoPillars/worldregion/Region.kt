package org.Kloppie74.locoPillars.worldregion

import org.bukkit.Location
import org.bukkit.World

/**
 * An axis-aligned cuboid region inside a single Bukkit world.
 *
 * Used by [WorldRegionManager] to enforce arena boundaries without WorldGuard.
 * All bounds are inclusive block-coordinates; containment checks add 1.0 to the
 * max so that standing on maxX/maxZ is still "inside".
 */
data class Region(
    val id: String,
    val worldName: String,
    val minX: Int,
    val minY: Int,
    val minZ: Int,
    val maxX: Int,
    val maxY: Int,
    val maxZ: Int
) {
    /** Full XYZ containment check. */
    fun contains(world: World, x: Double, y: Double, z: Double): Boolean {
        if (world.name != worldName) return false
        return x >= minX && x <= maxX + 1.0 &&
               y >= minY && y <= maxY + 1.0 &&
               z >= minZ && z <= maxZ + 1.0
    }

    fun contains(location: Location): Boolean {
        val w = location.world ?: return false
        return contains(w, location.x, location.y, location.z)
    }

    /**
     * XZ-only containment check (ignores Y).
     * Preferred for movement enforcement — players cannot naturally escape the
     * world's vertical bounds, so checking Y would only add overhead.
     */
    fun containsXZ(world: World, x: Double, z: Double): Boolean {
        if (world.name != worldName) return false
        return x >= minX && x <= maxX + 1.0 && z >= minZ && z <= maxZ + 1.0
    }

    fun containsXZ(location: Location): Boolean {
        val w = location.world ?: return false
        return containsXZ(w, location.x, location.z)
    }

    override fun toString(): String =
        "Region[$id world=$worldName ($minX,$minY,$minZ)→($maxX,$maxY,$maxZ)]"
}
