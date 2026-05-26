package org.Kloppie74.locoPillars.cosmetics

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.cosmetics.engine.CosmeticType
import org.Kloppie74.locoPillars.utils.MSG

/**
 * /cosmetics — main entry point for the cosmetics system.
 *
 * Sub-commands:
 *   /cosmetics                    — open the select/equip GUI
 *   /cosmetics shop               — open the shop (all types)
 *   /cosmetics shop <type>        — open the shop for a specific type
 *   /cosmetics reload             — reload all cosmetics (admin)
 *   /cosmetics give <player> <id> <type> — grant a cosmetic (admin)
 *   /cosmetics coins <player>     — show coin balance (admin)
 *   /cosmetics coins give <player> <amount> — add coins (admin)
 */
class CosmeticsCommand(private val plugin: LocoPillars) : CommandExecutor, TabCompleter {

    private val manager   get() = plugin.cosmeticManager
    private val shopGui   get() = manager.shopGui
    private val selectGui get() = manager.selectGui

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player && args.firstOrNull()?.lowercase() !in listOf("reload", "give", "coins")) {
            sender.sendMessage("§cThis command can only be used by players.")
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {

            null, "select", "equip" -> {
                val player = sender as? Player ?: return true
                selectGui.openOverview(player)
            }

            "shop" -> {
                val player = sender as? Player ?: return true
                val typeArg = args.getOrNull(1)?.uppercase()
                val type = typeArg?.let { t ->
                    CosmeticType.values().firstOrNull { it.name == t || it.folderName.uppercase() == t }
                }
                shopGui.open(player, type)
            }

            "reload" -> {
                if (!sender.hasPermission("locopillars.admin")) {
                    sender.sendMessage(MSG.chatColors("&cNo permission."))
                    return true
                }
                manager.reload()
                sender.sendMessage(MSG.chatColors("&aCosmetics reloaded! Total: &f${manager.loader.count()}"))
            }

            "give" -> {
                if (!sender.hasPermission("locopillars.admin")) {
                    sender.sendMessage(MSG.chatColors("&cNo permission.")); return true
                }
                val targetName = args.getOrNull(1)
                val cosmeticId = args.getOrNull(2)
                val typeStr    = args.getOrNull(3)
                if (targetName == null || cosmeticId == null || typeStr == null) {
                    sender.sendMessage(MSG.chatColors("&cUsage: /cosmetics give <player> <cosmeticId> <type>"))
                    return true
                }
                val target = plugin.server.getPlayer(targetName)
                if (target == null) { sender.sendMessage(MSG.chatColors("&cPlayer '$targetName' not found.")); return true }
                val type = CosmeticType.values().firstOrNull { it.name.equals(typeStr, true) }
                if (type == null) { sender.sendMessage(MSG.chatColors("&cUnknown type. Valid: ${CosmeticType.values().joinToString()}")); return true }
                val success = manager.grant(target, cosmeticId, type)
                if (success) {
                    sender.sendMessage(MSG.chatColors("&aGranted cosmetic '&f$cosmeticId&a' (${type.displayName}) to ${target.name}"))
                    target.sendMessage(MSG.chatColors("&aYou received a new cosmetic: &f$cosmeticId &7(${type.displayName})"))
                } else {
                    sender.sendMessage(MSG.chatColors("&cFailed to grant cosmetic. Player may already own it or DB error."))
                }
            }

            "coins" -> {
                if (!sender.hasPermission("locopillars.admin")) {
                    // Non-admin: just show own balance
                    val player = sender as? Player ?: return true
                    val bal = manager.getCoins(player)
                    player.sendMessage(MSG.chatColors("&6Your coins: &e$bal"))
                    return true
                }
                when (args.getOrNull(1)?.lowercase()) {
                    "give" -> {
                        val targetName = args.getOrNull(2)
                        val amountStr  = args.getOrNull(3)
                        if (targetName == null || amountStr == null) {
                            sender.sendMessage(MSG.chatColors("&cUsage: /cosmetics coins give <player> <amount>")); return true
                        }
                        val target = plugin.server.getPlayer(targetName)
                        if (target == null) { sender.sendMessage(MSG.chatColors("&cPlayer not found.")); return true }
                        val amount = amountStr.toIntOrNull()
                        if (amount == null || amount <= 0) { sender.sendMessage(MSG.chatColors("&cInvalid amount.")); return true }
                        manager.addCoins(target, amount)
                        sender.sendMessage(MSG.chatColors("&aGave &e$amount &acoins to ${target.name}."))
                        target.sendMessage(MSG.chatColors("&aYou received &e$amount &acoins!"))
                    }
                    null -> {
                        val targetName = args.getOrNull(2) ?: run {
                            sender.sendMessage(MSG.chatColors("&cUsage: /cosmetics coins <player>")); return true
                        }
                        val target = plugin.server.getPlayer(targetName)
                        if (target == null) {
                            sender.sendMessage(MSG.chatColors("&cPlayer '$targetName' is not online.")); return true
                        }
                        val bal = manager.getCoins(target)
                        sender.sendMessage(MSG.chatColors("&6${target.name}'s coins: &e$bal"))
                    }
                    else -> sender.sendMessage(MSG.chatColors("&cUnknown sub-command."))
                }
            }

            else -> {
                sender.sendMessage(MSG.chatColors("&7Usage: /cosmetics [shop|select|give|coins|reload]"))
            }
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        return when (args.size) {
            1    -> listOf("shop", "select", "give", "coins", "reload").filter { it.startsWith(args[0], true) }
            2    -> when (args[0].lowercase()) {
                "shop"  -> CosmeticType.values().map { it.name.lowercase() }.filter { it.startsWith(args[1], true) }
                "give"  -> plugin.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[1], true) }
                "coins" -> listOf("give").filter { it.startsWith(args[1], true) }
                else    -> emptyList()
            }
            3    -> when {
                args[0].equals("give", true)  -> plugin.cosmeticManager.loader.getAll().map { it.id }.filter { it.startsWith(args[2], true) }
                args[0].equals("coins", true) && args[1].equals("give", true) -> plugin.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[2], true) }
                else -> emptyList()
            }
            4    -> when {
                args[0].equals("give", true)  -> CosmeticType.values().map { it.name.lowercase() }.filter { it.startsWith(args[3], true) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
