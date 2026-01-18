package org.Kloppie74.locoPillars.game.map

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import java.io.File

/**
 * MapIOManager - Handles Folder-Based Map Storage
 * 
 * Architecture:
 * - Maps are stored in: plugins/LocoPillars/maps/<MapName>/
 * - Each map folder contains:
 *   - region.schem: The physical world data (WorldEdit schematic)
 *   - settings.yml: Metadata (SpawnPoints, BorderSize, MaxPlayers, SchematicRadius, etc.)
 * 
 * The .yml acts as the database record; the .schem is the physical asset.
 */
class MapIOManager(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager
) {
    
    private val mapsFolder: File get() = File(plugin.dataFolder, "maps")
    private val templatesFolder: File get() = File(plugin.dataFolder, "templates")
    
    init {
        ensureFoldersExist()
        createDefaultTemplates()
    }
    
    private fun ensureFoldersExist() {
        if (!mapsFolder.exists()) {
            mapsFolder.mkdirs()
            debugLog("Created maps folder: ${mapsFolder.absolutePath}")
        }
        if (!templatesFolder.exists()) {
            templatesFolder.mkdirs()
            debugLog("Created templates folder: ${templatesFolder.absolutePath}")
        }
    }
    
    private fun createDefaultTemplates() {
        val defaultSettingsFile = File(templatesFolder, "default_settings.yml")
        val defaultSchemReadme = File(templatesFolder, "default_map.schem.readme")
        
        if (!defaultSettingsFile.exists()) {
            createDefaultSettingsTemplate(defaultSettingsFile)
            debugLog("Created default_settings.yml template")
        }
        
        if (!defaultSchemReadme.exists()) {
            defaultSchemReadme.writeText("""
                # Default Map Schematic
                
                This is a placeholder for the default_map.schem file.
                
                To create a default template schematic:
                1. Build a basic arena in a test world
                2. Use WorldEdit: //copy
                3. Save: //schem save default_map
                4. Place the default_map.schem file in this templates folder
                
                When creating new maps, this schematic will be copied automatically.
                If this file doesn't exist, maps will start as empty void worlds.
            """.trimIndent())
            debugLog("Created default schematic readme")
        }
    }
    
    private fun createDefaultSettingsTemplate(file: File) {
        val config = YamlConfiguration()
        
        // Map Metadata
        config.set("MapName", "NewMap")
        config.set("DisplayName", "New Map")
        config.set("SchematicRadius", 50) // Critical for dynamic save
        
        // Game Settings
        config.set("Settings.BuildHeight", 80)
        config.set("Settings.BuildRange", 50)
        config.set("Settings.DeathHeight", -10)
        config.set("Settings.LobbyTimer", 15)
        config.set("Settings.CageTimer", 5)
        config.set("Settings.GameTimer", 600)
        config.set("Settings.ItemTimer", 10)
        config.set("Settings.Can-Break-Map-Blocks", false)
        
        // Spawn Point
        config.set("Settings.SpawnPoint.X", 0.5)
        config.set("Settings.SpawnPoint.Y", 65.0)
        config.set("Settings.SpawnPoint.Z", 0.5)
        
        // Player Configuration
        config.set("SpawnLocations.MinimumPlayers", 4)
        config.set("SpawnLocations.MaximumPlayers", 8)
        config.set("SpawnLocations.MaxTeams", 8)
        config.set("SpawnLocations.PlayersPerTeam", 1)
        
        // Default Teams (4 basic teams)
        createDefaultTeam(config, "Team1", "&c[Red]", "RED", -10.5, 65.0, -10.5, 45.0f)
        createDefaultTeam(config, "Team2", "&9[Blue]", "BLUE", 10.5, 65.0, -10.5, 135.0f)
        createDefaultTeam(config, "Team3", "&a[Green]", "GREEN", 10.5, 65.0, 10.5, -135.0f)
        createDefaultTeam(config, "Team4", "&e[Yellow]", "YELLOW", -10.5, 65.0, 10.5, -45.0f)
        
        // Border Configuration
        config.set("Border.Enabled", true)
        config.set("Border.Size", 100.0)
        config.set("Border.DamageAmount", 1.0)
        config.set("Border.DamageBuffer", 5.0)
        
        try {
            config.save(file)
        } catch (e: Exception) {
            plugin.logger.severe("Failed to create default settings template: ${e.message}")
        }
    }
    
    private fun createDefaultTeam(
        config: YamlConfiguration,
        teamKey: String,
        teamName: String,
        color: String,
        x: Double,
        y: Double,
        z: Double,
        yaw: Float
    ) {
        config.set("SpawnLocations.$teamKey.Name", teamName)
        config.set("SpawnLocations.$teamKey.Color", color)
        config.set("SpawnLocations.$teamKey.Player1.X", x)
        config.set("SpawnLocations.$teamKey.Player1.Y", y)
        config.set("SpawnLocations.$teamKey.Player1.Z", z)
        config.set("SpawnLocations.$teamKey.Player1.Yaw", yaw)
        config.set("SpawnLocations.$teamKey.Player1.Pitch", 0.0f)
        config.set("SpawnLocations.$teamKey.Player1.Cage-Rotation", 0)
    }
    
    /**
     * Create a new map by copying templates
     */
    fun createMap(mapName: String): Boolean {
        val mapFolder = File(mapsFolder, mapName)
        
        if (mapFolder.exists()) {
            plugin.logger.warning("Map folder already exists: $mapName")
            return false
        }
        
        mapFolder.mkdirs()
        debugLog("Created map folder: ${mapFolder.absolutePath}")
        
        // Copy default settings
        val templateSettings = File(templatesFolder, "default_settings.yml")
        val mapSettings = File(mapFolder, "settings.yml")
        
        if (templateSettings.exists()) {
            templateSettings.copyTo(mapSettings, overwrite = true)
            
            // Update map name in settings
            val config = YamlConfiguration.loadConfiguration(mapSettings)
            config.set("MapName", mapName)
            config.set("DisplayName", mapName)
            config.save(mapSettings)
            
            debugLog("Copied and updated settings.yml for $mapName")
        } else {
            // Create basic settings if template doesn't exist
            createDefaultSettingsTemplate(mapSettings)
            val config = YamlConfiguration.loadConfiguration(mapSettings)
            config.set("MapName", mapName)
            config.set("DisplayName", mapName)
            config.save(mapSettings)
            debugLog("Created new settings.yml for $mapName")
        }
        
        // Copy default schematic if it exists
        val templateSchem = File(templatesFolder, "default_map.schem")
        val mapSchem = File(mapFolder, "region.schem")
        
        if (templateSchem.exists()) {
            templateSchem.copyTo(mapSchem, overwrite = true)
            debugLog("Copied default_map.schem to $mapName")
        } else {
            debugLog("No default schematic found - map will start as void world")
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
            mapFolder.deleteRecursively()
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
     * Get the schematic file for a map
     */
    fun getMapSchematicFile(mapName: String): File {
        return File(mapsFolder, "$mapName/region.schem")
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
     * Get the schematic radius for a map (used for dynamic save)
     */
    fun getSchematicRadius(mapName: String): Int {
        val config = loadMapSettings(mapName)
        return config?.getInt("SchematicRadius", 50) ?: 50
    }
    
    /**
     * Update the schematic radius for a map
     */
    fun updateSchematicRadius(mapName: String, radius: Int): Boolean {
        val config = loadMapSettings(mapName) ?: return false
        config.set("SchematicRadius", radius)
        return saveMapSettings(mapName, config)
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
                
                if (!config.contains("SchematicRadius")) {
                    warnings.add("SchematicRadius not defined (will default to 50)")
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
            plugin.logger.info("[MapIOManager] $message")
        }
    }
}

data class MapValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)
