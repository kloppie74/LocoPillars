package org.Kloppie74.locoPillars.utils

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.game.data.Team
import org.bukkit.entity.Player

class Placeholders(private val plugin: LocoPillars) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "locopillars"
    override fun getAuthor(): String = "Kloppie74"
    override fun getVersion(): String = "5.0.0"
    override fun persist(): Boolean = true

    override fun onRequest(p: OfflinePlayer?, params: String): String? {
        val player = p?.player

        val gameInstance = player?.let { plugin.gameManager.getPlayerInstance(it) }
        val timerEvent = plugin.gameManager.timerEvent
        val db = plugin.databaseManager
        val stats = if (player != null && db.isConnected()) db.statsTable else null
        val playersTable = if (db.isConnected()) db.playersTable else null

        val pLower = params.lowercase()

        // === NEW ENHANCED TEAM STATISTICS PLACEHOLDERS ===
        val teamRegex = Regex("""game_team_(team\d+|[\w\-_]+)_(players_count|players_alive|players_total|players_dead|status|status_symbol|displayname)""")
        teamRegex.matchEntire(pLower)?.let { match ->
            val teamIdentifier = match.groupValues[1]
            val statType = match.groupValues[2]

            if (gameInstance != null) {
                val teamStats = getTeamStatistics(gameInstance, teamIdentifier)
                val team = getTeamByIdentifier(gameInstance, teamIdentifier)
                return when (statType) {
                    "players_count", "players_total" -> teamStats.totalPlayers.toString()
                    "players_alive" -> teamStats.alivePlayers.toString()
                    "players_dead" -> teamStats.deadPlayers.toString()
                    "status" -> teamStats.status
                    "status_symbol" -> teamStats.statusSymbol
                    "displayname" -> team?.let { "[${it.name}]" } ?: ""
                    else -> ""
                }
            }
            return ""
        }

        // === NEW MAP-BASED PLACEHOLDERS WITH MULTI-MAP SUPPORT ===
        val mapRegex = Regex("""map_(.+?)_(players_in_lobby|players_playing|players_total|games_active|games_waiting|games_starting|games_total)""")
        mapRegex.matchEntire(pLower)?.let { match ->
            val mapNamePart = match.groupValues[1].removeSuffix(".yml") // Remove .yml if present
            val statType = match.groupValues[2]

            // Check if this is a multi-map placeholder with <map1,map2,map3> syntax
            val multiMapRegex = Regex("""<([^>]+)>""")
            val multiMapMatch = multiMapRegex.find(mapNamePart)

            if (multiMapMatch != null) {
                // Multi-map case: extract map names and get stats for each
                val mapList = multiMapMatch.groupValues[1]
                val maps = mapList.split(",").map { it.trim() }

                val results = maps.map { mapName ->
                    val mapStats = getMapStatistics(mapName)
                    when (statType) {
                        "players_in_lobby" -> mapStats.playersInLobby
                        "players_playing" -> mapStats.playersPlaying
                        "players_total" -> mapStats.totalPlayers
                        "games_active" -> mapStats.gamesActive
                        "games_waiting" -> mapStats.gamesWaiting
                        "games_starting" -> mapStats.gamesStarting
                        "games_total" -> mapStats.totalGames
                        else -> 0
                    }
                }

                // Sum all values and return as string
                return results.sum().toString()
            } else {
                // Single map case: use original logic
                val mapStats = getMapStatistics(mapNamePart)
                return when (statType) {
                    "players_in_lobby" -> mapStats.playersInLobby.toString()
                    "players_playing" -> mapStats.playersPlaying.toString()
                    "players_total" -> mapStats.totalPlayers.toString()
                    "games_active" -> mapStats.gamesActive.toString()
                    "games_waiting" -> mapStats.gamesWaiting.toString()
                    "games_starting" -> mapStats.gamesStarting.toString()
                    "games_total" -> mapStats.totalGames.toString()
                    else -> ""
                }
            }
        }

        // === INDIVIDUAL TEAM NAME AND COLOR PLACEHOLDERS ===
        val teamNameRegex = Regex("""game_team_(team\d+|[\w\-_]+)_name""")
        teamNameRegex.matchEntire(pLower)?.let { match ->
            val teamIdentifier = match.groupValues[1]
            if (gameInstance != null) {
                val team = getTeamByIdentifier(gameInstance, teamIdentifier)
                return team?.name ?: ""
            }
            return ""
        }

        val teamColorRegex = Regex("""game_team_(team\d+|[\w\-_]+)_color""")
        teamColorRegex.matchEntire(pLower)?.let { match ->
            val teamIdentifier = match.groupValues[1]
            if (gameInstance != null) {
                val team = getTeamByIdentifier(gameInstance, teamIdentifier)
                return team?.color?.name ?: ""
            }
            return ""
        }

        // === ORIGINAL PLACEHOLDERS ===
        when (pLower) {
            "active_games", "games_active" -> return plugin.gameManager.getActiveGames().size.toString()
            "player_name" -> return player?.name ?: ""
            "online_players" -> return Bukkit.getOnlinePlayers().size.toString()
            "game_name" -> return gameInstance?.getDisplayName() ?: ""
            "players" -> return gameInstance?.players?.size?.toString() ?: ""
            "max_players" -> return gameInstance?.getMaxPlayers()?.toString() ?: ""
            "min_players" -> return gameInstance?.getMinPlayers()?.toString() ?: ""
            "map_name" -> return gameInstance?.arenaInstance?.template?.name ?: ""
            "game_timer" -> return gameInstance?.id?.let { timerEvent.getGameTimeLeft(it)?.let { s -> formatSecondsHHMMSS(s) } ?: "-" } ?: "-"
            "cage_timer" -> return gameInstance?.arenaInstance?.config?.getCageTimer()?.let { formatSecondsHHMMSS(it) } ?: ""
            "players_left" -> return gameInstance?.players?.size?.toString() ?: ""
            "team_name" -> {
                if (gameInstance != null && player != null) {
                    val team = getPlayerTeam(gameInstance, player)
                    return team?.name ?: ""
                }
                return ""
            }
            "team_color" -> {
                if (gameInstance != null && player != null) {
                    val team = getPlayerTeam(gameInstance, player)
                    return team?.color?.name ?: ""
                }
                return ""
            }
            "team_size" -> {
                if (gameInstance != null && player != null) {
                    val team = getPlayerTeam(gameInstance, player)
                    return team?.spawns?.size?.toString() ?: ""
                }
                return ""
            }
            "teams_total" -> return gameInstance?.arenaInstance?.config?.teams?.size?.toString() ?: ""
            "teams_names" -> {
                val teams = gameInstance?.arenaInstance?.config?.teams
                return teams?.joinToString(", ") { it.name } ?: ""
            }
            "teams_colors" -> {
                val teams = gameInstance?.arenaInstance?.config?.teams
                return teams?.joinToString(", ") { it.color.name } ?: ""
            }
            "teams_sizes" -> {
                val teams = gameInstance?.arenaInstance?.config?.teams
                return teams?.joinToString(", ") { it.spawns.size.toString() } ?: ""
            }
        }

        // === GAME PLACEHOLDERS ===
        when (pLower) {
            "game_state" -> return gameInstance?.state?.name ?: "NONE"
            "game_status" -> {
                val state = gameInstance?.state
                return when (state) {
                    GameState.WAITING -> "Waiting"
                    GameState.STARTING -> "Starting"
                    GameState.ACTIVE -> "Active"
                    GameState.ENDED -> "Ended"
                    GameState.RESETTING -> "Resetting"
                    null -> "None"
                }
            }
            "game_map", "game_map_name" -> return gameInstance?.arenaInstance?.template?.name ?: "NONE"
            "game_world" -> return gameInstance?.arenaInstance?.world?.name ?: "NONE"
            "game_origin_x" -> return gameInstance?.arenaInstance?.origin?.x?.toInt()?.toString() ?: "0"
            "game_origin_y" -> return gameInstance?.arenaInstance?.origin?.y?.toInt()?.toString() ?: "0"
            "game_origin_z" -> return gameInstance?.arenaInstance?.origin?.z?.toInt()?.toString() ?: "0"
            "game_players_left", "game_players_in_game" -> return (gameInstance?.players?.size ?: 0).toString()
            "game_players_max" -> return (gameInstance?.getMaxPlayers() ?: 0).toString()
            "game_players_min" -> return (gameInstance?.getMinPlayers() ?: 0).toString()
            "game_team" -> {
                if (gameInstance != null && player != null) {
                    val team = getPlayerTeam(gameInstance, player)
                    return team?.name ?: "NONE"
                }
                return "NONE"
            }
            "game_team_color" -> {
                if (gameInstance != null && player != null) {
                    val team = getPlayerTeam(gameInstance, player)
                    return team?.color?.name ?: "NONE"
                }
                return "NONE"
            }
            "game_players_online" -> return Bukkit.getOnlinePlayers().size.toString()
            // Timers
            "game_timer_string" -> {
                val secondsLeft = gameInstance?.id?.let { timerEvent.getGameTimeLeft(it) } ?: -1
                return if (secondsLeft >= 0) formatSecondsHHMMSS(secondsLeft) else ""
            }
            "game_items_timer" -> {
                val secondsLeft = gameInstance?.id?.let { timerEvent.getItemTimeLeft(it) } ?: -1
                return if (secondsLeft >= 0) formatSecondsHHMMSS(secondsLeft) else ""
            }
            "game_lobby_timer" -> {
                val instance = gameInstance ?: return ""
                if (instance.state == GameState.STARTING) {
                    val secondsLeft = instance.id.let { timerEvent.getGameTimeLeft(it) } ?: -1
                    return if (secondsLeft >= 0) formatSecondsHHMMSS(secondsLeft) else ""
                }
                return ""
            }
        }

        // === DATABASE PLACEHOLDERS (db_) ===
        when (pLower) {
            "db_player_name" -> return player?.name ?: ""
            "db_player_id" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id ?: ""
            }
            "db_display_name" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { playersTable?.getDisplayName(it) } ?: player?.name ?: ""
            }
            "db_total_sessions" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { playersTable?.getTotalSessions(it) }?.toString() ?: ""
            }
            "db_first_played" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { playersTable?.getFirstPlayed(it)?.toString() } ?: ""
            }
            "db_last_seen" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { playersTable?.getLastSeen(it)?.toString() } ?: ""
            }
            "db_games_played" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getGamesPlayed(it)?.toString() } ?: ""
            }
            "db_games_won" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getGamesWon(it)?.toString() } ?: ""
            }
            "db_games_lost" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getGamesLost(it)?.toString() } ?: ""
            }
            "db_games_quit" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getGamesQuit(it)?.toString() } ?: ""
            }
            "db_total_kills" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getTotalKills(it)?.toString() } ?: ""
            }
            "db_total_deaths" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getTotalDeaths(it)?.toString() } ?: ""
            }
            "db_total_assists" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getTotalAssists(it)?.toString() } ?: ""
            }
            "db_best_killstreak" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getBestKillstreak(it)?.toString() } ?: ""
            }
            "db_current_killstreak" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getCurrentKillstreak(it)?.toString() } ?: ""
            }
            "db_pillars_destroyed" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getPillarsDestroyed(it)?.toString() } ?: ""
            }
            "db_blocks_broken" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getBlocksBroken(it)?.toString() } ?: ""
            }
            "db_blocks_placed" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getBlocksPlaced(it)?.toString() } ?: ""
            }
            "db_total_distance_moved" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { "%.1f".format(stats?.getTotalDistanceMoved(it) ?: 0.0) } ?: ""
            }
            "db_total_playtime" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getTotalPlayTime(it) }?.let { formatSecondsHHMMSS(it.toInt()) } ?: ""
            }
            "db_longest_game" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getLongestGame(it) }?.let { formatSecondsHHMMSS(it.toInt()) } ?: ""
            }
            "db_fastest_win" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getFastestWin(it) }?.let { formatSecondsHHMMSS(it.toInt()) } ?: ""
            }
            "db_average_game_time" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getAverageGameTime(it) }?.let { formatSecondsHHMMSS(it.toInt()) } ?: ""
            }
            "db_current_win_streak" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getCurrentWinStreak(it)?.toString() } ?: ""
            }
            "db_best_win_streak" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getBestWinStreak(it)?.toString() } ?: ""
            }
            "db_current_loss_streak" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getCurrentLossStreak(it)?.toString() } ?: ""
            }
            "db_worst_loss_streak" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getWorstLossStreak(it)?.toString() } ?: ""
            }
            "db_experience" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getPlayerExperience(it)?.toString() } ?: ""
            }
            "db_level" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getPlayerLevel(it)?.toString() } ?: ""
            }
            "db_coins" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getPlayerCoins(it)?.toString() } ?: ""
            }
            "db_damage_dealt" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { "%.1f".format(stats?.getDamageDealt(it) ?: 0.0) } ?: ""
            }
            "db_damage_taken" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { "%.1f".format(stats?.getDamageTaken(it) ?: 0.0) } ?: ""
            }
            "db_fall_damage_taken" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { "%.1f".format(stats?.getFallDamageTaken(it) ?: 0.0) } ?: ""
            }
            "db_double_kills" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getDoubleKills(it)?.toString() } ?: ""
            }
            "db_triple_kills" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getTripleKills(it)?.toString() } ?: ""
            }
            "db_quad_kills" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getQuadKills(it)?.toString() } ?: ""
            }
            "db_perfect_games" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getPerfectGames(it)?.toString() } ?: ""
            }
            "db_comeback_wins" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getComebackWins(it)?.toString() } ?: ""
            }
            "db_global_rank" -> {
                val id = player?.let { playersTable?.getPlayerId(it.name) }
                return id?.let { stats?.getIntStat(it, "global_rank")?.toString() } ?: ""
            }
        }

        // === ACHIEVEMENT PLACEHOLDERS ===
        when (pLower) {
            "achievement_total_completed_amount" -> {
                val unlocked = player?.let { plugin.achievementManager.getPlayerAchievements(it.uniqueId.toString()).size } ?: 0
                return unlocked.toString()
            }
            "achievement_total_completed_percent" -> {
                val all = plugin.achievementManager.getAllAchievements().size
                val unlocked = player?.let { plugin.achievementManager.getPlayerAchievements(it.uniqueId.toString()).size } ?: 0
                return if (all > 0) "%.1f".format(unlocked * 100.0 / all) else "0"
            }
            "achievement_total_count" -> {
                return plugin.achievementManager.getAllAchievements().size.toString()
            }
            "achievement_unlocked_ids" -> {
                return player?.let { plugin.achievementManager.getPlayerAchievements(it.uniqueId.toString()).joinToString(",") } ?: ""
            }
            "achievement_unlocked_names" -> {
                val unlockedIds = player?.let { plugin.achievementManager.getPlayerAchievements(it.uniqueId.toString()) } ?: emptyList()
                val unlocked = plugin.achievementManager.getAllAchievements().filter { unlockedIds.contains(it.achievementId) }
                return unlocked.joinToString(",") { it.displayName }
            }
        }

        // === TYPE / CATEGORY / DIFFICULTY STATUS ===
        when {
            pLower.endsWith("_type_status") && pLower.startsWith("achievement_") -> {
                val type = pLower.removePrefix("achievement_").removeSuffix("_type_status")
                val achievements = plugin.achievementManager.getAllAchievements().filter {
                    it.achievementType.lowercase() == type
                }
                val unlockedIds = player?.let { plugin.achievementManager.getPlayerAchievements(it.uniqueId.toString()) } ?: emptyList()
                val unlockedCount = achievements.count { unlockedIds.contains(it.achievementId) }
                val totalCount = achievements.size
                return "$unlockedCount/$totalCount"
            }
            pLower.endsWith("_category_status") && pLower.startsWith("achievement_") -> {
                val category = pLower.removePrefix("achievement_").removeSuffix("_category_status")
                val achievements = plugin.achievementManager.getAllAchievements().filter {
                    it.category.lowercase() == category
                }
                val unlockedIds = player?.let { plugin.achievementManager.getPlayerAchievements(it.uniqueId.toString()) } ?: emptyList()
                val unlockedCount = achievements.count { unlockedIds.contains(it.achievementId) }
                val totalCount = achievements.size
                return "$unlockedCount/$totalCount"
            }
            pLower.endsWith("_difficulty_status") && pLower.startsWith("achievement_") -> {
                val diff = pLower.removePrefix("achievement_").removeSuffix("_difficulty_status")
                val achievements = plugin.achievementManager.getAllAchievements().filter {
                    it.difficulty.lowercase() == diff
                }
                val unlockedIds = player?.let { plugin.achievementManager.getPlayerAchievements(it.uniqueId.toString()) } ?: emptyList()
                val unlockedCount = achievements.count { unlockedIds.contains(it.achievementId) }
                val totalCount = achievements.size
                return "$unlockedCount/$totalCount"
            }
        }

        // === INDIVIDUAL ACHIEVEMENT PLACEHOLDERS ===
        // completed, progress, percent, name, desc, difficulty, category, type, unlock_message
        if (pLower.startsWith("achievement_")) {
            val regexCompleted = Regex("""achievement_(.+)_completed""")
            val regexProgress = Regex("""achievement_(.+)_progress""")
            val regexPercent = Regex("""achievement_(.+)_percent""")
            val regexName = Regex("""achievement_(.+)_name""")
            val regexDesc = Regex("""achievement_(.+)_desc""")
            val regexDifficulty = Regex("""achievement_(.+)_difficulty""")
            val regexCategory = Regex("""achievement_(.+)_category""")
            val regexType = Regex("""achievement_(.+)_type""")
            val regexUnlockMessage = Regex("""achievement_(.+)_unlock_message""")

            regexCompleted.matchEntire(pLower)?.let { m ->
                val id = m.groupValues[1]
                val unlockedIds = player?.let { plugin.achievementManager.getPlayerAchievements(it.uniqueId.toString()) } ?: emptyList()
                return if (unlockedIds.contains(id)) "1" else "0"
            }
            regexProgress.matchEntire(pLower)?.let { m ->
                val id = m.groupValues[1]
                val achievement = plugin.achievementManager.getAchievement(id)
                val playerId = player?.uniqueId?.toString()
                if (achievement != null && playerId != null) {
                    val progress = plugin.achievementManager.getAchievementProgress(playerId, achievement)
                    val target = achievement.targetValue ?: 1
                    return "$progress/$target"
                } else return ""
            }
            regexPercent.matchEntire(pLower)?.let { m ->
                val id = m.groupValues[1]
                val achievement = plugin.achievementManager.getAchievement(id)
                val playerId = player?.uniqueId?.toString()
                if (achievement != null && playerId != null) {
                    val progress = plugin.achievementManager.getAchievementProgress(playerId, achievement)
                    val target = achievement.targetValue ?: 1
                    val percent = if (target > 0) (progress * 100.0 / target).coerceIn(0.0, 100.0) else 100.0
                    return "%.1f".format(percent)
                } else return ""
            }
            regexName.matchEntire(pLower)?.let { m ->
                val id = m.groupValues[1]
                val achievement = plugin.achievementManager.getAchievement(id)
                return achievement?.displayName ?: ""
            }
            regexDesc.matchEntire(pLower)?.let { m ->
                val id = m.groupValues[1]
                val achievement = plugin.achievementManager.getAchievement(id)
                return achievement?.description ?: ""
            }
            regexDifficulty.matchEntire(pLower)?.let { m ->
                val id = m.groupValues[1]
                val achievement = plugin.achievementManager.getAchievement(id)
                return achievement?.difficulty ?: ""
            }
            regexCategory.matchEntire(pLower)?.let { m ->
                val id = m.groupValues[1]
                val achievement = plugin.achievementManager.getAchievement(id)
                return achievement?.category ?: ""
            }
            regexType.matchEntire(pLower)?.let { m ->
                val id = m.groupValues[1]
                val achievement = plugin.achievementManager.getAchievement(id)
                return achievement?.achievementType ?: ""
            }
            regexUnlockMessage.matchEntire(pLower)?.let { m ->
                val id = m.groupValues[1]
                val achievement = plugin.achievementManager.getAchievement(id)
                return achievement?.unlockMessage ?: ""
            }
        }

        // Fallback: empty string for unknown placeholders
        return ""
    }

    // Formats seconds to HH:MM:SS
    private fun formatSecondsHHMMSS(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h == 0) {
            "%02d:%02d".format(m, s)
        } else {
            "%02d:%02d:%02d".format(h, m, s)
        }
    }

    /**
     * Finds the Team object the player is assigned to in the current game instance.
     * Returns null if not found.
     */
    private fun getPlayerTeam(gameInstance: org.Kloppie74.locoPillars.game.instance.GameInstance, player: Player): Team? {
        val uuid = player.uniqueId
        val teams = gameInstance.arenaInstance.config.teams

        val index = gameInstance.players.indexOf(uuid)
        if (index == -1) return null

        var runningIndex = 0
        for (team in teams) {
            val teamSize = team.spawns.size
            if (index < runningIndex + teamSize) {
                return team
            }
            runningIndex += teamSize
        }
        return null
    }

    /**
     * Get a team by identifier (supports both "team1", "team2", etc. and actual team names)
     */
    private fun getTeamByIdentifier(gameInstance: org.Kloppie74.locoPillars.game.instance.GameInstance, teamIdentifier: String): Team? {
        val teams = gameInstance.arenaInstance.config.teams

        return if (teamIdentifier.startsWith("team") && teamIdentifier.length > 4) {
            // Handle team1, team2, etc.
            val teamNumber = teamIdentifier.removePrefix("team").toIntOrNull()
            if (teamNumber != null && teamNumber > 0 && teamNumber <= teams.size) {
                teams[teamNumber - 1] // Convert to 0-based index
            } else null
        } else {
            // Handle actual team names
            teams.find { it.name.lowercase() == teamIdentifier.lowercase() }
        }
    }

    /**
     * Data class to hold team statistics
     */
    private data class TeamStatistics(
        val totalPlayers: Int,
        val alivePlayers: Int,
        val deadPlayers: Int,
        val status: String,
        val statusSymbol: String
    )

    /**
     * Get team statistics for a specific team identifier
     * Supports both "team1", "team2", etc. and actual team names
     */
    private fun getTeamStatistics(gameInstance: org.Kloppie74.locoPillars.game.instance.GameInstance, teamIdentifier: String): TeamStatistics {
        val teams = gameInstance.arenaInstance.config.teams
        val currentPlayers = gameInstance.players

        // Find the target team
        val targetTeam = getTeamByIdentifier(gameInstance, teamIdentifier)

        if (targetTeam == null) {
            return TeamStatistics(0, 0, 0, "Unknown", "❓")
        }

        // Calculate team statistics
        val teamPlayerUUIDs = getPlayersInTeam(gameInstance, targetTeam)
        val totalPlayersInTeam = teamPlayerUUIDs.size
        val alivePlayersInTeam = teamPlayerUUIDs.count { it in currentPlayers }
        val deadPlayersInTeam = totalPlayersInTeam - alivePlayersInTeam

        // Determine status
        val status = when {
            totalPlayersInTeam == 0 -> "Empty"
            alivePlayersInTeam == 0 -> "Eliminated"
            alivePlayersInTeam == totalPlayersInTeam -> "Alive"
            else -> "Partial"
        }

        // Determine status symbol
        val statusSymbol = when {
            totalPlayersInTeam == 0 -> "⚪" // Empty
            alivePlayersInTeam == 0 -> "❌" // All dead
            alivePlayersInTeam == totalPlayersInTeam -> "✅" // All alive
            else -> "⚠️" // Some alive, some dead
        }

        return TeamStatistics(
            totalPlayers = totalPlayersInTeam,
            alivePlayers = alivePlayersInTeam,
            deadPlayers = deadPlayersInTeam,
            status = status,
            statusSymbol = statusSymbol
        )
    }

    /**
     * Get all player UUIDs that belong to a specific team
     * Uses the same logic as GameplayManager.assignPlayersToTeams()
     */
    private fun getPlayersInTeam(gameInstance: org.Kloppie74.locoPillars.game.instance.GameInstance, team: Team): List<java.util.UUID> {
        val teams = gameInstance.arenaInstance.config.teams
        val teamIndex = teams.indexOf(team)
        if (teamIndex == -1) return emptyList()

        // Get all players that are currently in the game + any that might have been eliminated
        // We need to track all players who were originally assigned to teams
        val playersPerTeam = gameInstance.arenaInstance.config.getPlayersPerTeam()
        val teamPlayers = mutableListOf<java.util.UUID>()

        // Calculate starting player index for this team
        var playerIndex = 0
        for (i in 0 until teamIndex) {
            playerIndex += minOf(playersPerTeam, teams[i].spawns.size)
        }

        // Get players for this specific team
        val maxPlayersForThisTeam = minOf(playersPerTeam, team.spawns.size)

        // Find players by checking their current team assignment
        // This is a simplified approach - in a real implementation you'd want to track original team assignments
        val currentPlayers = gameInstance.players.toList()

        for (playerUUID in currentPlayers) {
            val player = Bukkit.getPlayer(playerUUID)
            if (player != null) {
                val playerTeam = getPlayerTeam(gameInstance, player)
                if (playerTeam == team) {
                    teamPlayers.add(playerUUID)
                }
            }
        }

        return teamPlayers
    }

    /**
     * Data class to hold map statistics
     */
    private data class MapStatistics(
        val playersInLobby: Int,
        val playersPlaying: Int,
        val totalPlayers: Int,
        val gamesActive: Int,
        val gamesWaiting: Int,
        val gamesStarting: Int,
        val totalGames: Int
    )

    /**
     * Get statistics for a specific map/game type
     */
    private fun getMapStatistics(mapName: String): MapStatistics {
        val instances = plugin.gameManager.getInstancesByGameType(mapName)

        var playersInLobby = 0
        var playersPlaying = 0
        var totalPlayers = 0
        var gamesActive = 0
        var gamesWaiting = 0
        var gamesStarting = 0
        val totalGames = instances.size

        instances.forEach { instance ->
            val playerCount = instance.players.size
            totalPlayers += playerCount

            when (instance.state) {
                GameState.WAITING -> {
                    gamesWaiting++
                    playersInLobby += playerCount
                }
                GameState.STARTING -> {
                    gamesStarting++
                    playersInLobby += playerCount
                }
                GameState.ACTIVE -> {
                    gamesActive++
                    playersPlaying += playerCount
                }
                GameState.ENDED, GameState.RESETTING -> {
                    // Don't count these in any category
                }
            }
        }

        return MapStatistics(
            playersInLobby = playersInLobby,
            playersPlaying = playersPlaying,
            totalPlayers = totalPlayers,
            gamesActive = gamesActive,
            gamesWaiting = gamesWaiting,
            gamesStarting = gamesStarting,
            totalGames = totalGames
        )
    }
}