package org.Kloppie74.locoPillars.game.voting

import org.bukkit.Material
import org.Kloppie74.locoPillars.LocoPillars
import org.json.JSONObject
import java.io.File

class VotingLoader(private val plugin: LocoPillars) {

    private val modules = mutableMapOf<String, VotingSettings>()
    val scriptEngine = VotingScriptEngine(plugin)

    private val defaultModuleIds = listOf(
        "no_fall_mode", "elytra_mode", "double_health", "one_heart_mode",
        "floor_is_lava", "inventory_swap", "player_swap",
        "speed_rush", "ultra_jump", "darkness_battle"
    )

    fun loadAll() {
        modules.clear()
        scriptEngine.clearCache()

        val root = File(plugin.dataFolder, "modules/voting")
        root.mkdirs()
        extractDefaults(root)

        var total = 0
        root.listFiles { f -> f.isDirectory }?.forEach { dir ->
            if (loadFromDir(dir)) total++
        }
        plugin.logger.info("[Voting] Loaded $total voting modules")
    }

    private fun extractDefaults(root: File) {
        defaultModuleIds.forEach { id ->
            val dir = File(root, id)
            if (!dir.exists()) {
                dir.mkdirs()
                copyResource("modules/voting/$id/settings.json", File(dir, "settings.json"))
                copyResource("modules/voting/$id/script.kts", File(dir, "script.kts"))
            }
        }
        val guiFile = File(root, "gui.kts")
        if (!guiFile.exists()) copyResource("modules/voting/gui.kts", guiFile)
    }

    private fun copyResource(resourcePath: String, target: File) {
        try {
            val stream = plugin.getResource(resourcePath) ?: return
            target.outputStream().use { stream.copyTo(it) }
        } catch (e: Exception) {
            plugin.logger.warning("[Voting] Could not extract $resourcePath: ${e.message}")
        }
    }

    private fun loadFromDir(dir: File): Boolean {
        val settingsFile = File(dir, "settings.json")
        val scriptFile = File(dir, "script.kts")
        if (!settingsFile.exists()) {
            plugin.logger.warning("[Voting] Skipping '${dir.name}' — missing settings.json")
            return false
        }
        if (!scriptFile.exists()) {
            plugin.logger.warning("[Voting] Skipping '${dir.name}' — missing script.kts")
            return false
        }
        return try {
            val json = JSONObject(settingsFile.readText())
            val id = json.optString("id", dir.name)
            val iconObj = json.optJSONObject("icon")
            val iconMaterial = try {
                Material.valueOf(iconObj?.optString("material", "PAPER")?.uppercase() ?: "PAPER")
            } catch (_: Exception) { Material.PAPER }
            val announce = json.optJSONObject("announce")
            val settings = VotingSettings(
                id = id,
                displayName = json.optString("display_name", id),
                description = json.optString("description", ""),
                iconMaterial = iconMaterial,
                iconCustomModelData = iconObj?.optInt("custom_model_data") ?: 0,
                active = json.optBoolean("active", true),
                voteWeight = json.optInt("vote_weight", 1),
                duration = json.optInt("duration", -1),
                announceTitle = announce?.optString("title", "") ?: "",
                announceSubtitle = announce?.optString("subtitle", "") ?: "",
                announceChat = announce?.optString("chat", "") ?: "",
                scriptFile = scriptFile
            )
            if (!settings.active) return false
            modules[id] = settings
            scriptEngine.compileModule(id, scriptFile)
            true
        } catch (e: Exception) {
            plugin.logger.severe("[Voting] Failed to load '${dir.name}': ${e.message}")
            false
        }
    }

    fun get(id: String): VotingSettings? = modules[id]
    fun getAll(): List<VotingSettings> = modules.values.sortedBy { it.id }
    fun count(): Int = modules.size
    fun getGuiFile(): File = File(plugin.dataFolder, "modules/voting/gui.kts")
}
