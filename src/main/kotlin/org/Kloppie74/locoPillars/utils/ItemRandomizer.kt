package org.Kloppie74.locoPillars.utils

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionType
import org.bukkit.configuration.file.FileConfiguration
import java.util.concurrent.ThreadLocalRandom

class ItemRandomizer(private val arenaConfig: FileConfiguration) {

    // Cache for valid materials (initialized once)
    private val validItemMaterials: List<Material> by lazy {
        Material.values().filter { it.isItem && !it.isAir }.toList()
    }

    // Cache for all available items and commands
    private val allAvailableItems: List<ItemOrCommand> by lazy {
        loadAllPossibleItems()
    }

    /**
     * Returns a random item
     */
    fun getRandomItem(): ItemStack {
        if (allAvailableItems.isEmpty()) {
            // Fallback to stone if no items available
            return ItemStack(Material.STONE)
        }

        val randomItem = allAvailableItems[ThreadLocalRandom.current().nextInt(allAvailableItems.size)]

        return when {
            randomItem.isCommand -> {
                // For commands return a command block as placeholder
                ItemStack(Material.COMMAND_BLOCK)
            }
            randomItem.isCustomItem -> {
                randomItem.customItem!!.clone()
            }
            else -> {
                ItemStack(randomItem.material!!, 1)
            }
        }
    }

    /**
     * Execute a random action for a player (give item or execute command)
     */
    fun executeRandomAction(playerName: String) {
        if (allAvailableItems.isEmpty()) {
            return
        }

        val randomItem = allAvailableItems[ThreadLocalRandom.current().nextInt(allAvailableItems.size)]

        when {
            randomItem.isCommand -> {
                // Replace %player% placeholder and execute command
                val command = randomItem.command!!.replace("%player%", playerName)
                val finalCommand = if (command.startsWith("/")) command.substring(1) else command
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand)
            }
            else -> {
                // Give item to player
                val player = Bukkit.getPlayer(playerName)
                if (player != null && player.isOnline) {
                    val item = if (randomItem.isCustomItem) {
                        randomItem.customItem!!.clone()
                    } else {
                        ItemStack(randomItem.material!!, 1)
                    }
                    /**
                     * addItemSafely(player, item)
                     */

                }
            }
        }
    }

    /**
     * Load all possible items, potions and commands from config
     */
    private fun loadAllPossibleItems(): List<ItemOrCommand> {
        val items = mutableListOf<ItemOrCommand>()

        val randomItemsType = arenaConfig.getString("Settings.RandomItems.Type", "Blacklisted")?.uppercase()
        val randomItemsList = arenaConfig.getStringList("Settings.RandomItems.List")

        when (randomItemsType) {
            "WHITELISTED" -> {
                // Only include items/commands from the whitelist
                randomItemsList.forEach { itemString ->
                    when {
                        itemString.startsWith("/") -> {
                            // It's a command
                            items.add(ItemOrCommand(command = itemString))
                        }
                        else -> {
                            // It's a material or potion
                            val material = Material.getMaterial(itemString.uppercase())
                            if (material != null && material.isItem && !material.isAir) {
                                items.add(ItemOrCommand(material = material))
                            }
                        }
                    }
                }

                // Add potions if they're not explicitly blacklisted
                if (randomItemsList.none { it.uppercase().contains("POTION") }) {
                    addAllPotions(items)
                }
            }
            "BLACKLISTED" -> {
                // Include all materials except blacklisted ones
                val blacklistedMaterials = randomItemsList
                    .filter { !it.startsWith("/") }
                    .map { it.uppercase() }
                    .toSet()

                validItemMaterials.forEach { material ->
                    if (material.name !in blacklistedMaterials) {
                        items.add(ItemOrCommand(material = material))
                    }
                }

                // Add potions if not blacklisted
                if (!blacklistedMaterials.any { it.contains("POTION") }) {
                    addAllPotions(items)
                }

                // Commands are only supported in WHITELISTED mode
            }
            else -> {
                // Default: include all valid items and potions
                validItemMaterials.forEach { material ->
                    items.add(ItemOrCommand(material = material))
                }
                addAllPotions(items)
            }
        }

        return items
    }

    /**
     * Add all usable potions to the items list
     */
    private fun addAllPotions(items: MutableList<ItemOrCommand>) {
        val potionMaterials = listOf(Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION)

        for (potionMaterial in potionMaterials) {
            for (potionType in PotionType.values()) {
                try {
                    val potion = ItemStack(potionMaterial, 1)
                    val meta = potion.itemMeta as? PotionMeta
                    if (meta != null) {
                        // Normal potion
                        meta.basePotionType = potionType
                        potion.itemMeta = meta
                        items.add(ItemOrCommand(customItem = potion.clone()))

                        // Upgraded potion (if possible)
                        if (potionType.isUpgradeable) {
                            val upgradedPotion = ItemStack(potionMaterial, 1)
                            val upgradedMeta = upgradedPotion.itemMeta as? PotionMeta
                            if (upgradedMeta != null) {
                                upgradedMeta.basePotionType = potionType
                                // Set upgraded level (usually level 2)
                                upgradedPotion.itemMeta = upgradedMeta
                                items.add(ItemOrCommand(customItem = upgradedPotion.clone()))
                            }
                        }

                        // Extended potion (if possible)
                        if (potionType.isExtendable) {
                            val extendedPotion = ItemStack(potionMaterial, 1)
                            val extendedMeta = extendedPotion.itemMeta as? PotionMeta
                            if (extendedMeta != null) {
                                extendedMeta.basePotionType = potionType
                                // Extended duration is handled by the PotionType itself
                                extendedPotion.itemMeta = extendedMeta
                                items.add(ItemOrCommand(customItem = extendedPotion.clone()))
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Skip any invalid potion types
                }
            }
        }
    }

    /**
     * Helper class to store a Material, ItemStack or command
     */
    private data class ItemOrCommand(
        val material: Material? = null,
        val customItem: ItemStack? = null,
        val command: String? = null
    ) {
        val isCommand: Boolean = command != null
        val isCustomItem: Boolean = customItem != null
    }

    /**
     * Debug function to show the number of available items
     */
    fun getItemCount(): Int = allAvailableItems.size

    /**
     * Debug function to show the distribution of items
     */
    fun getItemDistribution(): Map<String, Int> {
        val distribution = mutableMapOf<String, Int>()

        allAvailableItems.forEach { item ->
            when {
                item.isCommand -> {
                    distribution["Commands"] = distribution.getOrDefault("Commands", 0) + 1
                }
                item.isCustomItem -> {
                    val type = item.customItem!!.type.name
                    if (type.contains("POTION")) {
                        distribution["Potions"] = distribution.getOrDefault("Potions", 0) + 1
                    } else {
                        distribution["Custom Items"] = distribution.getOrDefault("Custom Items", 0) + 1
                    }
                }
                else -> {
                    distribution["Materials"] = distribution.getOrDefault("Materials", 0) + 1
                }
            }
        }

        return distribution
    }

    /**
     * Get a list of all available commands
     */
    fun getAllCommands(): List<String> {
        return allAvailableItems.filter { it.isCommand }.mapNotNull { it.command }
    }

    /**
     * Check if a string is a command (starts with /)
     */
    private fun String.isCommand(): Boolean = this.startsWith("/")
}