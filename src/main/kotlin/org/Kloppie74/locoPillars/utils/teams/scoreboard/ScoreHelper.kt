package org.Kloppie74.locoPillars.utils.teams.scoreboard

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Criteria
import org.bukkit.scoreboard.DisplaySlot
import org.bukkit.scoreboard.Objective
import org.bukkit.scoreboard.Scoreboard
import org.Kloppie74.locoPillars.LocoPillars
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * ScoreHelper die met TeamManager werkt en altijd een sidebar weergeeft
 * Fix: Regel 1 staat nu bovenaan! Debug systeem toegevoegd. Flikker fix.
 */
class ScoreHelper private constructor(private val player: Player) {

    companion object {
        private val players = ConcurrentHashMap<UUID, ScoreHelper>()
        val ENTRIES = Array(16) { "" }

        init {
            for (i in 1..15) {
                ENTRIES[i] = ChatColor.values()[i].toString()
            }
        }

        fun hasScore(player: Player?) = player != null && players.containsKey(player.uniqueId)

        fun createScore(player: Player?): ScoreHelper? = player?.let {
            removeScore(it)
            val helper = ScoreHelper(it)
            players[it.uniqueId] = helper
            helper
        }

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
        }
    }

    private val plugin: LocoPillars = LocoPillars.instance
    val scoreboard: Scoreboard
    private val sidebar: Objective
    private val activeSlots = HashMap<Int, String>() // Slot -> value
    private var currentTitle: String = ""

    init {
        scoreboard = plugin.teamManager.getPlayerScoreboard(player)
        sidebar = createOrGetSidebarObjective(scoreboard, player)
        sidebar.displaySlot = DisplaySlot.SIDEBAR
        player.scoreboard = scoreboard
        debugLog("Created scoreboard for ${player.name} with objective ${sidebar.name}")
    }

    private fun cleanup() {
        activeSlots.clear()
        sidebar.unregister()
        debugLog("Cleanup for ${player.name}")
    }

    /**
     * Zet de titel van de sidebar, alleen als hij echt verandert.
     */
    fun setTitle(title: String?) {
        val safeTitle = title ?: ""
        if (safeTitle == currentTitle) return
        try {
            sidebar.displayName(LegacyComponentSerializer.legacySection().deserialize(safeTitle.take(128)))
            currentTitle = safeTitle
            debugLog("Set title for ${player.name}: '$safeTitle'")
        } catch (e: Exception) {
            plugin.logger.warning("[ScoreHelper] Error setting title for ${player.name}: ${e.message}")
        }
    }

    /**
     * Zet een slot als de tekst echt anders is.
     */
    fun setSlot(slot: Int, text: String?) {
        if (slot !in 1..15) return
        val safeText = text ?: ""
        val entry = ENTRIES[slot]
        if (activeSlots[slot] == safeText) return // Niet opnieuw zetten!
        try {
            sidebar.getScore(entry).score = slot
            activeSlots[slot] = safeText
            debugLog("Set slot $slot for ${player.name}: '$safeText'")
        } catch (e: Exception) {
            plugin.logger.warning("[ScoreHelper] Error setting slot $slot for ${player.name}: ${e.message}")
        }
    }

    /**
     * Verwijder een slot.
     */
    fun removeSlot(slot: Int) {
        if (slot !in 1..15) return
        val entry = ENTRIES[slot]
        if (!activeSlots.containsKey(slot)) return
        try {
            scoreboard.resetScores(entry)
            activeSlots.remove(slot)
            debugLog("Removed slot $slot for ${player.name}")
        } catch (e: Exception) {
            plugin.logger.warning("[ScoreHelper] Error removing slot $slot for ${player.name}: ${e.message}")
        }
    }

    /**
     * Verwijder alle slots (force).
     */
    fun forceClearAllSlots() {
        val slotsToRemove = activeSlots.keys.toList()
        slotsToRemove.forEach { removeSlot(it) }
        debugLog("Cleared all slots for ${player.name}")
    }

    /**
     * Set slots using a score→text map.
     * The map key is used directly as the Minecraft score (determines vertical position:
     * higher score = higher on screen). Entry slots are allocated by sorted-key index.
     * Anti-flicker: only updates entries whose score or text actually changed.
     */
    fun setSlotsFromMap(scoreToText: Map<Int, String>?) {
        if (scoreToText.isNullOrEmpty()) {
            for (i in 1..15) removeSlot(i)
            return
        }

        try {
            val sorted = scoreToText.entries.sortedBy { it.key }
            val size = sorted.size.coerceAtMost(15)
            val updatedSlots = HashSet<Int>(size)

            for (i in 0 until size) {
                val entryIndex = i + 1
                updatedSlots.add(entryIndex)
                setSlotWithScore(entryIndex, sorted[i].key, sorted[i].value)
            }

            // Remove slots that are no longer in use
            for (i in 1..15) {
                if (i !in updatedSlots) removeSlot(i)
            }
            debugLog("Set $size slots from map for ${player.name}")
        } catch (e: Exception) {
            plugin.logger.warning("[ScoreHelper] Error setting slots from map for ${player.name}: ${e.message}")
        }
    }

    /**
     * Set a single slot entry with an explicit Minecraft score value.
     * @param entryIndex  Which ENTRIES[] index to use (1..15) — picks the ChatColor entry string
     * @param score       The Minecraft score assigned to that entry (determines vertical position)
     * @param text        Display text handled by the sidebar team (prefix/suffix via TeamManager)
     * Anti-flicker: skips the update if score + text are unchanged.
     */
    fun setSlotWithScore(entryIndex: Int, score: Int, text: String?) {
        if (entryIndex !in 1..15) return
        val safeText = text ?: ""
        val entry = ENTRIES[entryIndex]
        val cacheKey = "$score:$safeText"
        if (activeSlots[entryIndex] == cacheKey) return // nothing changed
        try {
            sidebar.getScore(entry).score = score
            activeSlots[entryIndex] = cacheKey
            debugLog("Set slot $entryIndex (score=$score) for ${player.name}: '$safeText'")
        } catch (e: Exception) {
            plugin.logger.warning("[ScoreHelper] Error setting slot $entryIndex for ${player.name}: ${e.message}")
        }
    }

    /**
     * Zet alle regels van boven naar beneden (regel 1 bovenaan).
     * Flikker fix: Alleen updaten als de tekst echt verandert.
     */
    fun setSlotsFromList(list: List<String>?) {
        if (list.isNullOrEmpty()) {
            for (i in 1..15) removeSlot(i)
            return
        }

        try {
            val size = list.size.coerceAtMost(15)
            val updatedSlots = HashSet<Int>(size)

            for (i in 0 until size) {
                val slot = i + 1 // slot 1 = eerste regel, slot 2 = tweede regel, etc
                updatedSlots.add(slot)
                setSlot(slot, list[i])
            }

            // Remove unused slots
            for (i in 1..15) {
                if (i !in updatedSlots) {
                    removeSlot(i)
                }
            }
            debugLog("Set $size slots from list for ${player.name}")
        } catch (e: Exception) {
            plugin.logger.warning("[ScoreHelper] Error setting slots from list for ${player.name}: ${e.message}")
        }
    }

    /**
     * Debug logger
     */
    private fun debugLog(message: String) {
        try {
            if (plugin.configManager.debugMode) {
                plugin.logger.info("[ScoreHelper] $message")
            }
        } catch (_: Exception) {}
    }

    /**
     * Maak altijd een nieuwe sidebar objective (nooit flikkeren).
     */
    private fun createOrGetSidebarObjective(scoreboard: Scoreboard, player: Player): Objective {
        val objName = "sidebar_${player.name.lowercase().take(8)}_${System.currentTimeMillis() % 1000}"
        scoreboard.objectives.filter { it.displaySlot == DisplaySlot.SIDEBAR }.forEach {
            try { it.unregister() } catch (_: Exception) {}
        }
        val obj = try {
            scoreboard.registerNewObjective(
                objName,
                Criteria.DUMMY,
                LegacyComponentSerializer.legacySection().deserialize("Scoreboard")
            )
        } catch (e: Exception) {
            throw RuntimeException("Failed to create sidebar objective for ${player.name}: ${e.message}")
        }
        debugLog("Created sidebar objective '$objName' for ${player.name}")
        return obj
    }
}