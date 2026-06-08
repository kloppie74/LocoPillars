package org.Kloppie74.locoPillars.utils.teams.scoreboard

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import me.clip.placeholderapi.PlaceholderAPI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ScoreboardManager — volledig herschreven met Paper 1.21 Score.customName() API.
 *
 * VORIGE aanpak (team-gebaseerd):
 *   - Sidebar-tekst via §1–§f entries + sb_-teams (prefix/suffix).
 *   - Race-condition: score-packet arriveerde VOOR het team-packet → client zag
 *     de raw entry string (bijv. "§1" = letterlijk "§1") tijdelijk als regel-tekst.
 *   - Gevolg: speler zag korte flitsen van nummers/codes in de sidebar.
 *
 * NIEUWE aanpak (customName):
 *   - Tekst direct in Score.customName(Component) — zit in hetzelfde Set-Score-packet
 *     als de score-waarde → client ontvangt tekst en positie tegelijk.
 *   - Geen sidebar-teams meer nodig. TeamManager beheert alleen nog tab-teams.
 *   - Stabiele objective-naam per speler (UUID-hash, nooit milliseconden-conflict).
 */
class ScoreboardManager(private val plugin: LocoPillars) {

    companion object {
        // Pre-compiled regexes for if-condition LINE= parsing (reused every scoreboard tick)
        private val REGEX_IF_BRACKET = Regex("""(.+?)\s+LINE=\[([^\]]*)]""")  // LINE=[text] or LINE=[]
        private val REGEX_IF_QUOTE   = Regex("""(.+?)\s+LINE="(.+)"""")       // LINE="text"
        private val REGEX_IF_BARE    = Regex("""(.+?)\s+LINE=(.+)""")         // LINE=text (bare)

        /** Entry-key prefix voor sidebar-scores. Nooit een naam van een speler. */
        private const val ENTRY_PREFIX = "_sb_"
    }

    data class BoardSection(
        val enabled: Boolean,
        val title: String,
        val lines: LinkedHashMap<String, String>
    )

    // Per-player scoreboard state
    private data class PlayerBoard(
        val scoreboard: Scoreboard,
        val objective: Objective
    )

    private var globalEnabled: Boolean = true
    private val boardSections = HashMap<String, BoardSection>()
    private val playerSectionType  = ConcurrentHashMap<UUID, String>()        // last rendered section
    private val playerBoards       = ConcurrentHashMap<UUID, PlayerBoard>()    // scoreboard + objective
    private val playerActiveEntries = ConcurrentHashMap<UUID, Set<String>>()   // currently live entries
    private val playerToggledScoreboards = ConcurrentHashMap<UUID, Boolean>()
    private var updateTask: BukkitRunnable? = null

    init {
        loadConfig()
        if (globalEnabled) startAutoUpdate()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Config & lifecycle
    // ──────────────────────────────────────────────────────────────────────

    fun loadConfig() {
        val config = plugin.configManager.getConfig()
        globalEnabled = config.getBoolean("Scoreboards.GlobalEnabled", true)

        val scoreboardsSection = config.getConfigurationSection("Scoreboards") ?: run {
            plugin.logger.warning("[ScoreboardManager] No 'Scoreboards' section found in config!")
            return
        }

        boardSections.clear()
        for (sectionName in scoreboardsSection.getKeys(false)) {
            if (sectionName == "GlobalEnabled") continue
            val section = scoreboardsSection.getConfigurationSection(sectionName) ?: continue
            val enabled = section.getBoolean("Enabled", true)
            val title   = section.getString("Title") ?: ""
            val linesSection = section.getConfigurationSection("Lines")
            val lines = LinkedHashMap<String, String>()
            linesSection?.getKeys(false)?.forEach { key ->
                lines[key] = linesSection.getString(key) ?: ""
            }
            boardSections[sectionName] = BoardSection(enabled, title, lines)
            debugLog("Loaded section '$sectionName' — enabled=$enabled, lines=${lines.size}")
        }
        debugLog("Config loaded — globalEnabled=$globalEnabled, sections=${boardSections.keys}")
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
        Bukkit.getOnlinePlayers().forEach { clearPlayerScoreboard(it) }
        debugLog("Scoreboard system disabled")
    }

    fun reload() {
        loadConfig()
        forceUpdateAllPlayers()
        if (globalEnabled) startAutoUpdate()
    }

    fun isEnabled(): Boolean = globalEnabled

    // ──────────────────────────────────────────────────────────────────────
    // Player toggle
    // ──────────────────────────────────────────────────────────────────────

    fun togglePlayerScoreboard(player: Player): Boolean {
        val newState = !isPlayerScoreboardEnabled(player)
        playerToggledScoreboards[player.uniqueId] = !newState
        if (newState) updatePlayerScoreboard(player) else clearPlayerScoreboard(player)
        return newState
    }

    fun isPlayerScoreboardEnabled(player: Player): Boolean {
        if (!globalEnabled) return false
        return !(playerToggledScoreboards[player.uniqueId] ?: false)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Core update
    // ──────────────────────────────────────────────────────────────────────

    fun updatePlayerScoreboard(player: Player, sectionType: String? = null) {
        if (!globalEnabled || !isPlayerScoreboardEnabled(player)) {
            clearPlayerScoreboard(player)
            return
        }

        try {
            val type    = sectionType ?: getDefaultTypeForPlayer(player)
            val section = boardSections[type]

            if (section == null || !section.enabled) {
                clearPlayerScoreboard(player)
                return
            }

            // If type changed, destroy the old board so the objective is recreated cleanly.
            if (playerSectionType[player.uniqueId] != type) {
                destroyBoard(player)
            }

            val board = getOrCreateBoard(player)

            // --- Title ---
            val title = try {
                MSG.chatColors(PlaceholderAPI.setPlaceholders(player, section.title))
            } catch (e: Exception) {
                debugLog("PlaceholderAPI error in title for ${player.name}: ${e.message}")
                MSG.chatColors(section.title)
            }
            board.objective.displayName(
                LegacyComponentSerializer.legacySection().deserialize(title)
            )

            // --- Lines ---
            val processedLines = try {
                parseConfigLines(section.lines, player)
            } catch (e: Exception) {
                plugin.logger.warning("[ScoreboardManager] Error parsing lines for ${player.name}: ${e.message}")
                emptyMap()
            }

            // Build new entry set
            val newEntries = mutableSetOf<String>()
            processedLines.forEach { (score, text) ->
                val entryKey = "$ENTRY_PREFIX$score"
                newEntries.add(entryKey)

                val s = board.objective.getScore(entryKey)
                s.score = score

                // Paper 1.21: Score.customName(Component) — sets display text directly on the
                // score. The text travels in the same Set-Score packet as the score value,
                // so the client always receives them together. No sidebar teams needed.
                try {
                    s.customName(LegacyComponentSerializer.legacySection().deserialize(text))
                } catch (e: Exception) {
                    // Fallback: if the server somehow doesn't support customName yet
                    debugLog("customName not supported for ${player.name} slot $score: ${e.message}")
                }
            }

            // Remove stale entries (from previous frame or section type)
            val prevEntries = playerActiveEntries[player.uniqueId] ?: emptySet()
            (prevEntries - newEntries).forEach { stale ->
                try { board.scoreboard.resetScores(stale) } catch (_: Exception) {}
            }
            playerActiveEntries[player.uniqueId] = newEntries

            playerSectionType[player.uniqueId] = type

            // Assign scoreboard exactly once per update. Paper can reset player.scoreboard
            // during respawn/teleport/world-change — always re-check reference equality.
            if (player.scoreboard !== board.scoreboard) {
                player.scoreboard = board.scoreboard
            }

            debugLog("Updated scoreboard for ${player.name} — type=$type, lines=${processedLines.size}")

        } catch (e: Exception) {
            plugin.logger.warning("[ScoreboardManager] Error updating scoreboard for ${player.name}: ${e.message}")
            // Safety net: if something threw AFTER the board was created, still ensure the
            // scoreboard is assigned so the player doesn't lose their sidebar entirely.
            try {
                val existingBoard = playerBoards[player.uniqueId]
                if (existingBoard != null && player.isOnline) {
                    player.scoreboard = existingBoard.scoreboard
                }
            } catch (_: Exception) {}
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Board management
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Get or create the player's dedicated scoreboard + sidebar objective.
     * The scoreboard is shared with the tab-team system (TeamManager also uses it).
     */
    private fun getOrCreateBoard(player: Player): PlayerBoard {
        return playerBoards.getOrPut(player.uniqueId) {
            val scoreboard = plugin.teamManager.getPlayerScoreboard(player)

            // Remove any lingering sidebar objectives (avoid duplicate display)
            scoreboard.objectives
                .filter { it.displaySlot == DisplaySlot.SIDEBAR }
                .forEach { try { it.unregister() } catch (_: Exception) {} }

            // Stable objective name: "lpsb" + first 10 hex chars of UUID = 14 chars (≤ 16 limit)
            val objName = "lpsb" + player.uniqueId.toString().replace("-", "").take(10)
            val obj = try {
                scoreboard.registerNewObjective(
                    objName,
                    Criteria.DUMMY,
                    LegacyComponentSerializer.legacySection().deserialize("§f")
                )
            } catch (e: Exception) {
                // Name already registered (rare — reuse it)
                scoreboard.getObjective(objName)
                    ?: throw RuntimeException("Cannot create sidebar objective for ${player.name}: ${e.message}")
            }
            obj.displaySlot = DisplaySlot.SIDEBAR

            player.scoreboard = scoreboard
            debugLog("Created board for ${player.name} — objective='$objName'")
            PlayerBoard(scoreboard, obj)
        }
    }

    /** Destroy a player's board (unregister objective, clear caches). */
    private fun destroyBoard(player: Player) {
        val board = playerBoards.remove(player.uniqueId) ?: return
        try { board.objective.unregister() } catch (_: Exception) {}
        playerActiveEntries.remove(player.uniqueId)
        debugLog("Destroyed board for ${player.name}")
    }

    // ──────────────────────────────────────────────────────────────────────
    // Clear
    // ──────────────────────────────────────────────────────────────────────

    fun clearPlayerScoreboard(player: Player) {
        try {
            destroyBoard(player)
            playerSectionType.remove(player.uniqueId)
            debugLog("Cleared scoreboard for ${player.name}")
        } catch (e: Exception) {
            plugin.logger.warning("[ScoreboardManager] Error clearing scoreboard for ${player.name}: ${e.message}")
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Bulk updates
    // ──────────────────────────────────────────────────────────────────────

    fun updateAllScoreboards() {
        if (!globalEnabled) return
        Bukkit.getOnlinePlayers().forEach { updatePlayerScoreboard(it) }
    }

    fun forceUpdateAllPlayers() {
        playerSectionType.clear()
        updateAllScoreboards()
        debugLog("Force update completed for all players")
    }

    // ──────────────────────────────────────────────────────────────────────
    // Section type resolution
    // ──────────────────────────────────────────────────────────────────────

    private fun getDefaultTypeForPlayer(player: Player): String {
        if (plugin.adminEditorManager.isActive(player)) return "Editor"

        if (SpectatorUtils.isSpectator(player, plugin)) return "Spectate"

        val instance = plugin.gameManager.getPlayerInstance(player)
        return when {
            instance == null -> "Lobby"
            instance.state == GameState.WAITING  -> "GameLobby"
            instance.state == GameState.STARTING -> "Cage"
            instance.state == GameState.ACTIVE   -> "Game"
            instance.state == GameState.ENDED || instance.state == GameState.RESETTING -> "Spectate"
            else -> "Lobby"
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Config line parsing (unchanged logic)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Parse config lines into a score→text map.
     * Numeric keys become their Minecraft sidebar score directly (higher = higher on screen).
     * if-condition keys are evaluated in YAML order and placed above the highest numeric key.
     */
    private fun parseConfigLines(lines: LinkedHashMap<String, String>, player: Player): Map<Int, String> {
        val result = mutableMapOf<Int, String>()

        var maxNumericKey = 0
        for (key in lines.keys) {
            val n = key.toIntOrNull()
            if (n != null && n > maxNumericKey) maxNumericKey = n
        }

        var ifCounter = 0
        for ((key, value) in lines.entries) {
            val numKey = key.toIntOrNull()
            when {
                numKey != null -> {
                    result[numKey] = MSG.chatColors(PlaceholderAPI.setPlaceholders(player, value))
                }
                key.startsWith("if") -> {
                    val matchBracket = REGEX_IF_BRACKET.find(value)
                    val matchQuote   = REGEX_IF_QUOTE.find(value)
                    val matchBare    = REGEX_IF_BARE.find(value)

                    var condition = ""
                    var lineText  = ""
                    var isSpacer  = false

                    when {
                        matchBracket != null -> {
                            condition = matchBracket.groups[1]?.value?.trim() ?: ""
                            lineText  = matchBracket.groups[2]?.value ?: ""
                            isSpacer  = lineText.isEmpty()
                        }
                        matchQuote != null -> {
                            condition = matchQuote.groups[1]?.value?.trim() ?: ""
                            lineText  = matchQuote.groups[2]?.value ?: ""
                        }
                        matchBare != null -> {
                            condition = matchBare.groups[1]?.value?.trim() ?: ""
                            lineText  = matchBare.groups[2]?.value ?: ""
                        }
                    }

                    if (condition.isNotEmpty() && evaluateCondition(condition, player)) {
                        ifCounter++
                        val score = maxNumericKey + ifCounter
                        result[score] = when {
                            isSpacer              -> " "
                            lineText.isNotEmpty() -> MSG.chatColors(PlaceholderAPI.setPlaceholders(player, lineText))
                            else                  -> " "
                        }
                    }
                }
            }
        }

        return result
    }

    private fun evaluateCondition(condition: String, player: Player): Boolean {
        val match = Regex("""TEAM_AMOUNT\s*(>=|<=|==|!=|>|<)\s*(\d+)""").find(condition)
        if (match != null) {
            val op       = match.groups[1]?.value ?: "=="
            val reqValue = match.groups[2]?.value?.toIntOrNull() ?: 0
            val actual   = getTeamAmount(player)
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
            ?: plugin.gameManager.getSpectatorInstance(player)
        return instance?.getActiveTeamCount() ?: 0
    }

    // ──────────────────────────────────────────────────────────────────────
    // Auto-update timer
    // ──────────────────────────────────────────────────────────────────────

    private fun startAutoUpdate() {
        if (!globalEnabled) return
        stopAutoUpdate()
        updateTask = object : BukkitRunnable() {
            override fun run() {
                try { updateAllScoreboards() } catch (e: Exception) {
                    plugin.logger.warning("[ScoreboardManager] Error in auto-update: ${e.message}")
                }
            }
        }
        updateTask?.runTaskTimer(plugin, 20L, 20L)
        debugLog("Auto-update started (every 20 ticks)")
    }

    private fun stopAutoUpdate() {
        updateTask?.cancel()
        updateTask = null
    }

    // ──────────────────────────────────────────────────────────────────────
    // Lifecycle hooks
    // ──────────────────────────────────────────────────────────────────────

    fun onPlayerJoin(player: Player) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            updatePlayerScoreboard(player)
        }, 10L)
    }

    fun onPlayerQuit(player: Player) {
        clearPlayerScoreboard(player)
        playerToggledScoreboards.remove(player.uniqueId)
    }

    fun onPlayerJoinGame(player: Player) {
        playerSectionType.remove(player.uniqueId)
        updatePlayerScoreboard(player)
    }

    fun onPlayerLeaveGame(player: Player) {
        playerSectionType.remove(player.uniqueId)
        updatePlayerScoreboard(player)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Stats & cleanup
    // ──────────────────────────────────────────────────────────────────────

    fun getStats(): Map<String, Any> = mapOf(
        "globalEnabled"              to globalEnabled,
        "activeBoards"               to playerBoards.size,
        "boardSections"              to boardSections.size,
        "playerStates"               to playerSectionType.size,
        "updateTaskActive"           to (updateTask != null && !updateTask!!.isCancelled),
        "playersWithToggledBoards"   to playerToggledScoreboards.size,
        "onlinePlayers"              to Bukkit.getOnlinePlayers().size,
        "sidebarApproach"            to "Score.customName() — no sidebar teams"
    )

    fun cleanup() {
        stopAutoUpdate()
        Bukkit.getOnlinePlayers().forEach { clearPlayerScoreboard(it) }
        playerBoards.clear()
        playerSectionType.clear()
        playerActiveEntries.clear()
        playerToggledScoreboards.clear()
        debugLog("Cleanup completed")
    }

    // ──────────────────────────────────────────────────────────────────────
    // Debug
    // ──────────────────────────────────────────────────────────────────────

    private fun debugLog(message: String) {
        try {
            if (plugin.configManager.debugMode) plugin.logger.info("[ScoreboardManager] $message")
        } catch (_: Exception) {}
    }
}
