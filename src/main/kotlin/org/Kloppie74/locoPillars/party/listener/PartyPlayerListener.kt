package org.Kloppie74.locoPillars.party.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.Kloppie74.locoPillars.party.PartyManager

/**
 * Cleans up party state when a player disconnects or is kicked.
 */
class PartyPlayerListener(private val partyManager: PartyManager) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) = partyManager.handlePlayerDisconnect(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerKick(event: PlayerKickEvent) = partyManager.handlePlayerDisconnect(event.player)
}
