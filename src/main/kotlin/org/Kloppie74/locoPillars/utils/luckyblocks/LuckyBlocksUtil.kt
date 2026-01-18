package org.Kloppie74.locoPillars.utils.luckyblocks

import org.bukkit.Material
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import org.Kloppie74.locoPillars.utils.luckyblocks.engine.LuckyBlockEngine
import org.Kloppie74.locoPillars.utils.MSG
import kotlin.random.Random
import java.util.UUID
import java.lang.reflect.Field

data class LuckyBlockTexture(val glass: Material, val texture: String)
data class LuckyBlockRarity(val name: String, val chance: Int)

object LuckyBlocksUtil {

    private val placedLuckyBlocks: MutableSet<Location> = mutableSetOf()
    private var luckyBlockEngine: LuckyBlockEngine? = null

    fun setLuckyBlockEngine(engine: LuckyBlockEngine) {
        luckyBlockEngine = engine
    }

    fun cacheLuckyBlock(loc: Location) {
        placedLuckyBlocks.add(loc)
    }

    fun removeLuckyBlock(loc: Location) {
        placedLuckyBlocks.remove(loc)
    }

    fun isPlacedLuckyBlock(loc: Location): Boolean {
        return placedLuckyBlocks.contains(loc)
    }

    fun getPlacedLuckyBlocksCache(): Set<Location> = placedLuckyBlocks

    fun handleTraditionalLuckyBlockBreak(player: Player, location: Location) {
        val engine = luckyBlockEngine ?: run {
            player.sendMessage(MSG.chatColors("&cLuckyBlock engine not initialized!"))
            return
        }

        val allConfigs = engine.getAllConfigs().values.toList()
        if (allConfigs.isEmpty()) {
            player.sendMessage(MSG.chatColors("&cNo LuckyBlock configurations found in Luckyblocks folder!"))
            return
        }

        val selectedConfig = selectRandomConfigByRarity(allConfigs)
        player.sendMessage(MSG.chatColors("&6[DEBUG] Selected: ${selectedConfig.id} (${selectedConfig.rarity})"))

        val triggersNeedingItem = selectedConfig.triggers.filter { trigger ->
            trigger.event.startsWith("ITEM_") ||
                    trigger.event == "RIGHT_CLICK" ||
                    trigger.event == "LEFT_CLICK" ||
                    trigger.event == "SHIFT_RIGHT_CLICK" ||
                    trigger.event == "SHIFT_LEFT_CLICK"
        }

        if (triggersNeedingItem.isNotEmpty()) {
            val luckyBlockItem = engine.getLuckyBlockItem(selectedConfig.id)
            if (luckyBlockItem != null) {
                player.inventory.addItem(luckyBlockItem)
                player.sendMessage(MSG.chatColors("&eYou found a special ${selectedConfig.displayName} with triggers!"))
                player.sendMessage(MSG.chatColors("&6[DEBUG] Also executing ${selectedConfig.effects.size} effects..."))
                executeConfigEffects(player, location, selectedConfig, engine)
                return
            }
        }

        player.sendMessage(MSG.chatColors("&6[DEBUG] Executing ${selectedConfig.effects.size} effects..."))
        executeConfigEffects(player, location, selectedConfig, engine)
    }

    private fun selectRandomConfigByRarity(configs: List<org.Kloppie74.locoPillars.utils.luckyblocks.engine.LuckyBlockConfig>): org.Kloppie74.locoPillars.utils.luckyblocks.engine.LuckyBlockConfig {
        val rarityGroups = configs.groupBy { it.rarity.uppercase() }

        val rarityChances = luckyBlockEngine?.let { engine ->
            engine.javaClass.getDeclaredField("configManager").let { field ->
                field.isAccessible = true
                val configManager = field.get(engine) as org.Kloppie74.locoPillars.utils.config.ConfigManager
                configManager.luckyBlockRarityChances
            }
        } ?: mapOf(
            "LEGENDARY" to 5,
            "EPIC" to 10,
            "RARE" to 20,
            "UNCOMMON" to 30,
            "COMMON" to 35
        )

        val availableRarities = rarityGroups.keys.intersect(rarityChances.keys)
        val totalWeight = availableRarities.sumOf { rarityChances[it] ?: 0 }

        if (totalWeight <= 0 || availableRarities.isEmpty()) {
            val selected = configs.random()
            println("[LuckyBlocksUtil] No rarity weights found, selected random: ${selected.id} (${selected.rarity})")
            return selected
        }

        val roll = Random.nextInt(totalWeight)
        var accumulator = 0

        val sortedRarities = availableRarities.sortedBy { rarityChances[it] ?: 0 }

        for (rarity in sortedRarities) {
            val chance = rarityChances[rarity] ?: 0
            accumulator += chance

            if (roll < accumulator) {
                val rarityConfigs = rarityGroups[rarity] ?: continue
                if (rarityConfigs.isNotEmpty()) {
                    val selected = rarityConfigs.random()
                    println("[LuckyBlocksUtil] Rolled $roll/$totalWeight, selected: ${selected.id} (${selected.rarity})")
                    return selected
                }
            }
        }

        val selected = configs.random()
        println("[LuckyBlocksUtil] Fallback selection: ${selected.id} (${selected.rarity})")
        return selected
    }

    private fun executeConfigEffects(
        player: Player,
        location: Location,
        config: org.Kloppie74.locoPillars.utils.luckyblocks.engine.LuckyBlockConfig,
        engine: LuckyBlockEngine
    ) {
        player.sendMessage(MSG.chatColors("&6[DEBUG] Checking filters (${config.filters.size})..."))

        val filtersPassed = config.filters.all { filter ->
            val result = engine.getFilterRegistry().checkFilter(player, filter.type, filter.value)
            player.sendMessage(MSG.chatColors("&7Filter ${filter.type}=${filter.value}: ${if (result) "&a✓" else "&c✗"}"))
            result
        }

        if (!filtersPassed) {
            player.sendMessage(MSG.chatColors("&7The LuckyBlock fizzles out... (filters failed)"))
            return
        }

        player.sendMessage(MSG.chatColors("&6[DEBUG] Checking conditions (${config.conditions.size})..."))

        val conditionsPassed = config.conditions.all { condition ->
            val result = engine.getConditionRegistry().checkCondition(player, condition.type, condition.value)
            player.sendMessage(MSG.chatColors("&7Condition ${condition.type}=${condition.value}: ${if (result) "&a✓" else "&c✗"}"))
            result
        }

        if (!conditionsPassed) {
            player.sendMessage(MSG.chatColors("&7The conditions weren't right... (conditions failed)"))
            return
        }

        player.sendMessage(MSG.chatColors("&6[DEBUG] All checks passed! Executing ${config.effects.size} effects..."))

        config.effects.forEachIndexed { index, effect ->
            try {
                player.sendMessage(MSG.chatColors("&7Executing effect ${index + 1}: ${effect.type}"))
                engine.getEffectRegistry().executeEffect(player, location, effect.type, effect.properties)
                player.sendMessage(MSG.chatColors("&a✓ Effect ${effect.type} completed"))
            } catch (e: Exception) {
                player.sendMessage(MSG.chatColors("&c✗ Effect ${effect.type} failed: ${e.message}"))
                e.printStackTrace()
            }
        }

        player.sendMessage(MSG.chatColors("&eYou broke a ${config.displayName} LuckyBlock!"))
    }

    fun getItemTextures(config: ConfigManager): List<LuckyBlockTexture> {
        val itemSection = config.getConfig().getConfigurationSection("Luckyblocks.item")
        val textureStrings = itemSection?.getStringList("textures") ?: emptyList()
        return textureStrings.mapNotNull { line ->
            val parts = line.split(",")
            val glassName = parts.find { it.trim().startsWith("Glass=") }?.substringAfter("Glass=")?.trim()?.uppercase()
            val texture = parts.find { it.trim().startsWith("Texture=") }?.substringAfter("Texture=")?.trim()
            val glassMat = when (glassName) {
                "RED" -> Material.RED_STAINED_GLASS
                "BLUE" -> Material.BLUE_STAINED_GLASS
                "GREEN" -> Material.GREEN_STAINED_GLASS
                "YELLOW" -> Material.YELLOW_STAINED_GLASS
                "PINK" -> Material.PINK_STAINED_GLASS
                else -> Material.GLASS
            }
            if (glassName != null && texture != null) LuckyBlockTexture(glassMat, texture) else null
        }
    }

    fun getBlockTextures(config: ConfigManager): List<LuckyBlockTexture> {
        val blockSection = config.getConfig().getConfigurationSection("Luckyblocks.block-layout")
        val textureStrings = blockSection?.getStringList("textures") ?: emptyList()

        println("[LuckyBlocks] Loading ${textureStrings.size} block textures from config")

        return textureStrings.mapNotNull { line ->
            println("[LuckyBlocks] Parsing texture line: $line")

            val parts = line.split(",")
            val glassName = parts.find { it.trim().startsWith("Glass=") }
                ?.substringAfter("Glass=")?.trim()?.uppercase()
            val texture = parts.find { it.trim().startsWith("Texture=") }
                ?.substringAfter("Texture=")?.trim()

            println("[LuckyBlocks] Parsed - Glass: $glassName, Texture: ${texture?.take(50)}...")

            val glassMat = when (glassName) {
                "RED" -> Material.RED_STAINED_GLASS
                "BLUE" -> Material.BLUE_STAINED_GLASS
                "GREEN" -> Material.GREEN_STAINED_GLASS
                "YELLOW" -> Material.YELLOW_STAINED_GLASS
                "PINK" -> Material.PINK_STAINED_GLASS
                else -> Material.GLASS
            }

            if (glassName != null && texture != null && texture.isNotBlank()) {
                println("[LuckyBlocks] Created texture: $glassName -> ${glassMat.name}")
                LuckyBlockTexture(glassMat, texture)
            } else {
                println("[LuckyBlocks] Skipped invalid texture line")
                null
            }
        }
    }

    fun getRarities(config: ConfigManager): List<LuckyBlockRarity> {
        return config.luckyBlockRarityChances
            .map { (name, chance) -> LuckyBlockRarity(name.uppercase(), chance) }
            .filter { it.chance > 0 }
            .sortedByDescending { it.chance }
    }

    fun getRandomRarity(config: ConfigManager): LuckyBlockRarity? {
        val rarities = getRarities(config)
        val totalWeight = rarities.sumOf { it.chance }
        if (totalWeight <= 0) return rarities.firstOrNull()
        val roll = Random.nextInt(totalWeight)
        var acc = 0
        for (rarity in rarities) {
            acc += rarity.chance
            if (roll < acc) return rarity
        }
        return rarities.lastOrNull()
    }

    fun getRandomRarityName(config: ConfigManager): String {
        return getRandomRarity(config)?.name ?: "COMMON"
    }

    // FIXED: This now uses block-layout textures and stores the texture in PDC
    fun getLuckyBlock(config: ConfigManager, player: Player): ItemStack {
        val rarity = getRandomRarityName(config)

        // Use block-layout textures instead of item textures
        val textures = getBlockTextures(config)
        val textureData = textures.randomOrNull() ?: LuckyBlockTexture(Material.GLASS, "")

        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item

        // Set the head texture
        val texture = textureData.texture
        if (texture.isNotBlank()) {
            setHeadTexture(meta, texture)
        }

        meta.setDisplayName(MSG.chatColors(config.luckyBlockItemName))
        meta.lore = config.getLuckyBlockItemLore(rarity).map { MSG.chatColors(it) }

        val key = org.bukkit.NamespacedKey.minecraft("lucky_block")
        meta.persistentDataContainer.set(key, org.bukkit.persistence.PersistentDataType.STRING, "true")

        // Store BOTH glass type AND texture in PDC
        val glassKey = org.bukkit.NamespacedKey.minecraft("lucky_block_glass")
        meta.persistentDataContainer.set(glassKey, org.bukkit.persistence.PersistentDataType.STRING, textureData.glass.name)

        val textureKey = org.bukkit.NamespacedKey.minecraft("lucky_block_texture")
        meta.persistentDataContainer.set(textureKey, org.bukkit.persistence.PersistentDataType.STRING, texture)

        val traditionalKey = org.bukkit.NamespacedKey.minecraft("traditional_lucky_block")
        meta.persistentDataContainer.set(traditionalKey, org.bukkit.persistence.PersistentDataType.STRING, "true")

        item.itemMeta = meta
        return item
    }

    fun getLuckyBlockItem(config: ConfigManager, rarity: String = "COMMON"): ItemStack {
        val textures = getItemTextures(config)
        val textureData = textures.randomOrNull() ?: LuckyBlockTexture(Material.GLASS, "")
        val item = ItemStack(config.luckyBlockItemMaterial)
        val meta = item.itemMeta as? SkullMeta ?: return item

        val texture = textureData.texture
        if (texture.isNotBlank()) {
            setHeadTexture(meta, texture)
        }
        meta.setDisplayName(MSG.chatColors(config.luckyBlockItemName))
        meta.lore = config.getLuckyBlockItemLore(rarity).map { MSG.chatColors(it) }
        val key = org.bukkit.NamespacedKey.minecraft("lucky_block")
        meta.persistentDataContainer.set(key, org.bukkit.persistence.PersistentDataType.STRING, "true")
        val glassKey = org.bukkit.NamespacedKey.minecraft("lucky_block_glass")
        meta.persistentDataContainer.set(glassKey, org.bukkit.persistence.PersistentDataType.STRING, textureData.glass.name)

        val traditionalKey = org.bukkit.NamespacedKey.minecraft("traditional_lucky_block")
        meta.persistentDataContainer.set(traditionalKey, org.bukkit.persistence.PersistentDataType.STRING, "true")

        item.itemMeta = meta
        return item
    }

    fun getLuckyBlockGlass(config: ConfigManager): Material {
        return getBlockTextures(config).randomOrNull()?.glass ?: Material.GLASS
    }

    // FIXED: Now also reads texture from PDC
    fun getGlassTextureFromSkull(item: ItemStack, config: ConfigManager): LuckyBlockTexture? {
        val meta = item.itemMeta as? SkullMeta ?: return null

        val glassKey = org.bukkit.NamespacedKey.minecraft("lucky_block_glass")
        val glassName = meta.persistentDataContainer.get(glassKey, org.bukkit.persistence.PersistentDataType.STRING)

        val textureKey = org.bukkit.NamespacedKey.minecraft("lucky_block_texture")
        val texture = meta.persistentDataContainer.get(textureKey, org.bukkit.persistence.PersistentDataType.STRING)

        if (glassName != null && texture != null) {
            val glassMaterial = try {
                Material.valueOf(glassName)
            } catch (e: Exception) {
                Material.GLASS
            }
            return LuckyBlockTexture(glassMaterial, texture)
        }

        // Fallback: try to find from block-layout textures
        return getBlockTextures(config).find { it.glass.name == glassName }
    }

    fun setHeadTexture(meta: SkullMeta, texture: String) {
        if (texture.isBlank()) {
            println("[LuckyBlocks] Attempted to set blank texture")
            return
        }

        try {
            println("[LuckyBlocks] Setting head texture using Paper API: ${texture.take(50)}...")

            // Use Paper's PlayerProfile API instead of reflection
            val profile = org.bukkit.Bukkit.createProfile(java.util.UUID.randomUUID())
            profile.setProperty(com.destroystokyo.paper.profile.ProfileProperty("textures", texture))
            meta.playerProfile = profile

            println("[LuckyBlocks] Successfully set head texture using Paper API")
        } catch (e: Exception) {
            println("[LuckyBlocks] Failed to set head texture with Paper API: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()

            // Fallback to old reflection method if Paper API fails
            try {
                println("[LuckyBlocks] Trying fallback reflection method...")

                val profileClass = Class.forName("com.mojang.authlib.GameProfile")
                // Use a non-null name to avoid NullPointerException
                val profile = profileClass.getConstructor(java.util.UUID::class.java, String::class.java)
                    .newInstance(java.util.UUID.randomUUID(), "LuckyBlock")

                val propertyMap = profileClass.getMethod("getProperties").invoke(profile)
                val propertyClass = Class.forName("com.mojang.authlib.properties.Property")
                val property = propertyClass.getConstructor(String::class.java, String::class.java)
                    .newInstance("textures", texture)

                val putMethod = propertyMap.javaClass.getDeclaredMethod("put", Any::class.java, Any::class.java)
                putMethod.invoke(propertyMap, "textures", property)

                val metaClass: Class<*> = meta.javaClass
                var field: Field? = null

                try {
                    field = metaClass.getDeclaredField("profile")
                } catch (e2: NoSuchFieldException) {
                    val superClass = metaClass.superclass
                    field = superClass.getDeclaredField("profile")
                }

                field?.let {
                    it.isAccessible = true
                    it.set(meta, profile)
                    println("[LuckyBlocks] Successfully set head texture using reflection fallback")
                }
            } catch (e2: Exception) {
                println("[LuckyBlocks] Fallback reflection also failed: ${e2.javaClass.simpleName} - ${e2.message}")
                e2.printStackTrace()
            }
        }
    }

    fun getRarityDisplay(rarity: String): String = rarity.replaceFirstChar { it.uppercase() }

    fun isLuckyBlockItem(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.PLAYER_HEAD || item.itemMeta == null) return false
        val meta = item.itemMeta
        val key = org.bukkit.NamespacedKey.minecraft("lucky_block")
        return key != null &&
                meta.persistentDataContainer.has(key, org.bukkit.persistence.PersistentDataType.STRING)
    }

    fun isTraditionalLuckyBlockItem(item: ItemStack?): Boolean {
        if (!isLuckyBlockItem(item)) return false
        val meta = item?.itemMeta ?: return false
        val traditionalKey = org.bukkit.NamespacedKey.minecraft("traditional_lucky_block")
        return meta.persistentDataContainer.has(traditionalKey, org.bukkit.persistence.PersistentDataType.STRING)
    }

    fun canEquipLuckyBlock(item: ItemStack?): Boolean {
        return false
    }

    fun giveLuckyBlock(config: ConfigManager, player: Player, rarity: String = getRandomRarityName(config)) {
        val item = getLuckyBlockItem(config, rarity)
        val inventory = player.inventory
        val firstEmpty = inventory.firstEmpty()
        if (firstEmpty != -1) {
            inventory.setItem(firstEmpty, item)
        } else {
            player.world.dropItemNaturally(player.location, item)
            player.sendMessage(MSG.chatColors("&cYour inventory is full! LuckyBlock dropped at your feet."))
        }
    }

    fun spawnLuckyBlockArmorStand(loc: Location, texture: String) {
        val world: World = loc.world ?: return
        val armorStand = world.spawnEntity(
            loc.clone().add(0.5, -0.4, 0.5),
            EntityType.ARMOR_STAND
        ) as ArmorStand
        armorStand.isInvisible = true
        armorStand.isInvulnerable = true
        armorStand.isMarker = true
        armorStand.setGravity(false)
        armorStand.isSmall = true
        armorStand.equipment?.helmet = createLuckyBlockHead(texture)
    }

    fun createLuckyBlockHead(texture: String): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item
        if (texture.isNotBlank()) {
            setHeadTexture(meta, texture)
        }
        item.itemMeta = meta
        return item
    }

    fun removeLuckyBlockArmorStand(loc: Location) {
        val world = loc.world ?: return
        val center = loc.clone().add(0.5, -0.4, 0.5)
        world.getNearbyEntities(center, 0.5, 0.5, 0.5).forEach { entity ->
            if (entity is ArmorStand && entity.isMarker && entity.isInvisible && entity.isSmall) {
                entity.remove()
            }
        }
    }
}