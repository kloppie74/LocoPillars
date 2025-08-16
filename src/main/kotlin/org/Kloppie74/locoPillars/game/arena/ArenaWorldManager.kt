package org.Kloppie74.locoPillars.game.arena

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.WorldType
import org.bukkit.generator.ChunkGenerator
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.lang.reflect.Field
import java.util.Random

class ArenaWorldManager(private val plugin: JavaPlugin) {
    private val arenaWorlds = mutableMapOf<String, World>()
    private val gameWorldsFolder: File

    init {
        // Initialize the GameWorlds folder in Data/GameWorlds/
        val dataFolder = File(plugin.dataFolder, "Data")
        gameWorldsFolder = File(dataFolder, "GameWorlds")

        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
            plugin.logger.info("[ArenaWorldManager] Created Data folder: ${dataFolder.absolutePath}")
        }

        if (!gameWorldsFolder.exists()) {
            gameWorldsFolder.mkdirs()
            plugin.logger.info("[ArenaWorldManager] Created GameWorlds folder: ${gameWorldsFolder.absolutePath}")
        }

        plugin.logger.info("[ArenaWorldManager] Initialized ArenaWorldManager")
        plugin.logger.info("[ArenaWorldManager] Game worlds will be stored in: ${gameWorldsFolder.absolutePath}")
    }

    fun createArenaWorld(worldName: String): World {
        plugin.logger.info("[ArenaWorldManager] Request to create world: $worldName")
        plugin.logger.info("[ArenaWorldManager] World storage location: ${gameWorldsFolder.absolutePath}")

        // Check if world already exists in our cache
        arenaWorlds[worldName]?.let { world ->
            plugin.logger.info("[ArenaWorldManager] World $worldName already exists in cache")
            return world
        }

        // Check if world exists in Bukkit
        val existingWorld = Bukkit.getWorld(worldName)
        if (existingWorld != null) {
            plugin.logger.info("[ArenaWorldManager] World $worldName already exists in Bukkit")
            arenaWorlds[worldName] = existingWorld
            configureWorld(existingWorld)
            return existingWorld
        }

        plugin.logger.info("[ArenaWorldManager] Creating new void world: $worldName")
        plugin.logger.info("[ArenaWorldManager] Using custom VoidChunkGenerator")

        try {
            // FIXED: Force world creation in our custom folder using reflection
            val originalWorldContainer = setWorldContainer(gameWorldsFolder)

            try {
                val creator = WorldCreator(worldName)
                    .type(WorldType.FLAT)
                    .generator(VoidChunkGenerator())
                    .generateStructures(false)

                plugin.logger.info("[ArenaWorldManager] WorldCreator configured:")
                plugin.logger.info("[ArenaWorldManager]   - Name: $worldName")
                plugin.logger.info("[ArenaWorldManager]   - Type: ${creator.type()}")
                plugin.logger.info("[ArenaWorldManager]   - Generator: VoidChunkGenerator")
                plugin.logger.info("[ArenaWorldManager]   - Generate structures: false")
                plugin.logger.info("[ArenaWorldManager]   - Custom world container: ${gameWorldsFolder.absolutePath}")

                plugin.logger.info("[ArenaWorldManager] Calling createWorld()...")
                val world = creator.createWorld()

                if (world != null) {
                    plugin.logger.info("[ArenaWorldManager] ✓ World created successfully: ${world.name}")
                    plugin.logger.info("[ArenaWorldManager] World details:")
                    plugin.logger.info("[ArenaWorldManager]   - Environment: ${world.environment}")
                    plugin.logger.info("[ArenaWorldManager]   - Seed: ${world.seed}")
                    plugin.logger.info("[ArenaWorldManager]   - Spawn location: ${world.spawnLocation}")
                    plugin.logger.info("[ArenaWorldManager]   - Keep spawn in memory: ${world.keepSpawnInMemory}")

                    configureWorld(world)
                    arenaWorlds[worldName] = world
                    plugin.logger.info("[ArenaWorldManager] World $worldName added to cache")
                    return world
                } else {
                    plugin.logger.severe("[ArenaWorldManager] World creation returned null!")
                    throw RuntimeException("World creation returned null")
                }
            } finally {
                // FIXED: Always restore original world container
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

            // Try multiple possible field names for world container
            val possibleFieldNames = listOf("container", "worldContainer", "worlds")
            var worldContainerField: Field? = null

            for (fieldName in possibleFieldNames) {
                try {
                    worldContainerField = serverClass.getDeclaredField(fieldName)
                    break
                } catch (e: NoSuchFieldException) {
                    // Try next field name
                    continue
                }
            }

            if (worldContainerField == null) {
                // Try parent classes
                var currentClass = serverClass.superclass
                while (currentClass != null && worldContainerField == null) {
                    for (fieldName in possibleFieldNames) {
                        try {
                            worldContainerField = currentClass.getDeclaredField(fieldName)
                            break
                        } catch (e: NoSuchFieldException) {
                            continue
                        }
                    }
                    currentClass = currentClass.superclass
                }
            }

            if (worldContainerField == null) {
                plugin.logger.warning("[ArenaWorldManager] Could not find world container field, using default location")
                return File(".")
            }

            worldContainerField.isAccessible = true
            val originalContainer = worldContainerField.get(server) as File

            plugin.logger.info("[ArenaWorldManager] Changing world container:")
            plugin.logger.info("[ArenaWorldManager]   From: ${originalContainer.absolutePath}")
            plugin.logger.info("[ArenaWorldManager]   To: ${newContainer.absolutePath}")

            worldContainerField.set(server, newContainer)

            return originalContainer

        } catch (e: Exception) {
            plugin.logger.warning("[ArenaWorldManager] Failed to set world container via reflection: ${e.message}")
            plugin.logger.warning("[ArenaWorldManager] World will be created in default location")
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
                } catch (e: NoSuchFieldException) {
                    continue
                }
            }

            if (worldContainerField == null) {
                var currentClass = serverClass.superclass
                while (currentClass != null && worldContainerField == null) {
                    for (fieldName in possibleFieldNames) {
                        try {
                            worldContainerField = currentClass.getDeclaredField(fieldName)
                            break
                        } catch (e: NoSuchFieldException) {
                            continue
                        }
                    }
                    currentClass = currentClass.superclass
                }
            }

            if (worldContainerField != null) {
                worldContainerField.isAccessible = true
                worldContainerField.set(server, originalContainer)
                plugin.logger.info("[ArenaWorldManager] World container restored to: ${originalContainer.absolutePath}")
            }

        } catch (e: Exception) {
            plugin.logger.warning("[ArenaWorldManager] Failed to restore world container: ${e.message}")
        }
    }

    private fun configureWorld(world: World) {
        plugin.logger.info("[ArenaWorldManager] Configuring world: ${world.name}")

        try {
            // Disable natural spawning and set game rules
            plugin.logger.info("[ArenaWorldManager] Setting spawn flags...")
            world.setSpawnFlags(false, false)

            plugin.logger.info("[ArenaWorldManager] Setting game rules...")
            // Set game rules for minigame environment
            world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false)
            world.setGameRule(org.bukkit.GameRule.KEEP_INVENTORY, false) // Players should lose items on death
            world.setGameRule(org.bukkit.GameRule.DO_FIRE_TICK, false)
            world.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false)
            world.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false)
            world.setGameRule(org.bukkit.GameRule.ANNOUNCE_ADVANCEMENTS, false)
            world.setGameRule(org.bukkit.GameRule.SHOW_DEATH_MESSAGES, false) // We handle death messages ourselves
            world.setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, true)
            world.setGameRule(org.bukkit.GameRule.NATURAL_REGENERATION, true)

            plugin.logger.info("[ArenaWorldManager] Setting time and weather...")
            // Set time to noon for good visibility
            world.time = 6000

            // Disable storm
            world.setStorm(false)
            world.setThundering(false)
            world.weatherDuration = 0

            plugin.logger.info("[ArenaWorldManager] ✓ World configuration completed for: ${world.name}")

        } catch (e: Exception) {
            plugin.logger.severe("[ArenaWorldManager] Error configuring world ${world.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    fun getArenaWorld(worldName: String): World? {
        return arenaWorlds[worldName] ?: Bukkit.getWorld(worldName)
    }

    fun deleteArenaWorld(worldName: String) {
        plugin.logger.info("[ArenaWorldManager] Request to delete world: $worldName")

        val world = arenaWorlds[worldName]
        if (world != null) {
            plugin.logger.info("[ArenaWorldManager] Deleting world: $worldName")

            // Teleport all players out of the world first
            val playersInWorld = world.players.size
            plugin.logger.info("[ArenaWorldManager] Found $playersInWorld players in world")

            val mainWorld = Bukkit.getWorlds().firstOrNull { !it.name.startsWith("pillars_") }
            if (mainWorld != null) {
                world.players.forEach { player ->
                    plugin.logger.info("[ArenaWorldManager] Teleporting ${player.name} out of world $worldName")
                    player.teleport(mainWorld.spawnLocation)
                }
            }

            // Unload the world
            val unloaded = Bukkit.unloadWorld(world, false)
            if (unloaded) {
                arenaWorlds.remove(worldName)

                // Delete world files from our GameWorlds folder
                try {
                    val worldFolder = File(gameWorldsFolder, worldName)
                    if (worldFolder.exists() && worldFolder.isDirectory) {
                        val deleted = deleteDirectory(worldFolder)
                        if (deleted) {
                            plugin.logger.info("[ArenaWorldManager] ✓ World folder deleted successfully")
                        } else {
                            plugin.logger.warning("[ArenaWorldManager] ✗ Failed to delete world folder")
                        }
                    }
                } catch (e: Exception) {
                    plugin.logger.warning("[ArenaWorldManager] Failed to delete world files for $worldName: ${e.message}")
                }

                plugin.logger.info("[ArenaWorldManager] ✓ Successfully deleted world: $worldName")
            } else {
                plugin.logger.severe("[ArenaWorldManager] ✗ Failed to unload world: $worldName")
            }
        }
    }

    private fun deleteDirectory(directory: File): Boolean {
        if (directory.isDirectory) {
            directory.listFiles()?.forEach { file ->
                deleteDirectory(file)
            }
        }
        return directory.delete()
    }

    fun getAllArenaWorlds(): Map<String, World> = arenaWorlds.toMap()

    fun cleanup() {
        plugin.logger.info("[ArenaWorldManager] Starting cleanup of ${arenaWorlds.size} arena worlds")

        arenaWorlds.keys.toList().forEach { worldName ->
            try {
                deleteArenaWorld(worldName)
            } catch (e: Exception) {
                plugin.logger.severe("[ArenaWorldManager] Error during cleanup of world $worldName: ${e.message}")
            }
        }

        plugin.logger.info("[ArenaWorldManager] Cleanup completed")
    }

    fun unloadAllGameWorlds() {
        plugin.server.worlds
            .filter { it.name.startsWith("pillars_") }
            .forEach { world ->
                plugin.server.unloadWorld(world, true)
                plugin.logger.info("[ArenaWorldManager] Unloaded world: ${world.name}")
            }
    }

    fun deleteGameWorldsFolder() {
        if (gameWorldsFolder.exists()) {
            gameWorldsFolder.deleteRecursively()
            plugin.logger.info("[ArenaWorldManager] Deleted GameWorlds folder: ${gameWorldsFolder.absolutePath}")
        }
    }

    fun getGameWorldsFolder(): File = gameWorldsFolder
}

// Void chunk generator for empty worlds
class VoidChunkGenerator : ChunkGenerator() {
    override fun generateChunkData(world: World, random: Random, x: Int, z: Int, biome: BiomeGrid): ChunkData {
        return createChunkData(world)
    }

    override fun canSpawn(world: World, x: Int, z: Int): Boolean = true
    override fun getDefaultPopulators(world: World): List<org.bukkit.generator.BlockPopulator> = emptyList()
}