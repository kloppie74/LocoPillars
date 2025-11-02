package org.Kloppie74.locoPillars.utils.teams.scoreboard

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG
import me.clip.placeholderapi.PlaceholderAPI
import java.util.concurrent.ConcurrentHashMap
import java.util.*

class ScoreboardManager(private val plugin: LocoPillars) {

    data class BoardSection(
        val enabled: Boolean,
        val title: String,
        val lines: LinkedHashMap<String, String>
    )

    private var globalEnabled: Boolean = true
    private val boardSections = HashMap<String, BoardSection>()
    private val playerScoreboardState = ConcurrentHashMap<UUID, String>()
    private val playerScoreboards = ConcurrentHashMap<UUID, ScoreHelper>()
    private val playerToggledScoreboards = ConcurrentHashMap<UUID, Boolean>()
    private var updateTask: BukkitRunnable? = null

    init {
        loadConfig()
    }

    fun loadConfig() {
        val config = plugin.configManager.getConfig()
        globalEnabled = config.getBoolean("Scoreboards.GlobalEnabled", true)
        debugLog("Global enabled loaded: $globalEnabled")

        val scoreboardsSection = config.getConfigurationSection("Scoreboards") ?: run {
            plugin.logger.warning("[ScoreboardManager] No 'Scoreboards' section found in config!")
            return
        }

        boardSections.clear()
        for (sectionName in scoreboardsSection.getKeys(false)) {
            if (sectionName == "GlobalEnabled") continue
            val section = scoreboardsSection.getConfigurationSection(sectionName) ?: continue
            val enabled = section.getBoolean("Enabled", true)
            val title = section.getString("Title") ?: ""
            val linesSection = section.getConfigurationSection("Lines")
            val lines = LinkedHashMap<String, String>()
            // LET OP: YAML keys zijn in insertion order!
            if (linesSection != null) {
                for (key in linesSection.getKeys(false)) {
                    lines[key] = linesSection.getString(key) ?: ""
                }
            }
            boardSections[sectionName] = BoardSection(enabled, title, lines)
            debugLog("Loaded board section '$sectionName' - enabled: $enabled, lines: ${lines.size}")
        }
        debugLog("Config loaded - globalEnabled: $globalEnabled, sections: ${boardSections.keys}")
    }

    fun enable() {
        globalEnabled = true
        startAutoUpdate()
        forceUpdateAllPlayers()
        debugLog("Scoreboard system enabled")
    }

    fun disable() {
        globalEnabled = false
        stopAutoUpdate()
        Bukkit.getOnlinePlayers().forEach { player ->
            clearPlayerScoreboard(player)
        }
        debugLog("Scoreboard system disabled")
    }

    fun reload() {
        debugLog("Reloading scoreboard configuration...")
        loadConfig()
        forceUpdateAllPlayers()
        if (globalEnabled) {
            startAutoUpdate()
        }
        debugLog("Scoreboard configuration reloaded")
    }

    fun isEnabled(): Boolean = globalEnabled

    fun togglePlayerScoreboard(player: Player): Boolean {
        val newState = !isPlayerScoreboardEnabled(player)
        playerToggledScoreboards[player.uniqueId] = !newState

        if (newState) {
            updatePlayerScoreboard(player)
            debugLog("Enabled scoreboard for ${player.name}")
        } else {
            clearPlayerScoreboard(player)
            debugLog("Disabled scoreboard for ${player.name}")
        }
        return newState
    }

    fun isPlayerScoreboardEnabled(player: Player): Boolean {
        if (!globalEnabled) return false
        val playerToggled = playerToggledScoreboards[player.uniqueId] ?: false
        return !playerToggled
    }

    fun updatePlayerScoreboard(player: Player, sectionType: String? = null) {
        if (!globalEnabled || !isPlayerScoreboardEnabled(player)) {
            clearPlayerScoreboard(player)
            return
        }

        try {
            val type = sectionType ?: getDefaultTypeForPlayer(player)
            val section = boardSections[type]

            debugLog("Updating scoreboard for ${player.name} with type '$type' - section found: ${section != null}")

            if (section == null || !section.enabled) {
                debugLog("Section '$type' is null or disabled for ${player.name}")
                clearPlayerScoreboard(player)
                return
            }

            val prevType = playerScoreboardState[player.uniqueId]
            var scoreHelper = playerScoreboards[player.uniqueId]

            if (scoreHelper == null || prevType != type) {
                clearPlayerScoreboard(player)
                scoreHelper = ScoreHelper.createScore(player)
                if (scoreHelper != null) {
                    playerScoreboards[player.uniqueId] = scoreHelper
                    debugLog("Created new ScoreHelper for ${player.name}")
                }
            }

            if (scoreHelper != null) {
                val titleWithPlaceholders = MSG.chatColors(PlaceholderAPI.setPlaceholders(player, section.title))
                scoreHelper.setTitle(titleWithPlaceholders)

                val processedLines = parseConfigLines(section.lines, player)
                scoreHelper.setSlotsFromList(processedLines)

                // FIXED: Use UNIQUE sidebar teams that don't conflict with tab teams!
                updateSidebarTeams(player, processedLines)

                playerScoreboardState[player.uniqueId] = type
                player.scoreboard = scoreHelper.scoreboard
                debugLog("Successfully updated scoreboard for ${player.name} with ${processedLines.size} lines")
            } else {
                debugLog("Failed to create ScoreHelper for ${player.name}")
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ScoreboardManager] Error updating scoreboard for ${player.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Parse config lines: genummerde lines worden gesorteerd op nummer (ASC),
     * if-statements blijven op hun YAML positie en schuiven genummerde lines op.
     */
    private fun parseConfigLines(lines: LinkedHashMap<String, String>, player: Player): List<String> {
        val result = mutableListOf<String>()

        // Eerst alle genummerde lines verzamelen en sorteren
        val numericLines = mutableMapOf<Int, String>()

        // Verzamel alle genummerde lines
        for ((key, value) in lines.entries) {
            val numKey = key.toIntOrNull()
            if (numKey != null) {
                numericLines[numKey] = value
            }
        }

        // Sorteer genummerde lines in ascending order (1, 2, 3, etc.)
        val sortedNumericLines = numericLines.toSortedMap().values.toMutableList()

        // Nu door de originele YAML volgorde gaan en dynamisch lines toewijzen
        val orderedKeys = lines.keys.toList()
        var numericIndex = 0

        for (key in orderedKeys) {
            if (key.toIntOrNull() != null) {
                // Dit is een genummerde line - neem volgende uit gesorteerde lijst
                if (numericIndex < sortedNumericLines.size) {
                    var line = sortedNumericLines[numericIndex]
                    line = PlaceholderAPI.setPlaceholders(player, line)
                    line = MSG.chatColors(line)
                    result.add(line)
                    numericIndex++
                }
            } else if (key.startsWith("if")) {
                // Dit is een if-statement - verwerk direct op deze positie
                val value = lines[key] ?: continue

                val regexBracket = Regex("""(.+?)\s+LINE=\[(.+)]""")
                val regexQuote = Regex("""(.+?)\s+LINE="(.+)"""")
                val matchBracket = regexBracket.find(value)
                val matchQuote = regexQuote.find(value)

                var condition = ""
                var lineText = ""

                if (matchBracket != null) {
                    condition = matchBracket.groups[1]?.value?.trim() ?: ""
                    lineText = matchBracket.groups[2]?.value ?: ""
                } else if (matchQuote != null) {
                    condition = matchQuote.groups[1]?.value?.trim() ?: ""
                    lineText = matchQuote.groups[2]?.value ?: ""
                }

                // Alleen toevoegen als condition waar is
                if (condition.isNotEmpty() && lineText.isNotEmpty() && evaluateCondition(condition, player)) {
                    lineText = PlaceholderAPI.setPlaceholders(player, lineText)
                    lineText = MSG.chatColors(lineText)
                    result.add(lineText)
                }
            }
            // Andere keys worden genegeerd
        }

        return result
    }

    private fun evaluateCondition(condition: String, player: Player): Boolean {
        val match = Regex("""TEAM_AMOUNT\s*(>=|<=|==|!=|>|<)\s*(\d+)""").find(condition)
        if (match != null) {
            val op = match.groups[1]?.value ?: "=="
            val reqValue = match.groups[2]?.value?.toIntOrNull() ?: 0
            val actual = getTeamAmount(player)
            return when (op) {
                ">=" -> actual >= reqValue
                "<=" -> actual <= reqValue
                "==" -> actual == reqValue
                "!=" -> actual != reqValue
                ">"  -> actual > reqValue
                "<"  -> actual < reqValue
                else -> false
            }
        }
        return false
    }

    private fun getTeamAmount(player: Player): Int {
        val instance = plugin.gameManager.getPlayerInstance(player)
        return instance?.arenaInstance?.config?.teams?.size ?: 0
    }

    /**
     * FIXED: Create UNIQUE sidebar teams that don't conflict with tab teams!
     */
    private fun updateSidebarTeams(player: Player, lines: List<String>) {
        for (i in 1..lines.size.coerceAtMost(15)) {
            val entry = ScoreHelper.ENTRIES[i]
            val line = lines[i - 1]
            if (line.isNotEmpty()) {
                val (prefix, suffix) = extractPrefixSuffix(line)
                // FIXED: Use unique prefix for sidebar teams to avoid conflicts!
                plugin.teamManager.createSidebarTeam(player, i, entry, prefix, suffix)
            } else {
                plugin.teamManager.removeSidebarTeam(player, i, entry)
            }
        }
    }

    private fun extractPrefixSuffix(line: String): Pair<String, String> {
        val maxPrefixLength = 64
        val maxSuffixLength = 64

        return if (line.length <= maxPrefixLength) {
            Pair(line, "")
        } else {
            var splitPos = maxPrefixLength
            if (splitPos < line.length) {
                if (line[splitPos - 1] == '§') splitPos--
                else if (splitPos >= 2 && line[splitPos - 2] == '§') splitPos -= 2
            }
            val prefix = line.substring(0, splitPos)
            val lastColors = getLastColors(prefix)
            val remainingLength = (line.length - splitPos).coerceAtMost(maxSuffixLength - lastColors.length)
            val suffix = if (remainingLength > 0) {
                lastColors + line.substring(splitPos, splitPos + remainingLength)
            } else {
                lastColors
            }
            Pair(prefix, suffix)
        }
    }

    private fun getLastColors(text: String): String {
        if (text.isEmpty()) return ""
        val result = StringBuilder()
        var lastColor = ""
        var lastFormat = ""
        var hasColor = false
        var i = 0
        while (i < text.length - 1) {
            if (text[i] == '§') {
                val colorChar = text[i + 1]
                if (colorChar == 'x' && i + 13 < text.length) {
                    var validHex = true
                    for (j in 0..5) {
                        val pos = i + 2 + (j * 2)
                        if (pos + 1 >= text.length || text[pos] != '§') {
                            validHex = false
                            break
                        }
                    }
                    if (validHex) {
                        lastColor = text.substring(i, i + 14)
                        hasColor = true
                        i += 13
                    }
                } else if (isColorChar(colorChar)) {
                    lastColor = text.substring(i, i + 2)
                    hasColor = true
                    i++
                } else if (isFormatChar(colorChar)) {
                    lastFormat += text.substring(i, i + 2)
                    i++
                }
            }
            i++
        }
        if (hasColor) result.append(lastColor)
        result.append(lastFormat)
        return result.toString()
    }

    private fun isColorChar(c: Char) =
        c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' || c == 'r'

    private fun isFormatChar(c: Char) =
        c == 'k' || c == 'l' || c == 'm' || c == 'n' || c == 'o'

    fun clearPlayerScoreboard(player: Player) {
        try {
            val scoreHelper = playerScoreboards.remove(player.uniqueId)
            scoreHelper?.let { ScoreHelper.removeScore(player) }
            playerScoreboardState.remove(player.uniqueId)

            // FIXED: Clean up sidebar teams
            for (i in 1..15) {
                val entry = ScoreHelper.ENTRIES[i]
                plugin.teamManager.removeSidebarTeam(player, i, entry)
            }

            debugLog("Cleared scoreboard for ${player.name}")
        } catch (e: Exception) {
            plugin.logger.warning("[ScoreboardManager] Error clearing scoreboard for ${player.name}: ${e.message}")
        }
    }

    fun updateAllScoreboards() {
        if (!globalEnabled) return
        try {
            Bukkit.getOnlinePlayers().forEach { player ->
                updatePlayerScoreboard(player)
            }
        } catch (e: Exception) {
            plugin.logger.warning("[ScoreboardManager] Error updating all scoreboards: ${e.message}")
        }
    }

    fun forceUpdateAllPlayers() {
        playerScoreboardState.clear()
        updateAllScoreboards()
        debugLog("Forced update completed for all players")
    }

    private fun getDefaultTypeForPlayer(player: Player): String {
        val instance = plugin.gameManager.getPlayerInstance(player)
        val type = when {
            instance == null -> "Lobby"
            instance.state == org.Kloppie74.locoPillars.game.data.GameState.WAITING -> "GameLobby"
            instance.state == org.Kloppie74.locoPillars.game.data.GameState.STARTING -> "Cage"
            instance.state == org.Kloppie74.locoPillars.game.data.GameState.ACTIVE && player.gameMode == org.bukkit.GameMode.SPECTATOR -> "Spectate"
            instance.state == org.Kloppie74.locoPillars.game.data.GameState.ACTIVE -> "Game"
            instance.state == org.Kloppie74.locoPillars.game.data.GameState.ENDED || instance.state == org.Kloppie74.locoPillars.game.data.GameState.RESETTING -> "Spectate"
            else -> "Lobby"
        }
        debugLog("Determined type '$type' for player ${player.name}")
        return type
    }

    private fun startAutoUpdate() {
        if (!globalEnabled) return
        stopAutoUpdate()
        updateTask = object : BukkitRunnable() {
            override fun run() {
                try {
                    updateAllScoreboards()
                } catch (e: Exception) {
                    plugin.logger.warning("[ScoreboardManager] Error in auto-update: ${e.message}")
                }
            }
        }
        updateTask?.runTaskTimer(plugin, 20L, 20L)
        debugLog("Auto-update started")
    }

    private fun stopAutoUpdate() {
        updateTask?.cancel()
        updateTask = null
        debugLog("Auto-update stopped")
    }

    fun onPlayerJoin(player: Player) {
        try {
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                updatePlayerScoreboard(player)
            }, 10L)
        } catch (e: Exception) {
            plugin.logger.warning("[ScoreboardManager] Error handling player join for ${player.name}: ${e.message}")
        }
    }

    fun onPlayerQuit(player: Player) {
        try {
            clearPlayerScoreboard(player)
            playerToggledScoreboards.remove(player.uniqueId)
        } catch (e: Exception) {
            plugin.logger.warning("[ScoreboardManager] Error handling player quit for ${player.name}: ${e.message}")
        }
    }

    fun onPlayerJoinGame(player: Player) {
        try {
            playerScoreboardState.remove(player.uniqueId)
            updatePlayerScoreboard(player)
        } catch (e: Exception) {
            plugin.logger.warning("[ScoreboardManager] Error handling player join game for ${player.name}: ${e.message}")
        }
    }

    fun onPlayerLeaveGame(player: Player) {
        try {
            playerScoreboardState.remove(player.uniqueId)
            updatePlayerScoreboard(player)
        } catch (e: Exception) {
            plugin.logger.warning("[ScoreboardManager] Error handling player leave game for ${player.name}: ${e.message}")
        }
    }

    fun getStats(): Map<String, Any> {
        return mapOf(
            "globalEnabled" to globalEnabled,
            "activeScoreboards" to playerScoreboards.size,
            "boardSections" to boardSections.size,
            "playerStates" to playerScoreboardState.size,
            "updateTaskActive" to (updateTask != null && !updateTask!!.isCancelled),
            "playersWithToggledScoreboards" to playerToggledScoreboards.size,
            "onlinePlayers" to Bukkit.getOnlinePlayers().size
        )
    }

    fun cleanup() {
        try {
            stopAutoUpdate()
            Bukkit.getOnlinePlayers().forEach { player ->
                clearPlayerScoreboard(player)
            }
            playerScoreboards.clear()
            playerScoreboardState.clear()
            playerToggledScoreboards.clear()
            debugLog("Cleanup completed")
        } catch (e: Exception) {
            plugin.logger.warning("[ScoreboardManager] Error during cleanup: ${e.message}")
        }
    }

    private fun debugLog(message: String) {
        try {
            if (plugin.configManager.debugMode) {
                plugin.logger.info("[ScoreboardManager] $message")
            }
        } catch (_: Exception) {}
    }
}