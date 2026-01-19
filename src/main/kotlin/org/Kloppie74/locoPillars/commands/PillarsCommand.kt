package org.Kloppie74.locoPillars.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.commands.admin.MapAdminCommand
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.MSG

class PillarsCommand(private val plugin: LocoPillars) : CommandExecutor, TabCompleter {

    private val adminSubCmds = listOf("map")
    private val adminPerm = "locopillars.admin"
    
    // Map admin command handler
    private val mapAdminCommand: MapAdminCommand by lazy {
        MapAdminCommand(plugin.mapRepository, plugin.editorSessionManager, plugin.mapEditorMenu)
    }

    private val subPermissions = mapOf(
        "join" to "locopillars.pillars.join",
        "leave" to "locopillars.pillars.leave",
        "list" to "locopillars.pillars.list",
        "status" to "locopillars.pillars.status",
        "stats" to "locopillars.pillars.stats",
        "help" to "locopillars.pillars.help"
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(MSG.chatColors("&cThis command can only be used by players!"))
            return true
        }

        // --- ADMIN COMMANDS ---
        if (args.isNotEmpty() && args[0].equals("admin", ignoreCase = true)) {
            if (!sender.hasPermission(adminPerm)) {
                sender.sendMessage(MSG.chatColors("&cYou don't have permission to use /pillars admin!"))
                return true
            }
            if (args.size < 2) {
                sender.sendMessage(MSG.chatColors("&e/pillars admin map <subcommand>"))
                return true
            }
            when (args[1].lowercase()) {
                "map" -> {
                    // Route to MapAdminCommand
                    val mapArgs = args.drop(2).toTypedArray()
                    return mapAdminCommand.onCommand(sender, command, label, mapArgs)
                }
                else -> {
                    sender.sendMessage(MSG.chatColors("&cUnknown admin subcommand: ${args[1]}"))
                    sender.sendMessage(MSG.chatColors("&e/pillars admin map <subcommand>"))
                    return true
                }
            }
        }

        // --- NORMAL PLAYER COMMANDS ---
        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        val sub = args[0].lowercase()
        val perm = subPermissions[sub] ?: ""
        if (perm.isNotEmpty() && !sender.hasPermission(perm)) {
            sender.sendMessage(MSG.chatColors("&cYou don't have permission to use /pillars $sub!"))
            return true
        }

        when (sub) {
            "join" -> {
                if (args.size < 2) {
                    sender.sendMessage(MSG.chatColors("&cUsage: /pillars join <gametype>"))
                    sender.sendMessage(MSG.chatColors("&eAvailable game types:"))
                    plugin.configs.forEach { config ->
                        sender.sendMessage(MSG.chatColors("&7- ${config.name} (${config.getDisplayName()})"))
                    }
                    sender.sendMessage(MSG.chatColors("&7Or use &b/pillars join random &7to join any available game"))
                    return true
                }

                val gameType = args[1]
                if (gameType.equals("random", ignoreCase = true)) {
                    joinRandomGame(sender)
                } else {
                    joinGame(sender, gameType)
                }
                return true
            }

            "leave" -> {
                leaveGame(sender)
                return true
            }

            "list" -> {
                listGames(sender)
                return true
            }

            "status" -> {
                showStatus(sender)
                return true
            }

            "stats" -> {
                showStats(sender)
                return true
            }

            "help" -> {
                showHelp(sender)
                return true
            }

            else -> {
                sender.sendMessage(MSG.chatColors("&cUnknown subcommand: &f${args[0]}"))
                showHelp(sender)
                return true
            }
        }
    }

    private fun joinRandomGame(player: Player) {
        val currentInstance = plugin.gameManager.getPlayerInstance(player)
        if (currentInstance != null) {
            player.sendMessage(MSG.chatColors("&cYou are already in a game! Use &e/pillars leave &cto leave your current game."))
            return
        }

        val joinable: List<GameInstance> = plugin.gameManager.getAllInstances().filter {
            it.canJoin()
        }

        if (joinable.isEmpty()) {
            player.sendMessage(MSG.chatColors("&cNo available games to join right now!"))
            return
        }

        val sorted = joinable.sortedWith(compareByDescending<GameInstance> { it.players.size }
            .thenBy { it.getMinPlayers() })

        val chosen = sorted.firstOrNull()
        if (chosen == null) {
            player.sendMessage(MSG.chatColors("&cNo suitable game found!"))
            return
        }

        val joinedInstance = plugin.gameManager.joinGame(player, chosen.arenaInstance.template.name)
        if (joinedInstance == null) {
            player.sendMessage(MSG.chatColors("&cFailed to join game. Game may be full."))
            return
        }

        player.sendMessage(MSG.chatColors("&aYou have joined a random game: &e${joinedInstance.getDisplayName()}"))
        player.sendMessage(MSG.chatColors("&ePlayers: &b${joinedInstance.players.size}&7/&b${joinedInstance.getMaxPlayers()}"))
        if (joinedInstance.players.size >= joinedInstance.getMinPlayers()) {
            player.sendMessage(MSG.chatColors("&aGame will start soon!"))
        } else {
            val needed = joinedInstance.getMinPlayers() - joinedInstance.players.size
            player.sendMessage(MSG.chatColors("&eWaiting for &c$needed &emore player(s) to start..."))
        }
    }

    private fun joinGame(player: Player, gameType: String) {
        val currentInstance = plugin.gameManager.getPlayerInstance(player)
        if (currentInstance != null) {
            player.sendMessage(MSG.chatColors("&cYou are already in a game! Use &e/pillars leave &cto leave your current game."))
            return
        }

        val config = plugin.configs.firstOrNull { it.name.equals(gameType, ignoreCase = true) }
        if (config == null) {
            player.sendMessage(MSG.chatColors("&cGame type '&f$gameType&c' not found!"))
            player.sendMessage(MSG.chatColors("&eAvailable game types:"))
            plugin.configs.forEach { availableConfig ->
                player.sendMessage(MSG.chatColors("&7- ${availableConfig.name} (${availableConfig.getDisplayName()})"))
            }
            return
        }

        val instance = plugin.gameManager.joinGame(player, config.name)
        if (instance == null) {
            player.sendMessage(MSG.chatColors("&cFailed to join game. No available instances or game is full."))
            return
        }

        player.sendMessage(MSG.chatColors("&aSuccessfully joined &e${config.getDisplayName()}&a!"))
        player.sendMessage(MSG.chatColors("&ePlayers: &b${instance.players.size}&7/&b${instance.getMaxPlayers()}"))

        if (instance.players.size >= instance.getMinPlayers()) {
            player.sendMessage(MSG.chatColors("&aGame will start soon!"))
        } else {
            val needed = instance.getMinPlayers() - instance.players.size
            player.sendMessage(MSG.chatColors("&eWaiting for &c$needed &emore player(s) to start..."))
        }
    }

    private fun leaveGame(player: Player) {
        val success = plugin.gameManager.leaveGame(player)
        if (!success) {
            player.sendMessage(MSG.chatColors("&cYou are not in a game!"))
        }
    }

    private fun listGames(player: Player) {
        player.sendMessage(MSG.chatColors("&6=== Available Game Types ==="))

        if (plugin.configs.isEmpty()) {
            player.sendMessage(MSG.chatColors("&cNo game types available!"))
            return
        }

        plugin.configs.forEach { config ->
            val instances = plugin.gameManager.getInstancesByGameType(config.name)
            val availableInstances = instances.count { it.canJoin() }
            val activeGames = instances.count { it.state == GameState.ACTIVE }

            player.sendMessage(MSG.chatColors("&e${config.name} &7(${config.getDisplayName()})"))
            player.sendMessage(MSG.chatColors("&7  Available: &b$availableInstances &7| Active: &b$activeGames"))
            player.sendMessage(MSG.chatColors("&7  Players: &b${config.getMinPlayers()}-${config.getMaxPlayers()} &7| Teams: &b${config.getMaxTeams()}"))
        }

        player.sendMessage(MSG.chatColors("&7Use &e/pillars join <gametype> &7to join a game!"))
        player.sendMessage(MSG.chatColors("&7Or use &b/pillars join random &7to join any available game"))
    }

    private fun showStatus(player: Player) {
        val currentInstance = plugin.gameManager.getPlayerInstance(player)

        if (currentInstance == null) {
            player.sendMessage(MSG.chatColors("&eYou are not currently in a game."))
            player.sendMessage(MSG.chatColors("&7Use &e/pillars list &7to see available games!"))
            return
        }

        player.sendMessage(MSG.chatColors("&6=== Game Status ==="))
        player.sendMessage(MSG.chatColors("&eGame: &b${currentInstance.getDisplayName()}"))
        player.sendMessage(MSG.chatColors("&eState: &b${currentInstance.state}"))
        player.sendMessage(MSG.chatColors("&ePlayers: &b${currentInstance.players.size}&7/&b${currentInstance.getMaxPlayers()}"))
        player.sendMessage(MSG.chatColors("&eWorld: &b${currentInstance.arenaInstance.world.name}"))
        player.sendMessage(MSG.chatColors("&eInstance ID: &b${currentInstance.id}"))

        when (currentInstance.state) {
            GameState.WAITING -> {
                val needed = currentInstance.getMinPlayers() - currentInstance.players.size
                if (needed > 0) {
                    player.sendMessage(MSG.chatColors("&7Waiting for &c$needed &7more player(s) to start..."))
                } else {
                    player.sendMessage(MSG.chatColors("&aReady to start!"))
                }
            }
            GameState.STARTING -> {
                player.sendMessage(MSG.chatColors("&aGame is starting..."))
            }
            GameState.ACTIVE -> {
                player.sendMessage(MSG.chatColors("&cGame is active! Fight!"))
            }
            GameState.ENDED -> {
                player.sendMessage(MSG.chatColors("&eGame has ended."))
            }
            GameState.RESETTING -> {
                player.sendMessage(MSG.chatColors("&7Game is resetting..."))
            }
        }
    }

    private fun showStats(player: Player) {
        player.sendMessage(MSG.chatColors("&6=== Server Statistics ==="))

        val totalInstances = plugin.gameManager.getAllInstances().size
        val availableInstances = plugin.gameManager.getAvailableInstances().size
        val activeGames = plugin.gameManager.getActiveGames().size
        val totalWorlds = plugin.worldManager.getAllArenaWorlds().size

        player.sendMessage(MSG.chatColors("&eTotal Instances: &b$totalInstances"))
        player.sendMessage(MSG.chatColors("&eAvailable Instances: &b$availableInstances"))
        player.sendMessage(MSG.chatColors("&eActive Games: &b$activeGames"))
        player.sendMessage(MSG.chatColors("&eArena Worlds: &b$totalWorlds"))
        player.sendMessage(MSG.chatColors("&eGame Types: &b${plugin.configs.size}"))

        plugin.configs.forEach { config ->
            val instances = plugin.gameManager.getInstancesByGameType(config.name)
            val available = instances.count { it.canJoin() }
            val active = instances.count { it.state == GameState.ACTIVE }
            val totalPlayers = instances.sumOf { it.players.size }

            player.sendMessage(MSG.chatColors("&7${config.name}: &b$available &7available, &b$active &7active, &b$totalPlayers &7players"))
        }
    }

    private fun showHelp(player: Player) {
        player.sendMessage(MSG.chatColors("&6=== Pillars of Fortune Commands ==="))
        if (player.hasPermission(subPermissions["join"]!!)) {
            player.sendMessage(MSG.chatColors("&e/pillars join <gametype> &7- Join a game"))
            player.sendMessage(MSG.chatColors("&e/pillars join random &7- Join any available game"))
        }
        if (player.hasPermission(subPermissions["leave"]!!)) {
            player.sendMessage(MSG.chatColors("&e/pillars leave &7- Leave your current game"))
        }
        if (player.hasPermission(subPermissions["list"]!!)) {
            player.sendMessage(MSG.chatColors("&e/pillars list &7- List available game types"))
        }
        if (player.hasPermission(subPermissions["status"]!!)) {
            player.sendMessage(MSG.chatColors("&e/pillars status &7- Show your current game status"))
        }
        if (player.hasPermission(subPermissions["stats"]!!)) {
            player.sendMessage(MSG.chatColors("&e/pillars stats &7- Show server statistics"))
        }
        if (player.hasPermission(subPermissions["help"]!!)) {
            player.sendMessage(MSG.chatColors("&e/pillars help &7- Show this help message"))
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (sender !is Player) return emptyList()

        // --- ADMIN TAB-COMPLETE ---
        if (args.isNotEmpty() && args[0].equals("admin", ignoreCase = true) && sender.hasPermission(adminPerm)) {
            return when (args.size) {
                2 -> adminSubCmds.filter { it.startsWith(args[1], ignoreCase = true) }
                3 -> {
                    when (args[1].lowercase()) {
                        "map" -> {
                            // Route to MapAdminCommand tab completion
                            val mapArgs = args.drop(2).toTypedArray()
                            mapAdminCommand.onTabComplete(sender, command, alias, mapArgs) ?: emptyList()
                        }
                        else -> emptyList()
                    }
                }
                else -> {
                    // For args size 4+, check if it's a map subcommand
                    if (args[1].lowercase() == "map") {
                        val mapArgs = args.drop(2).toTypedArray()
                        mapAdminCommand.onTabComplete(sender, command, alias, mapArgs) ?: emptyList()
                    } else {
                        emptyList()
                    }
                }
            }
        }

        // Only show subcommands player has permission for
        val availableSubCmds = subPermissions.filter { sender.hasPermission(it.value) }.keys.toList()

        return when (args.size) {
            1 -> {
                availableSubCmds.filter { it.startsWith(args[0], ignoreCase = true) }
            }
            2 -> {
                if (args[0].equals("join", ignoreCase = true) && sender.hasPermission(subPermissions["join"]!!)) {
                    val types = plugin.configs.map { it.name } + "random"
                    types.filter { it.startsWith(args[1], ignoreCase = true) }
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }
}
