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
import org.Kloppie74.locoPillars.utils.deleteRecursivelyJava
import org.Kloppie74.locoPillars.utils.SchematicHelper
import java.io.File
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * EditorSessionManager - Manages all active editor sessions
 *
 * ### Fix 1 patch
 * [finishSessionStart] roept `adminEditorManager.enable(..., deferHotbar = true)` aan
 * (of respecteert dat het al actief is) en teleporteert daarna de speler naar
 * de edit world. De [PlayerChangedWorldEvent] in EditorModeListener geeft
 * vervolgens de hotbar — pas nádat de world-switch klaar is.
 */
class EditorSessionManager(
    private val plugin: LocoPillars,
    private val mapRepository: MapRepository
) {

    private val activeSessions  = mutableMapOf<UUID, EditorSession>()
    private val pendingSessions = mutableSetOf<UUID>()

    // -------------------------------------------------------------------------
    // Start session
    // -------------------------------------------------------------------------

    fun startSession(player: Player, mapName: String): Boolean {
        if (!mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
            return false
        }

        if (activeSessions.containsKey(player.uniqueId) || pendingSessions.contains(player.uniqueId)) {
            player.sendMessage(MSG.chatColors("&cYou already have an active editor session!"))
            player.sendMessage(MSG.chatColors("&7Use &e/editor cancel &7to end it"))
            return false
        }

        pendingSessions.add(player.uniqueId)

        val worldName = "pillars_edit_${mapName}_${UUID.randomUUID().toString().substring(0, 8)}"
        val world = createVoidWorld(worldName)

        if (world == null) {
            player.sendMessage(MSG.chatColors("&cFailed to create the editor world!"))
            pendingSessions.remove(player.uniqueId)
            return false
        }

        val schemFile   = mapRepository.getMapSchematicFile(mapName)
        val settings    = mapRepository.loadMapSettings(mapName)
        val centerX     = settings?.getDouble("Settings.SpawnPoint.X", 0.0) ?: 0.0
        val centerY     = settings?.getDouble("Settings.SpawnPoint.Y", 65.0) ?: 65.0
        val centerZ     = settings?.getDouble("Settings.SpawnPoint.Z", 0.0) ?: 0.0
        val saveRadius  = mapRepository.getSaveRadius(mapName).toDouble()

        // Paste origin = the minimum corner of the save region, matching
        // getSaveRegionBounds() loc1 exactly. Y always starts at world floor
        // so all block Y-coordinates are preserved after a round-trip save/load.
        val pasteX      = centerX - saveRadius
        val pasteY      = world.minHeight.toDouble()
        val pasteZ      = centerZ - saveRadius
        val centerLoc   = Location(world, pasteX, pasteY, pasteZ)

        if (schemFile.exists()) {
            player.sendMessage(MSG.chatColors("&eLoading schematic (async)..."))
            loadSchematicAsync(schemFile)
                .thenCompose { clipboard ->
                    if (clipboard == null) CompletableFuture.completedFuture(false)
                    else pasteClipboardAsync(clipboard, centerLoc)
                }
                .exceptionally { ex ->
                    plugin.logger.severe(
                        "[EditorSessionManager] Failed to load schematic for $mapName: ${ex.message}")
                    false
                }
                .whenComplete { success, _ ->
                    runOnMain { finishSessionStart(player, mapName, world, worldName, success, true) }
                }
        } else {
            finishSessionStart(player, mapName, world, worldName, false, false)
        }

        return true
    }

    // -------------------------------------------------------------------------
    // ### Fix 1 – finishSessionStart
    // -------------------------------------------------------------------------

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
                player.sendMessage(MSG.chatColors("&aExisting schematic loaded for map: &f$mapName"))
            } else {
                player.sendMessage(MSG.chatColors("&cFailed to load schematic, starting with empty world"))
                applyBedrockAnchor(world, mapName, updateSettings = false)
            }
        } else {
            player.sendMessage(MSG.chatColors("&7No schematic found, starting with bedrock anchor"))
            applyBedrockAnchor(world, mapName, updateSettings = true)
        }

        val session = EditorSession(player, mapName, world, plugin, mapRepository)
        activeSessions[player.uniqueId] = session

        // ### Fix 1: zorg dat edit mode actief is met deferHotbar=true
        // zodat de hotbar pas wordt gegeven ná de teleport (via PlayerChangedWorldEvent).
        val adminEditorManager = plugin.adminEditorManager
        if (!adminEditorManager.isActive(player)) {
            // Eerste keer: activeer edit mode met uitgestelde hotbar
            adminEditorManager.enable(player, mapName, deferHotbar = true)
        } else {
            // Al actief (bijv. via /editor create → enable() al eerder aangeroepen):
            // Reset hotbarEnabled naar false zodat onPlayerArrivedInEditWorld()
            // de hotbar opnieuw geeft ná de teleport.
            adminEditorManager.getState(player)?.hotbarEnabled = false
        }

        // Teleporteer naar de edit world — dit triggert PlayerChangedWorldEvent
        // → EditorModeListener → onPlayerArrivedInEditWorld() → hotbar + berichten
        player.gameMode = GameMode.CREATIVE
        player.teleport(world.getBlockAt(0, 100, 0).location)

        debugLog("Editor session started for ${player.name} on map $mapName")
    }

    // -------------------------------------------------------------------------
    // Schematic helpers (ongewijzigd)
    // -------------------------------------------------------------------------

    private fun loadSchematicAsync(schemFile: File): CompletableFuture<Clipboard?> =
        scheduleTask(true) {
            SchematicHelper.loadSchematic(plugin, plugin.configManager, schemFile)
        }

    private fun pasteClipboardAsync(clipboard: Clipboard, location: Location): CompletableFuture<Boolean> =
        scheduleTask(isFaweAvailable()) {
            SchematicHelper.pasteClipboard(plugin, plugin.configManager, clipboard, location)
        }

    private fun saveSchematicAsync(loc1: Location, loc2: Location, schemFile: File): CompletableFuture<Boolean> =
        scheduleTask(isFaweAvailable()) {
            SchematicHelper.saveSchematic(plugin, plugin.configManager, loc1, loc2, schemFile)
        }

    private fun isFaweAvailable(): Boolean {
        val provider = plugin.configManager.worldEditProvider
        val faweInstalled = Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null
        return when (provider) {
            "FAWE"       -> faweInstalled
            "WORLDEDIT"  -> false
            else         -> faweInstalled // AUTO
        }
    }

    // -------------------------------------------------------------------------
    // Save / Cancel / End (ongewijzigd)
    // -------------------------------------------------------------------------

    /**
     * Save a schematic from the current session's save region to an arbitrary file.
     * Callback is invoked on the main thread with the success boolean.
     */
    fun saveSchematicToFile(player: Player, loc1: org.bukkit.Location, loc2: org.bukkit.Location, schemFile: java.io.File, callback: (Boolean) -> Unit) {
        schemFile.parentFile?.mkdirs()
        saveSchematicAsync(loc1, loc2, schemFile)
            .exceptionally { ex ->
                plugin.logger.severe("[EditorSessionManager] Failed to save schematic to ${schemFile.name}: ${ex.message}")
                false
            }
            .whenComplete { success, _ -> runOnMain { callback(success ?: false) } }
    }

    /**
     * Load a schematic file and paste it into the player's active editor session world.
     * Pastes at the session's minimum corner (world floor) so coordinates are preserved.
     * Callback is invoked on the main thread with the success boolean.
     */
    fun loadSchematicIntoSession(player: Player, schemFile: java.io.File, callback: (Boolean) -> Unit) {
        val session = getSession(player) ?: run { callback(false); return }
        val pasteLocation = session.getSaveRegionBounds().first

        loadSchematicAsync(schemFile)
            .thenCompose { clipboard ->
                if (clipboard == null) CompletableFuture.completedFuture(false)
                else pasteClipboardAsync(clipboard, pasteLocation)
            }
            .exceptionally { ex ->
                plugin.logger.severe("[EditorSessionManager] Failed to load schematic ${schemFile.name}: ${ex.message}")
                false
            }
            .whenComplete { success, _ -> runOnMain { callback(success ?: false) } }
    }

    fun saveAndEndSession(player: Player, onComplete: ((Boolean) -> Unit)? = null): Boolean {
        val session = activeSessions[player.uniqueId] ?: run {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            return false
        }

        if (!session.isActive()) {
            player.sendMessage(MSG.chatColors("&cYour editor session is no longer active!"))
            activeSessions.remove(player.uniqueId)
            return false
        }

        player.sendMessage(MSG.chatColors("&eSaving map changes..."))

        val mapName  = session.mapName
        val (loc1, loc2) = session.getSaveRegionBounds()
        val schemFile    = mapRepository.getMapSchematicFile(mapName)
        schemFile.parentFile?.mkdirs()

        saveSchematicAsync(loc1, loc2, schemFile)
            .exceptionally { ex ->
                plugin.logger.severe(
                    "[EditorSessionManager] Failed to save schematic for $mapName: ${ex.message}")
                false
            }
            .whenComplete { success, _ ->
                runOnMain {
                    if (!success) {
                        player.sendMessage(MSG.chatColors("&cFailed to save schematic!"))
                        player.sendMessage(MSG.chatColors("&7Your session will remain active"))
                        onComplete?.invoke(false)
                        return@runOnMain
                    }

                    player.sendMessage(MSG.chatColors("&aSuccessfully saved: &f$mapName"))
                    player.sendMessage(MSG.chatColors("&7Schematic saved to: &f${schemFile.name}"))

                    mapRepository.loadMapSettings(mapName)?.let { config ->
                        config.set("SaveRadius",      session.getSaveRadius())
                        config.set("SchematicRadius", session.getSaveRadius())
                        mapRepository.saveMapSettings(mapName, config)
                    }

                    debugLog("Map $mapName saved")
                    endSession(player, true)
                    onComplete?.invoke(true)
                }
            }

        return true
    }

    fun cancelSession(player: Player): Boolean {
        activeSessions[player.uniqueId] ?: run {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            return false
        }
        player.sendMessage(MSG.chatColors("&eEditor session cancelled (no changes saved)"))
        endSession(player, false)
        return true
    }

    private fun endSession(player: Player, saveChanges: Boolean) {
        val session   = activeSessions.remove(player.uniqueId) ?: return
        val worldName = session.tempWorld.name

        session.end(saveChanges)
        session.returnPlayerToLobby()

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val world = Bukkit.getWorld(worldName) ?: return@Runnable
            val mainWorld = Bukkit.getWorlds().firstOrNull {
                it.environment == World.Environment.NORMAL
            }
            world.players.forEach { p ->
                if (mainWorld != null) p.teleport(mainWorld.spawnLocation)
            }
            // Resolve folder BEFORE unloading — world.worldFolder is unavailable afterwards.
            val folder = resolveWorldFolder(worldName, world)
            if (Bukkit.unloadWorld(world, false)) {
                if (folder != null && folder.exists()) {
                    folder.deleteRecursivelyJava()
                    debugLog("Temporary world deleted: $worldName (${folder.absolutePath})")
                } else {
                    debugLog("World folder for $worldName not found — skipping delete")
                }
            }
        }, 20L)

        debugLog("Editor session ended for ${player.name}")
    }

    // -------------------------------------------------------------------------
    // Queries (ongewijzigd)
    // -------------------------------------------------------------------------

    fun getSession(player: Player): EditorSession?  = activeSessions[player.uniqueId]
    fun hasSession(player: Player): Boolean         = activeSessions.containsKey(player.uniqueId)

    fun resizeSession(player: Player, radius: Int): Boolean {
        val session = activeSessions[player.uniqueId] ?: run {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            return false
        }
        session.setSaveRadius(radius)
        return true
    }

    fun showSessionInfo(player: Player): Boolean {
        val session = activeSessions[player.uniqueId] ?: run {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            return false
        }
        session.showInfo()
        return true
    }

    fun cleanup() {
        activeSessions.values.toList().forEach { session ->
            if (session.player.isOnline) {
                session.player.sendMessage(MSG.chatColors("&cEditor session ended due to plugin reload"))
                session.returnPlayerToLobby()
            }
            session.end(false)
        }
        activeSessions.clear()
        debugLog("All editor sessions cleaned up")
    }

    // -------------------------------------------------------------------------
    // World helpers (ongewijzigd)
    // -------------------------------------------------------------------------

    private fun applyBedrockAnchor(world: World, mapName: String, updateSettings: Boolean) {
        world.getBlockAt(0, 0, 0).type = Material.BEDROCK
        if (!updateSettings) return
        mapRepository.loadMapSettings(mapName)?.let { config ->
            config.set("Settings.SpawnPoint.X", 0.0)
            config.set("Settings.SpawnPoint.Y", 1.0)
            config.set("Settings.SpawnPoint.Z", 0.0)
            mapRepository.saveMapSettings(mapName, config)
        }
    }

    private fun cleanupWorld(worldName: String) {
        val world     = Bukkit.getWorld(worldName) ?: return
        val mainWorld = Bukkit.getWorlds().firstOrNull { it.environment == World.Environment.NORMAL }
        if (mainWorld != null) world.players.forEach { it.teleport(mainWorld.spawnLocation) }
        val folder = resolveWorldFolder(worldName, world)
        if (Bukkit.unloadWorld(world, false)) {
            folder?.takeIf { it.exists() }?.deleteRecursivelyJava()
        }
    }

    /**
     * Resolves the on-disk folder for a world, supporting both storage layouts:
     *
     * - **Classic** (< Paper 1.21.2): `<worldContainer>/<worldName>/`
     * - **Dimension** (≥ Paper 1.21.2): `<worldContainer>/world/dimensions/minecraft/<worldName>/`
     *
     * Always pass [loadedWorld] when the world is still loaded — `world.worldFolder`
     * is the most reliable source and works regardless of layout. Falls back to
     * path-based probing when the world object is no longer available.
     */
    private fun resolveWorldFolder(worldName: String, loadedWorld: World? = null): File? {
        // 1. Most reliable: ask the loaded world object directly.
        loadedWorld?.worldFolder?.takeIf { it.exists() }?.let { return it }

        val container = Bukkit.getWorldContainer()

        // 2. Classic layout: <container>/<worldName>/
        File(container, worldName).takeIf { it.exists() }?.let { return it }

        // 3. New dimension layout (Paper ≥ 1.21.2):
        //    <container>/world/dimensions/minecraft/<worldName>/
        File(container, "world/dimensions/minecraft/$worldName").takeIf { it.exists() }?.let { return it }

        return null
    }

    private fun createVoidWorld(worldName: String): World? = try {
        WorldCreator(worldName)
            .generator(VoidChunkGenerator())
            .biomeProvider(VoidBiomeProvider)
            .generateStructures(false)
            .createWorld()
            ?.also { configureEditorWorld(it) }
    } catch (e: Exception) {
        plugin.logger.severe("Failed to create void world: ${e.message}")
        null
    }

    /**
     * Lock down an editor world: no mobs, no growth/spread, no weather. Combined
     * with the void generator this keeps the edit world completely empty so
     * neither vanilla nor custom-content plugins (CraftEngine, ItemsAdder, …) can
     * spawn mobs, crops or flowers in it.
     */
    private fun configureEditorWorld(world: World) {
        // No mobs of any kind
        world.difficulty = org.bukkit.Difficulty.PEACEFUL
        world.setSpawnFlags(false, false)            // (allowMonsters, allowAnimals)
        runCatching { world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false) }
        runCatching { world.setGameRule(org.bukkit.GameRule.DO_PATROL_SPAWNING, false) }
        runCatching { world.setGameRule(org.bukkit.GameRule.DO_TRADER_SPAWNING, false) }
        runCatching { world.setGameRule(org.bukkit.GameRule.DISABLE_RAIDS, true) }

        // No block growth / spreading (stops crops & flowers from growing/spreading)
        runCatching { world.setGameRule(org.bukkit.GameRule.RANDOM_TICK_SPEED, 0) }
        runCatching { world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, false) }

        // Static, clear environment
        runCatching { world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false) }
        runCatching { world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false) }
        world.setStorm(false)
        world.isThundering = false
        world.time = 6000L
    }

    private fun <T> scheduleTask(async: Boolean, task: () -> T): CompletableFuture<T> {
        val future  = CompletableFuture<T>()
        if (!ensurePluginEnabled(future)) return future
        val runnable = Runnable {
            if (!ensurePluginEnabled(future)) return@Runnable
            try { future.complete(task()) } catch (e: Exception) { future.completeExceptionally(e) }
        }
        try {
            if (async) Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable)
            else       Bukkit.getScheduler().runTask(plugin, runnable)
        } catch (e: IllegalStateException) {
            future.completeExceptionally(e)
        }
        return future
    }

    private fun <T> ensurePluginEnabled(future: CompletableFuture<T>): Boolean {
        if (!plugin.isEnabled) {
            future.completeExceptionally(IllegalStateException("Plugin uitgeschakeld"))
            return false
        }
        return true
    }

    private fun runOnMain(task: () -> Unit) =
        Bukkit.getScheduler().runTask(plugin, Runnable { task() })

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) plugin.logger.info("[EditorSessionManager] $message")
    }

    // -------------------------------------------------------------------------
    // Void generator (ongewijzigd)
    // -------------------------------------------------------------------------

    private class VoidChunkGenerator : ChunkGenerator() {
        override fun generateChunkData(
            world: World, random: java.util.Random, x: Int, z: Int, biome: BiomeGrid
        ): ChunkData = createChunkData(world)

        override fun canSpawn(world: World, x: Int, z: Int): Boolean = true
        override fun getDefaultPopulators(world: World): List<org.bukkit.generator.BlockPopulator> =
            emptyList()

        // Skip EVERY generation step. This stops not only vanilla decoration but
        // also custom-content plugins (CraftEngine, ItemsAdder, Oraxen, …) that
        // inject their crops/flowers/features into the decoration & mob steps —
        // which is why they were appearing mid-air in the void editor world.
        override fun shouldGenerateNoise(): Boolean = false
        override fun shouldGenerateSurface(): Boolean = false
        override fun shouldGenerateCaves(): Boolean = false
        override fun shouldGenerateDecorations(): Boolean = false
        override fun shouldGenerateMobs(): Boolean = false
        override fun shouldGenerateStructures(): Boolean = false

        // Force the barren THE_VOID biome everywhere so nothing is biome-driven
        // into spawning (vegetation/mobs are tied to biome decoration).
        override fun getDefaultBiomeProvider(worldInfo: org.bukkit.generator.WorldInfo): org.bukkit.generator.BiomeProvider =
            VoidBiomeProvider
    }

    private object VoidBiomeProvider : org.bukkit.generator.BiomeProvider() {
        override fun getBiome(worldInfo: org.bukkit.generator.WorldInfo, x: Int, y: Int, z: Int): org.bukkit.block.Biome =
            org.bukkit.block.Biome.THE_VOID

        override fun getBiomes(worldInfo: org.bukkit.generator.WorldInfo): List<org.bukkit.block.Biome> =
            listOf(org.bukkit.block.Biome.THE_VOID)
    }
}