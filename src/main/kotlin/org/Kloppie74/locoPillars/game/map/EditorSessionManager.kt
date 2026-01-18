package org.Kloppie74.locoPillars.game.map

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.entity.Player
import org.bukkit.generator.ChunkGenerator
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.SchematicHelper
import java.io.File
import java.util.UUID

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
    private val mapIOManager: MapIOManager
) {
    
    private val activeSessions = mutableMapOf<UUID, EditorSession>()
    
    /**
     * Start an editor session for a player
     */
    fun startSession(player: Player, mapName: String): Boolean {
        // Check if map exists
        if (!mapIOManager.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
            return false
        }
        
        // Check if player already has an active session
        if (activeSessions.containsKey(player.uniqueId)) {
            player.sendMessage(MSG.chatColors("&cYou already have an active editor session!"))
            player.sendMessage(MSG.chatColors("&7Use &e/pillars admin map cancel &7to end it"))
            return false
        }
        
        // Create temporary world
        val worldName = "pillars_edit_${mapName}_${UUID.randomUUID().toString().substring(0, 8)}"
        val world = createVoidWorld(worldName)
        
        if (world == null) {
            player.sendMessage(MSG.chatColors("&cFailed to create editor world!"))
            return false
        }
        
        // Load schematic if it exists
        val schemFile = mapIOManager.getMapSchematicFile(mapName)
        if (schemFile.exists()) {
            val centerLoc = world.getBlockAt(0, 65, 0).location
            val success = SchematicHelper.pasteSchematic(plugin, plugin.configManager, schemFile, centerLoc)
            
            if (success) {
                player.sendMessage(MSG.chatColors("&aLoaded existing schematic for map: &f$mapName"))
            } else {
                player.sendMessage(MSG.chatColors("&cFailed to load schematic, starting with empty world"))
            }
        } else {
            player.sendMessage(MSG.chatColors("&7No existing schematic found, starting with empty world"))
        }
        
        // Create session
        val session = EditorSession(player, mapName, world, plugin, mapIOManager)
        activeSessions[player.uniqueId] = session
        
        // Teleport player
        player.teleport(world.getBlockAt(0, 65, 0).location.add(0.5, 1.0, 0.5))
        
        // Send info
        player.sendMessage(MSG.chatColors("&a=== Editor Session Started ==="))
        player.sendMessage(MSG.chatColors("&eMap: &f$mapName"))
        player.sendMessage(MSG.chatColors("&eWorld: &f$worldName"))
        player.sendMessage(MSG.chatColors("&7RED particles show the save region"))
        player.sendMessage(MSG.chatColors(""))
        player.sendMessage(MSG.chatColors("&7Commands:"))
        player.sendMessage(MSG.chatColors("  &e/pillars admin map save &7- Save changes"))
        player.sendMessage(MSG.chatColors("  &e/pillars admin map resize <radius> &7- Adjust save region"))
        player.sendMessage(MSG.chatColors("  &e/pillars admin map info &7- Show session info"))
        player.sendMessage(MSG.chatColors("  &e/pillars admin map cancel &7- Cancel without saving"))
        
        debugLog("Started editor session for ${player.name} on map $mapName")
        return true
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
        val schemFile = mapIOManager.getMapSchematicFile(session.mapName)
        
        // Ensure parent directory exists
        schemFile.parentFile?.mkdirs()
        
        // Save schematic using WorldEdit/FAWE
        val success = SchematicHelper.saveSchematic(plugin, plugin.configManager, loc1, loc2, schemFile)
        
        if (success) {
            player.sendMessage(MSG.chatColors("&aSuccessfully saved map: &f${session.mapName}"))
            player.sendMessage(MSG.chatColors("&7Schematic saved to: &f${schemFile.name}"))
            
            // Save settings (in case radius was changed)
            val config = mapIOManager.loadMapSettings(session.mapName)
            if (config != null) {
                config.set("SchematicRadius", session.getSchematicRadius())
                mapIOManager.saveMapSettings(session.mapName, config)
            }
            
            debugLog("Saved map ${session.mapName}")
        } else {
            player.sendMessage(MSG.chatColors("&cFailed to save schematic!"))
            player.sendMessage(MSG.chatColors("&7Your session will remain active"))
            return false
        }
        
        // End session and cleanup
        endSession(player, true)
        
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
        
        session.setSchematicRadius(radius)
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
