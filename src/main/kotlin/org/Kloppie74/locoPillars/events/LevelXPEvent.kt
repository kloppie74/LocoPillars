package org.Kloppie74.locoPillars.events

import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event fired when a player's XP is added (not level-up, just XP addition).
 */
class LevelXPEvent(
    val playerId: String,
    val newExperience: Int,
    val xpAdded: Int
) : Event() {

    companion object {
        private val handlers = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList = handlers
    }

    override fun getHandlers(): HandlerList = handlers
}