package org.Kloppie74.locoPillars.commands

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.data.GameState

class PillarsCommand(private val plugin: LocoPillars) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("${ChatColor.RED}This command can only be used by players!")
            return true
        }

        when {
            args.isEmpty() -> {
                showHelp(sender)
                return true
            }

            args[0].equals("join", ignoreCase = true) -> {
                if (args.size < 2) {
                    sender.sendMessage("${ChatColor.RED}Usage: /pillars join <gametype>")
                    sender.sendMessage("${ChatColor.YELLOW}Available game types:")
                    plugin.configs.forEach { config ->
                        sender.sendMessage("${ChatColor.GRAY}- ${config.name} (${config.getDisplayName()})")
                    }
                    return true
                }

                val gameType = args[1]
                joinGame(sender, gameType)
                return true
            }

            args[0].equals("leave", ignoreCase = true) -> {
                leaveGame(sender)
                return true
            }

            args[0].equals("list", ignoreCase = true) -> {
                listGames(sender)
                return true
            }

            args[0].equals("status", ignoreCase = true) -> {
                showStatus(sender)
                return true
            }

            args[0].equals("stats", ignoreCase = true) -> {
                showStats(sender)
                return true
            }

            args[0].equals("help", ignoreCase = true) -> {
                showHelp(sender)
                return true
            }

            else -> {
                sender.sendMessage("${ChatColor.RED}Unknown subcommand: ${args[0]}")
                showHelp(sender)
                return true
            }
        }
    }

    private fun joinGame(player: Player, gameType: String) {
        // Check if player is already in a game
        val currentInstance = plugin.gameManager.getPlayerInstance(player)
        if (currentInstance != null) {
            player.sendMessage("${ChatColor.RED}You are already in a game! Use /pillars leave to leave your current game.")
            return
        }

        // Check if game type exists
        val config = plugin.configs.firstOrNull { it.name.equals(gameType, ignoreCase = true) }
        if (config == null) {
            player.sendMessage("${ChatColor.RED}Game type '$gameType' not found!")
            player.sendMessage("${ChatColor.YELLOW}Available game types:")
            plugin.configs.forEach { availableConfig ->
                player.sendMessage("${ChatColor.GRAY}- ${availableConfig.name} (${availableConfig.getDisplayName()})")
            }
            return
        }

        // Try to join the game
        val instance = plugin.gameManager.joinGame(player, config.name)
        if (instance == null) {
            player.sendMessage("${ChatColor.RED}Failed to join game. No available instances or game is full.")
            return
        }

        player.sendMessage("${ChatColor.GREEN}Successfully joined ${config.getDisplayName()}!")
        player.sendMessage("${ChatColor.YELLOW}Players: ${instance.players.size}/${instance.getMaxPlayers()}")

        if (instance.players.size >= instance.getMinPlayers()) {
            player.sendMessage("${ChatColor.GREEN}Game will start soon!")
        } else {
            val needed = instance.getMinPlayers() - instance.players.size
            player.sendMessage("${ChatColor.YELLOW}Waiting for $needed more player(s) to start...")
        }
    }

    private fun leaveGame(player: Player) {
        val success = plugin.gameManager.leaveGame(player)
        if (!success) {
            player.sendMessage("${ChatColor.RED}You are not in a game!")
        }
        // Success message is handled in GameManager
    }

    private fun listGames(player: Player) {
        player.sendMessage("${ChatColor.GOLD}=== Available Game Types ===")

        if (plugin.configs.isEmpty()) {
            player.sendMessage("${ChatColor.RED}No game types available!")
            return
        }

        plugin.configs.forEach { config ->
            val instances = plugin.gameManager.getInstancesByGameType(config.name)
            val availableInstances = instances.count { it.canJoin() }
            val activeGames = instances.count { it.state == GameState.ACTIVE }

            player.sendMessage("${ChatColor.YELLOW}${config.name} (${config.getDisplayName()})")
            player.sendMessage("  ${ChatColor.GRAY}Available: $availableInstances | Active: $activeGames")
            player.sendMessage("  ${ChatColor.GRAY}Players: ${config.getMinPlayers()}-${config.getMaxPlayers()} | Teams: ${config.getMaxTeams()}")
        }

        player.sendMessage("${ChatColor.GRAY}Use /pillars join <gametype> to join a game!")
    }

    private fun showStatus(player: Player) {
        val currentInstance = plugin.gameManager.getPlayerInstance(player)

        if (currentInstance == null) {
            player.sendMessage("${ChatColor.YELLOW}You are not currently in a game.")
            player.sendMessage("${ChatColor.GRAY}Use /pillars list to see available games!")
            return
        }

        player.sendMessage("${ChatColor.GOLD}=== Game Status ===")
        player.sendMessage("${ChatColor.YELLOW}Game: ${currentInstance.getDisplayName()}")
        player.sendMessage("${ChatColor.YELLOW}State: ${currentInstance.state}")
        player.sendMessage("${ChatColor.YELLOW}Players: ${currentInstance.players.size}/${currentInstance.getMaxPlayers()}")
        player.sendMessage("${ChatColor.YELLOW}World: ${currentInstance.arenaInstance.world.name}")
        player.sendMessage("${ChatColor.YELLOW}Instance ID: ${currentInstance.id}")

        when (currentInstance.state) {
            GameState.WAITING -> {
                val needed = currentInstance.getMinPlayers() - currentInstance.players.size
                if (needed > 0) {
                    player.sendMessage("${ChatColor.GRAY}Waiting for $needed more player(s) to start...")
                } else {
                    player.sendMessage("${ChatColor.GREEN}Ready to start!")
                }
            }
            GameState.STARTING -> {
                player.sendMessage("${ChatColor.GREEN}Game is starting...")
            }
            GameState.ACTIVE -> {
                player.sendMessage("${ChatColor.RED}Game is active! Fight!")
            }
            GameState.ENDED -> {
                player.sendMessage("${ChatColor.YELLOW}Game has ended.")
            }
            GameState.RESETTING -> {
                player.sendMessage("${ChatColor.GRAY}Game is resetting...")
            }
        }
    }

    private fun showStats(player: Player) {
        player.sendMessage("${ChatColor.GOLD}=== Server Statistics ===")

        val totalInstances = plugin.gameManager.getAllInstances().size
        val availableInstances = plugin.gameManager.getAvailableInstances().size
        val activeGames = plugin.gameManager.getActiveGames().size
        val totalWorlds = plugin.worldManager.getAllArenaWorlds().size

        player.sendMessage("${ChatColor.YELLOW}Total Instances: $totalInstances")
        player.sendMessage("${ChatColor.YELLOW}Available Instances: $availableInstances")
        player.sendMessage("${ChatColor.YELLOW}Active Games: $activeGames")
        player.sendMessage("${ChatColor.YELLOW}Arena Worlds: $totalWorlds")
        player.sendMessage("${ChatColor.YELLOW}Game Types: ${plugin.configs.size}")

        // Show per-game-type stats
        plugin.configs.forEach { config ->
            val instances = plugin.gameManager.getInstancesByGameType(config.name)
            val available = instances.count { it.canJoin() }
            val active = instances.count { it.state == GameState.ACTIVE }
            val totalPlayers = instances.sumOf { it.players.size }

            player.sendMessage("${ChatColor.GRAY}${config.name}: $available available, $active active, $totalPlayers players")
        }
    }

    private fun showHelp(player: Player) {
        player.sendMessage("${ChatColor.GOLD}=== Pillars of Fortune Commands ===")
        player.sendMessage("${ChatColor.YELLOW}/pillars join <gametype> ${ChatColor.GRAY}- Join a game")
        player.sendMessage("${ChatColor.YELLOW}/pillars leave ${ChatColor.GRAY}- Leave your current game")
        player.sendMessage("${ChatColor.YELLOW}/pillars list ${ChatColor.GRAY}- List available game types")
        player.sendMessage("${ChatColor.YELLOW}/pillars status ${ChatColor.GRAY}- Show your current game status")
        player.sendMessage("${ChatColor.YELLOW}/pillars stats ${ChatColor.GRAY}- Show server statistics")
        player.sendMessage("${ChatColor.YELLOW}/pillars help ${ChatColor.GRAY}- Show this help message")
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (sender !is Player) return emptyList()

        return when (args.size) {
            1 -> {
                val subcommands = listOf("join", "leave", "list", "status", "stats", "help")
                subcommands.filter { it.startsWith(args[0], ignoreCase = true) }
            }
            2 -> {
                if (args[0].equals("join", ignoreCase = true)) {
                    plugin.configs.map { it.name }.filter { it.startsWith(args[1], ignoreCase = true) }
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }
}