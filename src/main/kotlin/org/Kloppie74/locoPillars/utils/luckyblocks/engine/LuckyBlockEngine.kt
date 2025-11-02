package org.Kloppie74.locoPillars.utils.luckyblocks.engine

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import org.Kloppie74.locoPillars.utils.MSG
import java.io.File
import java.util.logging.Logger
import kotlin.random.Random

data class LuckyBlockConfig(
    val id: String,
    val displayName: String,
    val description: String,
    val rarity: String,
    val icon: IconConfig,
    val blockType: String,
    val effects: List<EffectConfig>,
    val conditions: List<ConditionConfig>,
    val filters: List<FilterConfig>,
    val triggers: List<TriggerConfig>
)

data class IconConfig(
    val material: String,
    val customModelData: Int?
)

data class EffectConfig(
    val type: String,
    val properties: Map<String, Any?>
)

data class ConditionConfig(
    val type: String,
    val value: String
)

data class FilterConfig(
    val type: String,
    val value: String
)

data class TriggerConfig(
    val event: String,
    val itemName: String? = null,
    val effect: EffectConfig? = null
)

class LuckyBlockEngine(
    private val plugin: org.bukkit.plugin.Plugin,
    private val configManager: ConfigManager
) : Listener {

    private val logger: Logger = plugin.logger
    private val luckyBlockConfigs = mutableMapOf<String, LuckyBlockConfig>()
    private val effectRegistry = EffectRegistry()
    private val conditionRegistry = ConditionRegistry()
    private val filterRegistry = FilterRegistry()
    private val triggerRegistry = TriggerRegistry(this)

    // NBT Keys
    private val LUCKY_BLOCK_ID_KEY = NamespacedKey(plugin, "lucky_block_id")
    private val LUCKY_BLOCK_KEY = NamespacedKey(plugin, "lucky_block")
    private val CUSTOM_MODEL_DATA_KEY = NamespacedKey(plugin, "custom_model_data")

    init {
        loadAllLuckyBlocks()
        registerListeners()
    }

    fun initialize() {
        logger.info("[LuckyBlockEngine] Initializing LuckyBlock Engine...")
        logger.info("[LuckyBlockEngine] Loaded ${luckyBlockConfigs.size} LuckyBlock configurations")
        logger.info("[LuckyBlockEngine] Registered ${effectRegistry.getRegisteredEffects().size} effects")
        logger.info("[LuckyBlockEngine] Registered ${conditionRegistry.getRegisteredConditions().size} conditions")
        logger.info("[LuckyBlockEngine] Registered ${filterRegistry.getRegisteredFilters().size} filters")
        logger.info("[LuckyBlockEngine] Engine initialized successfully!")
    }

    private fun registerListeners() {
        Bukkit.getPluginManager().registerEvents(this, plugin)
        Bukkit.getPluginManager().registerEvents(triggerRegistry, plugin)
    }

    private fun loadAllLuckyBlocks() {
        val luckyBlocksFolder = File(plugin.dataFolder, "Luckyblocks")
        if (!luckyBlocksFolder.exists()) {
            luckyBlocksFolder.mkdirs()
            createExampleLuckyBlocks(luckyBlocksFolder)
        }

        val yamlFiles = luckyBlocksFolder.listFiles { _, name -> name.endsWith(".yml") }
        if (yamlFiles != null) {
            for (file in yamlFiles) {
                try {
                    loadLuckyBlockConfig(file)
                } catch (e: Exception) {
                    logger.severe("[LuckyBlockEngine] Failed to load ${file.name}: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun loadLuckyBlockConfig(file: File) {
        val yaml = YamlConfiguration.loadConfiguration(file)

        val id = yaml.getString("id") ?: throw IllegalArgumentException("Missing 'id' field")
        val displayName = yaml.getString("display_name") ?: id
        val description = yaml.getString("description") ?: ""
        val rarity = yaml.getString("rarity") ?: "COMMON"
        val blockType = yaml.getString("block_type") ?: "LUCKY_BLOCK"

        // Load icon
        val iconSection = yaml.getConfigurationSection("icon")
        val icon = if (iconSection != null) {
            IconConfig(
                material = iconSection.getString("material") ?: "PLAYER_HEAD",
                customModelData = if (iconSection.contains("custom_model_data"))
                    iconSection.getInt("custom_model_data") else null
            )
        } else {
            IconConfig("PLAYER_HEAD", null)
        }

        val effects = mutableListOf<EffectConfig>()
        val effectsList = yaml.getMapList("effects")
        for (effectMap in effectsList) {
            val type = effectMap["type"]?.toString() ?: continue
            val properties = effectMap.filterKeys { it != "type" }
                .mapKeys { it.key.toString() } // <---- fix hier!
            effects.add(EffectConfig(type, properties))
        }

        // Load conditions
        val conditions = mutableListOf<ConditionConfig>()
        val conditionsList = yaml.getMapList("conditions")
        for (conditionMap in conditionsList) {
            for ((key, value) in conditionMap) {
                conditions.add(ConditionConfig(key.toString(), value.toString()))
            }
        }

        // Load filters
        val filters = mutableListOf<FilterConfig>()
        val filtersList = yaml.getMapList("filters")
        for (filterMap in filtersList) {
            for ((key, value) in filterMap) {
                filters.add(FilterConfig(key.toString(), value.toString()))
            }
        }

        // Load triggers
        val triggers = mutableListOf<TriggerConfig>()
        val triggersList = yaml.getMapList("triggers")
        for (triggerMap in triggersList) {
            val event = triggerMap["event"]?.toString() ?: continue
            val itemName = triggerMap["item_name"]?.toString()
            val effectMap = triggerMap["effect"] as? Map<String, Any>
            val effect = if (effectMap != null) {
                val effectType = effectMap["type"]?.toString() ?: continue
                val effectProperties = effectMap.filterKeys { it != "type" }
                EffectConfig(effectType, effectProperties)
            } else null

            triggers.add(TriggerConfig(event, itemName, effect))
        }

        val config = LuckyBlockConfig(id, displayName, description, rarity, icon, blockType, effects, conditions, filters, triggers)
        luckyBlockConfigs[id] = config

        logger.info("[LuckyBlockEngine] Loaded LuckyBlock: $id (${effects.size} effects, ${conditions.size} conditions, ${filters.size} filters)")
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val player = event.player

        // Check if this is a placed LuckyBlock
        if (org.Kloppie74.locoPillars.utils.luckyblocks.LuckyBlocksUtil.isPlacedLuckyBlock(block.location)) {
            event.isCancelled = true

            // Remove the lucky block from cache and armor stand
            org.Kloppie74.locoPillars.utils.luckyblocks.LuckyBlocksUtil.removeLuckyBlock(block.location)
            org.Kloppie74.locoPillars.utils.luckyblocks.LuckyBlocksUtil.removeLuckyBlockArmorStand(block.location)

            // Remove the block
            block.type = org.bukkit.Material.AIR

            // Execute random LuckyBlock effect
            executeLuckyBlockBreak(player, block.location)
        }
    }

    private fun executeLuckyBlockBreak(player: Player, location: Location) {
        // Get a random LuckyBlock config based on rarity
        val selectedConfig = selectRandomLuckyBlock() ?: return

        // Check filters first
        if (!checkFilters(player, selectedConfig.filters)) {
            player.sendMessage(MSG.chatColors("&7The LuckyBlock fizzles out..."))
            return
        }

        // Check conditions
        if (!checkConditions(player, selectedConfig.conditions)) {
            player.sendMessage(MSG.chatColors("&7The conditions weren't right..."))
            return
        }

        // Execute effects
        executeEffects(player, location, selectedConfig.effects)

        // Send break message
        player.sendMessage(MSG.chatColors("&eYou broke a ${selectedConfig.displayName} LuckyBlock!"))
    }

    private fun selectRandomLuckyBlock(): LuckyBlockConfig? {
        if (luckyBlockConfigs.isEmpty()) return null

        // Group by rarity and select based on rarity chances
        val rarityGroups = luckyBlockConfigs.values.groupBy { it.rarity }
        val rarityChances = configManager.luckyBlockRarityChances

        val totalWeight = rarityChances.values.sum()
        if (totalWeight <= 0) return luckyBlockConfigs.values.random()

        val roll = Random.nextInt(totalWeight)
        var accumulator = 0

        for ((rarity, chance) in rarityChances) {
            accumulator += chance
            if (roll < accumulator) {
                val blocksOfRarity = rarityGroups[rarity.uppercase()] ?: continue
                return blocksOfRarity.random()
            }
        }

        return luckyBlockConfigs.values.random()
    }

    private fun checkFilters(player: Player, filters: List<FilterConfig>): Boolean {
        return filters.all { filter ->
            filterRegistry.checkFilter(player, filter.type, filter.value)
        }
    }

    private fun checkConditions(player: Player, conditions: List<ConditionConfig>): Boolean {
        return conditions.all { condition ->
            conditionRegistry.checkCondition(player, condition.type, condition.value)
        }
    }

    private fun executeEffects(player: Player, location: Location, effects: List<EffectConfig>) {
        for (effect in effects) {
            try {
                effectRegistry.executeEffect(player, location, effect.type, effect.properties)
            } catch (e: Exception) {
                logger.warning("[LuckyBlockEngine] Failed to execute effect ${effect.type}: ${e.message}")
            }
        }
    }

    fun executeTrigger(player: Player, trigger: TriggerConfig, item: ItemStack? = null) {
        // Check if item matches trigger requirements
        if (trigger.itemName != null && item != null) {
            val itemName = item.itemMeta?.displayName
            if (itemName == null || !itemName.contains(trigger.itemName, ignoreCase = true)) {
                return
            }
        }

        // Execute trigger effect if present
        trigger.effect?.let { effect ->
            executeEffects(player, player.location, listOf(effect))
        }
    }

    fun getLuckyBlockItem(id: String): ItemStack? {
        val config = luckyBlockConfigs[id] ?: return null
        return createLuckyBlockItem(config)
    }

    fun getRandomLuckyBlockItem(): ItemStack? {
        val config = selectRandomLuckyBlock() ?: return null
        return createLuckyBlockItem(config)
    }

    private fun createLuckyBlockItem(config: LuckyBlockConfig): ItemStack {
        val material = try {
            org.bukkit.Material.valueOf(config.icon.material.uppercase())
        } catch (e: Exception) {
            org.bukkit.Material.PLAYER_HEAD
        }

        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        // Set display name and lore
        meta.setDisplayName(MSG.chatColors("&e${config.displayName}"))
        val lore = mutableListOf<String>()
        if (config.description.isNotBlank()) {
            lore.add(MSG.chatColors("&7${config.description}"))
        }
        lore.add(MSG.chatColors("&8Rarity: &b${config.rarity}"))
        meta.lore = lore

        // Set NBT data
        meta.persistentDataContainer.set(LUCKY_BLOCK_KEY, PersistentDataType.STRING, "true")
        meta.persistentDataContainer.set(LUCKY_BLOCK_ID_KEY, PersistentDataType.STRING, config.id)

        // Set custom model data if present
        config.icon.customModelData?.let { customModelData ->
            meta.setCustomModelData(customModelData)
            meta.persistentDataContainer.set(CUSTOM_MODEL_DATA_KEY, PersistentDataType.INTEGER, customModelData)
        }

        item.itemMeta = meta
        return item
    }

    fun isLuckyBlockItem(item: ItemStack?): Boolean {
        if (item == null || item.itemMeta == null) return false
        val meta = item.itemMeta!!
        return meta.persistentDataContainer.has(LUCKY_BLOCK_KEY, PersistentDataType.STRING)
    }

    fun getLuckyBlockId(item: ItemStack): String? {
        val meta = item.itemMeta ?: return null
        return meta.persistentDataContainer.get(LUCKY_BLOCK_ID_KEY, PersistentDataType.STRING)
    }

    private fun createExampleLuckyBlocks(folder: File) {
        // Create firework example
        val fireworkFile = File(folder, "firework_frenzy.yml")
        fireworkFile.writeText("""
id: firework_frenzy
display_name: "Firework Frenzy"
description: "Shoots fireworks when broken!"
rarity: "COMMON"
icon:
  material: "FIREWORK_ROCKET"
  custom_model_data: 0
block_type: "LUCKY_BLOCK"
effects:
  - type: "SUMMON_FIREWORKS"
    amount: 12
    colors: ["RED", "BLUE", "GREEN"]
    power: 2
    message: "&dFireworks everywhere!"
conditions:
  - player_y: ">65"
filters:
  - time: "NIGHT"
        """.trimIndent())

        logger.info("[LuckyBlockEngine] Created example LuckyBlock: firework_frenzy.yml")
    }

    // Getters for registries (for external use)
    fun getEffectRegistry() = effectRegistry
    fun getConditionRegistry() = conditionRegistry
    fun getFilterRegistry() = filterRegistry
    fun getTriggerRegistry() = triggerRegistry

    fun getAllConfigs(): Map<String, LuckyBlockConfig> = luckyBlockConfigs.toMap()

    fun reloadConfigs() {
        luckyBlockConfigs.clear()
        loadAllLuckyBlocks()
        logger.info("[LuckyBlockEngine] Reloaded ${luckyBlockConfigs.size} LuckyBlock configurations")
    }
}