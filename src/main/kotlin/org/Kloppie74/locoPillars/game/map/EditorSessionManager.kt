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
            if (Bukkit.unloadWorld(world, false)) {
                val folder = File(Bukkit.getWorldContainer(), worldName)
                if (folder.exists()) {
                    folder.deleteRecursively()
                    debugLog("Temporary world deleted: $worldName")
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
        if (Bukkit.unloadWorld(world, false)) {
            val folder = File(Bukkit.getWorldContainer(), worldName)
            if (folder.exists()) folder.deleteRecursively()
        }
    }

    private fun createVoidWorld(worldName: String): World? = try {
        WorldCreator(worldName)
            .generator(VoidChunkGenerator())
            .generateStructures(false)
            .createWorld()
    } catch (e: Exception) {
        plugin.logger.severe("Failed to create void world: ${e.message}")
        null
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
    }
}