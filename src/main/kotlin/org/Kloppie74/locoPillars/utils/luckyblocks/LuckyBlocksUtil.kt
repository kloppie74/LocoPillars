package org.Kloppie74.locoPillars.utils.luckyblocks

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.manager.GameManager
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import org.Kloppie74.locoPillars.utils.luckyblocks.engine.LuckyblockLoader
import kotlin.random.Random

data class LuckyBlockTexture(val glass: Material, val texture: String)
data class LuckyBlockRarity(val name: String, val chance: Int)

object LuckyBlocksUtil {

    private val placedLuckyBlocks: MutableSet<Location> = mutableSetOf()
    private var loader: LuckyblockLoader? = null
    private var gameManager: GameManager? = null

    fun setLuckyblockLoader(l: LuckyblockLoader) { loader = l }
    fun setGameManager(gm: GameManager) { gameManager = gm }
    fun getLoader(): LuckyblockLoader? = loader

    // ── Placement cache ────────────────────────────────────────────────────────

    fun cacheLuckyBlock(loc: Location) = placedLuckyBlocks.add(loc)
    fun removeLuckyBlock(loc: Location) = placedLuckyBlocks.remove(loc)
    fun isPlacedLuckyBlock(loc: Location) = placedLuckyBlocks.contains(loc)
    fun getPlacedLuckyBlocksCache(): Set<Location> = placedLuckyBlocks

    // ── Break handler ──────────────────────────────────────────────────────────

    fun handleLuckyBlockBreak(player: Player, location: Location, game: GameInstance? = null) {
        val l = loader ?: run {
            player.sendMessage(MSG.chatColors("&c[LuckyBlocks] Loader not initialized!"))
            return
        }
        if (l.count() == 0) {
            player.sendMessage(MSG.chatColors("&c[LuckyBlocks] No luckyblocks loaded in the modules/luckyblocks/ folder!"))
            return
        }
        val activeGame = game ?: gameManager?.getPlayerInstance(player)
        if (activeGame != null) {
            val chance = activeGame.arenaInstance.config.getLuckyblockChance()
            if (chance < 100 && Random.nextInt(100) >= chance) {
                player.sendMessage(MSG.chatColors("&7The Lucky Block fizzled..."))
                return
            }
        }
        l.execute(player, location, activeGame)
    }

    // ── Texture helpers ────────────────────────────────────────────────────────

    fun getBlockTextures(config: ConfigManager): List<LuckyBlockTexture> {
        val blockSection = config.getConfig().getConfigurationSection("Luckyblocks.block-layout")
        val textureStrings = blockSection?.getStringList("textures") ?: emptyList()
        return textureStrings.mapNotNull { line ->
            val parts = line.split(",")
            val glassName = parts.find { it.trim().startsWith("Glass=") }
                ?.substringAfter("Glass=")?.trim()?.uppercase()
            val texture = parts.find { it.trim().startsWith("Texture=") }
                ?.substringAfter("Texture=")?.trim()
            val glassMat = when (glassName) {
                "RED" -> Material.RED_STAINED_GLASS
                "BLUE" -> Material.BLUE_STAINED_GLASS
                "GREEN" -> Material.GREEN_STAINED_GLASS
                "YELLOW" -> Material.YELLOW_STAINED_GLASS
                "PINK" -> Material.PINK_STAINED_GLASS
                else -> Material.GLASS
            }
            if (glassName != null && texture != null && texture.isNotBlank())
                LuckyBlockTexture(glassMat, texture) else null
        }
    }

    fun getItemTextures(config: ConfigManager): List<LuckyBlockTexture> {
        val itemSection = config.getConfig().getConfigurationSection("Luckyblocks.item")
        val textureStrings = itemSection?.getStringList("textures") ?: emptyList()
        return textureStrings.mapNotNull { line ->
            val parts = line.split(",")
            val glassName = parts.find { it.trim().startsWith("Glass=") }
                ?.substringAfter("Glass=")?.trim()?.uppercase()
            val texture = parts.find { it.trim().startsWith("Texture=") }
                ?.substringAfter("Texture=")?.trim()
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

    fun getLuckyBlockGlass(config: ConfigManager): Material =
        getBlockTextures(config).randomOrNull()?.glass ?: Material.GLASS

    fun getGlassTextureFromSkull(item: ItemStack, config: ConfigManager): LuckyBlockTexture? {
        val meta = item.itemMeta as? SkullMeta ?: return null
        val glassKey = org.bukkit.NamespacedKey.minecraft("lucky_block_glass")
        val textureKey = org.bukkit.NamespacedKey.minecraft("lucky_block_texture")
        val glassName = meta.persistentDataContainer.get(glassKey, org.bukkit.persistence.PersistentDataType.STRING)
        val texture = meta.persistentDataContainer.get(textureKey, org.bukkit.persistence.PersistentDataType.STRING)
        if (glassName != null && texture != null) {
            val mat = try { Material.valueOf(glassName) } catch (_: Exception) { Material.GLASS }
            return LuckyBlockTexture(mat, texture)
        }
        return getBlockTextures(config).find { it.glass.name == glassName }
    }

    // ── Rarity helpers ─────────────────────────────────────────────────────────

    fun getRarities(config: ConfigManager): List<LuckyBlockRarity> =
        config.luckyBlockRarityChances
            .map { (name, chance) -> LuckyBlockRarity(name.uppercase(), chance) }
            .filter { it.chance > 0 }
            .sortedByDescending { it.chance }

    fun getRandomRarity(config: ConfigManager): LuckyBlockRarity? {
        val rarities = getRarities(config)
        val total = rarities.sumOf { it.chance }
        if (total <= 0) return rarities.firstOrNull()
        var roll = Random.nextInt(total)
        for (r in rarities) { roll -= r.chance; if (roll < 0) return r }
        return rarities.lastOrNull()
    }

    fun getRandomRarityName(config: ConfigManager): String = getRandomRarity(config)?.name ?: "COMMON"
    fun getRarityDisplay(rarity: String): String = rarity.replaceFirstChar { it.uppercase() }

    // ── Item creation ──────────────────────────────────────────────────────────

    fun getLuckyBlock(config: ConfigManager, player: Player): ItemStack {
        val rarity = getRandomRarityName(config)
        val textures = getBlockTextures(config)
        val textureData = textures.randomOrNull() ?: LuckyBlockTexture(Material.GLASS, "")
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item
        if (textureData.texture.isNotBlank()) setHeadTexture(meta, textureData.texture)
        meta.setDisplayName(MSG.chatColors(config.luckyBlockItemName))
        @Suppress("DEPRECATION")
        meta.lore = config.getLuckyBlockItemLore(rarity).map { MSG.chatColors(it) }
        val key = org.bukkit.NamespacedKey.minecraft("lucky_block")
        meta.persistentDataContainer.set(key, org.bukkit.persistence.PersistentDataType.STRING, "true")
        val glassKey = org.bukkit.NamespacedKey.minecraft("lucky_block_glass")
        meta.persistentDataContainer.set(glassKey, org.bukkit.persistence.PersistentDataType.STRING, textureData.glass.name)
        val textureKey = org.bukkit.NamespacedKey.minecraft("lucky_block_texture")
        meta.persistentDataContainer.set(textureKey, org.bukkit.persistence.PersistentDataType.STRING, textureData.texture)
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
        if (textureData.texture.isNotBlank()) setHeadTexture(meta, textureData.texture)
        meta.setDisplayName(MSG.chatColors(config.luckyBlockItemName))
        @Suppress("DEPRECATION")
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

    fun giveLuckyBlock(config: ConfigManager, player: Player, rarity: String = getRandomRarityName(config)) {
        val item = getLuckyBlockItem(config, rarity)
        if (player.inventory.firstEmpty() != -1) {
            player.inventory.addItem(item)
        } else {
            player.world.dropItemNaturally(player.location, item)
            player.sendMessage(MSG.chatColors("&cInventory full — LuckyBlock dropped at your feet."))
        }
    }

    // ── Item detection ─────────────────────────────────────────────────────────

    fun isLuckyBlockItem(item: ItemStack?): Boolean {
        if (item == null || item.type != Material.PLAYER_HEAD || item.itemMeta == null) return false
        val key = org.bukkit.NamespacedKey.minecraft("lucky_block")
        return item.itemMeta!!.persistentDataContainer.has(key, org.bukkit.persistence.PersistentDataType.STRING)
    }

    fun isTraditionalLuckyBlockItem(item: ItemStack?): Boolean {
        if (!isLuckyBlockItem(item)) return false
        val meta = item?.itemMeta ?: return false
        val traditionalKey = org.bukkit.NamespacedKey.minecraft("traditional_lucky_block")
        return meta.persistentDataContainer.has(traditionalKey, org.bukkit.persistence.PersistentDataType.STRING)
    }

    // ── Armor stand (3D block visual) ──────────────────────────────────────────

    fun spawnLuckyBlockArmorStand(loc: Location, texture: String) {
        val world: World = loc.world ?: return
        val stand = world.spawnEntity(loc.clone().add(0.5, -0.4, 0.5), EntityType.ARMOR_STAND) as ArmorStand
        stand.isInvisible = true
        stand.isInvulnerable = true
        stand.isMarker = true
        stand.setGravity(false)
        stand.isSmall = true
        stand.equipment.helmet = createLuckyBlockHead(texture)
    }

    fun removeLuckyBlockArmorStand(loc: Location) {
        val world = loc.world ?: return
        world.getNearbyEntities(loc.clone().add(0.5, -0.4, 0.5), 0.5, 0.5, 0.5).forEach { entity ->
            if (entity is ArmorStand && entity.isMarker && entity.isInvisible && entity.isSmall) entity.remove()
        }
    }

    fun createLuckyBlockHead(texture: String): ItemStack {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as? SkullMeta ?: return item
        if (texture.isNotBlank()) setHeadTexture(meta, texture)
        item.itemMeta = meta
        return item
    }

    fun setHeadTexture(meta: SkullMeta, texture: String) {
        if (texture.isBlank()) return
        try {
            val profile = org.bukkit.Bukkit.createProfile(java.util.UUID.randomUUID())
            profile.setProperty(com.destroystokyo.paper.profile.ProfileProperty("textures", texture))
            meta.playerProfile = profile
        } catch (e: Exception) {
            try {
                val profileClass = Class.forName("com.mojang.authlib.GameProfile")
                val profile = profileClass.getConstructor(java.util.UUID::class.java, String::class.java)
                    .newInstance(java.util.UUID.randomUUID(), "LuckyBlock")
                val propertyMap = profileClass.getMethod("getProperties").invoke(profile)
                val propertyClass = Class.forName("com.mojang.authlib.properties.Property")
                val property = propertyClass.getConstructor(String::class.java, String::class.java)
                    .newInstance("textures", texture)
                propertyMap.javaClass.getDeclaredMethod("put", Any::class.java, Any::class.java)
                    .invoke(propertyMap, "textures", property)
                var field = try {
                    meta.javaClass.getDeclaredField("profile")
                } catch (_: NoSuchFieldException) {
                    meta.javaClass.superclass.getDeclaredField("profile")
                }
                field.isAccessible = true
                field.set(meta, profile)
            } catch (e2: Exception) {
                println("[LuckyBlocks] Could not set head texture: ${e2.message}")
            }
        }
    }
}
