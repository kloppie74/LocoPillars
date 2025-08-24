package org.Kloppie74.locoPillars.utils.scoreboards

import org.bukkit.entity.Player
import org.bukkit.Bukkit
import org.bukkit.plugin.Plugin
import org.bukkit.configuration.file.FileConfiguration
import org.Kloppie74.locoPillars.utils.MSG
import me.clip.placeholderapi.PlaceholderAPI
import java.util.*

class ScoreboardManager(
    private val plugin: Plugin,
    private val config: FileConfiguration
) {
    data class BoardSection(
        val enabled: Boolean,
        val title: String,
        val lines: LinkedHashMap<String, String>
    )

    private val boardSections = HashMap<String, BoardSection>()
    private val playerScoreboardState = HashMap<UUID, String>()

    // Caching PlaceholderAPI results per player, per scoreboard "type"
    private val placeholderCache = mutableMapOf<Pair<UUID, String>, CachedScoreboard>()
    private val PLACEHOLDER_CACHE_MS = 2000L // 2 sec cache for scoreboard lines

    data class CachedScoreboard(
        val timestamp: Long,
        val title: String,
        val lines: List<String>
    )

    fun initialize() {
        boardSections.clear()
        val rootSection = config.getConfigurationSection("ScoreBoards")
        if (rootSection == null) {
            plugin.logger.warning("[ScoreboardManager] No ScoreBoards section found in config")
            return
        }
        for (sectionName in rootSection.getKeys(false)) {
            val section = rootSection.getConfigurationSection(sectionName)
            if (section == null) continue
            val enabled = section.getBoolean("Enabled", true)
            val title = MSG.chatColors(section.getString("Title") ?: "")
            val linesSection = section.getConfigurationSection("Lines")
            val lines = LinkedHashMap<String, String>()
            linesSection?.getKeys(false)?.sortedBy { it.toIntOrNull() ?: Int.MAX_VALUE }?.forEach { key ->
                lines[key] = MSG.chatColors(linesSection.getString(key) ?: "")
            }
            boardSections[sectionName] = BoardSection(enabled, title, lines)
        }
        plugin.logger.info("[ScoreboardManager] Loaded scoreboard sections: ${boardSections.keys}")
    }

    fun reloadConfigs() {
        initialize()
        clearPlaceholderCache()
        updateAllScoreboards()
    }

    fun getBoardSection(type: String): BoardSection? = boardSections[type]

    fun updateScoreboard(player: Player, sectionType: String? = null) {
        val type = sectionType ?: getDefaultTypeForPlayer(player)
        val section = getBoardSection(type) ?: return clearScore(player)
        if (!section.enabled) return clearScore(player)
        val prevType = playerScoreboardState[player.uniqueId]
        if (prevType != type) ScoreHelper.removeScore(player)

        // CACHE PATCH
        val cacheKey = Pair(player.uniqueId, type)
        val now = System.currentTimeMillis()
        val cached = placeholderCache[cacheKey]
        val useCache = cached != null && now - cached.timestamp < PLACEHOLDER_CACHE_MS

        val titleWithPlaceholders: String
        val replacedLines: List<String>
        if (useCache) {
            titleWithPlaceholders = cached!!.title
            replacedLines = cached.lines
        } else {
            // Only run PlaceholderAPI when needed
            titleWithPlaceholders = PlaceholderAPI.setPlaceholders(player, section.title)
            replacedLines = section.lines.values.map { line ->
                val lineWithPlaceholders = if (line.contains("%")) PlaceholderAPI.setPlaceholders(player, line) else line
                replacePlaceholders(lineWithPlaceholders, player)
            }
            placeholderCache[cacheKey] = CachedScoreboard(now, titleWithPlaceholders, replacedLines)
        }

        val helper = ScoreHelper.getByPlayer(player) ?: ScoreHelper.createScore(player)
        helper?.let {
            it.setTitle(titleWithPlaceholders)
            it.setSlotsFromList(replacedLines)
            playerScoreboardState[player.uniqueId] = type
        }
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    }

    fun clearScore(player: Player) {
        ScoreHelper.clearScoreboard(player)
        playerScoreboardState.remove(player.uniqueId)
        player.scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    }

    fun updateAllScoreboards() {
        Bukkit.getOnlinePlayers().forEach { updateScoreboard(it) }
    }

    fun clearPlaceholderCache() {
        placeholderCache.clear()
    }

    // Detect game phase/type
    fun getDefaultTypeForPlayer(player: Player): String {
        val locoPillars = plugin as? org.Kloppie74.locoPillars.LocoPillars ?: return "Lobby"
        val instance = locoPillars.gameManager.getPlayerInstance(player)
        return when {
            instance == null -> "Lobby"
            instance.state == org.Kloppie74.locoPillars.game.data.GameState.WAITING -> "GameLobby"
            instance.state == org.Kloppie74.locoPillars.game.data.GameState.STARTING -> "Cage"
            instance.state == org.Kloppie74.locoPillars.game.data.GameState.ACTIVE && player.gameMode == org.bukkit.GameMode.SPECTATOR -> "Spectate"
            instance.state == org.Kloppie74.locoPillars.game.data.GameState.ACTIVE -> "Game"
            instance.state == org.Kloppie74.locoPillars.game.data.GameState.ENDED || instance.state == org.Kloppie74.locoPillars.game.data.GameState.RESETTING -> "Spectate"
            else -> "Lobby"
        }
    }

    // Patch: Only run PlaceholderAPI when needed (lines with %)
    private fun replacePlaceholders(line: String, player: Player): String {
        val locoPillars = plugin as? org.Kloppie74.locoPillars.LocoPillars
        var out = line
        out = out.replace("%online_players%", Bukkit.getOnlinePlayers().size.toString())
        if (locoPillars != null) {
            out = out.replace("%active_games%", locoPillars.gameManager.getActiveGames().size.toString())
            val instance = locoPillars.gameManager.getPlayerInstance(player)
            if (instance != null) {
                out = out.replace("%game_name%", instance.getDisplayName())
                out = out.replace("%players%", instance.players.size.toString())
                out = out.replace("%max_players%", instance.getMaxPlayers().toString())
                out = out.replace("%map_name%", instance.arenaInstance.template.name)
                out = out.replace("%game_timer%", locoPillars.gameManager.timerEvent.getGameTimeLeft(instance.id)?.toString() ?: "-")
                out = out.replace("%cage_timer%", instance.arenaInstance.config.getCageTimer().toString())
                out = out.replace("%players_left%", instance.players.size.toString())
                out = out.replace("%team_name%", "n/a")
            }
        }
        // Altijd PlaceholderAPI aan het eind!
        return MSG.chatColors(if (out.contains("%")) PlaceholderAPI.setPlaceholders(player, out) else out)
    }
}