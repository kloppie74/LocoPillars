package org.Kloppie74.locoPillars.utils

import org.Kloppie74.locoPillars.LocoPillars
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

class LanguageManager(private val plugin: LocoPillars) {

    private var config: YamlConfiguration = YamlConfiguration()

    init {
        reload()
    }

    fun reload() {
        val file = File(plugin.dataFolder, "language.yml")
        if (!file.exists()) {
            plugin.saveResource("language.yml", false)
        }
        config = YamlConfiguration.loadConfiguration(file)
        plugin.logger.info("[LanguageManager] language.yml loaded (${config.getKeys(true).count { !config.isConfigurationSection(it) }} keys)")
    }

    /**
     * Returns the translated and color-formatted message for [key].
     * Placeholders use {name} syntax and are replaced before color parsing.
     */
    fun get(key: String, vararg placeholders: Pair<String, String>): String {
        val raw = config.getString(key)
        if (raw == null) {
            plugin.logger.warning("[LanguageManager] Missing language key: $key")
            return MSG.chatColors("&c[Missing: $key]")
        }
        var result = raw
        for ((name, value) in placeholders) {
            result = result?.replace("{$name}", value)
        }
        return MSG.chatColors(result)
    }

    /**
     * Returns a translated list of strings (e.g. for multi-line messages).
     */
    fun getList(key: String, vararg placeholders: Pair<String, String>): List<String> {
        return config.getStringList(key).map { line ->
            var result = line
            for ((name, value) in placeholders) {
                result = result.replace("{$name}", value)
            }
            MSG.chatColors(result)
        }
    }
}
