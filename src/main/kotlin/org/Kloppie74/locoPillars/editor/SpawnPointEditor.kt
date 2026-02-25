package org.Kloppie74.locoPillars.editor

import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.configuration.file.YamlConfiguration
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.editor.model.SpawnPointData
import org.Kloppie74.locoPillars.game.map.MapRepository
import org.Kloppie74.locoPillars.utils.MSG

/**
 * SpawnPointEditor
 *
 * Provides high-level operations for reading, creating, updating and removing
 * spawn points in a map's `settings.yml`.  All mutations are persisted
 * immediately via [MapRepository].
 *
 * Spawn points live under the `SpawnLocations` YAML key with the structure:
 * ```yaml
 * SpawnLocations:
 *   Team1:
 *     Name: "&c[Red]"
 *     Color: RED
 *     Player1:
 *       X: -10.5
 *       Y: 65.0
 *       Z: -10.5
 *       Yaw: 45.0
 *       Pitch: 0.0
 *       Cage-Rotation: 0
 * ```
 *
 * @param plugin Plugin instance for logging.
 * @param mapRepository Repository used to load and save map settings.
 */
class SpawnPointEditor(
    private val plugin: LocoPillars,
    private val mapRepository: MapRepository
) {

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Reads all spawn points for [mapName] from disk.
     * Returns an empty list if the settings file cannot be loaded or the
     * `SpawnLocations` section is absent.
     */
    fun getAllSpawnPoints(mapName: String): List<SpawnPointData> {
        val config = mapRepository.loadMapSettings(mapName) ?: run {
            plugin.logger.warning("[SpawnPointEditor] Could not load settings for map: $mapName")
            return emptyList()
        }

        val spawnSection = config.getConfigurationSection("SpawnLocations") ?: return emptyList()
        val reservedKeys = setOf("MinimumPlayers", "MaximumPlayers", "MaxTeams", "PlayersPerTeam")
        val result = mutableListOf<SpawnPointData>()

        spawnSection.getKeys(false).forEach { teamKey ->
            if (teamKey in reservedKeys) return@forEach
            val teamSection = spawnSection.getConfigurationSection(teamKey) ?: return@forEach
            val teamName = teamSection.getString("Name", teamKey) ?: teamKey

            teamSection.getKeys(false)
                .filter { it.startsWith("Player") }
                .forEach { playerKey ->
                    val ps = teamSection.getConfigurationSection(playerKey) ?: return@forEach
                    result += SpawnPointData(
                        teamKey       = teamKey,
                        teamName      = teamName,
                        playerKey     = playerKey,
                        x             = ps.getDouble("X"),
                        y             = ps.getDouble("Y"),
                        z             = ps.getDouble("Z"),
                        yaw           = ps.getDouble("Yaw", 0.0).toFloat(),
                        pitch         = ps.getDouble("Pitch", 0.0).toFloat(),
                        cageRotation  = ps.getInt("Cage-Rotation", 0)
                    )
                }
        }

        return result
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Adds or overwrites a single spawn point entry in [mapName]'s settings.
     *
     * @param mapName Map to modify.
     * @param teamKey YAML key of the team (e.g. "Team1").
     * @param playerKey Sub-key of the player slot (e.g. "Player1").
     * @param location The world location to record.
     * @param cageRotation Cage rotation override (default 0).
     * @return `true` on success, `false` on persistence failure.
     */
    fun setSpawnPoint(
        mapName: String,
        teamKey: String,
        playerKey: String,
        location: Location,
        cageRotation: Int = 0
    ): Boolean {
        val config = mapRepository.loadMapSettings(mapName) ?: run {
            plugin.logger.warning("[SpawnPointEditor] Cannot set spawn: settings missing for $mapName")
            return false
        }

        val base = "SpawnLocations.$teamKey.$playerKey"
        config.set("$base.X",             location.x)
        config.set("$base.Y",             location.y)
        config.set("$base.Z",             location.z)
        config.set("$base.Yaw",           location.yaw.toDouble())
        config.set("$base.Pitch",         location.pitch.toDouble())
        config.set("$base.Cage-Rotation", cageRotation)

        val saved = mapRepository.saveMapSettings(mapName, config)
        if (saved) {
            debugLog("Set spawn $teamKey/$playerKey for $mapName @ (${location.blockX}, ${location.blockY}, ${location.blockZ})")
        } else {
            plugin.logger.warning("[SpawnPointEditor] Failed to save settings for $mapName after setting spawn.")
        }
        return saved
    }

    /**
     * Removes a spawn point entry from [mapName]'s settings.
     *
     * @return `true` if the key was removed and the file was saved, `false` otherwise.
     */
    fun removeSpawnPoint(mapName: String, teamKey: String, playerKey: String): Boolean {
        val config = mapRepository.loadMapSettings(mapName) ?: return false
        config.set("SpawnLocations.$teamKey.$playerKey", null)
        return mapRepository.saveMapSettings(mapName, config)
    }

    /**
     * Removes an entire team section from [mapName]'s settings.
     *
     * @return `true` if the team section was removed and the file was saved.
     */
    fun removeTeam(mapName: String, teamKey: String): Boolean {
        val config = mapRepository.loadMapSettings(mapName) ?: return false
        config.set("SpawnLocations.$teamKey", null)
        return mapRepository.saveMapSettings(mapName, config)
    }

    /**
     * Ensures a team section exists with basic metadata.
     * Does not overwrite existing values.
     *
     * @return `true` if the settings were persisted successfully.
     */
    fun ensureTeamExists(
        mapName: String,
        teamKey: String,
        teamDisplayName: String,
        teamColor: String = "WHITE"
    ): Boolean {
        val config = mapRepository.loadMapSettings(mapName) ?: return false
        val path = "SpawnLocations.$teamKey"

        if (!config.contains("$path.Name")) {
            config.set("$path.Name", teamDisplayName)
        }
        if (!config.contains("$path.Color")) {
            config.set("$path.Color", teamColor)
        }

        return mapRepository.saveMapSettings(mapName, config)
    }

    // -------------------------------------------------------------------------
    // Player-facing helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a formatted list of all spawn points for [mapName] to [player].
     */
    fun showSpawnPoints(player: Player, mapName: String) {
        val points = getAllSpawnPoints(mapName)

        if (points.isEmpty()) {
            player.sendMessage(MSG.chatColors("&eNo spawn points defined for &f$mapName&e."))
            player.sendMessage(MSG.chatColors("&7Use &e/editor spawn set <team> <slot> &7to add one."))
            return
        }

        player.sendMessage(MSG.chatColors("&6=== Spawn Points: $mapName (${points.size}) ==="))
        points.forEach { sp ->
            player.sendMessage(MSG.chatColors(
                "&e${sp.teamKey}&7/&f${sp.playerKey} &7→ " +
                "&b(${sp.x.toInt()}, ${sp.y.toInt()}, ${sp.z.toInt()}) " +
                "&7yaw:&f${String.format("%.1f", sp.yaw)}"
            ))
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[SpawnPointEditor] $message")
        }
    }
}
