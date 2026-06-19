package org.Kloppie74.locoPillars.game.arena

import org.Kloppie74.locoPillars.utils.config.ConfigManager
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.block.Biome
import org.bukkit.generator.BiomeProvider
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.Random

/**
 * Manages the single shared arena world ("LocoPillars_Gameworld").
 *
 * Architecture (single-world grid):
 *   All arena instances live in ONE void world.  FAWE pastes each map into a
 *   unique X-offset slot (index × 5000 blocks) during startup, so no two arenas
 *   can ever interfere.  A single world has far less overhead than 40 separate
 *   worlds: one game loop, one entity tracker, and Paper's chunk eviction
 *   naturally unloads idle arena cells when no players are nearby.
 */
class ArenaWorldManager(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager
) {
    companion object {
        const val GAMEWORLD_NAME = "LocoPillars_Gameworld"
    }

    private var gameWorld: World? = null

    init {
        debugLog("Initialized. All arenas will be placed in '$GAMEWORLD_NAME'.")
    }

    private fun debugLog(message: String) {
        if (configManager.debugMode) plugin.logger.info("[ArenaWorldManager] $message")
    }

    /** Folder on disk where Paper stores this world's region files. */
    private fun worldFolder(): File = File(plugin.server.worldContainer, GAMEWORLD_NAME)

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns the single arena gameworld, creating it if it does not yet exist.
     * Safe to call multiple times — subsequent calls return the cached reference.
     *
     * Throws [RuntimeException] only if Bukkit's world creation itself returns null
     * (should never happen under normal Paper operation).
     */
    fun ensureGameWorld(): World {
        // Fast path: in-memory reference still valid
        gameWorld?.takeIf { Bukkit.getWorld(it.name) != null }?.let { return it }

        // Already loaded (server reload / plugin reload scenario)
        Bukkit.getWorld(GAMEWORLD_NAME)?.let { existing ->
            debugLog("'$GAMEWORLD_NAME' already loaded — reconfiguring.")
            configureWorld(existing)
            existing.isAutoSave = false
            gameWorld = existing
            return existing
        }

        // Create from scratch.
        // Delete any stale folder from a previous session: we always repaste everything
        // from schematics, so old region files are worthless and only slow down shutdown
        // (Paper would try to save 60 k+ dirty chunks if we left them on disk).
        val staleFolder = worldFolder()
        if (staleFolder.exists()) {
            staleFolder.deleteRecursively()
            plugin.logger.info("[ArenaWorldManager] Deleted stale '$GAMEWORLD_NAME' folder — fresh void world will be created.")
        }

        debugLog("Creating void world '$GAMEWORLD_NAME'…")
        val creator = WorldCreator(GAMEWORLD_NAME)
            .environment(World.Environment.NORMAL)
            .generator(VoidChunkGenerator())
            .biomeProvider(PlainsBiomeProvider())
            .generateStructures(false)

        val world = creator.createWorld()
            ?: throw RuntimeException("WorldCreator returned null for '$GAMEWORLD_NAME'")

        configureWorld(world)
        world.isAutoSave = false   // belt-and-suspenders alongside configureWorld()
        gameWorld = world
        plugin.logger.info("[ArenaWorldManager] ✓ '$GAMEWORLD_NAME' created and configured.")
        return world
    }

    /** Returns the cached gameworld reference, or looks it up from Bukkit if not yet cached. */
    fun getGameWorld(): World? = gameWorld ?: Bukkit.getWorld(GAMEWORLD_NAME)

    /**
     * Backward-compatible: returns the single gameworld in a map keyed by name.
     * Callers in [LocoPillars.onDisable] use [Map.size] for logging.
     */
    fun getAllArenaWorlds(): Map<String, World> =
        getGameWorld()?.let { mapOf(it.name to it) } ?: emptyMap()

    /**
     * Unloads any leftover `pillars_*` worlds from the old multi-world
     * architecture so they no longer consume memory after an upgrade.
     */
    fun cleanupLegacyPillarsWorlds() {
        val legacy = plugin.server.worlds.filter { it.name.startsWith("pillars_") }
        if (legacy.isEmpty()) return
        plugin.logger.info(
            "[ArenaWorldManager] Unloading ${legacy.size} legacy pillars_ world(s) from a previous installation."
        )
        legacy.forEach { world ->
            plugin.server.unloadWorld(world, false)
            debugLog("Unloaded legacy world: ${world.name}")
        }
    }

    /**
     * Called from [LocoPillars.onDisable].
     *
     * Unloads the gameworld WITHOUT saving, then deletes its folder.
     *
     * Why delete the folder?
     *   Paper's shutdown performs a forced save on every world still in its
     *   internal level list.  If [org.bukkit.Server.unloadWorld] succeeds the
     *   world is removed from that list and Paper never touches it.  But if
     *   the unload fails for any reason (a stray player still in the world,
     *   a Paper internal lock, etc.), we delete the region folder so that even
     *   if Paper does try to write chunks it finds nothing — the shutdown
     *   completes instantly regardless.
     *
     *   The next startup always rebuilds the world fresh from schematics anyway,
     *   so there is zero value in keeping region files between sessions.
     */
    fun cleanup() {
        cleanupLegacyPillarsWorlds()
        val world = gameWorld
        gameWorld = null
        if (world == null || Bukkit.getWorld(world.name) == null) return

        // Re-confirm autosave is off — defensive against anything that may have
        // re-enabled it (e.g. a plugin reload or a Paper internals quirk).
        world.isAutoSave = false

        // Evacuate any players still in the arena world so unloadWorld() can succeed.
        // LocoPillars.onDisable() already teleports players out via gameManager.cleanup(),
        // but spectators or crash-orphaned sessions can slip through — catch them here.
        val stranded = world.players.toList()
        if (stranded.isNotEmpty()) {
            val safeSpot = plugin.server.worlds
                .firstOrNull { it.name != GAMEWORLD_NAME && it.environment == World.Environment.NORMAL }
                ?.spawnLocation
            stranded.forEach { player ->
                if (safeSpot != null) player.teleport(safeSpot)
                else player.kickPlayer("Server is restarting")
            }
            plugin.logger.info("[ArenaWorldManager] Evacuated ${stranded.size} player(s) from '${world.name}'.")
        }

        // Unload without saving.
        val unloaded = plugin.server.unloadWorld(world, false)
        if (unloaded) {
            plugin.logger.info("[ArenaWorldManager] ✓ '${world.name}' unloaded (not saved).")
        } else {
            plugin.logger.warning(
                "[ArenaWorldManager] ✗ Could not unload '${world.name}' — " +
                "folder will be deleted as a failsafe to prevent a slow shutdown save."
            )
        }

        // Delete the world folder.  Whether or not unload succeeded:
        //   • If unload succeeded: folder deletion removes stale region files so the
        //     next startup creates a genuinely blank void world.
        //   • If unload failed:    Paper cannot write chunks to a folder that no longer
        //     exists → shutdown completes instantly instead of spending 10+ minutes
        //     serialising 60 000+ chunks to disk.
        val folder = worldFolder()
        if (folder.exists()) {
            val deleted = folder.deleteRecursively()
            plugin.logger.info(
                if (deleted)
                    "[ArenaWorldManager] ✓ '${world.name}' folder deleted — shutdown will be instant."
                else
                    "[ArenaWorldManager] ✗ Could not fully delete '${world.name}' folder — shutdown may be slow."
            )
        }
    }

    // Legacy method names so existing call sites in LocoPillars.kt compile unchanged.
    fun unloadAllGameWorlds() = cleanup()
    fun deleteAllUnloadedPillarsWorldFolders() { /* no-op in single-world mode */ }

    // ── World configuration ───────────────────────────────────────────────────

    private fun configureWorld(world: World) {
        world.difficulty = Difficulty.NORMAL
        world.setSpawnFlags(false, false)
        world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false)
        world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, false)
        world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, false)
        world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false)
        world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false)
        world.setGameRule(org.bukkit.GameRule.ANNOUNCE_ADVANCEMENTS, false)
        world.setGameRule(org.bukkit.GameRule.SHOW_DEATH_MESSAGES, false)
        world.setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, true)
        world.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION, true)
        world.time = 6000
        world.setStorm(false)
        world.setThundering(false)
        world.weatherDuration = 0
        // Transient world — no disk persistence needed between resets.
        world.setAutoSave(false)
        // Arena spawn point is irrelevant; releasing spawn chunks saves ~25 chunks.
        world.keepSpawnInMemory = false
        debugLog("✓ '${world.name}' configured (autoSave=false, keepSpawn=false).")
    }
}

// Void chunk generator — produces completely empty chunks (no bedrock, no terrain).
class VoidChunkGenerator : ChunkGenerator() {
    override fun generateChunkData(world: World, random: Random, x: Int, z: Int, biome: BiomeGrid): ChunkData =
        createChunkData(world)
    override fun canSpawn(world: World, x: Int, z: Int) = true
    override fun getDefaultPopulators(world: World): List<org.bukkit.generator.BlockPopulator> = emptyList()
}

// Forces every chunk in the gameworld to use Plains biome, so grass and leaf colour
// is always consistent across all arenas regardless of their X-grid position.
class PlainsBiomeProvider : BiomeProvider() {
    override fun getBiomes(worldInfo: WorldInfo): List<Biome> = listOf(Biome.PLAINS)
    override fun getBiome(worldInfo: WorldInfo, x: Int, y: Int, z: Int): Biome = Biome.PLAINS
}
