package org.Kloppie74.locoPillars.editor

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.Kloppie74.locoPillars.editor.model.MapFileInfo
import org.Kloppie74.locoPillars.game.map.MapRepository
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import java.io.File

/**
 * GameFileService
 *
 * Service layer for all file I/O operations related to game maps:
 * - Reading map metadata from `settings.yml`
 * - Verifying file existence and readability
 * - Providing structured [MapFileInfo] snapshots
 * - Copying map templates
 * - Listing maps with status information
 *
 * This service is intentionally read-heavy and does **not** perform WorldEdit
 * schematic paste/save operations — those stay in
 * [org.Kloppie74.locoPillars.game.map.EditorSessionManager].
 *
 * @param plugin Plugin instance for logging and data-folder resolution.
 * @param configManager Configuration manager for debug flag.
 * @param mapRepository Underlying map storage.
 */
class GameFileService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val mapRepository: MapRepository
) {
    private val mapsFolder: File get() = File(plugin.dataFolder, "maps")
    private val schemsFolder: File get() = File(plugin.dataFolder, "Data/Schems")

    // -------------------------------------------------------------------------
    // Map file inspection
    // -------------------------------------------------------------------------

    /**
     * Builds a [MapFileInfo] snapshot for [mapName].
     * Never throws; missing/unreadable files are reflected in the returned object.
     */
    fun getMapFileInfo(mapName: String): MapFileInfo {
        val settingsFile  = mapRepository.getMapSettingsFile(mapName)
        val schematicFile = mapRepository.getMapSchematicFile(mapName)

        return MapFileInfo(
            mapName          = mapName,
            settingsFile     = settingsFile,
            schematicFile    = schematicFile,
            settingsExists   = settingsFile.exists() && settingsFile.isFile,
            schematicExists  = schematicFile.exists() && schematicFile.isFile,
            settingsSizeBytes  = if (settingsFile.exists()) settingsFile.length() else 0L,
            schematicSizeBytes = if (schematicFile.exists()) schematicFile.length() else 0L
        )
    }

    /**
     * Returns [MapFileInfo] for every map currently in the maps folder.
     */
    fun getAllMapFileInfos(): List<MapFileInfo> {
        return mapRepository.getAllMapNames().map { getMapFileInfo(it) }
    }

    // -------------------------------------------------------------------------
    // Game-asset (.yml) helpers
    // -------------------------------------------------------------------------

    /**
     * Returns a list of all `.yml` game configuration files from
     * `Data/Games/` (classic game-type configs, not map settings).
     */
    fun listGameConfigFiles(): List<File> {
        val gamesFolder = File(plugin.dataFolder, "Data/Games")
        if (!gamesFolder.exists()) return emptyList()
        return gamesFolder.listFiles { f -> f.isFile && f.extension == "yml" }?.toList() ?: emptyList()
    }

    /**
     * Returns a list of all `.schem` files from the legacy `Data/Schems/` folder
     * (game-type schematics, not the per-map `region.schem`).
     */
    fun listLegacySchematicFiles(): List<File> {
        if (!schemsFolder.exists()) return emptyList()
        return schemsFolder.listFiles { f -> f.isFile && f.extension == "schem" }?.toList() ?: emptyList()
    }

    /**
     * Tries to load a game-config YAML file by [configName] (without `.yml`).
     * Returns null and logs a warning on failure.
     */
    fun loadGameConfig(configName: String): YamlConfiguration? {
        val file = File(plugin.dataFolder, "Data/Games/$configName.yml")
        if (!file.exists()) {
            plugin.logger.warning("[GameFileService] Game config not found: $configName")
            return null
        }
        return try {
            YamlConfiguration.loadConfiguration(file)
        } catch (e: Exception) {
            plugin.logger.severe("[GameFileService] Failed to load game config '$configName': ${e.message}")
            null
        }
    }

    /**
     * Saves a game-config YAML file for [configName].
     * Returns `true` on success.
     */
    fun saveGameConfig(configName: String, config: YamlConfiguration): Boolean {
        val gamesFolder = File(plugin.dataFolder, "Data/Games")
        gamesFolder.mkdirs()
        val file = File(gamesFolder, "$configName.yml")
        return try {
            config.save(file)
            debugLog("Saved game config: $configName")
            true
        } catch (e: Exception) {
            plugin.logger.severe("[GameFileService] Failed to save game config '$configName': ${e.message}")
            false
        }
    }

    // -------------------------------------------------------------------------
    // Template & copy utilities
    // -------------------------------------------------------------------------

    /**
     * Copies [sourceFile] to [destinationName] inside `Data/Games/`.
     * Returns `true` if the copy succeeded.
     */
    fun copyGameConfigTemplate(sourceFile: File, destinationName: String): Boolean {
        val dest = File(plugin.dataFolder, "Data/Games/$destinationName.yml")
        return try {
            sourceFile.copyTo(dest, overwrite = false)
            debugLog("Copied template ${sourceFile.name} → $destinationName.yml")
            true
        } catch (e: Exception) {
            plugin.logger.warning("[GameFileService] Copy failed for '$destinationName': ${e.message}")
            false
        }
    }

    // -------------------------------------------------------------------------
    // Folder readiness
    // -------------------------------------------------------------------------

    /**
     * Ensures that the `Data/Games` and `Data/Schems` folders exist.
     * Creates them if needed and logs the result.
     */
    fun ensureDataFolders() {
        val gameFoldr = File(plugin.dataFolder, "Data/Games")
        val schemFldr = File(plugin.dataFolder, "Data/Schems")

        if (!gameFoldr.exists()) {
            gameFoldr.mkdirs()
            plugin.logger.info("[GameFileService] Created Data/Games folder")
        }
        if (!schemFldr.exists()) {
            schemFldr.mkdirs()
            plugin.logger.info("[GameFileService] Created Data/Schems folder")
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun debugLog(message: String) {
        if (configManager.debugMode) {
            plugin.logger.info("[GameFileService] $message")
        }
    }
}
