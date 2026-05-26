package org.Kloppie74.locoPillars.cosmetics.provider

import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Delegates to the PlayerPoints plugin (https://dev.bukkit.org/projects/playerpoints).
 * Falls back gracefully when the plugin is absent.
 */
class PlayerPointsCoinsProvider : CoinsProvider {

    private val api by lazy {
        try {
            val pp = Bukkit.getPluginManager().getPlugin("PlayerPoints") ?: return@lazy null
            if (!pp.isEnabled) return@lazy null
            // Reflective access so we don't need a hard compile dependency
            val clazz = Class.forName("org.black_ixx.playerpoints.PlayerPoints")
            val getInstance = clazz.getMethod("getInstance")
            val instance = getInstance.invoke(null)
            val getApi = clazz.getMethod("getAPI")
            getApi.invoke(instance)
        } catch (_: Exception) { null }
    }

    override fun isAvailable(): Boolean = api != null

    override fun getCoins(player: Player): Int {
        val a = api ?: return 0
        return try {
            a.javaClass.getMethod("look", java.util.UUID::class.java).invoke(a, player.uniqueId) as Int
        } catch (_: Exception) { 0 }
    }

    override fun addCoins(player: Player, amount: Int): Boolean {
        val a = api ?: return false
        return try {
            a.javaClass.getMethod("give", java.util.UUID::class.java, Int::class.java)
                .invoke(a, player.uniqueId, amount) as Boolean
        } catch (_: Exception) { false }
    }

    override fun removeCoins(player: Player, amount: Int): Boolean {
        val a = api ?: return false
        return try {
            a.javaClass.getMethod("take", java.util.UUID::class.java, Int::class.java)
                .invoke(a, player.uniqueId, amount) as Boolean
        } catch (_: Exception) { false }
    }
}
