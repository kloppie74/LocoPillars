package org.Kloppie74.locoPillars.utils.hotbar.listeners

import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.InventoryClickEvent
import org.Kloppie74.locoPillars.utils.hotbar.SpeedGui

class SpeedGuiListener : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (SpeedGui.isSpeedGui(event.view.title)) {
            SpeedGui.handleClick(event)
        }
    }
}