package org.Kloppie74.locoPillars.utils

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.EditSession
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.BlockArrayClipboard
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.extent.clipboard.io.ClipboardWriter
import com.sk89q.worldedit.function.operation.ForwardExtentCopy
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.session.ClipboardHolder
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object SchematicHelper {

    private fun debugLog(plugin: JavaPlugin, configManager: ConfigManager, message: String) {
        if (configManager.debugMode) {
            plugin.logger.info("[SchematicHelper] $message")
        }
    }

    fun pasteSchematic(plugin: JavaPlugin, configManager: ConfigManager, schemFile: File, location: Location): Boolean {
        debugLog(plugin, configManager, "Starting schematic paste operation")
        debugLog(plugin, configManager, "File: ${schemFile.absolutePath}")
        debugLog(plugin, configManager, "Target location: ${location.world?.name} at ${location.blockX}, ${location.blockY}, ${location.blockZ}")

        if (!validateSchematicFile(plugin, configManager, schemFile)) return false
        val clipboard = loadSchematic(plugin, configManager, schemFile) ?: return false
        return pasteClipboard(plugin, configManager, clipboard, location)
    }

    /**
     * Paste a preloaded clipboard into the world at the given location.
     *
     * @param plugin plugin context used for logging
     * @param configManager configuration access for debug logging
     * @param clipboard clipboard to paste
     * @param location target location for the paste
     * @return true when the paste completes successfully
     */
    fun pasteClipboard(plugin: JavaPlugin, configManager: ConfigManager, clipboard: Clipboard, location: Location): Boolean {
        try {
            val bWorld = location.world ?: run {
                plugin.logger.severe("[SchematicHelper] Location world is null!")
                return false
            }

            val weWorld = BukkitAdapter.adapt(bWorld)
            WorldEdit.getInstance().newEditSession(weWorld).use { editSession ->
                val operation = ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BlockVector3.at(location.blockX, location.blockY, location.blockZ))
                    .ignoreAirBlocks(false)
                    .build()

                Operations.complete(operation)
            }

            debugLog(plugin, configManager, "✓ Paste operation completed successfully!")
            return true
        } catch (e: Exception) {
            plugin.logger.severe("[SchematicHelper] Error during schematic paste: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    fun saveSchematic(plugin: JavaPlugin, configManager: ConfigManager, pos1: Location, pos2: Location, schemFile: File): Boolean {
        debugLog(plugin, configManager, "Starting schematic save operation")
        debugLog(plugin, configManager, "File: ${schemFile.absolutePath}")
        debugLog(plugin, configManager, "Positions: Pos1(${pos1.blockX}, ${pos1.blockY}, ${pos1.blockZ}), Pos2(${pos2.blockX}, ${pos2.blockY}, ${pos2.blockZ})")

        try {
            val bWorld = pos1.world ?: run {
                plugin.logger.severe("[SchematicHelper] World is null!")
                return false
            }

            val weWorld = BukkitAdapter.adapt(bWorld)
            val min = BlockVector3.at(pos1.blockX, pos1.blockY, pos1.blockZ)
            val max = BlockVector3.at(pos2.blockX, pos2.blockY, pos2.blockZ)
            val region = CuboidRegion(min, max)
            val clipboard = BlockArrayClipboard(region)

            WorldEdit.getInstance().newEditSession(weWorld).use { editSession ->
                val copyOperation = ForwardExtentCopy(
                    editSession, region, clipboard, region.minimumPoint
                )
                copyOperation.isCopyingEntities = true
                Operations.complete(copyOperation)
            }

            val format = ClipboardFormats.findByAlias("sponge") ?: run {
                plugin.logger.severe("[SchematicHelper] Sponge format not found!")
                return false
            }

            FileOutputStream(schemFile).use { outputStream ->
                format.getWriter(outputStream).use { writer ->
                    writer.write(clipboard)
                }
            }

            debugLog(plugin, configManager, "✓ Schematic saved successfully to ${schemFile.absolutePath}")
            return true
        } catch (e: Exception) {
            plugin.logger.severe("[SchematicHelper] Error during schematic save: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    fun validateSchematicFile(plugin: JavaPlugin, configManager: ConfigManager, schemFile: File): Boolean {
        if (!schemFile.exists()) {
            debugLog(plugin, configManager, "File does not exist: ${schemFile.absolutePath}")
            return false
        }

        if (!schemFile.canRead()) {
            debugLog(plugin, configManager, "File is not readable: ${schemFile.absolutePath}")
            return false
        }

        if (schemFile.length() == 0L) {
            debugLog(plugin, configManager, "File is empty: ${schemFile.absolutePath}")
            return false
        }

        val format = ClipboardFormats.findByFile(schemFile)
        if (format == null) {
            debugLog(plugin, configManager, "Unknown format for file: ${schemFile.name}")
            return false
        }

        return true
    }

    fun loadSchematic(plugin: JavaPlugin, configManager: ConfigManager, schemFile: File): Clipboard? {
        debugLog(plugin, configManager, "Loading schematic from file: ${schemFile.absolutePath}")

        try {
            val format = ClipboardFormats.findByFile(schemFile) ?: run {
                plugin.logger.severe("[SchematicHelper] Could not determine format for: ${schemFile.name}")
                return null
            }

            FileInputStream(schemFile).use { inputStream ->
                format.getReader(inputStream).use { reader ->
                    val clipboard = reader.read()
                    debugLog(plugin, configManager, "✓ Schematic loaded successfully!")
                    return clipboard
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("[SchematicHelper] Error during schematic load: ${e.message}")
            e.printStackTrace()
            return null
        }
    }
}
