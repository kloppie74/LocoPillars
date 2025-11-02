package org.Kloppie74.locoPillars.utils.vanish.commands

import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.vanish.VanishManager
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class VanishCommand(private val plugin: LocoPillars) : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(MSG.chatColors("&cOnly players can use this command!"))
            return true
        }

        if (args.isNotEmpty()) {
            if (!player.hasPermission("locopillars.vanish.others")) {
                player.sendMessage(MSG.chatColors("&cYou do not have permission to vanish other players."))
                return true
            }
            val target = Bukkit.getPlayer(args[0])
            if (target == null) {
                player.sendMessage(MSG.chatColors("&cPlayer not found or not online."))
                return true
            }
            toggleVanish(target, player)
            return true
        }

        toggleVanish(player, null)
        return true
    }

    private fun toggleVanish(target: Player, executor: Player?) {
        val currentState = VanishManager.isVanished(target)
        val newState = !currentState

        // Use VanishManager which handles all visibility updates automatically
        VanishManager.setVanished(target, newState)

        if (newState) {
            target.sendMessage(MSG.chatColors("&aYou are now vanished."))
            executor?.sendMessage(MSG.chatColors("&aYou have vanished &6${target.name}."))
        } else {
            target.sendMessage(MSG.chatColors("&cYou are no longer vanished."))
            executor?.sendMessage(MSG.chatColors("&6${target.name} &cis no longer vanished."))
        }
    }
}