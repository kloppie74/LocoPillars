package org.Kloppie74.locoPillars.utils

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.EditSession
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.bukkit.BukkitPlayer
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.session.ClipboardHolder
import com.sk89q.worldedit.session.SessionManager
import com.sk89q.worldedit.world.block.BlockTypes
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import org.Kloppie74.locoPillars.config.ConfigManager
import java.io.File
import java.io.FileInputStream
import java.util.UUID

object SchematicCageHelper {

    data class CageInfo(
        val location: BlockVector3,
        val dimensions: BlockVector3,
        val world: com.sk89q.worldedit.world.World,
        val originalBlocks: Map<BlockVector3, com.sk89q.worldedit.world.block.BlockState>
    )

    private val playerCageInfo = mutableMapOf<UUID, MutableList<CageInfo>>()

    private fun debugLog(plugin: JavaPlugin, configManager: ConfigManager, message: String) {
        if (configManager.debugMode) {
            plugin.logger.info("[SchematicCageHelper] $message")
        }
    }

    private fun getPlayerWorldEditSession(playerUUID: UUID) = try {
        val player = Bukkit.getPlayer(playerUUID)
        if (player != null) {
            val wePlayer = BukkitAdapter.adapt(player)
            WorldEdit.getInstance().sessionManager.get(wePlayer)
        } else null
    } catch (e: Exception) {
        null
    }

    fun pasteCageForPlayer(
        plugin: JavaPlugin,
        configManager: ConfigManager,
        playerUUID: UUID,
        schemFile: File,
        location: Location
    ): Boolean {
        debugLog(plugin, configManager, "Pasting cage for player UUID: $playerUUID")
        debugLog(plugin, configManager, "Cage file: ${schemFile.name}")
        debugLog(plugin, configManager, "Player spawn location: ${location.world?.name} at ${location.blockX}, ${location.blockY}, ${location.blockZ}")

        try {
            if (!SchematicHelper.validateSchematicFile(plugin, configManager, schemFile)) return false
            val bWorld = location.world ?: run {
                plugin.logger.severe("[SchematicCageHelper] Location world is null!")
                return false
            }

            val weWorld = BukkitAdapter.adapt(bWorld)
            val player = Bukkit.getPlayer(playerUUID)

            if (player == null || !player.isOnline) {
                debugLog(plugin, configManager, "Player $playerUUID is not online, cannot use WorldEdit session")
                return fallbackPasteCage(plugin, configManager, playerUUID, schemFile, location)
            }

            val wePlayer = BukkitAdapter.adapt(player)
            val sessionManager = WorldEdit.getInstance().sessionManager
            val playerSession = sessionManager.get(wePlayer)

            val format = ClipboardFormats.findByFile(schemFile) ?: run {
                plugin.logger.severe("[SchematicCageHelper] Could not determine format for cage file")
                return false
            }

            val clipboard: Clipboard = FileInputStream(schemFile).use { inputStream ->
                format.getReader(inputStream).use { reader -> reader.read() }
            }

            val pasteLocation = BlockVector3.at(location.blockX, location.blockY, location.blockZ)
            debugLog(plugin, configManager, "Cage paste location (exact spawn): $pasteLocation")

            val dimensions = clipboard.dimensions
            val minPoint = pasteLocation
            val maxPoint = pasteLocation.add(dimensions.subtract(1, 1, 1))
            val affectedRegion = CuboidRegion(weWorld, minPoint, maxPoint)

            val originalBlocks = mutableMapOf<BlockVector3, com.sk89q.worldedit.world.block.BlockState>()
            WorldEdit.getInstance().newEditSession(weWorld).use { readSession ->
                for (vector in affectedRegion) {
                    originalBlocks[vector] = readSession.getBlock(vector)
                }
            }

            val editSession = sessionManager.get(wePlayer).createEditSession(wePlayer)
            try {
                val operation = ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(pasteLocation)
                    .ignoreAirBlocks(false)
                    .build()

                Operations.complete(operation)
                editSession.flushSession()

                playerSession.remember(editSession)

                debugLog(plugin, configManager, "✓ Cage pasted and remembered in WorldEdit session for UUID: $playerUUID")
                debugLog(plugin, configManager, "Player can now use //undo to remove their cage")

                val cageInfo = CageInfo(
                    location = pasteLocation,
                    dimensions = dimensions,
                    world = weWorld,
                    originalBlocks = originalBlocks
                )
                playerCageInfo.computeIfAbsent(playerUUID) { mutableListOf() }.add(cageInfo)

                debugLog(plugin, configManager, "Player $playerUUID now has ${playerCageInfo[playerUUID]?.size ?: 0} cages stored (fallback)")
                return true

            } finally {
            }

        } catch (e: Exception) {
            plugin.logger.severe("[SchematicCageHelper] Exception while pasting cage for UUID $playerUUID: ${e.message}")
            e.printStackTrace()
            return fallbackPasteCage(plugin, configManager, playerUUID, schemFile, location)
        }
    }

    private fun fallbackPasteCage(
        plugin: JavaPlugin,
        configManager: ConfigManager,
        playerUUID: UUID,
        schemFile: File,
        location: Location
    ): Boolean {
        debugLog(plugin, configManager, "Using fallback paste for UUID: $playerUUID")

        try {
            val bWorld = location.world ?: return false
            val weWorld = BukkitAdapter.adapt(bWorld)

            val format = ClipboardFormats.findByFile(schemFile) ?: return false
            val clipboard: Clipboard = FileInputStream(schemFile).use { inputStream ->
                format.getReader(inputStream).use { reader -> reader.read() }
            }

            val pasteLocation = BlockVector3.at(location.blockX, location.blockY, location.blockZ)
            val dimensions = clipboard.dimensions
            val minPoint = pasteLocation
            val maxPoint = pasteLocation.add(dimensions.subtract(1, 1, 1))
            val affectedRegion = CuboidRegion(weWorld, minPoint, maxPoint)

            val originalBlocks = mutableMapOf<BlockVector3, com.sk89q.worldedit.world.block.BlockState>()
            WorldEdit.getInstance().newEditSession(weWorld).use { readSession ->
                for (vector in affectedRegion) {
                    originalBlocks[vector] = readSession.getBlock(vector)
                }
            }

            WorldEdit.getInstance().newEditSession(weWorld).use { editSession ->
                val operation = ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(pasteLocation)
                    .ignoreAirBlocks(false)
                    .build()

                Operations.complete(operation)
                editSession.flushSession()
            }

            val cageInfo = CageInfo(
                location = pasteLocation,
                dimensions = dimensions,
                world = weWorld,
                originalBlocks = originalBlocks
            )
            playerCageInfo.computeIfAbsent(playerUUID) { mutableListOf() }.add(cageInfo)

            debugLog(plugin, configManager, "✓ Fallback cage paste successful for UUID: $playerUUID")
            return true

        } catch (e: Exception) {
            plugin.logger.severe("[SchematicCageHelper] Fallback paste failed for UUID $playerUUID: ${e.message}")
            return false
        }
    }

    fun undoCageForPlayer(plugin: JavaPlugin, configManager: ConfigManager, playerUUID: UUID): Boolean {
        debugLog(plugin, configManager, "Undoing cage for player UUID: $playerUUID")

        val playerSession = getPlayerWorldEditSession(playerUUID)
        if (playerSession != null) {
            try {
                debugLog(plugin, configManager, "Attempting WorldEdit undo with player session")

                val player = Bukkit.getPlayer(playerUUID)
                if (player != null && player.isOnline) {
                    val wePlayer = BukkitAdapter.adapt(player)

                    val editSession = playerSession.createEditSession(wePlayer)
                    try {
                        val undoEditSession = playerSession.undo(null, wePlayer)
                        if (undoEditSession != null) {
                            debugLog(plugin, configManager, "✓ WorldEdit undo successful for $playerUUID")

                            playerCageInfo[playerUUID]?.clear()
                            return true
                        } else {
                            debugLog(plugin, configManager, "WorldEdit undo returned null for $playerUUID")
                        }
                    } finally {
                        editSession.close()
                    }
                }
            } catch (e: Exception) {
                debugLog(plugin, configManager, "WorldEdit undo exception for $playerUUID: ${e.message}")
            }
        } else {
            debugLog(plugin, configManager, "No WorldEdit session found for $playerUUID")
        }

        debugLog(plugin, configManager, "Using fallback restoration for $playerUUID")
        return fallbackUndoCage(plugin, configManager, playerUUID)
    }

    private fun fallbackUndoCage(plugin: JavaPlugin, configManager: ConfigManager, playerUUID: UUID): Boolean {
        val playerCages = playerCageInfo[playerUUID]
        if (playerCages.isNullOrEmpty()) {
            debugLog(plugin, configManager, "No cages found for fallback undo of UUID: $playerUUID")
            return false
        }

        var undoSuccessCount = 0
        val totalCages = playerCages.size
        debugLog(plugin, configManager, "Fallback restoring ${totalCages} cage(s) for UUID: $playerUUID")

        playerCages.forEach { cageInfo ->
            try {
                WorldEdit.getInstance().newEditSession(cageInfo.world).use { editSession ->
                    var restoredCount = 0
                    var errorCount = 0

                    cageInfo.originalBlocks.forEach { (position, originalBlock) ->
                        try {
                            editSession.setBlock(position, originalBlock)
                            restoredCount++
                        } catch (e: Exception) {
                            errorCount++
                            debugLog(plugin, configManager, "Failed to restore block at $position: ${e.message}")
                        }
                    }

                    editSession.flushSession()
                    debugLog(plugin, configManager, "Restored $restoredCount/${cageInfo.originalBlocks.size} blocks (${errorCount} errors)")
                    if (restoredCount > 0) undoSuccessCount++
                }
            } catch (e: Exception) {
                plugin.logger.severe("[SchematicCageHelper] Failed fallback undo for UUID $playerUUID: ${e.message}")
                e.printStackTrace()
            }
        }

        debugLog(plugin, configManager, "Fallback undo completed: $undoSuccessCount/$totalCages cages restored successfully")

        playerCages.clear()
        return undoSuccessCount > 0
    }

    fun forceRemoveCageForPlayer(plugin: JavaPlugin, configManager: ConfigManager, playerUUID: UUID): Boolean {
        debugLog(plugin, configManager, "FORCE removing cages for player UUID: $playerUUID")

        val playerCages = playerCageInfo[playerUUID]
        if (playerCages.isNullOrEmpty()) {
            debugLog(plugin, configManager, "No cages found for force removal of UUID: $playerUUID")
            return false
        }

        var removedCount = 0
        playerCages.forEach { cageInfo ->
            try {
                WorldEdit.getInstance().newEditSession(cageInfo.world).use { editSession ->
                    val region = CuboidRegion(
                        cageInfo.world,
                        cageInfo.location,
                        cageInfo.location.add(cageInfo.dimensions.subtract(1, 1, 1))
                    )

                    var clearedBlocks = 0
                    for (vector in region) {
                        try {
                            editSession.setBlock(vector, BlockTypes.AIR!!.defaultState)
                            clearedBlocks++
                        } catch (_: Exception) {
                        }
                    }

                    editSession.flushSession()
                    debugLog(plugin, configManager, "Force cleared $clearedBlocks blocks")
                    removedCount++
                }
            } catch (e: Exception) {
                plugin.logger.severe("[SchematicCageHelper] Failed to force remove cage: ${e.message}")
            }
        }

        playerCages.clear()
        return removedCount > 0
    }

    fun clearCageInfoForPlayer(plugin: JavaPlugin, configManager: ConfigManager, playerUUID: UUID) {
        debugLog(plugin, configManager, "Clearing cage info for UUID: $playerUUID")
        val playerCages = playerCageInfo[playerUUID]
        if (playerCages != null) {
            debugLog(plugin, configManager, "Cleared ${playerCages.size} cage info entries for UUID: $playerUUID")
            playerCages.clear()
            playerCageInfo.remove(playerUUID)
        }
    }

    fun clearCageSessionsForPlayer(plugin: JavaPlugin, configManager: ConfigManager, playerUUID: UUID) {
        debugLog(plugin, configManager, "Clearing sessions for UUID: $playerUUID")
        clearCageInfoForPlayer(plugin, configManager, playerUUID)

        val player = Bukkit.getPlayer(playerUUID)
        if (player != null) {
            try {
                val wePlayer = BukkitAdapter.adapt(player)
                val sessionManager = WorldEdit.getInstance().sessionManager
                debugLog(plugin, configManager, "WorldEdit session will be cleared automatically for UUID: $playerUUID")
            } catch (e: Exception) {
                debugLog(plugin, configManager, "Error clearing WorldEdit session for $playerUUID: ${e.message}")
            }
        }
    }

    fun getCageSessionCount(playerUUID: UUID): Int {
        return playerCageInfo[playerUUID]?.size ?: 0
    }

    fun hasCageSessions(playerUUID: UUID): Boolean {
        return getCageSessionCount(playerUUID) > 0
    }

    fun cleanupAllCageSessions(plugin: JavaPlugin, configManager: ConfigManager) {
        debugLog(plugin, configManager, "Cleaning up all cage sessions...")
        val totalPlayers = playerCageInfo.size
        var totalCages = 0
        playerCageInfo.values.forEach { cages ->
            totalCages += cages.size
            cages.clear()
        }
        playerCageInfo.clear()
        debugLog(plugin, configManager, "✓ Cleaned up $totalCages cage sessions for $totalPlayers players")
    }
}