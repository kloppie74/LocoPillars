package org.Kloppie74.locoPillars.game.map

import org.bukkit.*
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.editor.EditorHologramHelper
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
    private var saveHeight: Int = mapRepository.getSaveHeight(mapName)
    private var borderSize: Double = mapRepository.getBorderSize(mapName)
    private var borderEnabled: Boolean = mapRepository.loadMapSettings(mapName)
        ?.getBoolean("Border.Enabled", true)
        ?: true
    private var borderDamageAmount: Double = mapRepository.loadMapSettings(mapName)
        ?.getDouble("Border.DamageAmount", 1.0)
        ?: 1.0
    private var borderDamageBuffer: Double = mapRepository.loadMapSettings(mapName)
        ?.getDouble("Border.DamageBuffer", 5.0)
        ?: 5.0
    private var buildHeight: Int = mapRepository.loadMapSettings(mapName)
        ?.getInt("Settings.BuildHeight", 80)
        ?: 80
    private var buildRange: Int = mapRepository.loadMapSettings(mapName)
        ?.getInt("Settings.BuildRange", 50)
        ?: 50
    private var deathHeight: Int = mapRepository.loadMapSettings(mapName)
        ?.getInt("Settings.DeathHeight", -10)
        ?: -10
    private var lobbyTimer: Int = mapRepository.loadMapSettings(mapName)
        ?.getInt("Settings.LobbyTimer", 15)
        ?: 15
    private var cageTimer: Int = mapRepository.loadMapSettings(mapName)
        ?.getInt("Settings.CageTimer", 5)
        ?: 5
    private var gameTimer: Int = mapRepository.loadMapSettings(mapName)
        ?.getInt("Settings.GameTimer", 600)
        ?: 600
    private var itemTimer: Int = mapRepository.loadMapSettings(mapName)
        ?.getInt("Settings.ItemTimer", 10)
        ?: 10
    private var canBreakMapBlocks: Boolean = mapRepository.loadMapSettings(mapName)
        ?.getBoolean("Settings.Can-Break-Map-Blocks", false)
        ?: false
    private var lobbyEnabled: Boolean = mapRepository.loadMapSettings(mapName)
        ?.getBoolean("Settings.LobbyEnabled", true)
        ?: true
    private var hologramsEnabled: Boolean = mapRepository.loadMapSettings(mapName)
        ?.getBoolean("Editor.Holograms.Enabled", true)
        ?: true
    // ── New fields from full YML ──────────────────────────────────────────────
    private var minimumPlayers: Int = mapRepository.loadMapSettings(mapName)
        ?.getInt("SpawnLocations.MinimumPlayers", 2) ?: 2
    private var maximumPlayers: Int = mapRepository.loadMapSettings(mapName)
        ?.getInt("SpawnLocations.MaximumPlayers", 8) ?: 8
    private var maxTeams: Int = mapRepository.loadMapSettings(mapName)
        ?.getInt("SpawnLocations.MaxTeams", 8) ?: 8
    private var playersPerTeam: Int = mapRepository.loadMapSettings(mapName)
        ?.getInt("SpawnLocations.PlayersPerTeam", 1) ?: 1
    private var displayName: String = mapRepository.loadMapSettings(mapName)
        ?.getString("Settings.DisplayName", mapName) ?: mapName
    private var worldsAmount: Int = mapRepository.loadMapSettings(mapName)
        ?.getInt("Settings.Worlds-Amount", 10) ?: 10
    private var loadMap: Boolean = mapRepository.loadMapSettings(mapName)
        ?.getBoolean("Settings.LoadMap", true) ?: true
    // RandomItems.Type: "Blacklisted" | "Whitelisted" | "None"
    private var randomItemsType: String = mapRepository.loadMapSettings(mapName)
        ?.getString("Settings.RandomItems.Type", "Blacklisted") ?: "Blacklisted"
    private var blacklistedItems: MutableList<String> = mapRepository.loadMapSettings(mapName)
        ?.getStringList("Settings.RandomItems.BlacklistedItems")?.toMutableList() ?: mutableListOf()
    private var luckyBlocksEnabled: Boolean = mapRepository.loadMapSettings(mapName)
        ?.getBoolean("Settings.LuckyblockEnabled", false) ?: false
    private var luckyblockChance: Int = mapRepository.loadMapSettings(mapName)
        ?.getInt("Settings.LuckyblockChance", 100) ?: 100

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
        if (hologramsEnabled) {
            spawnSpawnHolograms()
        }
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
        val world    = tempWorld
        val xzRadius = saveRadius.toDouble()

        val minX = centerX - xzRadius
        val maxX = centerX + xzRadius
        val minY = world.minHeight.toDouble()
        val maxY = minOf(world.maxHeight.toDouble(), centerY + saveHeight.toDouble())
        val minZ = centerZ - xzRadius
        val maxZ = centerZ + xzRadius

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
            if (teamKey in reservedKeys) return@forEach

            val teamSection = spawnSection.getConfigurationSection(teamKey) ?: return@forEach
            val teamName = teamSection.getString("Name", teamKey) ?: teamKey

            teamSection.getKeys(false)
                .filter { it.startsWith("Player") }
                .forEach { playerKey ->
                    val playerSection = teamSection.getConfigurationSection(playerKey) ?: return@forEach
                    val x = playerSection.getDouble("X")
                    val y = playerSection.getDouble("Y")
                    val z = playerSection.getDouble("Z")
                    val location = Location(tempWorld, x, y + 2.0, z)

                    // EditorHologramHelper gebruikt Display.Billboard.CENTER zodat het
                    // hologram vanuit alle kanten (N/E/S/W) leesbaar is.
                    val display = EditorHologramHelper.spawn(
                        location,
                        "&e$teamName &7/ &f$playerKey",
                        "&7(&b${x.toInt()}&7, &b${y.toInt()}&7, &b${z.toInt()}&7)"
                    )
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


    fun getMinimumPlayers(): Int  = minimumPlayers
    fun getMaximumPlayers(): Int  = maximumPlayers
    fun getMaxTeams(): Int        = maxTeams
    fun getPlayersPerTeam(): Int  = playersPerTeam
    fun getDisplayName(): String  = displayName
    fun getWorldsAmount(): Int    = worldsAmount
    fun isLoadMap(): Boolean      = loadMap
    fun getRandomItemsType(): String = randomItemsType
    fun isLuckyBlocksEnabled(): Boolean = luckyBlocksEnabled
    fun getLuckyblockChance(): Int = luckyblockChance

    /**
     * Get the current schematic radius
     */
    fun getSaveRadius(): Int {
        return saveRadius
    }

    fun getBorderSize(): Double {
        return borderSize
    }

    fun isBorderEnabled(): Boolean = borderEnabled
    fun getBorderDamageAmount(): Double = borderDamageAmount
    fun getBorderDamageBuffer(): Double = borderDamageBuffer
    fun getBuildHeight(): Int = buildHeight
    fun getBuildRange(): Int = buildRange
    fun getDeathHeight(): Int = deathHeight
    fun getLobbyTimer(): Int = lobbyTimer
    fun getCageTimer(): Int = cageTimer
    fun getGameTimer(): Int = gameTimer
    fun getItemTimer(): Int = itemTimer
    fun canBreakMapBlocks(): Boolean = canBreakMapBlocks
    fun isLobbyEnabled(): Boolean = lobbyEnabled
    fun isHologramsEnabled(): Boolean = hologramsEnabled

    /**
     * Update the schematic radius and refresh particle display
     */
    fun setSaveRadius(radius: Int) {
        val clamped = radius.coerceIn(10, 500)
        saveRadius = clamped
        mapRepository.updateSaveRadius(mapName, clamped)
        player.sendActionBar(MSG.chatColors("&aSave XZ-radius: &f$clamped blocks"))
        debugLog("Updated save radius to $clamped for map $mapName")
    }

    fun getSaveHeight(): Int = saveHeight

    fun setSaveHeight(height: Int) {
        val clamped = height.coerceIn(20, 500)
        saveHeight = clamped
        mapRepository.updateSaveHeight(mapName, clamped)
        player.sendActionBar(MSG.chatColors("&aSave height: &f+$clamped blocks above center"))
        debugLog("Updated save height to $clamped for map $mapName")
    }

    fun getBlacklistedItems(): List<String> = blacklistedItems.toList()

    fun addBlacklistedItem(material: String): Boolean {
        if (blacklistedItems.contains(material)) return false
        blacklistedItems.add(material)
        persist("Settings.RandomItems.BlacklistedItems", blacklistedItems)
        return true
    }

    fun removeBlacklistedItem(material: String): Boolean {
        if (!blacklistedItems.remove(material)) return false
        persist("Settings.RandomItems.BlacklistedItems", blacklistedItems)
        return true
    }

    fun setBorderSize(size: Double) {
        val clamped = size.coerceIn(10.0, 1000.0)
        borderSize = clamped
        persist("Border.Size", clamped)
        player.sendMessage(MSG.chatColors("&aBorder size updated to: &f${clamped.toInt()}"))
    }

    fun toggleBorderEnabled() {
        borderEnabled = !borderEnabled
        persist("Border.Enabled", borderEnabled)
        player.sendMessage(MSG.chatColors("&aBorder visualization is now: &f${if (borderEnabled) "enabled" else "disabled"}"))
    }

    fun setBorderDamageAmount(amount: Double) {
        val clamped = amount.coerceIn(0.0, 20.0)
        borderDamageAmount = clamped
        persist("Border.DamageAmount", clamped)
        player.sendMessage(MSG.chatColors("&aBorder damage amount updated to: &f${"%.1f".format(clamped)}"))
    }

    fun setBorderDamageBuffer(buffer: Double) {
        val clamped = buffer.coerceIn(0.0, 100.0)
        borderDamageBuffer = clamped
        persist("Border.DamageBuffer", clamped)
        player.sendMessage(MSG.chatColors("&aBorder damage buffer updated to: &f${"%.1f".format(clamped)}"))
    }

    fun setBuildHeight(value: Int) {
        buildHeight = value.coerceIn(20, 320)
        persist("Settings.BuildHeight", buildHeight)
        player.sendMessage(MSG.chatColors("&aBuildHeight updated to: &f$buildHeight"))
    }

    fun setBuildRange(value: Int) {
        buildRange = value.coerceIn(10, 500)
        persist("Settings.BuildRange", buildRange)
        player.sendMessage(MSG.chatColors("&aBuildRange updated to: &f$buildRange"))
    }

    fun setDeathHeight(value: Int) {
        deathHeight = value.coerceIn(-128, 320)
        persist("Settings.DeathHeight", deathHeight)
        player.sendMessage(MSG.chatColors("&aDeathHeight updated to: &f$deathHeight"))
    }

    fun setLobbyTimer(value: Int) {
        lobbyTimer = value.coerceIn(5, 300)
        persist("Settings.LobbyTimer", lobbyTimer)
        player.sendMessage(MSG.chatColors("&aLobbyTimer updated to: &f$lobbyTimer"))
    }

    fun setCageTimer(value: Int) {
        cageTimer = value.coerceIn(1, 60)
        persist("Settings.CageTimer", cageTimer)
        player.sendMessage(MSG.chatColors("&aCageTimer updated to: &f$cageTimer"))
    }

    fun setGameTimer(value: Int) {
        gameTimer = value.coerceIn(60, 7200)
        persist("Settings.GameTimer", gameTimer)
        player.sendMessage(MSG.chatColors("&aGameTimer updated to: &f$gameTimer"))
    }

    fun setItemTimer(value: Int) {
        itemTimer = value.coerceIn(1, 300)
        persist("Settings.ItemTimer", itemTimer)
        player.sendMessage(MSG.chatColors("&aItemTimer updated to: &f$itemTimer"))
    }

    fun toggleBreakMapBlocks() {
        canBreakMapBlocks = !canBreakMapBlocks
        persist("Settings.Can-Break-Map-Blocks", canBreakMapBlocks)
        player.sendActionBar(MSG.chatColors("&aCan-Break-Map-Blocks: &f${if (canBreakMapBlocks) "enabled" else "disabled"}"))
    }

    fun toggleLobbyEnabled() {
        lobbyEnabled = !lobbyEnabled
        persist("Settings.LobbyEnabled", lobbyEnabled)
        player.sendActionBar(MSG.chatColors("&aLobby: &f${if (lobbyEnabled) "enabled" else "disabled"}"))
    }

    fun setHologramsEnabled(enabled: Boolean) {
        if (hologramsEnabled == enabled) {
            return
        }
        hologramsEnabled = enabled
        persist("Editor.Holograms.Enabled", enabled)
        if (enabled) {
            spawnSpawnHolograms()
        } else {
            clearSpawnHolograms()
        }
        player.sendMessage(MSG.chatColors("&aSpawn holograms ${if (enabled) "enabled" else "disabled"}"))
    }

    fun refreshHolograms() {
        clearSpawnHolograms()
        if (hologramsEnabled) {
            spawnSpawnHolograms()
        }
        player.sendMessage(MSG.chatColors("&aSpawn holograms refreshed."))
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
        val xzRadius = saveRadius.toDouble()
        val yTop     = minOf(tempWorld.maxHeight.toDouble(), centerY + saveHeight.toDouble())

        val loc1 = Location(tempWorld, centerX - xzRadius, tempWorld.minHeight.toDouble(), centerZ - xzRadius)
        val loc2 = Location(tempWorld, centerX + xzRadius, yTop, centerZ + xzRadius)

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
        clearSpawnHolograms()
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
        player.sendMessage(MSG.chatColors("&eSave Radius (X/Z): &f$saveRadius blocks"))
        player.sendMessage(MSG.chatColors("&eSave Height (Y):   &f+$saveHeight blocks above center  &8(floor: world min)"))
        player.sendMessage(MSG.chatColors("&eBorder Size: &f${borderSize.toInt()}"))
        player.sendMessage(MSG.chatColors("&eBorder Enabled: &f${if (borderEnabled) "yes" else "no"}"))
        player.sendMessage(MSG.chatColors("&eHolograms: &f${if (hologramsEnabled) "enabled" else "disabled"}"))
        player.sendMessage(MSG.chatColors("&eCenter: &f${centerX.toInt()}, ${centerY.toInt()}, ${centerZ.toInt()}"))
        player.sendMessage(MSG.chatColors("&7The RED particles show the save region"))
        player.sendMessage(MSG.chatColors("&7The GREEN particles show the play border"))
        player.sendMessage(MSG.chatColors("&7Use &e/editor resize <radius> &7to adjust XZ radius"))
        player.sendMessage(MSG.chatColors("&7Use &e/editor save &7to save your changes"))
    }

    private fun clearSpawnHolograms() {
        EditorHologramHelper.removeAll(spawnHolograms)
    }


    fun setMinimumPlayers(value: Int) {
        minimumPlayers = value.coerceIn(1, maximumPlayers)
        persist("SpawnLocations.MinimumPlayers", minimumPlayers)
        player.sendActionBar(MSG.chatColors("&aMin Players: &f$minimumPlayers"))
    }

    fun setMaximumPlayers(value: Int) {
        maximumPlayers = value.coerceIn(minimumPlayers, maxTeams * playersPerTeam)
        persist("SpawnLocations.MaximumPlayers", maximumPlayers)
        player.sendActionBar(MSG.chatColors("&aMax Players: &f$maximumPlayers"))
    }

    fun setMaxTeams(value: Int) {
        maxTeams = value.coerceIn(1, 100)
        persist("SpawnLocations.MaxTeams", maxTeams)
        player.sendActionBar(MSG.chatColors("&aMax Teams: &f$maxTeams"))
    }

    fun setPlayersPerTeam(value: Int) {
        playersPerTeam = value.coerceIn(1, 20)
        persist("SpawnLocations.PlayersPerTeam", playersPerTeam)
        player.sendActionBar(MSG.chatColors("&aPlayers Per Team: &f$playersPerTeam"))
    }

    fun setDisplayName(name: String) {
        displayName = name
        persist("Settings.DisplayName", name)
        player.sendActionBar(MSG.chatColors("&aDisplay name: &f$name"))
    }

    fun setWorldsAmount(value: Int) {
        worldsAmount = value.coerceIn(1, 200)
        persist("Settings.Worlds-Amount", worldsAmount)
        player.sendActionBar(MSG.chatColors("&aWorlds Amount: &f$worldsAmount"))
    }

    fun toggleLoadMap() {
        loadMap = !loadMap
        persist("Settings.LoadMap", loadMap)
        player.sendActionBar(MSG.chatColors("&aLoad Map: &f${if (loadMap) "enabled" else "disabled"}"))
    }

    fun cycleRandomItemsType() {
        randomItemsType = when (randomItemsType) {
            "Blacklisted"  -> "Whitelisted"
            "Whitelisted"  -> "None"
            else           -> "Blacklisted"
        }
        persist("Settings.RandomItems.Type", randomItemsType)
        player.sendActionBar(MSG.chatColors("&aRandom Items: &f$randomItemsType"))
    }

    fun toggleLuckyBlocks() {
        luckyBlocksEnabled = !luckyBlocksEnabled
        persist("Settings.LuckyblockEnabled", luckyBlocksEnabled)
        player.sendActionBar(MSG.chatColors("&aLucky Blocks: &f${if (luckyBlocksEnabled) "enabled" else "disabled"}"))
    }

    fun setLuckyblockChance(value: Int) {
        luckyblockChance = value.coerceIn(0, 100)
        persist("Settings.LuckyblockChance", luckyblockChance)
        player.sendActionBar(MSG.chatColors("&aLuckyblock Chance: &f$luckyblockChance%"))
    }

    private fun persist(path: String, value: Any) {
        val config = mapRepository.loadMapSettings(mapName) ?: return
        config.set(path, value)
        mapRepository.saveMapSettings(mapName, config)
    }

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[EditorSession] $message")
        }
    }
}