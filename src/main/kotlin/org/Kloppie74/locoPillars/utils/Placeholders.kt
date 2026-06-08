package org.Kloppie74.locoPillars.utils

import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.game.data.Team
import org.Kloppie74.locoPillars.database.dao.AchievementDAO
import org.Kloppie74.locoPillars.database.dao.LeaderboardCacheDAO
import org.Kloppie74.locoPillars.database.dao.PlayerDAO
import org.Kloppie74.locoPillars.database.dao.StatsDAO
import org.bukkit.entity.Player

/**
 * PlaceholderAPI expansion for LocoPillars.
 *
 * CHANGELOG (v2.0):
 * - Replaced all playersTable calls with PlayerDAO
 * - Replaced all statsTable calls with StatsDAO
 * - Replaced all achievementManager database calls with AchievementDAO
 * - Added proper error handling for all database operations
 * - Improved null-safety checks
 * - Better performance with cached error states
 *
 * @author Kloppie74
 * @since 5.0.0
 */
class Placeholders(private val plugin: LocoPillars) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "locopillars"
    override fun getAuthor(): String = "Kloppie74"
    override fun getVersion(): String = "5.0.0"
    override fun persist(): Boolean = true

    /** Mapt placeholder-token → leaderboard-type voor positie-placeholders (db_rank_N_<type>). */
    private val LEADERBOARD_TYPE_TOKENS = mapOf(
        "wins" to LeaderboardCacheDAO.TYPE_WINS,
        "winrate" to LeaderboardCacheDAO.TYPE_WINRATE,
        "kills" to LeaderboardCacheDAO.TYPE_KILLS,
        "kd" to LeaderboardCacheDAO.TYPE_KD_RATIO,
        "level" to LeaderboardCacheDAO.TYPE_LEVEL,
        "playtime" to LeaderboardCacheDAO.TYPE_PLAYTIME,
        "streak" to LeaderboardCacheDAO.TYPE_STREAK,
        "pillars" to LeaderboardCacheDAO.TYPE_PILLARS
    )

    override fun onRequest(p: OfflinePlayer?, params: String): String? {
        val player = p?.player

        val gameInstance = player?.let { plugin.gameManager.getPlayerInstance(it) }
        val timerEvent = plugin.gameManager.timerEvent

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
                    "displayname" -> team?.let { "${it.color.toChatColor()}${it.name}" } ?: ""
                    else -> ""
                }
            }
            return ""
        }

        // === NEW MAP-BASED PLACEHOLDERS WITH MULTI-MAP SUPPORT ===
        val mapRegex = Regex("""map_(.+?)_(players_in_lobby|players_playing|players_total|games_active|games_waiting|games_starting|games_total)""")
        mapRegex.matchEntire(pLower)?.let { match ->
            val mapNamePart = match.groupValues[1].removeSuffix(".yml")
            val statType = match.groupValues[2]

            val multiMapRegex = Regex("""<([^>]+)>""")
            val multiMapMatch = multiMapRegex.find(mapNamePart)

            if (multiMapMatch != null) {
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

                return results.sum().toString()
            } else {
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

        // === POSITIONAL LEADERBOARD PLACEHOLDERS ===
        // db_rank_<positie>_<type>            → naam van de speler op die positie
        // db_rank_<positie>_<type>_name       → idem (expliciet)
        // db_rank_<positie>_<type>_value      → nette weergavewaarde (bijv. "42", "3.5 K/D")
        // db_rank_<positie>_<type>_raw        → ruwe numerieke waarde
        // type = wins, winrate, kills, kd, level, playtime, streak, pillars
        val posRankRegex = Regex("""db_rank_(\d+)_(.+?)(?:_(name|value|raw))?""")
        posRankRegex.matchEntire(pLower)?.let { m ->
            val position = m.groupValues[1].toIntOrNull() ?: return ""
            val typeToken = m.groupValues[2]
            val field = m.groupValues[3] // "", "name", "value", "raw"
            val type = LEADERBOARD_TYPE_TOKENS[typeToken] ?: return ""
            return getLeaderboardPositionSafe(type, position, field)
        }

        // === DATABASE PLACEHOLDERS (db_) - UPDATED WITH DAO ===
        when (pLower) {
            "db_player_name" -> return player?.name ?: ""
            "db_player_id" -> {
                return player?.let {
                    try {
                        PlayerDAO.getPlayerId(it.name) ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                } ?: ""
            }
            "db_display_name" -> {
                return player?.let {
                    try {
                        val playerId = PlayerDAO.getPlayerId(it.name)
                        playerId?.let { id -> PlayerDAO.getDisplayName(id) } ?: it.name
                    } catch (e: Exception) {
                        it.name
                    }
                } ?: ""
            }
            "db_total_sessions" -> {
                return player?.let {
                    try {
                        val playerId = PlayerDAO.getPlayerId(it.name)
                        playerId?.let { id -> PlayerDAO.getTotalSessions(id).toString() } ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                } ?: ""
            }
            "db_first_played" -> {
                return player?.let {
                    try {
                        val playerId = PlayerDAO.getPlayerId(it.name)
                        playerId?.let { id -> PlayerDAO.getFirstPlayed(id).toString() } ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                } ?: ""
            }
            "db_last_seen" -> {
                return player?.let {
                    try {
                        val playerId = PlayerDAO.getPlayerId(it.name)
                        playerId?.let { id -> PlayerDAO.getLastSeen(id).toString() } ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                } ?: ""
            }
            "db_games_played" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "games_played").toString() }
            "db_games_won" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "games_won").toString() }
            "db_games_lost" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "games_lost").toString() }
            "db_games_quit" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "games_quit").toString() }
            "db_total_kills" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "total_kills").toString() }
            "db_total_deaths" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "total_deaths").toString() }
            "db_total_assists" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "total_assists").toString() }
            "db_best_killstreak" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "best_killstreak").toString() }
            "db_current_killstreak" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "current_killstreak").toString() }
            "db_pillars_destroyed" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "pillars_destroyed").toString() }
            "db_blocks_broken" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "blocks_broken").toString() }
            "db_blocks_placed" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "blocks_placed").toString() }
            "db_total_distance_moved" -> return getStatSafe(player) { "%.1f".format(StatsDAO.getDoubleStat(it, "total_distance_moved")) }
            "db_total_playtime" -> return getStatSafe(player) { formatSecondsHHMMSS(StatsDAO.getLongStat(it, "total_playtime").toInt()) }
            "db_longest_game" -> return getStatSafe(player) { formatSecondsHHMMSS(StatsDAO.getIntStat(it, "longest_game_time")) }
            "db_fastest_win" -> return getStatSafe(player) {
                val fastestWin = StatsDAO.getIntStat(it, "fastest_win_time")
                if (fastestWin > 0) formatSecondsHHMMSS(fastestWin) else ""
            }
            "db_average_game_time" -> return getStatSafe(player) { formatSecondsHHMMSS(StatsDAO.getIntStat(it, "average_game_time")) }
            "db_current_win_streak" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "current_win_streak").toString() }
            "db_best_win_streak" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "best_win_streak").toString() }
            "db_current_loss_streak" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "current_loss_streak").toString() }
            "db_worst_loss_streak" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "worst_loss_streak").toString() }
            "db_experience" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "experience").toString() }
            "db_level" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "level").toString() }
            "db_coins" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "coins").toString() }
            "db_damage_dealt" -> return getStatSafe(player) { "%.1f".format(StatsDAO.getDoubleStat(it, "damage_dealt")) }
            "db_damage_taken" -> return getStatSafe(player) { "%.1f".format(StatsDAO.getDoubleStat(it, "damage_taken")) }
            "db_fall_damage_taken" -> return getStatSafe(player) { "%.1f".format(StatsDAO.getDoubleStat(it, "fall_damage_taken")) }
            "db_double_kills" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "double_kills").toString() }
            "db_triple_kills" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "triple_kills").toString() }
            "db_quad_kills" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "quad_kills").toString() }
            "db_perfect_games" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "perfect_games").toString() }
            "db_comeback_wins" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "comeback_wins").toString() }
            "db_global_rank" -> return getStatSafe(player) { StatsDAO.getIntStat(it, "global_rank").toString() }
            "db_rank_wins" -> return getLeaderboardRankSafe(player, LeaderboardCacheDAO.TYPE_WINS)
            "db_rank_kd" -> return getLeaderboardRankSafe(player, LeaderboardCacheDAO.TYPE_KD_RATIO)
            "db_rank_winrate" -> return getLeaderboardRankSafe(player, LeaderboardCacheDAO.TYPE_WINRATE)
            "db_rank_kills" -> return getLeaderboardRankSafe(player, LeaderboardCacheDAO.TYPE_KILLS)
            "db_rank_level" -> return getLeaderboardRankSafe(player, LeaderboardCacheDAO.TYPE_LEVEL)
            "db_rank_playtime" -> return getLeaderboardRankSafe(player, LeaderboardCacheDAO.TYPE_PLAYTIME)
            "db_rank_streak" -> return getLeaderboardRankSafe(player, LeaderboardCacheDAO.TYPE_STREAK)
            "db_rank_pillars" -> return getLeaderboardRankSafe(player, LeaderboardCacheDAO.TYPE_PILLARS)
        }

        // === ACHIEVEMENT PLACEHOLDERS - UPDATED WITH DAO ===
        when (pLower) {
            "achievement_total_completed_amount" -> {
                return player?.let {
                    try {
                        val playerId = PlayerDAO.getPlayerId(it.name)
                        playerId?.let { id ->
                            AchievementDAO.getPlayerAchievements(id).size.toString()
                        } ?: "0"
                    } catch (e: Exception) {
                        "0"
                    }
                } ?: "0"
            }
            "achievement_total_completed_percent" -> {
                val all = plugin.achievementManager.getAllAchievements().size
                val unlocked = player?.let {
                    try {
                        val playerId = PlayerDAO.getPlayerId(it.name)
                        playerId?.let { id -> AchievementDAO.getPlayerAchievements(id).size } ?: 0
                    } catch (e: Exception) {
                        0
                    }
                } ?: 0
                return if (all > 0) "%.1f".format(unlocked * 100.0 / all) else "0"
            }
            "achievement_total_count" -> {
                return plugin.achievementManager.getAllAchievements().size.toString()
            }
            "achievement_unlocked_ids" -> {
                return player?.let {
                    try {
                        val playerId = PlayerDAO.getPlayerId(it.name)
                        playerId?.let { id ->
                            AchievementDAO.getPlayerAchievements(id).joinToString(",")
                        } ?: ""
                    } catch (e: Exception) {
                        ""
                    }
                } ?: ""
            }
            "achievement_unlocked_names" -> {
                val unlockedIds = player?.let {
                    try {
                        val playerId = PlayerDAO.getPlayerId(it.name)
                        playerId?.let { id -> AchievementDAO.getPlayerAchievements(id) } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                } ?: emptyList()
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
                val unlockedIds = player?.let {
                    try {
                        val playerId = PlayerDAO.getPlayerId(it.name)
                        playerId?.let { id -> AchievementDAO.getPlayerAchievements(id) } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                } ?: emptyList()
                val unlockedCount = achievements.count { unlockedIds.contains(it.achievementId) }
                val totalCount = achievements.size
                return "$unlockedCount/$totalCount"
            }
            pLower.endsWith("_category_status") && pLower.startsWith("achievement_") -> {
                val category = pLower.removePrefix("achievement_").removeSuffix("_category_status")
                val achievements = plugin.achievementManager.getAllAchievements().filter {
                    it.category.lowercase() == category
                }
                val unlockedIds = player?.let {
                    try {
                        val playerId = PlayerDAO.getPlayerId(it.name)
                        playerId?.let { id -> AchievementDAO.getPlayerAchievements(id) } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                } ?: emptyList()
                val unlockedCount = achievements.count { unlockedIds.contains(it.achievementId) }
                val totalCount = achievements.size
                return "$unlockedCount/$totalCount"
            }
            pLower.endsWith("_difficulty_status") && pLower.startsWith("achievement_") -> {
                val diff = pLower.removePrefix("achievement_").removeSuffix("_difficulty_status")
                val achievements = plugin.achievementManager.getAllAchievements().filter {
                    it.difficulty.lowercase() == diff
                }
                val unlockedIds = player?.let {
                    try {
                        val playerId = PlayerDAO.getPlayerId(it.name)
                        playerId?.let { id -> AchievementDAO.getPlayerAchievements(id) } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                } ?: emptyList()
                val unlockedCount = achievements.count { unlockedIds.contains(it.achievementId) }
                val totalCount = achievements.size
                return "$unlockedCount/$totalCount"
            }
        }

        // === INDIVIDUAL ACHIEVEMENT PLACEHOLDERS ===
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
                val unlockedIds = player?.let {
                    try {
                        val playerId = PlayerDAO.getPlayerId(it.name)
                        playerId?.let { pid -> AchievementDAO.getPlayerAchievements(pid) } ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                } ?: emptyList()
                return if (unlockedIds.contains(id)) "1" else "0"
            }
            regexProgress.matchEntire(pLower)?.let { m ->
                val id = m.groupValues[1]
                val achievement = plugin.achievementManager.getAchievement(id)
                val playerId = player?.let {
                    try {
                        PlayerDAO.getPlayerId(it.name)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (achievement != null && playerId != null) {
                    val progress = try {
                        plugin.achievementManager.getAchievementProgress(playerId, achievement)
                    } catch (e: Exception) {
                        0
                    }
                    val target = achievement.targetValue ?: 1
                    return "$progress/$target"
                } else return ""
            }
            regexPercent.matchEntire(pLower)?.let { m ->
                val id = m.groupValues[1]
                val achievement = plugin.achievementManager.getAchievement(id)
                val playerId = player?.let {
                    try {
                        PlayerDAO.getPlayerId(it.name)
                    } catch (e: Exception) {
                        null
                    }
                }
                if (achievement != null && playerId != null) {
                    val progress = try {
                        plugin.achievementManager.getAchievementProgress(playerId, achievement)
                    } catch (e: Exception) {
                        0
                    }
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

        return ""
    }

    // =================== HELPER METHODS ===================

    /**
     * Geeft een veld terug van de speler op een specifieke leaderboard-positie.
     * field: "" / "name" → spelernaam, "value" → nette weergavewaarde, "raw" → ruwe waarde.
     * Lege string als er niemand op die positie staat.
     */
    private fun getLeaderboardPositionSafe(type: String, position: Int, field: String): String {
        return try {
            val entry = LeaderboardCacheDAO.getEntryAtPosition(type, position) ?: return ""
            when (field) {
                "value" -> entry.rankDisplayValue.ifBlank { formatRawValue(entry.rankValue) }
                "raw" -> formatRawValue(entry.rankValue)
                else -> entry.playerName // "" of "name"
            }
        } catch (e: Exception) {
            ""
        }
    }

    /** Toont een hele waarde als integer, anders met 2 decimalen. */
    private fun formatRawValue(value: Double): String {
        return if (value == Math.floor(value) && !value.isInfinite()) {
            value.toLong().toString()
        } else {
            "%.2f".format(value)
        }
    }

    private fun getLeaderboardRankSafe(player: Player?, type: String): String {
        return player?.let {
            try {
                val playerId = PlayerDAO.getPlayerId(it.name) ?: return@let "Unranked"
                val rank = LeaderboardCacheDAO.getPlayerRank(playerId, type)
                if (rank == 999) "Unranked" else rank.toString()
            } catch (e: Exception) {
                "Unranked"
            }
        } ?: "Unranked"
    }

    /**
     * Safe wrapper for getting stats with error handling
     */
    private fun getStatSafe(player: Player?, statGetter: (String) -> String): String {
        return player?.let {
            try {
                val playerId = PlayerDAO.getPlayerId(it.name)
                playerId?.let { id -> statGetter(id) } ?: ""
            } catch (e: Exception) {
                ""
            }
        } ?: ""
    }

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

    private fun getTeamByIdentifier(gameInstance: org.Kloppie74.locoPillars.game.instance.GameInstance, teamIdentifier: String): Team? {
        val teams = gameInstance.arenaInstance.config.teams

        return if (teamIdentifier.startsWith("team") && teamIdentifier.length > 4) {
            val teamNumber = teamIdentifier.removePrefix("team").toIntOrNull()
            if (teamNumber != null && teamNumber > 0 && teamNumber <= teams.size) {
                teams[teamNumber - 1]
            } else null
        } else {
            teams.find { it.name.lowercase() == teamIdentifier.lowercase() }
        }
    }

    private data class TeamStatistics(
        val totalPlayers: Int,
        val alivePlayers: Int,
        val deadPlayers: Int,
        val status: String,
        val statusSymbol: String
    )

    private fun getTeamStatistics(gameInstance: org.Kloppie74.locoPillars.game.instance.GameInstance, teamIdentifier: String): TeamStatistics {
        val teams = gameInstance.arenaInstance.config.teams
        val currentPlayers = gameInstance.players

        val targetTeam = getTeamByIdentifier(gameInstance, teamIdentifier)

        if (targetTeam == null) {
            return TeamStatistics(0, 0, 0, "Unknown", "❓")
        }

        val teamPlayerUUIDs = getPlayersInTeam(gameInstance, targetTeam)
        val totalPlayersInTeam = teamPlayerUUIDs.size
        val alivePlayersInTeam = teamPlayerUUIDs.count { it in currentPlayers }
        val deadPlayersInTeam = totalPlayersInTeam - alivePlayersInTeam

        val status = when {
            totalPlayersInTeam == 0 -> "Empty"
            alivePlayersInTeam == 0 -> "Eliminated"
            alivePlayersInTeam == totalPlayersInTeam -> "Alive"
            else -> "Partial"
        }

        val statusSymbol = when {
            totalPlayersInTeam == 0 -> "⚪"
            alivePlayersInTeam == 0 -> "❌"
            alivePlayersInTeam == totalPlayersInTeam -> "✅"
            else -> "⚠️"
        }

        return TeamStatistics(
            totalPlayers = totalPlayersInTeam,
            alivePlayers = alivePlayersInTeam,
            deadPlayers = deadPlayersInTeam,
            status = status,
            statusSymbol = statusSymbol
        )
    }

    private fun getPlayersInTeam(gameInstance: org.Kloppie74.locoPillars.game.instance.GameInstance, team: Team): List<java.util.UUID> {
        val teamPlayers = mutableListOf<java.util.UUID>()
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

    private data class MapStatistics(
        val playersInLobby: Int,
        val playersPlaying: Int,
        val totalPlayers: Int,
        val gamesActive: Int,
        val gamesWaiting: Int,
        val gamesStarting: Int,
        val totalGames: Int
    )

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
                GameState.ENDED, GameState.RESETTING -> {}
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