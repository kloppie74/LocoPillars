package org.Kloppie74.locoPillars.game.map

import com.sk89q.worldedit.extent.clipboard.Clipboard
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import org.bukkit.generator.ChunkGenerator
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.SchematicHelper
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * EditorSessionManager - Manages all active editor sessions
 * 
 * Responsibilities:
 * - Create and track editor sessions
 * - Generate temporary void worlds for editing
 * - Load schematics into edit worlds
 * - Save regions back to schematics
 * - Clean up sessions and worlds
 */
class EditorSessionManager(
    private val plugin: LocoPillars,
    private val mapRepository: MapRepository
) {
    
    private val activeSessions = mutableMapOf<UUID, EditorSession>()
    private val pendingSessions = mutableSetOf<UUID>()
    
    /**
     * Start an editor session for a player
     */
    fun startSession(player: Player, mapName: String): Boolean {
        // Check if map exists
        if (!mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
            return false
        }
        
        // Check if player already has an active session
        if (activeSessions.containsKey(player.uniqueId) || pendingSessions.contains(player.uniqueId)) {
            player.sendMessage(MSG.chatColors("&cYou already have an active editor session!"))
            player.sendMessage(MSG.chatColors("&7Use &e/pillars admin map cancel &7to end it"))
            return false
        }

        pendingSessions.add(player.uniqueId)
        
        // Create temporary world
        val worldName = "pillars_edit_${mapName}_${UUID.randomUUID().toString().substring(0, 8)}"
        val world = createVoidWorld(worldName)
        
        if (world == null) {
            player.sendMessage(MSG.chatColors("&cFailed to create editor world!"))
            pendingSessions.remove(player.uniqueId)
            return false
        }

        // Load schematic if it exists
        val schemFile = mapRepository.getMapSchematicFile(mapName)
        val spawnY = mapRepository.loadMapSettings(mapName)
            ?.getDouble("Settings.SpawnPoint.Y", 65.0)
            ?: 65.0
        val centerLoc = world.getBlockAt(0, spawnY.toInt(), 0).location

        if (schemFile.exists()) {
            player.sendMessage(MSG.chatColors("&eLoading schematic asynchronously..."))
            loadSchematicAsync(schemFile)
                .thenCompose { clipboard ->
                    if (clipboard == null) {
                        CompletableFuture.completedFuture(false)
                    } else {
                        pasteClipboardAsync(clipboard, centerLoc)
                    }
                }
                .exceptionally { ex ->
                    plugin.logger.severe("[EditorSessionManager] Failed to load schematic for $mapName: ${ex.message}")
                    false
                }
                .whenComplete { success, _ ->
                    runOnMain {
                        finishSessionStart(player, mapName, world, worldName, success, true)
                    }
                }
        } else {
            finishSessionStart(player, mapName, world, worldName, false, false)
        }

        return true
    }

    private fun loadSchematicAsync(schemFile: File): CompletableFuture<Clipboard?> {
        // File I/O is safe off-thread regardless of FAWE availability.
        return scheduleTask(true) {
            SchematicHelper.loadSchematic(plugin, plugin.configManager, schemFile)
        }
    }

    private fun pasteClipboardAsync(clipboard: Clipboard, location: Location): CompletableFuture<Boolean> {
        return scheduleTask(isFaweAvailable()) {
            SchematicHelper.pasteClipboard(plugin, plugin.configManager, clipboard, location)
        }
    }

    private fun saveSchematicAsync(loc1: Location, loc2: Location, schemFile: File): CompletableFuture<Boolean> {
        return scheduleTask(isFaweAvailable()) {
            SchematicHelper.saveSchematic(plugin, plugin.configManager, loc1, loc2, schemFile)
        }
    }

    private fun isFaweAvailable(): Boolean {
        // FAWE (FastAsyncWorldEdit) allows safe async schematic operations.
        return Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null
    }

    private fun finishSessionStart(
        player: Player,
        mapName: String,
        world: World,
        worldName: String,
        pasteSuccess: Boolean,
        hadSchematic: Boolean
    ) {
        pendingSessions.remove(player.uniqueId)
        if (!player.isOnline) {
            cleanupWorld(worldName)
            return
        }

        if (hadSchematic) {
            if (pasteSuccess) {
                player.sendMessage(MSG.chatColors("&aLoaded existing schematic for map: &f$mapName"))
            } else {
                player.sendMessage(MSG.chatColors("&cFailed to load schematic, starting with empty world"))
                applyBedrockAnchor(world, mapName, updateSettings = false)
            }
        } else {
            player.sendMessage(MSG.chatColors("&7No existing schematic found, starting with bedrock anchor"))
            applyBedrockAnchor(world, mapName, updateSettings = true)
        }

        val session = EditorSession(player, mapName, world, plugin, mapRepository)
        activeSessions[player.uniqueId] = session

        player.gameMode = GameMode.CREATIVE
        player.teleport(world.getBlockAt(0, 100, 0).location)

        player.sendMessage(MSG.chatColors("&a=== Editor Session Started ==="))
        player.sendMessage(MSG.chatColors("&eMap: &f$mapName"))
        player.sendMessage(MSG.chatColors("&eWorld: &f$worldName"))
        player.sendMessage(MSG.chatColors("&7RED particles show the save region"))
        player.sendMessage(MSG.chatColors("&7GREEN particles show the play border"))
        player.sendMessage(MSG.chatColors(""))
        player.sendMessage(MSG.chatColors("&7Commands:"))
        player.sendMessage(MSG.chatColors("  &e/pillars admin map save &7- Save changes"))
        player.sendMessage(MSG.chatColors("  &e/pillars admin map resize <radius> &7- Adjust save region"))
        player.sendMessage(MSG.chatColors("  &e/pillars admin map settings &7- Open editor settings"))
        player.sendMessage(MSG.chatColors("  &e/pillars admin map info &7- Show session info"))
        player.sendMessage(MSG.chatColors("  &e/pillars admin map cancel &7- Cancel without saving"))

        debugLog("Started editor session for ${player.name} on map $mapName")
    }

    private fun applyBedrockAnchor(world: World, mapName: String, updateSettings: Boolean) {
        world.getBlockAt(0, 0, 0).type = Material.BEDROCK
        if (!updateSettings) {
            return
        }

        val config = mapRepository.loadMapSettings(mapName) ?: return
        config.set("Settings.SpawnPoint.X", 0.0)
        config.set("Settings.SpawnPoint.Y", 1.0)
        config.set("Settings.SpawnPoint.Z", 0.0)
        mapRepository.saveMapSettings(mapName, config)
    }

    private fun <T> scheduleTask(async: Boolean, task: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        if (!ensurePluginEnabled(future)) return future
        val runnable = Runnable {
            if (!ensurePluginEnabled(future)) return@Runnable
            try {
                future.complete(task())
            } catch (e: Exception) {
                future.completeExceptionally(e)
            }
        }

        try {
            if (async) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable)
            } else {
                Bukkit.getScheduler().runTask(plugin, runnable)
            }
        } catch (e: IllegalStateException) {
            future.completeExceptionally(e)
        }

        return future
    }

    private fun <T> ensurePluginEnabled(future: CompletableFuture<T>): Boolean {
        if (!plugin.isEnabled) {
            future.completeExceptionally(IllegalStateException("Plugin disabled"))
            return false
        }
        return true
    }

    private fun runOnMain(task: () -> Unit) {
        Bukkit.getScheduler().runTask(plugin, Runnable { task() })
    }

    private fun cleanupWorld(worldName: String) {
        val world = Bukkit.getWorld(worldName) ?: return
        val mainWorld = Bukkit.getWorlds().firstOrNull { it.environment == World.Environment.NORMAL }
        if (mainWorld != null) {
            world.players.forEach { it.teleport(mainWorld.spawnLocation) }
        }
        if (Bukkit.unloadWorld(world, false)) {
            val worldFolder = File(Bukkit.getWorldContainer(), worldName)
            if (worldFolder.exists()) {
                worldFolder.deleteRecursively()
            }
        }
    }
    
    /**
     * Save the current session and end it
     */
    fun saveAndEndSession(player: Player): Boolean {
        val session = activeSessions[player.uniqueId]
        
        if (session == null) {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            return false
        }
        
        if (!session.isActive()) {
            player.sendMessage(MSG.chatColors("&cYour editor session is no longer active!"))
            activeSessions.remove(player.uniqueId)
            return false
        }
        
        player.sendMessage(MSG.chatColors("&eSaving map changes..."))
        
        // Get save region bounds
        val (loc1, loc2) = session.getSaveRegionBounds()
        val schemFile = mapRepository.getMapSchematicFile(session.mapName)
        
        // Ensure parent directory exists
        schemFile.parentFile?.mkdirs()

        saveSchematicAsync(loc1, loc2, schemFile)
            .exceptionally { ex ->
                plugin.logger.severe("[EditorSessionManager] Failed to save schematic for ${session.mapName}: ${ex.message}")
                false
            }
            .whenComplete { success, _ ->
                runOnMain {
                    if (!success) {
                        player.sendMessage(MSG.chatColors("&cFailed to save schematic!"))
                        player.sendMessage(MSG.chatColors("&7Your session will remain active"))
                        return@runOnMain
                    }

                    player.sendMessage(MSG.chatColors("&aSuccessfully saved: &f${session.mapName}"))
                    player.sendMessage(MSG.chatColors("&7Schematic saved to: &f${schemFile.name}"))

                    val config = mapRepository.loadMapSettings(session.mapName)
                    if (config != null) {
                        config.set("SaveRadius", session.getSaveRadius())
                        config.set("SchematicRadius", session.getSaveRadius())
                        mapRepository.saveMapSettings(session.mapName, config)
                    }

                    debugLog("Saved map ${session.mapName}")
                    endSession(player, true)
                }
            }

        return true
    }
    
    /**
     * Cancel and end a session without saving
     */
    fun cancelSession(player: Player): Boolean {
        val session = activeSessions[player.uniqueId]
        
        if (session == null) {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            return false
        }
        
        player.sendMessage(MSG.chatColors("&eEditor session cancelled (no changes saved)"))
        endSession(player, false)
        
        return true
    }
    
    /**
     * End a session and cleanup
     */
    private fun endSession(player: Player, saveChanges: Boolean) {
        val session = activeSessions.remove(player.uniqueId) ?: return
        
        val worldName = session.tempWorld.name
        
        // End session (stops particles, etc.)
        session.end(saveChanges)
        
        // Teleport player back
        session.returnPlayerToLobby()
        
        // Unload and delete temporary world
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val world = Bukkit.getWorld(worldName)
            if (world != null) {
                // Move any remaining players
                world.players.forEach { p ->
                    val mainWorld = Bukkit.getWorlds().firstOrNull { it.environment == World.Environment.NORMAL }
                    if (mainWorld != null) {
                        p.teleport(mainWorld.spawnLocation)
                    }
                }
                
                // Unload world
                val unloaded = Bukkit.unloadWorld(world, false)
                if (unloaded) {
                    // Delete world folder
                    val worldFolder = File(Bukkit.getWorldContainer(), worldName)
                    if (worldFolder.exists()) {
                        worldFolder.deleteRecursively()
                        debugLog("Deleted temporary world: $worldName")
                    }
                }
            }
        }, 20L) // Wait 1 second before cleanup
        
        debugLog("Ended editor session for ${player.name}")
    }
    
    /**
     * Get active session for a player
     */
    fun getSession(player: Player): EditorSession? {
        return activeSessions[player.uniqueId]
    }
    
    /**
     * Check if player has an active session
     */
    fun hasSession(player: Player): Boolean {
        return activeSessions.containsKey(player.uniqueId)
    }
    
    /**
     * Resize the save region for a player's active session
     */
    fun resizeSession(player: Player, radius: Int): Boolean {
        val session = activeSessions[player.uniqueId]
        
        if (session == null) {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            return false
        }
        
        session.setSaveRadius(radius)
        return true
    }
    
    /**
     * Show info about player's active session
     */
    fun showSessionInfo(player: Player): Boolean {
        val session = activeSessions[player.uniqueId]
        
        if (session == null) {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            return false
        }
        
        session.showInfo()
        return true
    }
    
    /**
     * Cleanup all sessions (called on plugin disable)
     */
    fun cleanup() {
        activeSessions.values.toList().forEach { session ->
            if (session.player.isOnline) {
                session.player.sendMessage(MSG.chatColors("&cEditor session ended due to plugin reload"))
                session.returnPlayerToLobby()
            }
            session.end(false)
        }
        
        activeSessions.clear()
        debugLog("Cleaned up all editor sessions")
    }
    
    /**
     * Create a void world for editing
     */
    private fun createVoidWorld(worldName: String): World? {
        return try {
            val creator = WorldCreator(worldName)
                .generator(VoidChunkGenerator())
                .generateStructures(false)
            
            creator.createWorld()
        } catch (e: Exception) {
            plugin.logger.severe("Failed to create void world: ${e.message}")
            null
        }
    }
    
    /**
     * Simple void chunk generator
     */
    private class VoidChunkGenerator : ChunkGenerator() {
        override fun generateChunkData(
            world: World,
            random: java.util.Random,
            x: Int,
            z: Int,
            biome: BiomeGrid
        ): ChunkData {
            return createChunkData(world)
        }
        
        override fun canSpawn(world: World, x: Int, z: Int): Boolean = true
        override fun getDefaultPopulators(world: World): List<org.bukkit.generator.BlockPopulator> = emptyList()
    }
    
    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[EditorSessionManager] $message")
        }
    }
}
