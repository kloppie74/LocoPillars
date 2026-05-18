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
            sender.sendMessage(MSG.lang("vanish.only_players"))
            return true
        }

        if (args.isNotEmpty()) {
            if (!player.hasPermission("locopillars.hideothers.others")) {
                player.sendMessage(MSG.lang("vanish.hideothers.no_permission"))
                return true
            }
            val target = Bukkit.getPlayer(args[0])
            if (target == null) {
                player.sendMessage(MSG.lang("vanish.player_not_found"))
                return true
            }
            toggleHideOthers(target, player)
            return true
        }

        toggleHideOthers(player, null)
        return true
    }

    private fun toggleHideOthers(target: Player, executor: Player?) {
        val currentState = VanishManager.isHideOthers(target)
        val newState = !currentState

        VanishManager.setHideOthers(target, newState)

        if (newState) {
            target.sendMessage(MSG.lang("vanish.hideothers.enabled_self"))
            executor?.sendMessage(MSG.lang("vanish.hideothers.enabled_other", "target" to target.name))
        } else {
            target.sendMessage(MSG.lang("vanish.hideothers.disabled_self"))
            executor?.sendMessage(MSG.lang("vanish.hideothers.disabled_other", "target" to target.name))
        }
    }
}
