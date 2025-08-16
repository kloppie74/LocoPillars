package org.Kloppie74.locoPillars

import org.bukkit.plugin.java.JavaPlugin
import org.Kloppie74.locoPillars.game.config.GameConfig
import org.Kloppie74.locoPillars.game.manager.GameManager
import org.Kloppie74.locoPillars.game.arena.ArenaManager
import org.Kloppie74.locoPillars.game.arena.ArenaWorldManager
import org.Kloppie74.locoPillars.game.template.TemplateManager
import org.Kloppie74.locoPillars.commands.PillarsCommand
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.manager.SpawnAreaManager
import org.Kloppie74.locoPillars.listeners.GameListener
import org.Kloppie74.locoPillars.config.ConfigManager
import java.io.File

class LocoPillars : JavaPlugin() {
    lateinit var configManager: ConfigManager
        private set
    lateinit var gameManager: GameManager
        private set
    lateinit var arenaManager: ArenaManager
        private set
    lateinit var worldManager: ArenaWorldManager
        private set
    lateinit var templateManager: TemplateManager
        private set
    lateinit var spawnAreaManager: SpawnAreaManager
        private set
    lateinit var configs: List<GameConfig>
        private set

    override fun onEnable() {
        logger.info("[LocoPillars] ==========================================")
        logger.info("[LocoPillars] PILLARS OF FORTUNE - Starting Up...")
        logger.info("[LocoPillars] ==========================================")
        logger.info("[LocoPillars] Version: ${description.version}")
        logger.info("[LocoPillars] Authors: ${description.authors}")

        // FIXED: Initialize ConfigManager FIRST, before anything else
        try {
            logger.info("[LocoPillars] Initializing ConfigManager...")
            configManager = ConfigManager(this)
            logger.info("[LocoPillars] ConfigManager initialized successfully")

            if (configManager.debugMode) {
                logger.info("[LocoPillars] DEBUG MODE ENABLED")
                configManager.logCurrentConfig()
            }
        } catch (e: Exception) {
            logger.severe("[LocoPillars] CRITICAL: Failed to initialize ConfigManager: ${e.message}")
            server.pluginManager.disablePlugin(this)
            return
        }

        // Now check dependencies (which uses debugLog that needs configManager)
        if (!checkDependencies()) {
            logger.severe("[LocoPillars] Missing required dependencies! Disabling plugin.")
            server.pluginManager.disablePlugin(this)
            return
        }

        try {
            // Initialize folder structure
            createFolderStructure()

            // Initialize template manager
            debugLog("Initializing TemplateManager...")
            templateManager = TemplateManager(this)
            debugLog("TemplateManager initialized successfully")

            // Create default templates if none exist
            if (templateManager.listAvailableTemplates().isEmpty()) {
                logger.info("[LocoPillars] No templates found, creating default templates...")
                templateManager.createDefaultTemplates()
            }

            // Initialize managers in correct order
            debugLog("Initializing ArenaWorldManager...")
            worldManager = ArenaWorldManager(this)
            debugLog("ArenaWorldManager initialized successfully")

            debugLog("Initializing ArenaManager...")
            arenaManager = ArenaManager(this, worldManager)
            debugLog("ArenaManager initialized successfully")

            debugLog("Initializing GameManager...")
            gameManager = GameManager(this)
            gameManager.initialize()
            debugLog("GameManager initialized successfully")

            debugLog("Initializing SpawnAreaManager...")
            spawnAreaManager = SpawnAreaManager(this)
            debugLog("SpawnAreaManager initialized successfully")

            // Load configurations
            logger.info("[LocoPillars] Loading game configurations...")
            configs = GameConfig.loadAll(this)

            if (configs.isEmpty()) {
                showNoConfigsWarning()
            } else {
                logger.info("[LocoPillars] Successfully loaded ${configs.size} game configurations")

                if (configManager.debugMode) {
                    configs.forEach { config ->
                        logger.info("[LocoPillars]   - ${config.name}: ${config.getDisplayName()}")
                        logger.info("[LocoPillars]     Players: ${config.getMinPlayers()}-${config.getMaxPlayers()}")
                        logger.info("[LocoPillars]     Teams: ${config.getMaxTeams()} (${config.getPlayersPerTeam()} per team)")
                    }
                }

                // Preload arena instances
                logger.info("[LocoPillars] Starting arena generation process...")
                arenaManager.preloadInstances(configs, configManager.instancesPerGameType)

                // Initialize game instances
                logger.info("[LocoPillars] Initializing game instances...")
                gameManager.preloadInstances(arenaManager)
            }

            // Register commands and listeners
            debugLog("Registering commands and listeners...")
            registerCommands()
            registerListeners()

            // Log final statistics
            showStartupSummary()

        } catch (e: Exception) {
            logger.severe("[LocoPillars] ==========================================")
            logger.severe("[LocoPillars] PLUGIN INITIALIZATION FAILED!")
            logger.severe("[LocoPillars] ==========================================")
            logger.severe("[LocoPillars] Failed to enable plugin: ${e.message}")
            logger.severe("[LocoPillars] Exception type: ${e.javaClass.name}")
            e.printStackTrace()

            server.pluginManager.disablePlugin(this)
        }
    }

    override fun onDisable() {
        logger.info("[LocoPillars] ==========================================")
        logger.info("[LocoPillars] PILLARS OF FORTUNE - Shutting Down...")
        logger.info("[LocoPillars] ==========================================")

        try {
            var cleanupErrors = 0

            if (::gameManager.isInitialized) {
                val activeGames = gameManager.getActiveGames().size
                val totalPlayers = gameManager.getAllInstances().sumOf { it.players.size }

                logger.info("[LocoPillars] Ending $activeGames active games with $totalPlayers total players...")

                gameManager.getAllInstances().forEach { instance ->
                    try {
                        if (instance.players.isNotEmpty()) {
                            instance.players.toList().forEach { playerId ->
                                val player = server.getPlayer(playerId)
                                if (player != null && player.isOnline) {
                                    gameManager.leaveGame(player)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        cleanupErrors++
                        logger.severe("[LocoPillars] Error cleaning up game instance ${instance.id}: ${e.message}")
                    }
                }

                gameManager.cleanup()
                logger.info("[LocoPillars] ✓ Game cleanup completed with $cleanupErrors errors")
            }

            if (::worldManager.isInitialized) {
                val worldCount = worldManager.getAllArenaWorlds().size
                logger.info("[LocoPillars] Cleaning up $worldCount arena worlds...")
                worldManager.cleanup()
                logger.info("[LocoPillars] ✓ World cleanup completed")
            }

            if (::arenaManager.isInitialized) {
                arenaManager.onDisable()
            }

            logger.info("[LocoPillars] ✓ Cleanup completed with $cleanupErrors total errors")

        } catch (e: Exception) {
            logger.severe("[LocoPillars] ✗ Error during plugin disable: ${e.message}")
            e.printStackTrace()
        }

        logger.info("[LocoPillars] ==========================================")
        logger.info("[LocoPillars] PLUGIN DISABLED SUCCESSFULLY!")
        logger.info("[LocoPillars] ==========================================")
    }

    private fun createFolderStructure() {
        debugLog("Creating plugin folder structure...")

        val foldersToCreate = listOf(
            File(dataFolder, "Games"),
            File(dataFolder, "Schems"),
            File(dataFolder, "Data"),
            File(dataFolder, "Data/GameWorlds")
        )

        foldersToCreate.forEach { folder ->
            if (!folder.exists()) {
                folder.mkdirs()
                debugLog("✓ Created folder: ${folder.absolutePath}")
            } else {
                debugLog("✓ Folder exists: ${folder.absolutePath}")
            }
        }

        debugLog("Folder structure ready")
    }

    private fun checkDependencies(): Boolean {
        debugLog("Checking dependencies...")

        val requiredPlugins = listOf("WorldEdit", "WorldGuard")
        val missingPlugins = mutableListOf<String>()
        val foundPlugins = mutableListOf<String>()

        requiredPlugins.forEach { pluginName ->
            val plugin = server.pluginManager.getPlugin(pluginName)
            if (plugin != null && plugin.isEnabled) {
                foundPlugins.add("$pluginName v${plugin.description.version}")
                debugLog("✓ Found $pluginName v${plugin.description.version}")
            } else {
                missingPlugins.add(pluginName)
                logger.severe("[LocoPillars] ✗ Missing or disabled: $pluginName")
            }
        }

        if (missingPlugins.isNotEmpty()) {
            logger.severe("[LocoPillars] ==========================================")
            logger.severe("[LocoPillars] MISSING REQUIRED DEPENDENCIES!")
            logger.severe("[LocoPillars] ==========================================")
            logger.severe("[LocoPillars] Missing: ${missingPlugins.joinToString(", ")}")
            logger.severe("[LocoPillars] Download links:")
            logger.severe("[LocoPillars]   - WorldEdit: https://dev.bukkit.org/projects/worldedit")
            logger.severe("[LocoPillars]   - WorldGuard: https://dev.bukkit.org/projects/worldguard")
            logger.severe("[LocoPillars] ==========================================")
            return false
        }

        logger.info("[LocoPillars] ✓ All dependencies found: ${foundPlugins.joinToString(", ")}")
        return true
    }

    private fun registerCommands() {
        debugLog("Registering commands...")

        val pillarsCommand = getCommand("pillars")
        if (pillarsCommand != null) {
            pillarsCommand.setExecutor(PillarsCommand(this))
            debugLog("✓ Command '/pillars' registered")
            if (configManager.debugMode) {
                debugLog("Command aliases: ${pillarsCommand.aliases.joinToString(", ")}")
            }
        } else {
            logger.severe("[LocoPillars] ✗ Failed to register '/pillars' command")
        }
    }

    private fun registerListeners() {
        debugLog("Registering event listeners...")
        val gameListener = GameListener(this)
        server.pluginManager.registerEvents(gameListener, this)
        debugLog("✓ GameListener registered")
    }

    private fun showNoConfigsWarning() {
        logger.warning("[LocoPillars] ==========================================")
        logger.warning("[LocoPillars] NO GAME CONFIGURATIONS FOUND!")
        logger.warning("[LocoPillars] ==========================================")
        logger.warning("[LocoPillars] Please ensure:")
        logger.warning("[LocoPillars] 1. Config files (.yml) are in: ${dataFolder.absolutePath}/Games/")
        logger.warning("[LocoPillars] 2. Schematic files (.schem) are in: ${dataFolder.absolutePath}/Schems/")
        logger.warning("[LocoPillars] 3. File names match (without extensions)")
        logger.warning("[LocoPillars] 4. Both files exist for each game type")
        logger.warning("[LocoPillars]")
        logger.warning("[LocoPillars] The plugin will continue running but no games will be available")
        logger.warning("[LocoPillars] Default template examples have been created in the Games folder")
        logger.warning("[LocoPillars] ==========================================")

        if (configManager.debugMode) {
            val availableTemplates = templateManager.listAvailableTemplates()
            logger.info("[LocoPillars] Available templates: ${availableTemplates.size}")
            availableTemplates.forEach { templateName ->
                val info = templateManager.getTemplateInfo(templateName)
                logger.info("[LocoPillars] Template: $templateName")
                logger.info("[LocoPillars]   Display Name: ${info?.displayName ?: "N/A"}")
                logger.info("[LocoPillars]   Has Schematic: ${info?.hasSchematic ?: false}")

                val validation = templateManager.validateTemplate(templateName)
                logger.info("[LocoPillars]   Valid: ${validation.isValid}")
                if (!validation.isValid) {
                    validation.errors.forEach { error ->
                        logger.warning("[LocoPillars]     Error: $error")
                    }
                }
            }
        }
    }

    private fun showStartupSummary() {
        logger.info("[LocoPillars] ==========================================")
        logger.info("[LocoPillars] INITIALIZATION COMPLETE!")
        logger.info("[LocoPillars] ==========================================")
        logger.info("[LocoPillars] Successfully initialized:")
        logger.info("[LocoPillars]   - Game types: ${configs.size}")

        if (::arenaManager.isInitialized) {
            logger.info("[LocoPillars]   - Arena instances: ${arenaManager.getAllInstances().size}")
            logger.info("[LocoPillars]   - Arena templates: ${arenaManager.getAllTemplates().size}")
        }

        if (::gameManager.isInitialized) {
            logger.info("[LocoPillars]   - Game instances: ${gameManager.getAllInstances().size}")
            logger.info("[LocoPillars]   - Available games: ${gameManager.getAvailableInstances().size}")
        }

        if (::worldManager.isInitialized) {
            logger.info("[LocoPillars]   - Arena worlds: ${worldManager.getAllArenaWorlds().size}")
        }

        if (configManager.debugMode) {
            logger.info("[LocoPillars] Folder structure:")
            logger.info("[LocoPillars]   - Plugin folder: ${dataFolder.absolutePath}")
            logger.info("[LocoPillars]   - Games folder: ${dataFolder.resolve("Games").absolutePath}")
            logger.info("[LocoPillars]   - Schems folder: ${dataFolder.resolve("Schems").absolutePath}")
            logger.info("[LocoPillars]   - Data folder: ${dataFolder.resolve("Data").absolutePath}")
            logger.info("[LocoPillars]   - GameWorlds folder: ${dataFolder.resolve("Data/GameWorlds").absolutePath}")

            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            val totalMemory = runtime.totalMemory() / 1024 / 1024
            logger.info("[LocoPillars] Memory usage: ${usedMemory}MB / ${totalMemory}MB")
        }

        logger.info("[LocoPillars] Plugin enabled successfully!")
        logger.info("[LocoPillars] ==========================================")
    }

    // FIXED: Safe debugLog method that checks if configManager is initialized
    private fun debugLog(message: String) {
        // Use ::configManager.isInitialized to safely check if it's been initialized
        if (::configManager.isInitialized && configManager.debugMode) {
            logger.info("[LocoPillars] $message")
        }
        // If configManager isn't initialized yet, we simply don't log debug messages
        // This prevents the UninitializedPropertyAccessException
    }

    // Utility methods for debugging
    fun logMemoryUsage(context: String) {
        if (::configManager.isInitialized && configManager.debugMode) {
            val runtime = Runtime.getRuntime()
            val maxMemory = runtime.maxMemory() / 1024 / 1024
            val totalMemory = runtime.totalMemory() / 1024 / 1024
            val freeMemory = runtime.freeMemory() / 1024 / 1024
            val usedMemory = totalMemory - freeMemory

            logger.info("[LocoPillars] Memory usage ($context):")
            logger.info("[LocoPillars]   Used: ${usedMemory}MB")
            logger.info("[LocoPillars]   Total: ${totalMemory}MB")
            logger.info("[LocoPillars]   Max: ${maxMemory}MB")
        }
    }

    fun logSystemInfo() {
        if (::configManager.isInitialized && configManager.debugMode) {
            logger.info("[LocoPillars] System information:")
            logger.info("[LocoPillars]   OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
            logger.info("[LocoPillars]   Java: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
            logger.info("[LocoPillars]   Available processors: ${Runtime.getRuntime().availableProcessors()}")
            logMemoryUsage("System Info")
        }
    }
}