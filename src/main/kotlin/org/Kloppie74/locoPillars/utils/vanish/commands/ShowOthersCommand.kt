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
 * CHANGELOG (v2.0):
 * - Replaced VanishManager.getState() with VanishDAO.load()
 * - Replaced VanishManager.addShowOther() with VanishDAO.addShowOther()
 * - Replaced VanishManager.removeShowOther() with VanishDAO.removeShowOther()
 * - Added proper error handling for database operations
 * - Improved null-safety checks
 * - Better error messages for players
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
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

        try {
            // Load vanish state using DAO
            val state = VanishDAO.load(player.uniqueId)

            if (state == null) {
                player.sendMessage(MSG.chatColors("&cCould not load your vanish settings. Please try again."))
                return true
            }

            // Check if hideOthers is enabled
            if (!state.hideOthers) {
                player.sendMessage(MSG.chatColors("&cYou must enable hideOthers before you can show specific players."))
                return true
            }

            // Toggle showOthers for target player
            if (state.showOthers.contains(target.uniqueId)) {
                // Remove from showOthers list
                val removed = VanishDAO.removeShowOther(player.uniqueId, target.uniqueId)
                if (removed) {
                    player.sendMessage(MSG.chatColors("&cYou can no longer see &6${target.name}&c."))
                } else {
                    player.sendMessage(MSG.chatColors("&cFailed to update visibility for &6${target.name}&c. Please try again."))
                }
            } else {
                // Add to showOthers list
                val added = VanishDAO.addShowOther(player.uniqueId, target.uniqueId)
                if (added) {
                    player.sendMessage(MSG.chatColors("&aYou can now see &6${target.name}&a."))
                } else {
                    player.sendMessage(MSG.chatColors("&cFailed to update visibility for &6${target.name}&c. Please try again."))
                }
            }

        } catch (e: Exception) {
            Bukkit.getLogger().severe("[ShowOthersCommand] Error for ${player.name}: ${e.message}")
            e.printStackTrace()
            player.sendMessage(MSG.chatColors("&cAn error occurred while updating visibility. Please try again."))
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