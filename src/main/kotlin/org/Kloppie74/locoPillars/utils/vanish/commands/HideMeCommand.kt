package org.Kloppie74.locoPillars.utils.vanish.commands

import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.vanish.VanishManager
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class HideMeCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(MSG.chatColors("&cOnly players can use this command!"))
            return true
        }

        if (args.isNotEmpty()) {
            if (!player.hasPermission("locopillars.hideme.others")) {
                player.sendMessage(MSG.chatColors("&cYou do not have permission to toggle hideMe for other players."))
                return true
            }
            val target = Bukkit.getPlayer(args[0])
            if (target == null) {
                player.sendMessage(MSG.chatColors("&cPlayer not found or not online."))
                return true
            }
            toggleHideMe(target, player)
            return true
        }

        toggleHideMe(player, null)
        return true
    }

    private fun toggleHideMe(target: Player, executor: Player?) {
        val currentState = VanishManager.isHideMe(target)
        val newState = !currentState

        // Use VanishManager which handles all visibility updates automatically
        VanishManager.setHideMe(target, newState)

        if (newState) {
            target.sendMessage(MSG.chatColors("&aYou are now hidden from other players."))
            executor?.sendMessage(MSG.chatColors("&aYou have hidden &6${target.name} &afrom other players."))
        } else {
            target.sendMessage(MSG.chatColors("&cYou are now visible to other players again."))
            executor?.sendMessage(MSG.chatColors("&6${target.name} &cis now visible to other players again."))
        }
    }
}