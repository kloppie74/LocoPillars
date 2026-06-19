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
            sender.sendMessage(MSG.lang("vanish.only_players"))
            return true
        }

        if (args.isNotEmpty()) {
            if (!player.hasPermission("locopillars.hideme.others")) {
                player.sendMessage(MSG.lang("vanish.hideme.no_permission"))
                return true
            }
            val target = Bukkit.getPlayer(args[0])
            if (target == null) {
                player.sendMessage(MSG.lang("vanish.player_not_found"))
                return true
            }
            toggleHideMe(target, player)
            return true
        }

        toggleHideMe(player, null)
        return true
    }

    private fun toggleHideMe(target: Player, executor: Player?) {
        // hideMe is a lobby-only feature — block it during a game (or when targeting a
        // player who is in a game), otherwise a hidden player breaks in-game visibility.
        if (VanishManager.isInGameContext(target)) {
            (executor ?: target).sendMessage(MSG.lang("vanish.hideme.lobby_only"))
            return
        }

        val currentState = VanishManager.isHideMe(target)
        val newState = !currentState

        VanishManager.setHideMe(target, newState)

        if (newState) {
            target.sendMessage(MSG.lang("vanish.hideme.enabled_self"))
            executor?.sendMessage(MSG.lang("vanish.hideme.enabled_other", "target" to target.name))
        } else {
            target.sendMessage(MSG.lang("vanish.hideme.disabled_self"))
            executor?.sendMessage(MSG.lang("vanish.hideme.disabled_other", "target" to target.name))
        }
    }
}
