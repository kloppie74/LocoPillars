package org.Kloppie74.locoPillars.utils

import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.EditSession
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.extent.clipboard.Clipboard
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.function.operation.Operations
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.session.ClipboardHolder
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import org.Kloppie74.locoPillars.config.ConfigManager
import java.io.File
import java.io.FileInputStream

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

        try {
            if (!validateSchematicFile(plugin, configManager, schemFile)) return false
            val bWorld = location.world ?: run {
                plugin.logger.severe("[SchematicHelper] Location world is null!")
                return false
            }

            val weWorld = BukkitAdapter.adapt(bWorld)
            WorldEdit.getInstance().newEditSession(weWorld).use { editSession ->
                return pasteSchematicWithEditSession(plugin, configManager, schemFile, location, editSession)
            }
        } catch (e: Exception) {
            plugin.logger.severe("[SchematicHelper] Error during schematic paste: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    fun pasteSchematicWithEditSession(
        plugin: JavaPlugin,
        configManager: ConfigManager,
        schemFile: File,
        location: Location,
        editSession: EditSession
    ): Boolean {
        debugLog(plugin, configManager, "Pasting schematic with EditSession: ${schemFile.name}")
        debugLog(plugin, configManager, "Target location: ${location.world?.name} at ${location.blockX}, ${location.blockY}, ${location.blockZ}")

        try {
            val format = ClipboardFormats.findByFile(schemFile) ?: run {
                plugin.logger.severe("[SchematicHelper] Could not determine format for: ${schemFile.name}")
                return false
            }

            val clipboard: Clipboard = FileInputStream(schemFile).use { inputStream ->
                format.getReader(inputStream).use { reader -> reader.read() }
            }

            debugLog(plugin, configManager, "Clipboard loaded:")
            debugLog(plugin, configManager, "  - Dimensions: ${clipboard.dimensions}")
            debugLog(plugin, configManager, "  - Origin: ${clipboard.origin}")
            debugLog(plugin, configManager, "  - Minimum: ${clipboard.minimumPoint}")
            debugLog(plugin, configManager, "  - Maximum: ${clipboard.maximumPoint}")

            val pasteLocation = BlockVector3.at(location.blockX, location.blockY, location.blockZ)
            debugLog(plugin, configManager, "Paste location: $pasteLocation")

            val operation = ClipboardHolder(clipboard)
                .createPaste(editSession)
                .to(pasteLocation)
                .ignoreAirBlocks(false)
                .build()

            Operations.complete(operation)
            editSession.flushSession()

            debugLog(plugin, configManager, "✓ Paste operation completed successfully!")
            return true
        } catch (e: Exception) {
            plugin.logger.severe("[SchematicHelper] Error during schematic paste: ${e.message}")
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
}