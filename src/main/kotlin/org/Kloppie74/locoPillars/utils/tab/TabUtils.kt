package org.Kloppie74.locoPillars.utils.tab

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG
import org.bukkit.scheduler.BukkitRunnable
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID

class TabUtils(val plugin: LocoPillars) {

    private var enabled: Boolean = true
    private var header: List<String> = listOf()
    private var footer: List<String> = listOf()
    private var sortingOrder: List<String> = listOf()
    private var groupPermissions: Map<String, String> = mapOf()
    var displayMode: String = "AUTO"
    var prefixSuffix: Map<String, Pair<String, String>> = mapOf()
    var displayPerGameInstance: Boolean = false

    private var updateTask: BukkitRunnable? = null
    private val lastPlayerStates = ConcurrentHashMap<UUID, String>() // Track changes to minimize updates

    private val mainScoreboard: Scoreboard by lazy { Bukkit.getScoreboardManager().mainScoreboard }

    init {
        loadConfig()
    }

    private fun loadConfig() {
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
    }

    fun getGroup(player: Player): String {
        for (group in sortingOrder) {
            val perm = groupPermissions[group] ?: continue
            if (player.hasPermission(perm)) {
                return group
            }
        }
        return sortingOrder.lastOrNull() ?: "Default"
    }

    fun getPrefix(player: Player): String {
        val modeKey = displayMode.uppercase()
        val pair = prefixSuffix[modeKey] ?: prefixSuffix["AUTO"] ?: Pair("", "")
        val group = getGroup(player)
        var prefix = pair.first
        prefix = prefix.replace("%group%", group)
        prefix = replaceCustomPlaceholders(prefix, player)
        return MSG.chatColors(prefix)
    }

    fun getSuffix(player: Player): String {
        val modeKey = displayMode.uppercase()
        val pair = prefixSuffix[modeKey] ?: prefixSuffix["AUTO"] ?: Pair("", "")
        var suffix = pair.second
        suffix = suffix.replace("%group%", getGroup(player))
        suffix = replaceCustomPlaceholders(suffix, player)
        return MSG.chatColors(suffix)
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
            plugin.logger.warning("[TabUtils] Error replacing placeholders for ${player.name}: ${e.message}")
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

    fun assignTeamForTab(player: Player) {
        val group = getGroup(player)
        val sortIndex = sortingOrder.indexOf(group).let { if (it != -1) it else sortingOrder.size - 1 }
        val teamNameRaw = String.format("%02d_%s_%s", sortIndex + 1, group, player.name)
        val teamName = if (teamNameRaw.length > 16) teamNameRaw.substring(0, 16) else teamNameRaw

        var team: Team? = null
        try {
            team = mainScoreboard.getTeam(teamName)
            if (team == null) {
                team = mainScoreboard.registerNewTeam(teamName)
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabUtils] Error getting/creating team $teamName: ${e.message}")
        }

        val prefix = getPrefix(player)
        val suffix = getSuffix(player)
        try {
            team?.prefix = prefix
            team?.suffix = suffix
        } catch (e: Exception) {
            plugin.logger.warning("[TabUtils] Error setting prefix/suffix for team $teamName: ${e.message}")
        }

        try {
            mainScoreboard.teams.forEach { t ->
                if (t != team && t.hasEntry(player.name)) {
                    t.removeEntry(player.name)
                }
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabUtils] Error cleaning up old teams for ${player.name}: ${e.message}")
        }

        try {
            if (!team!!.hasEntry(player.name)) {
                team.addEntry(player.name)
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabUtils] Error adding ${player.name} to team $teamName: ${e.message}")
        }

        try {
            player.scoreboard = mainScoreboard
        } catch (e: Exception) {
            plugin.logger.warning("[TabUtils] Error setting scoreboard for ${player.name}: ${e.message}")
        }
    }

    fun updateTabForPlayer(player: Player) {
        if (!enabled || !player.isOnline) {
            return
        }

        try {
            val playerUUID = try {
                player.uniqueId
            } catch (e: Exception) {
                plugin.logger.warning("[TabUtils] Error getting player UUID: ${e.message}")
                return
            }

            if (playerUUID == null) {
                plugin.logger.warning("[TabUtils] Player UUID is null")
                return
            }

            val currentState = generatePlayerStateString(player)
            val lastState = lastPlayerStates[playerUUID]

            if (currentState != lastState) {
                lastPlayerStates[playerUUID] = currentState
                assignTeamForTab(player)
                updateHeaderFooterForPlayer(player)
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabUtils] Error updating tab for player ${player.name}: ${e.message}")
        }
    }

    private fun generatePlayerStateString(player: Player): String {
        val gameInstance = plugin.gameManager.getPlayerInstance(player)
        return "${gameInstance?.id ?: "lobby"}-${gameInstance?.players?.size ?: 0}-${gameInstance?.state?.name ?: "LOBBY"}-${Bukkit.getOnlinePlayers().size}"
    }

    private fun updateHeaderFooterForPlayer(player: Player) {
        try {
            val headerLine = if (header.isNotEmpty()) {
                header.joinToString("\n") { MSG.chatColors(replaceCustomPlaceholders(it, player)) }
            } else ""

            val footerLine = if (footer.isNotEmpty()) {
                footer.joinToString("\n") { MSG.chatColors(replaceCustomPlaceholders(it, player)) }
            } else ""

            player.setPlayerListHeaderFooter(headerLine, footerLine)
        } catch (e: Exception) {
            plugin.logger.warning("[TabUtils] Error updating header/footer for ${player.name}: ${e.message}")
        }
    }

    fun updateAllTabs() {
        if (!enabled) {
            return
        }

        try {
            val onlinePlayers = Bukkit.getOnlinePlayers().toList()
            onlinePlayers.forEach { player ->
                updateTabForPlayer(player)
            }

            if (displayPerGameInstance) {
                plugin.tabVisibilityManager.updateTabVisibilityForAllPlayers()
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabUtils] Error updating all tabs: ${e.message}")
        }
    }

    fun isEnabled(): Boolean = enabled

    fun startTabAutoUpdate() {
        if (!enabled) {
            return
        }

        stopTabAutoUpdate()
        updateTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    updateAllTabs()
                } catch (e: Exception) {
                    plugin.logger.warning("[TabUtils] Error in auto-update task: ${e.message}")
                }
            }
        }
        updateTask?.runTaskTimer(plugin, 20L, 20L)
        plugin.logger.info("[TabUtils] Auto-update started (every 1 second)")
    }

    fun stopTabAutoUpdate() {
        updateTask?.cancel()
        updateTask = null
        plugin.logger.info("[TabUtils] Auto-update stopped")
    }

    fun onPlayerJoinGame(player: Player) {
        if (!enabled) {
            return
        }

        try {
            val playerUUID = try {
                player.uniqueId
            } catch (e: Exception) {
                plugin.logger.warning("[TabUtils] Error getting player UUID in onPlayerJoinGame: ${e.message}")
                return
            }

            if (playerUUID == null) {
                plugin.logger.warning("[TabUtils] Player UUID is null in onPlayerJoinGame")
                return
            }

            lastPlayerStates.remove(playerUUID)
            updateTabForPlayer(player)

            if (displayPerGameInstance) {
                val gameInstance = plugin.gameManager.getPlayerInstance(player)
                if (gameInstance != null) {
                    plugin.tabVisibilityManager.onPlayerJoinGame(player, gameInstance)
                }
            }

            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                try {
                    lastPlayerStates.clear()
                    updateAllTabs()
                } catch (e: Exception) {
                    plugin.logger.warning("[TabUtils] Error in delayed full update after game join: ${e.message}")
                }
            }, 5L)
        } catch (e: Exception) {
            plugin.logger.warning("[TabUtils] Error handling player join game for ${player.name}: ${e.message}")
        }
    }

    fun onPlayerLeaveGame(player: Player) {
        if (!enabled) {
            return
        }

        try {
            val playerUUID = try {
                player.uniqueId
            } catch (e: Exception) {
                plugin.logger.warning("[TabUtils] Error getting player UUID in onPlayerLeaveGame: ${e.message}")
                return
            }

            if (playerUUID == null) {
                plugin.logger.warning("[TabUtils] Player UUID is null in onPlayerLeaveGame")
                return
            }

            val gameInstance = plugin.gameManager.getPlayerInstance(player)
            lastPlayerStates.remove(playerUUID)
            updateTabForPlayer(player)

            if (displayPerGameInstance) {
                plugin.tabVisibilityManager.onPlayerLeaveGame(player, gameInstance)
            }

            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                try {
                    lastPlayerStates.clear()
                    updateAllTabs()
                } catch (e: Exception) {
                    plugin.logger.warning("[TabUtils] Error in delayed full update after game leave: ${e.message}")
                }
            }, 5L)
        } catch (e: Exception) {
            plugin.logger.warning("[TabUtils] Error handling player leave game for ${player.name}: ${e.message}")
        }
    }

    fun onPlayerJoin(player: Player) {
        if (!enabled) {
            return
        }

        try {
            val playerUUID = try {
                player.uniqueId
            } catch (e: Exception) {
                plugin.logger.warning("[TabUtils] Error getting player UUID in onPlayerJoin: ${e.message}")
                return
            }

            if (playerUUID == null) {
                plugin.logger.warning("[TabUtils] Player UUID is null in onPlayerJoin")
                return
            }

            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                updateTabForPlayer(player)
                if (displayPerGameInstance) {
                    plugin.tabVisibilityManager.updateTabVisibilityForPlayer(player)
                }
            }, 10L)
        } catch (e: Exception) {
            plugin.logger.warning("[TabUtils] Error handling player join for ${player.name}: ${e.message}")
        }
    }

    fun onPlayerQuit(player: Player) {
        if (!enabled) {
            return
        }

        try {
            val playerUUID = try {
                player.uniqueId
            } catch (e: Exception) {
                plugin.logger.warning("[TabUtils] Error getting player UUID in onPlayerQuit: ${e.message}")
                return
            }

            if (playerUUID == null) {
                plugin.logger.warning("[TabUtils] Player UUID is null in onPlayerQuit")
                return
            }

            lastPlayerStates.remove(playerUUID)

            if (displayPerGameInstance) {
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    plugin.tabVisibilityManager.updateTabVisibilityForAllPlayers()
                }, 5L)
            }
        } catch (e: Exception) {
            plugin.logger.warning("[TabUtils] Error handling player quit for ${player.name}: ${e.message}")
        }
    }

    fun forceUpdateAllPlayers() {
        try {
            lastPlayerStates.clear()
            updateAllTabs()
        } catch (e: Exception) {
            plugin.logger.warning("[TabUtils] Error in force update: ${e.message}")
        }
    }

    fun getTabStats(): Map<String, Any> {
        return mapOf(
            "enabled" to enabled,
            "displayPerGameInstance" to displayPerGameInstance,
            "cachedPlayerStates" to lastPlayerStates.size,
            "updateTaskActive" to (updateTask != null && !updateTask!!.isCancelled),
            "onlinePlayers" to Bukkit.getOnlinePlayers().size
        )
    }

    fun cleanup() {
        try {
            stopTabAutoUpdate()
            lastPlayerStates.clear()
            plugin.logger.info("[TabUtils] Cleanup completed")
        } catch (e: Exception) {
            plugin.logger.warning("[TabUtils] Error during cleanup: ${e.message}")
        }
    }
}