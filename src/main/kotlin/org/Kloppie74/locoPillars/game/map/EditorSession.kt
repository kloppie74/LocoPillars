package org.Kloppie74.locoPillars.game.map

import net.kyori.adventure.text.Component
import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
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
    private val mapRepository: MapRepository
) {
    
    val sessionId: UUID = UUID.randomUUID()
    private var saveRadius: Int = mapRepository.getSaveRadius(mapName)
    private var borderSize: Double = mapRepository.getBorderSize(mapName)
    private val borderEnabled: Boolean = mapRepository.loadMapSettings(mapName)
        ?.getBoolean("Border.Enabled", true)
        ?: true
    private var particleTask: BukkitTask? = null
    private val spawnHolograms = mutableListOf<TextDisplay>()
    private var isActive: Boolean = true
    private val originalGameMode = player.gameMode
    
    // Center point for schematic save (usually 0,0,0 or the map center)
    private val centerX: Double = mapRepository.loadMapSettings(mapName)
        ?.getDouble("Settings.SpawnPoint.X", 0.0)
        ?: 0.0
    private val centerY: Double = mapRepository.loadMapSettings(mapName)
        ?.getDouble("Settings.SpawnPoint.Y", 65.0)
        ?: 65.0
    private val centerZ: Double = mapRepository.loadMapSettings(mapName)
        ?.getDouble("Settings.SpawnPoint.Z", 0.0)
        ?: 0.0
    
    init {
        debugLog("EditorSession created for player ${player.name}, map: $mapName")
        startParticleVisualization()
        spawnSpawnHolograms()
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
                displayBorderParticles()
            }
        }.runTaskTimer(plugin, 0L, 10L) // Run every 0.5 seconds
        
        debugLog("Started particle visualization task")
    }
    
    /**
     * Display RED particles outlining the save region
     */
    private fun displaySaveRegionParticles() {
        val world = tempWorld
        val radius = saveRadius.toDouble()
        
        // Calculate bounds
        val minX = centerX - radius
        val maxX = centerX + radius
        val minY = Math.max(world.minHeight.toDouble(), centerY - radius)
        val maxY = Math.min(world.maxHeight.toDouble(), centerY + radius)
        val minZ = centerZ - radius
        val maxZ = centerZ + radius
        
        // Draw cuboid outline with particles
        drawParticleLine(world, minX, minY, minZ, maxX, minY, minZ, Color.RED) // Bottom edge 1
        drawParticleLine(world, minX, minY, maxZ, maxX, minY, maxZ, Color.RED) // Bottom edge 2
        drawParticleLine(world, minX, minY, minZ, minX, minY, maxZ, Color.RED) // Bottom edge 3
        drawParticleLine(world, maxX, minY, minZ, maxX, minY, maxZ, Color.RED) // Bottom edge 4
        
        drawParticleLine(world, minX, maxY, minZ, maxX, maxY, minZ, Color.RED) // Top edge 1
        drawParticleLine(world, minX, maxY, maxZ, maxX, maxY, maxZ, Color.RED) // Top edge 2
        drawParticleLine(world, minX, maxY, minZ, minX, maxY, maxZ, Color.RED) // Top edge 3
        drawParticleLine(world, maxX, maxY, minZ, maxX, maxY, maxZ, Color.RED) // Top edge 4
        
        drawParticleLine(world, minX, minY, minZ, minX, maxY, minZ, Color.RED) // Vertical edge 1
        drawParticleLine(world, maxX, minY, minZ, maxX, maxY, minZ, Color.RED) // Vertical edge 2
        drawParticleLine(world, minX, minY, maxZ, minX, maxY, maxZ, Color.RED) // Vertical edge 3
        drawParticleLine(world, maxX, minY, maxZ, maxX, maxY, maxZ, Color.RED) // Vertical edge 4
    }

    private fun displayBorderParticles() {
        if (!borderEnabled || borderSize <= 0.0) {
            return
        }

        val world = tempWorld
        val radius = borderSize / 2.0
        val minX = centerX - radius
        val maxX = centerX + radius
        val minY = world.minHeight.toDouble()
        val maxY = world.maxHeight.toDouble()
        val minZ = centerZ - radius
        val maxZ = centerZ + radius

        drawParticleLine(world, minX, minY, minZ, maxX, minY, minZ, Color.LIME)
        drawParticleLine(world, minX, minY, maxZ, maxX, minY, maxZ, Color.LIME)
        drawParticleLine(world, minX, minY, minZ, minX, minY, maxZ, Color.LIME)
        drawParticleLine(world, maxX, minY, minZ, maxX, minY, maxZ, Color.LIME)

        drawParticleLine(world, minX, maxY, minZ, maxX, maxY, minZ, Color.LIME)
        drawParticleLine(world, minX, maxY, maxZ, maxX, maxY, maxZ, Color.LIME)
        drawParticleLine(world, minX, maxY, minZ, minX, maxY, maxZ, Color.LIME)
        drawParticleLine(world, maxX, maxY, minZ, maxX, maxY, maxZ, Color.LIME)

        drawParticleLine(world, minX, minY, minZ, minX, maxY, minZ, Color.LIME)
        drawParticleLine(world, maxX, minY, minZ, maxX, maxY, minZ, Color.LIME)
        drawParticleLine(world, minX, minY, maxZ, minX, maxY, maxZ, Color.LIME)
        drawParticleLine(world, maxX, minY, maxZ, maxX, maxY, maxZ, Color.LIME)
    }

    private fun spawnSpawnHolograms() {
        val config = mapRepository.loadMapSettings(mapName) ?: return
        val spawnSection = config.getConfigurationSection("SpawnLocations") ?: return
        val reservedKeys = setOf("MinimumPlayers", "MaximumPlayers", "MaxTeams", "PlayersPerTeam")

        spawnSection.getKeys(false).forEach { teamKey ->
            if (teamKey in reservedKeys) {
                return@forEach
            }

            val teamSection = spawnSection.getConfigurationSection(teamKey) ?: return@forEach
            val teamName = teamSection.getString("Name", teamKey)

            teamSection.getKeys(false)
                .filter { it.startsWith("Player") }
                .forEach { playerKey ->
                    val playerSection = teamSection.getConfigurationSection(playerKey) ?: return@forEach
                    val x = playerSection.getDouble("X")
                    val y = playerSection.getDouble("Y")
                    val z = playerSection.getDouble("Z")
                    val location = Location(tempWorld, x, y + 1.5, z)
                    val display = tempWorld.spawn(location, TextDisplay::class.java)
                    display.text(Component.text("$teamName $playerKey"))
                    display.isPersistent = false
                    spawnHolograms.add(display)
                }
        }
    }
    
    /**
     * Draw a line of particles between two points
     */
    private fun drawParticleLine(
        world: World,
        x1: Double,
        y1: Double,
        z1: Double,
        x2: Double,
        y2: Double,
        z2: Double,
        color: Color
    ) {
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
                Particle.DustOptions(color, 1.0f)
            )
        }
    }
    
    /**
     * Get the current schematic radius
     */
    fun getSaveRadius(): Int {
        return saveRadius
    }

    fun getBorderSize(): Double {
        return borderSize
    }
    
    /**
     * Update the schematic radius and refresh particle display
     */
    fun setSaveRadius(radius: Int) {
        if (radius < 10) {
            player.sendMessage(MSG.chatColors("&cSave radius must be at least 10!"))
            return
        }
        
        if (radius > 500) {
            player.sendMessage(MSG.chatColors("&cSave radius cannot exceed 500!"))
            return
        }
        
        saveRadius = radius
        mapRepository.updateSaveRadius(mapName, radius)
        
        player.sendMessage(MSG.chatColors("&aSave radius updated to: &f$radius"))
        player.sendMessage(MSG.chatColors("&7The RED particle boundary has been updated"))
        
        debugLog("Updated save radius to $radius for map $mapName")
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
        val radius = saveRadius.toDouble()
        
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
        spawnHolograms.forEach { it.remove() }
        spawnHolograms.clear()
        player.gameMode = originalGameMode
        
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
        player.sendMessage(MSG.chatColors("&eSave Radius: &f$saveRadius"))
        player.sendMessage(MSG.chatColors("&eBorder Size: &f${borderSize.toInt()}"))
        player.sendMessage(MSG.chatColors("&eCenter: &f${centerX.toInt()}, ${centerY.toInt()}, ${centerZ.toInt()}"))
        player.sendMessage(MSG.chatColors("&7The RED particles show the save region"))
        player.sendMessage(MSG.chatColors("&7The GREEN particles show the play border"))
        player.sendMessage(MSG.chatColors("&7Use &e/pillars admin map resize <radius> &7to adjust"))
        player.sendMessage(MSG.chatColors("&7Use &e/pillars admin map save &7to save your changes"))
    }
    
    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[EditorSession] $message")
        }
    }
}
