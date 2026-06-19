package org.Kloppie74.locoPillars.cosmetics.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
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
 * Select GUI — toont alleen cosmetics die de speler al bezit.
 * Smooth navigatie: openInventory() zonder closeInventory() tussendoor.
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun pane(material: Material, name: String = " "): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta!!
        meta.setDisplayName(name)
        item.itemMeta = meta
        return item
    }

    private fun fillBorder(inv: Inventory, material: Material = Material.CYAN_STAINED_GLASS_PANE) {
        val p = pane(material)
        val size = inv.size
        for (i in 0 until 9) inv.setItem(i, p)
        for (i in size - 9 until size) inv.setItem(i, p)
        for (i in 0 until size step 9) inv.setItem(i, p)
        for (i in 8 until size step 9) inv.setItem(i, p)
    }

    private fun switchTo(player: Player, session: SelectSession) {
        openInventories[player.uniqueId] = session
        player.openInventory(session.inventory)
    }

    // ── Overview (no type selected, 54 slots) ─────────────────────────────────

    fun openOverview(player: Player) {
        val inv = Bukkit.createInventory(null, 54, "§b§lMy Cosmetics")
        fillBorder(inv)

        // Accent-panes rondom categorieën
        val accentPane = pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
        listOf(10, 12, 14, 16, 20, 24, 28, 29, 30, 32, 33, 34).forEach { inv.setItem(it, accentPane) }

        val categoryData = listOf(
            11 to CosmeticType.KILL,
            13 to CosmeticType.WIN,
            15 to CosmeticType.DEATH,
            31 to CosmeticType.CAGE
        )

        categoryData.forEach { (slot, type) ->
            val selected     = manager.getSelected(player, type)
            val ownedCount   = manager.getOwnedIds(player, type).size
            val totalCount   = manager.loader.getAll(type).size

            val item = ItemStack(categoryIcon(type))
            val meta = item.itemMeta!!
            meta.setDisplayName("${categoryColor(type)}§l${type.displayName}")
            meta.lore = buildList {
                add("§8▸ §7Owned: §f$ownedCount §8/ §7${totalCount} total")
                add(if (selected != null) "§8▸ §7Active: §a${selected.displayName}" else "§8▸ §7Active: §8None")
                if (type == CosmeticType.CAGE) add("§8▸ §b✦ Customize your spawn cage!")
                add("")
                add("§e▶ Click to manage")
            }
            item.itemMeta = meta
            inv.setItem(slot, item)
        }

        // Shop shortcut (slot 45)
        val shopItem = ItemStack(Material.GOLD_INGOT)
        val shopMeta = shopItem.itemMeta!!
        shopMeta.setDisplayName("§6§lCosmetics Shop")
        shopMeta.lore = listOf("§7Buy more cosmetics!")
        shopItem.itemMeta = shopMeta
        inv.setItem(45, shopItem)

        // Sluiten (slot 49)
        inv.setItem(49, closeButton())

        switchTo(player, SelectSession(inv, null))
    }

    // ── Type view (owned cosmetics for one type, 54 slots) ────────────────────

    fun openTypeView(player: Player, type: CosmeticType) {
        val ownedIds    = manager.getOwnedIds(player, type)
        val allForType  = manager.loader.getAll(type).filter { it.id in ownedIds || it.price == 0 }
        val selectedId  = manager.getSelected(player, type)?.id
        val title       = "${categoryColor(type)}§lMijn ${type.displayName}s"

        val inv = Bukkit.createInventory(null, 54, title)
        fillBorder(inv, categoryPaneColor(type))

        val slotMap = mutableMapOf<Int, CosmeticDefinition>()
        val innerSlots = (10..43).filter { it % 9 != 0 && it % 9 != 8 }

        allForType.forEachIndexed { index, def ->
            val slot = innerSlots.getOrNull(index) ?: return@forEachIndexed
            val isSelected = def.id == selectedId

            val item = ItemStack(def.iconMaterial)
            val meta = item.itemMeta!!
            if (def.iconCustomModelData > 0) meta.setCustomModelData(def.iconCustomModelData)

            meta.setDisplayName(when {
                isSelected -> "§a§l✔ ${def.displayName}"
                else       -> "§f${def.displayName}"
            })
            meta.lore = buildList {
                add("§8${def.rarity.color}${def.rarity.displayName}")
                add("")
                def.lore.forEach { add(MSG.chatColors(it)) }
                add("")
                if (isSelected) {
                    add("§a§l★ ACTIVE")
                    add("§c Right-click to unequip")
                } else {
                    add("§e▶ Left-click to equip")
                }
            }
            item.itemMeta = meta
            inv.setItem(slot, item)
            slotMap[slot] = def
        }

        // No cosmetic (slot 46)
        val noneItem = ItemStack(Material.GRAY_DYE)
        val noneMeta = noneItem.itemMeta!!
        noneMeta.setDisplayName("§7§lNo Cosmetic")
        noneMeta.lore = listOf("§7Remove your active ${type.displayName}.")
        noneItem.itemMeta = noneMeta
        inv.setItem(46, noneItem)

        // Back (slot 48)
        val backItem = ItemStack(Material.ARROW)
        val backMeta = backItem.itemMeta!!
        backMeta.setDisplayName("§e§l← Back")
        backMeta.lore = listOf("§7Back to overview.")
        backItem.itemMeta = backMeta
        inv.setItem(48, backItem)

        // Sluiten (slot 50)
        inv.setItem(50, closeButton())

        switchTo(player, SelectSession(inv, type, slotMap))
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
            when (slot) {
                11 -> openTypeView(player, CosmeticType.KILL)
                13 -> openTypeView(player, CosmeticType.WIN)
                15 -> openTypeView(player, CosmeticType.DEATH)
                31 -> openTypeView(player, CosmeticType.CAGE)
                45 -> plugin.cosmeticManager.shopGui.openOverview(player)
                49 -> player.closeInventory()
            }
        } else {
            val type = session.type
            when (slot) {
                46 -> {
                    manager.clearSelected(player, type)
                    player.sendMessage(MSG.chatColors("&7Active ${type.displayName} removed."))
                    openTypeView(player, type)
                }
                48 -> openOverview(player)
                50 -> player.closeInventory()
                else -> {
                    val def = session.slots[slot] ?: return
                    val isRight = event.click == ClickType.RIGHT || event.click == ClickType.SHIFT_RIGHT
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
        val player = event.player as? Player ?: return
        val session = openInventories[player.uniqueId] ?: return
        // Alleen verwijderen als dit de HUIDIGE session-inventory is
        if (event.inventory == session.inventory) {
            openInventories.remove(player.uniqueId)
        }
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    private fun closeButton(): ItemStack {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta!!
        meta.setDisplayName("§c§lClose")
        item.itemMeta = meta
        return item
    }

    private fun categoryIcon(type: CosmeticType) = when (type) {
        CosmeticType.KILL  -> Material.SKELETON_SKULL
        CosmeticType.WIN   -> Material.NETHER_STAR
        CosmeticType.DEATH -> Material.WITHER_ROSE
        CosmeticType.CAGE  -> Material.IRON_BARS
    }

    private fun categoryColor(type: CosmeticType) = when (type) {
        CosmeticType.KILL  -> "§c"
        CosmeticType.WIN   -> "§6"
        CosmeticType.DEATH -> "§8"
        CosmeticType.CAGE  -> "§b"
    }

    private fun categoryPaneColor(type: CosmeticType) = when (type) {
        CosmeticType.KILL  -> Material.RED_STAINED_GLASS_PANE
        CosmeticType.WIN   -> Material.YELLOW_STAINED_GLASS_PANE
        CosmeticType.DEATH -> Material.GRAY_STAINED_GLASS_PANE
        CosmeticType.CAGE  -> Material.CYAN_STAINED_GLASS_PANE
    }
}
