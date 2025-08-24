package org.Kloppie74.locoPillars.utils

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.data.GameState
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

        // === SCOREBOARDMANAGER PLACEHOLDERS ===
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
        return "%02d:%02d:%02d".format(h, m, s)
    }

    /**
     * Finds the Team object the player is assigned to in the current game instance.
     * Returns null if not found.
     */
    private fun getPlayerTeam(gameInstance: org.Kloppie74.locoPillars.game.instance.GameInstance, player: Player): org.Kloppie74.locoPillars.game.data.Team? {
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
}