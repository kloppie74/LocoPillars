package org.Kloppie74.locoPillars.editor.model

import java.util.UUID
import org.bukkit.inventory.ItemStack

/**
 * Represents the current edit mode state for an admin player.
 *
 * @param playerUuid UUID of the admin who activated edit mode.
 * @param startedAt Timestamp (ms) when edit mode was activated.
 * @param activeMapName The map name currently being focused on, or null if none selected.
 * @param hotbarSlotSnapshot Snapshot of the player's hotbar contents before editor tools were given.
 */
data class EditModeState(
    val playerUuid: UUID,
    val startedAt: Long = System.currentTimeMillis(),
    var activeMapName: String? = null,
    val hotbarSlotSnapshot: Map<Int, ItemStack?> = emptyMap()
) {
    /** Returns how long (in seconds) this edit mode has been active. */
    fun activeDurationSeconds(): Long = (System.currentTimeMillis() - startedAt) / 1000L
}
