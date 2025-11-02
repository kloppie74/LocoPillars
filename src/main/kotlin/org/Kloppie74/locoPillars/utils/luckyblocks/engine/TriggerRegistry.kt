package org.Kloppie74.locoPillars.utils.luckyblocks.engine

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.*
import org.bukkit.inventory.ItemStack
import org.bukkit.NamespacedKey
import org.bukkit.persistence.PersistentDataType
import org.Kloppie74.locoPillars.utils.MSG

class TriggerRegistry(private val engine: LuckyBlockEngine) : Listener {

    private val CUSTOM_ITEM_KEY = NamespacedKey.minecraft("custom_item_id")
    private val playerMovementTracker = mutableMapOf<Player, org.bukkit.Location>()

    // Track all triggers from loaded configs
    private fun getTriggerConfigs(): List<Pair<LuckyBlockConfig, TriggerConfig>> {
        return engine.getAllConfigs().values.flatMap { config ->
            config.triggers.map { trigger -> config to trigger }
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return

        val player = event.player
        val item = event.item ?: return

        // Check for custom items with triggers
        handleItemTrigger(player, item, "ITEM_RIGHT_CLICK")
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val from = event.from
        val to = event.to ?: return

        // Only trigger if player actually moved to a different block
        if (from.blockX != to.blockX || from.blockY != to.blockY || from.blockZ != to.blockZ) {
            val lastLocation = playerMovementTracker[player]
            // FIX: Only measure distance if worlds are the same to avoid IllegalArgumentException
            if (lastLocation == null || lastLocation.world == to.world && lastLocation.distance(to) > 1.0) {
                playerMovementTracker[player] = to.clone()
                handleMovementTrigger(player, to)
            }
        }
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.entity !is Player) return
        val player = event.entity as Player

        handleDamageTrigger(player, "ENTITY_DAMAGE", null)
    }

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        // Player deals damage
        if (event.damager is Player) {
            val player = event.damager as Player
            val heldItem = player.inventory.itemInMainHand
            handleDamageTrigger(player, "ENTITY_DAMAGE", heldItem)
        }

        // Player takes damage
        if (event.entity is Player) {
            val player = event.entity as Player
            handleDamageTrigger(player, "ENTITY_DAMAGE", null)
        }
    }

    @EventHandler
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        val item = event.itemDrop.itemStack

        handleItemTrigger(player, item, "ITEM_DROP")
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val item = event.itemInHand

        handleItemTrigger(player, item, "BLOCK_PLACE")
    }

    @EventHandler
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        val item = player.inventory.itemInMainHand

        handleItemTrigger(player, item, "INTERACT")
    }

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        playerMovementTracker[event.player] = event.player.location.clone()
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        playerMovementTracker.remove(event.player)
    }

    private fun handleItemTrigger(player: Player, item: ItemStack, eventType: String) {
        // Check if this item has custom triggers
        val triggerConfigs = getTriggerConfigs().filter { (_, trigger) ->
            trigger.event.equals(eventType, ignoreCase = true)
        }

        for ((config, trigger) in triggerConfigs) {
            if (shouldTriggerForItem(item, trigger)) {
                // Check filters and conditions before executing
                if (engine.getFilterRegistry().let { filterRegistry ->
                        config.filters.all { filter ->
                            filterRegistry.checkFilter(player, filter.type, filter.value)
                        }
                    }) {
                    if (engine.getConditionRegistry().let { conditionRegistry ->
                            config.conditions.all { condition ->
                                conditionRegistry.checkCondition(player, condition.type, condition.value)
                            }
                        }) {
                        engine.executeTrigger(player, trigger, item)

                        // Send trigger message if configured
                        player.sendMessage(MSG.chatColors("&eTrigger activated: ${config.displayName}!"))
                    }
                }
            }
        }
    }

    private fun handleMovementTrigger(player: Player, location: org.bukkit.Location) {
        val triggerConfigs = getTriggerConfigs().filter { (_, trigger) ->
            trigger.event.equals("PLAYER_MOVE", ignoreCase = true)
        }

        for ((config, trigger) in triggerConfigs) {
            // Check if player has the required item (if specified)
            if (trigger.itemName != null) {
                val hasRequiredItem = player.inventory.contents.any { item ->
                    item != null && itemMatchesName(item, trigger.itemName)
                }
                if (!hasRequiredItem) continue
            }

            // Check filters and conditions
            if (engine.getFilterRegistry().let { filterRegistry ->
                    config.filters.all { filter ->
                        filterRegistry.checkFilter(player, filter.type, filter.value)
                    }
                }) {
                if (engine.getConditionRegistry().let { conditionRegistry ->
                        config.conditions.all { condition ->
                            conditionRegistry.checkCondition(player, condition.type, condition.value)
                        }
                    }) {
                    engine.executeTrigger(player, trigger, null)
                }
            }
        }
    }

    private fun handleDamageTrigger(player: Player, eventType: String, item: ItemStack?) {
        val triggerConfigs = getTriggerConfigs().filter { (_, trigger) ->
            trigger.event.equals(eventType, ignoreCase = true)
        }

        for ((config, trigger) in triggerConfigs) {
            if (trigger.itemName != null && item != null) {
                if (!itemMatchesName(item, trigger.itemName)) continue
            }

            // Check filters and conditions
            if (engine.getFilterRegistry().let { filterRegistry ->
                    config.filters.all { filter ->
                        filterRegistry.checkFilter(player, filter.type, filter.value)
                    }
                }) {
                if (engine.getConditionRegistry().let { conditionRegistry ->
                        config.conditions.all { condition ->
                            conditionRegistry.checkCondition(player, condition.type, condition.value)
                        }
                    }) {
                    engine.executeTrigger(player, trigger, item)
                }
            }
        }
    }

    private fun shouldTriggerForItem(item: ItemStack, trigger: TriggerConfig): Boolean {
        // If no specific item name is required, any custom item can trigger
        if (trigger.itemName == null) {
            return hasCustomData(item)
        }

        // Check if item name matches
        return itemMatchesName(item, trigger.itemName)
    }

    private fun itemMatchesName(item: ItemStack, requiredName: String): Boolean {
        val itemMeta = item.itemMeta ?: return false
        val itemName = itemMeta.displayName

        if (itemName.isNullOrBlank()) return false

        // Strip color codes for comparison
        val cleanItemName = itemName.replace(Regex("§[0-9a-fk-or]"), "")
        val cleanRequiredName = requiredName.replace(Regex("&[0-9a-fk-or]"), "")

        return cleanItemName.contains(cleanRequiredName, ignoreCase = true) ||
                cleanItemName.equals(cleanRequiredName, ignoreCase = true)
    }

    private fun hasCustomData(item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false

        // Check if it's a lucky block item
        if (engine.isLuckyBlockItem(item)) return true

        // Check if it has custom item data
        return meta.persistentDataContainer.has(CUSTOM_ITEM_KEY, PersistentDataType.STRING) ||
                meta.hasCustomModelData() ||
                (meta.hasDisplayName() && meta.displayName.contains("§")) ||
                (meta.hasLore() && meta.lore?.any { it.contains("§") } == true)
    }
}

// ===== TRIGGER TYPES =====

enum class TriggerType(val eventName: String) {
    BLOCK_BREAK("BLOCK_BREAK"),
    ITEM_RIGHT_CLICK("ITEM_RIGHT_CLICK"),
    PLAYER_MOVE("PLAYER_MOVE"),
    ENTITY_DAMAGE("ENTITY_DAMAGE"),
    ITEM_DROP("ITEM_DROP"),
    INTERACT("INTERACT"),
    BLOCK_PLACE("BLOCK_PLACE"),
    PLAYER_JOIN("PLAYER_JOIN"),
    PLAYER_DEATH("PLAYER_DEATH"),
    ITEM_CONSUME("ITEM_CONSUME"),
    ENCHANT_ITEM("ENCHANT_ITEM"),
    CRAFT_ITEM("CRAFT_ITEM"),
    FISH("FISH"),
    SHEAR("SHEAR"),
    BUCKET_FILL("BUCKET_FILL"),
    BUCKET_EMPTY("BUCKET_EMPTY"),
    ITEM_BREAK("ITEM_BREAK"),
    LEVEL_CHANGE("LEVEL_CHANGE"),
    TOGGLE_FLIGHT("TOGGLE_FLIGHT"),
    TOGGLE_SNEAK("TOGGLE_SNEAK"),
    TOGGLE_SPRINT("TOGGLE_SPRINT"),
    BED_ENTER("BED_ENTER"),
    BED_LEAVE("BED_LEAVE"),
    PORTAL("PORTAL"),
    KICK("KICK"),
    QUIT("QUIT"),
    RESPAWN("RESPAWN"),
    WORLD_CHANGE("WORLD_CHANGE"),
    GAMEMODE_CHANGE("GAMEMODE_CHANGE");

    companion object {
        fun fromString(name: String): TriggerType? {
            return values().find { it.eventName.equals(name, ignoreCase = true) }
        }
    }
}

// ===== TRIGGER EXECUTION CONTEXT =====

data class TriggerContext(
    val player: Player,
    val item: ItemStack?,
    val location: org.bukkit.Location,
    val eventType: String,
    val additionalData: Map<String, Any> = emptyMap()
)

// ===== ADVANCED TRIGGER HANDLERS =====

class AdvancedTriggerHandler {
    private val cooldowns = mutableMapOf<String, Long>()
    private val usageCount = mutableMapOf<String, Int>()

    fun canTrigger(player: Player, triggerId: String, cooldownMs: Long = 0): Boolean {
        val key = "${player.uniqueId}_$triggerId"
        val now = System.currentTimeMillis()
        val lastUse = cooldowns[key] ?: 0

        return now - lastUse >= cooldownMs
    }

    fun recordTriggerUse(player: Player, triggerId: String) {
        val key = "${player.uniqueId}_$triggerId"
        cooldowns[key] = System.currentTimeMillis()
        usageCount[key] = (usageCount[key] ?: 0) + 1
    }

    fun getTriggerUsageCount(player: Player, triggerId: String): Int {
        val key = "${player.uniqueId}_$triggerId"
        return usageCount[key] ?: 0
    }

    fun resetPlayerData(player: Player) {
        val playerKey = player.uniqueId.toString()
        cooldowns.keys.removeIf { it.startsWith(playerKey) }
        usageCount.keys.removeIf { it.startsWith(playerKey) }
    }
}

// ===== CUSTOM TRIGGER BUILDERS =====

class TriggerBuilder {
    private var event: String = "ITEM_RIGHT_CLICK"
    private var itemName: String? = null
    private var effect: EffectConfig? = null
    private var cooldown: Long = 0
    private var maxUses: Int = -1 // -1 = unlimited

    fun event(eventName: String): TriggerBuilder {
        this.event = eventName
        return this
    }

    fun itemName(name: String): TriggerBuilder {
        this.itemName = name
        return this
    }

    fun effect(effectType: String, properties: Map<String, Any>): TriggerBuilder {
        this.effect = EffectConfig(effectType, properties)
        return this
    }

    fun cooldown(ms: Long): TriggerBuilder {
        this.cooldown = ms
        return this
    }

    fun maxUses(uses: Int): TriggerBuilder {
        this.maxUses = uses
        return this
    }

    fun build(): TriggerConfig {
        return TriggerConfig(event, itemName, effect)
    }
}

// ===== TRIGGER UTILITIES =====

object TriggerUtils {

    fun createCustomItem(
        material: org.bukkit.Material,
        name: String,
        lore: List<String> = emptyList(),
        customModelData: Int? = null,
        triggers: List<TriggerConfig> = emptyList()
    ): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta ?: return item

        meta.setDisplayName(MSG.chatColors(name))
        if (lore.isNotEmpty()) {
            meta.lore = lore.map { MSG.chatColors(it) }
        }

        customModelData?.let { meta.setCustomModelData(it) }

        // Store trigger data
        if (triggers.isNotEmpty()) {
            val triggerData = triggers.joinToString(";") { trigger ->
                "${trigger.event}:${trigger.itemName ?: ""}:${trigger.effect?.type ?: ""}"
            }
            meta.persistentDataContainer.set(
                NamespacedKey.minecraft("custom_triggers"),
                PersistentDataType.STRING,
                triggerData
            )
        }

        item.itemMeta = meta
        return item
    }

    fun getTriggerData(item: ItemStack): List<TriggerConfig> {
        val meta = item.itemMeta ?: return emptyList()
        val triggerData = meta.persistentDataContainer.get(
            NamespacedKey.minecraft("custom_triggers"),
            PersistentDataType.STRING
        ) ?: return emptyList()

        return triggerData.split(";").mapNotNull { triggerString ->
            val parts = triggerString.split(":")
            if (parts.size >= 3) {
                val event = parts[0]
                val itemName = parts[1].takeIf { it.isNotBlank() }
                val effectType = parts[2].takeIf { it.isNotBlank() }

                val effect = effectType?.let { EffectConfig(it, emptyMap()) }
                TriggerConfig(event, itemName, effect)
            } else null
        }
    }

    fun isCustomTriggerItem(item: ItemStack): Boolean {
        val meta = item.itemMeta ?: return false
        return meta.persistentDataContainer.has(
            NamespacedKey.minecraft("custom_triggers"),
            PersistentDataType.STRING
        )
    }
}