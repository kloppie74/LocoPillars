package org.Kloppie74.locoPillars.worldregion

import org.bukkit.Location
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.arena.ArenaInstance
import java.util.concurrent.ConcurrentHashMap

/**
 * Drop-in replacement for WorldGuard's region system.
 *
 * Registers axis-aligned cuboid regions per arena instance and allows
 * fast point-in-region queries that the [WorldRegionListener] uses to
 * enforce arena boundaries without any external dependency.
 *
 * Thread-safe: uses [ConcurrentHashMap] for the region registry so that
 * the async schematic preload can register regions off the main thread.
 */
class WorldRegionManager(private val plugin: LocoPillars) {

    private val regions = ConcurrentHashMap<String, Region>()

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Register (or replace) a region.
     */
    fun registerRegion(region: Region) {
        regions[region.id] = region
        debugLog("Registered ${region}")
    }

    /**
     * Convenience factory: creates a region from an [ArenaInstance] and registers it.
     *
     * Bounds are origin ± (instanceDistance / 2) on XZ; full world height on Y.
     * The `instanceDistance` value is taken from [ConfigManager] so that
     * config changes are respected without recompiling.
     */
    fun registerArenaRegion(instance: ArenaInstance): Region {
        val halfDist = plugin.configManager.instanceDistance / 2
        val origin   = instance.origin
        val world    = instance.world

        val region = Region(
            id        = instance.regionId,
            worldName = world.name,
            minX      = origin.blockX - halfDist,
            minY      = world.minHeight,
            minZ      = origin.blockZ - halfDist,
            maxX      = origin.blockX + halfDist,
            maxY      = world.maxHeight,
            maxZ      = origin.blockZ + halfDist
        )
        registerRegion(region)
        return region
    }

    // -------------------------------------------------------------------------
    // Removal
    // -------------------------------------------------------------------------

    fun removeRegion(id: String) {
        val removed = regions.remove(id)
        if (removed != null) debugLog("Removed region '$id'")
    }

    /**
     * Remove all regions that belong to the given world.
     * Called when a game world is torn down.
     */
    fun removeRegionsInWorld(worldName: String) {
        val toRemove = regions.values.filter { it.worldName == worldName }.map { it.id }
        toRemove.forEach { regions.remove(it) }
        debugLog("Removed ${toRemove.size} region(s) for world '$worldName'")
    }

    // -------------------------------------------------------------------------
    // Queries
    // -------------------------------------------------------------------------

    fun getRegion(id: String): Region? = regions[id]

    /**
     * Returns every registered region whose XZ bounds contain [location].
     */
    fun getRegionsAt(location: Location): List<Region> {
        val world = location.world ?: return emptyList()
        return regions.values.filter { it.containsXZ(world, location.x, location.z) }
    }

    fun getAllRegions(): Collection<Region> = regions.values

    fun getRegionCount(): Int = regions.size

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    /** Remove all regions. Called from [LocoPillars.onDisable]. */
    fun clearAll() {
        val count = regions.size
        regions.clear()
        debugLog("Cleared $count region(s)")
    }

    // -------------------------------------------------------------------------

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[WorldRegionManager] $message")
        }
    }
}
