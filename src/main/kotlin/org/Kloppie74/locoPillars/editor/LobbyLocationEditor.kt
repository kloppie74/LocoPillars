package org.Kloppie74.locoPillars.editor

import org.bukkit.Location
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.map.MapRepository
import org.Kloppie74.locoPillars.utils.MSG

/**
 * LobbyLocationEditor
 *
 * Handles reading and writing of the pre-game lobby settings inside a map's
 * `settings.yml`.  All values live under the `Settings` key to match the
 * existing game format:
 *
 * ```yaml
 * Settings:
 *   SpawnPoint:
 *     X: 0.0
 *     Y: 60.0
 *     Z: 0.0
 *     Yaw: 0.0
 *     Pitch: 0.0
 *   Spawn-Area:
 *     Loc1:
 *       X: -10.0
 *       Y: 59.0
 *       Z: -10.0
 *     Loc2:
 *       X: 10.0
 *       Y: 64.0
 *       Z: 10.0
 * ```
 *
 * @param plugin Plugin instance for logging.
 * @param mapRepository Repository used to load and save map settings.
 */
class LobbyLocationEditor(
    private val plugin: LocoPillars,
    private val mapRepository: MapRepository
) {

    // -------------------------------------------------------------------------
    // Data holders
    // -------------------------------------------------------------------------

    /**
     * Snapshot of the lobby spawn point as stored in `settings.yml`.
     */
    data class LobbySpawn(
        val x: Double,
        val y: Double,
        val z: Double,
        val yaw: Float = 0f,
        val pitch: Float = 0f
    ) {
        fun summary(): String =
            "(${x.toInt()}, ${y.toInt()}, ${z.toInt()}) yaw:${"%.1f".format(yaw)}"
    }

    /**
     * Snapshot of the two corners that define the lobby spawn-area.
     */
    data class LobbyArea(
        val x1: Double, val y1: Double, val z1: Double,
        val x2: Double, val y2: Double, val z2: Double
    ) {
        fun summary(): String =
            "(${x1.toInt()}, ${y1.toInt()}, ${z1.toInt()}) ↔ (${x2.toInt()}, ${y2.toInt()}, ${z2.toInt()})"

        fun volume(): Int {
            val dx = Math.abs(x1.toInt() - x2.toInt()) + 1
            val dy = Math.abs(y1.toInt() - y2.toInt()) + 1
            val dz = Math.abs(z1.toInt() - z2.toInt()) + 1
            return dx * dy * dz
        }
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /** Returns the current lobby spawn point for [mapName], or `null` if not set. */
    fun getLobbySpawn(mapName: String): LobbySpawn? {
        val config = mapRepository.loadMapSettings(mapName) ?: return null
        val path = "Settings.SpawnPoint"
        if (!config.contains(path)) return null
        return LobbySpawn(
            x     = config.getDouble("$path.X"),
            y     = config.getDouble("$path.Y"),
            z     = config.getDouble("$path.Z"),
            yaw   = config.getDouble("$path.Yaw", 0.0).toFloat(),
            pitch = config.getDouble("$path.Pitch", 0.0).toFloat()
        )
    }

    /** Returns the current lobby spawn-area for [mapName], or `null` if not set. */
    fun getLobbyArea(mapName: String): LobbyArea? {
        val config = mapRepository.loadMapSettings(mapName) ?: return null
        val path = "Settings.Spawn-Area"
        if (!config.contains(path)) return null
        return LobbyArea(
            x1 = config.getDouble("$path.Loc1.X"),
            y1 = config.getDouble("$path.Loc1.Y"),
            z1 = config.getDouble("$path.Loc1.Z"),
            x2 = config.getDouble("$path.Loc2.X"),
            y2 = config.getDouble("$path.Loc2.Y"),
            z2 = config.getDouble("$path.Loc2.Z")
        )
    }

    // -------------------------------------------------------------------------
    // Write — SpawnPoint
    // -------------------------------------------------------------------------

    /**
     * Saves [location] as the lobby spawn point (`Settings.SpawnPoint`) for [mapName].
     * @return `true` on success.
     */
    fun setLobbySpawn(mapName: String, location: Location): Boolean {
        val config = mapRepository.loadMapSettings(mapName) ?: run {
            plugin.logger.warning("[LobbyLocationEditor] Cannot set lobby spawn: settings missing for $mapName")
            return false
        }

        val path = "Settings.SpawnPoint"
        config.set("$path.X",     location.x)
        config.set("$path.Y",     location.y)
        config.set("$path.Z",     location.z)
        config.set("$path.Yaw",   location.yaw.toDouble())
        config.set("$path.Pitch", location.pitch.toDouble())

        val saved = mapRepository.saveMapSettings(mapName, config)
        debugLog("Set lobby spawn for $mapName @ (${location.blockX}, ${location.blockY}, ${location.blockZ})")
        return saved
    }

    /** Removes the lobby spawn point from [mapName]'s settings. */
    fun clearLobbySpawn(mapName: String): Boolean {
        val config = mapRepository.loadMapSettings(mapName) ?: return false
        config.set("Settings.SpawnPoint", null)
        return mapRepository.saveMapSettings(mapName, config)
    }

    // -------------------------------------------------------------------------
    // Write — Spawn-Area
    // -------------------------------------------------------------------------

    /**
     * Saves [loc1] and [loc2] as the lobby spawn-area (`Settings.Spawn-Area`)
     * for [mapName].
     * @return `true` on success.
     */
    fun setLobbyArea(mapName: String, loc1: Location, loc2: Location): Boolean {
        val config = mapRepository.loadMapSettings(mapName) ?: run {
            plugin.logger.warning("[LobbyLocationEditor] Cannot set lobby area: settings missing for $mapName")
            return false
        }

        val path = "Settings.Spawn-Area"
        config.set("$path.Loc1.X", loc1.x)
        config.set("$path.Loc1.Y", loc1.y)
        config.set("$path.Loc1.Z", loc1.z)
        config.set("$path.Loc2.X", loc2.x)
        config.set("$path.Loc2.Y", loc2.y)
        config.set("$path.Loc2.Z", loc2.z)

        val saved = mapRepository.saveMapSettings(mapName, config)
        debugLog("Set lobby area for $mapName: " +
                "(${loc1.blockX},${loc1.blockY},${loc1.blockZ}) ↔ " +
                "(${loc2.blockX},${loc2.blockY},${loc2.blockZ})")
        return saved
    }

    /** Removes the lobby spawn-area from [mapName]'s settings. */
    fun clearLobbyArea(mapName: String): Boolean {
        val config = mapRepository.loadMapSettings(mapName) ?: return false
        config.set("Settings.Spawn-Area", null)
        return mapRepository.saveMapSettings(mapName, config)
    }

    // -------------------------------------------------------------------------
    // Player-facing helpers
    // -------------------------------------------------------------------------

    /**
     * Sends a formatted status overview of all lobby settings for [mapName]
     * to [player].
     */
    fun showLobbyStatus(player: Player, mapName: String) {
        player.sendMessage(MSG.chatColors("&6=== Lobby Settings: $mapName ==="))

        val spawn = getLobbySpawn(mapName)
        if (spawn != null) {
            player.sendMessage(MSG.chatColors("&eSpawnPoint: &a✓ &f${spawn.summary()}"))
        } else {
            player.sendMessage(MSG.chatColors("&eSpawnPoint: &c✗ not set"))
            player.sendMessage(MSG.chatColors("  &7Use &e/editor lobby spawn &7to set it."))
        }

        val area = getLobbyArea(mapName)
        if (area != null) {
            player.sendMessage(MSG.chatColors("&eSpawn-Area: &a✓ &f${area.summary()}"))
            player.sendMessage(MSG.chatColors("  &7Volume: &f${area.volume()} blocks"))
        } else {
            player.sendMessage(MSG.chatColors("&eSpawn-Area: &c✗ not set"))
            player.sendMessage(MSG.chatColors("  &7Use &e/editor lobby area set &7to set it."))
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[LobbyLocationEditor] $message")
        }
    }
}