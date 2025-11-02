package org.Kloppie74.locoPillars.utils.hotbar

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import java.util.concurrent.ConcurrentHashMap

data class CustomHotbarItem(
    val item: ItemStack?,
    val command: String?,
    val speed: Float?,
    val slot: Int,
    val type: String
)

class HotbarItemManager(
    private val plugin: LocoPillars,
    private val itemsConfig: FileConfiguration
) {

    private val ITEM_CACHE = ConcurrentHashMap<String, List<CustomHotbarItem>>()
    private val NBT_KEY = NamespacedKey(plugin, "locoHotbarLocked")
    private val itemsAdderEnabled = isItemsAdderPresent()

    companion object {
        const val SPECTATE_TYPE = "spectate"
        const val LOBBY_TYPE = "lobby"
        const val GAME_LOBBY_TYPE = "game_lobby"
        const val CAGE_TYPE = "cage"
        const val GAME_TYPE = "game"
    }

    init {
        // Initialize SpeedGui with the plugin
        SpeedGui.initialize(plugin)
        // Load all item configurations
        loadItemConfigurations()
    }

    /**
     * Checks if ItemsAdder is present
     */
    private fun isItemsAdderPresent(): Boolean {
        return Bukkit.getPluginManager().getPlugin("ItemsAdder") != null
    }

    /**
     * Loads all item configurations from items.yml
     */
    private fun loadItemConfigurations() {
        ITEM_CACHE.clear()

        val itemsSection = itemsConfig.getConfigurationSection("items") ?: return

        for (type in itemsSection.getKeys(false)) {
            val typeItems = loadItemsForType(type)
            ITEM_CACHE[type.lowercase()] = typeItems
        }

        plugin.logger.info("[HotbarItemManager] Loaded ${ITEM_CACHE.size} item configurations")
    }


    /**
     * Loads items for a specific hotbar type
     * Supports ItemsAdder items via "itemsadder-namespace:itemid" (optioneel!)
     */
    private fun loadItemsForType(type: String): List<CustomHotbarItem> {
        val itemsSection = itemsConfig.getConfigurationSection("items.$type") ?: return emptyList()
        val result = mutableListOf<CustomHotbarItem>()

        for (key in itemsSection.getKeys(false)) {
            val itemData = itemsSection.getConfigurationSection(key) ?: continue

            try {
                val matName = itemData.getString("type")
                val item: ItemStack? = when {
                    matName == null -> null
                    matName.startsWith("itemsadder-", true) -> {
                        // ItemsAdder custom item: itemsadder-namespace:itemid
                        if (!itemsAdderEnabled) {
                            plugin.logger.warning("[HotbarItemManager] ItemsAdder item '$matName' requested, but ItemsAdder is NOT installed! Skipping...")
                            null
                        } else {
                            try {
                                // Dynamisch laden via reflectie
                                val classIa = Class.forName("dev.lone.itemsadder.api.CustomStack")
                                val getInstance = classIa.getMethod("getInstance", String::class.java)
                                val id = matName.removePrefix("itemsadder-").trim()
                                val customStack = getInstance.invoke(null, id)
                                if (customStack == null) {
                                    plugin.logger.warning("[HotbarItemManager] ItemsAdder item '$id' not found for $type.$key")
                                    null
                                } else {
                                    val itemStack = classIa.getMethod("getItemStack").invoke(customStack) as ItemStack
                                    val stack = itemStack.clone()
                                    val meta = stack.itemMeta
                                    if (meta != null) {
                                        // Add NBT data to mark as locked hotbar item
                                        meta.persistentDataContainer.set(NBT_KEY, PersistentDataType.BYTE, 1)
                                        val typeKey = NamespacedKey(plugin, "hotbarType")
                                        val slotKey = NamespacedKey(plugin, "hotbarSlot")
                                        meta.persistentDataContainer.set(typeKey, PersistentDataType.STRING, type)
                                        meta.persistentDataContainer.set(slotKey, PersistentDataType.INTEGER, itemData.getInt("slot", key.toIntOrNull() ?: 0))
                                        // Set display name/lore if custom
                                        val name = itemData.getString("name")
                                        val lore = itemData.getStringList("lore")
                                        if (name != null) meta.setDisplayName(MSG.chatColors(name))
                                        if (lore.isNotEmpty()) meta.lore = lore.map { MSG.chatColors(it) }
                                        stack.itemMeta = meta
                                    }
                                    stack
                                }
                            } catch (ex: Exception) {
                                plugin.logger.warning("[HotbarItemManager] ItemsAdder reflectie error: ${ex.message}")
                                null
                            }
                        }
                    }
                    else -> {
                        // Normale Bukkit item
                        val mat = Material.getMaterial(matName.uppercase())
                        if (mat == null) {
                            plugin.logger.warning("[HotbarItemManager] Invalid material '$matName' for item $type.$key")
                            null
                        } else {
                            val name = itemData.getString("name")
                            val lore = itemData.getStringList("lore")
                            val slot = itemData.getInt("slot", key.toIntOrNull() ?: 0)
                            createLockedHotbarItem(mat, name, lore, type, slot)
                        }
                    }
                }

                val command = itemData.getString("command")
                val speed = itemData.getDouble("speed", 0.0).toFloat().takeIf { it != 0.0f }
                val slot = itemData.getInt("slot", key.toIntOrNull() ?: 0)

                result.add(CustomHotbarItem(item, command, speed, slot, type))

            } catch (e: Exception) {
                plugin.logger.severe("[HotbarItemManager] Error loading item $type.$key: ${e.message}")
            }
        }

        return result
    }

    /**
     * Creates a locked hotbar item with NBT data
     */
    private fun createLockedHotbarItem(
        material: Material,
        name: String?,
        lore: List<String>,
        type: String,
        slot: Int
    ): ItemStack {
        val stack = ItemStack(material)
        val meta = stack.itemMeta

        if (meta != null) {
            // Set display properties
            if (name != null) {
                meta.setDisplayName(MSG.chatColors(name))
            }
            if (lore.isNotEmpty()) {
                meta.lore = lore.map { MSG.chatColors(it) }
            }

            // Add NBT data to mark as locked hotbar item
            meta.persistentDataContainer.set(NBT_KEY, PersistentDataType.BYTE, 1)

            // Add additional metadata for identification
            val typeKey = NamespacedKey(plugin, "hotbarType")
            val slotKey = NamespacedKey(plugin, "hotbarSlot")
            meta.persistentDataContainer.set(typeKey, PersistentDataType.STRING, type)
            meta.persistentDataContainer.set(slotKey, PersistentDataType.INTEGER, slot)

            stack.itemMeta = meta
        }

        return stack
    }

    /**
     * Gets custom items for a specific hotbar type
     */
    fun getCustomItemsForType(type: String): List<CustomHotbarItem> {
        return ITEM_CACHE[type.lowercase()] ?: emptyList()
    }

    /**
     * Gets the current hotbar type for a player (delegates to HotbarUtils)
     */
    fun getCurrentType(player: Player): String {
        return HotbarUtils.determineHotbarType(player, plugin)
    }

    /**
     * Checks if two ItemStacks are the same (by type, display name, and NBT)
     */
    fun isSameItem(item1: ItemStack?, item2: ItemStack?): Boolean {
        if (item1 == null || item2 == null) return false
        if (item1.type != item2.type) return false

        val meta1 = item1.itemMeta
        val meta2 = item2.itemMeta

        if (meta1 == null || meta2 == null) return meta1 == meta2

        // Compare display names
        if (meta1.displayName != meta2.displayName) return false

        // Compare NBT data
        val container1 = meta1.persistentDataContainer
        val container2 = meta2.persistentDataContainer

        // Check if both are locked hotbar items
        val isLocked1 = container1.has(NBT_KEY, PersistentDataType.BYTE)
        val isLocked2 = container2.has(NBT_KEY, PersistentDataType.BYTE)

        if (isLocked1 != isLocked2) return false

        if (isLocked1) {
            // Compare type and slot for locked items
            val typeKey = NamespacedKey(plugin, "hotbarType")
            val slotKey = NamespacedKey(plugin, "hotbarSlot")

            val type1 = container1.get(typeKey, PersistentDataType.STRING)
            val type2 = container2.get(typeKey, PersistentDataType.STRING)
            val slot1 = container1.get(slotKey, PersistentDataType.INTEGER)
            val slot2 = container2.get(slotKey, PersistentDataType.INTEGER)

            return type1 == type2 && slot1 == slot2
        }

        return true
    }

    /**
     * Checks if an item is a locked hotbar item
     */
    fun isLockedHotbarItem(item: ItemStack?): Boolean {
        return HotbarUtils.isLockedHotbarItem(item, plugin)
    }

    /**
     * Gets the hotbar type from an item's NBT data
     */
    fun getHotbarTypeFromItem(item: ItemStack?): String? {
        if (item == null) return null
        val meta = item.itemMeta ?: return null
        val typeKey = NamespacedKey(plugin, "hotbarType")
        return meta.persistentDataContainer.get(typeKey, PersistentDataType.STRING)
    }

    /**
     * Gets the hotbar slot from an item's NBT data
     */
    fun getHotbarSlotFromItem(item: ItemStack?): Int? {
        if (item == null) return null
        val meta = item.itemMeta ?: return null
        val slotKey = NamespacedKey(plugin, "hotbarSlot")
        return meta.persistentDataContainer.get(slotKey, PersistentDataType.INTEGER)
    }

    /**
     * Gives a player the appropriate hotbar items based on their current state
     * This method coordinates with HotbarUtils to avoid conflicts
     */
    fun givePlayerItems(player: Player) {
        // Get current type through HotbarUtils to ensure consistency
        val type = HotbarUtils.getCurrentHotbarType(player, plugin)
        val items = getCustomItemsForType(type)

        // Clear inventory first
        player.inventory.clear()

        // Set items in their designated slots
        for (custom in items) {
            custom.item?.let { item ->
                // Ensure slot is within valid range
                if (custom.slot in 0..8) {
                    player.inventory.setItem(custom.slot, item.clone())
                }
            }
        }

        // Update inventory
        player.updateInventory()
    }

    /**
     * Handles interaction with a locked hotbar item
     */
    fun handleItemInteraction(player: Player, item: ItemStack) {
        val type = getCurrentType(player)
        val items = getCustomItemsForType(type)

        val matchingItem = items.find { custom ->
            custom.item != null && isSameItem(item, custom.item)
        }

        if (matchingItem != null) {
            when {
                // Speed GUI command
                matchingItem.command != null && matchingItem.command.contains("%speed%") -> {
                    if (SpectatorUtils.isSpectator(player, plugin)) {
                        SpeedGui.open(player)
                    } else {
                        player.sendMessage(MSG.chatColors("&cYou must be spectating to use the speed GUI!"))
                    }
                }

                // Regular command execution
                matchingItem.command != null -> {
                    val cmd = matchingItem.command.removePrefix("/")
                    player.performCommand(cmd)
                }

                // Direct speed setting (for spectators)
                matchingItem.speed != null && SpectatorUtils.isSpectator(player, plugin) -> {
                    val speedLevel = (matchingItem.speed * 10).toInt().coerceIn(1, 7)
                    org.Kloppie74.locoPillars.utils.spectator.SpectatorSpeedManager.setPlayerSpeed(player, speedLevel)
                }

                else -> {
                    // Default action - could be extended
                    player.sendMessage(MSG.chatColors("&7Item activated!"))
                }
            }
        }
    }

    /**
     * Gets all items for a specific type and validates them
     */
    fun validateItemsForType(type: String): List<String> {
        val errors = mutableListOf<String>()
        val items = getCustomItemsForType(type)

        if (items.isEmpty()) {
            errors.add("No items configured for type '$type'")
            return errors
        }

        items.forEachIndexed { index, item ->
            if (item.item == null) {
                errors.add("Item $index has null ItemStack")
            }
            if (item.slot < 0 || item.slot > 8) {
                errors.add("Item $index has invalid slot: ${item.slot}")
            }
            if (item.command == null && item.speed == null) {
                errors.add("Item $index has no command or speed action")
            }
        }

        return errors
    }

    /**
     * Reloads item configurations
     */
    fun reloadItems() {
        loadItemConfigurations()
        SpeedGui.reload(plugin)
        plugin.logger.info("[HotbarItemManager] Items reloaded successfully")
    }

    /**
     * Gets a summary of loaded items
     */
    fun getItemSummary(): Map<String, Int> {
        return ITEM_CACHE.mapValues { it.value.size }
    }

    /**
     * Checks if a specific type has items configured
     */
    fun hasItemsForType(type: String): Boolean {
        return getCustomItemsForType(type).isNotEmpty()
    }

    /**
     * Gets all configured types
     */
    fun getConfiguredTypes(): Set<String> {
        return ITEM_CACHE.keys.toSet()
    }

    /**
     * Creates a test item for debugging
     */
    fun createTestItem(type: String, slot: Int): ItemStack? {
        return createLockedHotbarItem(
            Material.STICK,
            "&eTest Item &7($type)",
            listOf("&7Test item for type: &f$type", "&7Slot: &f$slot"),
            type,
            slot
        )
    }

    /**
     * Debug method to check item consistency
     */
    fun debugItemCheck(player: Player): List<String> {
        val issues = mutableListOf<String>()
        val currentType = getCurrentType(player)
        val expectedItems = getCustomItemsForType(currentType)

        issues.add("Current type: $currentType")
        issues.add("Expected items: ${expectedItems.size}")

        for (i in 0..8) {
            val actualItem = player.inventory.getItem(i)
            val expectedItem = expectedItems.find { it.slot == i }

            if (expectedItem != null) {
                if (actualItem == null) {
                    issues.add("Slot $i: Missing expected item")
                } else if (!isSameItem(actualItem, expectedItem.item)) {
                    issues.add("Slot $i: Item mismatch")
                }
            } else if (actualItem != null && isLockedHotbarItem(actualItem)) {
                issues.add("Slot $i: Unexpected locked item")
            }
        }

        return issues
    }
}