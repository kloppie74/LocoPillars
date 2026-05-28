package org.Kloppie74.locoPillars.cosmetics.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.cosmetics.CosmeticManager
import org.Kloppie74.locoPillars.cosmetics.engine.CosmeticDefinition
import org.Kloppie74.locoPillars.cosmetics.engine.CosmeticType
import org.Kloppie74.locoPillars.utils.MSG
import java.util.UUID

/**
 * Cosmetic selection GUI — shows only cosmetics the player already owns.
 *
 * Opens a per-type view showing owned cosmetics with equip/unequip controls.
 * Opened via /cosmetics [select] or by clicking a type category from the main overview.
 *
 * Main overview (no type selected — 6 rows):
 *   Slot 11 → KILL category button
 *   Slot 13 → WIN category button
 *   Slot 15 → DEATH category button
 *   Slot 31 → CAGE category button
 */
class CosmeticsSelectGui(
    private val plugin: LocoPillars,
    private val manager: CosmeticManager
) : Listener {

    private val openInventories = mutableMapOf<UUID, SelectSession>()

    private data class SelectSession(
        val inventory: Inventory,
        val type: CosmeticType?,
        val slots: Map<Int, CosmeticDefinition> = emptyMap()
    )

    // ── Open overview ──────────────────────────────────────────────────────────

    fun openOverview(player: Player) {
        val inv = Bukkit.createInventory(null, 54, "§8§lMy Cosmetics")
        fillBorder(inv)

        val categoryData = listOf(
            11 to CosmeticType.KILL,
            13 to CosmeticType.WIN,
            15 to CosmeticType.DEATH,
            31 to CosmeticType.CAGE
        )

        val slotMap = mutableMapOf<Int, CosmeticDefinition>()

        categoryData.forEach { (slot, type) ->
            val selected = manager.getSelected(player, type)
            val ownedCount = manager.getOwnedIds(player, type).size
            val item = ItemStack(categoryIcon(type))
            val meta = item.itemMeta!!
            meta.setDisplayName("§b§l${type.displayName}")
            meta.lore = mutableListOf<String>().also { l ->
                l.add("§7You own §f$ownedCount §7cosmetics of this type.")
                if (selected != null) l.add("§7Active: §a${selected.displayName}")
                else l.add("§7Active: §8None")
                l.add("")
                l.add("§e▶ Click to browse and equip")
            }
            item.itemMeta = meta
            inv.setItem(slot, item)
        }

        // Close button
        val close = ItemStack(Material.BARRIER)
        val closeMeta = close.itemMeta!!
        closeMeta.setDisplayName("§c§lClose")
        close.itemMeta = closeMeta
        inv.setItem(49, close)

        // Shop shortcut
        val shop = ItemStack(Material.GOLD_INGOT)
        val shopMeta = shop.itemMeta!!
        shopMeta.setDisplayName("§6§lOpen Cosmetics Shop")
        shopMeta.lore = listOf("§7Buy more cosmetics!")
        shop.itemMeta = shopMeta
        inv.setItem(53, shop)

        player.openInventory(inv)
        openInventories[player.uniqueId] = SelectSession(inv, null, slotMap)
    }

    // ── Open type view ─────────────────────────────────────────────────────────

    fun openTypeView(player: Player, type: CosmeticType) {
        val owned = manager.getOwnedIds(player, type)
        val allForType = manager.loader.getAll(type).filter { it.id in owned || it.price == 0 }

        val title = "§8§lMy ${type.displayName}s"
        val inv = Bukkit.createInventory(null, 54, title)
        fillBorder(inv)

        val slotMap = mutableMapOf<Int, CosmeticDefinition>()
        var slot = 10
        val selectedId = manager.getSelected(player, type)?.id

        allForType.forEach { def ->
            if (slot >= 44) return@forEach
            // Skip border slots
            if (slot % 9 == 0 || slot % 9 == 8) slot++

            val item = ItemStack(def.iconMaterial)
            val meta = item.itemMeta!!
            if (def.iconCustomModelData > 0) meta.setCustomModelData(def.iconCustomModelData)
            val isSelected = def.id == selectedId
            meta.setDisplayName(if (isSelected) "§a§l✔ ${def.displayName}" else "§f${def.displayName}")
            meta.lore = mutableListOf<String>().also { l ->
                l.add("§8${def.rarity.color}${def.rarity.displayName}")
                l.add("")
                def.lore.forEach { line -> l.add(MSG.chatColors(line)) }
                l.add("")
                if (isSelected) {
                    l.add("§a§lACTIVE  §7| §cRight-click to unequip")
                } else {
                    l.add("§e▶ Left-click to equip")
                }
            }
            item.itemMeta = meta
            inv.setItem(slot, item)
            slotMap[slot] = def
            slot++
        }

        // None / unequip button
        val none = ItemStack(Material.GRAY_DYE)
        val noneMeta = none.itemMeta!!
        noneMeta.setDisplayName("§7§lNone (no cosmetic)")
        noneMeta.lore = listOf("§7Remove your active cosmetic for this type.")
        none.itemMeta = noneMeta
        inv.setItem(46, none)

        // Back button
        val back = ItemStack(Material.ARROW)
        val backMeta = back.itemMeta!!
        backMeta.setDisplayName("§e§lBack")
        back.itemMeta = backMeta
        inv.setItem(48, back)

        // Close button
        val close = ItemStack(Material.BARRIER)
        val closeMeta = close.itemMeta!!
        closeMeta.setDisplayName("§c§lClose")
        close.itemMeta = closeMeta
        inv.setItem(50, close)

        player.openInventory(inv)
        openInventories[player.uniqueId] = SelectSession(inv, type, slotMap)
    }

    // ── Click handler ─────────────────────────────────────────────────────────

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = openInventories[player.uniqueId] ?: return
        if (event.inventory != session.inventory) return
        event.isCancelled = true

        val slot = event.rawSlot

        if (session.type == null) {
            // Overview mode
            when (slot) {
                11 -> { player.closeInventory(); openTypeView(player, CosmeticType.KILL) }
                13 -> { player.closeInventory(); openTypeView(player, CosmeticType.WIN) }
                15 -> { player.closeInventory(); openTypeView(player, CosmeticType.DEATH) }
                31 -> { player.closeInventory(); openTypeView(player, CosmeticType.CAGE) }
                49 -> player.closeInventory()
                53 -> { player.closeInventory(); plugin.cosmeticManager.shopGui.openOverview(player) }
            }
        } else {
            // Type view mode
            val type = session.type
            when (slot) {
                46 -> {
                    manager.clearSelected(player, type)
                    player.sendMessage(MSG.chatColors("&7Removed active ${type.displayName} cosmetic."))
                    openTypeView(player, type)
                }
                48 -> { player.closeInventory(); openOverview(player) }
                50 -> player.closeInventory()
                else -> {
                    val def = session.slots[slot] ?: return
                    val isRight = event.isRightClick
                    if (isRight && manager.getSelected(player, type)?.id == def.id) {
                        manager.clearSelected(player, type)
                        player.sendMessage(MSG.chatColors("&7Unequipped: &f${def.displayName}"))
                    } else {
                        manager.setSelected(player, def)
                        player.sendMessage(MSG.chatColors("&aEquipped: &f${def.displayName}"))
                    }
                    openTypeView(player, type)
                }
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        openInventories.remove((event.player as? Player)?.uniqueId)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fillBorder(inv: Inventory) {
        val pane = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val paneMeta = pane.itemMeta!!
        paneMeta.setDisplayName(" ")
        pane.itemMeta = paneMeta
        val size = inv.size
        for (i in 0 until 9) inv.setItem(i, pane)
        for (i in size - 9 until size) inv.setItem(i, pane)
        for (i in 0 until size step 9) inv.setItem(i, pane)
        for (i in 8 until size step 9) inv.setItem(i, pane)
    }

    private fun categoryIcon(type: CosmeticType) = when (type) {
        CosmeticType.KILL  -> Material.DIAMOND_SWORD
        CosmeticType.WIN   -> Material.GOLDEN_HELMET
        CosmeticType.DEATH -> Material.BONE
        CosmeticType.CAGE  -> Material.IRON_BARS
    }
}
