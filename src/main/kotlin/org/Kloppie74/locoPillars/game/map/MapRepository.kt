package org.Kloppie74.locoPillars.game.map

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import org.Kloppie74.locoPillars.utils.deleteRecursivelyJava
import java.io.File

/**
 * MapRepository - Handles Folder-Based Map Storage
 * 
 * Architecture:
 * - Maps are stored in: plugins/LocoPillars/maps/<MapName>/
 * - Each map folder contains:
 *   - region.schem: The physical world data (WorldEdit schematic)
 *   - settings.yml: Metadata (SpawnPoints, BorderSize, SaveRadius, etc.)
 * 
 * The .yml acts as the database record; the .schem is the physical asset.
 */
class MapRepository(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager
) {
    
    private val mapsFolder: File get() = File(plugin.dataFolder, "maps")

    init {
        ensureFoldersExist()
    }

    private fun ensureFoldersExist() {
        if (!mapsFolder.exists()) {
            mapsFolder.mkdirs()
            debugLog("Created maps folder: ${mapsFolder.absolutePath}")
        }
    }

    /**
     * Create a new map with a minimal settings.yml.
     */
    fun createMap(mapName: String): Boolean {
        val mapFolder = File(mapsFolder, mapName)

        if (mapFolder.exists()) {
            plugin.logger.warning("Map folder already exists: $mapName")
            return false
        }

        mapFolder.mkdirs()
        debugLog("Created map folder: ${mapFolder.absolutePath}")

        val mapSettings = File(mapFolder, "settings.yml")
        val config = YamlConfiguration()
        config.set("MapName", mapName)
        config.set("DisplayName", mapName)
        config.set("SaveRadius", 50)
        config.set("SchematicRadius", 50)
        config.set("Settings.BuildHeight", 80)
        config.set("Settings.BuildRange", 50)
        config.set("Settings.DeathHeight", -10)
        config.set("Settings.LobbyTimer", 15)
        config.set("Settings.CageTimer", 5)
        config.set("Settings.GameTimer", 600)
        config.set("Settings.ItemTimer", 10)
        config.set("Settings.Can-Break-Map-Blocks", false)
        config.set("Settings.Can-BlowUp-Map-Blocks", false)
        config.set("Settings.SpawnPoint.X", 0.0)
        config.set("Settings.SpawnPoint.Y", 1.0)
        config.set("Settings.SpawnPoint.Z", 0.0)
        config.set("SpawnLocations.MinimumPlayers", 2)
        config.set("SpawnLocations.MaximumPlayers", 8)
        config.set("SpawnLocations.MaxTeams", 8)
        config.set("SpawnLocations.PlayersPerTeam", 1)
        try {
            config.save(mapSettings)
            debugLog("Created settings.yml for $mapName")
        } catch (e: Exception) {
            plugin.logger.severe("Failed to create settings.yml for $mapName: ${e.message}")
            return false
        }

        plugin.logger.info("Successfully created map: $mapName")
        return true
    }
    
    /**
     * Delete a map and all its files
     */
    fun deleteMap(mapName: String): Boolean {
        val mapFolder = File(mapsFolder, mapName)
        
        if (!mapFolder.exists()) {
            plugin.logger.warning("Map folder does not exist: $mapName")
            return false
        }
        
        return try {
            mapFolder.deleteRecursivelyJava()
            plugin.logger.info("Successfully deleted map: $mapName")
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to delete map $mapName: ${e.message}")
            false
        }
    }
    
    /**
     * Check if a map exists
     */
    fun mapExists(mapName: String): Boolean {
        return File(mapsFolder, mapName).exists()
    }
    
    /**
     * Get all available map names
     */
    fun getAllMapNames(): List<String> {
        return mapsFolder.listFiles { file -> file.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }
    
    /**
     * Get the settings file for a map
     */
    fun getMapSettingsFile(mapName: String): File {
        return File(mapsFolder, "$mapName/settings.yml")
    }
    
    /**
     * Get the primary schematic file for a map (legacy region.schem, used for single-schem saves).
     */
    fun getMapSchematicFile(mapName: String): File {
        return File(mapsFolder, "$mapName/region.schem")
    }

    /**
     * Get the schematics subfolder for a map (multi-schematic support).
     */
    fun getMapSchematicsFolder(mapName: String): File {
        return File(mapsFolder, "$mapName/schematics")
    }

    /**
     * Get a numbered schematic file inside the schematics/ subfolder.
     */
    fun getMapSchematicFile(mapName: String, id: Int): File {
        return File(getMapSchematicsFolder(mapName), "$id.schem")
    }

    /**
     * List all numbered schematics for a map (from the schematics/ subfolder).
     * Falls back to [region.schem] if no schematics/ folder exists.
     */
    fun getMapSchematicFiles(mapName: String): List<File> {
        val schemsFolder = getMapSchematicsFolder(mapName)
        if (schemsFolder.exists() && schemsFolder.isDirectory) {
            val files = schemsFolder.listFiles { f -> f.extension == "schem" && f.nameWithoutExtension.toIntOrNull() != null }
                ?: emptyArray()
            if (files.isNotEmpty()) return files.sortedBy { it.nameWithoutExtension.toIntOrNull() ?: 0 }
        }
        val legacy = getMapSchematicFile(mapName)
        return if (legacy.exists()) listOf(legacy) else emptyList()
    }
    
    /**
     * Load map settings
     */
    fun loadMapSettings(mapName: String): YamlConfiguration? {
        val settingsFile = getMapSettingsFile(mapName)
        
        if (!settingsFile.exists()) {
            plugin.logger.warning("Settings file not found for map: $mapName")
            return null
        }
        
        return try {
            YamlConfiguration.loadConfiguration(settingsFile)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to load settings for map $mapName: ${e.message}")
            null
        }
    }
    
    /**
     * Save map settings
     */
    fun saveMapSettings(mapName: String, config: YamlConfiguration): Boolean {
        val settingsFile = getMapSettingsFile(mapName)
        
        return try {
            config.save(settingsFile)
            debugLog("Saved settings for map: $mapName")
            true
        } catch (e: Exception) {
            plugin.logger.severe("Failed to save settings for map $mapName: ${e.message}")
            false
        }
    }
    
    /**
     * Get the save radius for a map (used for dynamic save)
     */
    fun getSaveRadius(mapName: String): Int {
        val config = loadMapSettings(mapName)
        return config?.getInt("SaveRadius")
            ?: config?.getInt("SchematicRadius", 50)
            ?: 50
    }
    
    /**
     * Update the save radius for a map
     */
    fun updateSaveRadius(mapName: String, radius: Int): Boolean {
        val config = loadMapSettings(mapName) ?: return false
        config.set("SaveRadius", radius)
        config.set("SchematicRadius", radius)
        return saveMapSettings(mapName, config)
    }

    fun getSaveHeight(mapName: String): Int {
        val config = loadMapSettings(mapName)
        return config?.getInt("SaveHeight", 200) ?: 200
    }

    fun updateSaveHeight(mapName: String, height: Int): Boolean {
        val config = loadMapSettings(mapName) ?: return false
        config.set("SaveHeight", height)
        return saveMapSettings(mapName, config)
    }

    fun getBorderSize(mapName: String): Double {
        val config = loadMapSettings(mapName)
        return config?.getDouble("Border.Size", 100.0) ?: 100.0
    }
    
    /**
     * Get map validation result
     */
    fun validateMap(mapName: String): MapValidationResult {
        val mapFolder = File(mapsFolder, mapName)
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        
        if (!mapFolder.exists() || !mapFolder.isDirectory) {
            errors.add("Map folder does not exist: $mapName")
            return MapValidationResult(false, errors, warnings)
        }
        
        val settingsFile = getMapSettingsFile(mapName)
        if (!settingsFile.exists()) {
            errors.add("settings.yml not found")
        } else {
            try {
                val config = YamlConfiguration.loadConfiguration(settingsFile)
                
                if (!config.contains("SpawnLocations")) {
                    warnings.add("No spawn locations defined")
                }
                
                if (!config.contains("SaveRadius") && !config.contains("SchematicRadius")) {
                    warnings.add("SaveRadius not defined (will default to 50)")
                }
                
            } catch (e: Exception) {
                errors.add("Invalid settings.yml: ${e.message}")
            }
        }
        
        val schemFile = getMapSchematicFile(mapName)
        if (!schemFile.exists()) {
            warnings.add("region.schem not found (map will be empty)")
        }
        
        val isValid = errors.isEmpty()
        return MapValidationResult(isValid, errors, warnings)
    }
    
    private fun debugLog(message: String) {
        if (configManager.debugMode) {
            plugin.logger.info("[MapRepository] $message")
        }
    }
}

data class MapValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)
