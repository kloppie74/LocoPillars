package org.Kloppie74.locoPillars.utils.teams.scoreboard

import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * ScoreHelper — DEPRECATED.
 *
 * Vroeger beheerde deze klasse de sidebar-objective en de §1–§f entry-strings.
 * ScoreboardManager gebruikt nu Score.customName(Component) rechtstreeks (Paper 1.21 API),
 * waardoor ScoreHelper volledig overbodig is geworden.
 *
 * De klasse blijft aanwezig om compile-fouten te vermijden als externe code er nog naar
 * verwijst, maar wordt nergens meer aangeroepen vanuit de plugin.
 */
@Deprecated("Vervangen door Score.customName() in ScoreboardManager — niet meer in gebruik")
object ScoreHelper {

    private val players = ConcurrentHashMap<UUID, Any>()

    /** Entries 1–15: §1, §2, …, §f  (legacy, alleen nog voor backwards compat) */
    val ENTRIES: Array<String> = Array(16) { "" }.also { arr ->
        val codes = "0123456789abcdef"
        for (i in 1..15) arr[i] = "§${codes[i]}"
    }

    fun hasScore(player: Player?) = player != null && players.containsKey(player.uniqueId)
    fun createScore(player: Player?): Any? = null   // no-op
    fun getByPlayer(player: Player?): Any?  = null
    fun removeScore(player: Player?): Any?  = null
    fun clearScoreboard(player: Player?)   {}
}
