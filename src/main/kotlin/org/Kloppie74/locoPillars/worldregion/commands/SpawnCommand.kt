package org.Kloppie74.locoPillars.worldregion.commands

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars

/**
 * /spawn               — Teleport yourself to the lobby spawn.
 * /spawn <player>      — Teleport another player to spawn (requires locopillars.spawn.others).
 * /setspawn            — Save your current location as the lobby spawn (requires locopillars.setspawn).
 *
 * The spawn location is persisted in config.yml (Spawn section) and is
 * also used by game-leave, arena reset, and dead-player respawn flows.
 */
class SpawnCommand(private val plugin: LocoPillars) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): Boolean {
        when (command.name.lowercase()) {
            "spawn"    -> handleSpawn(sender, args)
            "setspawn" -> handleSetSpawn(sender)
        }
        return true
    }

    // -------------------------------------------------------------------------

    private fun handleSpawn(sender: CommandSender, args: Array<String>) {
        // Determine target player
        val target: Player = when {
            args.isNotEmpty() && sender.hasPermission("locopillars.spawn.others") -> {
                Bukkit.getPlayer(args[0]) ?: run {
                    sender.sendMessage("§cPlayer '${args[0]}' is not online.")
                    return
                }
            }
            args.isNotEmpty() && !sender.hasPermission("locopillars.spawn.others") -> {
                sender.sendMessage("§cYou don't have permission to teleport other players.")
                return
            }
            sender is Player -> sender
            else -> {
                sender.sendMessage("§cUsage (console): /spawn <player>")
                return
            }
        }

        val spawn = plugin.configManager.getLobbySpawnLocation()
        if (spawn == null) {
            sender.sendMessage("§cSpawn has not been set yet. An admin must run §e/setspawn §cfirst.")
            return
        }

        // If the target is in a game, remove them first
        val instance = plugin.gameManager.getPlayerInstance(target)
        if (instance != null) {
            plugin.gameManager.leaveGame(target)
        }

        target.teleport(spawn)

        if (target.uniqueId == (sender as? Player)?.uniqueId) {
            target.sendMessage("§aTeleported to spawn!")
        } else {
            sender.sendMessage("§aTeleported §e${target.name}§a to spawn.")
            target.sendMessage("§aYou were teleported to spawn by §e${sender.name}§a.")
        }
    }

    private fun handleSetSpawn(sender: CommandSender) {
        if (!sender.hasPermission("locopillars.setspawn")) {
            sender.sendMessage("§cYou don't have permission to set the spawn location.")
            sender.sendMessage("§7Required permission: §elocopillars.setspawn")
            return
        }

        if (sender !is Player) {
            sender.sendMessage("§cOnly players can set the spawn location.")
            return
        }

        val loc = sender.location
        plugin.configManager.setLobbySpawnLocation(loc)

        sender.sendMessage(
            "§aSpawn set! Location: §e(${loc.blockX}, ${loc.blockY}, ${loc.blockZ}) " +
            "§aYaw: §e${loc.yaw.toInt()}° §ain world §e${loc.world?.name ?: "unknown"}§a."
        )
        sender.sendMessage("§7All game-leave teleports and /spawn will now use this location.")
    }

    // -------------------------------------------------------------------------

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): List<String> {
        return when (command.name.lowercase()) {
            "spawn" -> {
                if (args.size == 1 && sender.hasPermission("locopillars.spawn.others")) {
                    Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[0].lowercase()) }
                } else emptyList()
            }
            else -> emptyList()
        }
    }
}
