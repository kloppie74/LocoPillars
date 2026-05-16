package org.Kloppie74.locoPillars.editor

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.map.EditorSessionManager
import org.Kloppie74.locoPillars.utils.MSG

/**
 * EditorSettingsGui
 *
 * A 27-slot inventory GUI that allows admins to adjust schematic-save
 * parameters (save radius, border size) for the currently active
 * [org.Kloppie74.locoPillars.game.map.EditorSession].
 *
 * Open it with [open]; clicks are processed via the [onInventoryClick]
 * event handler (register this class as a Bukkit [Listener]).
 *
 * Layout:
 * ```
 * [ ][ ][ ][ ][ ][ ][ ][ ][ ]
 * [ ][-5][ ][Radius][ ][+5][ ][ ][ ]
 * [ ][ ][ ][ ][Border][ ][ ][ ][ ]
 * ```
 *
 * @param plugin Plugin instance, used for task scheduling.
 * @param editorSessionManager Provides access to the active [EditorSession].
 */
class EditorSettingsGui(
    private val plugin: LocoPillars,
    private val editorSessionManager: EditorSessionManager
) : Listener {

    private val title = MSG.chatColors("&8✦ &bEditor Settings &8✦")

    /** Opens the settings GUI for [player]. Requires an active editor session. */
    fun open(player: Player) {
        val session = editorSessionManager.getSession(player)
        if (session == null) {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            player.sendMessage(MSG.chatColors("&7Use &e/editor edit <map> &7to start one."))
            return
        }

        val inventory = Bukkit.createInventory(player, 27, title)
        inventory.setItem(11, makeButton(Material.REDSTONE,   "&c-5 Save Radius", listOf("&7Shrinks the schematic save bounds.")))
        inventory.setItem(13, makeButton(Material.PAPER,      "&eSave Radius",    listOf("&7Current: &f${session.getSaveRadius()}")))
        inventory.setItem(15, makeButton(Material.EMERALD,    "&a+5 Save Radius", listOf("&7Expands the schematic save bounds.")))
        inventory.setItem(22, makeButton(Material.LIME_DYE,   "&aBorder Size",    listOf("&7Current: &f${session.getBorderSize().toInt()}")))

        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title != title) return
        event.isCancelled = true

        val player  = event.whoClicked as? Player ?: return
        val session = editorSessionManager.getSession(player) ?: return

        when (event.slot) {
            11 -> session.setSaveRadius(session.getSaveRadius() - 5)
            15 -> session.setSaveRadius(session.getSaveRadius() + 5)
            else -> return
        }

        // Re-open the GUI on the next tick so the updated values are shown.
        Bukkit.getScheduler().runTask(plugin, Runnable { open(player) })
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeButton(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta?.setDisplayName(MSG.chatColors(name))
        meta?.lore = lore.map { MSG.chatColors(it) }
        item.itemMeta = meta
        return item
    }
}
