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
 * BlacklistedItemsGui — Manage the RandomItems blacklist for a map.
 *
 * Layout (54 slots):
 *   Slots  0–44  — existing blacklisted items (click to remove)
 *   Slot  45     — "Add held item" button (adds item in main hand)
 *   Slots 46–52  — filler
 *   Slot  53     — Back button (returns to EditorSettingsGui)
 */
class BlacklistedItemsGui(
    private val plugin: LocoPillars,
    private val editorSessionManager: EditorSessionManager
) : Listener {

    var editorSettingsGui: EditorSettingsGui? = null

    private val title = MSG.chatColors("&0&l⚙ &cBlacklisted Items &0&l⚙")

    // -------------------------------------------------------------------------
    // Open
    // -------------------------------------------------------------------------

    fun open(player: Player) {
        val session = editorSessionManager.getSession(player) ?: run {
            player.sendMessage(MSG.chatColors("&cNo active editor session!"))
            return
        }

        val inv = Bukkit.createInventory(player, 54, title)
        val items = session.getBlacklistedItems()

        // Fill item slots with existing blacklisted materials
        items.forEachIndexed { index, materialName ->
            if (index >= 45) return@forEachIndexed
            val mat = runCatching { Material.valueOf(materialName) }.getOrNull()
                ?: Material.BARRIER
            inv.setItem(index, makeEntry(mat, materialName))
        }

        // Remaining item slots: filler
        for (slot in items.size until 45) inv.setItem(slot, filler())

        // Slot 45: Add held item button
        inv.setItem(45, makeButton(Material.LIME_WOOL, "&aAdd Held Item",
            listOf(
                "&7Hold any item in your &emain hand",
                "&7then click this button to add it",
                "&7to the blacklist."
            )))

        // Slots 46-52: filler
        for (slot in 46..52) inv.setItem(slot, filler())

        // Slot 53: Back
        inv.setItem(53, makeButton(Material.ARROW, "&7← Back",
            listOf("&7Return to Map Settings")))

        player.openInventory(inv)
    }

    // -------------------------------------------------------------------------
    // Click handler
    // -------------------------------------------------------------------------

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title != title) return
        event.isCancelled = true

        val player  = event.whoClicked as? Player ?: return
        val session = editorSessionManager.getSession(player) ?: return
        val slot    = event.slot

        when {
            slot == 53 -> {
                player.closeInventory()
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    editorSettingsGui?.open(player)
                })
                return
            }
            slot == 45 -> {
                val held = player.inventory.itemInMainHand
                if (held.type == Material.AIR) {
                    player.sendActionBar(MSG.chatColors("&cHold an item in your main hand first!"))
                    return
                }
                val materialName = held.type.name
                if (session.addBlacklistedItem(materialName)) {
                    player.sendActionBar(MSG.chatColors("&a+ ${held.type.name.lowercase().replace('_', ' ')} added to blacklist"))
                } else {
                    player.sendActionBar(MSG.chatColors("&e${held.type.name.lowercase().replace('_', ' ')} is already blacklisted"))
                }
                Bukkit.getScheduler().runTask(plugin, Runnable { open(player) })
            }
            slot in 0..44 -> {
                val clickedItem = event.currentItem ?: return
                if (clickedItem.type == Material.BLACK_STAINED_GLASS_PANE) return
                val materialName = clickedItem.itemMeta?.displayName
                    ?.replace("§c", "")?.replace("§7", "")?.trim()
                    ?: return
                if (session.removeBlacklistedItem(materialName)) {
                    player.sendActionBar(MSG.chatColors("&c- ${materialName.lowercase().replace('_', ' ')} removed from blacklist"))
                }
                Bukkit.getScheduler().runTask(plugin, Runnable { open(player) })
            }
        }
    }

    // -------------------------------------------------------------------------
    // Item builders
    // -------------------------------------------------------------------------

    private fun makeEntry(material: Material, materialName: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta!!
        meta.setDisplayName(MSG.chatColors("&c$materialName"))
        meta.lore = listOf(MSG.chatColors("&7Click to &cremove &7from blacklist"))
        item.itemMeta = meta
        return item
    }

    private fun filler(): ItemStack {
        val item = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val meta = item.itemMeta!!
        meta.setDisplayName(" ")
        item.itemMeta = meta
        return item
    }

    private fun makeButton(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta!!
        meta.setDisplayName(MSG.chatColors(name))
        meta.lore = lore.map { MSG.chatColors(it) }
        item.itemMeta = meta
        return item
    }
}
