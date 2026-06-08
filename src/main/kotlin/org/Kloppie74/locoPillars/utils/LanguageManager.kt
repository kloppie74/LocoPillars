package org.Kloppie74.locoPillars.utils

import org.Kloppie74.locoPillars.LocoPillars
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

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
        val loadedKeys = config.getKeys(true).count { !config.isConfigurationSection(it) }
        plugin.logger.info("[LanguageManager] language.yml loaded ($loadedKeys keys)")

        // ── Startup completeness check ──────────────────────────────────────
        // Compare the bundled default language.yml (from the JAR) against the
        // file on disk. Any missing leaf key is auto-filled with its default
        // value so the server never prints "[Missing: …]" warnings again.
        validateAndBackfill(file)
    }

    /**
     * Loads the bundled `language.yml` from the JAR, finds every leaf key that
     * is absent in the on-disk file, inserts the default value, and saves.
     * Logs a summary so admins know the file was patched.
     */
    private fun validateAndBackfill(file: File) {
        val stream = plugin.getResource("language.yml") ?: run {
            plugin.logger.warning("[LanguageManager] Could not read bundled language.yml — skipping completeness check.")
            return
        }
        val defaults = YamlConfiguration()
        defaults.load(InputStreamReader(stream, StandardCharsets.UTF_8))

        val defaultLeafKeys = defaults.getKeys(true).filter { !defaults.isConfigurationSection(it) }
        val missing = defaultLeafKeys.filter { !config.contains(it) }

        if (missing.isEmpty()) return   // file is already complete

        plugin.logger.warning("[LanguageManager] language.yml is missing ${missing.size} key(s) — auto-filling with defaults:")
        missing.forEach { key ->
            val value = defaults.get(key)
            config.set(key, value)
            plugin.logger.warning("[LanguageManager]   + $key")
        }

        try {
            config.save(file)
            plugin.logger.info("[LanguageManager] language.yml patched and saved (${missing.size} key(s) added).")
        } catch (e: Exception) {
            plugin.logger.severe("[LanguageManager] Failed to save patched language.yml: ${e.message}")
        }
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
