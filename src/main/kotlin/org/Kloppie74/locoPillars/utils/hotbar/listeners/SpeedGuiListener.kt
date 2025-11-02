package org.Kloppie74.locoPillars.utils.hotbar.listeners

import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.Kloppie74.locoPillars.utils.hotbar.SpeedGui

class SpeedGuiListener : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        // Only handle if the player is in a SpeedGUI (tracked via cache)
        if (SpeedGui.hasSpeedGuiOpen(player)) {
            event.isCancelled = true
            SpeedGui.handleClick(event)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return

        // Unmark cache so player can open SpeedGui again
        if (SpeedGui.hasSpeedGuiOpen(player)) {
            SpeedGui.markInventoryClosed(player)
        }
    }
}