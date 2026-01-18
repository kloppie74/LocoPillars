package org.Kloppie74.locoPillars.api

import org.Kloppie74.locoPillars.LocoPillars
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask

/**
 * Service die stats tracked en alle API calls beheert
 */
class LocoDevStatsService(private val plugin: LocoPillars) {

    private var client: LocoDevStatsClient? = null
    private var statsTask: BukkitTask? = null
    private var enabled = false

    fun start() {
        try {
            // Hardcoded config
            val apiUrl = "https://api.locomc.net"
            val pluginId = "cmjzuy3zg00004nph1vzb2p7y"
            val updateInterval = 30L // 30 seconds

            client = LocoDevStatsClient(apiUrl, pluginId)

            // Register startup
            val startupSuccess = client!!.registerStartup(
                serverVersion = Bukkit.getVersion(),
                pluginVersion = plugin.description.version
            )

            if (startupSuccess) {
                plugin.logger.info("[LocoDev] Server startup registered")
            }

            // Check voor updates
            checkForUpdates()

            // Haal announcements op
            fetchAnnouncements()

            // Haal globale stats op
            fetchGlobalStats()

            // Start periodic stats task
            val ticks = updateInterval * 20L
            statsTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable {
                sendStats()
            }, ticks, ticks)

            enabled = true
            plugin.logger.info("[LocoDev] Stats tracking started (every ${updateInterval}s)")

            // Send first stats immediately
            sendStats()

        } catch (e: Exception) {
            plugin.logger.warning("[LocoDev] Could not start stats: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun sendStats() {
        try {
            val stats = LocoDevStatsClient.StatsData(
                onlinePlayers = Bukkit.getOnlinePlayers().size,
                activeGames = plugin.gameManager.getActiveGames().size,
                totalServers = 1
            )

            val success = client?.sendStats(stats) ?: false

            if (plugin.configManager.debugMode) {
                if (success) {
                    plugin.logger.info("[LocoDev] Stats sent: ${stats.onlinePlayers} players, ${stats.activeGames} games")
                } else {
                    plugin.logger.warning("[LocoDev] Failed to send stats")
                }
            }
        } catch (e: Exception) {
            if (plugin.configManager.debugMode) {
                plugin.logger.warning("[LocoDev] Error sending stats: ${e.message}")
            }
        }
    }

    /**
     * Check voor nieuwe plugin versie
     */
    fun checkForUpdates() {
        try {
            plugin.logger.info("[LocoDev] Checking version with API...")
            val versionInfo = client?.checkVersion(plugin.description.version)

            if (versionInfo != null) {
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7==========================================")
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §eVersion Check")
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7Current: §f${versionInfo.currentVersion}")
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7Latest: §f${versionInfo.latestVersion}")

                if (versionInfo.updateAvailable) {
                    Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §a✓ Update available!")
                    if (versionInfo.downloadUrl != null) {
                        Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7Download: §b${versionInfo.downloadUrl}")
                    }
                    if (versionInfo.changelog != null) {
                        Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7Changelog:")
                        versionInfo.changelog.lines().forEach { line ->
                            Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8]   §7$line")
                        }
                    }
                } else {
                    Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §a✓ You're running the latest version!")
                }

                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7==========================================")
            } else {
                plugin.logger.warning("[LocoDev] Version check returned null - API might be unreachable")
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §cFailed to check version - API unreachable")
            }
        } catch (e: Exception) {
            plugin.logger.warning("[LocoDev] Error checking version: ${e.message}")
            e.printStackTrace()
            Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §cError: ${e.message}")
        }
    }

    /**
     * Haal announcements op en toon in console
     */
    fun fetchAnnouncements() {
        try {
            plugin.logger.info("[LocoDev] Fetching announcements from API...")
            val announcements = client?.getAnnouncements() ?: emptyList()

            if (announcements.isNotEmpty()) {
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7==========================================")
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §cAnnouncements (${announcements.size})")

                announcements.forEach { announcement ->
                    val color = when (announcement.priority) {
                        "CRITICAL" -> "§c"
                        "HIGH" -> "§6"
                        "MEDIUM" -> "§e"
                        else -> "§7"
                    }

                    Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] $color[${announcement.priority}] ${announcement.title}")
                    Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7${announcement.message}")
                    Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §8${announcement.createdAt}")
                    Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §8---")
                }

                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7==========================================")
            } else {
                plugin.logger.info("[LocoDev] No announcements found or API unreachable")
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7No announcements available")
            }
        } catch (e: Exception) {
            plugin.logger.warning("[LocoDev] Error fetching announcements: ${e.message}")
            e.printStackTrace()
            Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §cError: ${e.message}")
        }
    }

    /**
     * Haal globale stats op en toon in console
     */
    fun fetchGlobalStats() {
        try {
            plugin.logger.info("[LocoDev] Fetching global stats from API...")
            val globalStats = client?.getGlobalStats()

            if (globalStats != null) {
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7==========================================")
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §aGlobal Statistics")
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7Total Servers: §f${globalStats.totalServers}")
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7Total Players: §f${globalStats.totalPlayers}")
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7Active Games: §f${globalStats.totalActiveGames}")
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7Peak Players: §f${globalStats.peakPlayers}")
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §8Updated: ${globalStats.updatedAt}")
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §7==========================================")
            } else {
                plugin.logger.warning("[LocoDev] Global stats returned null - API might be unreachable")
                Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §cFailed to fetch global stats - API unreachable")
            }
        } catch (e: Exception) {
            plugin.logger.warning("[LocoDev] Error fetching global stats: ${e.message}")
            e.printStackTrace()
            Bukkit.getConsoleSender().sendMessage("§8[§6LocoDev§8] §cError: ${e.message}")
        }
    }

    /**
     * Rapporteer een error naar LocoDev
     */
    fun reportError(errorType: String, message: String, stackTrace: String? = null) {
        try {
            val success = client?.reportError(errorType, message, stackTrace) ?: false

            if (plugin.configManager.debugMode) {
                if (success) {
                    plugin.logger.info("[LocoDev] Error reported: $errorType")
                } else {
                    plugin.logger.warning("[LocoDev] Failed to report error")
                }
            }
        } catch (e: Exception) {
            // Silent fail
        }
    }

    fun stop() {
        if (!enabled) return

        statsTask?.cancel()
        statsTask = null

        // Register shutdown
        try {
            val shutdownSuccess = client?.registerShutdown() ?: false
            if (shutdownSuccess) {
                plugin.logger.info("[LocoDev] Server shutdown registered")
            }
        } catch (e: Exception) {
            // Ignore
        }

        // Send final stats
        try {
            sendStats()
        } catch (e: Exception) {
            // Ignore
        }

        client?.close()
        client = null
        enabled = false

        plugin.logger.info("[LocoDev] Stats tracking stopped")
    }

    fun isEnabled() = enabled
}