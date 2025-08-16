package org.Kloppie74.locoPillars.game.arena

import org.bukkit.Location
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.*
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats
import com.sk89q.worldedit.math.BlockVector3
import com.sk89q.worldedit.session.ClipboardHolder
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.bukkit.WorldGuardPlugin
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion
import com.sk89q.worldguard.protection.flags.Flags
import com.sk89q.worldguard.protection.flags.StateFlag
import org.Kloppie74.locoPillars.game.config.GameConfig

enum class ArenaStatus {
    AVAILABLE, OCCUPIED, RESETTING
}

data class ArenaTemplate(
    val name: String,
    val configFile: File,
    val schemFile: File
)

data class ArenaInstance(
    val id: UUID,
    val template: ArenaTemplate,
    val config: GameConfig,
    val origin: Location,
    val world: World,
    var status: ArenaStatus = ArenaStatus.AVAILABLE,
    val regionId: String
)

class ArenaManager(
    private val plugin: JavaPlugin,
    private val worldManager: ArenaWorldManager
) {
    private val templates = mutableMapOf<String, ArenaTemplate>()
    private val instances = mutableListOf<ArenaInstance>()
    private val instanceDistance = 1000 // Distance between arena origins
    private val worldGuard = WorldGuard.getInstance()

    init {
        loadArenaTemplates()
    }

    private fun loadArenaTemplates() {
        // Create necessary folders
        val gamesFolder = File(plugin.dataFolder, "Games")
        val schemsFolder = File(plugin.dataFolder, "Schems")
        val dataFolder = File(plugin.dataFolder, "Data")
        val gameWorldsFolder = File(dataFolder, "GameWorlds")

        // Create folders if they don't exist
        if (!gamesFolder.exists()) {
            gamesFolder.mkdirs()
            plugin.logger.info("[ArenaManager] Created Games folder at: ${gamesFolder.absolutePath}")
        }

        if (!schemsFolder.exists()) {
            schemsFolder.mkdirs()
            plugin.logger.info("[ArenaManager] Created Schems folder at: ${schemsFolder.absolutePath}")
        }

        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
            plugin.logger.info("[ArenaManager] Created Data folder at: ${dataFolder.absolutePath}")
        }

        if (!gameWorldsFolder.exists()) {
            gameWorldsFolder.mkdirs()
            plugin.logger.info("[ArenaManager] Created GameWorlds folder at: ${gameWorldsFolder.absolutePath}")
        }

        plugin.logger.info("[ArenaManager] Scanning for templates...")
        plugin.logger.info("[ArenaManager] Games folder: ${gamesFolder.absolutePath}")
        plugin.logger.info("[ArenaManager] Schems folder: ${schemsFolder.absolutePath}")

        val configs = gamesFolder.listFiles { f -> f.extension == "yml" } ?: emptyArray()
        plugin.logger.info("[ArenaManager] Found ${configs.size} .yml config files")

        configs.forEach { configFile ->
            val name = configFile.nameWithoutExtension
            val schemFile = File(schemsFolder, "$name.schem")

            plugin.logger.info("[ArenaManager] Checking template: $name")
            plugin.logger.info("[ArenaManager] Config: ${configFile.absolutePath} (exists: ${configFile.exists()})")
            plugin.logger.info("[ArenaManager] Schematic: ${schemFile.absolutePath} (exists: ${schemFile.exists()})")

            if (schemFile.exists()) {
                // Validate the schematic file
                if (org.Kloppie74.locoPillars.utils.SchematicHelper.validateSchematicFile(plugin, schemFile)) {
                    templates[name] = ArenaTemplate(name, configFile, schemFile)
                    plugin.logger.info("[ArenaManager] ✓ Arena template registered: $name")
                    plugin.logger.info("[ArenaManager]   Config: ${configFile.name}")
                    plugin.logger.info("[ArenaManager]   Schematic: ${schemFile.name} (${schemFile.length()} bytes)")
                } else {
                    plugin.logger.warning("[ArenaManager] ✗ Invalid schematic file for: $name")
                }
            } else {
                plugin.logger.warning("[ArenaManager] ✗ No .schem found for config: $name.yml")
                plugin.logger.warning("[ArenaManager] Expected schematic at: ${schemFile.absolutePath}")
            }
        }

        plugin.logger.info("[ArenaManager] Template loading completed")
        plugin.logger.info("[ArenaManager] Total templates loaded: ${templates.size}")

        if (templates.isEmpty()) {
            plugin.logger.warning("[ArenaManager] No templates found! Please ensure:")
            plugin.logger.warning("[ArenaManager] 1. Config files (.yml) are in: ${gamesFolder.absolutePath}")
            plugin.logger.warning("[ArenaManager] 2. Schematic files (.schem) are in: ${schemsFolder.absolutePath}")
            plugin.logger.warning("[ArenaManager] 3. Config and schematic files have matching names (without extension)")
        } else {
            templates.forEach { (name, template) ->
                plugin.logger.info("[ArenaManager] ✓ Template: $name")
                plugin.logger.info("[ArenaManager]   Config: ${template.configFile.name}")
                plugin.logger.info("[ArenaManager]   Schematic: ${template.schemFile.name}")
            }
        }
    }

    fun preloadInstances(configs: List<GameConfig>, amountPerType: Int) {
        plugin.logger.info("[ArenaManager] Starting preload of instances...")
        plugin.logger.info("[ArenaManager] Configs to process: ${configs.size}")
        plugin.logger.info("[ArenaManager] Templates available: ${templates.size}")
        plugin.logger.info("[ArenaManager] Instances per type: $amountPerType")

        if (templates.isEmpty()) {
            plugin.logger.severe("[ArenaManager] No templates available! Cannot preload instances.")
            plugin.logger.severe("[ArenaManager] Please check that:")
            plugin.logger.severe("[ArenaManager] 1. .yml config files are in Games/ folder")
            plugin.logger.severe("[ArenaManager] 2. .schem files are in Schems/ folder")
            plugin.logger.severe("[ArenaManager] 3. File names match (without extensions)")
            return
        }

        var globalIndex = 0
        var totalInstancesCreated = 0

        configs.forEach { config ->
            plugin.logger.info("[ArenaManager] Processing config: ${config.name}")

            val template = templates[config.name]
            if (template == null) {
                plugin.logger.warning("[ArenaManager] No template found for config: ${config.name}")
                plugin.logger.warning("[ArenaManager] Available templates: ${templates.keys}")
                return@forEach
            }

            plugin.logger.info("[ArenaManager] ✓ Template found for ${config.name}")
            plugin.logger.info("[ArenaManager] Template details:")
            plugin.logger.info("[ArenaManager]   Config file: ${template.configFile.absolutePath}")
            plugin.logger.info("[ArenaManager]   Schematic file: ${template.schemFile.absolutePath}")
            plugin.logger.info("[ArenaManager]   Schematic exists: ${template.schemFile.exists()}")
            plugin.logger.info("[ArenaManager]   Schematic size: ${template.schemFile.length()} bytes")

            // Create one world per game type in the GameWorlds folder
            val worldName = "pillars_${config.name}"
            plugin.logger.info("[ArenaManager] Creating world: $worldName")

            val world = worldManager.createArenaWorld(worldName)
            plugin.logger.info("[ArenaManager] ✓ World created successfully: ${world.name}")

            repeat(amountPerType) { instanceIndex ->
                // Calculate position in grid
                val gridX = globalIndex % 10 // 10 instances per row
                val gridZ = globalIndex / 10

                val x = gridX * instanceDistance
                val z = gridZ * instanceDistance
                val origin = Location(world, x.toDouble(), 40.0, z.toDouble())

                val regionId = "pillars_${config.name}_${instanceIndex}"

                plugin.logger.info("[ArenaManager] Creating instance $instanceIndex for ${config.name}")
                plugin.logger.info("[ArenaManager] Instance details:")
                plugin.logger.info("[ArenaManager]   Origin: (${origin.x}, ${origin.y}, ${origin.z})")
                plugin.logger.info("[ArenaManager]   World: ${world.name}")
                plugin.logger.info("[ArenaManager]   Region ID: $regionId")

                val instance = ArenaInstance(
                    id = UUID.randomUUID(),
                    template = template,
                    config = config,
                    origin = origin,
                    world = world,
                    regionId = regionId
                )

                instances.add(instance)
                plugin.logger.info("[ArenaManager] Instance added to list, total: ${instances.size}")

                createWorldGuardRegion(instance)
                plugin.logger.info("[ArenaManager] WorldGuard region created for instance $instanceIndex")

                resetArena(instance)
                plugin.logger.info("[ArenaManager] Arena reset completed for instance $instanceIndex")

                globalIndex++
                totalInstancesCreated++
            }

            plugin.logger.info("[ArenaManager] Completed processing config: ${config.name}")
            plugin.logger.info("[ArenaManager] Instances created for this config: $amountPerType")
        }

        plugin.logger.info("[ArenaManager] ==========================================")
        plugin.logger.info("[ArenaManager] PRELOAD COMPLETED SUCCESSFULLY!")
        plugin.logger.info("[ArenaManager] ==========================================")
        plugin.logger.info("[ArenaManager] Total instances created: $totalInstancesCreated")
        plugin.logger.info("[ArenaManager] Total instances in list: ${instances.size}")
        plugin.logger.info("[ArenaManager] Configs processed: ${configs.size}")
        plugin.logger.info("[ArenaManager] Templates used: ${templates.size}")
        plugin.logger.info("[ArenaManager] Worlds created: ${configs.size}")

        // Log summary per game type
        configs.forEach { config ->
            val typeInstances = instances.filter { it.template.name == config.name }
            plugin.logger.info("[ArenaManager] ${config.name}: ${typeInstances.size} instances")
        }
    }

    private fun createWorldGuardRegion(instance: ArenaInstance) {
        plugin.logger.info("[ArenaManager] Creating WorldGuard region for ${instance.regionId}")

        val regionManager = worldGuard.platform.regionContainer.get(BukkitAdapter.adapt(instance.world))
        if (regionManager == null) {
            plugin.logger.severe("[ArenaManager] Could not get WorldGuard region manager for world ${instance.world.name}")
            return
        }

        val origin = instance.origin
        val halfDistance = instanceDistance / 2

        // Create a region around the instance origin
        val min = BlockVector3.at(
            origin.blockX - halfDistance,
            0,
            origin.blockZ - halfDistance
        )
        val max = BlockVector3.at(
            origin.blockX + halfDistance,
            255,
            origin.blockZ + halfDistance
        )

        plugin.logger.info("[ArenaManager] Region bounds: ($min) to ($max)")

        val region = ProtectedCuboidRegion(instance.regionId, min, max)

        // Set all relevant flags for full access
        region.setFlag(Flags.PVP, StateFlag.State.ALLOW)
        region.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW)
        region.setFlag(Flags.BLOCK_PLACE, StateFlag.State.ALLOW)
        region.setFlag(Flags.TNT, StateFlag.State.ALLOW)
        region.setFlag(Flags.MOB_SPAWNING, StateFlag.State.ALLOW)
        region.setFlag(Flags.ENTITY_PAINTING_DESTROY, StateFlag.State.ALLOW)
        region.setFlag(Flags.ENTITY_ITEM_FRAME_DESTROY, StateFlag.State.ALLOW)
        region.setFlag(Flags.CHEST_ACCESS, StateFlag.State.ALLOW)
        region.setFlag(Flags.USE_ANVIL, StateFlag.State.ALLOW)
        region.setFlag(Flags.USE_DRIPLEAF, StateFlag.State.ALLOW)
        region.setFlag(Flags.EXIT, StateFlag.State.DENY)
        region.setFlag(Flags.EXIT_VIA_TELEPORT, StateFlag.State.ALLOW)
        region.setFlag(Flags.ENDER_BUILD, StateFlag.State.ALLOW)
        region.setFlag(Flags.FALL_DAMAGE, StateFlag.State.ALLOW)
        region.setFlag(Flags.FIRE_SPREAD, StateFlag.State.ALLOW)
        region.setFlag(Flags.LAVA_FLOW, StateFlag.State.ALLOW)
        region.setFlag(Flags.LEAF_DECAY, StateFlag.State.ALLOW)
        region.setFlag(Flags.POTION_SPLASH, StateFlag.State.ALLOW)
        region.setFlag(Flags.SLEEP, StateFlag.State.ALLOW)
        region.setFlag(Flags.SNOW_FALL, StateFlag.State.ALLOW)
        region.setFlag(Flags.SNOW_MELT, StateFlag.State.ALLOW)
        region.setFlag(Flags.VINE_GROWTH, StateFlag.State.ALLOW)
        region.setFlag(Flags.WATER_FLOW, StateFlag.State.ALLOW)
        region.setFlag(Flags.WITHER_DAMAGE, StateFlag.State.ALLOW)
        region.setFlag(Flags.ITEM_PICKUP, StateFlag.State.ALLOW)
        region.setFlag(Flags.ITEM_DROP, StateFlag.State.ALLOW)
        region.setFlag(Flags.HUNGER_DRAIN, StateFlag.State.ALLOW)
        region.setFlag(Flags.INVINCIBILITY, StateFlag.State.DENY)
        region.setFlag(Flags.HEALTH_REGEN, StateFlag.State.ALLOW)
        region.setFlag(Flags.CREEPER_EXPLOSION, StateFlag.State.ALLOW)
        region.setFlag(Flags.ENDERDRAGON_BLOCK_DAMAGE, StateFlag.State.ALLOW)
        region.setFlag(Flags.GHAST_FIREBALL, StateFlag.State.ALLOW)
        region.setFlag(Flags.OTHER_EXPLOSION, StateFlag.State.ALLOW)
        region.setFlag(Flags.PISTONS, StateFlag.State.ALLOW)
        region.setFlag(Flags.TRAMPLE_BLOCKS, StateFlag.State.ALLOW)
        region.setFlag(Flags.DESTROY_VEHICLE, StateFlag.State.ALLOW)
        region.setFlag(Flags.PLACE_VEHICLE, StateFlag.State.ALLOW)
        region.setFlag(Flags.USE, StateFlag.State.ALLOW)
        region.setFlag(Flags.INTERACT, StateFlag.State.ALLOW)
        region.setFlag(Flags.DAMAGE_ANIMALS, StateFlag.State.ALLOW)
        region.setFlag(Flags.DENY_MESSAGE, "You cannot leave the arena!")
        region.setFlag(Flags.GREET_MESSAGE, "Welcome to the arena!")
        region.setFlag(Flags.SEND_CHAT, StateFlag.State.ALLOW)
        region.setFlag(Flags.RECEIVE_CHAT, StateFlag.State.ALLOW)

        regionManager.addRegion(region)

        plugin.logger.info("[ArenaManager] WorldGuard region created successfully: ${instance.regionId}")
    }

    fun getAvailableInstance(templateName: String): ArenaInstance? {
        plugin.logger.info("[ArenaManager] Looking for available instance of type: $templateName")

        val instance = instances.firstOrNull {
            it.template.name == templateName &&
                    it.status == ArenaStatus.AVAILABLE
        }

        if (instance != null) {
            plugin.logger.info("[ArenaManager] Found available instance: ${instance.id}")
        } else {
            plugin.logger.warning("[ArenaManager] No available instance found for: $templateName")
            plugin.logger.info("[ArenaManager] Available instances:")
            instances.forEach { inst ->
                plugin.logger.info("[ArenaManager]   - ${inst.template.name}: ${inst.status} (${inst.id})")
            }
        }

        return instance
    }

    fun occupyInstance(instance: ArenaInstance) {
        plugin.logger.info("[ArenaManager] Occupying instance: ${instance.id} (${instance.template.name})")
        instance.status = ArenaStatus.OCCUPIED
        plugin.logger.info("[ArenaManager] Instance ${instance.id} status changed to OCCUPIED")
    }

    fun releaseInstance(instance: ArenaInstance) {
        plugin.logger.info("[ArenaManager] Releasing instance: ${instance.id} (${instance.template.name})")
        instance.status = ArenaStatus.RESETTING

        // Clear all players from the region
        clearPlayersFromInstance(instance)
        plugin.logger.info("[ArenaManager] Players cleared from instance")

        // Reset the arena
        resetArena(instance)
        plugin.logger.info("[ArenaManager] Arena reset completed")

        instance.status = ArenaStatus.AVAILABLE
        plugin.logger.info("[ArenaManager] Instance ${instance.id} status changed to AVAILABLE")
    }

    private fun clearPlayersFromInstance(instance: ArenaInstance) {
        plugin.logger.info("[ArenaManager] Clearing players from instance: ${instance.id}")

        val halfDistance = instanceDistance / 2
        val origin = instance.origin
        var playersCleared = 0

        instance.world.players.forEach { player ->
            val loc = player.location
            if (loc.x >= origin.x - halfDistance && loc.x <= origin.x + halfDistance &&
                loc.z >= origin.z - halfDistance && loc.z <= origin.z + halfDistance) {

                plugin.logger.info("[ArenaManager] Found player ${player.name} in instance area, teleporting out")

                // Teleport player to main world spawn
                val mainWorld = plugin.server.worlds.firstOrNull { !it.name.startsWith("pillars_") }
                if (mainWorld != null) {
                    player.teleport(mainWorld.spawnLocation)
                    playersCleared++
                    plugin.logger.info("[ArenaManager] Teleported ${player.name} to main world")
                } else {
                    plugin.logger.warning("[ArenaManager] No main world found for player teleport")
                }
            }
        }

        plugin.logger.info("[ArenaManager] Cleared $playersCleared players from instance")
    }

    fun resetArena(instance: ArenaInstance) {
        plugin.logger.info("[ArenaManager] Starting arena reset for: ${instance.template.name} (${instance.id})")
        plugin.logger.info("[ArenaManager] Schematic file: ${instance.template.schemFile.absolutePath}")
        plugin.logger.info("[ArenaManager] Origin location: ${instance.origin}")

        // Use the SchematicHelper for better error handling and logging
        val success = org.Kloppie74.locoPillars.utils.SchematicHelper.pasteSchematic(
            plugin,
            instance.template.schemFile,
            instance.origin
        )

        if (success) {
            plugin.logger.info("[ArenaManager] ✓ Arena ${instance.template.name} (instance ${instance.id}) successfully reset")
            plugin.logger.info("[ArenaManager]   Location: ${instance.origin}")
            plugin.logger.info("[ArenaManager]   World: ${instance.world.name}")
        } else {
            plugin.logger.severe("[ArenaManager] ✗ Failed to reset arena ${instance.template.name} (instance ${instance.id})")
            plugin.logger.severe("[ArenaManager] Debug information:")
            plugin.logger.severe("[ArenaManager]   Template name: ${instance.template.name}")
            plugin.logger.severe("[ArenaManager]   Schematic path: ${instance.template.schemFile.absolutePath}")
            plugin.logger.severe("[ArenaManager]   World: ${instance.world.name}")
            plugin.logger.severe("[ArenaManager]   Origin: ${instance.origin}")
            plugin.logger.severe("[ArenaManager]   Instance ID: ${instance.id}")

            // Try to validate the schematic file
            plugin.logger.severe("[ArenaManager] Running additional validation...")
            org.Kloppie74.locoPillars.utils.SchematicHelper.validateSchematicFile(plugin, instance.template.schemFile)
        }
    }

    fun getInstanceByLocation(location: Location): ArenaInstance? {
        return instances.firstOrNull { instance ->
            if (instance.world != location.world) return@firstOrNull false

            val origin = instance.origin
            val halfDistance = instanceDistance / 2

            location.x >= origin.x - halfDistance && location.x <= origin.x + halfDistance &&
                    location.z >= origin.z - halfDistance && location.z <= origin.z + halfDistance
        }
    }

    fun getAllInstances(): List<ArenaInstance> {
        plugin.logger.info("[ArenaManager] Returning ${instances.size} instances")
        return instances
    }

    fun getAllTemplates(): List<ArenaTemplate> {
        plugin.logger.info("[ArenaManager] Returning ${templates.size} templates")
        return templates.values.toList()
    }

    fun getInstancesByTemplate(templateName: String): List<ArenaInstance> {
        val matchingInstances = instances.filter { it.template.name == templateName }
        plugin.logger.info("[ArenaManager] Found ${matchingInstances.size} instances for template: $templateName")
        return matchingInstances
    }

    fun onDisable() {
        plugin.logger.info("[ArenaManager] Disabling plugin: Unloading and deleting game worlds...")
        worldManager.unloadAllGameWorlds()
        worldManager.deleteGameWorldsFolder()
        plugin.logger.info("[ArenaManager] All game worlds unloaded and GameWorlds folder deleted.")
    }
}