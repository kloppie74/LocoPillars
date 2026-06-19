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
 *   /cosmetics shop          → openOverview()  (category picker)
 *   /cosmetics shop <type>   → openTypeView()  (skip to type)
 *   Click category → openTypeView()   (smooth — no close/reopen flicker)
 *   Back button    → openOverview()   (smooth)
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

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun pane(material: Material, name: String = " "): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta!!
        meta.setDisplayName(name)
        item.itemMeta = meta
        return item
    }

    private fun fillBorder(inv: Inventory, material: Material = Material.PURPLE_STAINED_GLASS_PANE) {
        val p = pane(material)
        val size = inv.size
        for (i in 0 until 9) inv.setItem(i, p)
        for (i in size - 9 until size) inv.setItem(i, p)
        for (i in 0 until size step 9) inv.setItem(i, p)
        for (i in 8 until size step 9) inv.setItem(i, p)
    }

    /** Opent een nieuwe view ZONDER eerst te sluiten — voorkomt flicker. */
    private fun switchTo(player: Player, session: ShopSession) {
        openInventories[player.uniqueId] = session   // session vóór openInventory zetten
        player.openInventory(session.inventory)       // sluit oude, opent nieuwe (1 packet)
    }

    // ── Entry points ───────────────────────────────────────────────────────────

    fun open(player: Player, type: CosmeticType? = null) {
        if (type == null) openOverview(player) else openTypeView(player, type)
    }

    // ── Overview (category picker, 54 slots) ──────────────────────────────────

    fun openOverview(player: Player) {
        val inv = Bukkit.createInventory(null, 54, "§5§lCosmetics Shop")
        fillBorder(inv)

        val coins = manager.getCoins(player)

        // Dekoratieve accent-panes rondom categorieën
        val accentPane = pane(Material.MAGENTA_STAINED_GLASS_PANE)
        listOf(10, 12, 14, 16, 20, 24, 28, 29, 30, 32, 33, 34).forEach { inv.setItem(it, accentPane) }

        // Categorie-knoppen  (slots: 11 KILL · 13 WIN · 15 DEATH · 31 CAGE)
        val categoryData = listOf(
            11 to CosmeticType.KILL,
            13 to CosmeticType.WIN,
            15 to CosmeticType.DEATH,
            31 to CosmeticType.CAGE
        )
        categoryData.forEach { (slot, type) ->
            val all     = manager.loader.getAll(type)
            val owned   = manager.getOwnedIds(player, type).size
            val total   = all.size
            val selected = manager.getSelected(player, type)

            val item = ItemStack(categoryIcon(type))
            val meta = item.itemMeta!!
            meta.setDisplayName("${categoryColor(type)}§l${type.displayName}")
            meta.lore = buildList {
                add("§8▸ §7Owned: §f$owned §7/ §f$total")
                add(if (selected != null) "§8▸ §7Active: §a${selected.displayName}" else "§8▸ §7Active: §8None")
                val cheapest = all.filter { !manager.owns(player, it) && it.price > 0 }.minByOrNull { it.price }
                add("")
                if (cheapest != null) add("§8▸ §7Cheapest: §6${cheapest.price} coins")
                else add("§8▸ §a✔ Everything owned!")
                if (type == CosmeticType.CAGE) add("§8▸ §b✦ Customize your spawn cage!")
                add("")
                add("§e▶ Click to browse")
            }
            item.itemMeta = meta
            inv.setItem(slot, item)
        }

        // Coins display (slot 22 — center)
        val coinsItem = ItemStack(Material.SUNFLOWER)
        val coinsMeta = coinsItem.itemMeta!!
        coinsMeta.setDisplayName("§6§lYour Coins: §e$coins")
        coinsMeta.lore = listOf("§7Earn coins by playing!", "§7Use them to unlock cosmetics.")
        coinsItem.itemMeta = coinsMeta
        inv.setItem(22, coinsItem)

        // My Cosmetics knop (slot 45)
        val myItem = ItemStack(Material.CHEST)
        val myMeta = myItem.itemMeta!!
        myMeta.setDisplayName("§b§lMy Cosmetics")
        myMeta.lore = listOf("§7View and equip your cosmetics.")
        myItem.itemMeta = myMeta
        inv.setItem(45, myItem)

        // Sluiten (slot 49)
        inv.setItem(49, closeButton())

        switchTo(player, ShopSession(inv, null))
    }

    // ── Type view (cosmetics list, 54 slots) ──────────────────────────────────

    fun openTypeView(player: Player, type: CosmeticType) {
        val all   = manager.loader.getAll(type)
        val coins = manager.getCoins(player)
        val title = "${categoryColor(type)}§l${type.displayName} Shop"

        val inv = Bukkit.createInventory(null, 54, title)
        fillBorder(inv, categoryPaneColor(type))

        val slotMap = mutableMapOf<Int, CosmeticDefinition>()

        // Items op inner-slots (rijen 2–5, kolommen 2–8)
        val innerSlots = (10..43).filter { it % 9 != 0 && it % 9 != 8 }
        all.forEachIndexed { index, def ->
            val slot = innerSlots.getOrNull(index) ?: return@forEachIndexed
            val item = buildShopItem(player, def, coins)
            inv.setItem(slot, item)
            slotMap[slot] = def
        }

        // Rij 6: Terug / Coins / Sluiten
        val backItem = ItemStack(Material.ARROW)
        val backMeta = backItem.itemMeta!!
        backMeta.setDisplayName("§e§l← Back")
        backMeta.lore = listOf("§7Back to overview.")
        backItem.itemMeta = backMeta
        inv.setItem(45, backItem)

        val coinsItem = ItemStack(Material.SUNFLOWER)
        val coinsMeta = coinsItem.itemMeta!!
        coinsMeta.setDisplayName("§6§lYour Coins: §e$coins")
        coinsMeta.lore = listOf("§7Use coins to buy cosmetics.")
        coinsItem.itemMeta = coinsMeta
        inv.setItem(49, coinsItem)

        inv.setItem(53, closeButton())

        switchTo(player, ShopSession(inv, type, slotMap))
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
                45 -> plugin.cosmeticManager.selectGui.openOverview(player)
                49 -> player.closeInventory()
            }
        } else {
            when (slot) {
                45 -> openOverview(player)
                49 -> { /* coins, geen actie */ }
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
        val player = event.player as? Player ?: return
        val session = openInventories[player.uniqueId] ?: return
        // Alleen verwijderen als dit de HUIDIGE session-inventory is
        // (niet als er al geswitcht werd naar een nieuwe view)
        if (event.inventory == session.inventory) {
            openInventories.remove(player.uniqueId)
        }
    }

    // ── Purchase logic ────────────────────────────────────────────────────────

    private fun handlePurchase(player: Player, def: CosmeticDefinition) {
        if (manager.owns(player, def)) {
            manager.setSelected(player, def)
            player.sendMessage(MSG.chatColors("&aEquipped: &f${def.displayName}"))
            openTypeView(player, def.type)
            return
        }

        when (manager.purchase(player, def)) {
            CosmeticManager.PurchaseResult.SUCCESS -> {
                player.sendMessage(MSG.chatColors("&aPurchased: &f${def.displayName} &afor &6${def.price} coins&a!"))
                manager.setSelected(player, def)
                player.sendMessage(MSG.chatColors("&aAuto-equipped as active ${def.type.displayName}!"))
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

        val owned    = manager.owns(player, def)
        val selected = manager.getSelected(player, def.type)?.id == def.id

        val namePrefix = when {
            selected -> "§a§l✔ "
            owned    -> "§7"
            else     -> def.rarity.color
        }
        meta.setDisplayName("$namePrefix${def.displayName}")
        if (def.iconCustomModelData > 0) meta.setCustomModelData(def.iconCustomModelData)

        meta.lore = buildList {
            add("§8${def.rarity.color}${def.rarity.displayName}")
            add("")
            def.lore.forEach { add(MSG.chatColors(it)) }
            add("")
            when {
                selected -> { add("§a§l★ ACTIVE"); add("§7Right-click to unequip") }
                owned    -> add("§e▶ Click to equip")
                def.price == 0 -> { add("§a§lFREE"); add("§e▶ Click to equip") }
                else -> {
                    val canAfford = playerCoins >= def.price
                    add(if (canAfford) "§6§lPrice: §e${def.price} coins" else "§c§lPrice: §4${def.price} coins §c(${def.price - playerCoins} more needed)")
                    if (def.requiredLevel > 0) add("§7Required level: §f${def.requiredLevel}")
                    add(if (canAfford) "§e▶ Click to buy" else "§c✗ Not enough coins")
                }
            }
        }
        item.itemMeta = meta
        return item
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
