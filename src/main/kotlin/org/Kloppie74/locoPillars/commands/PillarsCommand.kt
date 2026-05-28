package org.Kloppie74.locoPillars.commands

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.MSG

class PillarsCommand(private val plugin: LocoPillars) : CommandExecutor, TabCompleter {

    private val subPermissions = mapOf(
        "join"      to "locopillars.pillars.join",
        "leave"     to "locopillars.pillars.leave",
        "list"      to "locopillars.pillars.list",
        "status"    to "locopillars.pillars.status",
        "stats"     to "locopillars.pillars.stats",
        "help"      to "locopillars.pillars.help",
        "vote"      to "locopillars.pillars.vote",
        "autoqueue" to "locopillars.pillars.autoqueue"
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(MSG.lang("general.player_only"))
            return true
        }

        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        val sub = args[0].lowercase()
        val perm = subPermissions[sub] ?: ""
        if (perm.isNotEmpty() && !sender.hasPermission(perm)) {
            sender.sendMessage(MSG.lang("pillars.no_permission_sub", "sub" to sub))
            return true
        }

        when (sub) {
            "join" -> {
                if (args.size < 2) {
                    sender.sendMessage(MSG.lang("pillars.join.usage"))
                    sender.sendMessage(MSG.lang("pillars.join.available_types_header"))
                    plugin.configs.forEach { config ->
                        sender.sendMessage(MSG.lang("pillars.join.available_type_entry", "name" to config.name, "display" to config.getDisplayName()))
                    }
                    sender.sendMessage(MSG.lang("pillars.join.random_hint"))
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
            "leave"     -> { leaveGame(sender); return true }
            "list"      -> { listGames(sender); return true }
            "status"    -> { showStatus(sender); return true }
            "stats"     -> { showStats(sender); return true }
            "help"      -> { showHelp(sender); return true }
            "vote"      -> { openVoteGui(sender); return true }
            "autoqueue" -> { toggleAutoQueue(sender); return true }
            else -> {
                sender.sendMessage(MSG.lang("general.unknown_subcommand", "input" to args[0]))
                showHelp(sender)
                return true
            }
        }
    }

    private fun joinRandomGame(player: Player) {
        if (plugin.gameManager.getPlayerInstance(player) != null) {
            player.sendMessage(MSG.lang("pillars.join.already_in_game"))
            return
        }

        val joinable: List<GameInstance> = plugin.gameManager.getAllInstances().filter { it.canJoin() }
        if (joinable.isEmpty()) {
            player.sendMessage(MSG.lang("pillars.join.no_games_available"))
            return
        }

        val chosen = joinable.sortedWith(compareByDescending<GameInstance> { it.players.size }
            .thenBy { it.getMinPlayers() }).firstOrNull()
        if (chosen == null) {
            player.sendMessage(MSG.lang("pillars.join.no_suitable_game"))
            return
        }

        val joinedInstance = plugin.gameManager.joinGame(player, chosen.arenaInstance.template.name)
        if (joinedInstance == null) {
            player.sendMessage(MSG.lang("pillars.join.failed_game_full"))
            return
        }

        player.sendMessage(MSG.lang("pillars.join.random_joined", "game" to joinedInstance.getDisplayName()))
        player.sendMessage(MSG.lang("pillars.join.players_count",
            "current" to joinedInstance.players.size.toString(),
            "max" to joinedInstance.getMaxPlayers().toString()))
        if (joinedInstance.players.size >= joinedInstance.getMinPlayers()) {
            player.sendMessage(MSG.lang("pillars.join.starting_soon"))
        } else {
            val needed = joinedInstance.getMinPlayers() - joinedInstance.players.size
            player.sendMessage(MSG.lang("pillars.join.waiting_for_players", "needed" to needed.toString()))
        }
    }

    private fun joinGame(player: Player, gameType: String) {
        if (plugin.gameManager.getPlayerInstance(player) != null) {
            player.sendMessage(MSG.lang("pillars.join.already_in_game"))
            return
        }

        val config = plugin.configs.firstOrNull { it.name.equals(gameType, ignoreCase = true) }
        if (config == null) {
            player.sendMessage(MSG.lang("pillars.join.type_not_found", "type" to gameType))
            player.sendMessage(MSG.lang("pillars.join.available_types_header"))
            plugin.configs.forEach { player.sendMessage(MSG.lang("pillars.join.available_type_entry", "name" to it.name, "display" to it.getDisplayName())) }
            return
        }

        val instance = plugin.gameManager.joinGame(player, config.name)
        if (instance == null) {
            player.sendMessage(MSG.lang("pillars.join.failed_no_instance"))
            return
        }

        player.sendMessage(MSG.lang("pillars.join.joined", "game" to config.getDisplayName()))
        player.sendMessage(MSG.lang("pillars.join.players_count",
            "current" to instance.players.size.toString(),
            "max" to instance.getMaxPlayers().toString()))
        if (instance.players.size >= instance.getMinPlayers()) {
            player.sendMessage(MSG.lang("pillars.join.starting_soon"))
        } else {
            val needed = instance.getMinPlayers() - instance.players.size
            player.sendMessage(MSG.lang("pillars.join.waiting_for_players", "needed" to needed.toString()))
        }
    }

    private fun leaveGame(player: Player) {
        val cancelledQueue = plugin.gameManager.cancelAutoQueue(player.uniqueId)
        val leftGame = plugin.gameManager.leaveGame(player)
        when {
            leftGame -> { /* game left successfully */ }
            cancelledQueue -> {
                // Player was in the post-match auto-queue countdown (spectator phase).
                // Take them out of spectator + send them straight to lobby so the cancel
                // command actually does what they expect.
                plugin.gameManager.exitPostMatchToLobby(player)
                player.sendMessage(MSG.lang("pillars.autoqueue.cancelled"))
            }
            else -> player.sendMessage(MSG.lang("pillars.leave.not_in_game"))
        }
    }

    private fun listGames(player: Player) {
        player.sendMessage(MSG.lang("pillars.list.header"))
        if (plugin.configs.isEmpty()) {
            player.sendMessage(MSG.lang("pillars.list.no_types"))
            return
        }
        plugin.configs.forEach { config ->
            val instances = plugin.gameManager.getInstancesByGameType(config.name)
            player.sendMessage(MSG.lang("pillars.list.type_entry", "name" to config.name, "display" to config.getDisplayName()))
            player.sendMessage(MSG.lang("pillars.list.type_stats",
                "available" to instances.count { it.canJoin() }.toString(),
                "active" to instances.count { it.state == GameState.ACTIVE }.toString()))
            player.sendMessage(MSG.lang("pillars.list.type_player_info",
                "min" to config.getMinPlayers().toString(),
                "max" to config.getMaxPlayers().toString(),
                "teams" to config.getMaxTeams().toString()))
        }
        player.sendMessage(MSG.lang("pillars.list.join_hint"))
        player.sendMessage(MSG.lang("pillars.list.random_hint"))
    }

    private fun showStatus(player: Player) {
        val instance = plugin.gameManager.getPlayerInstance(player)
        if (instance == null) {
            player.sendMessage(MSG.lang("pillars.status.not_in_game"))
            player.sendMessage(MSG.lang("pillars.status.not_in_game_hint"))
            return
        }
        player.sendMessage(MSG.lang("pillars.status.header"))
        player.sendMessage(MSG.lang("pillars.status.game", "game" to instance.getDisplayName()))
        player.sendMessage(MSG.lang("pillars.status.state", "state" to instance.state.toString()))
        player.sendMessage(MSG.lang("pillars.status.players",
            "current" to instance.players.size.toString(),
            "max" to instance.getMaxPlayers().toString()))
        player.sendMessage(MSG.lang("pillars.status.world", "world" to instance.arenaInstance.world.name))
        player.sendMessage(MSG.lang("pillars.status.instance_id", "id" to instance.id.toString()))

        when (instance.state) {
            GameState.WAITING -> {
                val needed = instance.getMinPlayers() - instance.players.size
                if (needed > 0) player.sendMessage(MSG.lang("pillars.status.waiting_for_players", "needed" to needed.toString()))
                else            player.sendMessage(MSG.lang("pillars.status.ready_to_start"))
            }
            GameState.STARTING  -> player.sendMessage(MSG.lang("pillars.status.starting"))
            GameState.ACTIVE    -> player.sendMessage(MSG.lang("pillars.status.active"))
            GameState.ENDED     -> player.sendMessage(MSG.lang("pillars.status.ended"))
            GameState.RESETTING -> player.sendMessage(MSG.lang("pillars.status.resetting"))
        }
    }

    private fun showStats(player: Player) {
        player.sendMessage(MSG.lang("pillars.stats.header"))
        player.sendMessage(MSG.lang("pillars.stats.total_instances",     "count" to plugin.gameManager.getAllInstances().size.toString()))
        player.sendMessage(MSG.lang("pillars.stats.available_instances", "count" to plugin.gameManager.getAvailableInstances().size.toString()))
        player.sendMessage(MSG.lang("pillars.stats.active_games",        "count" to plugin.gameManager.getActiveGames().size.toString()))
        player.sendMessage(MSG.lang("pillars.stats.arena_worlds",        "count" to plugin.worldManager.getAllArenaWorlds().size.toString()))
        player.sendMessage(MSG.lang("pillars.stats.game_types",          "count" to plugin.configs.size.toString()))

        plugin.configs.forEach { config ->
            val instances = plugin.gameManager.getInstancesByGameType(config.name)
            player.sendMessage(MSG.lang("pillars.stats.type_summary",
                "name" to config.name,
                "available" to instances.count { it.canJoin() }.toString(),
                "active" to instances.count { it.state == GameState.ACTIVE }.toString(),
                "players" to instances.sumOf { it.players.size }.toString()))
        }
    }

    private fun toggleAutoQueue(player: Player) {
        if (!plugin.configManager.autoQueueEnabled) {
            player.sendMessage(MSG.lang("pillars.autoqueue.disabled_server"))
            return
        }
        val now = plugin.playerPreferences.toggleAutoQueue(player.uniqueId)
        val key = if (now) "pillars.autoqueue.toggled_on" else "pillars.autoqueue.toggled_off"
        player.sendMessage(MSG.lang(key))
    }

    private fun openVoteGui(player: Player) {
        val instance = plugin.gameManager.getPlayerInstance(player)
        if (instance == null) {
            player.sendMessage(MSG.lang("pillars.vote.not_in_game"))
            return
        }
        if (instance.state != GameState.WAITING && instance.state != GameState.STARTING) {
            player.sendMessage(MSG.lang("pillars.vote.voting_closed"))
            return
        }
        if (!plugin.isVotingReady) {
            player.sendMessage(MSG.lang("general.feature_unavailable"))
            return
        }
        plugin.votingGui.open(player, instance)
    }

    private fun showHelp(player: Player) {
        player.sendMessage(MSG.lang("pillars.help.header"))
        if (player.hasPermission(subPermissions["join"]!!)) {
            player.sendMessage(MSG.lang("pillars.help.join"))
            player.sendMessage(MSG.lang("pillars.help.join_random"))
        }
        if (player.hasPermission(subPermissions["leave"]!!))  player.sendMessage(MSG.lang("pillars.help.leave"))
        if (player.hasPermission(subPermissions["list"]!!))   player.sendMessage(MSG.lang("pillars.help.list"))
        if (player.hasPermission(subPermissions["status"]!!)) player.sendMessage(MSG.lang("pillars.help.status"))
        if (player.hasPermission(subPermissions["stats"]!!))  player.sendMessage(MSG.lang("pillars.help.server_stats"))
        if (player.hasPermission(subPermissions["vote"]!!))      player.sendMessage(MSG.lang("pillars.help.vote"))
        if (player.hasPermission(subPermissions["autoqueue"]!!)) player.sendMessage(MSG.lang("pillars.help.autoqueue"))
        if (player.hasPermission(subPermissions["help"]!!))      player.sendMessage(MSG.lang("pillars.help.help"))
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (sender !is Player) return emptyList()
        val availableSubCmds = subPermissions.filter { sender.hasPermission(it.value) }.keys.toList()
        return when (args.size) {
            1 -> availableSubCmds.filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> if (args[0].equals("join", ignoreCase = true) && sender.hasPermission(subPermissions["join"]!!)) {
                (plugin.configs.map { it.name } + "random").filter { it.startsWith(args[1], ignoreCase = true) }
            } else emptyList()
            else -> emptyList()
        }
    }
}
