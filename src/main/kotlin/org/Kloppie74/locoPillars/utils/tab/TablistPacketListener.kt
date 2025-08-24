package org.Kloppie74.locoPillars.utils.tab

import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.events.ListenerPriority
import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import org.bukkit.plugin.Plugin
import org.Kloppie74.locoPillars.LocoPillars

/**
 * Simplified packet listener that only monitors tab-related packets
 * All actual filtering is handled by TabVisibilityManager using Bukkit's built-in methods
 */
class TablistPacketListener(
    plugin: Plugin,
    private val tabVisibilityManager: TabVisibilityManager
) : PacketAdapter(plugin, ListenerPriority.MONITOR, PacketType.Play.Server.PLAYER_INFO) {

    private val locoPillarsPlugin = plugin as LocoPillars

    override fun onPacketSending(event: PacketEvent) {
        // We only monitor packets for debugging purposes, no modification
        // All actual tab filtering is handled by TabVisibilityManager using showPlayer/hidePlayer

        try {
            if (locoPillarsPlugin.configManager.debugMode) {
                val player = event.player
                val packet = event.packet

                // Log packet info for debugging if needed
                debugLog("PLAYER_INFO packet sent to ${player.name}")
            }
        } catch (e: Exception) {
            // Silently handle any errors to avoid breaking packet flow
            locoPillarsPlugin.logger.warning("[TablistPacketListener] Error monitoring packet: ${e.message}")
        }
    }

    private fun debugLog(message: String) {
        if (locoPillarsPlugin.configManager.debugMode) {
            locoPillarsPlugin.logger.info("[TablistPacketListener] $message")
        }
    }
}

/**
 * Registers the packet listener if ProtocolLib is available
 */
fun registerTablistPacketListener(plugin: LocoPillars) {
    try {
        if (plugin.server.pluginManager.getPlugin("ProtocolLib") != null) {
            val listener = TablistPacketListener(plugin, plugin.tabVisibilityManager)
            ProtocolLibrary.getProtocolManager().addPacketListener(listener)
            plugin.logger.info("[TabUtils] ProtocolLib packet listener registered")
        } else {
            plugin.logger.info("[TabUtils] ProtocolLib not found, packet monitoring disabled")
        }
    } catch (e: Exception) {
        plugin.logger.warning("[TabUtils] Failed to register packet listener: ${e.message}")
    }
}