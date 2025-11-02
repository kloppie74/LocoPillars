package org.Kloppie74.locoPillars.game.config

import java.io.File
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin
import org.yaml.snakeyaml.Yaml
import org.Kloppie74.locoPillars.game.data.Team

class GameConfig(
    val name: String,
    val file: File,
    val configMap: Map<String, Any>,
    val teams: List<Team>
) {
    fun getMaxPlayers(): Int {
        val spawnLocs = configMap["SpawnLocations"] as? Map<String, Any> ?: return 8
        return (spawnLocs["MaximumPlayers"] as? Number)?.toInt() ?: 8
    }

    fun getMinPlayers(): Int {
        val spawnLocs = configMap["SpawnLocations"] as? Map<String, Any> ?: return 4
        return (spawnLocs["MinimumPlayers"] as? Number)?.toInt() ?: 4
    }

    fun getMaxTeams(): Int {
        val spawnLocs = configMap["SpawnLocations"] as? Map<String, Any> ?: return 8
        return (spawnLocs["MaxTeams"] as? Number)?.toInt() ?: 8
    }

    fun getPlayersPerTeam(): Int {
        val spawnLocs = configMap["SpawnLocations"] as? Map<String, Any> ?: return 1
        return (spawnLocs["PlayersPerTeam"] as? Number)?.toInt() ?: 1
    }

    fun getDisplayName(): String {
        val settings = configMap["Settings"] as? Map<String, Any> ?: return name
        return (settings["DisplayName"] as? String) ?: name
    }

    fun getBuildHeight(): Int {
        val settings = configMap["Settings"] as? Map<String, Any> ?: return 60
        return (settings["BuildHeight"] as? Number)?.toInt() ?: 60
    }

    fun getBuildRange(): Int {
        val settings = configMap["Settings"] as? Map<String, Any> ?: return 40
        return (settings["BuildRange"] as? Number)?.toInt() ?: 40
    }

    fun getDeathHeight(): Int {
        val settings = configMap["Settings"] as? Map<String, Any> ?: return -5
        return (settings["DeathHeight"] as? Number)?.toInt() ?: -5
    }

    fun getLobbyTimer(): Int {
        val settings = configMap["Settings"] as? Map<String, Any> ?: return 10
        return (settings["LobbyTimer"] as? Number)?.toInt() ?: 10
    }

    fun getCageTimer(): Int {
        val settings = configMap["Settings"] as? Map<String, Any> ?: return 5
        return (settings["CageTimer"] as? Number)?.toInt() ?: 5
    }

    fun getGameTimer(): Int {
        val settings = configMap["Settings"] as? Map<String, Any> ?: return 900
        return (settings["GameTimer"] as? Number)?.toInt() ?: 900
    }

    fun getItemTimer(): Int {
        val settings = configMap["Settings"] as? Map<String, Any> ?: return 5
        return (settings["ItemTimer"] as? Number)?.toInt() ?: 5
    }

    fun canBreakMapBlocks(): Boolean {
        val settings = configMap["Settings"] as? Map<String, Any> ?: return false
        return (settings["Can-Break-Map-Blocks"] as? Boolean) ?: false
    }

    fun getSpawnTeleportLocation(origin: Location): Location {
        val settings = configMap["Settings"] as? Map<String, Any> ?: return origin
        val spawnPoint = settings["SpawnPoint"] as? Map<String, Any> ?: return origin

        val x = (spawnPoint["X"] as? Number)?.toDouble() ?: 0.0
        val y = (spawnPoint["Y"] as? Number)?.toDouble() ?: 60.0
        val z = (spawnPoint["Z"] as? Number)?.toDouble() ?: 0.0

        return Location(origin.world, origin.x + x, origin.y + y, origin.z + z)
    }

    fun getSpawnArea(origin: Location): Pair<Location, Location>? {
        val settings = configMap["Settings"] as? Map<String, Any> ?: return null
        val spawnArea = settings["Spawn-Area"] as? Map<String, Any> ?: return null

        val loc1Data = spawnArea["Loc1"] as? Map<String, Any> ?: return null
        val loc2Data = spawnArea["Loc2"] as? Map<String, Any> ?: return null

        val x1 = (loc1Data["X"] as? Number)?.toDouble() ?: return null
        val y1 = (loc1Data["Y"] as? Number)?.toDouble() ?: return null
        val z1 = (loc1Data["Z"] as? Number)?.toDouble() ?: return null

        val x2 = (loc2Data["X"] as? Number)?.toDouble() ?: return null
        val y2 = (loc2Data["Y"] as? Number)?.toDouble() ?: return null
        val z2 = (loc2Data["Z"] as? Number)?.toDouble() ?: return null

        val loc1 = Location(origin.world, origin.x + x1, origin.y + y1, origin.z + z1)
        val loc2 = Location(origin.world, origin.x + x2, origin.y + y2, origin.z + z2)

        return Pair(loc1, loc2)
    }

    fun hasSpawnArea(): Boolean {
        val settings = configMap["Settings"] as? Map<String, Any> ?: return false
        return settings.containsKey("Spawn-Area")
    }

    fun getAsFileConfiguration(): org.bukkit.configuration.file.FileConfiguration {
        val config = org.bukkit.configuration.file.YamlConfiguration()
        configMap.forEach { (key, value) ->
            config.set(key, value)
        }
        return config
    }



    companion object {
        fun loadAll(plugin: JavaPlugin): List<GameConfig> {
            val folder = File(plugin.dataFolder, "Data/Games")
            if (!folder.exists()) {
                folder.mkdirs()
                plugin.logger.info("[GameConfig] Created Data/Games folder at: ${folder.absolutePath}")
                return emptyList()
            }

            val yaml = Yaml()
            val configs = folder.listFiles { f -> f.extension == "yml" }?.mapNotNull { file ->
                try {
                    plugin.logger.info("[GameConfig] Loading config: ${file.name}")
                    val map = yaml.load<Map<String, Any>>(file.inputStream())
                    val teams = ConfigLoader.parseTeams(map["SpawnLocations"] as? Map<String, Any> ?: emptyMap())
                    plugin.logger.info("[GameConfig] Loaded config ${file.nameWithoutExtension} with ${teams.size} teams")
                    GameConfig(file.nameWithoutExtension, file, map, teams)
                } catch (e: Exception) {
                    plugin.logger.severe("[GameConfig] Failed to load config ${file.name}: ${e.message}")
                    e.printStackTrace()
                    null
                }
            } ?: emptyList()

            plugin.logger.info("[GameConfig] Successfully loaded ${configs.size} game configurations")
            return configs
        }

        fun loadSingle(plugin: JavaPlugin, configFile: File): GameConfig? {
            // Optioneel: extra check of file in ./Data/Games ligt
            val gamesFolder = File(plugin.dataFolder, "Data/Games")
            val canonicalGamesFolder = gamesFolder.canonicalFile
            val canonicalFile = configFile.canonicalFile
            if (!canonicalFile.path.startsWith(canonicalGamesFolder.path)) {
                plugin.logger.warning("[GameConfig] Warning: config file ${configFile.path} is not in Data/Games!")
            }

            return try {
                plugin.logger.info("[GameConfig] Loading single config: ${configFile.name}")
                val yaml = Yaml()
                val map = yaml.load<Map<String, Any>>(configFile.inputStream())
                val teams = ConfigLoader.parseTeams(map["SpawnLocations"] as? Map<String, Any> ?: emptyMap())
                plugin.logger.info("[GameConfig] Loaded single config ${configFile.nameWithoutExtension} with ${teams.size} teams")
                GameConfig(configFile.nameWithoutExtension, configFile, map, teams)
            } catch (e: Exception) {
                plugin.logger.severe("[GameConfig] Failed to load single config ${configFile.name}: ${e.message}")
                e.printStackTrace()
                null
            }
        }
    }
}