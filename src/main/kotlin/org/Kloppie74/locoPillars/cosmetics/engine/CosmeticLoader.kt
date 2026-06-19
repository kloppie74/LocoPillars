package org.Kloppie74.locoPillars.cosmetics.engine

import org.bukkit.Material
import org.Kloppie74.locoPillars.LocoPillars
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Loads all cosmetics from the plugin's Cosmetics folder.
 *
 * Directory layout:
 *   modules/cosmetics/kill/<id>/Settings.json + Cosmetic.kts
 *   modules/cosmetics/win/<id>/Settings.json + Cosmetic.kts
 *   modules/cosmetics/death/<id>/Settings.json + Cosmetic.kts
 *   modules/cosmetics/cages/<id>/Settings.json + <id>.schem
 */
class CosmeticLoader(private val plugin: LocoPillars) {

    /** All loaded cosmetics, keyed by type then id */
    private val cosmetics = mutableMapOf<CosmeticType, MutableMap<String, CosmeticDefinition>>()
    val scriptEngine = CosmeticScriptEngine(plugin)

    // Default cosmetic IDs bundled with the plugin
    private val defaultKillIds  = listOf(
        "default_kill", "firework_kill", "lightning_kill", "explosion_kill", "crown_kill",
        "blood_kill", "confetti_kill", "ice_kill", "fire_kill", "magic_kill",
        "lava_kill", "tornado_kill", "ghost_kill", "galaxy_kill", "arrow_shower_kill",
        "grim_reaper_kill", "disco_kill", "rocket_kill", "angel_kill", "void_kill"
    )
    private val defaultWinIds   = listOf(
        "default_win", "firework_win", "rainbow_win", "lightning_win",
        "dragon_win", "galaxy_win", "angel_win", "crown_win", "confetti_win",
        "ice_throne_win", "phoenix_win", "star_shower_win", "lightning_storm_win",
        "disco_win", "volcano_win", "neon_win", "god_rays_win", "time_freeze_win",
        "rainbow_shower_win", "tornado_win"
    )
    private val defaultDeathIds = listOf(
        "default_death", "smoke_death", "explode_death", "soul_death",
        "blood_death", "ghost_death", "fire_death", "ice_shard_death", "lightning_death",
        "void_death", "flower_death", "magic_death", "skulls_death", "angel_death",
        "galaxy_death", "tornado_death", "crystal_death", "lava_death",
        "rainbow_death", "smoke_cloud_death"
    )
    private val defaultCageIds  = listOf("default")

    init {
        CosmeticType.values().forEach { cosmetics[it] = mutableMapOf() }
    }

    fun loadAll() {
        CosmeticType.values().forEach { cosmetics[it]!!.clear() }
        scriptEngine.clearCache()

        val root = File(plugin.dataFolder, "modules/cosmetics")
        extractDefaults(root)

        var total = 0
        CosmeticType.values().forEach { type ->
            val typeFolder = File(root, type.folderName)
            if (!typeFolder.exists()) typeFolder.mkdirs()
            val subdirs = typeFolder.listFiles { f -> f.isDirectory } ?: return@forEach
            for (dir in subdirs) {
                if (loadFromDir(dir, type)) total++
            }
        }
        plugin.logger.info("[Cosmetics] Loaded $total cosmetics")
    }

    // ── Default extraction ─────────────────────────────────────────────────────

    private fun extractDefaults(root: File) {
        root.mkdirs()
        fun extract(type: CosmeticType, ids: List<String>) {
            ids.forEach { id ->
                val dir = File(root, "${type.folderName}/$id")
                if (!dir.exists()) {
                    dir.mkdirs()
                    copyResource("modules/cosmetics/${type.folderName}/$id/Settings.json", File(dir, "Settings.json"))
                    if (type != CosmeticType.CAGE) {
                        copyResource("modules/cosmetics/${type.folderName}/$id/Cosmetic.kts", File(dir, "Cosmetic.kts"))
                    }
                    // Cages: schem file is copied by the user — we just create the JSON
                }
            }
        }
        extract(CosmeticType.KILL,  defaultKillIds)
        extract(CosmeticType.WIN,   defaultWinIds)
        extract(CosmeticType.DEATH, defaultDeathIds)
        extract(CosmeticType.CAGE,  defaultCageIds)
    }

    private fun copyResource(resourcePath: String, target: File) {
        try {
            val stream = plugin.getResource(resourcePath) ?: return
            target.outputStream().use { stream.copyTo(it) }
        } catch (e: Exception) {
            plugin.logger.warning("[Cosmetics] Could not extract $resourcePath: ${e.message}")
        }
    }

    // ── Per-directory loading ──────────────────────────────────────────────────

    private fun loadFromDir(dir: File, type: CosmeticType): Boolean {
        val settingsFile = File(dir, "Settings.json")
        if (!settingsFile.exists()) {
            plugin.logger.warning("[Cosmetics] Skipping '${dir.name}' — missing Settings.json")
            return false
        }

        return try {
            val json = JSONObject(settingsFile.readText())
            val id = json.optString("id", dir.name)

            val rarity = try {
                CosmeticRarity.valueOf(json.optString("rarity", "COMMON").uppercase())
            } catch (_: Exception) { CosmeticRarity.COMMON }

            val iconObj = json.optJSONObject("icon")
            val iconMaterial = try {
                Material.valueOf(iconObj?.optString("material", "NETHER_STAR")?.uppercase() ?: "NETHER_STAR")
            } catch (_: Exception) { Material.NETHER_STAR }

            val loreJson = json.optJSONArray("lore") ?: JSONArray()
            val lore = (0 until loreJson.length()).map { loreJson.getString(it) }

            when (type) {
                CosmeticType.CAGE -> loadCage(dir, id, json, rarity, iconMaterial, iconObj, lore)
                else              -> loadScript(dir, id, type, json, rarity, iconMaterial, iconObj, lore)
            }
        } catch (e: Exception) {
            plugin.logger.severe("[Cosmetics] Failed to load '${dir.name}' (${type.folderName}): ${e.message}")
            false
        }
    }

    private fun loadScript(
        dir: File, id: String, type: CosmeticType,
        json: JSONObject, rarity: CosmeticRarity,
        iconMaterial: Material, iconObj: JSONObject?,
        lore: List<String>
    ): Boolean {
        val scriptFile = File(dir, "Cosmetic.kts")
        if (!scriptFile.exists()) {
            plugin.logger.warning("[Cosmetics] Skipping '${dir.name}' — missing Cosmetic.kts")
            return false
        }
        val def = CosmeticDefinition(
            id = id,
            type = type,
            displayName = json.optString("display_name", id),
            lore = lore,
            price = json.optInt("price", 0),
            requiredLevel = json.optInt("required_level", 0),
            permission = json.optString("permission", ""),
            rarity = rarity,
            iconMaterial = iconMaterial,
            iconCustomModelData = iconObj?.optInt("custom_model_data") ?: 0,
            active = json.optBoolean("active", true),
            scriptFile = scriptFile
        )
        cosmetics[type]!![id] = def
        scriptEngine.compile(id, scriptFile, type)
        return true
    }

    private fun loadCage(
        dir: File, id: String,
        json: JSONObject, rarity: CosmeticRarity,
        iconMaterial: Material, iconObj: JSONObject?,
        lore: List<String>
    ): Boolean {
        // Cage.schem is the canonical name; any other .schem in the folder is also accepted.
        val schemFile = File(dir, "Cage.schem").takeIf { it.exists() }
            ?: dir.listFiles { f -> f.extension == "schem" }?.firstOrNull()

        if (schemFile == null) {
            plugin.logger.info("[Cosmetics] Cage '$id' has no Cage.schem yet — registered as placeholder (add Cage.schem to the folder)")
        }
        val def = CosmeticDefinition(
            id = id,
            type = CosmeticType.CAGE,
            displayName = json.optString("display_name", id),
            lore = lore,
            price = json.optInt("price", 0),
            requiredLevel = json.optInt("required_level", 0),
            permission = json.optString("permission", ""),
            rarity = rarity,
            iconMaterial = iconMaterial,
            iconCustomModelData = iconObj?.optInt("custom_model_data") ?: 0,
            active = json.optBoolean("active", true),
            schemFile = schemFile
        )
        cosmetics[CosmeticType.CAGE]!![id] = def
        return true
    }

    // ── Public accessors ───────────────────────────────────────────────────────

    fun getAll(type: CosmeticType): List<CosmeticDefinition> =
        cosmetics[type]?.values?.sortedWith(compareBy({ it.rarity.ordinal }, { it.displayName })) ?: emptyList()

    fun get(type: CosmeticType, id: String): CosmeticDefinition? = cosmetics[type]?.get(id)

    fun getAll(): List<CosmeticDefinition> = CosmeticType.values().flatMap { getAll(it) }

    fun count(): Int = cosmetics.values.sumOf { it.size }

    fun reload() = loadAll()
}
