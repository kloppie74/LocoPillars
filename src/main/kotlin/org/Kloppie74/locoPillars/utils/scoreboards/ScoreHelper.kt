package org.Kloppie74.locoPillars.utils.scoreboards

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

class ScoreHelper private constructor(player: Player) {

    companion object {
        private val players = ConcurrentHashMap<UUID, ScoreHelper>()
        private val ENTRIES = Array(16) { "" }
        private const val MAX_PREFIX_LENGTH = 64
        private const val MAX_SUFFIX_LENGTH = 64
        private val lastColorsCache = ConcurrentHashMap<String, String>()

        init {
            for (i in 1..15) {
                ENTRIES[i] = ChatColor.values()[i].toString()
            }
        }

        fun hasScore(player: Player?) = player != null && players.containsKey(player.uniqueId)
        fun createScore(player: Player?): ScoreHelper? = player?.let { ScoreHelper(it) }
        fun getByPlayer(player: Player?): ScoreHelper? = player?.let { players[it.uniqueId] }
        fun removeScore(player: Player?): ScoreHelper? = player?.let {
            val helper = players.remove(it.uniqueId)
            helper?.cleanup()
            helper
        }
        fun clearScoreboard(player: Player?) {
            if (player == null) return
            if (hasScore(player)) {
                removeScore(player)
            }
            try {
                // Only clear the sidebar objective, do NOT assign a new scoreboard (fix for Tab teams)
                val scoreboard = Bukkit.getScoreboardManager().mainScoreboard
                scoreboard.getObjective(DisplaySlot.SIDEBAR)?.unregister()
                player.scoreboard = scoreboard
            } catch (_: Exception) { }
        }
        fun cleanupCache() = lastColorsCache.clear()

        // ADVANCED: conditional scoreboard lines (for IF-lines support)
        data class ScoreboardLine(
            val line: String,
            val condition: ScoreboardCondition? = null,
            val key: String = "",
            val order: Int = 0
        )
        interface ScoreboardCondition {
            fun evaluate(player: Player): Boolean
        }
        class NumericCondition(
            private val valueSupplier: (Player) -> Int,
            private val operator: String,
            private val expected: Int
        ) : ScoreboardCondition {
            override fun evaluate(player: Player): Boolean {
                val value = valueSupplier(player)
                return when (operator) {
                    "==" -> value == expected
                    ">=" -> value >= expected
                    "<=" -> value <= expected
                    ">" -> value > expected
                    "<" -> value < expected
                    else -> false
                }
            }
        }

        // Parse IF lines: "if PLAYERS_LEFT >= 2 LINE=\"Er zijn nog 2 spelers!\""
        private val IF_PATTERN = Pattern.compile("""^([A-Z_]+)\s*(==|>=|<=|>|<)\s*(\d+)\s*LINE="(.+)"$""")
        fun parseScoreboardLines(yamlLines: Map<String, Any>): List<ScoreboardLine> {
            val result = mutableListOf<ScoreboardLine>()
            var order = 0
            for ((key, valueObj) in yamlLines.entries) {
                val value = valueObj.toString()
                if (key.startsWith("if")) {
                    val m = IF_PATTERN.matcher(value)
                    if (m.matches()) {
                        val type = m.group(1)
                        val op = m.group(2)
                        val expected = m.group(3).toInt()
                        val line = m.group(4)
                        // Extend this logic for more types if needed!
                        val supplier: (Player) -> Int = when (type) {
                            "PLAYERS_LEFT" -> { p ->
                                // If you want to use your own API, change here!
                                Bukkit.getOnlinePlayers().size
                            }
                            "TEAM_SIZE" -> { _ -> 1 }
                            else -> { _ -> 0 }
                        }
                        result.add(ScoreboardLine(line, NumericCondition(supplier, op, expected), key, order++))
                    }
                } else {
                    result.add(ScoreboardLine(value, null, key, order++))
                }
            }
            return result
        }
    }

    // PATCH: Always use mainScoreboard for compatibility with Tab/teams
    private val scoreboard: Scoreboard = Bukkit.getScoreboardManager().mainScoreboard
    private val sidebar: Objective = createOrGetSidebarObjective(scoreboard, player)
    private val cachedTextParts = HashMap<Int, Array<String>>()
    private val activeSlots = HashSet<Int>()
    private var currentTitle: String = ""

    init {
        sidebar.displaySlot = DisplaySlot.SIDEBAR
        for (i in 1..15) {
            val teamName = "SLOT_$i"
            var team = scoreboard.getTeam(teamName)
            if (team == null) team = scoreboard.registerNewTeam(teamName)
            if (!team.hasEntry(ENTRIES[i])) team.addEntry(ENTRIES[i])
        }
        player.scoreboard = scoreboard
        players[player.uniqueId] = this
    }

    private fun cleanup() {
        cachedTextParts.clear()
        activeSlots.clear()
        try {
            sidebar.unregister()
            // Do NOT unregister teams: TabUtils might need them!
            // Only clear slot teams if you want to fully reset sidebar
        } catch (_: Exception) { }
    }

    fun setTitle(title: String?) {
        val safeTitle = title ?: ""
        if (safeTitle == currentTitle) return
        sidebar.displayName = safeTitle.take(32)
        currentTitle = safeTitle
    }

    fun setSlot(slot: Int, text: String?) {
        if (slot !in 1..15) return
        val safeText = text ?: ""
        val team = scoreboard.getTeam("SLOT_$slot") ?: return
        val entry = ENTRIES[slot]
        if (!activeSlots.contains(slot)) {
            sidebar.getScore(entry).score = slot
            activeSlots.add(slot)
        }
        val cached = cachedTextParts[slot]
        if (cached != null && cached[0] == safeText) return

        var prefix = ""
        var suffix = ""
        if (safeText.length <= MAX_PREFIX_LENGTH) {
            prefix = safeText
        } else {
            var splitPos = MAX_PREFIX_LENGTH
            if (splitPos < safeText.length) {
                if (safeText[splitPos - 1] == '§') splitPos--
                else if (splitPos >= 2 && safeText[splitPos - 2] == '§') splitPos -= 2
            }
            prefix = safeText.substring(0, splitPos)
            val lastColors = getLastColors(prefix)
            val remainingLength = (safeText.length - splitPos).coerceAtMost(MAX_SUFFIX_LENGTH - lastColors.length)
            suffix = if (remainingLength > 0) lastColors + safeText.substring(splitPos, splitPos + remainingLength) else lastColors
        }
        team.prefix = prefix
        team.suffix = suffix
        cachedTextParts[slot] = arrayOf(safeText, prefix, suffix)
    }

    fun removeSlot(slot: Int) {
        if (slot !in 1..15) return
        if (activeSlots.contains(slot)) {
            scoreboard.resetScores(ENTRIES[slot])
            activeSlots.remove(slot)
            cachedTextParts.remove(slot)
        }
    }

    fun setSlotsFromList(list: List<String>?) {
        if (list.isNullOrEmpty()) {
            for (i in 1..15) removeSlot(i)
            return
        }
        val size = list.size.coerceAtMost(15)
        val updatedSlots = HashSet<Int>(size)
        for (i in 0 until size) {
            val slot = size - i
            updatedSlots.add(slot)
            setSlot(slot, list[i])
        }
        for (i in 1..15) {
            if (i !in updatedSlots && activeSlots.contains(i)) removeSlot(i)
        }
    }

    fun setSlotsFromScoreboardLines(lines: List<ScoreboardLine>?, player: Player) {
        if (lines.isNullOrEmpty()) {
            for (i in 1..15) removeSlot(i)
            return
        }
        val activeLines = lines.filter { it.condition?.evaluate(player) ?: true }
            .sortedBy { it.order }
        val displayLines = activeLines.map { it.line }
        setSlotsFromList(displayLines)
    }

    // Kleuren splits helper (voor prefix/suffix)
    private fun getLastColors(text: String?): String {
        if (text.isNullOrEmpty()) return ""
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

    // Helper: create or get sidebar objective per player (unique name)
    private fun createOrGetSidebarObjective(scoreboard: Scoreboard, player: Player): Objective {
        val objName = "sidebar_${player.name.lowercase()}"
        var obj = scoreboard.getObjective(objName)
        if (obj == null) {
            obj = scoreboard.registerNewObjective(objName, "dummy")
        }
        obj.displaySlot = DisplaySlot.SIDEBAR
        return obj
    }
}