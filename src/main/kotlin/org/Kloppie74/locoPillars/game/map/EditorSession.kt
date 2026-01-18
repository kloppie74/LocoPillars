package org.Kloppie74.locoPillars.game.map

import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG
import java.util.UUID

/**
 * EditorSession - Manages a single map editing session
 * 
 * Responsibilities:
 * - Track the player editing the map
 * - Manage the temporary/volatile edit world
 * - Store the current schematic radius for dynamic save
 * - Run particle visualization task to show save region
 * - Handle cleanup on session end
 */
class EditorSession(
    val player: Player,
    val mapName: String,
    val tempWorld: World,
    private val plugin: LocoPillars,
    private val mapIOManager: MapIOManager
) {
    
    val sessionId: UUID = UUID.randomUUID()
    private var schematicRadius: Int = mapIOManager.getSchematicRadius(mapName)
    private var particleTask: BukkitTask? = null
    private var isActive: Boolean = true
    
    // Center point for schematic save (usually 0,0,0 or the map center)
    private val centerX: Double = 0.0
    private val centerY: Double = 65.0
    private val centerZ: Double = 0.0
    
    init {
        debugLog("EditorSession created for player ${player.name}, map: $mapName")
        startParticleVisualization()
    }
    
    /**
     * Start the particle visualization task
     * Displays RED particles outlining the cuboid that will be saved
     */
    private fun startParticleVisualization() {
        particleTask = object : BukkitRunnable() {
            override fun run() {
                if (!isActive || !player.isOnline || player.world != tempWorld) {
                    cancel()
                    return
                }
                
                displaySaveRegionParticles()
            }
        }.runTaskTimer(plugin, 0L, 10L) // Run every 0.5 seconds
        
        debugLog("Started particle visualization task")
    }
    
    /**
     * Display RED particles outlining the save region
     */
    private fun displaySaveRegionParticles() {
        val world = tempWorld
        val radius = schematicRadius.toDouble()
        
        // Calculate bounds
        val minX = centerX - radius
        val maxX = centerX + radius
        val minY = Math.max(world.minHeight.toDouble(), centerY - radius)
        val maxY = Math.min(world.maxHeight.toDouble(), centerY + radius)
        val minZ = centerZ - radius
        val maxZ = centerZ + radius
        
        // Draw cuboid outline with particles
        drawParticleLine(world, minX, minY, minZ, maxX, minY, minZ) // Bottom edge 1
        drawParticleLine(world, minX, minY, maxZ, maxX, minY, maxZ) // Bottom edge 2
        drawParticleLine(world, minX, minY, minZ, minX, minY, maxZ) // Bottom edge 3
        drawParticleLine(world, maxX, minY, minZ, maxX, minY, maxZ) // Bottom edge 4
        
        drawParticleLine(world, minX, maxY, minZ, maxX, maxY, minZ) // Top edge 1
        drawParticleLine(world, minX, maxY, maxZ, maxX, maxY, maxZ) // Top edge 2
        drawParticleLine(world, minX, maxY, minZ, minX, maxY, maxZ) // Top edge 3
        drawParticleLine(world, maxX, maxY, minZ, maxX, maxY, maxZ) // Top edge 4
        
        drawParticleLine(world, minX, minY, minZ, minX, maxY, minZ) // Vertical edge 1
        drawParticleLine(world, maxX, minY, minZ, maxX, maxY, minZ) // Vertical edge 2
        drawParticleLine(world, minX, minY, maxZ, minX, maxY, maxZ) // Vertical edge 3
        drawParticleLine(world, maxX, minY, maxZ, maxX, maxY, maxZ) // Vertical edge 4
    }
    
    /**
     * Draw a line of particles between two points
     */
    private fun drawParticleLine(world: World, x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double) {
        val distance = Math.sqrt(
            Math.pow(x2 - x1, 2.0) + 
            Math.pow(y2 - y1, 2.0) + 
            Math.pow(z2 - z1, 2.0)
        )
        
        val points = Math.ceil(distance / 2.0).toInt() // Particle every 2 blocks
        
        for (i in 0..points) {
            val t = i.toDouble() / points
            val x = x1 + (x2 - x1) * t
            val y = y1 + (y2 - y1) * t
            val z = z1 + (z2 - z1) * t
            
            world.spawnParticle(
                Particle.DUST,
                Location(world, x, y, z),
                1,
                0.0, 0.0, 0.0, 0.0,
                Particle.DustOptions(Color.RED, 1.0f)
            )
        }
    }
    
    /**
     * Get the current schematic radius
     */
    fun getSchematicRadius(): Int {
        return schematicRadius
    }
    
    /**
     * Update the schematic radius and refresh particle display
     */
    fun setSchematicRadius(radius: Int) {
        if (radius < 10) {
            player.sendMessage(MSG.chatColors("&cSchematic radius must be at least 10!"))
            return
        }
        
        if (radius > 500) {
            player.sendMessage(MSG.chatColors("&cSchematic radius cannot exceed 500!"))
            return
        }
        
        schematicRadius = radius
        mapIOManager.updateSchematicRadius(mapName, radius)
        
        player.sendMessage(MSG.chatColors("&aSchematic radius updated to: &f$radius"))
        player.sendMessage(MSG.chatColors("&7The RED particle boundary has been updated"))
        
        debugLog("Updated schematic radius to $radius for map $mapName")
    }
    
    /**
     * Get the center location for the save region
     */
    fun getCenterLocation(): Location {
        return Location(tempWorld, centerX, centerY, centerZ)
    }
    
    /**
     * Get the save region bounds as a pair of locations
     */
    fun getSaveRegionBounds(): Pair<Location, Location> {
        val radius = schematicRadius.toDouble()
        
        val loc1 = Location(
            tempWorld,
            centerX - radius,
            Math.max(tempWorld.minHeight.toDouble(), centerY - radius),
            centerZ - radius
        )
        
        val loc2 = Location(
            tempWorld,
            centerX + radius,
            Math.min(tempWorld.maxHeight.toDouble(), centerY + radius),
            centerZ + radius
        )
        
        return Pair(loc1, loc2)
    }
    
    /**
     * Check if the session is still active
     */
    fun isActive(): Boolean {
        return isActive && player.isOnline
    }
    
    /**
     * End the session and cleanup
     */
    fun end(saveChanges: Boolean = false) {
        if (!isActive) {
            debugLog("Session already ended")
            return
        }
        
        isActive = false
        
        // Stop particle task
        particleTask?.cancel()
        particleTask = null
        
        debugLog("EditorSession ended for player ${player.name}, saveChanges: $saveChanges")
    }
    
    /**
     * Teleport player back to main world
     */
    fun returnPlayerToLobby() {
        val mainWorld = Bukkit.getWorlds().firstOrNull { it.environment == World.Environment.NORMAL }
        
        if (mainWorld != null) {
            player.teleport(mainWorld.spawnLocation)
            player.sendMessage(MSG.chatColors("&aTeleported back to spawn"))
        } else {
            player.sendMessage(MSG.chatColors("&cCould not find main world to teleport to!"))
        }
    }
    
    /**
     * Display session info to the player
     */
    fun showInfo() {
        player.sendMessage(MSG.chatColors("&6=== Editor Session Info ==="))
        player.sendMessage(MSG.chatColors("&eMap: &f$mapName"))
        player.sendMessage(MSG.chatColors("&eWorld: &f${tempWorld.name}"))
        player.sendMessage(MSG.chatColors("&eSchematic Radius: &f$schematicRadius"))
        player.sendMessage(MSG.chatColors("&eCenter: &f${centerX.toInt()}, ${centerY.toInt()}, ${centerZ.toInt()}"))
        player.sendMessage(MSG.chatColors("&7The RED particles show the save region"))
        player.sendMessage(MSG.chatColors("&7Use &e/pillars admin map resize <radius> &7to adjust"))
        player.sendMessage(MSG.chatColors("&7Use &e/pillars admin map save &7to save your changes"))
    }
    
    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[EditorSession] $message")
        }
    }
}
