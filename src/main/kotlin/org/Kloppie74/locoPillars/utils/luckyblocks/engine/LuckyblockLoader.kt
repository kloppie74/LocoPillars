package org.Kloppie74.locoPillars.utils.luckyblocks.engine

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.json.JSONObject
import java.io.File
import kotlin.random.Random

data class LoadedLuckyblock(
    val settings: LuckyblockSettings,
    val scriptFile: File
)

/**
 * Manages loading, caching, and execution of all luckyblocks.
 * Each luckyblock lives in plugins/LocoPillars/modules/luckyblocks/<id>/
 * and requires Settings.json + Luckyblock.kts.
 */
class LuckyblockLoader(private val plugin: LocoPillars) {

    private val luckyblocks = mutableMapOf<String, LoadedLuckyblock>()
    val scriptEngine = LuckyblockScriptEngine(plugin)

    private val defaultIds = listOf(
        // COMMON (20)
        "firework_frenzy", "random_teleport", "tnt_surprise", "speed_rush",
        "arrow_rain", "xp_shower", "pumpkin_head", "milk_cure", "dirt_tower",
        "cake_gift", "snowball_fight", "confusion_cloud", "night_hunter",
        "pig_ride", "item_shower", "leap_launch", "web_trap", "baby_creeper",
        "flower_circle", "sand_avalanche",
        // UNCOMMON (16)
        "mob_swarm", "treasure_chest", "potion_rain", "freeze_trap",
        "bone_storm", "obsidian_cage", "enchanted_sword", "ender_chest_gift",
        "double_strength", "diamond_helmet_gift", "guardian_swarm", "tnt_cluster",
        "healing_aura", "enchanted_bow_gift", "wolf_pack", "lava_ring",
        // RARE (9)
        "lightning_storm", "chaos_swap", "pillar_of_fire", "wither_awakens",
        "god_apple_gift", "diamond_kit", "earthquake", "levitation_trap", "mass_teleport",
        // EPIC (3)
        "thunder_god", "undead_army", "black_hole",
        // LEGENDARY (2)
        "lucky_legend", "apocalypse"
    )

    fun loadAll() {
        luckyblocks.clear()
        scriptEngine.clearCache()

        val dir = File(plugin.dataFolder, "modules/luckyblocks")
        extractDefaults(dir)

        val subdirs = dir.listFiles { f -> f.isDirectory } ?: return
        var count = 0
        for (sub in subdirs) {
            if (loadFromDir(sub)) count++
        }
        plugin.logger.info("[LuckyBlocks] Loaded $count luckyblocks")
    }

    private fun extractDefaults(dir: File) {
        dir.mkdirs()
        copyResource("modules/luckyblocks/README.md", File(dir, "README.md"))
        for (id in defaultIds) {
            val target = File(dir, id)
            if (!target.exists()) {
                target.mkdirs()
                copyResource("modules/luckyblocks/$id/Settings.json", File(target, "Settings.json"))
                copyResource("modules/luckyblocks/$id/Luckyblock.kts", File(target, "Luckyblock.kts"))
            }
        }
    }

    private fun copyResource(resourcePath: String, target: File) {
        try {
            val stream = plugin.getResource(resourcePath) ?: return
            target.outputStream().use { stream.copyTo(it) }
        } catch (e: Exception) {
            plugin.logger.warning("[LuckyBlocks] Could not extract $resourcePath: ${e.message}")
        }
    }

    private fun loadFromDir(dir: File): Boolean {
        val settingsFile = File(dir, "Settings.json")
        val scriptFile = File(dir, "Luckyblock.kts")

        if (!settingsFile.exists() || !scriptFile.exists()) {
            plugin.logger.warning("[LuckyBlocks] Skipping '${dir.name}' — missing Settings.json or Luckyblock.kts")
            return false
        }

        return try {
            val json = JSONObject(settingsFile.readText())
            val id = json.optString("id", dir.name)
            val rarity = try {
                LuckyblockRarity.valueOf(json.optString("rarity", "COMMON").uppercase())
            } catch (_: Exception) { LuckyblockRarity.COMMON }
            val iconObj = json.optJSONObject("icon")
            val iconMaterial = try {
                Material.valueOf(iconObj?.optString("material", "PLAYER_HEAD")?.uppercase() ?: "PLAYER_HEAD")
            } catch (_: Exception) { Material.PLAYER_HEAD }

            val settings = LuckyblockSettings(
                id = id,
                displayName = json.optString("display_name", id),
                description = json.optString("description", ""),
                icon = LuckyblockIconSettings(iconMaterial, iconObj?.optInt("custom_model_data") ?: 0),
                blockType = json.optString("block_type", "LUCKY_BLOCK"),
                rarity = rarity
            )

            val lb = LoadedLuckyblock(settings, scriptFile)
            luckyblocks[id] = lb
            scriptEngine.compile(id, scriptFile)
            true
        } catch (e: Exception) {
            plugin.logger.severe("[LuckyBlocks] Failed to load '${dir.name}': ${e.message}")
            false
        }
    }

    fun execute(player: Player, location: Location, game: GameInstance?) {
        val lb = selectRandom() ?: run {
            plugin.logger.warning("[LuckyBlocks] No luckyblocks loaded!")
            return
        }
        val context = LuckyblockContext(location, game, plugin)
        scriptEngine.execute(lb.settings.id, lb.scriptFile, player, context)
    }

    private fun selectRandom(): LoadedLuckyblock? {
        if (luckyblocks.isEmpty()) return null
        val list = luckyblocks.values.toList()
        val total = list.sumOf { it.settings.rarity.weight }
        var roll = Random.nextInt(total)
        for (lb in list) {
            roll -= lb.settings.rarity.weight
            if (roll < 0) return lb
        }
        return list.last()
    }

    fun getLuckyblock(id: String): LoadedLuckyblock? = luckyblocks[id]
    fun getAll(): List<LoadedLuckyblock> = luckyblocks.values.sortedBy { it.settings.displayName }
    fun count(): Int = luckyblocks.size
    fun reload() = loadAll()
}
