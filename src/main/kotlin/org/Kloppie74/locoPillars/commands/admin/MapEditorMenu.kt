package org.Kloppie74.locoPillars.commands.admin

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

class MapEditorMenu(
    private val plugin: LocoPillars,
    private val editorSessionManager: EditorSessionManager
) : Listener {
    private val title = MSG.chatColors("&8✦ &bEditor Settings &8✦")

    fun open(player: Player) {
        val session = editorSessionManager.getSession(player)
        if (session == null) {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            return
        }

        val inventory = Bukkit.createInventory(player, 27, title)
        inventory.setItem(11, makeButton(Material.REDSTONE, "&c-5 Save Radius", listOf("&7Shrink the save bounds.")))
        inventory.setItem(
            13,
            makeButton(
                Material.PAPER,
                "&eSave Radius",
                listOf("&7Current: &f${session.getSaveRadius()}")
            )
        )
        inventory.setItem(15, makeButton(Material.EMERALD, "&a+5 Save Radius", listOf("&7Expand the save bounds.")))
        inventory.setItem(
            22,
            makeButton(
                Material.LIME_DYE,
                "&aBorder Size",
                listOf("&7Current: &f${session.getBorderSize().toInt()}")
            )
        )

        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title != title) {
            return
        }

        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return
        val session = editorSessionManager.getSession(player) ?: return

        when (event.slot) {
            11 -> session.setSaveRadius(session.getSaveRadius() - 5)
            15 -> session.setSaveRadius(session.getSaveRadius() + 5)
            else -> return
        }

        Bukkit.getScheduler().runTask(plugin, Runnable { open(player) })
    }

    private fun makeButton(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta?.setDisplayName(MSG.chatColors(name))
        meta?.lore = lore.map { MSG.chatColors(it) }
        item.itemMeta = meta
        return item
    }
}
