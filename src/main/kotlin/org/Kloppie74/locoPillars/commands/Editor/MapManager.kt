package org.Kloppie74.locoPillars.commands.Editor

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.configuration.file.YamlConfiguration
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.SchematicHelper
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import org.Kloppie74.locoPillars.game.config.GameConfig
import java.io.File
import java.util.UUID
import kotlin.random.Random

object MapManager {

    private val dataFolder: File get() = File(LocoPillars.instance.dataFolder, "Data")
    private val gamesFolder: File get() = File(dataFolder, "Games")
    private val schemsFolder: File get() = File(dataFolder, "Schems")
    private val configManager: ConfigManager get() = LocoPillars.instance.configManager
    private val plugin get() = LocoPillars.instance

    init {
        ensureFoldersExist()
    }

    private fun ensureFoldersExist() {
        if (!dataFolder.exists()) dataFolder.mkdirs()
        if (!gamesFolder.exists()) gamesFolder.mkdirs()
        if (!schemsFolder.exists()) schemsFolder.mkdirs()
    }

    fun getAllMapNames(): List<String> {
        val mapNames = mutableSetOf<String>()

        // Get from config files
        if (gamesFolder.exists()) {
            gamesFolder.listFiles { f -> f.extension == "yml" }?.forEach {
                mapNames.add(it.nameWithoutExtension)
            }
        }

        // Get from schematic folders and files
        if (schemsFolder.exists()) {
            schemsFolder.listFiles { f -> f.isDirectory }?.forEach {
                mapNames.add(it.name)
            }
            schemsFolder.listFiles { f -> f.extension == "schem" }?.forEach {
                mapNames.add(it.nameWithoutExtension)
            }
        }

        return mapNames.sorted()
    }

    fun createMap(player: Player, name: String) {
        ensureFoldersExist()

        if (getAllMapNames().contains(name)) {
            player.sendMessage(MSG.chatColors("&cMap '$name' already exists!"))
            return
        }

        // Create config file with default values
        val configFile = File(gamesFolder, "$name.yml")
        if (!configFile.exists()) {
            createDefaultConfig(configFile, name)
            player.sendMessage(MSG.chatColors("&aCreated config file: &f${configFile.name}"))
        }

        // Create schematic folder
        val mapSchemsFolder = File(schemsFolder, name)
        if (!mapSchemsFolder.exists()) {
            mapSchemsFolder.mkdirs()
            player.sendMessage(MSG.chatColors("&aCreated schematic folder: &f${mapSchemsFolder.name}"))
        }

        // Generate UUID for world name
        val worldUUID = UUID.randomUUID().toString().replace("-", "")
        val worldName = "pillars_edit_$worldUUID"
        val world = createVoidWorld(worldName)
        val spawn = Location(world, 0.5, 60.0, 0.5)

        player.teleport(spawn)
        player.sendMessage(MSG.chatColors("&aTeleported to edit world: &f$worldName"))
        player.sendMessage(MSG.chatColors("&7Map &e$name &7created successfully!"))
        player.sendMessage(MSG.chatColors("&7Use &e/pillars admin menu &7to configure settings."))

        // Give position tool
        PositionTool.givePositionTool(player)
    }

    private fun createDefaultConfig(configFile: File, mapName: String) {
        val config = YamlConfiguration()

        // Settings section
        config.set("Settings.DisplayName", mapName)
        config.set("Settings.BuildHeight", 60)
        config.set("Settings.BuildRange", 40)
        config.set("Settings.DeathHeight", -5)
        config.set("Settings.LobbyTimer", 10)
        config.set("Settings.CageTimer", 5)
        config.set("Settings.GameTimer", 900)
        config.set("Settings.ItemTimer", 5)
        config.set("Settings.Can-Break-Map-Blocks", false)
        config.set("Settings.SpawnPoint.X", 0.5)
        config.set("Settings.SpawnPoint.Y", 60.0)
        config.set("Settings.SpawnPoint.Z", 0.5)

        // Spawn Locations section
        config.set("SpawnLocations.MinimumPlayers", 4)
        config.set("SpawnLocations.MaximumPlayers", 8)
        config.set("SpawnLocations.MaxTeams", 8)
        config.set("SpawnLocations.PlayersPerTeam", 1)

        // Default teams (Team1 and Team2 as examples)
        createDefaultTeam(config, "Team1", "RED", 10.0, 60.0, 10.0)
        createDefaultTeam(config, "Team2", "BLUE", -10.0, 60.0, -10.0)
        createDefaultTeam(config, "Team3", "GREEN", 10.0, 60.0, -10.0)
        createDefaultTeam(config, "Team4", "YELLOW", -10.0, 60.0, 10.0)

        // Item Configuration section
        config.set("ItemConfiguration.EnableItems", true)
        config.set("ItemConfiguration.ItemSpawnDelay", 5)

        try {
            config.save(configFile)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to create default config for $mapName: ${e.message}")
        }
    }

    private fun createDefaultTeam(
        config: YamlConfiguration,
        teamName: String,
        color: String,
        x: Double,
        y: Double,
        z: Double
    ) {
        config.set("SpawnLocations.$teamName.Name", teamName)
        config.set("SpawnLocations.$teamName.Color", color)
        config.set("SpawnLocations.$teamName.Player1.X", x)
        config.set("SpawnLocations.$teamName.Player1.Y", y)
        config.set("SpawnLocations.$teamName.Player1.Z", z)
        config.set("SpawnLocations.$teamName.Player1.Yaw", 0.0f)
        config.set("SpawnLocations.$teamName.Player1.Pitch", 0.0f)
        config.set("SpawnLocations.$teamName.Player1.Cage-Rotation", 0)
    }

    fun deleteMap(name: String) {
        // Delete config file
        val configFile = File(gamesFolder, "$name.yml")
        if (configFile.exists()) {
            configFile.delete()
            plugin.logger.info("Deleted config file: ${configFile.name}")
        }

        // Delete schematic folder
        val mapSchemsFolder = File(schemsFolder, name)
        if (mapSchemsFolder.exists()) {
            mapSchemsFolder.deleteRecursively()
            plugin.logger.info("Deleted schematic folder: ${mapSchemsFolder.name}")
        }

        // Delete single schematic file
        val singleSchemFile = File(schemsFolder, "$name.schem")
        if (singleSchemFile.exists()) {
            singleSchemFile.delete()
            plugin.logger.info("Deleted schematic file: ${singleSchemFile.name}")
        }

        // Delete associated worlds (if any exist)
        deleteAssociatedWorlds(name)
    }

    private fun deleteAssociatedWorlds(mapName: String) {
        Bukkit.getWorlds().forEach { world ->
            if (world.name.contains(mapName) && world.name.startsWith("pillars_")) {
                val mainWorld = Bukkit.getWorlds().firstOrNull { !it.name.startsWith("pillars_") }
                if (mainWorld != null) {
                    world.players.forEach { player ->
                        player.teleport(mainWorld.spawnLocation)
                        player.sendMessage(MSG.chatColors("&cWorld deleted, teleported to spawn!"))
                    }
                }

                val unloaded = Bukkit.unloadWorld(world, false)
                if (unloaded) {
                    val worldFolder = File(world.name)
                    if (worldFolder.exists()) {
                        worldFolder.deleteRecursively()
                    }
                }
            }
        }
    }

    fun teleportAndPasteSchematic(player: Player, mapName: String) {
        val worldName = findOrCreateWorldForMap(mapName)
        val world = Bukkit.getWorld(worldName) ?: createVoidWorld(worldName)
        val spawn = Location(world, 0.5, 60.0, 0.5)

        player.teleport(spawn)
        player.sendMessage(MSG.chatColors("&aTeleported to edit world: &f$worldName"))

        // Find and paste schematic
        val schemFiles = findSchematicFiles(mapName)
        if (schemFiles.isNotEmpty()) {
            val chosenSchem = schemFiles[Random.nextInt(schemFiles.size)]
            val result = SchematicHelper.pasteSchematic(plugin, configManager, chosenSchem, spawn)
            if (result) {
                player.sendMessage(MSG.chatColors("&aPasted schematic: &f${chosenSchem.name}"))
            } else {
                player.sendMessage(MSG.chatColors("&cFailed to paste schematic: &f${chosenSchem.name}"))
            }
        } else {
            player.sendMessage(MSG.chatColors("&eNo schematics found for this map. Build your arena!"))
        }

        // Give position tool
        PositionTool.givePositionTool(player)
    }

    private fun findOrCreateWorldForMap(mapName: String): String {
        // Look for existing world
        val existingWorld = Bukkit.getWorlds().firstOrNull {
            it.name.contains(mapName) && it.name.startsWith("pillars_edit_")
        }

        if (existingWorld != null) {
            return existingWorld.name
        }

        // Create new world
        val worldUUID = UUID.randomUUID().toString().replace("-", "")
        return "pillars_edit_${mapName}_$worldUUID"
    }

    private fun findSchematicFiles(mapName: String): List<File> {
        val schemFiles = mutableListOf<File>()

        // Check folder
        val schemFolder = File(schemsFolder, mapName)
        if (schemFolder.exists() && schemFolder.isDirectory) {
            schemFolder.listFiles { f -> f.extension == "schem" }?.forEach { file ->
                if (SchematicHelper.validateSchematicFile(plugin, configManager, file)) {
                    schemFiles.add(file)
                }
            }
        }

        // Check single file
        if (schemFiles.isEmpty()) {
            val singleFile = File(schemsFolder, "$mapName.schem")
            if (singleFile.exists() && SchematicHelper.validateSchematicFile(plugin, configManager, singleFile)) {
                schemFiles.add(singleFile)
            }
        }

        return schemFiles
    }

    fun getMapConfig(mapName: String): GameConfig? {
        val configFile = File(gamesFolder, "$mapName.yml")
        return if (configFile.exists()) {
            GameConfig.loadSingle(plugin, configFile)
        } else {
            null
        }
    }

    fun updateConfigValue(mapName: String, key: String, value: Any) {
        val configFile = File(gamesFolder, "$mapName.yml")
        if (!configFile.exists()) {
            plugin.logger.warning("Config file not found for map: $mapName")
            return
        }

        val config = YamlConfiguration.loadConfiguration(configFile)

        // Map keys to proper config paths
        val configPath = when (key) {
            "DisplayName" -> "Settings.DisplayName"
            "BuildHeight" -> "Settings.BuildHeight"
            "BuildRange" -> "Settings.BuildRange"
            "DeathHeight" -> "Settings.DeathHeight"
            "MinimumPlayers" -> "SpawnLocations.MinimumPlayers"
            "MaximumPlayers" -> "SpawnLocations.MaximumPlayers"
            "MaxTeams" -> "SpawnLocations.MaxTeams"
            "PlayersPerTeam" -> "SpawnLocations.PlayersPerTeam"
            "LobbyTimer" -> "Settings.LobbyTimer"
            "CageTimer" -> "Settings.CageTimer"
            "GameTimer" -> "Settings.GameTimer"
            "ItemTimer" -> "Settings.ItemTimer"
            "Can-Break-Map-Blocks" -> "Settings.Can-Break-Map-Blocks"
            else -> key
        }

        config.set(configPath, value)

        try {
            config.save(configFile)
            plugin.logger.info("Updated $configPath to $value for map $mapName")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save config for $mapName: ${e.message}")
        }
    }

    fun saveMapConfig(mapName: String) {
        val configFile = File(gamesFolder, "$mapName.yml")
        if (configFile.exists()) {
            plugin.logger.info("Config for $mapName is already saved to file")
        } else {
            plugin.logger.warning("Config file not found for map: $mapName")
        }
    }

    fun addTeamSpawn(mapName: String, teamName: String, playerNum: Int, location: Location, yaw: Float, pitch: Float) {
        val configFile = File(gamesFolder, "$mapName.yml")
        if (!configFile.exists()) return

        val config = YamlConfiguration.loadConfiguration(configFile)
        val teamPath = "SpawnLocations.$teamName"
        val playerPath = "$teamPath.Player$playerNum"

        // Ensure team exists
        if (!config.contains(teamPath)) {
            config.set("$teamPath.Name", teamName)
            config.set("$teamPath.Color", "GRAY")
        }

        // Add player spawn
        config.set("$playerPath.X", location.x)
        config.set("$playerPath.Y", location.y)
        config.set("$playerPath.Z", location.z)
        config.set("$playerPath.Yaw", yaw)
        config.set("$playerPath.Pitch", pitch)
        config.set("$playerPath.Cage-Rotation", 0)

        try {
            config.save(configFile)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save team spawn for $mapName: ${e.message}")
        }
    }

    fun setSpawnArea(mapName: String, loc1: Location, loc2: Location) {
        val configFile = File(gamesFolder, "$mapName.yml")
        if (!configFile.exists()) return

        val config = YamlConfiguration.loadConfiguration(configFile)

        config.set("Settings.Spawn-Area.Loc1.X", loc1.x)
        config.set("Settings.Spawn-Area.Loc1.Y", loc1.y)
        config.set("Settings.Spawn-Area.Loc1.Z", loc1.z)

        config.set("Settings.Spawn-Area.Loc2.X", loc2.x)
        config.set("Settings.Spawn-Area.Loc2.Y", loc2.y)
        config.set("Settings.Spawn-Area.Loc2.Z", loc2.z)

        try {
            config.save(configFile)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save spawn area for $mapName: ${e.message}")
        }
    }

    fun saveSchematic(mapName: String, player: Player, schematicName: String) {
        val pos1 = PositionTool.getPos1(player)
        val pos2 = PositionTool.getPos2(player)

        if (pos1 == null || pos2 == null) {
            player.sendMessage(MSG.chatColors("&cPlease select both positions with the position tool first!"))
            return
        }

        if (pos1.world != pos2.world) {
            player.sendMessage(MSG.chatColors("&cBoth positions must be in the same world!"))
            return
        }

        val targetFolder = File(schemsFolder, mapName)
        if (!targetFolder.exists()) {
            targetFolder.mkdirs()
        }

        val schematicFile = File(targetFolder, "$schematicName.schem")
        val success = SchematicHelper.saveSchematic(plugin, configManager, pos1, pos2, schematicFile)

        if (success) {
            player.sendMessage(MSG.chatColors("&aSchematic saved: &f$schematicName.schem"))
            player.sendMessage(MSG.chatColors("&7Saved to: &f${schematicFile.absolutePath}"))
        } else {
            player.sendMessage(MSG.chatColors("&cFailed to save schematic!"))
        }
    }

    private fun createVoidWorld(worldName: String): World {
        if (Bukkit.getWorld(worldName) != null) {
            return Bukkit.getWorld(worldName)!!
        }

        val creator = org.bukkit.WorldCreator(worldName)
            .generator(VoidChunkGenerator())
            .generateStructures(false)
        return creator.createWorld()!!
    }

    class VoidChunkGenerator : org.bukkit.generator.ChunkGenerator() {
        override fun generateChunkData(
            world: World,
            random: java.util.Random,
            x: Int,
            z: Int,
            biome: BiomeGrid
        ): ChunkData {
            return createChunkData(world)
        }

        override fun canSpawn(world: World, x: Int, z: Int): Boolean = true
        override fun getDefaultPopulators(world: World): List<org.bukkit.generator.BlockPopulator> = emptyList()
    }
}