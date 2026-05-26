package org.Kloppie74.locoPillars.utils.teams

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars

/**
 * UPDATED Unified /tab command voor scoreboard, tab functionaliteit en ProtocolLib support
 */
class TabCommand(
    private val plugin: LocoPillars
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }
        when (args[0].lowercase()) {
            "help" -> { showHelp(sender); return true }
            "reload" -> {
                if (!sender.hasPermission("locopillars.admin")) {
                    sender.sendMessage("§cYou do not have permission to do this."); return true
                }
                handleReload(sender); return true
            }
            "enable" -> {
                if (!sender.hasPermission("locopillars.admin")) {
                    sender.sendMessage("§cYou do not have permission to do this."); return true
                }
                if (args.size < 2) {
                    sender.sendMessage("§eUsage: /tab enable <tab|scoreboard|protocollib|all>"); return true
                }
                handleEnable(sender, args[1]); return true
            }
            "disable" -> {
                if (!sender.hasPermission("locopillars.admin")) {
                    sender.sendMessage("§cYou do not have permission to do this."); return true
                }
                if (args.size < 2) {
                    sender.sendMessage("§eUsage: /tab disable <tab|scoreboard|protocollib|all>"); return true
                }
                handleDisable(sender, args[1]); return true
            }
            "toggle" -> {
                if (sender !is Player) {
                    sender.sendMessage("§cThis command can only be used by players."); return true
                }
                if (args.size < 2) {
                    sender.sendMessage("§eUsage: /tab toggle <scoreboard>"); return true
                }
                handleToggle(sender, args[1]); return true
            }
            "stats" -> {
                if (!sender.hasPermission("locopillars.admin")) {
                    sender.sendMessage("§cYou do not have permission to do this."); return true
                }
                handleStats(sender); return true
            }
            "debug" -> {
                if (!sender.hasPermission("locopillars.admin")) {
                    sender.sendMessage("§cYou do not have permission to do this."); return true
                }
                handleDebug(sender); return true
            }
            "protocollib", "protocol" -> {
                if (!sender.hasPermission("locopillars.admin")) {
                    sender.sendMessage("§cYou do not have permission to do this."); return true
                }
                handleProtocolLib(sender, args.drop(1).toTypedArray()); return true
            }
            else -> {
                sender.sendMessage("§cUnknown subcommand. Use /tab help for available commands."); return true
            }
        }
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("§6§l=== TAB & SCOREBOARD COMMANDS ===")
        sender.sendMessage("")
        sender.sendMessage("§e/tab reload §7- Reload both tab and scoreboard configs")
        sender.sendMessage("§e/tab enable <tab|scoreboard|protocollib|all> §7- Enable system(s)")
        sender.sendMessage("§e/tab disable <tab|scoreboard|protocollib|all> §7- Disable system(s)")
        if (sender is Player) {
            sender.sendMessage("§e/tab toggle scoreboard §7- Toggle your personal scoreboard")
        }
        if (sender.hasPermission("locopillars.admin")) {
            sender.sendMessage("")
            sender.sendMessage("§6Admin Commands:")
            sender.sendMessage("§e/tab stats §7- Show system statistics")
            sender.sendMessage("§e/tab debug §7- Show debug information")
            sender.sendMessage("§e/tab protocollib <stats|refresh|test> §7- ProtocolLib management")
        }
        sender.sendMessage("")
        sender.sendMessage("§7Systems managed: §aTab List§7, §aScoreboards§7, §aTeams§7, §bProtocolLib")

        // Show current system status
        val protocolLibStatus = if (plugin.isProtocolLibAvailable()) "§aEnabled" else "§cDisabled"
        val tabPerGameStatus = if (plugin.tabManager.displayPerGameInstance) "§aEnabled" else "§cDisabled"
        sender.sendMessage("§7ProtocolLib Tab System: $protocolLibStatus")
        sender.sendMessage("§7Tab Per-Game-Instance: $tabPerGameStatus")
    }

    private fun handleReload(sender: CommandSender) {
        try {
            sender.sendMessage("§7Reloading tab and scoreboard systems...")
            plugin.configManager.reloadConfig()
            plugin.tabManager.reload()
            plugin.scoreboardManager.reload()

            // Reload ProtocolLib system if available
            if (plugin.isProtocolLibAvailable()) {
                plugin.getProtocolLibTabManager()?.cleanup()
                plugin.reloadTabSystems()
                sender.sendMessage("§aProtocolLib tab system reloaded!")
            }

            sender.sendMessage("§aSuccessfully reloaded tab and scoreboard systems!")
            plugin.logger.info("[TabCommand] Systems reloaded by ${sender.name}")
        } catch (e: Exception) {
            sender.sendMessage("§cError during reload: ${e.message}")
            plugin.logger.warning("[TabCommand] Error during reload by ${sender.name}: ${e.message}")
        }
    }

    private fun handleEnable(sender: CommandSender, target: String) {
        try {
            when (target.lowercase()) {
                "tab" -> {
                    plugin.tabManager.enable()
                    sender.sendMessage("§aTab system has been enabled!")
                }
                "scoreboard", "scoreboards" -> {
                    plugin.scoreboardManager.enable()
                    sender.sendMessage("§aScoreboard system has been enabled!")
                }
                "protocollib", "protocol" -> {
                    if (plugin.isProtocolLibAvailable()) {
                        plugin.getProtocolLibTabManager()?.forceUpdateAllPlayers()
                        sender.sendMessage("§aProtocolLib tab system refreshed!")
                    } else {
                        sender.sendMessage("§cProtocolLib tab system is not available!")
                    }
                }
                "all" -> {
                    plugin.tabManager.enable()
                    plugin.scoreboardManager.enable()
                    if (plugin.isProtocolLibAvailable()) {
                        plugin.getProtocolLibTabManager()?.forceUpdateAllPlayers()
                    }
                    sender.sendMessage("§aAll systems have been enabled!")
                }
                else -> {
                    sender.sendMessage("§cInvalid target. Use: tab, scoreboard, protocollib, or all")
                    return
                }
            }
            plugin.logger.info("[TabCommand] Enabled $target by ${sender.name}")
        } catch (e: Exception) {
            sender.sendMessage("§cError enabling $target: ${e.message}")
            plugin.logger.warning("[TabCommand] Error enabling $target by ${sender.name}: ${e.message}")
        }
    }

    private fun handleDisable(sender: CommandSender, target: String) {
        try {
            when (target.lowercase()) {
                "tab" -> {
                    plugin.tabManager.disable()
                    sender.sendMessage("§cTab system has been disabled!")
                }
                "scoreboard", "scoreboards" -> {
                    plugin.scoreboardManager.disable()
                    sender.sendMessage("§cScoreboard system has been disabled!")
                }
                "protocollib", "protocol" -> {
                    if (plugin.isProtocolLibAvailable()) {
                        plugin.getProtocolLibTabManager()?.cleanup()
                        sender.sendMessage("§cProtocolLib tab system has been disabled!")
                    } else {
                        sender.sendMessage("§eProtocolLib tab system is already disabled!")
                    }
                }
                "all" -> {
                    plugin.tabManager.disable()
                    plugin.scoreboardManager.disable()
                    if (plugin.isProtocolLibAvailable()) {
                        plugin.getProtocolLibTabManager()?.cleanup()
                    }
                    sender.sendMessage("§cAll systems have been disabled!")
                }
                else -> {
                    sender.sendMessage("§cInvalid target. Use: tab, scoreboard, protocollib, or all")
                    return
                }
            }
            plugin.logger.info("[TabCommand] Disabled $target by ${sender.name}")
        } catch (e: Exception) {
            sender.sendMessage("§cError disabling $target: ${e.message}")
            plugin.logger.warning("[TabCommand] Error disabling $target by ${sender.name}: ${e.message}")
        }
    }

    private fun handleToggle(player: Player, target: String) {
        try {
            when (target.lowercase()) {
                "scoreboard", "scoreboards" -> {
                    val newState = plugin.scoreboardManager.togglePlayerScoreboard(player)
                    if (newState) {
                        player.sendMessage("§aYour scoreboard has been enabled!")
                    } else {
                        player.sendMessage("§cYour scoreboard has been disabled!")
                    }
                }
                else -> {
                    player.sendMessage("§cInvalid target. Use: scoreboard")
                    return
                }
            }
        } catch (e: Exception) {
            player.sendMessage("§cError toggling $target: ${e.message}")
            plugin.logger.warning("[TabCommand] Error toggling $target for ${player.name}: ${e.message}")
        }
    }

    private fun handleStats(sender: CommandSender) {
        try {
            sender.sendMessage("§6§l=== TAB & SCOREBOARD STATISTICS ===")
            sender.sendMessage("")

            // Team Manager Stats
            val teamStats = plugin.teamManager.getStats()
            sender.sendMessage("§eTeam Manager:")
            sender.sendMessage("§7  Total Players: §f${teamStats["totalPlayers"]}")
            sender.sendMessage("§7  Total Teams: §f${teamStats["totalTeams"]}")
            sender.sendMessage("§7  Tab Teams: §f${teamStats["tabTeams"]}")
            sender.sendMessage("§7  Sidebar Teams: §f${teamStats["sidebarTeams"]}")
            sender.sendMessage("")

            // Tab Manager Stats
            val tabStats = plugin.tabManager.getStats()
            sender.sendMessage("§eTab Manager:")
            sender.sendMessage("§7  Enabled: §f${tabStats["enabled"]}")
            sender.sendMessage("§7  Per-Game Instance: §f${tabStats["displayPerGameInstance"]}")
            sender.sendMessage("§7  Cached States: §f${tabStats["cachedPlayerStates"]}")
            sender.sendMessage("§7  Update Task Active: §f${tabStats["updateTaskActive"]}")
            sender.sendMessage("")

            // ProtocolLib Tab Manager Stats
            if (plugin.isProtocolLibAvailable()) {
                val protocolStats = plugin.getProtocolLibTabStats()
                if (protocolStats != null) {
                    sender.sendMessage("§bProtocolLib Tab Manager:")
                    sender.sendMessage("§7  Total Players: §f${protocolStats["totalPlayers"]}")
                    sender.sendMessage("§7  Players in Games: §f${protocolStats["playersInGames"]}")
                    sender.sendMessage("§7  Players in Lobby: §f${protocolStats["playersInLobby"]}")
                    sender.sendMessage("§7  Tracked Tab States: §f${protocolStats["trackedTabStates"]}")
                    sender.sendMessage("§7  Total Visibility Entries: §f${protocolStats["totalVisibilityEntries"]}")
                    sender.sendMessage("§7  Active Updates: §f${protocolStats["activeUpdates"]}")
                    sender.sendMessage("§7  ProtocolLib Enabled: §f${protocolStats["protocolLibEnabled"]}")
                    sender.sendMessage("")
                }
            } else {
                sender.sendMessage("§bProtocolLib Tab Manager: §cNot Available")
                sender.sendMessage("")
            }

            // Scoreboard Manager Stats
            val scoreboardStats = plugin.scoreboardManager.getStats()
            sender.sendMessage("§eScoreboard Manager:")
            sender.sendMessage("§7  Globally Enabled: §f${scoreboardStats["globalEnabled"]}")
            sender.sendMessage("§7  Active Scoreboards: §f${scoreboardStats["activeScoreboards"]}")
            sender.sendMessage("§7  Board Sections: §f${scoreboardStats["boardSections"]}")
            sender.sendMessage("§7  Player States: §f${scoreboardStats["playerStates"]}")
            sender.sendMessage("§7  Players with Toggled: §f${scoreboardStats["playersWithToggledScoreboards"]}")
            sender.sendMessage("§7  Update Task Active: §f${scoreboardStats["updateTaskActive"]}")
            sender.sendMessage("")

            // Global Visibility Ticker Stats
            val visibilityStats = GlobalVisibilityTicker.getVisibilityStats()
            sender.sendMessage("§eGlobal Visibility Ticker:")
            visibilityStats.forEach { (key, value) ->
                sender.sendMessage("§7  $key: §f$value")
            }

            sender.sendMessage("")
            sender.sendMessage("§7Online Players: §f${scoreboardStats["onlinePlayers"]}")
        } catch (e: Exception) {
            sender.sendMessage("§cError getting statistics: ${e.message}")
            plugin.logger.warning("[TabCommand] Error getting stats for ${sender.name}: ${e.message}")
        }
    }

    private fun handleDebug(sender: CommandSender) {
        try {
            sender.sendMessage("§6§l=== DEBUG INFORMATION ===")
            sender.sendMessage("")

            val teamStats = plugin.teamManager.getStats()
            sender.sendMessage("§eTeamManager Details:")
            teamStats.forEach { (key, value) ->
                sender.sendMessage("§7  $key: §f$value")
            }
            sender.sendMessage("")

            if (plugin.isProtocolLibAvailable()) {
                val protocolStats = plugin.getProtocolLibTabStats()
                if (protocolStats != null) {
                    sender.sendMessage("§bProtocolLibTabVisibilityManager Details:")
                    protocolStats.forEach { (key, value) ->
                        sender.sendMessage("§7  $key: §f$value")
                    }
                }
            } else {
                // Fallback to legacy system
                val visibilityStats = plugin.tabVisibilityManager.getVisibilityStats()
                sender.sendMessage("§eTabVisibilityManager Details (Legacy):")
                visibilityStats.forEach { (key, value) ->
                    sender.sendMessage("§7  $key: §f$value")
                }
            }
            sender.sendMessage("")

            val globalStats = GlobalVisibilityTicker.getVisibilityStats()
            sender.sendMessage("§eGlobalVisibilityTicker Details:")
            globalStats.forEach { (key, value) ->
                sender.sendMessage("§7  $key: §f$value")
            }
            sender.sendMessage("")

            sender.sendMessage("§7Debug mode: §f${plugin.configManager.debugMode}")
            sender.sendMessage("§7ProtocolLib available: §f${plugin.isProtocolLibAvailable()}")
            sender.sendMessage("§7Per-game-instance enabled: §f${plugin.tabManager.displayPerGameInstance}")
        } catch (e: Exception) {
            sender.sendMessage("§cError getting debug information: ${e.message}")
            plugin.logger.warning("[TabCommand] Error getting debug info for ${sender.name}: ${e.message}")
        }
    }

    private fun handleProtocolLib(sender: CommandSender, args: Array<String>) {
        if (!plugin.isProtocolLibAvailable()) {
            sender.sendMessage("§cProtocolLib tab system is not available!")
            return
        }

        if (args.isEmpty()) {
            sender.sendMessage("§eUsage: /tab protocollib <stats|refresh|test>")
            return
        }

        when (args[0].lowercase()) {
            "stats" -> {
                val stats = plugin.getProtocolLibTabStats()
                if (stats != null) {
                    sender.sendMessage("§6§l=== PROTOCOLLIB TAB STATISTICS ===")
                    stats.forEach { (key, value) ->
                        sender.sendMessage("§7$key: §f$value")
                    }
                } else {
                    sender.sendMessage("§cError getting ProtocolLib statistics!")
                }
            }
            "refresh", "reload" -> {
                try {
                    plugin.getProtocolLibTabManager()?.forceUpdateAllPlayers()
                    sender.sendMessage("§aProtocolLib tab visibility refreshed for all players!")
                } catch (e: Exception) {
                    sender.sendMessage("§cError refreshing ProtocolLib system: ${e.message}")
                }
            }
            "test" -> {
                if (sender is Player) {
                    try {
                        plugin.getProtocolLibTabManager()?.updateTabVisibilityForPlayer(sender)
                        sender.sendMessage("§aProtocolLib tab test completed for you!")
                    } catch (e: Exception) {
                        sender.sendMessage("§cError in ProtocolLib test: ${e.message}")
                    }
                } else {
                    sender.sendMessage("§cThis test can only be run by players!")
                }
            }
            else -> {
                sender.sendMessage("§cUnknown ProtocolLib command. Use: stats, refresh, or test")
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        when (args.size) {
            1 -> {
                val subcommands = mutableListOf("help", "reload")
                if (sender.hasPermission("locopillars.admin")) {
                    subcommands.addAll(listOf("enable", "disable", "stats", "debug", "protocollib"))
                }
                if (sender is Player) {
                    subcommands.add("toggle")
                }
                return subcommands.filter { it.startsWith(args[0], true) }
            }
            2 -> {
                when (args[0].lowercase()) {
                    "enable", "disable" -> {
                        return listOf("tab", "scoreboard", "protocollib", "all").filter { it.startsWith(args[1], true) }
                    }
                    "toggle" -> {
                        return listOf("scoreboard").filter { it.startsWith(args[1], true) }
                    }
                    "protocollib", "protocol" -> {
                        return listOf("stats", "refresh", "test").filter { it.startsWith(args[1], true) }
                    }
                }
            }
        }
        return emptyList()
    }
}