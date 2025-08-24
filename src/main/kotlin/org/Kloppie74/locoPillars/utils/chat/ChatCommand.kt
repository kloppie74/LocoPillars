package org.Kloppie74.locoPillars.utils.chat.commands

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.chat.ChatUtils

class ChatCommand(private val plugin: LocoPillars, private val chatUtils: ChatUtils) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> handleReload(sender)
            "stats" -> handleStats(sender)
            "announce" -> handleAnnounce(sender, args)
            "clear" -> handleClear(sender, args)
            "spy" -> handleSpy(sender, args)
            "toggle" -> handleToggle(sender, args)
            "cooldown" -> handleCooldown(sender, args)
            "help" -> showHelp(sender)
            else -> {
                sender.sendMessage("§cUnknown subcommand. Use §e/chatadmin help §cfor available commands.")
            }
        }

        return true
    }

    private fun handleReload(sender: CommandSender) {
        if (!checkPermission(sender, "locopillars.chat.admin.reload")) return

        try {
            plugin.configManager.reloadConfig()
            plugin.chatManager.reload()
            sender.sendMessage("§aChat configuration reloaded successfully!")

            if (plugin.configManager.debugMode) {
                sender.sendMessage("§7Debug mode is currently: §eENABLED")
            }
        } catch (e: Exception) {
            sender.sendMessage("§cFailed to reload configuration: ${e.message}")
            plugin.logger.severe("[ChatCommand] Reload failed: ${e.message}")
        }
    }

    private fun handleStats(sender: CommandSender) {
        if (!checkPermission(sender, "locopillars.chat.admin.stats")) return

        val stats = chatUtils.getChatStats()

        sender.sendMessage("§6=== Chat System Statistics ===")
        sender.sendMessage("§7Players with active cooldown: §e${stats["players_with_cooldown"]}")
        sender.sendMessage("§7Active social spy users: §e${stats["social_spy_users"]}")
        sender.sendMessage("§7Active team chat users: §e${stats["team_chat_users"]}")
        sender.sendMessage("§7Chat enabled globally: §e${stats["chat_enabled"]}")
        sender.sendMessage("§7Social spy enabled: §e${stats["social_spy_enabled"]}")
        sender.sendMessage("§7Profanity filter enabled: §e${stats["profanity_filter_enabled"]}")
        sender.sendMessage("§7Chat cooldown: §e${stats["chat_cooldown_seconds"]}s")
        sender.sendMessage("§7Max message length: §e${stats["max_chat_length"]} characters")

        // Additional server statistics
        val onlinePlayers = Bukkit.getOnlinePlayers()
        val lobbyPlayers = onlinePlayers.count { plugin.gameManager.getPlayerInstance(it) == null }
        val gameInstances = plugin.gameManager.getActiveGames().size
        val totalInstances = plugin.gameManager.getAllInstances().size

        sender.sendMessage("§6=== Server Statistics ===")
        sender.sendMessage("§7Online players: §e${onlinePlayers.size}")
        sender.sendMessage("§7Lobby players: §e$lobbyPlayers")
        sender.sendMessage("§7Active games: §e$gameInstances")
        sender.sendMessage("§7Total game instances: §e$totalInstances")
    }

    private fun handleAnnounce(sender: CommandSender, args: Array<String>) {
        if (!checkPermission(sender, "locopillars.chat.admin.announce")) return

        if (args.size < 3) {
            sender.sendMessage("§cUsage: /chatadmin announce <target> <message>")
            sender.sendMessage("§7Targets: lobby, global, game:<id>")
            sender.sendMessage("§7Example: /chatadmin announce lobby Server restart in 5 minutes!")
            return
        }

        val target = args[1].lowercase()

        // Join all args from index 2 onwards as the message
        val message = args.drop(2).joinToString(" ")

        when {
            target == "lobby" -> {
                val lobbyPlayers = Bukkit.getOnlinePlayers().filter {
                    plugin.gameManager.getPlayerInstance(it) == null
                }

                if (lobbyPlayers.isEmpty()) {
                    sender.sendMessage("§cNo players in lobby to send announcement to.")
                    return
                }

                chatUtils.sendLobbyAnnouncement("§6[Announcement] §f$message")
                sender.sendMessage("§aAnnouncement sent to ${lobbyPlayers.size} lobby players.")
            }

            target == "global" -> {
                val onlinePlayers = Bukkit.getOnlinePlayers()

                if (onlinePlayers.isEmpty()) {
                    sender.sendMessage("§cNo players online to send announcement to.")
                    return
                }

                chatUtils.sendGlobalAnnouncement("§6[Announcement] §f$message")
                sender.sendMessage("§aAnnouncement sent to ${onlinePlayers.size} players.")
            }

            target.startsWith("game:") -> {
                val gameId = target.substringAfter("game:")
                try {
                    val uuid = java.util.UUID.fromString(gameId)
                    val instance = plugin.gameManager.getAllInstances().find { it.id == uuid }

                    if (instance == null) {
                        sender.sendMessage("§cGame instance not found: $gameId")
                        return
                    }

                    val gamePlayers = instance.players.mapNotNull { Bukkit.getPlayer(it) }

                    if (gamePlayers.isEmpty()) {
                        sender.sendMessage("§cNo players in game instance to send announcement to.")
                        return
                    }

                    chatUtils.sendGameAnnouncement(uuid, "§6[Announcement] §f$message")
                    sender.sendMessage("§aAnnouncement sent to ${gamePlayers.size} players in game.")

                } catch (e: IllegalArgumentException) {
                    sender.sendMessage("§cInvalid game ID format. Use a valid UUID.")
                }
            }

            else -> {
                sender.sendMessage("§cInvalid target. Use: lobby, global, or game:<uuid>")
                sender.sendMessage("§7To get game UUIDs, use: §e/pillars admin list")
            }
        }
    }

    private fun handleClear(sender: CommandSender, args: Array<String>) {
        if (!checkPermission(sender, "locopillars.chat.admin.clear")) return

        val target = if (args.size > 1) args[1].lowercase() else "global"
        val clearedBy = if (sender is Player) sender.name else "Console"

        when (target) {
            "global" -> {
                val onlinePlayers = Bukkit.getOnlinePlayers()

                if (onlinePlayers.isEmpty()) {
                    sender.sendMessage("§cNo players online to clear chat for.")
                    return
                }

                repeat(100) {
                    onlinePlayers.forEach { it.sendMessage(" ") }
                }

                chatUtils.sendGlobalAnnouncement("§eChat has been cleared by $clearedBy")
                sender.sendMessage("§aChat cleared for ${onlinePlayers.size} players.")
            }

            "lobby" -> {
                val lobbyPlayers = Bukkit.getOnlinePlayers().filter {
                    plugin.gameManager.getPlayerInstance(it) == null
                }

                if (lobbyPlayers.isEmpty()) {
                    sender.sendMessage("§cNo players in lobby to clear chat for.")
                    return
                }

                repeat(100) {
                    lobbyPlayers.forEach { it.sendMessage(" ") }
                }

                chatUtils.sendLobbyAnnouncement("§eLobby chat cleared by $clearedBy")
                sender.sendMessage("§aLobby chat cleared for ${lobbyPlayers.size} players.")
            }

            else -> {
                sender.sendMessage("§cInvalid target. Use: global or lobby")
            }
        }
    }

    private fun handleSpy(sender: CommandSender, args: Array<String>) {
        if (!checkPermission(sender, "locopillars.chat.socialspy")) return

        if (sender !is Player) {
            sender.sendMessage("§cThis command can only be used by players.")
            return
        }

        val enabled = chatUtils.toggleSocialSpy(sender)
        val status = if (enabled) "§aenabled" else "§cdisabled"
        sender.sendMessage("§7Social spy $status.")

        if (enabled) {
            sender.sendMessage("§7You will now see all chat messages from other players.")
        } else {
            sender.sendMessage("§7You will no longer see chat messages from other players.")
        }
    }

    /**
     * Toggle features: chat, socialspy, profanity, logging
     * Usage: /chatadmin toggle <feature>
     * For global toggles, all args are valid: [toggle, <feature>]
     */
    private fun handleToggle(sender: CommandSender, args: Array<String>) {
        if (!checkPermission(sender, "locopillars.chat.admin.toggle")) return

        if (args.size < 2) {
            sender.sendMessage("§cUsage: /chatadmin toggle <feature>")
            sender.sendMessage("§7Features: chat, socialspy, profanity, logging")
            return
        }

        val feature = args[1].lowercase()
        when (feature) {
            "chat", "global" -> {
                val currentState = plugin.configManager.chatEnabled
                val newState = !currentState
                plugin.configManager.chatEnabled = newState
                plugin.configManager.saveConfig()
                sender.sendMessage("§aGlobal chat has been §e${if (newState) "ENABLED" else "DISABLED"}§a.")
            }
            "socialspy" -> {
                val currentState = plugin.configManager.socialSpyEnabled
                val newState = !currentState
                plugin.configManager.socialSpyEnabled = newState
                plugin.configManager.saveConfig()
                sender.sendMessage("§aSocial spy has been §e${if (newState) "ENABLED" else "DISABLED"}§a.")
            }
            "profanity" -> {
                val currentState = plugin.configManager.profanityFilterEnabled
                val newState = !currentState
                plugin.configManager.profanityFilterEnabled = newState
                plugin.configManager.saveConfig()
                sender.sendMessage("§aProfanity filter has been §e${if (newState) "ENABLED" else "DISABLED"}§a.")
            }
            "logging" -> {
                val currentState = plugin.configManager.chatLogEnabled
                val newState = !currentState
                plugin.configManager.chatLogEnabled = newState
                plugin.configManager.saveConfig()
                sender.sendMessage("§aChat logging has been §e${if (newState) "ENABLED" else "DISABLED"}§a.")
            }
            else -> {
                sender.sendMessage("§cUnknown feature. Available: chat, socialspy, profanity, logging")
            }
        }
    }

    private fun handleCooldown(sender: CommandSender, args: Array<String>) {
        if (!checkPermission(sender, "locopillars.chat.admin.cooldown")) return

        if (args.size < 2) {
            sender.sendMessage("§cUsage: /chatadmin cooldown <player> [seconds]")
            sender.sendMessage("§7Use without seconds to check current cooldown")
            return
        }

        val playerName = args[1]
        val targetPlayer = Bukkit.getPlayer(playerName)

        if (targetPlayer == null) {
            sender.sendMessage("§cPlayer not found: $playerName")
            return
        }

        if (args.size == 2) {
            // Show current cooldown
            val remainingCooldown = chatUtils.getCooldownRemaining(targetPlayer)
            if (remainingCooldown > 0) {
                sender.sendMessage("§7Player §e${targetPlayer.name} §7has §e${remainingCooldown}s §7cooldown remaining.")
            } else {
                sender.sendMessage("§7Player §e${targetPlayer.name} §7has no active cooldown.")
            }
            return
        }

        if (args.size >= 3) {
            try {
                val seconds = args[2].toInt()
                if (seconds < 0) {
                    sender.sendMessage("§cCooldown must be 0 or positive.")
                    return
                }

                // Set player cooldown (not implemented yet)
                sender.sendMessage("§cIndividual player cooldown setting is not yet implemented.")
                sender.sendMessage("§7Current global cooldown: §e${plugin.configManager.chatCooldownSeconds}s")
                sender.sendMessage("§7Player §e${targetPlayer.name} §7current cooldown: §e${chatUtils.getCooldownRemaining(targetPlayer)}s")

            } catch (e: NumberFormatException) {
                sender.sendMessage("§cInvalid number: ${args[2]}")
            }
        }
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("§6=== Chat Admin Commands ===")
        sender.sendMessage("§e/chatadmin reload §7- Reload chat configuration from config.yml")
        sender.sendMessage("§e/chatadmin stats §7- Show detailed chat and server statistics")
        sender.sendMessage("§e/chatadmin announce <target> <message> §7- Send announcement to players")
        sender.sendMessage("§7  §8• §7Targets: §elobby §7(lobby players), §eglobal §7(all players), §egame:<uuid> §7(game instance)")
        sender.sendMessage("§7  §8• §7Example: §e/chatadmin announce global Welcome to the server!")
        sender.sendMessage("§e/chatadmin clear [target] §7- Clear chat messages")
        sender.sendMessage("§7  §8• §7Targets: §eglobal §7(all players), §elobby §7(lobby only)")
        sender.sendMessage("§e/chatadmin spy §7- Toggle social spy for yourself (players only)")
        sender.sendMessage("§e/chatadmin toggle <feature> §7- Toggle chat features")
        sender.sendMessage("§7  §8• §7Features: §echat§7, §esocialspy§7, §eprofanity§7, §elogging")
        sender.sendMessage("§e/chatadmin cooldown <player> [seconds] §7- Check/manage player cooldown")
        sender.sendMessage("§e/chatadmin help §7- Show this help message")

        // Show current status
        sender.sendMessage("§7")
        sender.sendMessage("§b=== Current Status ===")
        sender.sendMessage("§7Chat enabled: §e${plugin.configManager.chatEnabled}")
        sender.sendMessage("§7Social spy enabled: §e${plugin.configManager.socialSpyEnabled}")
        sender.sendMessage("§7Chat cooldown: §e${plugin.configManager.chatCooldownSeconds}s")
        sender.sendMessage("§7Online players: §e${Bukkit.getOnlinePlayers().size}")

    }

    private fun checkPermission(sender: CommandSender, permission: String): Boolean {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage("§cYou don't have permission to use this command.")
            sender.sendMessage("§7Required permission: §e$permission")
            return false
        }
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("reload", "stats", "announce", "clear", "spy", "toggle", "cooldown", "help")
                .filter { it.startsWith(args[0].lowercase()) }
        }

        if (args.size == 2) {
            when (args[0].lowercase()) {
                "announce" -> {
                    val suggestions = mutableListOf("lobby", "global")

                    // Add game instances if available
                    if (sender.hasPermission("locopillars.chat.admin.announce")) {
                        plugin.gameManager.getActiveGames().forEach { instance ->
                            suggestions.add("game:${instance.id}")
                        }
                    }

                    return suggestions.filter { it.startsWith(args[1].lowercase()) }
                }

                "clear" -> {
                    return listOf("global", "lobby")
                        .filter { it.startsWith(args[1].lowercase()) }
                }

                "toggle" -> {
                    return listOf("chat", "global", "socialspy", "profanity", "logging")
                        .filter { it.startsWith(args[1].lowercase()) }
                }

                "cooldown" -> {
                    return Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[1].lowercase()) }
                }
            }
        }

        if (args.size == 3) {
            when (args[0].lowercase()) {
                "cooldown" -> {
                    return listOf("0", "5", "10", "30", "60")
                        .filter { it.startsWith(args[2]) }
                }

                "announce" -> {
                    // Tab completion for announce messages
                    return listOf("Welcome!", "Server restart in 5 minutes!", "New event starting!", "Maintenance starting soon!")
                        .filter { it.lowercase().startsWith(args[2].lowercase()) }
                }
            }
        }

        return emptyList()
    }
}