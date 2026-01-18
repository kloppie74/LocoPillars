package org.Kloppie74.locoPillars.api

import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.api.LocoDevStatsService
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * Command om LocoDev API data op te vragen
 */
class LocoDevCommand(private val plugin: LocoPillars) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (!sender.hasPermission("locopillars.locodev")) {
            sender.sendMessage("§cYou don't have permission to use this command.")
            return true
        }

        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "version" -> checkVersion(sender)
            "announcements" -> showAnnouncements(sender)
            "stats" -> showGlobalStats(sender)
            "reload" -> reloadStats(sender)
            "info" -> showInfo(sender)
            else -> showHelp(sender)
        }

        return true
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("§8§m------------------§r §6LocoDev API§8§m ------------------")
        sender.sendMessage("§e/locodev version §7- Check for plugin updates")
        sender.sendMessage("§e/locodev announcements §7- View announcements")
        sender.sendMessage("§e/locodev stats §7- View global statistics")
        sender.sendMessage("§e/locodev info §7- View service information")
        sender.sendMessage("§e/locodev reload §7- Reload stats service")
        sender.sendMessage("§8§m------------------------------------------------")
    }

    private fun checkVersion(sender: CommandSender) {
        if (plugin.locoDevStats?.isEnabled() != true) {
            sender.sendMessage("§8[§6LocoDev§8] §cStats service is not enabled!")
            return
        }

        sender.sendMessage("§8[§6LocoDev§8] §7Checking for updates...")

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                plugin.locoDevStats?.checkForUpdates()
                sender.sendMessage("§8[§6LocoDev§8] §aVersion check complete! Check console for details.")
            } catch (e: Exception) {
                sender.sendMessage("§8[§6LocoDev§8] §cFailed to check version: ${e.message}")
            }
        })
    }

    private fun showAnnouncements(sender: CommandSender) {
        if (plugin.locoDevStats?.isEnabled() != true) {
            sender.sendMessage("§8[§6LocoDev§8] §cStats service is not enabled!")
            return
        }

        sender.sendMessage("§8[§6LocoDev§8] §7Fetching announcements...")

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                plugin.locoDevStats?.fetchAnnouncements()
                sender.sendMessage("§8[§6LocoDev§8] §aAnnouncements fetched! Check console for details.")
            } catch (e: Exception) {
                sender.sendMessage("§8[§6LocoDev§8] §cFailed to fetch announcements: ${e.message}")
            }
        })
    }

    private fun showGlobalStats(sender: CommandSender) {
        if (plugin.locoDevStats?.isEnabled() != true) {
            sender.sendMessage("§8[§6LocoDev§8] §cStats service is not enabled!")
            return
        }

        sender.sendMessage("§8[§6LocoDev§8] §7Fetching global statistics...")

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                plugin.locoDevStats?.fetchGlobalStats()
                sender.sendMessage("§8[§6LocoDev§8] §aGlobal stats fetched! Check console for details.")
            } catch (e: Exception) {
                sender.sendMessage("§8[§6LocoDev§8] §cFailed to fetch stats: ${e.message}")
            }
        })
    }

    private fun showInfo(sender: CommandSender) {
        sender.sendMessage("§8§m------------------§r §6LocoDev Info§8§m ------------------")
        sender.sendMessage("§7Service Status: ${if (plugin.locoDevStats?.isEnabled() == true) "§aEnabled" else "§cDisabled"}")
        sender.sendMessage("§7Plugin Version: §f${plugin.description.version}")
        sender.sendMessage("§7Server Version: §f${plugin.server.version}")
        sender.sendMessage("§7Online Players: §f${plugin.server.onlinePlayers.size}")
        sender.sendMessage("§7Active Games: §f${plugin.gameManager.getActiveGames().size}")
        sender.sendMessage("§7Total Games: §f${plugin.gameManager.getAllInstances().size}")
        sender.sendMessage("§8§m------------------------------------------------")
    }

    private fun reloadStats(sender: CommandSender) {
        sender.sendMessage("§8[§6LocoDev§8] §7Reloading stats service...")

        try {
            plugin.locoDevStats?.stop()
            plugin.locoDevStats = LocoDevStatsService(plugin)
            plugin.locoDevStats?.start()
            sender.sendMessage("§8[§6LocoDev§8] §aStats service reloaded successfully!")
        } catch (e: Exception) {
            sender.sendMessage("§8[§6LocoDev§8] §cFailed to reload: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("version", "announcements", "stats", "info", "reload")
                .filter { it.startsWith(args[0].lowercase()) }
        }
        return emptyList()
    }
}