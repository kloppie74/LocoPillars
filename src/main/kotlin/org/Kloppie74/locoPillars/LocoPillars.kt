package org.Kloppie74.locoPillars

import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import org.Kloppie74.locoPillars.commands.MemoryCommand
import org.Kloppie74.locoPillars.commands.editor.AdminEditorCommand
import org.Kloppie74.locoPillars.editor.AdminEditorManager
import org.Kloppie74.locoPillars.editor.EditorFileValidator
import org.Kloppie74.locoPillars.editor.EditorHotbarManager
import org.Kloppie74.locoPillars.editor.EditorSettingsGui
import org.Kloppie74.locoPillars.editor.GameFileService
import org.Kloppie74.locoPillars.editor.SpawnPointEditor
import org.Kloppie74.locoPillars.editor.listener.EditorInteractionListener
import org.Kloppie74.locoPillars.editor.listener.EditorModeListener
import org.bukkit.plugin.java.JavaPlugin
import org.Kloppie74.locoPillars.game.config.GameConfig
import org.Kloppie74.locoPillars.game.manager.GameManager
import org.Kloppie74.locoPillars.game.arena.ArenaManager
import org.Kloppie74.locoPillars.game.arena.ArenaWorldManager
import org.Kloppie74.locoPillars.game.template.TemplateManager
import org.Kloppie74.locoPillars.game.map.MapRepository
import org.Kloppie74.locoPillars.game.map.EditorSessionManager
import org.Kloppie74.locoPillars.commands.PillarsCommand
import org.Kloppie74.locoPillars.commands.StatsCommand
import org.Kloppie74.locoPillars.game.manager.SpawnAreaManager
import org.Kloppie74.locoPillars.game.listeners.GameListener
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import org.Kloppie74.locoPillars.database.DatabaseManager
import org.Kloppie74.locoPillars.events.listeners.PlayerJoinListener
import org.Kloppie74.locoPillars.utils.luckyblocks.LuckyBlockEngineIntegration
import org.Kloppie74.locoPillars.utils.luckyblocks.LuckyBlocksListener
import org.Kloppie74.locoPillars.utils.luckyblocks.LuckyBlocksMenu
import org.Kloppie74.locoPillars.utils.luckyblocks.LuckyBlocksUtil
import org.Kloppie74.locoPillars.utils.Placeholders
import org.Kloppie74.locoPillars.utils.achievements.AchievementListener
import org.Kloppie74.locoPillars.utils.achievements.AchievementLoader
import org.Kloppie74.locoPillars.utils.achievements.AchievementManager
import org.Kloppie74.locoPillars.utils.achievements.AchievementsCommand
import org.Kloppie74.locoPillars.utils.chat.ChatManager
import org.Kloppie74.locoPillars.utils.hotbar.HotbarItemManager
import org.Kloppie74.locoPillars.utils.hotbar.listeners.ItemHotBarListener
import org.Kloppie74.locoPillars.utils.hotbar.listeners.SpeedGuiListener
import org.Kloppie74.locoPillars.utils.spectator.listeners.SpectateListener
import org.Kloppie74.locoPillars.utils.spectator.SpectatorInteractionHandler
import org.Kloppie74.locoPillars.utils.spectator.SpectatorInventoryManager
import org.Kloppie74.locoPillars.utils.teams.TabCommand
import org.Kloppie74.locoPillars.utils.teams.tab.TabVisibilityManager
import org.Kloppie74.locoPillars.utils.teams.TeamManager
import org.Kloppie74.locoPillars.utils.teams.TeamsListener
import org.Kloppie74.locoPillars.utils.teams.scoreboard.ScoreboardManager
import org.Kloppie74.locoPillars.utils.teams.tab.TabManager
import org.Kloppie74.locoPillars.utils.vanish.*
import org.Kloppie74.locoPillars.utils.vanish.commands.HideMeCommand
import org.Kloppie74.locoPillars.utils.vanish.commands.HideOthersCommand
import org.Kloppie74.locoPillars.utils.vanish.commands.ShowOthersCommand
import org.Kloppie74.locoPillars.utils.vanish.commands.VanishCommand
import org.Kloppie74.locoPillars.utils.teams.GlobalVisibilityTicker
import org.Kloppie74.locoPillars.utils.teams.tab.ProtocolLibTabVisibilityManager
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.util.*

class LocoPillars : JavaPlugin() {
    companion object {
        lateinit var instance: LocoPillars
            private set
    }

    val playerIdCache = mutableMapOf<UUID, String>()

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
    lateinit var gameListener: GameListener
    
    // Map Editor System (core: session management and file storage)
    lateinit var mapRepository: MapRepository
        private set
    lateinit var editorSessionManager: EditorSessionManager
        private set

    // Admin Editor System
    lateinit var editorHotbarManager: EditorHotbarManager
        private set
    lateinit var adminEditorManager: AdminEditorManager
        private set
    lateinit var editorSettingsGui: EditorSettingsGui
        private set
    lateinit var spawnPointEditor: SpawnPointEditor
        private set
    lateinit var gameFileService: GameFileService
        private set
    lateinit var editorFileValidator: EditorFileValidator
        private set

    lateinit var hotbarItemManager: HotbarItemManager
    lateinit var itemHotBarListener: ItemHotBarListener
    lateinit var spectateListener: SpectateListener
    lateinit var spectatorInventoryManager: SpectatorInventoryManager
        private set
    lateinit var spectatorInteractionHandler: SpectatorInteractionHandler
        private set
    lateinit var achievementManager: AchievementManager
        private set
    lateinit var achievementListener: AchievementListener
        private set

    lateinit var chatManager: ChatManager
        private set
    lateinit var placeholders: Placeholders
        private set

    // ProtocolLib vars
    lateinit var protocolLibTabVisibilityManager: ProtocolLibTabVisibilityManager
        private set
    private var protocolManager: ProtocolManager? = null
    private var protocolLibAvailable = false

    // Team/Tab/Scoreboard
    lateinit var teamManager: TeamManager
    lateinit var tabManager: TabManager
    lateinit var scoreboardManager: ScoreboardManager
    lateinit var tabVisibilityManager: TabVisibilityManager
    lateinit var teamsListener: TeamsListener

    override fun onEnable() {
        instance = this

        logger.info("[LocoPillars] ==========================================")
        logger.info("[LocoPillars] PILLARS OF FORTUNE - Starting Up...")
        logger.info("[LocoPillars] ==========================================")
        logger.info("[LocoPillars] Version: ${description.version}")
        logger.info("[LocoPillars] Authors: ${description.authors}")

        try {
            // 1. CONFIG & DEPENDENCIES
            logger.info("[LocoPillars] Initializing ConfigManager...")
            configManager = ConfigManager(this)
            logger.info("[LocoPillars] ConfigManager initialized successfully")

            if (configManager.debugMode) {
                logger.info("[LocoPillars] DEBUG MODE ENABLED")
                configManager.logCurrentConfig()
            }

            if (!checkDependencies()) {
                logger.severe("[LocoPillars] Missing required dependencies! Disabling plugin.")
                server.pluginManager.disablePlugin(this)
                return
            }

            // 2. FOLDER STRUCTURE
            createFolderStructure()

            // 3. TEMPLATE & ARENA SYSTEM
            templateManager = TemplateManager(this, configManager)
            if (templateManager.listAvailableTemplates().isEmpty()) {
                logger.info("[LocoPillars] No templates found, creating default templates...")
                templateManager.createDefaultTemplates()
            }

            // 3.5 MAP EDITOR & ADMIN EDITOR SYSTEM
            logger.info("[LocoPillars] Initializing Map Editor & Admin Editor System...")
            mapRepository       = MapRepository(this, configManager)
            editorSessionManager = EditorSessionManager(this, mapRepository)

            editorHotbarManager  = EditorHotbarManager()
            editorSettingsGui    = EditorSettingsGui(this, editorSessionManager)
            adminEditorManager   = AdminEditorManager(this, editorHotbarManager)
            spawnPointEditor     = SpawnPointEditor(this, mapRepository)
            gameFileService      = GameFileService(this, configManager, mapRepository)
            editorFileValidator  = EditorFileValidator(this, configManager, mapRepository)
            val quickSetup = gameFileService.runQuickSetup()
            logger.info("[LocoPillars] Quick setup: folders=${quickSetup.foldersCreated}, bundled=${quickSetup.bundledConfigsCopied}, legacyCfg=${quickSetup.legacyConfigsMoved}, legacySchem=${quickSetup.legacySchemsMoved}, mirrored=${quickSetup.mapTemplatesMirrored}")
            if (quickSetup.warnings.isNotEmpty()) {
                quickSetup.warnings.forEach { logger.warning("[LocoPillars] Quick setup warning: $it") }
            }

            server.pluginManager.registerEvents(editorSettingsGui, this)
            server.pluginManager.registerEvents(
                EditorModeListener(adminEditorManager, editorSessionManager), this)
            server.pluginManager.registerEvents(
                EditorInteractionListener(
                    this, adminEditorManager, editorHotbarManager,
                    spawnPointEditor, editorFileValidator, editorSessionManager, editorSettingsGui
                ), this
            )
            logger.info("[LocoPillars] ✓ Map Editor & Admin Editor System initialized")

            worldManager = ArenaWorldManager(this, configManager)
            arenaManager = ArenaManager(this, worldManager, configManager)

            // 4. GAME MANAGER & INSTANCES
            gameManager = GameManager(this)
            gameManager.initialize()

            spawnAreaManager = SpawnAreaManager(this)

            // 5. GAME CONFIGS & INSTANCE PRELOAD
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
                logger.info("[LocoPillars] Starting arena generation process...")
                arenaManager.preloadInstances(configs, configManager.instancesPerGameType)
                logger.info("[LocoPillars] Initializing game instances...")
                gameManager.preloadInstances(arenaManager)
            }

            // 6. DATABASE (AFTER GAME SETUP!)
            try {
                DatabaseManager.initialize(this, configManager.dbConfig)
                logger.info("[LocoPillars] ✓ DatabaseManager initialized successfully")
            } catch (e: Exception) {
                logger.severe("[LocoPillars] ❌ Database initialization failed! ${e.message}")
                e.printStackTrace()
                server.pluginManager.disablePlugin(this)
                return
            }

            // 7. COMMANDS
            registerCommands()
            placeholders = Placeholders(this)


            if (!setupProtocolLib()) {
                logger.severe("ProtocolLib is required for proper tab functionality! Disabling tab per-game-instance feature.")
            }

            // ProtocolLib init: set protocolLibAvailable TRUE if setup succeed
            if (protocolManager != null) {
                protocolLibAvailable = true // <-- BELANGRIJK! Mark ProtocolLib als beschikbaar
                protocolLibTabVisibilityManager = ProtocolLibTabVisibilityManager(this)
                logger.info("ProtocolLib TabVisibilityManager enabled - using packet-based tab control")
            } else {
                protocolLibAvailable = false
                tabVisibilityManager = TabVisibilityManager(this)
                logger.warning("Using legacy TabVisibilityManager - ProtocolLib not available")
            }

            GlobalVisibilityTicker.initialize(this)

            // 8. VANISH SYSTEM
            val vanishListener = VanishListener(this)
            VanishManager.initialize(this, vanishListener)
            server.pluginManager.registerEvents(vanishListener, this)
            getCommand("vanish")?.setExecutor(VanishCommand(this))
            getCommand("hideme")?.setExecutor(HideMeCommand())
            getCommand("hideothers")?.setExecutor(HideOthersCommand())
            getCommand("showothers")?.setExecutor(ShowOthersCommand())
            Bukkit.getOnlinePlayers().forEach { vanishListener.updateBossBar(it) }

            // 9. UNIFIED TEAMS SYSTEM
            enableTeamsFunction()
            logger.info("[LocoPillars] ✓ Unified Teams system initialized successfully")

            // 10. HOTBAR & ITEMS SYSTEM
            val itemsFile = File(dataFolder, "items.yml")
            if (!itemsFile.exists()) itemsFile.writeText(DEFAULT_ITEMS_YML)
            val itemsConfig = YamlConfiguration.loadConfiguration(itemsFile)
            hotbarItemManager = HotbarItemManager(this, itemsConfig)

            // 11. EVENT LISTENERS
            gameListener = GameListener(this)
            server.pluginManager.registerEvents(gameListener, this)
            debugLog("✓ GameListener registered")

            spectateListener = SpectateListener(this, hotbarItemManager)
            server.pluginManager.registerEvents(spectateListener, this)
            debugLog("✓ SpectateListener registered")

            itemHotBarListener = ItemHotBarListener(this, hotbarItemManager)
            server.pluginManager.registerEvents(itemHotBarListener, this)
            debugLog("✓ ItemHotBarListener registered")

            server.pluginManager.registerEvents(SpeedGuiListener(), this)
            debugLog("✓ SpeedGuiListener registered")

            spectatorInventoryManager = SpectatorInventoryManager(this, hotbarItemManager)
            spectatorInteractionHandler = SpectatorInteractionHandler(this, hotbarItemManager)
            server.pluginManager.registerEvents(PlayerJoinListener(this), this)

            // 12. ACHIEVEMENTS SYSTEM
            achievementManager = AchievementManager()
            val achievementsFolder = File(dataFolder, "Achievements")
            if (!achievementsFolder.exists()) achievementsFolder.mkdirs()
            val achievementLoader = AchievementLoader(this)
            achievementLoader.loadAchievements(achievementsFolder, achievementManager)
            achievementListener = AchievementListener(achievementManager, this)
            server.pluginManager.registerEvents(achievementListener, this)

            val achievementsCommand = AchievementsCommand(achievementManager, this)
            getCommand("achievements")?.setExecutor(achievementsCommand)
            server.pluginManager.registerEvents(achievementsCommand, this)

            val statsCommand = StatsCommand(this, achievementsCommand)
            getCommand("stats")?.setExecutor(statsCommand)
            server.pluginManager.registerEvents(statsCommand, this)

            logger.info("[LocoPillars] ✓ Achievements system, /achievements and /stats command registered")

            // 13. CHAT SYSTEM
            chatManager = ChatManager(this)
            chatManager.initialize()
            debugLog("✓ Chat system initialized successfully")

            // 14. LUCKY BLOCKS SYSTEM
            val luckyBlockEngine = LuckyBlockEngineIntegration.initializeEngine(this, configManager)
            LuckyBlockEngineIntegration.registerEngineListeners(this, luckyBlockEngine)
            LuckyBlocksUtil.setLuckyBlockEngine(luckyBlockEngine)

            val luckyBlocksListener = LuckyBlocksListener(configManager)
            Bukkit.getPluginManager().registerEvents(luckyBlocksListener, this)

            val engineCommand = LuckyBlockEngineIntegration.createEngineCommand(configManager, luckyBlockEngine)
            getCommand("luckyblocks")?.setExecutor(engineCommand)
            getCommand("luckyblocks")?.tabCompleter = engineCommand

            Bukkit.getPluginManager().registerEvents(LuckyBlocksMenu, this)

            // 15. PLACEHOLDERS
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                placeholders.register()
                logger.info("[LocoPillars] Placeholders registered with PlaceholderAPI!")
            } else {
                logger.warning("[LocoPillars] PlaceholderAPI not found, placeholders will not work.")
            }

            // 16. STARTUP SUMMARY
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

    private fun setupProtocolLib(): Boolean {
        return try {
            val protocolLibPlugin = server.pluginManager.getPlugin("ProtocolLib")
            if (protocolLibPlugin == null || !protocolLibPlugin.isEnabled) {
                logger.warning("ProtocolLib plugin not found or not enabled!")
                protocolLibAvailable = false
                return false
            }

            protocolManager = ProtocolLibrary.getProtocolManager()
            protocolLibAvailable = true // <-- MARK AS AVAILABLE!
            logger.info("ProtocolLib integration successful - version: ${protocolLibPlugin.description.version}")
            true
        } catch (e: Exception) {
            protocolLibAvailable = false
            logger.severe("Failed to initialize ProtocolLib: ${e.message}")
            false
        }
    }

    fun isProtocolLibAvailable(): Boolean = protocolLibAvailable && ::protocolLibTabVisibilityManager.isInitialized

    fun getProtocolLibTabManager(): ProtocolLibTabVisibilityManager? {
        return if (isProtocolLibAvailable()) protocolLibTabVisibilityManager else null
    }

    fun getProtocolLibTabStats(): Map<String, Any>? {
        return getProtocolLibTabManager()?.getVisibilityStats()
    }

    fun reloadTabSystems() {
        try {
            tabManager.reload()

            if (isProtocolLibAvailable()) {
                protocolLibTabVisibilityManager.cleanup()
                protocolLibTabVisibilityManager = ProtocolLibTabVisibilityManager(this)
                protocolLibTabVisibilityManager.forceUpdateAllPlayers()
            }

            GlobalVisibilityTicker.queueGlobalUpdate()
        } catch (e: Exception) {
            logger.warning("Error reloading tab systems: ${e.message}")
        }
    }

    fun enableTeamsFunction() {
        teamManager = TeamManager(this)
        tabManager = TabManager(this)
        scoreboardManager = ScoreboardManager(this)
        tabVisibilityManager = TabVisibilityManager(this)
        teamsListener = TeamsListener(this)

        server.pluginManager.registerEvents(teamsListener, this)
        getCommand("tab")?.let {
            val tabCmd = TabCommand(this)
            it.setExecutor(tabCmd)
            it.tabCompleter = tabCmd
        }

        tabManager.enable()
        scoreboardManager.enable()

        if (tabManager.displayPerGameInstance) {
            tabVisibilityManager.updateTabVisibilityForAllPlayers()
        }

        teamManager.forceRefreshAllTeams()
        tabManager.forceUpdateAllPlayers()
        scoreboardManager.forceUpdateAllPlayers()
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
            
            // Cleanup editor sessions
            if (::editorSessionManager.isInitialized) {
                logger.info("[LocoPillars] Cleaning up editor sessions...")
                editorSessionManager.cleanup()
                logger.info("[LocoPillars] ✓ Editor sessions cleanup completed")
            }

            // Cleanup admin edit mode states
            if (::adminEditorManager.isInitialized) {
                adminEditorManager.cleanupAll()
                logger.info("[LocoPillars] ✓ Admin editor mode states cleaned up")
            }

            logger.info("[LocoPillars] ✓ Cleanup completed with $cleanupErrors total errors")

        } catch (e: Exception) {
            logger.severe("[LocoPillars] ✗ Error during plugin disable: ${e.message}")
            e.printStackTrace()
        }

        try {
            DatabaseManager.close()
            logger.info("[LocoPillars] ✓ DatabaseManager shutdown completed")
        } catch (e: Exception) {
            logger.severe("[LocoPillars] ❌ Error during database shutdown: ${e.message}")
            e.printStackTrace()
        }

        logger.info("[LocoPillars] ==========================================")
        logger.info("[LocoPillars] PLUGIN DISABLED SUCCESSFULLY!")
        logger.info("[LocoPillars] ==========================================")
    }

    private fun createFolderStructure() {
        debugLog("Creating plugin folder structure...")

        val foldersToCreate = listOf(
            File(dataFolder, "Data")
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

        val memoryCommand = getCommand("memory")
        if (memoryCommand != null) {
            memoryCommand.setExecutor(MemoryCommand())
            debugLog("✓ Command '/memory' registered")
            if (configManager.debugMode) {
                debugLog("Command aliases: ${memoryCommand.aliases.joinToString(", ")}")
            }
        } else {
            logger.severe("[LocoPillars] ✗ Failed to register '/memory' command")
        }

        // Admin Editor System command
        getCommand("editor")?.let {
            val editorCmd = AdminEditorCommand(
                this, adminEditorManager, mapRepository, editorSessionManager,
                editorSettingsGui, spawnPointEditor, editorFileValidator, gameFileService
            )
            it.setExecutor(editorCmd)
            it.tabCompleter = editorCmd
            debugLog("✓ Command '/editor' registered")
        } ?: logger.warning("[LocoPillars] '/editor' command not found in plugin.yml")
    }

    private fun showNoConfigsWarning() {
        logger.warning("[LocoPillars] ==========================================")
        logger.warning("[LocoPillars] NO GAME CONFIGURATIONS FOUND!")
        logger.warning("[LocoPillars] ==========================================")
        logger.warning("[LocoPillars] Please ensure:")
        logger.warning("[LocoPillars] 1. Config files (.yml) are in: ${dataFolder.absolutePath}/Data/Games/")
        logger.warning("[LocoPillars] 2. Schematic files (.schem) are in: ${dataFolder.absolutePath}/Data/Schems/")
        logger.warning("[LocoPillars] 3. File names match (without extensions)")
        logger.warning("[LocoPillars] 4. Both files exist for each game type")
        logger.warning("[LocoPillars]")
        logger.warning("[LocoPillars] The plugin will continue running but no games will be available")
        logger.warning("[LocoPillars] Default template examples have been created in the Data/Games folder")
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
            logger.info("[LocoPillars]   - Data folder: ${dataFolder.resolve("Data").absolutePath}")
            logger.info("[LocoPillars]   - Games folder: ${dataFolder.resolve("Data/Games").absolutePath}")
            logger.info("[LocoPillars]   - Schems folder: ${dataFolder.resolve("Data/Schems").absolutePath}")
            logger.info("[LocoPillars]   - GameWorlds folder: ${dataFolder.resolve("Data/GameWorlds").absolutePath}")

            val runtime = Runtime.getRuntime()
            val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024
            val totalMemory = runtime.totalMemory() / 1024 / 1024
            logger.info("[LocoPillars] Memory usage: ${usedMemory}MB / ${totalMemory}MB")
        }

        logger.info("[LocoPillars] Plugin enabled successfully!")
        logger.info("[LocoPillars] ==========================================")
    }

    private fun debugLog(message: String) {
        if (::configManager.isInitialized && configManager.debugMode) {
            logger.info("[LocoPillars] $message")
        }
    }

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

const val DEFAULT_ITEMS_YML = """
items:
  spectate:
    1:
      type: itemsadder-locomc:pack_joinrandomgame
      name: "&bJoin Random Game"
      lore:
        - "&7Click to join a random game"
      command: "/pillars join random"
      slot: 4
    2:
      type: itemsadder-locomc:pack_changespeed
      name: "&eSpectate Speed &7(GUI)"
      lore:
        - "&7Select your spectate speed"
      command: "%speed%"
      slot: 6
  lobby:
    1:
      type: itemsadder-locomc:pack_accountsettings
      name: "&aAccount Settings"
      lore:
        - "&7Open the account settings menu"
      command: "/settings"
      slot: 4
    2:
      type: itemsadder-locomc:pack_playerstats
      name: "&e&lPlayer Statistics"
      lore:
        - "&7View your player statistics"
      slot: 6
      command: "/stats"
    3:
      type: itemsadder-locomc:pack_joinrandomgame
      name: "&bJoin Random Game"
      lore:
        - "&7Click to join a random game"
      command: "/pillars join random"
      slot: 8
  game_lobby:
    1:
      type: itemsadder-locomc:pack_leavegame
      name: "&cLeave Game"
      lore:
        - "&7Click to leave this game and return to the lobby"
      command: "/pillars leave"
      slot: 4
    2:
      type: itemsadder-locomc:pack_vote
      name: "&eVote for Event"
      lore:
        - "&7Vote for a special event in this game!"
      command: "/pillars vote"
      slot: 6
Menu:
  Speed:
    Title: "&8■ &fSpeed Selection &8■"
    Border:
      Item: BLACK_STAINED_GLASS_PANE
    Filler:
      Item: GRAY_STAINED_GLASS_PANE
    Speeds:
      1:
        Slot: 10
        SpeedLevel: 1
        Item: itemsadder-locomc:pack_speed1
      2:
        Slot: 11
        SpeedLevel: 2
        Item: itemsadder-locomc:pack_speed2
      3:
        Slot: 12
        SpeedLevel: 3
        Item: itemsadder-locomc:pack_speed3
      4:
        Slot: 13
        SpeedLevel: 4
        Item: itemsadder-locomc:pack_speed4
      5:
        Slot: 14
        SpeedLevel: 5
        Item: itemsadder-locomc:pack_speed5
      6:
        Slot: 15
        SpeedLevel: 6
        Item: itemsadder-locomc:pack_speed6
      7:
        Slot: 16
        SpeedLevel: 7
        Item: itemsadder-locomc:pack_speed7
    Close:
      Slot: 22
      Item: BARRIER
"""
