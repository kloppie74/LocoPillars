package org.Kloppie74.locoPillars.utils.teams.tab

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG
import me.clip.placeholderapi.PlaceholderAPI
import org.Kloppie74.locoPillars.utils.teams.TeamManager
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

/**
 * TabManager die prefix/suffix en filtering per gameinstance/world regelt.
 * FIXED: No longer creates sidebar teams that conflict with scoreboard!
 */
class TabManager(private val plugin: LocoPillars) {

    private var enabled: Boolean = true
    private var header: List<String> = listOf()
    private var footer: List<String> = listOf()
    private var sortingOrder: List<String> = listOf()
    private var groupPermissions: Map<String, String> = mapOf()
    private var displayMode: String = "AUTO"
    private var prefixSuffix: Map<String, Pair<String, String>> = mapOf()
    var displayPerGameInstance: Boolean = false

    private var updateTask: BukkitRunnable? = null
    private val lastPlayerStates = ConcurrentHashMap<UUID, String>()

    init {
        loadConfig()
    }

    fun loadConfig() {
        val config = plugin.configManager.getConfig()
        val tabSection = config.getConfigurationSection("Tab") ?: return

        enabled = tabSection.getBoolean("Enabled", true)
        header = tabSection.getStringList("Header")
        footer = tabSection.getStringList("Footer")

        val sortingGroups = tabSection.getConfigurationSection("Sorting-Groups")
        sortingOrder = sortingGroups?.getString("Order")?.split(",")?.map { it.trim() } ?: listOf()
        groupPermissions = sortingGroups?.getConfigurationSection("Permissions")?.getKeys(false)?.associateWith { key ->
            sortingGroups.getConfigurationSection("Permissions")?.getString(key) ?: ""
        } ?: mapOf()

        displayMode = tabSection.getString("Display-Mode", "AUTO").toString()
        displayPerGameInstance = tabSection.getBoolean("display-online-player-list-per-gameinstance", false)

        val modesSection = tabSection.getConfigurationSection("Player-Prefix-Suffix")
        val map = mutableMapOf<String, Pair<String, String>>()
        modesSection?.getKeys(false)?.forEach { mode ->
            val prefix = modesSection.getConfigurationSection(mode)?.getString("Prefix") ?: ""
            val suffix = modesSection.getConfigurationSection(mode)?.getString("Suffix") ?: ""
            map[mode.uppercase()] = Pair(prefix, suffix)
        }
        prefixSuffix = map

        debugLog("Config loaded - enabled: $enabled, displayPerGameInstance: $displayPerGameInstance")
    }

    fun enable() {
        enabled = true
        startAutoUpdate()
        forceUpdateAllPlayers()
        debugLog("Tab system enabled")
    }

    fun disable() {
        enabled = false
        stopAutoUpdate()
        Bukkit.getOnlinePlayers().forEach { player ->
            plugin.teamManager.cleanupTeamsByType(player, TeamManager.TAB_TEAM_PREFIX)
        }
        debugLog("Tab system disabled")
    }

    fun reload() {
        debugLog("Reloading tab configuration...")
        loadConfig()
        forceUpdateAllPlayers()
        if (enabled) {
            startAutoUpdate()
        }
        debugLog("Tab configuration reloaded")
    }

    fun isEnabled(): Boolean = enabled

    /**
     * Update tab team (prefix/suffix) voor één speler.
     * FIXED: Only creates tab teams, NO sidebar teams!
     */
    fun updatePlayerTab(player: Player) {
        if (!enabled || !player.isOnline) return

        try {
            val playerUUID = player.uniqueId
            val currentState = generatePlayerStateString(player)
            val lastState = lastPlayerStates[playerUUID]
            if (currentState != lastState) {
                lastPlayerStates[playerUUID] = currentState
            }
            setupPlayerTabTeam(player) // FIXED: Only tab team, no sidebar!
            updateHeaderFooter(player)
        } catch (e: Exception) {
            plugin.logger.warning("[TabManager] Error updating tab for ${player.name}: ${e.message}")
        }
    }

    /**
     * Update tab voor alle spelers.
     */
    fun updateAllTabs() {
        if (!enabled) return
        try {
            Bukkit.getOnlinePlayers().forEach { player ->
                updatePlayerTab(player)
            }
            if (displayPerGameInstance) {
                plugin.tabVisibilityManager?.updateTabVisibilityForAllPlayers()
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabManager] Error updating all tabs: ${e.message}")
        }
    }

    fun forceUpdateAllPlayers() {
        lastPlayerStates.clear()
        updateAllTabs()
        debugLog("Forced update completed for all players")
    }

    /**
     * FIXED: Setup ONLY tab team for player, no sidebar teams!
     */
    private fun setupPlayerTabTeam(player: Player) {
        try {
            val prefix = getPrefix(player)
            val suffix = getSuffix(player)
            // FIXED: Only create tab team, no sidebar teams that conflict with scoreboard!
            plugin.teamManager.createTabTeam(player, prefix, suffix)
        } catch (e: Exception) {
            plugin.logger.warning("[TabManager] Error setting up tab team for ${player.name}: ${e.message}")
        }
    }

    fun getPrefix(player: Player): String {
        val modeKey = getPlayerTabMode(player)
        val pair = prefixSuffix[modeKey] ?: prefixSuffix["AUTO"] ?: Pair("", "")
        val group = getGroup(player)
        var prefix = pair.first
        prefix = prefix.replace("%group%", group)
        prefix = replaceCustomPlaceholders(prefix, player)
        return MSG.chatColors(prefix)
    }

    fun getSuffix(player: Player): String {
        val modeKey = getPlayerTabMode(player)
        val pair = prefixSuffix[modeKey] ?: prefixSuffix["AUTO"] ?: Pair("", "")
        var suffix = pair.second
        suffix = suffix.replace("%group%", getGroup(player))
        suffix = replaceCustomPlaceholders(suffix, player)
        return MSG.chatColors(suffix)
    }

    private fun getGroup(player: Player): String {
        for (group in sortingOrder) {
            val perm = groupPermissions[group] ?: continue
            if (player.hasPermission(perm)) {
                return group
            }
        }
        return sortingOrder.lastOrNull() ?: "Default"
    }

    private fun getPlayerTabMode(player: Player): String {
        val gameInstance = plugin.gameManager.getPlayerInstance(player)
        return when {
            displayMode.equals("ALL", ignoreCase = true) -> "ALL"
            displayMode.equals("GAME", ignoreCase = true) && gameInstance != null -> "GAME"
            displayMode.equals("LOBBY", ignoreCase = true) && gameInstance == null -> "LOBBY"
            gameInstance != null -> "GAME"
            else -> "LOBBY"
        }.uppercase()
    }

    private fun replaceCustomPlaceholders(text: String, player: Player): String {
        var result = text

        try {
            val gameInstance = plugin.gameManager.getPlayerInstance(player)
            if (gameInstance != null) {
                result = result.replace("%game_name%", gameInstance.getDisplayName())
                result = result.replace("%players%", gameInstance.players.size.toString())
                result = result.replace("%max_players%", gameInstance.getMaxPlayers().toString())
                result = result.replace("%players_left%", gameInstance.players.size.toString())
                result = result.replace("%map_name%", gameInstance.arenaInstance.template.name)
                result = result.replace("%game_state%", gameInstance.state.name)
            } else {
                result = result.replace("%game_name%", "Lobby")
                result = result.replace("%players%", "0")
                result = result.replace("%max_players%", "0")
                result = result.replace("%players_left%", "0")
                result = result.replace("%map_name%", "Lobby")
                result = result.replace("%game_state%", "LOBBY")
            }
            result = result.replace("%online_players%", getVisiblePlayerCount(player).toString())
            result = result.replace("%total_online%", Bukkit.getOnlinePlayers().size.toString())
            result = result.replace("%active_games%", plugin.gameManager.getActiveGames().size.toString())
            if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                result = PlaceholderAPI.setPlaceholders(player, result)
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabManager] Error replacing placeholders for ${player.name}: ${e.message}")
        }

        return result
    }

    private fun getVisiblePlayerCount(player: Player): Int {
        if (!displayPerGameInstance) {
            return Bukkit.getOnlinePlayers().size
        }
        val gameInstance = plugin.gameManager.getPlayerInstance(player)
        return gameInstance?.players?.size ?: Bukkit.getOnlinePlayers().size
    }

    private fun updateHeaderFooter(player: Player) {
        try {
            val headerLine = if (header.isNotEmpty()) {
                header.joinToString("\n") { MSG.chatColors(replaceCustomPlaceholders(it, player)) }
            } else ""
            val footerLine = if (footer.isNotEmpty()) {
                footer.joinToString("\n") { MSG.chatColors(replaceCustomPlaceholders(it, player)) }
            } else ""
            player.setPlayerListHeaderFooter(headerLine, footerLine)
        } catch (e: Exception) {
            plugin.logger.warning("[TabManager] Error updating header/footer for ${player.name}: ${e.message}")
        }
    }

    private fun generatePlayerStateString(player: Player): String {
        val gameInstance = plugin.gameManager.getPlayerInstance(player)
        return "${gameInstance?.id ?: "lobby"}-${gameInstance?.players?.size ?: 0}-${gameInstance?.state?.name ?: "LOBBY"}-${Bukkit.getOnlinePlayers().size}"
    }

    private fun startAutoUpdate() {
        if (!enabled) return
        stopAutoUpdate()
        updateTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    updateAllTabs()
                } catch (e: Exception) {
                    plugin.logger.warning("[TabManager] Error in auto-update: ${e.message}")
                }
            }
        }
        updateTask?.runTaskTimer(plugin, 20L, 20L)
        debugLog("Auto-update started")
    }

    private fun stopAutoUpdate() {
        updateTask?.cancel()
        updateTask = null
        debugLog("Auto-update stopped")
    }

    fun onPlayerJoin(player: Player) {
        if (!enabled) return
        try {
            lastPlayerStates.remove(player.uniqueId)
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                updatePlayerTab(player)
                if (displayPerGameInstance) {
                    plugin.tabVisibilityManager?.updateTabVisibilityForPlayer(player)
                }
            }, 10L)
        } catch (e: Exception) {
            plugin.logger.warning("[TabManager] Error handling player join for ${player.name}: ${e.message}")
        }
    }

    fun onPlayerQuit(player: Player) {
        if (!enabled) return
        try {
            lastPlayerStates.remove(player.uniqueId)
            if (displayPerGameInstance) {
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    plugin.tabVisibilityManager?.updateTabVisibilityForAllPlayers()
                }, 5L)
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabManager] Error handling player quit for ${player.name}: ${e.message}")
        }
    }

    fun onPlayerJoinGame(player: Player) {
        if (!enabled) return
        try {
            lastPlayerStates.remove(player.uniqueId)
            updatePlayerTab(player)
            if (displayPerGameInstance) {
                val gameInstance = plugin.gameManager.getPlayerInstance(player)
                if (gameInstance != null) {
                    plugin.tabVisibilityManager?.onPlayerJoinGame(player, gameInstance)
                }
            }
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                forceUpdateAllPlayers()
            }, 5L)
        } catch (e: Exception) {
            plugin.logger.warning("[TabManager] Error handling player join game for ${player.name}: ${e.message}")
        }
    }

    fun onPlayerLeaveGame(player: Player) {
        if (!enabled) return
        try {
            val gameInstance = plugin.gameManager.getPlayerInstance(player)
            lastPlayerStates.remove(player.uniqueId)
            updatePlayerTab(player)
            if (displayPerGameInstance) {
                plugin.tabVisibilityManager?.onPlayerLeaveGame(player, gameInstance)
            }
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                forceUpdateAllPlayers()
            }, 5L)
        } catch (e: Exception) {
            plugin.logger.warning("[TabManager] Error handling player leave game for ${player.name}: ${e.message}")
        }
    }

    fun getStats(): Map<String, Any> {
        return mapOf(
            "enabled" to enabled,
            "displayPerGameInstance" to displayPerGameInstance,
            "cachedPlayerStates" to lastPlayerStates.size,
            "updateTaskActive" to (updateTask != null && !updateTask!!.isCancelled),
            "onlinePlayers" to Bukkit.getOnlinePlayers().size,
            "sidebarTeamsDisabled" to true // Added indicator that sidebar teams are disabled
        )
    }

    fun cleanup() {
        try {
            stopAutoUpdate()
            lastPlayerStates.clear()
            Bukkit.getOnlinePlayers().forEach { player ->
                plugin.teamManager.cleanupTeamsByType(player, TeamManager.TAB_TEAM_PREFIX)
            }
            debugLog("Cleanup completed")
        } catch (e: Exception) {
            plugin.logger.warning("[TabManager] Error during cleanup: ${e.message}")
        }
    }

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[TabManager] $message")
        }
    }
}