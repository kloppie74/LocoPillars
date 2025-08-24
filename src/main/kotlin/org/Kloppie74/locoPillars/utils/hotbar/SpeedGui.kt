package org.Kloppie74.locoPillars.utils.hotbar

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.ChatColor
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import org.Kloppie74.locoPillars.utils.spectator.SpectatorSpeedManager
import org.Kloppie74.locoPillars.LocoPillars
import java.io.File

object SpeedGui {
    private const val GUI_TITLE_RAW = "&8■ &fSpeed Selection &8■"
    private var GUI_TITLE_COLORED = ""
    private var isConfigLoaded = false

    // Configuration values
    private var guiTitle = GUI_TITLE_RAW
    private var borderMaterial = Material.BLACK_STAINED_GLASS_PANE
    private var fillerMaterial = Material.GRAY_STAINED_GLASS_PANE
    private var speedItem = Material.FEATHER
    private var closeItem = Material.BARRIER
    private val speedSlots = mutableMapOf<Int, Int>() // slot -> speedLevel
    private var closeSlot = 22

    fun initialize(plugin: LocoPillars) {
        if (isConfigLoaded) return

        try {
            val itemsFile = File(plugin.dataFolder, "items.yml")
            if (!itemsFile.exists()) {
                plugin.logger.warning("[SpeedGui] items.yml not found, using defaults")
                isConfigLoaded = true
                GUI_TITLE_COLORED = MSG.chatColors(guiTitle)
                setupDefaults()
                return
            }

            val config = YamlConfiguration.loadConfiguration(itemsFile)
            val menuSection = config.getConfigurationSection("Menu.Speed")

            if (menuSection == null) {
                plugin.logger.warning("[SpeedGui] Menu.Speed section not found in items.yml, using defaults")
                isConfigLoaded = true
                GUI_TITLE_COLORED = MSG.chatColors(guiTitle)
                setupDefaults()
                return
            }

            guiTitle = menuSection.getString("Title") ?: GUI_TITLE_RAW
            GUI_TITLE_COLORED = MSG.chatColors(guiTitle)

            val borderMat = menuSection.getString("Border.Item")
            if (borderMat != null) {
                Material.getMaterial(borderMat.uppercase())?.let { borderMaterial = it }
            }

            val fillerMat = menuSection.getString("Filler.Item")
            if (fillerMat != null) {
                Material.getMaterial(fillerMat.uppercase())?.let { fillerMaterial = it }
            }

            val speedsSection = menuSection.getConfigurationSection("Speeds")
            if (speedsSection != null) {
                speedSlots.clear()
                for (key in speedsSection.getKeys(false)) {
                    val speedSection = speedsSection.getConfigurationSection(key) ?: continue
                    val slot = speedSection.getInt("Slot", -1)
                    val speedLevel = speedSection.getInt("SpeedLevel", key.toIntOrNull() ?: 1)
                    if (slot >= 0) {
                        speedSlots[slot] = speedLevel
                    }
                    val itemMat = speedSection.getString("Item")
                    if (itemMat != null) {
                        Material.getMaterial(itemMat.uppercase())?.let { speedItem = it }
                    }
                }
            }

            val closeSection = menuSection.getConfigurationSection("Close")
            if (closeSection != null) {
                closeSlot = closeSection.getInt("Slot", 22)
                val closeMat = closeSection.getString("Item")
                if (closeMat != null) {
                    Material.getMaterial(closeMat.uppercase())?.let { closeItem = it }
                }
            }

            isConfigLoaded = true
            plugin.logger.info("[SpeedGui] Configuration loaded successfully")

        } catch (e: Exception) {
            plugin.logger.severe("[SpeedGui] Error loading configuration: ${e.message}")
            isConfigLoaded = true
            GUI_TITLE_COLORED = MSG.chatColors(guiTitle)
            setupDefaults()
        }
    }

    private fun setupDefaults() {
        speedSlots.clear()
        for (i in 1..7) {
            speedSlots[9 + i] = i // slots 10-16
        }
    }

    fun open(player: Player) {
        val plugin = getPluginInstance()

        if (!SpectatorUtils.isSpectator(player, plugin)) {
            player.sendMessage(MSG.chatColors("&cYou must be spectating to use the speed GUI!"))
            return
        }

        val inv = Bukkit.createInventory(null, 27, GUI_TITLE_COLORED)
        fillBorder(inv)
        fillEmpty(inv)
        addSpeedItems(inv, player)
        addCloseButton(inv)

        player.openInventory(inv)
    }

    fun handleClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        val title = try {
            event.view.title()?.let { ChatColor.stripColor(it.toString()) } ?: ""
        } catch (e: Exception) {
            @Suppress("DEPRECATION")
            event.view.title ?: ""
        }

        if (!isSpeedGui(title)) return

        event.isCancelled = true
        val clickedSlot = event.rawSlot
        val item = event.currentItem ?: return

        if (clickedSlot == closeSlot) {
            player.closeInventory()
            return
        }

        val speedLevel = speedSlots[clickedSlot]
        if (speedLevel != null && speedLevel in 1..7) {
            SpectatorSpeedManager.handleSpeedSelection(player, speedLevel)
            player.closeInventory()
        }
    }

    fun isSpeedGui(title: String): Boolean {
        val strippedTitle = ChatColor.stripColor(title)
        val strippedGuiTitle = ChatColor.stripColor(GUI_TITLE_COLORED)
        return strippedTitle == strippedGuiTitle || title == GUI_TITLE_COLORED
    }

    fun isSpeedGui(title: Any?): Boolean {
        return when (title) {
            is String -> isSpeedGui(title)
            else -> {
                val titleString = title?.toString() ?: ""
                isSpeedGui(titleString)
            }
        }
    }

    private fun fillBorder(inv: Inventory) {
        val borderItem = createBorderItem()
        val borderSlots = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26)

        for (slot in borderSlots) {
            if (!speedSlots.containsKey(slot) && slot != closeSlot) {
                inv.setItem(slot, borderItem)
            }
        }
    }

    private fun fillEmpty(inv: Inventory) {
        val fillerItem = createFillerItem()
        for (i in 0 until inv.size) {
            if (inv.getItem(i) == null && !speedSlots.containsKey(i) && i != closeSlot) {
                inv.setItem(i, fillerItem)
            }
        }
    }

    private fun addSpeedItems(inv: Inventory, player: Player) {
        val currentSpeed = SpectatorSpeedManager.getCurrentSpeedLevel(player)
        for ((slot, speedLevel) in speedSlots) {
            if (slot >= 0 && slot < inv.size) {
                val item = createSpeedItem(speedLevel, currentSpeed == speedLevel)
                inv.setItem(slot, item)
            }
        }
    }

    private fun addCloseButton(inv: Inventory) {
        val item = createCloseItem()
        inv.setItem(closeSlot, item)
    }

    private fun createBorderItem(): ItemStack {
        val item = ItemStack(borderMaterial)
        val meta = item.itemMeta
        meta?.setDisplayName(" ")
        meta?.lore = emptyList()
        item.itemMeta = meta
        return item
    }

    private fun createFillerItem(): ItemStack {
        val item = ItemStack(fillerMaterial)
        val meta = item.itemMeta
        meta?.setDisplayName(" ")
        meta?.lore = emptyList()
        item.itemMeta = meta
        return item
    }

    private fun createSpeedItem(speedLevel: Int, isCurrentSpeed: Boolean): ItemStack {
        val item = ItemStack(speedItem)
        val meta = item.itemMeta

        val speedValue = SpectatorSpeedManager.getSpeedValueForLevel(speedLevel)
        val displayName = if (isCurrentSpeed) {
            "&a&lSpeed &f$speedLevel &7(&e${String.format("%.1f", speedValue)}&7) &a✓"
        } else {
            "&bSpeed &f$speedLevel &7(&e${String.format("%.1f", speedValue)}&7)"
        }

        val lore = if (isCurrentSpeed) {
            listOf("&7Current speed level", "&aClick to confirm")
        } else {
            listOf("&7Click to set speed to &e${String.format("%.1f", speedValue)}", "&7Level: &f$speedLevel")
        }

        meta?.setDisplayName(MSG.chatColors(displayName))
        meta?.lore = lore.map { MSG.chatColors(it) }

        val plugin = getPluginInstance()
        val key = NamespacedKey(plugin, "speedLevel")
        meta?.persistentDataContainer?.set(key, PersistentDataType.INTEGER, speedLevel)

        item.itemMeta = meta
        return item
    }

    private fun createCloseItem(): ItemStack {
        val item = ItemStack(closeItem)
        val meta = item.itemMeta

        meta?.setDisplayName(MSG.chatColors("&cClose Menu"))
        meta?.lore = listOf(MSG.chatColors("&7Click to close this menu"))

        item.itemMeta = meta
        return item
    }

    private fun getPluginInstance(): LocoPillars {
        return Bukkit.getPluginManager().getPlugin("LocoPillars") as LocoPillars
    }

    fun reload(plugin: LocoPillars) {
        isConfigLoaded = false
        speedSlots.clear()
        initialize(plugin)
    }

    fun getGuiTitle(): String {
        return GUI_TITLE_COLORED
    }

    fun hasSpeedGuiOpen(player: Player): Boolean {
        val openInv = player.openInventory.topInventory

        return try {
            val title = player.openInventory.title()
            isSpeedGui(title?.toString() ?: "")
        } catch (e: Exception) {
            try {
                @Suppress("DEPRECATION")
                val title = player.openInventory.title
                isSpeedGui(title ?: "")
            } catch (e2: Exception) {
                openInv.size == 27 && speedSlots.isNotEmpty()
            }
        }
    }

    fun closeAllSpeedGuis() {
        Bukkit.getOnlinePlayers().forEach { player ->
            if (hasSpeedGuiOpen(player)) {
                player.closeInventory()
            }
        }
    }

    fun isSpeedGuiInventory(inventory: Inventory): Boolean {
        return inventory.size == 27 && speedSlots.any { (slot, _) ->
            inventory.getItem(slot)?.type == speedItem
        }
    }
}