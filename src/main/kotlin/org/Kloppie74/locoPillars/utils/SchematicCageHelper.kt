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
import java.io.File
import java.io.FileInputStream
import java.util.UUID

object SchematicCageHelper {

    // Cage info voor fallback undo
    data class CageInfo(
        val location: BlockVector3,
        val dimensions: BlockVector3,
        val world: com.sk89q.worldedit.world.World,
        val originalBlocks: Map<BlockVector3, com.sk89q.worldedit.world.block.BlockState>
    )

    // Fallback cage info per speler (voor als WorldEdit undo faalt)
    private val playerCageInfo = mutableMapOf<UUID, MutableList<CageInfo>>()

    /**
     * Krijg de WorldEdit Session van een speler via de officiele SessionManager
     */
    private fun getPlayerWorldEditSession(playerUUID: UUID) = try {
        val player = Bukkit.getPlayer(playerUUID)
        if (player != null) {
            val wePlayer = BukkitAdapter.adapt(player)
            WorldEdit.getInstance().sessionManager.get(wePlayer)
        } else null
    } catch (e: Exception) {
        null
    }

    /**
     * Plaats cage voor specifieke speler via de ECHTE WorldEdit Session
     */
    fun pasteCageForPlayer(
        plugin: JavaPlugin,
        playerUUID: UUID,
        schemFile: File,
        location: Location
    ): Boolean {
        plugin.logger.info("[SchematicCageHelper] Pasting cage for player UUID: $playerUUID")
        plugin.logger.info("[SchematicCageHelper] Cage file: ${schemFile.name}")
        plugin.logger.info("[SchematicCageHelper] Player spawn location: ${location.world?.name} at ${location.blockX}, ${location.blockY}, ${location.blockZ}")

        try {
            if (!SchematicHelper.validateSchematicFile(plugin, schemFile)) return false
            val bWorld = location.world ?: run {
                plugin.logger.severe("[SchematicCageHelper] Location world is null!")
                return false
            }

            val weWorld = BukkitAdapter.adapt(bWorld)
            val player = Bukkit.getPlayer(playerUUID)

            if (player == null || !player.isOnline) {
                plugin.logger.warning("[SchematicCageHelper] Player $playerUUID is not online, cannot use WorldEdit session")
                return fallbackPasteCage(plugin, playerUUID, schemFile, location)
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
            plugin.logger.info("[SchematicCageHelper] Cage paste location (exact spawn): $pasteLocation")

            // Bepaal affected region voor fallback
            val dimensions = clipboard.dimensions
            val minPoint = pasteLocation
            val maxPoint = pasteLocation.add(dimensions.subtract(1, 1, 1))
            val affectedRegion = CuboidRegion(weWorld, minPoint, maxPoint)

            // Store originals voor fallback
            val originalBlocks = mutableMapOf<BlockVector3, com.sk89q.worldedit.world.block.BlockState>()
            WorldEdit.getInstance().newEditSession(weWorld).use { readSession ->
                for (vector in affectedRegion) {
                    originalBlocks[vector] = readSession.getBlock(vector)
                }
            }

            // CRUCIALE STAP: Paste met de ECHTE WorldEdit Session van de speler
            val editSession = sessionManager.get(wePlayer).createEditSession(wePlayer)
            try {
                val operation = ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(pasteLocation)
                    .ignoreAirBlocks(false)
                    .build()

                Operations.complete(operation)
                editSession.flushSession()

                // BELANGRIJK: Remember deze editSession in de player session
                // Dit zorgt ervoor dat //undo werkt!
                playerSession.remember(editSession)

                plugin.logger.info("[SchematicCageHelper] ✓ Cage pasted and remembered in WorldEdit session for UUID: $playerUUID")
                plugin.logger.info("[SchematicCageHelper] Player can now use //undo to remove their cage")

                // Fallback info bewaren voor als WorldEdit undo niet werkt
                val cageInfo = CageInfo(
                    location = pasteLocation,
                    dimensions = dimensions,
                    world = weWorld,
                    originalBlocks = originalBlocks
                )
                playerCageInfo.computeIfAbsent(playerUUID) { mutableListOf() }.add(cageInfo)

                plugin.logger.info("[SchematicCageHelper] Player $playerUUID now has ${playerCageInfo[playerUUID]?.size ?: 0} cages stored (fallback)")
                return true

            } finally {
                // EditSession wordt hier niet gesloten omdat het remembered is
                // De SessionManager beheert de lifecycle
            }

        } catch (e: Exception) {
            plugin.logger.severe("[SchematicCageHelper] Exception while pasting cage for UUID $playerUUID: ${e.message}")
            e.printStackTrace()
            return fallbackPasteCage(plugin, playerUUID, schemFile, location)
        }
    }

    /**
     * Fallback paste voor als WorldEdit session niet beschikbaar is
     */
    private fun fallbackPasteCage(
        plugin: JavaPlugin,
        playerUUID: UUID,
        schemFile: File,
        location: Location
    ): Boolean {
        plugin.logger.info("[SchematicCageHelper] Using fallback paste for UUID: $playerUUID")

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

            // Store originals
            val originalBlocks = mutableMapOf<BlockVector3, com.sk89q.worldedit.world.block.BlockState>()
            WorldEdit.getInstance().newEditSession(weWorld).use { readSession ->
                for (vector in affectedRegion) {
                    originalBlocks[vector] = readSession.getBlock(vector)
                }
            }

            // Paste
            WorldEdit.getInstance().newEditSession(weWorld).use { editSession ->
                val operation = ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(pasteLocation)
                    .ignoreAirBlocks(false)
                    .build()

                Operations.complete(operation)
                editSession.flushSession()
            }

            // Fallback info bewaren
            val cageInfo = CageInfo(
                location = pasteLocation,
                dimensions = dimensions,
                world = weWorld,
                originalBlocks = originalBlocks
            )
            playerCageInfo.computeIfAbsent(playerUUID) { mutableListOf() }.add(cageInfo)

            plugin.logger.info("[SchematicCageHelper] ✓ Fallback cage paste successful for UUID: $playerUUID")
            return true

        } catch (e: Exception) {
            plugin.logger.severe("[SchematicCageHelper] Fallback paste failed for UUID $playerUUID: ${e.message}")
            return false
        }
    }

    /**
     * Undo cage voor speler via WorldEdit Session
     */
    fun undoCageForPlayer(plugin: JavaPlugin, playerUUID: UUID): Boolean {
        plugin.logger.info("[SchematicCageHelper] Undoing cage for player UUID: $playerUUID")

        // STAP 1: Probeer WorldEdit undo via de echte Session
        val playerSession = getPlayerWorldEditSession(playerUUID)
        if (playerSession != null) {
            try {
                plugin.logger.info("[SchematicCageHelper] Attempting WorldEdit undo with player session")

                val player = Bukkit.getPlayer(playerUUID)
                if (player != null && player.isOnline) {
                    val wePlayer = BukkitAdapter.adapt(player)

                    // Probeer undo via SessionManager
                    val editSession = playerSession.createEditSession(wePlayer)
                    try {
                        val undoEditSession = playerSession.undo(null, wePlayer)
                        if (undoEditSession != null) {
                            plugin.logger.info("[SchematicCageHelper] ✓ WorldEdit undo successful for $playerUUID")

                            // Clear fallback na succesvolle undo
                            playerCageInfo[playerUUID]?.clear()
                            return true
                        } else {
                            plugin.logger.warning("[SchematicCageHelper] WorldEdit undo returned null for $playerUUID")
                        }
                    } finally {
                        editSession.close()
                    }
                }
            } catch (e: Exception) {
                plugin.logger.warning("[SchematicCageHelper] WorldEdit undo exception for $playerUUID: ${e.message}")
            }
        } else {
            plugin.logger.info("[SchematicCageHelper] No WorldEdit session found for $playerUUID")
        }

        // STAP 2: Fallback - restore originals
        plugin.logger.info("[SchematicCageHelper] Using fallback restoration for $playerUUID")
        return fallbackUndoCage(plugin, playerUUID)
    }

    /**
     * Fallback undo door originele blokken terug te zetten
     */
    private fun fallbackUndoCage(plugin: JavaPlugin, playerUUID: UUID): Boolean {
        val playerCages = playerCageInfo[playerUUID]
        if (playerCages.isNullOrEmpty()) {
            plugin.logger.warning("[SchematicCageHelper] No cages found for fallback undo of UUID: $playerUUID")
            return false
        }

        var undoSuccessCount = 0
        val totalCages = playerCages.size
        plugin.logger.info("[SchematicCageHelper] Fallback restoring ${totalCages} cage(s) for UUID: $playerUUID")

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
                            plugin.logger.warning("[SchematicCageHelper] Failed to restore block at $position: ${e.message}")
                        }
                    }

                    editSession.flushSession()
                    plugin.logger.info("[SchematicCageHelper] Restored $restoredCount/${cageInfo.originalBlocks.size} blocks (${errorCount} errors)")
                    if (restoredCount > 0) undoSuccessCount++
                }
            } catch (e: Exception) {
                plugin.logger.severe("[SchematicCageHelper] Failed fallback undo for UUID $playerUUID: ${e.message}")
                e.printStackTrace()
            }
        }

        plugin.logger.info("[SchematicCageHelper] Fallback undo completed: $undoSuccessCount/$totalCages cages restored successfully")

        // Clear na fallback
        playerCages.clear()
        return undoSuccessCount > 0
    }

    /**
     * Force remove - zet alle cage blokken naar air
     */
    fun forceRemoveCageForPlayer(plugin: JavaPlugin, playerUUID: UUID): Boolean {
        plugin.logger.info("[SchematicCageHelper] FORCE removing cages for player UUID: $playerUUID")

        val playerCages = playerCageInfo[playerUUID]
        if (playerCages.isNullOrEmpty()) {
            plugin.logger.warning("[SchematicCageHelper] No cages found for force removal of UUID: $playerUUID")
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
                            // ignore
                        }
                    }

                    editSession.flushSession()
                    plugin.logger.info("[SchematicCageHelper] Force cleared $clearedBlocks blocks")
                    removedCount++
                }
            } catch (e: Exception) {
                plugin.logger.severe("[SchematicCageHelper] Failed to force remove cage: ${e.message}")
            }
        }

        // Clear info na force remove
        playerCages.clear()
        return removedCount > 0
    }

    /**
     * Clear alle cage info voor een speler (fallback data)
     */
    fun clearCageInfoForPlayer(plugin: JavaPlugin, playerUUID: UUID) {
        plugin.logger.info("[SchematicCageHelper] Clearing cage info for UUID: $playerUUID")
        val playerCages = playerCageInfo[playerUUID]
        if (playerCages != null) {
            plugin.logger.info("[SchematicCageHelper] Cleared ${playerCages.size} cage info entries for UUID: $playerUUID")
            playerCages.clear()
            playerCageInfo.remove(playerUUID)
        }
        // BELANGRIJK: WorldEdit session wordt beheerd door WorldEdit zelf
    }

    /**
     * Clear session EN info voor een speler (bij disconnect)
     */
    fun clearCageSessionsForPlayer(plugin: JavaPlugin, playerUUID: UUID) {
        plugin.logger.info("[SchematicCageHelper] Clearing sessions for UUID: $playerUUID")
        clearCageInfoForPlayer(plugin, playerUUID)

        // WorldEdit session cleanup (als speler online is)
        val player = Bukkit.getPlayer(playerUUID)
        if (player != null) {
            try {
                val wePlayer = BukkitAdapter.adapt(player)
                val sessionManager = WorldEdit.getInstance().sessionManager
                // WorldEdit cleared automatisch sessions bij disconnect
                plugin.logger.info("[SchematicCageHelper] WorldEdit session will be cleared automatically for UUID: $playerUUID")
            } catch (e: Exception) {
                plugin.logger.warning("[SchematicCageHelper] Error clearing WorldEdit session for $playerUUID: ${e.message}")
            }
        }
    }

    /**
     * Helper methods
     */
    fun getCageSessionCount(playerUUID: UUID): Int {
        return playerCageInfo[playerUUID]?.size ?: 0
    }

    fun hasCageSessions(playerUUID: UUID): Boolean {
        return getCageSessionCount(playerUUID) > 0
    }

    /**
     * Plugin shutdown cleanup
     */
    fun cleanupAllCageSessions(plugin: JavaPlugin) {
        plugin.logger.info("[SchematicCageHelper] Cleaning up all cage sessions...")
        val totalPlayers = playerCageInfo.size
        var totalCages = 0
        playerCageInfo.values.forEach { cages ->
            totalCages += cages.size
            cages.clear()
        }
        playerCageInfo.clear()
        plugin.logger.info("[SchematicCageHelper] ✓ Cleaned up $totalCages cage sessions for $totalPlayers players")
    }
}