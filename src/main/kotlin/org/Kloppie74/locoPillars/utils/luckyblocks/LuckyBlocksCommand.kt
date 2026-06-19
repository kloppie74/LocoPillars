package org.Kloppie74.locoPillars.utils.luckyblocks

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import org.Kloppie74.locoPillars.utils.luckyblocks.engine.LuckyblockLoader

class LuckyBlocksCommand(
    private val config: ConfigManager,
    private val loader: LuckyblockLoader
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players can use this command.")
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            null, "menu" -> {
                if (!sender.hasPermission("locopillars.luckyblocks.menu")) {
                    sender.sendMessage("§cNo permission.")
                    return true
                }
                LuckyBlocksMenu.openMenu(sender)
            }

            "get" -> {
                if (!sender.hasPermission("locopillars.luckyblocks.get")) {
                    sender.sendMessage("§cNo permission.")
                    return true
                }
                val item = LuckyBlocksUtil.getLuckyBlock(config, sender)
                sender.inventory.addItem(item)
                sender.sendMessage("§aYou received a LuckyBlock!")
            }

            "getblock" -> {
                if (!sender.hasPermission("locopillars.luckyblocks.get")) {
                    sender.sendMessage("§cNo permission.")
                    return true
                }
                val item = LuckyBlocksUtil.getLuckyBlock(config, sender)
                sender.inventory.addItem(item)
                sender.sendMessage("§aYou received a LuckyBlock!")
            }

            "give" -> {
                if (!sender.hasPermission("locopillars.luckyblocks.give")) {
                    sender.sendMessage("§cNo permission.")
                    return true
                }
                val targetName = args.getOrNull(1) ?: run {
                    sender.sendMessage("§cUsage: /luckyblocks give <player>")
                    return true
                }
                val target = Bukkit.getPlayer(targetName) ?: run {
                    sender.sendMessage("§cPlayer not found: $targetName")
                    return true
                }
                val item = LuckyBlocksUtil.getLuckyBlock(config, target, guaranteed = true)
                target.inventory.addItem(item)
                target.sendMessage("§aYou received a §6§lGuaranteed §aLuckyBlock from §e${sender.name}§a!")
                sender.sendMessage("§aGave a guaranteed LuckyBlock to §e${target.name}§a.")
            }

            "list" -> {
                if (!sender.hasPermission("locopillars.luckyblocks.list")) {
                    sender.sendMessage("§cNo permission.")
                    return true
                }
                val all = loader.getAll()
                sender.sendMessage("§6LuckyBlocks (${all.size}):")
                all.forEach { lb ->
                    sender.sendMessage("§7 - §e${lb.settings.id} §7— ${lb.settings.rarity.color}${lb.settings.rarity.name} §7— §f${lb.settings.displayName}")
                }
            }

            "info" -> {
                if (!sender.hasPermission("locopillars.luckyblocks.list")) {
                    sender.sendMessage("§cNo permission.")
                    return true
                }
                val id = args.getOrNull(1) ?: run {
                    sender.sendMessage("§cUsage: /luckyblocks info <id>")
                    return true
                }
                val lb = loader.getLuckyblock(id) ?: run {
                    sender.sendMessage("§cLuckyblock '$id' not found.")
                    return true
                }
                sender.sendMessage("§6§l${lb.settings.displayName}")
                sender.sendMessage("§7ID: §f${lb.settings.id}")
                sender.sendMessage("§7Rarity: ${lb.settings.rarity.color}${lb.settings.rarity.name}")
                sender.sendMessage("§7Description: §f${lb.settings.description}")
                sender.sendMessage("§7Script: §f${lb.scriptFile.path}")
            }

            "reload" -> {
                if (!sender.hasPermission("locopillars.luckyblocks.reload")) {
                    sender.sendMessage("§cNo permission.")
                    return true
                }
                loader.reload()
                sender.sendMessage("§aLuckyblocks reloaded — ${loader.count()} loaded.")
            }

            else -> sender.sendMessage("§cUsage: /luckyblocks [menu|get|give|list|info|reload]")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<String>): List<String> {
        if (args.size == 1) return listOf("menu", "get", "getblock", "give", "list", "info", "reload")
            .filter { it.startsWith(args[0].lowercase()) }
        if (args.size == 2 && args[0].equals("info", ignoreCase = true))
            return loader.getAll().map { it.settings.id }.filter { it.startsWith(args[1].lowercase()) }
        if (args.size == 2 && args[0].equals("give", ignoreCase = true))
            return Bukkit.getOnlinePlayers().map { it.name }.filter { it.startsWith(args[1]) }
        return emptyList()
    }
}
