package org.Kloppie74.locoPillars.commands

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG

/**
 * /party command — full party system for LocoPillars.
 *
 * Subcommands:
 *   invite <player>        — invite a player (creates party if you have none)
 *   accept                 — accept a pending invite
 *   deny                   — deny a pending invite
 *   leave                  — leave your current party (disbands if you are leader)
 *   kick <player>          — kick a member (leader only)
 *   disband                — disband the party (leader only)
 *   transfer <player>      — transfer leadership (leader only)
 *   info / list            — show party members
 *   chat [message]         — toggle party chat, or send a one-off party message
 *   warp                   — warp all members to your location (leader only)
 *   help                   — show help
 */
class PartyCommand(private val plugin: LocoPillars) : CommandExecutor, TabCompleter {

    private val pm get() = plugin.partyManager

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) { sender.sendMessage(MSG.lang("general.player_only")); return true }
        if (!sender.hasPermission("locopillars.party.use")) {
            sender.sendMessage(MSG.lang("general.no_permission"))
            return true
        }

        if (args.isEmpty()) { sendHelp(sender); return true }

        when (args[0].lowercase()) {
            "invite"          -> handleInvite(sender, args)
            "accept"          -> pm.acceptInvite(sender)
            "deny", "decline" -> pm.denyInvite(sender)
            "leave"           -> pm.leaveParty(sender)
            "kick"            -> handleKick(sender, args)
            "disband"         -> pm.disbandParty(sender)
            "transfer"        -> handleTransfer(sender, args)
            "info", "list"    -> handleInfo(sender)
            "chat", "c"       -> handleChat(sender, args)
            "warp"            -> handleWarp(sender)
            "help"            -> sendHelp(sender)
            else -> {
                sender.sendMessage(MSG.lang("general.unknown_subcommand", "input" to args[0]))
                sendHelp(sender)
            }
        }
        return true
    }

    // =========================================================================
    // Invite
    // =========================================================================

    private fun handleInvite(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(MSG.lang("party.usage.invite"))
            return
        }
        val target = Bukkit.getPlayer(args[1])
        if (target == null || !target.isOnline) {
            player.sendMessage(MSG.lang("general.player_not_found"))
            return
        }
        pm.invite(player, target)
    }

    // =========================================================================
    // Kick
    // =========================================================================

    private fun handleKick(player: Player, args: Array<out String>) {
        if (args.size < 2) { player.sendMessage(MSG.lang("party.usage.kick")); return }
        val target = Bukkit.getPlayer(args[1])
        if (target == null || !target.isOnline) { player.sendMessage(MSG.lang("general.player_not_found")); return }
        pm.kickMember(player, target)
    }

    // =========================================================================
    // Transfer
    // =========================================================================

    private fun handleTransfer(player: Player, args: Array<out String>) {
        if (args.size < 2) { player.sendMessage(MSG.lang("party.usage.transfer")); return }
        val target = Bukkit.getPlayer(args[1])
        if (target == null || !target.isOnline) { player.sendMessage(MSG.lang("general.player_not_found")); return }
        pm.transferLeadership(player, target)
    }

    // =========================================================================
    // Info / List
    // =========================================================================

    private fun handleInfo(player: Player) {
        val party = pm.getParty(player)
        if (party == null) {
            player.sendMessage(MSG.lang("party.info.not_in_party"))
            return
        }
        val leader = Bukkit.getOfflinePlayer(party.leader).name ?: "Unknown"
        val limit  = pm.getPartyLimit(Bukkit.getPlayer(party.leader) ?: player)

        player.sendMessage(MSG.lang("party.info.header"))
        player.sendMessage(MSG.lang("party.info.leader", "leader" to leader))
        player.sendMessage(MSG.lang("party.info.size",
            "current" to party.size.toString(), "max" to limit.toString()))

        party.members.forEach { uuid ->
            val name   = Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()
            val online = Bukkit.getPlayer(uuid)?.isOnline == true
            val role   = if (uuid == party.leader) MSG.lang("party.info.role_leader") else MSG.lang("party.info.role_member")
            val status = if (online) MSG.lang("party.info.status_online") else MSG.lang("party.info.status_offline")
            player.sendMessage(MSG.lang("party.info.member_entry",
                "player" to name, "role" to role, "status" to status))
        }
    }

    // =========================================================================
    // Chat
    // =========================================================================

    private fun handleChat(player: Player, args: Array<out String>) {
        if (!pm.isInParty(player)) {
            player.sendMessage(MSG.lang("party.error.not_in_party"))
            return
        }
        if (args.size < 2) {
            // Toggle party chat mode
            pm.togglePartyChat(player)
        } else {
            // Send a one-off party message without toggling
            val message = args.drop(1).joinToString(" ")
            pm.sendPartyChat(player, message)
        }
    }

    // =========================================================================
    // Warp
    // =========================================================================

    private fun handleWarp(player: Player) {
        val party = pm.getParty(player)
        if (party == null) {
            player.sendMessage(MSG.lang("party.error.not_in_party"))
            return
        }
        if (party.leader != player.uniqueId) {
            player.sendMessage(MSG.lang("party.error.not_leader"))
            return
        }
        val members = pm.getOnlineMembers(party).filter { it.uniqueId != player.uniqueId }
        if (members.isEmpty()) {
            player.sendMessage(MSG.lang("party.warp.no_members"))
            return
        }
        val destination = player.location
        members.forEach { it.teleport(destination) }
        player.sendMessage(MSG.lang("party.warp.leader_success", "count" to members.size.toString()))
        members.forEach { it.sendMessage(MSG.lang("party.warp.member_warped", "leader" to player.name)) }
    }

    // =========================================================================
    // Help
    // =========================================================================

    private fun sendHelp(player: Player) {
        player.sendMessage(MSG.lang("party.help.header"))
        player.sendMessage(MSG.lang("party.help.invite"))
        player.sendMessage(MSG.lang("party.help.accept"))
        player.sendMessage(MSG.lang("party.help.deny"))
        player.sendMessage(MSG.lang("party.help.leave"))
        player.sendMessage(MSG.lang("party.help.kick"))
        player.sendMessage(MSG.lang("party.help.disband"))
        player.sendMessage(MSG.lang("party.help.transfer"))
        player.sendMessage(MSG.lang("party.help.info"))
        player.sendMessage(MSG.lang("party.help.chat"))
        player.sendMessage(MSG.lang("party.help.warp"))
        val limit = pm.getPartyLimit(player)
        player.sendMessage(MSG.lang("party.help.limit", "limit" to limit.toString()))
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    override fun onTabComplete(
        sender: CommandSender, command: Command, alias: String, args: Array<out String>
    ): List<String> {
        if (sender !is Player) return emptyList()
        val subs = listOf("invite", "accept", "deny", "leave", "kick",
            "disband", "transfer", "info", "list", "chat", "warp", "help")
        return when (args.size) {
            1 -> subs.filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> when (args[0].lowercase()) {
                "invite", "kick", "transfer" -> {
                    val party = pm.getParty(sender)
                    Bukkit.getOnlinePlayers()
                        .filter { it != sender }
                        .filter { target ->
                            // for kick/transfer: only list party members
                            if (args[0].lowercase() == "invite") true
                            else party?.isMember(target.uniqueId) == true
                        }
                        .map { it.name }
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
