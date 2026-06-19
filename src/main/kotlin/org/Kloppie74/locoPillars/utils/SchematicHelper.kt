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
import com.sk89q.worldedit.function.pattern.Pattern
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.regions.CuboidRegion
import com.sk89q.worldedit.regions.Region
import com.sk89q.worldedit.session.ClipboardHolder
import com.sk89q.worldedit.world.block.BlockTypes
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
            // changeSetNull()   — disables FAWE history (no NullChangeSet heap usage)
            // copyEntities(false) — skips entity NBT during paste; FAWE's recursive
            //   hashCode() on EagerFaweCompoundTag causes OutOfMemoryError when many
            //   arenas are pasted at startup.  Arena resets only need block structure.
            WorldEdit.getInstance().newEditSessionBuilder()
                .world(weWorld)
                .changeSetNull()
                .build()
                .use { editSession ->
                    val operation = ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(BlockVector3.at(location.blockX, location.blockY, location.blockZ))
                        .ignoreAirBlocks(false)
                        .copyEntities(false)
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

    /**
     * Wipe the cuboid [clearMin]..[clearMax] to air, then paste [clipboard] at
     * [pasteLocation] — all in a single EditSession. Guarantees a pixel-perfect arena
     * reset: every block a player could have placed in the buildable area is removed
     * before the map is written back, so no dirty/leftover blocks survive between rounds.
     *
     * The clear region is supplied by the caller (the full buildable column, which is
     * larger than the schematic footprint) rather than derived from the clipboard, since
     * players can build beyond the schematic's solid volume.
     *
     * Used by [org.Kloppie74.locoPillars.game.arena.ArenaManager] for in-game resets.
     */
    fun clearAndPasteClipboard(
        plugin: JavaPlugin,
        configManager: ConfigManager,
        clipboard: Clipboard,
        pasteLocation: Location,
        clearMin: Location,
        clearMax: Location
    ): Boolean {
        try {
            val bWorld = pasteLocation.world ?: run {
                plugin.logger.severe("[SchematicHelper] Location world is null!")
                return false
            }

            val weWorld = BukkitAdapter.adapt(bWorld)
            val to = BlockVector3.at(pasteLocation.blockX, pasteLocation.blockY, pasteLocation.blockZ)
            // Typed as Region (not CuboidRegion) so the setBlocks(Region, Pattern) overload
            // is chosen unambiguously — CuboidRegion also matches setBlocks(Set, Pattern).
            val clearRegion: Region = CuboidRegion(
                BlockVector3.at(clearMin.blockX, clearMin.blockY, clearMin.blockZ),
                BlockVector3.at(clearMax.blockX, clearMax.blockY, clearMax.blockZ)
            )

            WorldEdit.getInstance().newEditSessionBuilder()
                .world(weWorld)
                .changeSetNull()
                .build()
                .use { editSession ->
                    // 1) Wipe the whole buildable area to air — removes leftover/dirty blocks.
                    //    Cast to Pattern to disambiguate setBlocks() overloads (BlockState
                    //    satisfies both the BlockStateHolder and Pattern variants).
                    editSession.setBlocks(clearRegion, BlockTypes.AIR!!.defaultState as Pattern)
                    // 2) Paste the schematic back into the clean region.
                    val operation = ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(to)
                        .ignoreAirBlocks(false)
                        .copyEntities(false)
                        .build()
                    Operations.complete(operation)
                }

            debugLog(plugin, configManager, "✓ Clear + paste reset completed successfully!")
            return true
        } catch (e: Exception) {
            plugin.logger.severe("[SchematicHelper] Error during clear+paste reset: ${e.message}")
            e.printStackTrace()
            return false
        }
    }

    /**
     * Paste a preloaded clipboard into an **existing** EditSession without closing it.
     *
     * Used exclusively by the startup batch in [ArenaManager.preloadInstances].
     * All arenas share ONE session so FAWE's lighting recalculation and chunk
     * flush happen exactly ONCE (when the caller closes the session), not per-arena.
     *
     * @param ignoreAir when true, air blocks in the clipboard are not written —
     *   pass `true` for startup pastes in a void world (60–70 % fewer block writes).
     *
     * Throws on failure so the caller can log with its own context.
     */
    fun pasteClipboardBatch(
        editSession: EditSession,
        clipboard: Clipboard,
        location: Location,
        ignoreAir: Boolean = false
    ): Boolean {
        return try {
            val operation = ClipboardHolder(clipboard)
                .createPaste(editSession)
                .to(BlockVector3.at(location.blockX, location.blockY, location.blockZ))
                .ignoreAirBlocks(ignoreAir)
                .copyEntities(false)
                .copyBiomes(false)
                .build()
            Operations.complete(operation)
            true
        } catch (e: Exception) {
            throw e
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
            clipboard.origin = region.minimumPoint

            WorldEdit.getInstance().newEditSessionBuilder()
                .world(weWorld)
                .changeSetNull()
                .build()
                .use { editSession ->
                    val copyOperation = ForwardExtentCopy(
                        editSession, region, clipboard, region.minimumPoint
                    )
                    copyOperation.isCopyingEntities = true
                    Operations.complete(copyOperation)
                }

            val format = ClipboardFormats.findByAlias("fast")
                ?: ClipboardFormats.findByAlias("sponge")
                ?: ClipboardFormats.findByAlias("schem")
                ?: run {
                    plugin.logger.severe("[SchematicHelper] No supported clipboard format found (tried: fast, sponge, schem)!")
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
