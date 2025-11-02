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
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import org.Kloppie74.locoPillars.utils.MSG
import kotlin.math.max
import kotlin.math.min

object LuckyBlocksMenu : Listener {

    private val menuTitle = "LuckyBlocks"
    private val rows = 6
    private val cols = 9
    private val blocksPerPage = (rows - 2) * (cols - 2) // 7x4 = 28
    private val borderMaterial = Material.GRAY_STAINED_GLASS_PANE
    private val arrowLeft = Material.ARROW
    private val arrowRight = Material.ARROW
    private val pageTag = "§7Page: "

    private val playerPages = mutableMapOf<Player, Int>()
    private val openMenus = mutableSetOf<Player>()

    fun openMenu(config: ConfigManager, player: Player, page: Int = 0) {
        val luckyBlockFiles = LuckyBlockYamlLoader.getAllLuckyBlocksSortedAZ()
        val maxPage = max(0, ((luckyBlockFiles.size - 1) / blocksPerPage))
        val currentPage = min(max(page, 0), maxPage)
        playerPages[player] = currentPage

        val inv = Bukkit.createInventory(null, rows * cols, "$menuTitle $pageTag${currentPage + 1}/${maxPage + 1}")

        for (slot in 0 until rows * cols) {
            if (isBorder(slot)) {
                val item = ItemStack(borderMaterial)
                val meta = item.itemMeta
                meta?.setDisplayName(" ")
                item.itemMeta = meta
                inv.setItem(slot, item)
            }
        }

        val startIndex = currentPage * blocksPerPage
        val endIndex = min(startIndex + blocksPerPage, luckyBlockFiles.size)
        var blockSlot = 0
        for (i in startIndex until endIndex) {
            val luckyBlock = luckyBlockFiles[i]
            val slot = getSlotFromIndex(blockSlot)
            val item = luckyBlock.toMenuItem()
            inv.setItem(slot, item)
            blockSlot++
        }

        if (currentPage > 0) {
            val leftArrow = ItemStack(arrowLeft)
            val leftMeta = leftArrow.itemMeta
            leftMeta?.setDisplayName(MSG.chatColors("&bPrevious Page"))
            leftArrow.itemMeta = leftMeta
            inv.setItem(getArrowLeftSlot(), leftArrow)
        }
        if (currentPage < maxPage) {
            val rightArrow = ItemStack(arrowRight)
            val rightMeta = rightArrow.itemMeta
            rightMeta?.setDisplayName(MSG.chatColors("&bNext Page"))
            rightArrow.itemMeta = rightMeta
            inv.setItem(getArrowRightSlot(), rightArrow)
        }

        openMenus.add(player)
        player.openInventory(inv)
    }

    private fun isBorder(slot: Int): Boolean {
        val row = slot / cols
        val col = slot % cols
        return row == 0 || row == rows - 1 || col == 0 || col == cols - 1
    }

    private fun getSlotFromIndex(index: Int): Int {
        val row = index / 7 + 1
        val col = index % 7 + 1
        return row * cols + col
    }

    private fun getArrowLeftSlot(): Int = ((rows / 2) * cols)
    private fun getArrowRightSlot(): Int = ((rows / 2) * cols) + cols - 1

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!openMenus.contains(player)) return
        if (!event.view.title.startsWith(menuTitle)) return

        event.isCancelled = true

        val clicked = event.currentItem ?: return
        val slot = event.rawSlot

        if (clicked.type == arrowLeft && slot == getArrowLeftSlot()) {
            val page = (playerPages[player] ?: 0) - 1
            openMenu(LuckyBlockYamlLoader.configManager, player, page)
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            return
        }
        if (clicked.type == arrowRight && slot == getArrowRightSlot()) {
            val page = (playerPages[player] ?: 0) + 1
            openMenu(LuckyBlockYamlLoader.configManager, player, page)
            player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 1f)
            return
        }

        if (isBorder(slot)) return

        // NO GIVING FROM MENU!
        player.playSound(player.location, Sound.UI_BUTTON_CLICK, 1f, 2f)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        playerPages.remove(player)
        openMenus.remove(player)
    }
}

object LuckyBlockYamlLoader {
    lateinit var configManager: ConfigManager
    val LUCKY_BLOCK_ID_KEY = org.bukkit.NamespacedKey.minecraft("lucky_block_id")

    data class LuckyBlockData(
        val id: String,
        val displayName: String,
        val description: String,
        val rarity: String,
        val iconMaterial: Material,
        val iconCustomModelData: Int?,
        val blockType: String,
        val effects: List<String>,
        val conditions: List<String>,
        val filters: List<String>,
        val triggers: List<String>
    ) {
        fun toMenuItem(): ItemStack {
            val item = ItemStack(iconMaterial)
            val meta = item.itemMeta
            if (iconCustomModelData != null && meta != null) {
                try { meta.setCustomModelData(iconCustomModelData) } catch (_: Exception) { }
            }
            meta?.setDisplayName(MSG.chatColors("&e${displayName} &7[${LuckyBlocksUtil.getRarityDisplay(rarity)}]"))
            val lore = mutableListOf<String>()
            if (description.isNotBlank()) lore.add(MSG.chatColors("&f$description"))
            lore.add(MSG.chatColors("&8Type: &7$blockType"))
            lore.add(MSG.chatColors("&8Rarity: &b${LuckyBlocksUtil.getRarityDisplay(rarity)}"))
            if (effects.isNotEmpty()) {
                lore.add(MSG.chatColors("&8Effects:"))
                effects.forEach { lore.add(MSG.chatColors("&7- $it")) }
            }
            if (conditions.isNotEmpty()) {
                lore.add(MSG.chatColors("&8Conditions:"))
                conditions.forEach { lore.add(MSG.chatColors("&7- $it")) }
            }
            if (filters.isNotEmpty()) {
                lore.add(MSG.chatColors("&8Filters:"))
                filters.forEach { lore.add(MSG.chatColors("&7- $it")) }
            }
            if (triggers.isNotEmpty()) {
                lore.add(MSG.chatColors("&8Triggers:"))
                triggers.forEach { lore.add(MSG.chatColors("&7- $it")) }
            }
            lore.add(MSG.chatColors("&8ID: &7$id"))
            meta?.lore = lore
            meta?.persistentDataContainer?.set(LUCKY_BLOCK_ID_KEY, org.bukkit.persistence.PersistentDataType.STRING, id)
            item.itemMeta = meta
            return item
        }
    }

    fun getAllLuckyBlocksSortedAZ(): List<LuckyBlockData> {
        val files = Bukkit.getServer().pluginManager.getPlugin("LocoPillars")?.dataFolder
            ?.resolve("Luckyblocks")?.listFiles { f -> f.name.endsWith(".yml") } ?: return emptyList()
        val blocks = mutableListOf<LuckyBlockData>()
        for (file in files) {
            val yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
            val id = yaml.getString("id") ?: continue
            val displayName = yaml.getString("display_name") ?: id
            val description = yaml.getString("description") ?: ""
            val rarity = yaml.getString("rarity") ?: "COMMON"
            val blockType = yaml.getString("block_type") ?: "LUCKY_BLOCK"
            val iconSection = yaml.getConfigurationSection("icon")
            val iconMaterial = try {
                Material.valueOf(iconSection?.getString("material", "PLAYER_HEAD") ?: "PLAYER_HEAD")
            } catch (e: Exception) { Material.PLAYER_HEAD }
            val iconCustomModelData = iconSection?.getInt("custom_model_data", 0)
            val effectsList = (yaml.getList("effects") ?: emptyList<Any>()).map { e ->
                if (e is Map<*, *>) {
                    val type = e["type"]?.toString() ?: ""
                    if (e.containsKey("message")) "$type (${e["message"]})" else type
                } else e.toString()
            }.filter { it.isNotBlank() }
            val conditionsList = (yaml.getList("conditions") ?: emptyList<Any>()).map { it.toString() }.filter { it.isNotBlank() }
            val filtersList = (yaml.getList("filters") ?: emptyList<Any>()).map { it.toString() }.filter { it.isNotBlank() }
            val triggersList = (yaml.getList("triggers") ?: emptyList<Any>()).map { trig ->
                if (trig is Map<*, *>) {
                    val event = trig["event"]?.toString() ?: ""
                    val itemName = trig["item_name"]?.toString()
                    val effect = trig["effect"]
                    buildString {
                        append(event)
                        if (itemName != null) append(" (Item: $itemName)")
                        if (effect != null) append(" -> Effect: $effect")
                    }
                } else trig.toString()
            }.filter { it.isNotBlank() }
            blocks.add(
                LuckyBlockData(
                    id,
                    displayName,
                    description,
                    rarity.uppercase(),
                    iconMaterial,
                    iconCustomModelData,
                    blockType,
                    effectsList,
                    conditionsList,
                    filtersList,
                    triggersList
                )
            )
        }
        return blocks.sortedBy { it.displayName }
    }
}