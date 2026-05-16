package org.Kloppie74.locoPillars.editor

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.editor.model.EditModeState
import org.Kloppie74.locoPillars.utils.MSG
import java.util.UUID

/**
 * AdminEditorManager
 *
 * Central registry that tracks which admin players currently have edit mode
 * active. Edit mode is a lightweight "session" that precedes (or replaces) the
 * full [org.Kloppie74.locoPillars.game.map.EditorSessionManager] session.
 *
 * In edit mode an admin receives special hotbar tools and can:
 * - Set spawn points via [SpawnPointEditor]
 * - Validate map files via [EditorFileValidator]
 * - Start/stop a full map-editing session
 *
 * Edit mode does **not** create a separate world; that is handled by
 * [org.Kloppie74.locoPillars.game.map.EditorSessionManager].
 *
 * @param plugin The plugin instance used for logging.
 * @param hotbarManager Hotbar manager that gives/removes editor items.
 */
class AdminEditorManager(
    private val plugin: LocoPillars,
    private val hotbarManager: EditorHotbarManager
) {
    private val activeModes = mutableMapOf<UUID, EditModeState>()

    // -------------------------------------------------------------------------
    // Toggle / Enter / Exit
    // -------------------------------------------------------------------------

    /**
     * Toggle edit mode for [player].
     * If currently active it will be disabled; otherwise it will be enabled.
     * Returns `true` if edit mode is now active after the call.
     */
    fun toggle(player: Player): Boolean {
        return if (isActive(player)) {
            disable(player)
            false
        } else {
            enable(player)
            true
        }
    }

    /**
     * Enable edit mode for [player].
     * If already active, sends a reminder message and returns early.
     */
    fun enable(player: Player, mapName: String? = null) {
        if (isActive(player)) {
            player.sendMessage(MSG.chatColors("&eEdit mode is already active! Use &c/editor disable &eto exit."))
            return
        }

        // Snapshot current hotbar before replacing it
        val snapshot = mutableMapOf<Int, ItemStack?>()
        for (slot in 0..8) {
            snapshot[slot] = player.inventory.getItem(slot)?.clone()
        }

        val state = EditModeState(
            playerUuid = player.uniqueId,
            activeMapName = mapName,
            hotbarSlotSnapshot = snapshot,
            originalGameMode = player.gameMode,
            originalAllowFlight = player.allowFlight
        )
        activeModes[player.uniqueId] = state

        hotbarManager.giveEditorHotbar(player)
        state.hotbarEnabled = true
        player.gameMode = GameMode.CREATIVE
        player.allowFlight = true

        player.sendMessage(MSG.chatColors("&a✦ &lEdit Mode &aactivated!"))
        player.sendMessage(MSG.chatColors("&7Your hotbar now contains editor tools."))
        if (mapName != null) {
            player.sendMessage(MSG.chatColors("&7Active map: &e$mapName"))
        }
        player.sendMessage(MSG.chatColors("&7Use &e/editor help &7for a list of commands."))
        player.sendMessage(MSG.chatColors("&7Use &c/editor disable &7to exit edit mode."))

        debugLog("Edit mode enabled for ${player.name}" + (mapName?.let { " (map: $it)" } ?: ""))
    }

    /**
     * Disable edit mode for [player] and restore the original hotbar.
     * Returns `false` if edit mode was not active.
     */
    fun disable(player: Player): Boolean {
        val state = activeModes.remove(player.uniqueId) ?: run {
            player.sendMessage(MSG.chatColors("&cEdit mode is not active!"))
            return false
        }

        hotbarManager.removeEditorHotbar(player, state.hotbarSlotSnapshot)
        player.gameMode = state.originalGameMode
        player.allowFlight = state.originalAllowFlight

        player.sendMessage(MSG.chatColors("&c✦ Edit Mode &cdeactivated."))
        player.sendMessage(MSG.chatColors("&7Your original hotbar has been restored."))

        debugLog("Edit mode disabled for ${player.name} after ${state.activeDurationSeconds()}s")
        return true
    }

    // -------------------------------------------------------------------------
    // State queries
    // -------------------------------------------------------------------------

    /** Returns `true` if [player] currently has edit mode active. */
    fun isActive(player: Player): Boolean = activeModes.containsKey(player.uniqueId)

    /** Returns the [EditModeState] for [player], or `null` if not in edit mode. */
    fun getState(player: Player): EditModeState? = activeModes[player.uniqueId]

    /** Updates the active map name for the player's current edit mode session. */
    fun setActiveMap(player: Player, mapName: String?) {
        activeModes[player.uniqueId]?.activeMapName = mapName
    }

    /** Returns a snapshot of all currently active edit mode players. */
    fun getActiveEditors(): Map<UUID, EditModeState> = activeModes.toMap()

    fun setHotbarEnabled(player: Player, enabled: Boolean): Boolean {
        val state = activeModes[player.uniqueId] ?: return false
        if (enabled == state.hotbarEnabled) {
            return true
        }

        if (enabled) {
            hotbarManager.giveEditorHotbar(player)
            player.sendMessage(MSG.chatColors("&aEditor hotbar enabled."))
        } else {
            for (slot in 0..8) {
                player.inventory.setItem(slot, null)
            }
            player.updateInventory()
            player.sendMessage(MSG.chatColors("&eEditor hotbar hidden. Use &a/editor hotbar on &eto show it again."))
        }

        state.hotbarEnabled = enabled
        return true
    }

    fun toggleHotbar(player: Player): Boolean {
        val state = activeModes[player.uniqueId] ?: return false
        return setHotbarEnabled(player, !state.hotbarEnabled)
    }

    fun isHotbarEnabled(player: Player): Boolean = activeModes[player.uniqueId]?.hotbarEnabled == true

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    /**
     * Called on plugin disable. Forces disable for all active editors,
     * restoring hotbars only for online players.
     */
    fun cleanupAll() {
        activeModes.keys.toList().forEach { uuid ->
            val player = plugin.server.getPlayer(uuid)
            if (player != null && player.isOnline) {
                disable(player)
            } else {
                activeModes.remove(uuid)
            }
        }
        debugLog("All edit mode sessions cleaned up")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[AdminEditorManager] $message")
        }
    }
}
