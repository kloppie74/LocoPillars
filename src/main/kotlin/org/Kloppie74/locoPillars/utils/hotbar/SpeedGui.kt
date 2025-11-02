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
import java.util.*
import java.util.concurrent.ConcurrentHashMap

data class SpeedSlotConfig(
    val slot: Int,
    val speedLevel: Int,
    val item: ItemStack
)

object SpeedGui {
    private const val GUI_TITLE_RAW = "&8■ &fSpeed Selection &8■"
    private var GUI_TITLE_COLORED = ""
    private var isConfigLoaded = false
    private var plugin: LocoPillars? = null

    // Configuration values
    private var guiTitle = GUI_TITLE_RAW
    private var borderMaterial = Material.BLACK_STAINED_GLASS_PANE
    private var fillerMaterial = Material.GRAY_STAINED_GLASS_PANE
    private var closeItem = Material.BARRIER
    private var closeSlot = 22
    private val speedSlots = mutableListOf<SpeedSlotConfig>()
    private val itemsAdderEnabled by lazy { isItemsAdderPresent() }

    // Track open SpeedGui inventories by player UUID
    private val openSpeedGuiPlayers: MutableSet<UUID> = Collections.newSetFromMap(ConcurrentHashMap())

    fun initialize(pluginInstance: LocoPillars) {
        plugin = pluginInstance
        if (isConfigLoaded) return

        try {
            val itemsFile = File(pluginInstance.dataFolder, "items.yml")
            if (!itemsFile.exists()) {
                pluginInstance.logger.warning("[SpeedGui] items.yml not found, using defaults")
                setupDefaults()
                return
            }

            val config = YamlConfiguration.loadConfiguration(itemsFile)
            val menuSection = config.getConfigurationSection("Menu.Speed")

            if (menuSection == null) {
                pluginInstance.logger.warning("[SpeedGui] Menu.Speed section not found in items.yml, using defaults")
                setupDefaults()
                return
            }

            loadConfiguration(menuSection, pluginInstance)
            isConfigLoaded = true
            pluginInstance.logger.info("[SpeedGui] Configuration loaded successfully with ${speedSlots.size} speed items")

        } catch (e: Exception) {
            pluginInstance.logger.severe("[SpeedGui] Error loading configuration: ${e.message}")
            e.printStackTrace()
            setupDefaults()
        }
    }

    private fun loadConfiguration(menuSection: org.bukkit.configuration.ConfigurationSection, pluginInstance: LocoPillars) {
        // Load title
        guiTitle = menuSection.getString("Title") ?: GUI_TITLE_RAW
        GUI_TITLE_COLORED = MSG.chatColors(guiTitle)

        // Load border material
        val borderMat = menuSection.getString("Border.Item")
        if (borderMat != null) {
            Material.getMaterial(borderMat.uppercase())?.let { borderMaterial = it }
        }

        // Load filler material
        val fillerMat = menuSection.getString("Filler.Item")
        if (fillerMat != null) {
            Material.getMaterial(fillerMat.uppercase())?.let { fillerMaterial = it }
        }

        // Load close button
        val closeSection = menuSection.getConfigurationSection("Close")
        if (closeSection != null) {
            closeSlot = closeSection.getInt("Slot", 22)
            val closeMat = closeSection.getString("Item")
            if (closeMat != null) {
                Material.getMaterial(closeMat.uppercase())?.let { closeItem = it }
            }
        }

        // Load speed items
        speedSlots.clear()
        val speedsSection = menuSection.getConfigurationSection("Speeds")
        if (speedsSection != null) {
            for (key in speedsSection.getKeys(false)) {
                val speedSection = speedsSection.getConfigurationSection(key) ?: continue

                val slot = speedSection.getInt("Slot", -1)
                val speedLevel = speedSection.getInt("SpeedLevel", key.toIntOrNull() ?: 1)
                val itemConfig = speedSection.getString("Item") ?: continue

                if (slot < 0 || slot >= 27) {
                    pluginInstance.logger.warning("[SpeedGui] Invalid slot $slot for speed $key, skipping")
                    continue
                }

                val itemStack = createItemFromConfig(itemConfig, speedLevel, pluginInstance)
                if (itemStack != null) {
                    speedSlots.add(SpeedSlotConfig(slot, speedLevel, itemStack))
                } else {
                    pluginInstance.logger.warning("[SpeedGui] Failed to create item for speed $key with config '$itemConfig'")
                }
            }
        }

        // Sort speed slots by slot number for consistent ordering
        speedSlots.sortBy { it.slot }
    }

    private fun createItemFromConfig(itemConfig: String, speedLevel: Int, pluginInstance: LocoPillars): ItemStack? {
        return when {
            itemConfig.startsWith("itemsadder-", true) -> {
                createItemsAdderItem(itemConfig, speedLevel, pluginInstance)
            }
            else -> {
                createBukkitItem(itemConfig, speedLevel, pluginInstance)
            }
        }
    }

    private fun createItemsAdderItem(itemConfig: String, speedLevel: Int, pluginInstance: LocoPillars): ItemStack? {
        if (!itemsAdderEnabled) {
            pluginInstance.logger.warning("[SpeedGui] ItemsAdder item '$itemConfig' requested, but ItemsAdder is NOT installed!")
            return createFallbackSpeedItem(speedLevel)
        }

        return try {
            val classIa = Class.forName("dev.lone.itemsadder.api.CustomStack")
            val getInstance = classIa.getMethod("getInstance", String::class.java)
            val id = itemConfig.removePrefix("itemsadder-").trim()
            val customStack = getInstance.invoke(null, id)

            if (customStack == null) {
                pluginInstance.logger.warning("[SpeedGui] ItemsAdder item '$id' not found")
                return createFallbackSpeedItem(speedLevel)
            }

            val itemStack = classIa.getMethod("getItemStack").invoke(customStack) as ItemStack
            val stack = itemStack.clone()

            // Apply speed item metadata
            applySpeedItemMeta(stack, speedLevel, pluginInstance)
            stack

        } catch (ex: Exception) {
            pluginInstance.logger.warning("[SpeedGui] ItemsAdder reflection error for '$itemConfig': ${ex.message}")
            createFallbackSpeedItem(speedLevel)
        }
    }

    private fun createBukkitItem(itemConfig: String, speedLevel: Int, pluginInstance: LocoPillars): ItemStack? {
        val material = Material.getMaterial(itemConfig.uppercase())
        if (material == null) {
            pluginInstance.logger.warning("[SpeedGui] Invalid material '$itemConfig' for speed level $speedLevel")
            return createFallbackSpeedItem(speedLevel)
        }

        val stack = ItemStack(material)
        applySpeedItemMeta(stack, speedLevel, pluginInstance)
        return stack
    }

    private fun createFallbackSpeedItem(speedLevel: Int): ItemStack {
        val stack = ItemStack(Material.FEATHER)
        plugin?.let { applySpeedItemMeta(stack, speedLevel, it) }
        return stack
    }

    private fun applySpeedItemMeta(item: ItemStack, speedLevel: Int, pluginInstance: LocoPillars) {
        val meta = item.itemMeta ?: return

        val speedValue = SpectatorSpeedManager.getSpeedValueForLevel(speedLevel)
        val displayName = "&bSpeed &f$speedLevel &7(&e${String.format("%.1f", speedValue)}&7)"
        val lore = listOf(
            "&7Click to set speed to &e${String.format("%.1f", speedValue)}",
            "&7Level: &f$speedLevel"
        )

        meta.setDisplayName(MSG.chatColors(displayName))
        meta.lore = lore.map { MSG.chatColors(it) }

        // Add NBT data for identification
        val key = NamespacedKey(pluginInstance, "speedLevel")
        meta.persistentDataContainer.set(key, PersistentDataType.INTEGER, speedLevel)

        item.itemMeta = meta
    }

    private fun setupDefaults() {
        GUI_TITLE_COLORED = MSG.chatColors(guiTitle)
        speedSlots.clear()

        // Create default speed items (slots 10-16, speed levels 1-7)
        for (i in 1..7) {
            val slot = 9 + i
            val item = ItemStack(Material.FEATHER)
            plugin?.let { applySpeedItemMeta(item, i, it) }
            speedSlots.add(SpeedSlotConfig(slot, i, item))
        }

        isConfigLoaded = true
    }

    private fun isItemsAdderPresent(): Boolean {
        return Bukkit.getPluginManager().getPlugin("ItemsAdder") != null
    }

    fun open(player: Player) {
        val pluginInstance = plugin ?: run {
            player.sendMessage(MSG.chatColors("&cPlugin not initialized!"))
            return
        }

        if (!SpectatorUtils.isSpectator(player, pluginInstance)) {
            player.sendMessage(MSG.chatColors("&cYou must be spectating to use the speed GUI!"))
            return
        }

        val inv = Bukkit.createInventory(null, 27, GUI_TITLE_COLORED)
        fillBorder(inv)
        fillEmpty(inv)
        addSpeedItems(inv, player)
        addCloseButton(inv)

        player.openInventory(inv)
        openSpeedGuiPlayers.add(player.uniqueId)
    }

    fun handleClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        if (!hasSpeedGuiOpen(player)) return

        event.isCancelled = true
        val clickedSlot = event.rawSlot
        val item = event.currentItem ?: return

        // Handle close button
        if (clickedSlot == closeSlot) {
            player.closeInventory()
            openSpeedGuiPlayers.remove(player.uniqueId)
            return
        }

        // Handle speed selection
        val speedConfig = speedSlots.find { it.slot == clickedSlot }
        if (speedConfig != null) {
            val speedLevel = speedConfig.speedLevel
            if (speedLevel in 1..7) {
                SpectatorSpeedManager.handleSpeedSelection(player, speedLevel)
                player.closeInventory()
                openSpeedGuiPlayers.remove(player.uniqueId)
            }
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
            else -> isSpeedGui(title?.toString() ?: "")
        }
    }

    private fun fillBorder(inv: Inventory) {
        val borderItem = createBorderItem()
        val borderSlots = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 19, 20, 21, 23, 24, 25, 26)

        for (slot in borderSlots) {
            // Don't override speed slots or close slot
            if (speedSlots.none { it.slot == slot } && slot != closeSlot) {
                inv.setItem(slot, borderItem)
            }
        }
    }

    private fun fillEmpty(inv: Inventory) {
        val fillerItem = createFillerItem()
        for (i in 0 until inv.size) {
            if (inv.getItem(i) == null &&
                speedSlots.none { it.slot == i } &&
                i != closeSlot) {
                inv.setItem(i, fillerItem)
            }
        }
    }

    private fun addSpeedItems(inv: Inventory, player: Player) {
        val currentSpeed = SpectatorSpeedManager.getCurrentSpeedLevel(player)

        for (speedConfig in speedSlots) {
            if (speedConfig.slot >= 0 && speedConfig.slot < inv.size) {
                val item = speedConfig.item.clone()

                // Update display based on current speed
                if (speedConfig.speedLevel == currentSpeed) {
                    updateItemAsSelected(item, speedConfig.speedLevel)
                }

                inv.setItem(speedConfig.slot, item)
            }
        }
    }

    private fun updateItemAsSelected(item: ItemStack, speedLevel: Int) {
        val meta = item.itemMeta ?: return

        val speedValue = SpectatorSpeedManager.getSpeedValueForLevel(speedLevel)
        val displayName = "&a&lSpeed &f$speedLevel &7(&e${String.format("%.1f", speedValue)}&7) &a✓"
        val lore = listOf(
            "&7Current speed level",
            "&aClick to confirm"
        )

        meta.setDisplayName(MSG.chatColors(displayName))
        meta.lore = lore.map { MSG.chatColors(it) }

        item.itemMeta = meta
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

    private fun createCloseItem(): ItemStack {
        val item = ItemStack(closeItem)
        val meta = item.itemMeta

        meta?.setDisplayName(MSG.chatColors("&cClose Menu"))
        meta?.lore = listOf(MSG.chatColors("&7Click to close this menu"))

        item.itemMeta = meta
        return item
    }

    fun reload(pluginInstance: LocoPillars) {
        isConfigLoaded = false
        speedSlots.clear()
        plugin = pluginInstance
        initialize(pluginInstance)
    }

    fun getGuiTitle(): String {
        return GUI_TITLE_COLORED
    }

    fun hasSpeedGuiOpen(player: Player): Boolean {
        return openSpeedGuiPlayers.contains(player.uniqueId)
    }

    fun closeAllSpeedGuis() {
        Bukkit.getOnlinePlayers().forEach { player ->
            if (hasSpeedGuiOpen(player)) {
                player.closeInventory()
                openSpeedGuiPlayers.remove(player.uniqueId)
            }
        }
    }

    fun isSpeedGuiInventory(inventory: Inventory): Boolean {
        return inventory.size == 27 && speedSlots.any { config ->
            val item = inventory.getItem(config.slot)
            item != null && item.type != Material.AIR
        }
    }

    fun markInventoryClosed(player: Player) {
        openSpeedGuiPlayers.remove(player.uniqueId)
    }

    // Debug methods
    fun getLoadedSpeedSlots(): List<SpeedSlotConfig> {
        return speedSlots.toList()
    }

    fun isItemsAdderAvailable(): Boolean {
        return itemsAdderEnabled
    }

    fun validateConfiguration(): List<String> {
        val issues = mutableListOf<String>()

        if (speedSlots.isEmpty()) {
            issues.add("No speed slots configured")
        }

        val duplicateSlots = speedSlots.groupBy { it.slot }.filter { it.value.size > 1 }
        if (duplicateSlots.isNotEmpty()) {
            issues.add("Duplicate slots found: ${duplicateSlots.keys}")
        }

        val invalidSlots = speedSlots.filter { it.slot < 0 || it.slot >= 27 }
        if (invalidSlots.isNotEmpty()) {
            issues.add("Invalid slots: ${invalidSlots.map { it.slot }}")
        }

        val invalidSpeedLevels = speedSlots.filter { it.speedLevel < 1 || it.speedLevel > 7 }
        if (invalidSpeedLevels.isNotEmpty()) {
            issues.add("Invalid speed levels: ${invalidSpeedLevels.map { it.speedLevel }}")
        }

        return issues
    }
}