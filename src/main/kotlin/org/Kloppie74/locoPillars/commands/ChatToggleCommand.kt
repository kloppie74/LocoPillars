package org.Kloppie74.locoPillars.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG

/**
 * /chat command — player-facing chat mode switcher.
 *
 *   /chat party          toggle party chat on/off
 *   /chat global         switch to global chat (turn off party/team chat)
 *   /chat team           toggle team chat (in-game only)
 *   /chat                show current mode
 */
class ChatToggleCommand(private val plugin: LocoPillars) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) { sender.sendMessage(MSG.lang("general.player_only")); return true }

        if (args.isEmpty()) {
            showCurrentMode(sender)
            return true
        }

        when (args[0].lowercase()) {
            "party", "p" -> {
                if (!sender.hasPermission("locopillars.party.use")) {
                    sender.sendMessage(MSG.lang("general.no_permission"))
                    return true
                }
                plugin.partyManager.togglePartyChat(sender)
            }
            "global", "g" -> {
                // Turn off party chat if on
                if (plugin.partyManager.isPartyChatActive(sender)) {
                    plugin.partyManager.togglePartyChat(sender)   // turn off
                    sender.sendMessage(MSG.lang("party.chat.switched_global"))
                } else {
                    sender.sendMessage(MSG.lang("party.chat.already_global"))
                }
            }
            "team", "t" -> {
                if (!sender.hasPermission("locopillars.chat.teamchat")) {
                    sender.sendMessage(MSG.lang("general.no_permission"))
                    return true
                }
                plugin.chatManager.toggleTeamChat(sender)
            }
            else -> {
                sender.sendMessage(MSG.lang("general.unknown_subcommand", "input" to args[0]))
                showCurrentMode(sender)
            }
        }
        return true
    }

    private fun showCurrentMode(player: Player) {
        val partyActive = plugin.partyManager.isPartyChatActive(player)
        val mode = when {
            partyActive -> MSG.lang("party.chat.mode_party")
            else        -> MSG.lang("party.chat.mode_global")
        }
        player.sendMessage(MSG.lang("party.chat.current_mode", "mode" to mode))
        if (plugin.partyManager.isInParty(player)) {
            player.sendMessage(MSG.lang("party.chat.mode_hint"))
        }
    }

    override fun onTabComplete(
        sender: CommandSender, command: Command, alias: String, args: Array<out String>
    ): List<String> {
        if (args.size != 1) return emptyList()
        return listOf("party", "global", "team")
            .filter { it.startsWith(args[0], ignoreCase = true) }
    }
}
