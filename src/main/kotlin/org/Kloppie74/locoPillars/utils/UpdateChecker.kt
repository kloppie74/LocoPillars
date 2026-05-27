package org.Kloppie74.locoPillars.utils

import com.google.gson.JsonParser
import org.Kloppie74.locoPillars.LocoPillars
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(private val plugin: LocoPillars) {

    companion object {
        private const val MODRINTH_SLUG = "locopillars"
        private const val API_BASE = "https://api.modrinth.com/v2"
    }

    var updateAvailable: Boolean = false
        private set
    private var latestVersion: String? = null
    private var latestVersionType: String? = null

    /**
     * Which Modrinth version_types are acceptable depending on the running build type.
     *   RELEASE → only release builds
     *   BETA    → beta and release
     *   ALPHA   → alpha, beta and release
     */
    private fun acceptableTypes(buildType: String): Set<String> = when (buildType.uppercase()) {
        "BETA"  -> setOf("release", "beta")
        "ALPHA" -> setOf("release", "beta", "alpha")
        else    -> setOf("release")  // RELEASE (default)
    }

    fun checkAsync() {
        if (!plugin.config.getBoolean("update-checker.enabled", true)) return

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val currentVersion = plugin.config.getString("build.version") ?: plugin.pluginMeta.version
                val buildType      = plugin.config.getString("build.type", "RELEASE") ?: "RELEASE"
                val acceptable     = acceptableTypes(buildType)

                // Fetch enough versions to find one that matches our channel
                val url = URL("$API_BASE/project/$MODRINTH_SLUG/version?limit=10")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", "LocoPillars/$currentVersion (Minecraft Plugin)")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode != 200) {
                    plugin.logger.warning("[UpdateChecker] Modrinth API returned HTTP ${conn.responseCode}")
                    return@Runnable
                }

                val body = conn.inputStream.bufferedReader().readText()
                val versions = JsonParser.parseString(body).asJsonArray

                if (versions.size() == 0) return@Runnable

                // Find the newest version that fits our channel (Modrinth returns newest-first)
                for (element in versions) {
                    val obj         = element.asJsonObject
                    val versionNum  = obj.get("version_number").asString
                    val versionType = obj.get("version_type").asString  // "release" | "beta" | "alpha"

                    if (versionType !in acceptable) continue

                    if (isNewer(versionNum, currentVersion)) {
                        latestVersion     = versionNum
                        latestVersionType = versionType
                        updateAvailable   = true
                        plugin.logger.info("[LocoPillars] Update available: $currentVersion -> $versionNum (${versionType.uppercase()})")
                        plugin.logger.info("[LocoPillars] Download: https://modrinth.com/plugin/locopillars")
                    } else {
                        plugin.logger.info("[LocoPillars] Plugin is up-to-date. ($currentVersion / channel: $buildType)")
                    }
                    break  // First acceptable version is the newest in that channel
                }

            } catch (ex: Exception) {
                plugin.logger.warning("[UpdateChecker] Could not check for updates: ${ex.message}")
            }
        })
    }

    fun notifyPlayer(player: Player) {
        if (!updateAvailable) return
        if (!player.hasPermission("locopillars.notify.update")) return

        val currentVersion = plugin.config.getString("build.version") ?: plugin.pluginMeta.version
        val type   = latestVersionType?.uppercase() ?: "RELEASE"
        val latest = latestVersion ?: return

        // Small delay so join messages settle first
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            player.sendMessage("${ChatColor.DARK_GRAY}${ChatColor.STRIKETHROUGH}══════════════════════════════════════════")
            player.sendMessage(" ${ChatColor.GOLD}${ChatColor.BOLD}LocoPillars ${ChatColor.GRAY}» ${ChatColor.YELLOW}Update Available!")
            player.sendMessage(" ${ChatColor.GRAY}Huidig:  ${ChatColor.RED}$currentVersion")
            player.sendMessage(" ${ChatColor.GRAY}Nieuwst: ${ChatColor.GREEN}$latest ${ChatColor.DARK_GRAY}(${ChatColor.AQUA}$type${ChatColor.DARK_GRAY})")
            player.sendMessage(" ${ChatColor.GRAY}Download: ${ChatColor.AQUA}https://modrinth.com/plugin/locopillars")
            player.sendMessage("${ChatColor.DARK_GRAY}${ChatColor.STRIKETHROUGH}══════════════════════════════════════════")
        }, 40L)
    }

    fun getLatestVersion(): String? = latestVersion
    fun getLatestVersionType(): String? = latestVersionType

    // Version format: YY.MM.N  (e.g. 26.05.2)
    private fun isNewer(candidate: String, current: String): Boolean {
        val c = candidate.split(".").mapNotNull { it.toIntOrNull() }
        val r = current.split(".").mapNotNull { it.toIntOrNull() }
        if (c.isEmpty() || r.isEmpty()) return false
        val len = maxOf(c.size, r.size)
        for (i in 0 until len) {
            val cv = c.getOrElse(i) { 0 }
            val rv = r.getOrElse(i) { 0 }
            if (cv > rv) return true
            if (cv < rv) return false
        }
        return false
    }
}
