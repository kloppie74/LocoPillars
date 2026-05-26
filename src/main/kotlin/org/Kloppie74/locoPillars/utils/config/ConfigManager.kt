package org.Kloppie74.locoPillars.utils.config

import org.Kloppie74.locoPillars.database.DatabaseConfig
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import org.bukkit.Material

class ConfigManager(private val plugin: JavaPlugin) {
    private lateinit var config: FileConfiguration
    private val configFile: File = File(plugin.dataFolder, "config.yml")

    // Configuration values with defaults
    var debugMode: Boolean = false
        private set
    var instancesPerGameType: Int = 10
        private set
    var instanceDistance: Int = 1000
        private set
    var maxConcurrentSchemPastes: Int = 5
        private set
    var schemPasteTimeout: Long = 30000L
        private set
    var enableProgressBar: Boolean = true
        private set
    var worldEditProvider: String = "AUTO"
        private set
    var autoSave: Boolean = true
        private set
    var autoSaveInterval: Long = 6000L // 5 minutes in ticks
        private set

    // Lobby Spawn
    var lobbyWorld: String = "world"
        private set
    var lobbySpawnX: Double = 0.5
        private set
    var lobbySpawnY: Double = 68.0
        private set
    var lobbySpawnZ: Double = 0.5
        private set
    var lobbySpawnYaw: Float = 0.0f
        private set
    var lobbySpawnPitch: Float = 0.0f
        private set

    // Chat Configuration
    var chatEnabled: Boolean = true
    var lobbyChatEnabled: Boolean = true
        private set
    var gameChatEnabled: Boolean = true
        private set
    var spectatorChatEnabled: Boolean = true
        private set
    var teamChatEnabled: Boolean = true
        private set
    var socialSpyEnabled: Boolean = true
    var chatLogEnabled: Boolean = true
    var chatCooldownSeconds: Int = 3
        private set
    var maxChatLength: Int = 256
        private set
    var profanityFilterEnabled: Boolean = false

    // Chat Formats
    var lobbyChatFormat: String = "&7[&bLobby&7] &f{player}&7: &f{message}"
        private set
    var gameLobbyFormat: String = "&7[&eGame&7] &f{player}&7: &f{message}"
        private set
    var gameSpectatorFormat: String = "&7[&8Spectator&7] &f{player}&7: &8{message}"
        private set
    var teamChatFormat: String = "&7[&a{team}&7] &f{player}&7: &a{message}"
        private set
    var globalChatFormat: String = "&7[&cGlobal&7] &f{player}&7: &f{message}"
        private set
    var socialSpyFormat: String = "&8[SocialSpy] &7{type} &8» &f{player}&7: &f{message}"
        private set
    var joinMessageFormat: String = "&a{player} &7joined the lobby"
        private set
    var leaveMessageFormat: String = "&c{player} &7left the lobby"
        private set
    var gameJoinFormat: String = "&a{player} &7joined the game"
        private set
    var gameLeaveFormat: String = "&c{player} &7left the game"
        private set

    // Database config
    lateinit var dbConfig: DatabaseConfig
        private set

    // TAB CONFIG SECTION
    var tabSection: org.bukkit.configuration.ConfigurationSection? = null
        private set

    // === Cosmetics settings ===
    var cosmeticsEnabled: Boolean = true
        private set
    var cosmeticsCoinsProvider: String = "SELF"
        private set

    // === LuckyBlock settings ===
    var luckyBlocksEnabled: Boolean = true
        private set
    var luckyBlockTextures: List<LuckyBlockTexture> = emptyList()
        private set
    var luckyBlockItemMaterial: Material = Material.PLAYER_HEAD
        private set
    var luckyBlockItemName: String = "&6LuckyBlock"
        private set
    var luckyBlockItemLore: List<String> = listOf("&7Break for a random effect!", "&fRarity: {rarity}")
        private set
    var luckyBlockItemTexture: String = ""
        private set
    var luckyBlockRarityChances: Map<String, Int> = mapOf(
        "COMMON" to 50,
        "UNCOMMON" to 30,
        "RARE" to 15,
        "EPIC" to 4,
        "LEGENDARY" to 1
    )
        private set

    data class LuckyBlockTexture(
        val glass: Material,
        val texture: String
    )

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
        instancesPerGameType = config.getInt("game.instances-per-type", 10)
        instanceDistance = config.getInt("game.instance-distance", 1000)
        maxConcurrentSchemPastes = config.getInt("performance.max-concurrent-schem-pastes", 5)
        schemPasteTimeout = config.getLong("performance.schem-paste-timeout", 30000L)
        worldEditProvider = config.getString("worldedit.provider", "AUTO")?.uppercase() ?: "AUTO"
        enableProgressBar = config.getBoolean("ui.enable-progress-bar", true)
        autoSave = config.getBoolean("world.auto-save", true)
        autoSaveInterval = config.getLong("world.auto-save-interval", 6000L)

        // Load Lobby Spawn settings
        val spawnSection = config.getConfigurationSection("Spawn")
        lobbyWorld    = spawnSection?.getString("World") ?: "world"
        lobbySpawnX   = spawnSection?.getDouble("X",     0.5)  ?: 0.5
        lobbySpawnY   = spawnSection?.getDouble("Y",    68.0)  ?: 68.0
        lobbySpawnZ   = spawnSection?.getDouble("Z",     0.5)  ?: 0.5
        lobbySpawnYaw   = (spawnSection?.getDouble("Yaw",   0.0) ?: 0.0).toFloat()
        lobbySpawnPitch = (spawnSection?.getDouble("Pitch", 0.0) ?: 0.0).toFloat()

        // Load Chat Configuration
        val chatSection = config.getConfigurationSection("chat")
        chatEnabled = chatSection?.getBoolean("enabled", true) ?: true
        lobbyChatEnabled = chatSection?.getBoolean("lobby-chat.enabled", true) ?: true
        gameChatEnabled = chatSection?.getBoolean("game-chat.enabled", true) ?: true
        spectatorChatEnabled = chatSection?.getBoolean("spectator-chat.enabled", true) ?: true
        teamChatEnabled = chatSection?.getBoolean("team-chat.enabled", true) ?: true
        socialSpyEnabled = chatSection?.getBoolean("social-spy.enabled", true) ?: true
        chatLogEnabled = chatSection?.getBoolean("logging.enabled", true) ?: true
        chatCooldownSeconds = chatSection?.getInt("cooldown-seconds", 3) ?: 3
        maxChatLength = chatSection?.getInt("max-length", 256) ?: 256
        profanityFilterEnabled = chatSection?.getBoolean("profanity-filter.enabled", false) ?: false

        // Load Chat Formats
        val formatsSection = chatSection?.getConfigurationSection("formats")
        lobbyChatFormat = formatsSection?.getString("lobby", "&7[&bLobby&7] &f{player}&7: &f{message}")
            ?: "&7[&bLobby&7] &f{player}&7: &f{message}"
        gameLobbyFormat = formatsSection?.getString("game-lobby", "&7[&eGame&7] &f{player}&7: &f{message}")
            ?: "&7[&eGame&7] &f{player}&7: &f{message}"
        gameSpectatorFormat = formatsSection?.getString("spectator", "&7[&8Spectator&7] &f{player}&7: &8{message}")
            ?: "&7[&8Spectator&7] &f{player}&7: &8{message}"
        teamChatFormat = formatsSection?.getString("team", "&7[&a{team}&7] &f{player}&7: &a{message}")
            ?: "&7[&a{team}&7] &f{player}&7: &a{message}"
        globalChatFormat = formatsSection?.getString("global", "&7[&cGlobal&7] &f{player}&7: &f{message}")
            ?: "&7[&cGlobal&7] &f{player}&7: &f{message}"
        socialSpyFormat = formatsSection?.getString("social-spy", "&8[SocialSpy] &7{type} &8» &f{player}&7: &f{message}")
            ?: "&8[SocialSpy] &7{type} &8» &f{player}&7: &f{message}"
        joinMessageFormat = formatsSection?.getString("join", "&a{player} &7joined the lobby")
            ?: "&a{player} &7joined the lobby"
        leaveMessageFormat = formatsSection?.getString("leave", "&c{player} &7left the lobby")
            ?: "&c{player} &7left the lobby"
        gameJoinFormat = formatsSection?.getString("game-join", "&a{player} &7joined the game")
            ?: "&a{player} &7joined the game"
        gameLeaveFormat = formatsSection?.getString("game-leave", "&c{player} &7left the game")
            ?: "&c{player} &7left the game"

        // Load database config
        dbConfig = loadDatabaseConfig(config)

        // Load TAB section from config.yml (replacing tab.yml)
        tabSection = config.getConfigurationSection("Tab")

        // === Load Cosmetics ===
        val cosmeticsSection = config.getConfigurationSection("Cosmetics")
        cosmeticsEnabled      = cosmeticsSection?.getBoolean("enabled", true) ?: true
        cosmeticsCoinsProvider = cosmeticsSection?.getString("coins-provider", "SELF")?.uppercase() ?: "SELF"

        // === Load LuckyBlocks ===
        val luckySection = config.getConfigurationSection("Luckyblocks")
        luckyBlocksEnabled = luckySection?.getBoolean("enabled", true) ?: true

        // Parse textures
        val blockLayout = luckySection?.getConfigurationSection("block-layout")
        val textureList = blockLayout?.getStringList("textures") ?: emptyList()
        luckyBlockTextures = textureList.mapNotNull { line ->
            val parts = line.split(",")
            val glassPart = parts.find { it.trim().startsWith("Glass=") }
            val texturePart = parts.find { it.trim().startsWith("Texture=") }
            val glassName = glassPart?.substringAfter("Glass=")?.trim()?.uppercase()
            val glassMat = when (glassName) {
                "RED" -> Material.RED_STAINED_GLASS
                "BLUE" -> Material.BLUE_STAINED_GLASS
                "GREEN" -> Material.GREEN_STAINED_GLASS
                "YELLOW" -> Material.YELLOW_STAINED_GLASS
                "PINK" -> Material.PINK_STAINED_GLASS
                else -> Material.GLASS
            }
            val texture = texturePart?.substringAfter("Texture=")?.trim()
            if (glassName != null && texture != null) LuckyBlockTexture(glassMat, texture) else null
        }

        val luckyItem = luckySection?.getConfigurationSection("item")
        luckyBlockItemMaterial = Material.valueOf(luckyItem?.getString("material", "PLAYER_HEAD") ?: "PLAYER_HEAD")
        luckyBlockItemName = luckyItem?.getString("name", "&6LuckyBlock") ?: "&6LuckyBlock"
        luckyBlockItemLore = luckyItem?.getStringList("lore") ?: listOf("&7Break for a random effect!", "&fRarity: {rarity}")
        luckyBlockItemTexture = luckyItem?.getString("texture", "") ?: ""

        // Parse rarity chances as a map
        val raritySection = luckySection?.getConfigurationSection("rarity")
        luckyBlockRarityChances = if (raritySection != null) {
            raritySection.getKeys(false).associateWith { key ->
                raritySection.getInt("$key.chance", 0)
            }.mapKeys { it.key.uppercase() }
        } else {
            mapOf(
                "COMMON" to 50,
                "UNCOMMON" to 30,
                "RARE" to 15,
                "EPIC" to 4,
                "LEGENDARY" to 1
            )
        }

        validateConfig()
    }

    private fun createDefaultConfig() {
        configFile.parentFile.mkdirs()
        config = YamlConfiguration()

        // Set default values
        config.set("debug", false)
        config.createSection("game")
        config.set("game.instances-per-type", 10)
        config.set("game.instance-distance", 1000)

        config.createSection("Spawn")
        config.set("Spawn.World", "world")
        config.set("Spawn.X", 0.5)
        config.set("Spawn.Y", 68.0)
        config.set("Spawn.Z", 0.5)
        config.set("Spawn.Yaw", 0.0)
        config.set("Spawn.Pitch", 0.0)

        // Chat Configuration
        config.createSection("chat")
        val chatSection = config.getConfigurationSection("chat")!!
        chatSection.set("enabled", true)

        chatSection.createSection("lobby-chat")
        chatSection.set("lobby-chat.enabled", true)

        chatSection.createSection("game-chat")
        chatSection.set("game-chat.enabled", true)

        chatSection.createSection("spectator-chat")
        chatSection.set("spectator-chat.enabled", true)

        chatSection.createSection("team-chat")
        chatSection.set("team-chat.enabled", true)

        chatSection.createSection("social-spy")
        chatSection.set("social-spy.enabled", true)

        chatSection.createSection("logging")
        chatSection.set("logging.enabled", true)

        chatSection.set("cooldown-seconds", 3)
        chatSection.set("max-length", 256)

        chatSection.createSection("profanity-filter")
        chatSection.set("profanity-filter.enabled", false)
        chatSection.set("profanity-filter.words", listOf("word1", "word2", "word3"))

        // Chat Formats
        chatSection.createSection("formats")
        val formatsSection = chatSection.getConfigurationSection("formats")!!
        formatsSection.set("lobby", "&7[&bLobby&7] &f{player}&7: &f{message}")
        formatsSection.set("game-lobby", "&7[&eGame&7] &f{player}&7: &f{message}")
        formatsSection.set("spectator", "&7[&8Spectator&7] &f{player}&7: &8{message}")
        formatsSection.set("team", "&7[&a{team}&7] &f{player}&7: &a{message}")
        formatsSection.set("global", "&7[&cGlobal&7] &f{player}&7: &f{message}")
        formatsSection.set("social-spy", "&8[SocialSpy] &7{type} &8» &f{player}&7: &f{message}")
        formatsSection.set("join", "&a{player} &7joined the lobby")
        formatsSection.set("leave", "&c{player} &7left the lobby")
        formatsSection.set("game-join", "&a{player} &7joined the game")
        formatsSection.set("game-leave", "&c{player} &7left the game")

        config.createSection("database")
        val dbSection = config.getConfigurationSection("database")!!
        dbSection.set("type", "h2")
        dbSection.set("host", "localhost")
        dbSection.set("port", 3306)
        dbSection.set("database", "locopillars")
        dbSection.set("username", "")
        dbSection.set("password", "")
        dbSection.set("useSSL", false)
        dbSection.set("max-pool-size", 10)
        dbSection.set("min-pool-size", 5)
        dbSection.set("connection-timeout-ms", 30000)
        dbSection.set("idle-timeout-ms", 600000)
        dbSection.set("max-lifetime-ms", 1800000)
        dbSection.set("leak-detection-threshold-ms", 60000)
        dbSection.set("cleanup-enabled", true)
        dbSection.set("cleanup-days", 30)
        dbSection.set("enable-query-logging", false)
        dbSection.set("slow-query-threshold-ms", 1000)
        dbSection.set("cache-size", 1000)
        dbSection.set("cache-expiration-minutes", 30)

        config.createSection("performance")
        config.set("performance.max-concurrent-schem-pastes", 5)
        config.set("performance.schem-paste-timeout", 30000)

        config.createSection("ui")
        config.set("ui.enable-progress-bar", true)

        config.createSection("world")
        config.set("world.auto-save", true)
        config.set("world.auto-save-interval", 6000)

        // TAB section (NEW, replaces tab.yml)
        val tabSection = config.createSection("Tab")
        tabSection.set("Enabled", true)
        tabSection.set("Header", listOf("&aWelkom in de lobby!", "&bVeel plezier!"))
        tabSection.set("Footer", listOf("&7Online: %online_players%"))
        val sortingGroups = tabSection.createSection("Sorting-Groups")
        sortingGroups.set("Order", "Owner,Admin,Mod,Helper,Vip,Default")
        val permsSection = sortingGroups.createSection("Permissions")
        permsSection.set("Owner", "tab.order.owner")
        permsSection.set("Admin", "tab.order.admin")
        permsSection.set("Mod", "tab.order.mod")
        permsSection.set("Helper", "tab.order.helper")
        permsSection.set("Vip", "tab.order.vip")
        permsSection.set("Default", "tab.order.default")
        tabSection.set("Display-Mode", "AUTO")
        tabSection.set("display-online-player-list-per-gameinstance", false)
        val prefixSuffixSection = tabSection.createSection("Player-Prefix-Suffix")
        val ownerPS = prefixSuffixSection.createSection("Owner")
        ownerPS.set("Prefix", "&4[Owner] ")
        ownerPS.set("Suffix", "")
        val adminPS = prefixSuffixSection.createSection("Admin")
        adminPS.set("Prefix", "&c[Admin] ")
        adminPS.set("Suffix", "")
        val defaultPS = prefixSuffixSection.createSection("Default")
        defaultPS.set("Prefix", "")
        defaultPS.set("Suffix", "")

        // Cosmetics section
        val cosmeticsSection = config.createSection("Cosmetics")
        cosmeticsSection.set("enabled", true)
        cosmeticsSection.set("coins-provider", "SELF")

        // Luckyblocks section
        val luckySection = config.createSection("Luckyblocks")
        luckySection.set("enabled", true)
        val blockLayout = luckySection.createSection("block-layout")
        blockLayout.set("textures", listOf(
            "Glass=RED,Texture=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTNiY2RmMTUzMzRiMTc4Zjk5OGVhZDFiZGU2YWZhODhhM2Q0YTAxYzlkZDJjODY4OWMyZGJhZTJlOTNjOTk3In19fQ==",
            "Glass=BLUE,Texture=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGM1MzhmODZjODU4OWYyZmMxMzcxOTIyYzAxOTE5MWRkYTIxMjIwMjFjNjM5ZjI4ZGMyNjUwZTJhOTcxYWJkMiJ9fX0=",
            "Glass=GREEN,Texture=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzIzOWFhZjZhNGZkNDNiZmZjYmYwZWIyOGMxMWQ5MmUzM2UwOWMzNzlmODQyNzcxZWE4OTYyYTk2NzJhZTM0NCJ9fX0=",
            "Glass=YELLOW,Texture=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWRhZjA5Mjg0NTMwY2U5MmVkMmRmMmE2MmUxYjA1YTExZjE4NzFmODVhZTU1OTA0Mjg0NDIwNmQ2NmMwYjViMCJ9fX0=",
            "Glass=PINK,Texture=eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzEyMGQ1NzY1ODUxZDhlODlkYzI5YTU2YTYxY2FhYmVhYzdkMjZkOWYzNjQ3ZjQxZmI3NzE1ZWI3OGU0MmU3ZiJ9fX0="
        ))

        val luckyItem = luckySection.createSection("item")
        luckyItem.set("material", "PLAYER_HEAD")
        luckyItem.set("name", "&6LuckyBlock")
        luckyItem.set("lore", listOf("&7Break for a random effect!", "&fRarity: {rarity}"))
        luckyItem.set("texture", "")

        val raritySection = luckySection.createSection("rarity")
        raritySection.createSection("common").set("chance", 50)
        raritySection.createSection("uncommon").set("chance", 30)
        raritySection.createSection("rare").set("chance", 15)
        raritySection.createSection("epic").set("chance", 4)
        raritySection.createSection("legendary").set("chance", 1)

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

        // Validate chat settings
        if (chatCooldownSeconds < 0 || chatCooldownSeconds > 60) {
            plugin.logger.warning("[ConfigManager] Invalid chat cooldown: $chatCooldownSeconds, clamping to 0-60")
            chatCooldownSeconds = chatCooldownSeconds.coerceIn(0, 60)
            config.set("chat.cooldown-seconds", chatCooldownSeconds)
            needsSave = true
        }

        if (maxChatLength < 10 || maxChatLength > 512) {
            plugin.logger.warning("[ConfigManager] Invalid max chat length: $maxChatLength, clamping to 10-512")
            maxChatLength = maxChatLength.coerceIn(10, 512)
            config.set("chat.max-length", maxChatLength)
            needsSave = true
        }

        // Validate database config
        val dbErrors = dbConfig.validate()
        if (dbErrors.isNotEmpty()) {
            dbErrors.forEach {
                plugin.logger.warning("[ConfigManager][Database] $it")
            }
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

    // Chat configuration getters
    fun getProfanityWords(): List<String> {
        return config.getStringList("chat.profanity-filter.words")
    }

    // Logging helpers
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

    fun getLobbySpawnLocation(): org.bukkit.Location? {
        val world = plugin.server.getWorld(lobbyWorld) ?: return null
        return org.bukkit.Location(world, lobbySpawnX, lobbySpawnY, lobbySpawnZ, lobbySpawnYaw, lobbySpawnPitch)
    }

    /**
     * Persist a new lobby spawn location to config.yml and update in-memory state.
     * Called by /setspawn. All game-leave and /spawn teleports read from [getLobbySpawnLocation].
     */
    fun setLobbySpawnLocation(location: org.bukkit.Location) {
        val worldName = location.world?.name ?: return
        lobbyWorld      = worldName
        lobbySpawnX     = location.x
        lobbySpawnY     = location.y
        lobbySpawnZ     = location.z
        lobbySpawnYaw   = location.yaw
        lobbySpawnPitch = location.pitch

        config.set("Spawn.World", worldName)
        config.set("Spawn.X",     location.x)
        config.set("Spawn.Y",     location.y)
        config.set("Spawn.Z",     location.z)
        config.set("Spawn.Yaw",   location.yaw.toDouble())
        config.set("Spawn.Pitch", location.pitch.toDouble())
        saveConfig()

        if (debugMode) {
            plugin.logger.info("[ConfigManager] Spawn updated → ($lobbySpawnX, $lobbySpawnY, $lobbySpawnZ) " +
                "yaw=$lobbySpawnYaw pitch=$lobbySpawnPitch world=$lobbyWorld")
        }
    }

    // Get config values
    fun getConfig(): FileConfiguration = config
    fun getConfigFile(): File = configFile

    // === LuckyBlock util getters (gebruik alleen de property, geen duplicate getter!) ===
    fun getLuckyBlockItemLore(rarity: String = "COMMON"): List<String> =
        luckyBlockItemLore.map { it.replace("{rarity}", rarity.replaceFirstChar { it.uppercase() }) }
    fun getLuckyBlockRarityChance(rarity: String): Int = luckyBlockRarityChances[rarity.uppercase()] ?: 0
    fun getLuckyBlockRarities(): List<String> = luckyBlockRarityChances.keys.toList()

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
        plugin.logger.info("[ConfigManager]   Lobby spawn world: $lobbyWorld")
        plugin.logger.info("[ConfigManager]   Lobby spawn X: $lobbySpawnX")
        plugin.logger.info("[ConfigManager]   Lobby spawn Y: $lobbySpawnY")
        plugin.logger.info("[ConfigManager]   Lobby spawn Z: $lobbySpawnZ")
        plugin.logger.info("[ConfigManager]   Chat enabled: $chatEnabled")
        plugin.logger.info("[ConfigManager]   Social spy enabled: $socialSpyEnabled")
        plugin.logger.info("[ConfigManager]   Chat cooldown: ${chatCooldownSeconds}s")
        plugin.logger.info("[ConfigManager]   Max chat length: $maxChatLength")
        plugin.logger.info("[ConfigManager]   Database configuration:")
        dbConfig.getSummary().forEach { (k, v) ->
            plugin.logger.info("[ConfigManager]     $k: $v")
        }
        plugin.logger.info("[ConfigManager]   TAB config loaded: ${tabSection != null}")
        plugin.logger.info("[ConfigManager]   LuckyBlocks enabled: $luckyBlocksEnabled")
        plugin.logger.info("[ConfigManager]   LuckyBlock textures: ${luckyBlockTextures.size} entries")
        luckyBlockTextures.forEach { t ->
            plugin.logger.info("[ConfigManager]     ${t.glass} / ${t.texture.take(20)}...")
        }
        plugin.logger.info("[ConfigManager]   LuckyBlock item: $luckyBlockItemMaterial, $luckyBlockItemName")
        plugin.logger.info("[ConfigManager]   LuckyBlock rarities: ${luckyBlockRarityChances}")
    }

    private fun loadDatabaseConfig(config: FileConfiguration): DatabaseConfig {
        val dbSection = config.getConfigurationSection("database")
        return DatabaseConfig(
            type = dbSection?.getString("type") ?: "h2",
            host = dbSection?.getString("host") ?: "localhost",
            port = dbSection?.getInt("port") ?: 3306,
            database = dbSection?.getString("database") ?: "locopillars",
            username = dbSection?.getString("username") ?: "",
            password = dbSection?.getString("password") ?: "",
            useSSL = dbSection?.getBoolean("useSSL") ?: false,
            maxPoolSize = dbSection?.getInt("max-pool-size") ?: 10,
            minPoolSize = dbSection?.getInt("min-pool-size") ?: 5,
            connectionTimeoutMs = dbSection?.getLong("connection-timeout-ms") ?: 30000L,
            idleTimeoutMs = dbSection?.getLong("idle-timeout-ms") ?: 600000L,
            maxLifetimeMs = dbSection?.getLong("max-lifetime-ms") ?: 1800000L,
            leakDetectionThresholdMs = dbSection?.getLong("leak-detection-threshold-ms") ?: 60000L,
            cleanupEnabled = dbSection?.getBoolean("cleanup-enabled") ?: true,
            cleanupDays = dbSection?.getInt("cleanup-days") ?: 30,
            enableQueryLogging = dbSection?.getBoolean("enable-query-logging") ?: false,
            slowQueryThresholdMs = dbSection?.getLong("slow-query-threshold-ms") ?: 1000L,
            cacheSize = dbSection?.getInt("cache-size") ?: 1000,
            cacheExpirationMinutes = dbSection?.getInt("cache-expiration-minutes") ?: 30
        )
    }
}