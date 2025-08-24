package org.Kloppie74.locoPillars.utils.vanish.commands

import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.vanish.VanishManager
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ShowOthersCommand : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(MSG.chatColors("&cOnly players can use this command!"))
            return true
        }

        if (args.isEmpty()) {
            player.sendMessage(MSG.chatColors("&cUsage: /showothers <player>"))
            return true
        }

        val target = Bukkit.getPlayer(args[0])
        if (target == null) {
            player.sendMessage(MSG.chatColors("&cPlayer not found or not online."))
            return true
        }

        val state = VanishManager.getState(player)
        if (!state.hideOthers) {
            player.sendMessage(MSG.chatColors("&cYou must enable hideOthers before you can show specific players."))
            return true
        }

        if (state.showOthers.contains(target.uniqueId)) {
            VanishManager.removeShowOther(player, target)
            player.sendMessage(MSG.chatColors("&cYou can no longer see &6${target.name}."))
        } else {
            VanishManager.addShowOther(player, target)
            player.sendMessage(MSG.chatColors("&aYou can now see &6${target.name}."))
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val player = sender as? Player ?: return emptyList()
        if (args.size == 1) {
            val partial = args[0].lowercase()
            return Bukkit.getOnlinePlayers().filter { it.name.lowercase().startsWith(partial) }.map { it.name }
        }
        return emptyList()
    }
}