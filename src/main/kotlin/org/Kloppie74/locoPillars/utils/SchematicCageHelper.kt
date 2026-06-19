package org.Kloppie74.locoPillars.utils

import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.plugin.java.JavaPlugin
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/**
 * Handles cage placement and removal by reading schematics via WorldEdit's clipboard
 * and writing blocks directly through the Bukkit API.
 *
 * This bypasses all WorldGuard / FAWE region restrictions entirely — plugin block-writes
 * are not subject to player-based region checks.
 */
object SchematicCageHelper {

    data class BlockSnapshot(
        val x: Int,
        val y: Int,
        val z: Int,
        val worldName: String,
        val data: org.bukkit.block.data.BlockData
    )

    private val playerCageSnapshots = mutableMapOf<UUID, MutableList<BlockSnapshot>>()

    private fun debugLog(plugin: JavaPlugin, configManager: ConfigManager, message: String) {
        if (configManager.debugMode) plugin.logger.info("[SchematicCageHelper] $message")
    }

    fun pasteCageForPlayer(
        plugin: JavaPlugin,
        configManager: ConfigManager,
        playerUUID: UUID,
        schemFile: File,
        location: Location
    ): Boolean {
        debugLog(plugin, configManager, "Pasting cage for UUID: $playerUUID at ${location.blockX},${location.blockY},${location.blockZ}")

        if (!SchematicHelper.validateSchematicFile(plugin, configManager, schemFile)) return false

        val bWorld = location.world ?: run {
            plugin.logger.severe("[SchematicCageHelper] Location world is null!")
            return false
        }

        return try {
            val format = ClipboardFormats.findByFile(schemFile) ?: run {
                plugin.logger.severe("[SchematicCageHelper] Unknown clipboard format for ${schemFile.name}")
                return false
            }

            val clipboard = FileInputStream(schemFile).use { format.getReader(it).use { r -> r.read() } }

            // WorldEdit's paste aligns clipboard.origin to the target location.
            // So: worldPos = target + (clipPos - origin)
            val origin = clipboard.origin
            val targetX = location.blockX
            val targetY = location.blockY
            val targetZ = location.blockZ

            val snapshots = mutableListOf<BlockSnapshot>()

            // Pass 1: snapshot every position in the new cage's region so we can restore
            // the original terrain later (undo).
            for (clipPos in clipboard.region) {
                val wx = targetX + (clipPos.x - origin.x)
                val wy = targetY + (clipPos.y - origin.y)
                val wz = targetZ + (clipPos.z - origin.z)
                val block = bWorld.getBlockAt(wx, wy, wz)
                snapshots.add(BlockSnapshot(wx, wy, wz, bWorld.name, block.blockData.clone()))
            }

            // Pass 2: clear the entire paste region first.
            // This removes any stale blocks from a previous cage whose undo info was lost
            // (reload / crash), preventing old cage geometry from bleeding through.
            for (snap in snapshots) {
                bWorld.getBlockAt(snap.x, snap.y, snap.z).type = Material.AIR
            }

            // Pass 3: write the cage blocks.
            for (clipPos in clipboard.region) {
                val blockState = clipboard.getBlock(clipPos)
                val wx = targetX + (clipPos.x - origin.x)
                val wy = targetY + (clipPos.y - origin.y)
                val wz = targetZ + (clipPos.z - origin.z)
                // Place directly via Bukkit — no WorldGuard events fired by plugin edits
                bWorld.getBlockAt(wx, wy, wz).setBlockData(BukkitAdapter.adapt(blockState), false)
            }

            // Replace (not accumulate) — old append bug caused snapshot lists to grow
            // across multiple games, breaking undo ordering.
            playerCageSnapshots[playerUUID] = snapshots
            debugLog(plugin, configManager, "✓ Cage pasted: ${snapshots.size} blocks written for UUID $playerUUID")
            true

        } catch (e: Exception) {
            plugin.logger.severe("[SchematicCageHelper] Exception while pasting cage for UUID $playerUUID: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    fun undoCageForPlayer(plugin: JavaPlugin, configManager: ConfigManager, playerUUID: UUID): Boolean {
        debugLog(plugin, configManager, "Undoing cage for UUID: $playerUUID")
        val snapshots = playerCageSnapshots[playerUUID]
        if (snapshots.isNullOrEmpty()) {
            debugLog(plugin, configManager, "No cage snapshots found for UUID: $playerUUID")
            return false
        }

        var restored = 0
        for (snap in snapshots) {
            val world = plugin.server.getWorld(snap.worldName) ?: continue
            try {
                world.getBlockAt(snap.x, snap.y, snap.z).setBlockData(snap.data, false)
                restored++
            } catch (e: Exception) {
                debugLog(plugin, configManager, "Failed to restore block at ${snap.x},${snap.y},${snap.z}: ${e.message}")
            }
        }

        snapshots.clear()
        playerCageSnapshots.remove(playerUUID)
        debugLog(plugin, configManager, "✓ Cage undo: $restored blocks restored for UUID $playerUUID")
        return restored > 0
    }

    fun forceRemoveCageForPlayer(plugin: JavaPlugin, configManager: ConfigManager, playerUUID: UUID): Boolean {
        debugLog(plugin, configManager, "Force removing cage for UUID: $playerUUID")
        val snapshots = playerCageSnapshots[playerUUID]
        if (snapshots.isNullOrEmpty()) {
            debugLog(plugin, configManager, "No cage snapshots for force removal of UUID: $playerUUID")
            return false
        }

        var cleared = 0
        for (snap in snapshots) {
            val world = plugin.server.getWorld(snap.worldName) ?: continue
            try {
                world.getBlockAt(snap.x, snap.y, snap.z).type = Material.AIR
                cleared++
            } catch (e: Exception) {
                debugLog(plugin, configManager, "Failed to clear block at ${snap.x},${snap.y},${snap.z}: ${e.message}")
            }
        }

        snapshots.clear()
        playerCageSnapshots.remove(playerUUID)
        debugLog(plugin, configManager, "✓ Force removal: $cleared blocks cleared for UUID $playerUUID")
        return cleared > 0
    }

    fun clearCageInfoForPlayer(plugin: JavaPlugin, configManager: ConfigManager, playerUUID: UUID) {
        debugLog(plugin, configManager, "Clearing cage info for UUID: $playerUUID")
        val removed = playerCageSnapshots.remove(playerUUID)
        if (removed != null) debugLog(plugin, configManager, "Cleared ${removed.size} snapshot entries for UUID: $playerUUID")
    }

    // Kept for API compatibility — snapshots are self-contained, no separate sessions needed.
    fun clearCageSessionsForPlayer(plugin: JavaPlugin, configManager: ConfigManager, playerUUID: UUID) {
        clearCageInfoForPlayer(plugin, configManager, playerUUID)
    }

    fun getCageSessionCount(playerUUID: UUID): Int = playerCageSnapshots[playerUUID]?.size ?: 0

    fun hasCageSessions(playerUUID: UUID): Boolean = playerCageSnapshots[playerUUID]?.isNotEmpty() == true

    fun cleanupAllCageSessions(plugin: JavaPlugin, configManager: ConfigManager) {
        debugLog(plugin, configManager, "Cleaning up all cage sessions...")
        val totalPlayers = playerCageSnapshots.size
        var totalSnapshots = 0
        playerCageSnapshots.values.forEach { totalSnapshots += it.size; it.clear() }
        playerCageSnapshots.clear()
        debugLog(plugin, configManager, "✓ Cleaned up $totalSnapshots snapshots for $totalPlayers players")
    }
}
