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
import org.bukkit.inventory.meta.ItemMeta
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.cosmetics.CosmeticManager
import org.Kloppie74.locoPillars.cosmetics.engine.CosmeticDefinition
import org.Kloppie74.locoPillars.cosmetics.engine.CosmeticRarity
import org.Kloppie74.locoPillars.cosmetics.engine.CosmeticType
import org.Kloppie74.locoPillars.utils.MSG
import java.util.UUID

/**
 * Shop GUI — browse and buy cosmetics.
 *
 * Layout (6 rows, 54 slots):
 *  Row 0-1 (0-17): KILL cosmetics
 *  Row 2   (18-26): WIN cosmetics
 *  Row 3   (27-35): DEATH cosmetics
 *  Row 4   (36-44): CAGE cosmetics
 *  Row 5   (45-53): border / close / coins info
 *
 * Alternatively opened per-type via /cosmetics shop <type>.
 */
class CosmeticsShopGui(
    private val plugin: LocoPillars,
    private val manager: CosmeticManager
) : Listener {

    private val openInventories = mutableMapOf<UUID, ShopSession>()

    private data class ShopSession(
        val inventory: Inventory,
        val type: CosmeticType?,      // null = all types
        val page: Int = 0,
        val slots: Map<Int, CosmeticDefinition> = emptyMap()
    )

    // ── Open ──────────────────────────────────────────────────────────────────

    fun open(player: Player, type: CosmeticType? = null) {
        val cosmetics = if (type == null) manager.loader.getAll()
                        else             manager.loader.getAll(type)

        val title = if (type == null) "§8§lCosmetics Shop"
                    else "§8§l${type.displayName} Shop"

        val size = 54
        val inv = Bukkit.createInventory(null, size, title)
        val slotMap = mutableMapOf<Int, CosmeticDefinition>()
        val coins = manager.getCoins(player)

        // Fill cosmetics starting at slot 0
        cosmetics.forEachIndexed { index, def ->
            val slot = index
            if (slot >= 45) return@forEachIndexed  // last row reserved
            val item = buildShopItem(player, def, coins)
            inv.setItem(slot, item)
            slotMap[slot] = def
        }

        // Bottom row: border + coins display + close
        val borderPane = glassPane(Material.BLACK_STAINED_GLASS_PANE, " ")
        for (s in 45..53) inv.setItem(s, borderPane)

        val coinsDisplay = ItemStack(Material.SUNFLOWER)
        val coinsMeta = coinsDisplay.itemMeta!!
        coinsMeta.setDisplayName("§6§lYour Coins: §e$coins")
        coinsMeta.lore = listOf(
            "§7Use coins to buy cosmetics.",
            "§7Earn coins by playing games!"
        )
        coinsDisplay.itemMeta = coinsMeta
        inv.setItem(49, coinsDisplay)

        val closeItem = ItemStack(Material.BARRIER)
        val closeMeta = closeItem.itemMeta!!
        closeMeta.setDisplayName("§c§lClose")
        closeItem.itemMeta = closeMeta
        inv.setItem(53, closeItem)

        player.openInventory(inv)
        openInventories[player.uniqueId] = ShopSession(inv, type, 0, slotMap)
    }

    // ── Click handler ─────────────────────────────────────────────────────────

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val session = openInventories[player.uniqueId] ?: return
        if (event.inventory != session.inventory) return
        event.isCancelled = true

        val slot = event.rawSlot
        if (slot == 53) { player.closeInventory(); return }

        val def = session.slots[slot] ?: return
        handlePurchase(player, def)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        openInventories.remove((event.player as? Player)?.uniqueId)
    }

    // ── Purchase logic ────────────────────────────────────────────────────────

    private fun handlePurchase(player: Player, def: CosmeticDefinition) {
        if (manager.owns(player, def)) {
            player.sendMessage(MSG.chatColors("&eYou already own &f${def.displayName}&e!"))
            // Equip it instead
            manager.setSelected(player, def)
            player.sendMessage(MSG.chatColors("&aEquipped: &f${def.displayName}"))
            player.closeInventory()
            return
        }

        val result = manager.purchase(player, def)
        when (result) {
            CosmeticManager.PurchaseResult.SUCCESS -> {
                player.sendMessage(MSG.chatColors("&aPurchased: &f${def.displayName} &afor &6${def.price} coins&a!"))
                manager.setSelected(player, def)
                player.sendMessage(MSG.chatColors("&aAuto-equipped as your active ${def.type.displayName}!"))
                // Refresh the GUI
                val type = openInventories[player.uniqueId]?.type
                open(player, type)
            }
            CosmeticManager.PurchaseResult.NOT_ENOUGH_COINS ->
                player.sendMessage(MSG.chatColors("&cNot enough coins! You need &6${def.price}&c but have &6${manager.getCoins(player)}&c."))
            CosmeticManager.PurchaseResult.LEVEL_TOO_LOW ->
                player.sendMessage(MSG.chatColors("&cLevel too low! Required level: &f${def.requiredLevel}&c."))
            CosmeticManager.PurchaseResult.NO_PERMISSION ->
                player.sendMessage(MSG.chatColors("&cYou don't have permission for this cosmetic."))
            CosmeticManager.PurchaseResult.ALREADY_OWNED ->
                player.sendMessage(MSG.chatColors("&eYou already own this cosmetic!"))
            CosmeticManager.PurchaseResult.ERROR ->
                player.sendMessage(MSG.chatColors("&cSomething went wrong. Please try again."))
        }
    }

    // ── Item builder ──────────────────────────────────────────────────────────

    private fun buildShopItem(player: Player, def: CosmeticDefinition, playerCoins: Int): ItemStack {
        val item = ItemStack(def.iconMaterial)
        val meta: ItemMeta = item.itemMeta ?: return item

        val owned = manager.owns(player, def)
        val selected = manager.getSelected(player, def.type)?.id == def.id

        val namePrefix = when {
            selected -> "§a§l✔ "
            owned    -> "§7"
            else     -> def.rarity.color
        }
        meta.setDisplayName("$namePrefix${def.displayName}")

        if (def.iconCustomModelData > 0) meta.setCustomModelData(def.iconCustomModelData)

        val lore = mutableListOf<String>()
        lore.add("§8${def.rarity.color}${def.rarity.displayName}")
        lore.add("")
        def.lore.forEach { lore.add(MSG.chatColors(it)) }
        lore.add("")
        when {
            selected -> lore.add("§a§lACTIVE")
            owned    -> lore.add("§e▶ Click to equip")
            def.price == 0 -> { lore.add("§a§lFREE"); lore.add("§e▶ Click to equip") }
            else -> {
                val canAfford = playerCoins >= def.price
                lore.add(if (canAfford) "§6§lPrice: §e${def.price} coins" else "§c§lPrice: §4${def.price} coins §c(need ${def.price - playerCoins} more)")
                if (def.requiredLevel > 0) lore.add("§7Required level: §f${def.requiredLevel}")
                lore.add(if (canAfford) "§e▶ Click to purchase" else "§c✗ Can't afford")
            }
        }
        meta.lore = lore
        item.itemMeta = meta
        return item
    }

    private fun glassPane(material: Material, name: String): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta!!
        meta.setDisplayName(name)
        item.itemMeta = meta
        return item
    }
}
