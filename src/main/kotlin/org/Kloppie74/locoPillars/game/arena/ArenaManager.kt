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
import org.Kloppie74.locoPillars.config.ConfigManager
import org.Kloppie74.locoPillars.game.config.GameConfig
import kotlin.random.Random

enum class ArenaStatus {
    AVAILABLE, OCCUPIED, RESETTING
}

data class ArenaTemplate(
    val name: String,
    val configFile: File,
    val schemFiles: List<File>
)

data class ArenaInstance(
    val id: UUID,
    val template: ArenaTemplate,
    val config: GameConfig,
    val origin: Location,
    val world: World,
    var status: ArenaStatus = ArenaStatus.AVAILABLE,
    val regionId: String,
    var currentSchematicFile: File? = null
)

class ArenaManager(
    private val plugin: JavaPlugin,
    private val worldManager: ArenaWorldManager,
    private val configManager: ConfigManager // <-- debug modus via configManager!
) {
    private val templates = mutableMapOf<String, ArenaTemplate>()
    private val instances = mutableListOf<ArenaInstance>()
    private val instanceDistance = 1000
    private val worldGuard = WorldGuard.getInstance()

    private fun debugLog(message: String) {
        if (configManager.debugMode) {
            plugin.logger.info("[ArenaManager] $message")
        }
    }

    init {
        loadArenaTemplates()
    }

    private fun loadArenaTemplates() {
        val gamesFolder = File(plugin.dataFolder, "Games")
        val schemsFolder = File(plugin.dataFolder, "Schems")
        val dataFolder = File(plugin.dataFolder, "Data")
        val gameWorldsFolder = File(dataFolder, "GameWorlds")

        if (!gamesFolder.exists()) {
            gamesFolder.mkdirs()
            debugLog("Created Games folder at: ${gamesFolder.absolutePath}")
        }
        if (!schemsFolder.exists()) {
            schemsFolder.mkdirs()
            debugLog("Created Schems folder at: ${schemsFolder.absolutePath}")
        }
        if (!dataFolder.exists()) {
            dataFolder.mkdirs()
            debugLog("Created Data folder at: ${dataFolder.absolutePath}")
        }
        if (!gameWorldsFolder.exists()) {
            gameWorldsFolder.mkdirs()
            debugLog("Created GameWorlds folder at: ${gameWorldsFolder.absolutePath}")
        }

        debugLog("Scanning for templates...")
        debugLog("Games folder: ${gamesFolder.absolutePath}")
        debugLog("Schems folder: ${schemsFolder.absolutePath}")

        val configs = gamesFolder.listFiles { f -> f.extension == "yml" } ?: emptyArray()
        debugLog("Found ${configs.size} .yml config files")

        configs.forEach { configFile ->
            val name = configFile.nameWithoutExtension
            debugLog("Processing template: $name")
            debugLog("Config: ${configFile.absolutePath} (exists: ${configFile.exists()})")

            val schemFolder = File(schemsFolder, name)
            val schemFiles = mutableListOf<File>()

            if (schemFolder.exists() && schemFolder.isDirectory) {
                debugLog("Found schematic folder: ${schemFolder.absolutePath}")
                val folderSchematics = schemFolder.listFiles { f -> f.extension == "schem" } ?: emptyArray()
                debugLog("Found ${folderSchematics.size} .schem files in folder")

                folderSchematics.forEach { schemFile ->
                    if (org.Kloppie74.locoPillars.utils.SchematicHelper.validateSchematicFile(plugin, configManager, schemFile)) {
                        schemFiles.add(schemFile)
                        debugLog("✓ Valid schematic: ${schemFile.name} (${schemFile.length()} bytes)")
                    } else {
                        debugLog("✗ Invalid schematic: ${schemFile.name}")
                    }
                }
            }

            if (schemFiles.isEmpty()) {
                val singleSchemFile = File(schemsFolder, "$name.schem")
                debugLog("No folder found, checking single schematic: ${singleSchemFile.absolutePath}")

                if (singleSchemFile.exists()) {
                    if (org.Kloppie74.locoPillars.utils.SchematicHelper.validateSchematicFile(plugin, configManager, singleSchemFile)) {
                        schemFiles.add(singleSchemFile)
                        debugLog("✓ Valid single schematic: ${singleSchemFile.name} (${singleSchemFile.length()} bytes)")
                    } else {
                        debugLog("✗ Invalid single schematic: ${singleSchemFile.name}")
                    }
                } else {
                    debugLog("✗ No schematic found for config: $name")
                }
            }

            if (schemFiles.isNotEmpty()) {
                templates[name] = ArenaTemplate(name, configFile, schemFiles)
                debugLog("✓ Arena template registered: $name")
                debugLog("  Config: ${configFile.name}")
                debugLog("  Schematics: ${schemFiles.size} files")
                schemFiles.forEachIndexed { index, file ->
                    debugLog("    ${index + 1}. ${file.name} (${file.length()} bytes)")
                }
            } else {
                debugLog("✗ No valid schematics found for config: $name")
            }
        }

        debugLog("Template loading completed")
        debugLog("Total templates loaded: ${templates.size}")

        if (templates.isEmpty()) {
            debugLog("No templates found! Please ensure:")
            debugLog("1. Config files (.yml) are in: ${gamesFolder.absolutePath}")
            debugLog("2. Either:")
            debugLog("   - Single schematic files (.schem) in: ${schemsFolder.absolutePath}")
            debugLog("   - Multiple schematic files in folders: ${schemsFolder.absolutePath}/[config-name]/")
            debugLog("3. Config and schematic files have matching names")
        } else {
            templates.forEach { (name, template) ->
                debugLog("✓ Template: $name")
                debugLog("  Config: ${template.configFile.name}")
                debugLog("  Schematics: ${template.schemFiles.size} files")
            }
        }
    }

    private fun pickRandomSchematic(template: ArenaTemplate): File {
        if (template.schemFiles.isEmpty()) {
            throw IllegalStateException("No schematic files available for template: ${template.name}")
        }
        val selectedFile = template.schemFiles[Random.nextInt(template.schemFiles.size)]
        debugLog("Selected schematic for ${template.name}: ${selectedFile.name}")
        return selectedFile
    }

    fun preloadInstances(configs: List<GameConfig>, amountPerType: Int) {
        debugLog("Starting preload of instances...")
        debugLog("Configs to process: ${configs.size}")
        debugLog("Templates available: ${templates.size}")
        debugLog("Instances per type: $amountPerType")

        if (templates.isEmpty()) {
            plugin.logger.severe("[ArenaManager] No templates available! Cannot preload instances.")
            plugin.logger.severe("[ArenaManager] Please check that:")
            plugin.logger.severe("[ArenaManager] 1. .yml config files are in Games/ folder")
            plugin.logger.severe("[ArenaManager] 2. .schem files are in Schems/ folder or Schems/[config-name]/ folders")
            plugin.logger.severe("[ArenaManager] 3. File names match (without extensions)")
            return
        }

        var globalIndex = 0
        var totalInstancesCreated = 0

        configs.forEach { config ->
            debugLog("Processing config: ${config.name}")

            val template = templates[config.name]
            if (template == null) {
                debugLog("No template found for config: ${config.name}")
                debugLog("Available templates: ${templates.keys}")
                return@forEach
            }

            debugLog("✓ Template found for ${config.name}")
            debugLog("Template details:")
            debugLog("  Config file: ${template.configFile.absolutePath}")
            debugLog("  Available schematics: ${template.schemFiles.size}")

            val worldName = "pillars_${config.name}"
            debugLog("Creating world: $worldName")

            val world = worldManager.createArenaWorld(worldName)
            debugLog("✓ World created successfully: ${world.name}")

            repeat(amountPerType) { instanceIndex ->
                val gridX = globalIndex % 10
                val gridZ = globalIndex / 10

                val x = gridX * instanceDistance
                val z = gridZ * instanceDistance
                val origin = Location(world, x.toDouble(), 40.0, z.toDouble())

                val regionId = "pillars_${config.name}_${instanceIndex}"

                debugLog("Creating instance $instanceIndex for ${config.name}")
                debugLog("Instance details:")
                debugLog("  Origin: (${origin.x}, ${origin.y}, ${origin.z})")
                debugLog("  World: ${world.name}")
                debugLog("  Region ID: $regionId")

                val instance = ArenaInstance(
                    id = UUID.randomUUID(),
                    template = template,
                    config = config,
                    origin = origin,
                    world = world,
                    regionId = regionId
                )

                instances.add(instance)
                debugLog("Instance added to list, total: ${instances.size}")

                createWorldGuardRegion(instance)
                debugLog("WorldGuard region created for instance $instanceIndex")

                resetArena(instance)
                debugLog("Arena reset completed for instance $instanceIndex")

                globalIndex++
                totalInstancesCreated++
            }

            debugLog("Completed processing config: ${config.name}")
            debugLog("Instances created for this config: $amountPerType")
        }

        debugLog("==========================================")
        debugLog("PRELOAD COMPLETED SUCCESSFULLY!")
        debugLog("==========================================")
        debugLog("Total instances created: $totalInstancesCreated")
        debugLog("Total instances in list: ${instances.size}")
        debugLog("Configs processed: ${configs.size}")
        debugLog("Templates used: ${templates.size}")
        debugLog("Worlds created: ${configs.size}")

        configs.forEach { config ->
            val typeInstances = instances.filter { it.template.name == config.name }
            debugLog("${config.name}: ${typeInstances.size} instances")
        }
    }

    private fun createWorldGuardRegion(instance: ArenaInstance) {
        debugLog("Creating WorldGuard region for ${instance.regionId}")

        val regionManager = worldGuard.platform.regionContainer.get(BukkitAdapter.adapt(instance.world))
        if (regionManager == null) {
            plugin.logger.severe("[ArenaManager] Could not get WorldGuard region manager for world ${instance.world.name}")
            return
        }

        val origin = instance.origin
        val halfDistance = instanceDistance / 2

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

        debugLog("Region bounds: ($min) to ($max)")

        val region = ProtectedCuboidRegion(instance.regionId, min, max)

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

        debugLog("WorldGuard region created successfully: ${instance.regionId}")
    }

    fun getAvailableInstance(templateName: String): ArenaInstance? {
        debugLog("Looking for available instance of type: $templateName")
        val instance = instances.firstOrNull {
            it.template.name == templateName &&
                    it.status == ArenaStatus.AVAILABLE
        }

        if (instance != null) {
            debugLog("Found available instance: ${instance.id}")
        } else {
            debugLog("No available instance found for: $templateName")
            debugLog("Available instances:")
            instances.forEach { inst ->
                debugLog("  - ${inst.template.name}: ${inst.status} (${inst.id})")
            }
        }

        return instance
    }

    fun occupyInstance(instance: ArenaInstance) {
        debugLog("Occupying instance: ${instance.id} (${instance.template.name})")
        instance.status = ArenaStatus.OCCUPIED
        debugLog("Instance ${instance.id} status changed to OCCUPIED")
    }

    fun releaseInstance(instance: ArenaInstance) {
        debugLog("Releasing instance: ${instance.id} (${instance.template.name})")
        instance.status = ArenaStatus.RESETTING

        clearPlayersFromInstance(instance)
        debugLog("Players cleared from instance")

        resetArena(instance)
        debugLog("Arena reset completed")

        instance.status = ArenaStatus.AVAILABLE
        debugLog("Instance ${instance.id} status changed to AVAILABLE")
    }

    private fun clearPlayersFromInstance(instance: ArenaInstance) {
        debugLog("Clearing players from instance: ${instance.id}")

        val halfDistance = instanceDistance / 2
        val origin = instance.origin
        var playersCleared = 0

        instance.world.players.forEach { player ->
            val loc = player.location
            if (loc.x >= origin.x - halfDistance && loc.x <= origin.x + halfDistance &&
                loc.z >= origin.z - halfDistance && loc.z <= origin.z + halfDistance) {

                debugLog("Found player ${player.name} in instance area, teleporting out")

                val lobbySpawn = configManager.getLobbySpawnLocation()
                if (lobbySpawn != null) {
                    player.teleport(lobbySpawn)
                } else {
                    player.teleport(player.server.worlds[0].spawnLocation)
                }
                playersCleared++
            }
        }

        debugLog("Cleared $playersCleared players from instance")
    }

    fun resetArena(instance: ArenaInstance) {
        debugLog("Starting arena reset for: ${instance.template.name} (${instance.id})")

        val selectedSchematic = pickRandomSchematic(instance.template)
        instance.currentSchematicFile = selectedSchematic

        debugLog("Using schematic: ${selectedSchematic.name}")
        debugLog("Schematic file: ${selectedSchematic.absolutePath}")
        debugLog("Origin location: ${instance.origin}")

        val success = org.Kloppie74.locoPillars.utils.SchematicHelper.pasteSchematic(
            plugin,
            configManager,
            selectedSchematic,
            instance.origin
        )

        if (success) {
            debugLog("✓ Arena ${instance.template.name} (instance ${instance.id}) successfully reset")
            debugLog("  Used schematic: ${selectedSchematic.name}")
            debugLog("  Location: ${instance.origin}")
            debugLog("  World: ${instance.world.name}")
        } else {
            plugin.logger.severe("[ArenaManager] ✗ Failed to reset arena ${instance.template.name} (instance ${instance.id})")
            plugin.logger.severe("[ArenaManager] Debug information:")
            plugin.logger.severe("[ArenaManager]   Template name: ${instance.template.name}")
            plugin.logger.severe("[ArenaManager]   Selected schematic: ${selectedSchematic.name}")
            plugin.logger.severe("[ArenaManager]   Schematic path: ${selectedSchematic.absolutePath}")
            plugin.logger.severe("[ArenaManager]   World: ${instance.world.name}")
            plugin.logger.severe("[ArenaManager]   Origin: ${instance.origin}")
            plugin.logger.severe("[ArenaManager]   Instance ID: ${instance.id}")

            plugin.logger.severe("[ArenaManager] Running additional validation...")
            org.Kloppie74.locoPillars.utils.SchematicHelper.validateSchematicFile(plugin, configManager, selectedSchematic)
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
        debugLog("Returning ${instances.size} instances")
        return instances
    }

    fun getAllTemplates(): List<ArenaTemplate> {
        debugLog("Returning ${templates.size} templates")
        return templates.values.toList()
    }

    fun getInstancesByTemplate(templateName: String): List<ArenaInstance> {
        val matchingInstances = instances.filter { it.template.name == templateName }
        debugLog("Found ${matchingInstances.size} instances for template: $templateName")
        return matchingInstances
    }

    fun onDisable() {
        debugLog("Disabling plugin: Unloading and deleting game worlds...")
        worldManager.unloadAllGameWorlds()
        worldManager.deleteAllUnloadedPillarsWorldFolders()
        debugLog("All game worlds unloaded and GameWorlds folder deleted.")
    }
}