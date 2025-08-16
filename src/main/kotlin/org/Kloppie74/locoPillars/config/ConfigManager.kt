package org.Kloppie74.locoPillars.config

import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class ConfigManager(private val plugin: JavaPlugin) {
    private lateinit var config: FileConfiguration
    private val configFile: File = File(plugin.dataFolder, "config.yml")

    // Configuration values with defaults
    var debugMode: Boolean = false
        private set
    var instancesPerGameType: Int = 25
        private set
    var instanceDistance: Int = 1000
        private set
    var maxConcurrentSchemPastes: Int = 5
        private set
    var schemPasteTimeout: Long = 30000L
        private set
    var enableProgressBar: Boolean = true
        private set
    var autoSave: Boolean = true
        private set
    var autoSaveInterval: Long = 6000L // 5 minutes in ticks
        private set

    init {
        loadConfig()
        plugin.logger.info("[ConfigManager] Configuration loaded successfully")
        if (debugMode) {
            plugin.logger.info("[ConfigManager] Debug mode is ENABLED")
        }
    }

    private fun loadConfig() {
        // Create default config if it doesn't exist
        if (!configFile.exists()) {
            plugin.logger.info("[ConfigManager] Creating default config.yml")
            createDefaultConfig()
        }

        config = YamlConfiguration.loadConfiguration(configFile)

        // Load values with defaults
        debugMode = config.getBoolean("debug", false)
        instancesPerGameType = config.getInt("game.instances-per-type", 25)
        instanceDistance = config.getInt("game.instance-distance", 1000)
        maxConcurrentSchemPastes = config.getInt("performance.max-concurrent-schem-pastes", 5)
        schemPasteTimeout = config.getLong("performance.schem-paste-timeout", 30000L)
        enableProgressBar = config.getBoolean("ui.enable-progress-bar", true)
        autoSave = config.getBoolean("world.auto-save", true)
        autoSaveInterval = config.getLong("world.auto-save-interval", 6000L)

        // Validate values
        validateConfig()
    }

    private fun createDefaultConfig() {
        configFile.parentFile.mkdirs()
        config = YamlConfiguration()

        // Set default values
        config.set("debug", false)
        config.set("game.instances-per-type", 25)
        config.set("game.instance-distance", 1000)
        config.set("performance.max-concurrent-schem-pastes", 5)
        config.set("performance.schem-paste-timeout", 30000)
        config.set("ui.enable-progress-bar", true)
        config.set("world.auto-save", true)
        config.set("world.auto-save-interval", 6000)

        // Add comments
        config.setComments("debug", listOf(
            "Enable debug logging - shows detailed information about plugin operations",
            "Set to false for production to improve performance"
        ))

        config.setComments("game", listOf(
            "Game instance configuration"
        ))

        config.setComments("game.instances-per-type", listOf(
            "Number of game instances to create per game type",
            "Higher values = more concurrent games possible but more memory usage"
        ))

        config.setComments("game.instance-distance", listOf(
            "Distance between arena instances in blocks",
            "Should be larger than your biggest arena"
        ))

        config.setComments("performance", listOf(
            "Performance related settings"
        ))

        config.setComments("performance.max-concurrent-schem-pastes", listOf(
            "Maximum number of schematics that can be pasted simultaneously",
            "Lower values = slower startup but less server lag during initialization"
        ))

        config.setComments("performance.schem-paste-timeout", listOf(
            "Timeout in milliseconds for schematic pasting operations"
        ))

        config.setComments("ui", listOf(
            "User interface settings"
        ))

        config.setComments("ui.enable-progress-bar", listOf(
            "Show progress bar during arena generation instead of detailed logs"
        ))

        config.setComments("world", listOf(
            "World management settings"
        ))

        config.setComments("world.auto-save", listOf(
            "Automatically save arena worlds periodically"
        ))

        config.setComments("world.auto-save-interval", listOf(
            "Auto-save interval in ticks (20 ticks = 1 second)"
        ))

        try {
            config.save(configFile)
            plugin.logger.info("[ConfigManager] Default config.yml created")
        } catch (e: Exception) {
            plugin.logger.severe("[ConfigManager] Failed to create default config: ${e.message}")
        }
    }

    private fun validateConfig() {
        var needsSave = false

        // Validate and clamp values
        if (instancesPerGameType < 1 || instancesPerGameType > 100) {
            plugin.logger.warning("[ConfigManager] Invalid instances-per-type: $instancesPerGameType, clamping to 1-100")
            instancesPerGameType = instancesPerGameType.coerceIn(1, 100)
            config.set("game.instances-per-type", instancesPerGameType)
            needsSave = true
        }

        if (instanceDistance < 100 || instanceDistance > 10000) {
            plugin.logger.warning("[ConfigManager] Invalid instance-distance: $instanceDistance, clamping to 100-10000")
            instanceDistance = instanceDistance.coerceIn(100, 10000)
            config.set("game.instance-distance", instanceDistance)
            needsSave = true
        }

        if (maxConcurrentSchemPastes < 1 || maxConcurrentSchemPastes > 20) {
            plugin.logger.warning("[ConfigManager] Invalid max-concurrent-schem-pastes: $maxConcurrentSchemPastes, clamping to 1-20")
            maxConcurrentSchemPastes = maxConcurrentSchemPastes.coerceIn(1, 20)
            config.set("performance.max-concurrent-schem-pastes", maxConcurrentSchemPastes)
            needsSave = true
        }

        if (schemPasteTimeout < 5000L || schemPasteTimeout > 300000L) {
            plugin.logger.warning("[ConfigManager] Invalid schem-paste-timeout: $schemPasteTimeout, clamping to 5000-300000")
            schemPasteTimeout = schemPasteTimeout.coerceIn(5000L, 300000L)
            config.set("performance.schem-paste-timeout", schemPasteTimeout)
            needsSave = true
        }

        if (autoSaveInterval < 1200L || autoSaveInterval > 72000L) { // 1 minute to 1 hour
            plugin.logger.warning("[ConfigManager] Invalid auto-save-interval: $autoSaveInterval, clamping to 1200-72000")
            autoSaveInterval = autoSaveInterval.coerceIn(1200L, 72000L)
            config.set("world.auto-save-interval", autoSaveInterval)
            needsSave = true
        }

        if (needsSave) {
            saveConfig()
        }
    }

    fun reloadConfig() {
        plugin.logger.info("[ConfigManager] Reloading configuration...")
        val oldDebugMode = debugMode

        loadConfig()

        if (debugMode != oldDebugMode) {
            plugin.logger.info("[ConfigManager] Debug mode changed: $oldDebugMode -> $debugMode")
        }

        plugin.logger.info("[ConfigManager] Configuration reloaded successfully")
    }

    fun saveConfig() {
        try {
            config.save(configFile)
            if (debugMode) {
                plugin.logger.info("[ConfigManager] Configuration saved to file")
            }
        } catch (e: Exception) {
            plugin.logger.severe("[ConfigManager] Failed to save configuration: ${e.message}")
        }
    }

    fun setDebugMode(enabled: Boolean) {
        if (debugMode != enabled) {
            debugMode = enabled
            config.set("debug", enabled)
            saveConfig()
            plugin.logger.info("[ConfigManager] Debug mode set to: $enabled")
        }
    }

    // Convenience methods for logging
    fun debugLog(message: String) {
        if (debugMode) {
            plugin.logger.info("[DEBUG] $message")
        }
    }

    fun debugLog(tag: String, message: String) {
        if (debugMode) {
            plugin.logger.info("[DEBUG] [$tag] $message")
        }
    }

    // Get config values
    fun getConfig(): FileConfiguration = config

    fun getConfigFile(): File = configFile

    // Display current configuration
    fun logCurrentConfig() {
        plugin.logger.info("[ConfigManager] Current configuration:")
        plugin.logger.info("[ConfigManager]   Debug mode: $debugMode")
        plugin.logger.info("[ConfigManager]   Instances per game type: $instancesPerGameType")
        plugin.logger.info("[ConfigManager]   Instance distance: $instanceDistance")
        plugin.logger.info("[ConfigManager]   Max concurrent schem pastes: $maxConcurrentSchemPastes")
        plugin.logger.info("[ConfigManager]   Schem paste timeout: ${schemPasteTimeout}ms")
        plugin.logger.info("[ConfigManager]   Progress bar enabled: $enableProgressBar")
        plugin.logger.info("[ConfigManager]   Auto-save: $autoSave")
        plugin.logger.info("[ConfigManager]   Auto-save interval: ${autoSaveInterval} ticks")
    }
}