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
import java.io.File
import java.io.FileInputStream

object SchematicHelper {

    /**
     * Paste schematic zonder speler-context (algemene gebruik)
     */
    fun pasteSchematic(plugin: JavaPlugin, schemFile: File, location: Location): Boolean {
        plugin.logger.info("[SchematicHelper] Starting schematic paste operation")
        plugin.logger.info("[SchematicHelper] File: ${schemFile.absolutePath}")
        plugin.logger.info("[SchematicHelper] Target location: ${location.world?.name} at ${location.blockX}, ${location.blockY}, ${location.blockZ}")

        try {
            if (!validateSchematicFile(plugin, schemFile)) return false
            val bWorld = location.world ?: run {
                plugin.logger.severe("[SchematicHelper] Location world is null!")
                return false
            }

            val weWorld = BukkitAdapter.adapt(bWorld)
            WorldEdit.getInstance().newEditSession(weWorld).use { editSession ->
                return pasteSchematicWithEditSession(plugin, schemFile, location, editSession)
            }
        } catch (e: Exception) {
            plugin.logger.severe("[SchematicHelper] Error during schematic paste: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Paste met aangeleverde EditSession
     */
    fun pasteSchematicWithEditSession(
        plugin: JavaPlugin,
        schemFile: File,
        location: Location,
        editSession: EditSession
    ): Boolean {
        plugin.logger.info("[SchematicHelper] Pasting schematic with EditSession: ${schemFile.name}")
        plugin.logger.info("[SchematicHelper] Target location: ${location.world?.name} at ${location.blockX}, ${location.blockY}, ${location.blockZ}")

        try {
            val format = ClipboardFormats.findByFile(schemFile) ?: run {
                plugin.logger.severe("[SchematicHelper] Could not determine format for: ${schemFile.name}")
                return false
            }

            // Read clipboard
            val clipboard: Clipboard = FileInputStream(schemFile).use { inputStream ->
                format.getReader(inputStream).use { reader -> reader.read() }
            }

            plugin.logger.info("[SchematicHelper] Clipboard loaded:")
            plugin.logger.info("[SchematicHelper]   - Dimensions: ${clipboard.dimensions}")
            plugin.logger.info("[SchematicHelper]   - Origin: ${clipboard.origin}")
            plugin.logger.info("[SchematicHelper]   - Minimum: ${clipboard.minimumPoint}")
            plugin.logger.info("[SchematicHelper]   - Maximum: ${clipboard.maximumPoint}")

            // 0,0,0 paste point
            val pasteLocation = BlockVector3.at(location.blockX, location.blockY, location.blockZ)
            plugin.logger.info("[SchematicHelper] Paste location: $pasteLocation")

            val operation = ClipboardHolder(clipboard)
                .createPaste(editSession)
                .to(pasteLocation)
                .ignoreAirBlocks(false)
                .build()

            Operations.complete(operation)
            editSession.flushSession()

            plugin.logger.info("[SchematicHelper] ✓ Paste operation completed successfully!")
            return true
        } catch (e: Exception) {
            plugin.logger.severe("[SchematicHelper] Error during schematic paste: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Basis validatie voor schematic bestanden
     */
    fun validateSchematicFile(plugin: JavaPlugin, schemFile: File): Boolean {
        if (!schemFile.exists()) {
            plugin.logger.warning("[SchematicHelper] File does not exist: ${schemFile.absolutePath}")
            return false
        }

        if (!schemFile.canRead()) {
            plugin.logger.warning("[SchematicHelper] File is not readable: ${schemFile.absolutePath}")
            return false
        }

        if (schemFile.length() == 0L) {
            plugin.logger.warning("[SchematicHelper] File is empty: ${schemFile.absolutePath}")
            return false
        }

        val format = ClipboardFormats.findByFile(schemFile)
        if (format == null) {
            plugin.logger.warning("[SchematicHelper] Unknown format for file: ${schemFile.name}")
            return false
        }

        return true
    }
}