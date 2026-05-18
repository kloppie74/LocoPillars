package org.Kloppie74.locoPillars.utils.vanish.commands

import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.database.dao.VanishDAO
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Command to show specific players when hideOthers is enabled.
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
class ShowOthersCommand : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage(MSG.lang("vanish.only_players"))
            return true
        }

        if (args.isEmpty()) {
            player.sendMessage(MSG.lang("vanish.showothers.usage"))
            return true
        }

        val target = Bukkit.getPlayer(args[0])
        if (target == null) {
            player.sendMessage(MSG.lang("vanish.player_not_found"))
            return true
        }

        try {
            val state = VanishDAO.load(player.uniqueId)

            if (state == null) {
                player.sendMessage(MSG.lang("vanish.showothers.load_error"))
                return true
            }

            if (!state.hideOthers) {
                player.sendMessage(MSG.lang("vanish.showothers.require_hideothers"))
                return true
            }

            if (state.showOthers.contains(target.uniqueId)) {
                val removed = VanishDAO.removeShowOther(player.uniqueId, target.uniqueId)
                if (removed) {
                    player.sendMessage(MSG.lang("vanish.showothers.now_hidden", "target" to target.name))
                } else {
                    player.sendMessage(MSG.lang("vanish.showothers.update_failed", "target" to target.name))
                }
            } else {
                val added = VanishDAO.addShowOther(player.uniqueId, target.uniqueId)
                if (added) {
                    player.sendMessage(MSG.lang("vanish.showothers.now_visible", "target" to target.name))
                } else {
                    player.sendMessage(MSG.lang("vanish.showothers.update_failed", "target" to target.name))
                }
            }

        } catch (e: Exception) {
            Bukkit.getLogger().severe("[ShowOthersCommand] Error for ${player.name}: ${e.message}")
            e.printStackTrace()
            player.sendMessage(MSG.lang("vanish.showothers.generic_error"))
        }

        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        val player = sender as? Player ?: return emptyList()

        if (args.size == 1) {
            val partial = args[0].lowercase()
            return Bukkit.getOnlinePlayers()
                .filter { it != player && it.name.lowercase().startsWith(partial) }
                .map { it.name }
                .sorted()
        }

        return emptyList()
    }
}
