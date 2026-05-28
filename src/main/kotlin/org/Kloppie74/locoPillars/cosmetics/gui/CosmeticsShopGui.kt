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
 * Flow:
 *   /cosmetics shop          → openOverview()  (category picker, 4 types)
 *   /cosmetics shop <type>   → openTypeView()  (skip directly to that type)
 *   Click category in overview → openTypeView()
 *   Back button in type view → openOverview()
 */
class CosmeticsShopGui(
    private val plugin: LocoPillars,
    private val manager: CosmeticManager
) : Listener {

    private val openInventories = mutableMapOf<UUID, ShopSession>()

    private data class ShopSession(
        val inventory: Inventory,
        val type: CosmeticType?,
        val slots: Map<Int, CosmeticDefinition> = emptyMap()
    )

    // ── Entry points ───────────────────────────────────────────────────────────

    /** Opens the category overview when no type is specified, or directly a type view. */
    fun open(player: Player, type: CosmeticType? = null) {
        if (type == null) openOverview(player) else openTypeView(player, type)
    }

    // ── Overview (category picker) ─────────────────────────────────────────────

    fun openOverview(player: Player) {
        val inv = Bukkit.createInventory(null, 54, "§8§lCosmetics Shop")
        fillBorder(inv)

        val coins = manager.getCoins(player)

        val categoryData = listOf(
            11 to CosmeticType.KILL,
            13 to CosmeticType.WIN,
            15 to CosmeticType.DEATH,
            31 to CosmeticType.CAGE
        )

        categoryData.forEach { (slot, type) ->
            val all = manager.loader.getAll(type)
            val owned = manager.getOwnedIds(player, type).size
            val total = all.size
            val selected = manager.getSelected(player, type)

            val item = ItemStack(categoryIcon(type))
            val meta = item.itemMeta!!
            meta.setDisplayName("§e§l${type.displayName} Shop")
            meta.lore = mutableListOf<String>().also { l ->
                l.add("§7Owned: §f$owned §7/ §f$total")
                if (selected != null) l.add("§7Active: §a${selected.displayName}")
                else l.add("§7Active: §8None")
                l.add("")
                val cheapest = all.filter { !manager.owns(player, it) && it.price > 0 }
                    .minByOrNull { it.price }
                if (cheapest != null) l.add("§7Cheapest: §6${cheapest.price} coins")
                else l.add("§7All owned!")
                l.add("")
                l.add("§e▶ Click to browse")
            }
            item.itemMeta = meta
            inv.setItem(slot, item)
        }

        // Coins display
        val coinsItem = ItemStack(Material.SUNFLOWER)
        val coinsMeta = coinsItem.itemMeta!!
        coinsMeta.setDisplayName("§6§lYour Coins: §e$coins")
        coinsMeta.lore = listOf(
            "§7Earn coins by playing games!",
            "§7Use them to unlock cosmetics."
        )
        coinsItem.itemMeta = coinsMeta
        inv.setItem(22, coinsItem)

        // My cosmetics shortcut
        val myCosmetics = ItemStack(Material.CHEST)
        val myCosmeticsMeta = myCosmetics.itemMeta!!
        myCosmeticsMeta.setDisplayName("§b§lMy Cosmetics")
        myCosmeticsMeta.lore = listOf("§7View and equip your owned cosmetics.")
        myCosmetics.itemMeta = myCosmeticsMeta
        inv.setItem(45, myCosmetics)

        // Close
        val close = ItemStack(Material.BARRIER)
        val closeMeta = close.itemMeta!!
        closeMeta.setDisplayName("§c§lClose")
        close.itemMeta = closeMeta
        inv.setItem(49, close)

        player.openInventory(inv)
        openInventories[player.uniqueId] = ShopSession(inv, null)
    }

    // ── Type view (actual cosmetics list) ─────────────────────────────────────

    fun openTypeView(player: Player, type: CosmeticType) {
        val all = manager.loader.getAll(type)
        val coins = manager.getCoins(player)

        val title = "§8§l${type.displayName} Shop"
        val inv = Bukkit.createInventory(null, 54, title)
        fillBorder(inv)

        val slotMap = mutableMapOf<Int, CosmeticDefinition>()
        var slot = 10

        all.forEach { def ->
            if (slot >= 44) return@forEach
            if (slot % 9 == 0 || slot % 9 == 8) slot++
            if (slot >= 44) return@forEach

            val item = buildShopItem(player, def, coins)
            inv.setItem(slot, item)
            slotMap[slot] = def
            slot++
        }

        // Back
        val back = ItemStack(Material.ARROW)
        val backMeta = back.itemMeta!!
        backMeta.setDisplayName("§e§lBack")
        backMeta.lore = listOf("§7Return to the shop overview.")
        back.itemMeta = backMeta
        inv.setItem(45, back)

        // Coins display
        val coinsItem = ItemStack(Material.SUNFLOWER)
        val coinsMeta = coinsItem.itemMeta!!
        coinsMeta.setDisplayName("§6§lYour Coins: §e$coins")
        coinsMeta.lore = listOf("§7Use coins to buy cosmetics.")
        coinsItem.itemMeta = coinsMeta
        inv.setItem(49, coinsItem)

        // Close
        val close = ItemStack(Material.BARRIER)
        val closeMeta = close.itemMeta!!
        closeMeta.setDisplayName("§c§lClose")
        close.itemMeta = closeMeta
        inv.setItem(53, close)

        player.openInventory(inv)
        openInventories[player.uniqueId] = ShopSession(inv, type, slotMap)
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
                45 -> { player.closeInventory(); plugin.cosmeticManager.selectGui.openOverview(player) }
                49 -> player.closeInventory()
            }
        } else {
            // Type view mode
            when (slot) {
                45 -> { player.closeInventory(); openOverview(player) }
                49 -> { /* coins display, no action */ }
                53 -> player.closeInventory()
                else -> {
                    val def = session.slots[slot] ?: return
                    handlePurchase(player, def)
                }
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        openInventories.remove((event.player as? Player)?.uniqueId)
    }

    // ── Purchase logic ────────────────────────────────────────────────────────

    private fun handlePurchase(player: Player, def: CosmeticDefinition) {
        if (manager.owns(player, def)) {
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
                openTypeView(player, def.type)
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fillBorder(inv: Inventory) {
        val pane = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
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
