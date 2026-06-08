package org.Kloppie74.locoPillars.game.arena

import org.Kloppie74.locoPillars.utils.config.ConfigManager
import org.Kloppie74.locoPillars.utils.ProgressTracker
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.bukkit.generator.ChunkGenerator
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.lang.reflect.Field
import java.nio.file.Files
import java.util.Comparator
import java.util.Random

class ArenaWorldManager(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager // Added for debug system!
) {
    private val arenaWorlds = mutableMapOf<String, World>()
    private val mainDirectory: File = File(".")

    init {
        debugLog("[ArenaWorldManager] Initialized. Game worlds will be stored in: ${mainDirectory.absolutePath}")
    }

    // Debug helper
    private fun debugLog(message: String) {
        if (configManager.debugMode) {
            plugin.logger.info("[ArenaWorldManager][DEBUG] $message")
        }
    }

    private fun progressMessage(percentage: Int, message: String) {
        if (!configManager.debugMode) {
            plugin.logger.info("[ArenaWorldManager] $percentage% $message")
        }
    }

    fun createArenaWorld(worldName: String, progressTracker: ProgressTracker? = null): World {
        debugLog("Request to create world: $worldName")
        debugLog("World storage location: ${mainDirectory.absolutePath}")

        // Check if world already exists in our cache
        arenaWorlds[worldName]?.let { world ->
            debugLog("World $worldName already exists in cache")
            return world
        }

        // Check if world exists in Bukkit
        val existingWorld = Bukkit.getWorld(worldName)
        if (existingWorld != null) {
            debugLog("World $worldName already exists in Bukkit")
            arenaWorlds[worldName] = existingWorld
            configureWorld(existingWorld)
            return existingWorld
        }

        debugLog("Creating new void world: $worldName")
        debugLog("Using custom VoidChunkGenerator")

        try {
            val originalWorldContainer = setWorldContainer(mainDirectory)
            try {
                val creator = WorldCreator(worldName)
                    .type(WorldType.FLAT)
                    .generator(VoidChunkGenerator())
                    .generateStructures(false)

                debugLog("WorldCreator configured:")
                debugLog("  - Name: $worldName")
                debugLog("  - Type: ${creator.type()}")
                debugLog("  - Generator: VoidChunkGenerator")
                debugLog("  - Generate structures: false")
                debugLog("  - World container: ${mainDirectory.absolutePath}")

                val world = creator.createWorld()

                if (world != null) {
                    debugLog("✓ World created successfully: ${world.name}")
                    debugLog("World details:")
                    debugLog("  - Environment: ${world.environment}")
                    debugLog("  - Seed: ${world.seed}")
                    debugLog("  - Spawn location: ${world.spawnLocation}")

                    configureWorld(world)
                    arenaWorlds[worldName] = world
                    debugLog("World $worldName added to cache")
                    progressTracker?.incrementStep("Created world $worldName")
                    progressMessage(progressTracker?.getPercentage() ?: 100, "World $worldName created")
                    return world
                } else {
                    plugin.logger.severe("[ArenaWorldManager] World creation returned null!")
                    throw RuntimeException("World creation returned null")
                }
            } finally {
                restoreWorldContainer(originalWorldContainer)
            }
        } catch (e: Exception) {
            plugin.logger.severe("[ArenaWorldManager] Failed to create world $worldName: ${e.message}")
            plugin.logger.severe("[ArenaWorldManager] Exception type: ${e.javaClass.name}")
            e.printStackTrace()
            throw RuntimeException("Failed to create world: $worldName", e)
        }
    }

    private fun setWorldContainer(newContainer: File): File {
        return try {
            val server = Bukkit.getServer()
            val serverClass = server.javaClass
            val possibleFieldNames = listOf("container", "worldContainer", "worlds")
            var worldContainerField: Field? = null

            for (fieldName in possibleFieldNames) {
                try {
                    worldContainerField = serverClass.getDeclaredField(fieldName)
                    break
                } catch (_: NoSuchFieldException) {}
            }

            if (worldContainerField == null) {
                var currentClass = serverClass.superclass
                while (currentClass != null && worldContainerField == null) {
                    for (fieldName in possibleFieldNames) {
                        try {
                            worldContainerField = currentClass.getDeclaredField(fieldName)
                            break
                        } catch (_: NoSuchFieldException) {}
                    }
                    currentClass = currentClass.superclass
                }
            }

            if (worldContainerField == null) {
                debugLog("Could not find world container field, using default location")
                return File(".")
            }

            worldContainerField.isAccessible = true
            val originalContainer = worldContainerField.get(server) as File

            debugLog("Changing world container:")
            debugLog("  From: ${originalContainer.absolutePath}")
            debugLog("  To: ${newContainer.absolutePath}")

            worldContainerField.set(server, newContainer)

            return originalContainer
        } catch (e: Exception) {
            debugLog("Failed to set world container via reflection: ${e.message}")
            debugLog("World will be created in default location")
            return File(".")
        }
    }

    private fun restoreWorldContainer(originalContainer: File) {
        try {
            val server = Bukkit.getServer()
            val serverClass = server.javaClass
            val possibleFieldNames = listOf("container", "worldContainer", "worlds")
            var worldContainerField: Field? = null

            for (fieldName in possibleFieldNames) {
                try {
                    worldContainerField = serverClass.getDeclaredField(fieldName)
                    break
                } catch (_: NoSuchFieldException) {}
            }

            if (worldContainerField == null) {
                var currentClass = serverClass.superclass
                while (currentClass != null && worldContainerField == null) {
                    for (fieldName in possibleFieldNames) {
                        try {
                            worldContainerField = currentClass.getDeclaredField(fieldName)
                            break
                        } catch (_: NoSuchFieldException) {}
                    }
                    currentClass = currentClass.superclass
                }
            }

            if (worldContainerField != null) {
                worldContainerField.isAccessible = true
                worldContainerField.set(server, originalContainer)
                debugLog("World container restored to: ${originalContainer.absolutePath}")
            }
        } catch (e: Exception) {
            debugLog("Failed to restore world container: ${e.message}")
        }
    }

    private fun configureWorld(world: World) {
        debugLog("Configuring world: ${world.name}")
        try {
            // MUST be NORMAL (or harder) — PEACEFUL disables hunger depletion entirely.
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

            // ── Memory optimizations ────────────────────────────────────────────
            // Arena worlds are transient (reset after every game) — there is no
            // reason to persist them to disk.  Disabling auto-save prevents Paper
            // from keeping all touched chunks "hot" in memory for the 5-minute
            // save cycle, and avoids unnecessary disk writes.
            world.setAutoSave(false)

            // Do NOT preload the spawn chunks — the arena spawn point is irrelevant
            // (players teleport to their team spawn, not world spawn).
            // keepSpawnInMemory = false releases the 25 always-loaded spawn chunks.
            world.keepSpawnInMemory = false

            debugLog("✓ World configuration completed for: ${world.name} (autoSave=false, keepSpawn=false)")
        } catch (e: Exception) {
            plugin.logger.severe("[ArenaWorldManager] Error configuring world ${world.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    fun getArenaWorld(worldName: String): World? {
        return arenaWorlds[worldName] ?: Bukkit.getWorld(worldName)
    }

    fun deleteArenaWorld(worldName: String, progressTracker: ProgressTracker? = null) {
        debugLog("Request to delete world: $worldName")
        val world = arenaWorlds[worldName]
        if (world != null) {
            debugLog("Deleting world: $worldName")
            val mainWorld = Bukkit.getWorlds().firstOrNull { !it.name.startsWith("pillars_") }
            if (mainWorld != null) {
                world.players.forEach { player ->
                    debugLog("Teleporting ${player.name} out of world $worldName")
                    player.teleport(mainWorld.spawnLocation)
                }
            }
            // Resolve folder BEFORE unloading — world.worldFolder is unavailable afterwards.
            val worldFolder = resolveWorldFolder(worldName, world)
            val unloaded = Bukkit.unloadWorld(world, false)
            if (unloaded) {
                arenaWorlds.remove(worldName)
                debugLog("✓ World unloaded: $worldName")
                if (worldFolder != null && worldFolder.exists()) {
                    val deleted = deleteDirectoryJava(worldFolder)
                    if (deleted) {
                        debugLog("✓ World folder deleted: ${worldFolder.absolutePath}")
                    } else {
                        debugLog("✗ Failed to delete world folder: ${worldFolder.absolutePath}")
                    }
                } else {
                    debugLog("World folder for $worldName not found — skipping delete")
                }
                debugLog("✓ Successfully deleted world: $worldName")
                progressTracker?.incrementStep("Deleted world $worldName")
                progressMessage(progressTracker?.getPercentage() ?: 100, "World $worldName deleted")
            } else {
                plugin.logger.severe("[ArenaWorldManager] ✗ Failed to unload world: $worldName")
            }
        }
    }

    fun deleteAllUnloadedPillarsWorldFolders(progressTracker: ProgressTracker? = null) {
        debugLog("Brute-force deleting all pillars_ world folders (classic + dimension layout)")

        // Classic layout (< Paper 1.21.2): <mainDirectory>/pillars_*/
        mainDirectory.listFiles()?.forEach { folder ->
            if (folder.isDirectory && folder.name.startsWith("pillars_")) {
                val deleted = deleteDirectoryJava(folder)
                debugLog(if (deleted) "Verwijderd (classic): ${folder.name}" else "Kon niet verwijderen (classic): ${folder.name}")
                progressTracker?.incrementStep("Deleted folder ${folder.name}")
                progressMessage(progressTracker?.getPercentage() ?: 100, "Deleted folder ${folder.name}")
            }
        }

        // New dimension layout (≥ Paper 1.21.2): <mainDirectory>/world/dimensions/minecraft/pillars_*/
        val dimensionsDir = File(mainDirectory, "world/dimensions/minecraft")
        if (dimensionsDir.exists() && dimensionsDir.isDirectory) {
            dimensionsDir.listFiles()?.forEach { folder ->
                if (folder.isDirectory && folder.name.startsWith("pillars_")) {
                    val deleted = deleteDirectoryJava(folder)
                    debugLog(if (deleted) "Verwijderd (dimension): ${folder.name}" else "Kon niet verwijderen (dimension): ${folder.name}")
                    progressTracker?.incrementStep("Deleted folder ${folder.name}")
                    progressMessage(progressTracker?.getPercentage() ?: 100, "Deleted folder ${folder.name}")
                }
            }
        }
    }

    /**
     * Resolves the on-disk folder for a world, supporting both storage layouts:
     *
     * - **Classic** (< Paper 1.21.2): `<mainDirectory>/<worldName>/`
     * - **Dimension** (≥ Paper 1.21.2): `<mainDirectory>/world/dimensions/minecraft/<worldName>/`
     *
     * Always pass [loadedWorld] when the world is still loaded — `world.worldFolder`
     * is the most reliable source and works regardless of layout. Falls back to
     * path-based probing when the world object is no longer available.
     */
    private fun resolveWorldFolder(worldName: String, loadedWorld: World? = null): File? {
        // 1. Most reliable: ask the loaded world object directly.
        loadedWorld?.worldFolder?.takeIf { it.exists() }?.let { return it }

        // 2. Classic layout: <mainDirectory>/<worldName>/
        File(mainDirectory, worldName).takeIf { it.exists() }?.let { return it }

        // 3. New dimension layout (Paper ≥ 1.21.2):
        //    <mainDirectory>/world/dimensions/minecraft/<worldName>/
        File(mainDirectory, "world/dimensions/minecraft/$worldName").takeIf { it.exists() }?.let { return it }

        return null
    }

    /**
     * Verwijdert een map recursief via Java NIO — GEEN Kotlin FileTreeWalk / Sequence.
     *
     * Waarom niet File.deleteRecursively()?
     *   Kotlin's deleteRecursively() gebruikt intern File.walk() → FileTreeWalk.
     *   FileTreeWalk implementeert kotlin.sequences.Sequence.  Als een andere plugin
     *   (LocoLevels) ook Kotlin shadet, worden FileTreeWalk en Sequence door twee
     *   verschillende classloaders geladen → ClassCastException bij de cast.
     *
     * Files.walk() retourneert java.util.stream.Stream<Path> — een puur Java-type
     * dat nooit via Kotlin's classloader hoeft te gaan.
     */
    private fun deleteDirectoryJava(dir: File): Boolean {
        if (!dir.exists()) return true
        return try {
            Files.walk(dir.toPath())
                .sorted(Comparator.reverseOrder())   // diepste bestanden eerst
                .forEach { Files.deleteIfExists(it) }
            !dir.exists()
        } catch (e: Exception) {
            debugLog("deleteDirectoryJava failed for ${dir.name}: ${e.message}")
            false
        }
    }

    @Deprecated("Vervangen door deleteDirectoryJava — vermijdt Kotlin FileTreeWalk ClassCastException")
    private fun deleteDirectory(directory: File): Boolean {
        if (directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                deleteDirectory(file)
            }
        }
        return directory.delete()
    }

    fun getAllArenaWorlds(): Map<String, World> = arenaWorlds.toMap()

    fun cleanup(progressTracker: ProgressTracker? = null) {
        debugLog("Starting cleanup of ${arenaWorlds.size} arena worlds")
        arenaWorlds.keys.toList().forEach { worldName ->
            try {
                deleteArenaWorld(worldName, progressTracker)
            } catch (e: Exception) {
                plugin.logger.severe("[ArenaWorldManager] Error during cleanup of world $worldName: ${e.message}")
            }
        }
        deleteAllUnloadedPillarsWorldFolders(progressTracker)
        debugLog("Cleanup completed")
    }

    fun unloadAllGameWorlds(progressTracker: ProgressTracker? = null) {
        plugin.server.worlds
            .filter { it.name.startsWith("pillars_") }
            .forEach { world ->
                plugin.server.unloadWorld(world, true)
                debugLog("Unloaded world: ${world.name}")
                progressTracker?.incrementStep("Unloaded world ${world.name}")
                progressMessage(progressTracker?.getPercentage() ?: 100, "Unloaded world ${world.name}")
            }
    }

    fun getGameWorldsFolder(): File = mainDirectory
}

// Void chunk generator for empty worlds
class VoidChunkGenerator : ChunkGenerator() {
    override fun generateChunkData(world: World, random: Random, x: Int, z: Int, biome: BiomeGrid): ChunkData {
        return createChunkData(world)
    }
    override fun canSpawn(world: World, x: Int, z: Int): Boolean = true
    override fun getDefaultPopulators(world: World): List<org.bukkit.generator.BlockPopulator> = emptyList()
}