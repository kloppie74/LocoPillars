package org.Kloppie74.locoPillars.utils.preferences

import org.Kloppie74.locoPillars.LocoPillars
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Per-player preference storage backed by a single JSON file in the plugin
 * data folder ([plugin.dataFolder]/player_preferences.json).
 *
 * Currently tracks only the auto-queue toggle — defaults to ON when no entry
 * exists, matching the server-default contract.
 *
 * The server-wide flag in config.yml ([ConfigManager.autoQueueEnabled])
 * overrides any per-player preference: when disabled there, auto-queue is
 * fully off regardless of what individual players have chosen.
 */
class PlayerPreferences(private val plugin: LocoPillars) {

    private data class Prefs(var autoQueue: Boolean = true)

    private val cache = ConcurrentHashMap<UUID, Prefs>()
    private val file: File = File(plugin.dataFolder, "player_preferences.json")

    fun load() {
        cache.clear()
        if (!file.exists()) return
        try {
            val root = JSONObject(file.readText())
            for (key in root.keys()) {
                val obj = root.optJSONObject(key) ?: continue
                val uuid = try { UUID.fromString(key) } catch (_: Exception) { continue }
                cache[uuid] = Prefs(autoQueue = obj.optBoolean("auto_queue", true))
            }
            plugin.logger.info("[PlayerPreferences] Loaded ${cache.size} player preference entries")
        } catch (e: Exception) {
            plugin.logger.warning("[PlayerPreferences] Failed to read $file: ${e.message}")
        }
    }

    fun save() {
        try {
            val root = JSONObject()
            cache.forEach { (uuid, prefs) ->
                root.put(uuid.toString(), JSONObject().put("auto_queue", prefs.autoQueue))
            }
            file.parentFile?.mkdirs()
            file.writeText(root.toString(2))
        } catch (e: Exception) {
            plugin.logger.warning("[PlayerPreferences] Failed to save $file: ${e.message}")
        }
    }

    /** Player-level toggle. Defaults to true when no entry exists. */
    fun isAutoQueueEnabled(uuid: UUID): Boolean = cache[uuid]?.autoQueue ?: true

    /** Returns the new value after toggling. Persists immediately. */
    fun toggleAutoQueue(uuid: UUID): Boolean {
        val prefs = cache.getOrPut(uuid) { Prefs() }
        prefs.autoQueue = !prefs.autoQueue
        save()
        return prefs.autoQueue
    }

    fun setAutoQueue(uuid: UUID, value: Boolean) {
        val prefs = cache.getOrPut(uuid) { Prefs() }
        prefs.autoQueue = value
        save()
    }
}
