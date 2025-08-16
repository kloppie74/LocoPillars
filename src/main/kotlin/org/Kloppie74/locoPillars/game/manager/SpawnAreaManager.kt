package org.Kloppie74.locoPillars.game.manager

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.scheduler.BukkitRunnable
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import kotlin.math.max
import kotlin.math.min

class SpawnAreaManager(private val plugin: LocoPillars) {

    fun clearSpawnArea(instance: GameInstance) {
        plugin.logger.info("[SpawnAreaManager] Clearing spawn area for instance ${instance.id}")

        val spawnAreaData = instance.arenaInstance.config.getSpawnArea(instance.arenaInstance.origin)
        if (spawnAreaData == null) {
            plugin.logger.info("[SpawnAreaManager] No spawn area defined for ${instance.arenaInstance.config.name}, skipping clear")
            return
        }

        val (loc1, loc2) = spawnAreaData

        // Calculate the bounding box
        val minX = min(loc1.blockX, loc2.blockX)
        val maxX = max(loc1.blockX, loc2.blockX)
        val minY = min(loc1.blockY, loc2.blockY)
        val maxY = max(loc1.blockY, loc2.blockY)
        val minZ = min(loc1.blockZ, loc2.blockZ)
        val maxZ = max(loc1.blockZ, loc2.blockZ)

        val totalBlocks = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1)
        plugin.logger.info("[SpawnAreaManager] Clearing area from ($minX, $minY, $minZ) to ($maxX, $maxY, $maxZ)")
        plugin.logger.info("[SpawnAreaManager] Total blocks to clear: $totalBlocks")

        // Clear blocks in chunks to prevent server lag
        object : BukkitRunnable() {
            private var currentY = minY
            private var blocksCleared = 0

            override fun run() {
                if (currentY > maxY) {
                    plugin.logger.info("[SpawnAreaManager] ✓ Spawn area clearing completed! Cleared $blocksCleared blocks")
                    cancel()
                    return
                }

                // Clear one Y level at a time to spread the load
                val blocksThisRun = clearYLevel(instance, minX, maxX, currentY, minZ, maxZ)
                blocksCleared += blocksThisRun

                if (blocksThisRun > 0) {
                    plugin.logger.info("[SpawnAreaManager] Cleared Y=$currentY: $blocksThisRun blocks (Total: $blocksCleared/$totalBlocks)")
                }

                currentY++
            }
        }.runTaskTimer(plugin, 0L, 1L) // Run every tick to spread load
    }

    private fun clearYLevel(
        instance: GameInstance,
        minX: Int,
        maxX: Int,
        y: Int,
        minZ: Int,
        maxZ: Int
    ): Int {
        var cleared = 0
        val world = instance.arenaInstance.world

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                val block = world.getBlockAt(x, y, z)

                // Only clear non-air blocks to optimize performance
                if (block.type != Material.AIR) {
                    block.type = Material.AIR
                    cleared++
                }
            }
        }

        return cleared
    }

    fun clearSpawnAreaInstant(instance: GameInstance) {
        plugin.logger.info("[SpawnAreaManager] Instantly clearing spawn area for instance ${instance.id}")

        val spawnAreaData = instance.arenaInstance.config.getSpawnArea(instance.arenaInstance.origin)
        if (spawnAreaData == null) {
            plugin.logger.info("[SpawnAreaManager] No spawn area defined, skipping clear")
            return
        }

        val (loc1, loc2) = spawnAreaData

        // Calculate the bounding box
        val minX = min(loc1.blockX, loc2.blockX)
        val maxX = max(loc1.blockX, loc2.blockX)
        val minY = min(loc1.blockY, loc2.blockY)
        val maxY = max(loc1.blockY, loc2.blockY)
        val minZ = min(loc1.blockZ, loc2.blockZ)
        val maxZ = max(loc1.blockZ, loc2.blockZ)

        var blocksCleared = 0
        val world = instance.arenaInstance.world

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val block = world.getBlockAt(x, y, z)
                    if (block.type != Material.AIR) {
                        block.type = Material.AIR
                        blocksCleared++
                    }
                }
            }
        }

        plugin.logger.info("[SpawnAreaManager] ✓ Instant spawn area clear completed! Cleared $blocksCleared blocks")
    }

    fun validateSpawnArea(instance: GameInstance): Boolean {
        val spawnAreaData = instance.arenaInstance.config.getSpawnArea(instance.arenaInstance.origin)
        if (spawnAreaData == null) {
            plugin.logger.warning("[SpawnAreaManager] No spawn area defined for ${instance.arenaInstance.config.name}")
            return false
        }

        val (loc1, loc2) = spawnAreaData

        // Validate that both locations are in the same world
        if (loc1.world != loc2.world) {
            plugin.logger.severe("[SpawnAreaManager] Spawn area locations are in different worlds!")
            return false
        }

        // Log the area details
        plugin.logger.info("[SpawnAreaManager] Spawn area validation for ${instance.arenaInstance.config.name}:")
        plugin.logger.info("[SpawnAreaManager]   World: ${loc1.world?.name}")
        plugin.logger.info("[SpawnAreaManager]   Corner 1: (${loc1.blockX}, ${loc1.blockY}, ${loc1.blockZ})")
        plugin.logger.info("[SpawnAreaManager]   Corner 2: (${loc2.blockX}, ${loc2.blockY}, ${loc2.blockZ})")

        val volume = kotlin.math.abs((loc1.blockX - loc2.blockX + 1) *
                (loc1.blockY - loc2.blockY + 1) *
                (loc1.blockZ - loc2.blockZ + 1))
        plugin.logger.info("[SpawnAreaManager]   Volume: $volume blocks")

        return true
    }
}