package org.Kloppie74.locoPillars.utils.vanish.commands

import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.vanish.VanishManager
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class HideOthersCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(
                MSG.chatColors("&cOnly players can use this command!"))
            return true
        }

        if (args.isNotEmpty()) {
            if (!player.hasPermission("locopillars.hideothers.others")) {
                player.sendMessage(MSG.chatColors("&cYou do not have permission to toggle hideOthers for other players."))
                return true
            }
            val target = Bukkit.getPlayer(args[0])
            if (target == null) {
                player.sendMessage(MSG.chatColors("&cPlayer not found or not online."))
                return true
            }
            toggleHideOthers(target, player)
            return true
        }

        toggleHideOthers(player, null)
        return true
    }

    private fun toggleHideOthers(target: Player, executor: Player?) {
        val state = VanishManager.getState(target)
        val newState = !state.hideOthers
        VanishManager.setHideOthers(target, newState)
        if (newState) {
            target.sendMessage(MSG.chatColors("&aYou are now hiding other players in this world."))
            executor?.sendMessage(MSG.chatColors("&aYou have enabled hideOthers for &6${target.name}."))
        } else {
            target.sendMessage(MSG.chatColors("&cYou are no longer hiding other players."))
            executor?.sendMessage(MSG.chatColors("&cYou have disabled hideOthers for &6${target.name}."))
        }
    }
}