package org.Kloppie74.locoPillars.editor.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerKickEvent
import org.Kloppie74.locoPillars.editor.AdminEditorManager
import org.Kloppie74.locoPillars.game.map.EditorSessionManager

/**
 * EditorModeListener
 *
 * ### Fix 1 – Hotbar NA world switch
 * Luistert op [PlayerChangedWorldEvent]. Zodra een speler met actieve edit mode
 * in de edit world landt, roept het [AdminEditorManager.onPlayerArrivedInEditWorld]
 * aan. De hotbar wordt dan pas gegeven nadat Minecraft de world-switch heeft
 * voltooid en de inventory niet meer kan resetten.
 *
 * ### Disconnect cleanup
 * Ruimt edit-mode state en open editor sessions automatisch op bij
 * disconnect/kick.
 */
class EditorModeListener(
    private val adminEditorManager: AdminEditorManager,
    private val editorSessionManager: EditorSessionManager
) : Listener {

    // -------------------------------------------------------------------------
    // Fix 1: Give hotbar AFTER world switch
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        if (!adminEditorManager.isActive(player)) return

        // Check if the player has arrived in their active edit session world
        val session   = editorSessionManager.getSession(player) ?: return
        val editWorld = session.tempWorld               ?: return

        if (player.world == editWorld) {
            adminEditorManager.onPlayerArrivedInEditWorld(player)
        }
    }

    // -------------------------------------------------------------------------
    // Disconnect / kick cleanup
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onPlayerQuit(event: PlayerQuitEvent) = cleanupPlayer(event.player)

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onPlayerKick(event: PlayerKickEvent) = cleanupPlayer(event.player)

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun cleanupPlayer(player: Player) {
        // Player is going offline - no point restoring hotbar
        if (adminEditorManager.isActive(player)) {
            adminEditorManager.disable(player)
        }
        if (editorSessionManager.hasSession(player)) {
            editorSessionManager.cancelSession(player)
        }
    }
}