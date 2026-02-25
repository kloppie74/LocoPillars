package org.Kloppie74.locoPillars.editor.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerKickEvent
import org.Kloppie74.locoPillars.editor.AdminEditorManager
import org.Kloppie74.locoPillars.game.map.EditorSessionManager

/**
 * EditorModeListener
 *
 * Listens for player disconnect/kick events so we can automatically clean up
 * any active edit-mode state and open editor sessions.
 *
 * Without this, a player who disconnects while in edit mode would leave a
 * dangling [org.Kloppie74.locoPillars.editor.model.EditModeState] in the
 * [AdminEditorManager] and a potentially orphaned world in
 * [EditorSessionManager].
 *
 * @param adminEditorManager Tracks which players have edit mode enabled.
 * @param editorSessionManager Manages full map-editing worlds.
 */
class EditorModeListener(
    private val adminEditorManager: AdminEditorManager,
    private val editorSessionManager: EditorSessionManager
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        cleanupPlayer(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onPlayerKick(event: PlayerKickEvent) {
        cleanupPlayer(event.player)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun cleanupPlayer(player: Player) {
        // Clean up edit mode (hotbar snapshot etc.) – the player is offline so
        // we cannot call the normal disable() path that restores the hotbar.
        // AdminEditorManager.cleanupAll() iterates online players only; here
        // we remove the state entry directly.
        if (adminEditorManager.isActive(player)) {
            // The player is about to go offline – no point restoring hotbar.
            adminEditorManager.getState(player)   // side-effect: ensures state exists
            // Force-remove the state without attempting hotbar restore.
            adminEditorManager.disable(player)    // disable() is safe even if the player is still technically "online" at this point in the event chain
        }

        // Cancel any open editor world session so the temporary world gets cleaned up
        if (editorSessionManager.hasSession(player)) {
            editorSessionManager.cancelSession(player)
        }
    }
}
