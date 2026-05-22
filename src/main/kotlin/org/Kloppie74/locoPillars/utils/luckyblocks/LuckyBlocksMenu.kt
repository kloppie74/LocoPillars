package org.Kloppie74.locoPillars.utils.luckyblocks

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import org.Kloppie74.locoPillars.utils.luckyblocks.engine.LuckyblockLoader
import kotlin.math.max
import kotlin.math.min

object LuckyBlocksMenu : Listener {

    private const val MENU_TITLE = "LuckyBlocks"
    private const val ROWS = 6
    private const val COLS = 9
    private const val BLOCKS_PER_PAGE = (ROWS - 2) * (COLS - 2) // 28

    private val playerPages = mutableMapOf<Player, Int>()
    private val openMenus = mutableSetOf<Player>()

    // Set once from LocoPillars onEnable
    lateinit var loader: LuckyblockLoader

    fun openMenu(player: Player, page: Int = 0) {
        val all = loader.getAll()
        val maxPage = max(0, (all.size - 1) / BLOCKS_PER_PAGE)
        val current = min(max(page, 0), maxPage)
        playerPages[player] = current

        val inv = Bukkit.createInventory(null, ROWS * COLS, "$MENU_TITLE §7— Page ${current + 1}/${maxPage + 1}")

        // Border
        val border = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val borderMeta = border.itemMeta
        borderMeta?.setDisplayName(" ")
        border.itemMeta = borderMeta
        for (slot in 0 until ROWS * COLS) {
            if (isBorder(slot)) inv.setItem(slot, border)
        }

        // Luckyblock items
        val start = current * BLOCKS_PER_PAGE
        val end = min(start + BLOCKS_PER_PAGE, all.size)
        for ((i, lb) in all.subList(start, end).withIndex()) {
            val item = ItemStack(lb.settings.icon.material)
            val meta = item.itemMeta ?: continue
            if (lb.settings.icon.customModelData > 0) {
                try { meta.setCustomModelData(lb.settings.icon.customModelData) } catch (_: Exception) { }
            }
            val rarityColor = lb.settings.rarity.color
            meta.setDisplayName(MSG.chatColors("&e${lb.settings.displayName} ${rarityColor}[${lb.settings.rarity.name.replaceFirstChar { it.uppercase() }}]"))
            val lore = mutableListOf<String>()
            if (lb.settings.description.isNotBlank()) lore.add(MSG.chatColors("&f${lb.settings.description}"))
            lore.add("")
            lore.add(MSG.chatColors("&8Rarity: $rarityColor${lb.settings.rarity.name.replaceFirstChar { it.uppercase() }}"))
            lore.add(MSG.chatColors("&8ID: &7${lb.settings.id}"))
            meta.lore = lore
            item.itemMeta = meta
            inv.setItem(getContentSlot(i), item)
        }

        // Navigation arrows
        if (current > 0) {
            val left = ItemStack(Material.ARROW)
            val lm = left.itemMeta
            lm?.setDisplayName(MSG.chatColors("&bPrevious Page"))
            left.itemMeta = lm
            inv.setItem(arrowLeftSlot(), left)
        }
        if (current < maxPage) {
            val right = ItemStack(Material.ARROW)
            val rm = right.itemMeta
            rm?.setDisplayName(MSG.chatColors("&bNext Page"))
            right.itemMeta = rm
            inv.setItem(arrowRightSlot(), right)
        }

        openMenus.add(player)
        player.openInventory(inv)
    }

    private fun isBorder(slot: Int): Boolean {
        val row = slot / COLS; val col = slot % COLS
        return row == 0 || row == ROWS - 1 || col == 0 || col == COLS - 1
    }

    private fun getContentSlot(index: Int): Int = (index / 7 + 1) * COLS + (index % 7 + 1)
    private fun arrowLeftSlot(): Int = (ROWS / 2) * COLS
    private fun arrowRightSlot(): Int = (ROWS / 2) * COLS + COLS - 1

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!openMenus.contains(player)) return
        if (!event.view.title.startsWith(MENU_TITLE)) return
        event.isCancelled = true

        val clicked = event.currentItem ?: return
        val slot = event.rawSlot

        if (clicked.type == Material.ARROW && slot == arrowLeftSlot()) {
            openMenu(player, (playerPages[player] ?: 0) - 1)
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            return
        }
        if (clicked.type == Material.ARROW && slot == arrowRightSlot()) {
            openMenu(player, (playerPages[player] ?: 0) + 1)
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            return
        }
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 2f)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        playerPages.remove(player)
        openMenus.remove(player)
    }
}
