package org.Kloppie74.locoPillars.api

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * HTTP client voor LocoDev Stats API met alle mogelijke calls
 */
class LocoDevStatsClient(private val apiUrl: String, private val pluginId: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    data class StatsData(
        val onlinePlayers: Int,
        val activeGames: Int,
        val totalServers: Int = 1
    )

    data class VersionInfo(
        val latestVersion: String,
        val currentVersion: String,
        val updateAvailable: Boolean,
        val downloadUrl: String?,
        val changelog: String?
    )

    data class AnnouncementData(
        val id: String,
        val title: String,
        val message: String,
        val priority: String, // LOW, MEDIUM, HIGH, CRITICAL
        val createdAt: String
    )

    data class GlobalStats(
        val totalServers: Int,
        val totalPlayers: Int,
        val totalActiveGames: Int,
        val peakPlayers: Int,
        val updatedAt: String
    )

    /**
     * Stuur stats naar de API
     */
    fun sendStats(stats: StatsData): Boolean {
        return try {
            val json = JSONObject().apply {
                put("pluginId", pluginId)
                put("onlinePlayers", stats.onlinePlayers)
                put("activeGames", stats.activeGames)
                put("totalServers", stats.totalServers)
            }

            val request = Request.Builder()
                .url("$apiUrl/mc/stats")
                .post(json.toString().toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check voor nieuwe plugin versie
     */
    fun checkVersion(currentVersion: String): VersionInfo? {
        return try {
            val url = "$apiUrl/plugins/$pluginId/version"
            println("[LocoDev Debug] Calling: $url")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                println("[LocoDev Debug] Response code: ${response.code}")

                if (!response.isSuccessful) {
                    println("[LocoDev Debug] Request failed: ${response.message}")
                    return null
                }

                val body = response.body?.string()
                println("[LocoDev Debug] Response body: $body")

                if (body == null) return null

                val json = JSONObject(body)

                val latestVersion = json.optString("latestVersion", currentVersion)
                val updateAvailable = json.optBoolean("updateAvailable", false)

                VersionInfo(
                    latestVersion = latestVersion,
                    currentVersion = currentVersion,
                    updateAvailable = updateAvailable,
                    downloadUrl = json.optString("downloadUrl", null),
                    changelog = json.optString("changelog", null)
                )
            }
        } catch (e: Exception) {
            println("[LocoDev Debug] Exception: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Haal announcements op
     */
    fun getAnnouncements(): List<AnnouncementData> {
        return try {
            val url = "$apiUrl/plugins/$pluginId/announcements"
            println("[LocoDev Debug] Calling: $url")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                println("[LocoDev Debug] Response code: ${response.code}")

                if (!response.isSuccessful) {
                    println("[LocoDev Debug] Request failed: ${response.message}")
                    return emptyList()
                }

                val body = response.body?.string()
                println("[LocoDev Debug] Response body: $body")

                if (body == null) return emptyList()

                val json = JSONObject(body)
                val announcements = json.optJSONArray("announcements") ?: return emptyList()

                (0 until announcements.length()).mapNotNull { i ->
                    val item = announcements.optJSONObject(i) ?: return@mapNotNull null
                    AnnouncementData(
                        id = item.optString("id", ""),
                        title = item.optString("title", ""),
                        message = item.optString("message", ""),
                        priority = item.optString("priority", "LOW"),
                        createdAt = item.optString("createdAt", "")
                    )
                }
            }
        } catch (e: Exception) {
            println("[LocoDev Debug] Exception: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Haal globale statistieken op
     */
    fun getGlobalStats(): GlobalStats? {
        return try {
            val url = "$apiUrl/plugins/$pluginId/stats/overview"
            println("[LocoDev Debug] Calling: $url")

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                println("[LocoDev Debug] Response code: ${response.code}")

                if (!response.isSuccessful) {
                    println("[LocoDev Debug] Request failed: ${response.message}")
                    return null
                }

                val body = response.body?.string()
                println("[LocoDev Debug] Response body: $body")

                if (body == null) return null

                val json = JSONObject(body)

                GlobalStats(
                    totalServers = json.optInt("totalServers", 0),
                    totalPlayers = json.optInt("totalPlayers", 0),
                    totalActiveGames = json.optInt("totalActiveGames", 0),
                    peakPlayers = json.optInt("peakPlayers", 0),
                    updatedAt = json.optString("updatedAt", "")
                )
            }
        } catch (e: Exception) {
            println("[LocoDev Debug] Exception: ${e.javaClass.simpleName} - ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Rapporteer een error naar LocoDev
     */
    fun reportError(errorType: String, message: String, stackTrace: String?): Boolean {
        return try {
            val json = JSONObject().apply {
                put("pluginId", pluginId)
                put("errorType", errorType)
                put("message", message)
                if (stackTrace != null) {
                    put("stackTrace", stackTrace)
                }
            }

            val request = Request.Builder()
                .url("$apiUrl/mc/errors")
                .post(json.toString().toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Registreer server startup
     */
    fun registerStartup(serverVersion: String, pluginVersion: String): Boolean {
        return try {
            val json = JSONObject().apply {
                put("pluginId", pluginId)
                put("serverVersion", serverVersion)
                put("pluginVersion", pluginVersion)
            }

            val request = Request.Builder()
                .url("$apiUrl/mc/startup")
                .post(json.toString().toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Registreer server shutdown
     */
    fun registerShutdown(): Boolean {
        return try {
            val json = JSONObject().apply {
                put("pluginId", pluginId)
            }

            val request = Request.Builder()
                .url("$apiUrl/mc/shutdown")
                .post(json.toString().toRequestBody(JSON))
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    fun close() {
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}