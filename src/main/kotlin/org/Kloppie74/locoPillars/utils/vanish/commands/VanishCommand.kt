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
            sender.sendMessage(MSG.lang("vanish.only_players"))
            return true
        }

        if (args.isNotEmpty()) {
            if (!player.hasPermission("locopillars.vanish.others")) {
                player.sendMessage(MSG.lang("vanish.no_permission_others"))
                return true
            }
            val target = Bukkit.getPlayer(args[0])
            if (target == null) {
                player.sendMessage(MSG.lang("vanish.player_not_found"))
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

        VanishManager.setVanished(target, newState)

        if (newState) {
            target.sendMessage(MSG.lang("vanish.vanished_self"))
            executor?.sendMessage(MSG.lang("vanish.vanished_other", "target" to target.name))
        } else {
            target.sendMessage(MSG.lang("vanish.unvanished_self"))
            executor?.sendMessage(MSG.lang("vanish.unvanished_other", "target" to target.name))
        }
    }
}
