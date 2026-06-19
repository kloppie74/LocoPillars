package org.Kloppie74.locoPillars.utils

import org.bukkit.entity.Player
import kotlin.math.roundToInt

object BoostUtil {

    fun getXpMultiplier(player: Player): Int =
        highestMultiplier(player, "locopillars.boost.xp.")

    fun getCoinsMultiplier(player: Player): Int =
        highestMultiplier(player, "locopillars.boost.coins.")

    fun applyXp(player: Player, base: Int): Int = apply(base, getXpMultiplier(player))

    fun applyCoins(player: Player, base: Int): Int = apply(base, getCoinsMultiplier(player))

    private fun apply(base: Int, multiplier: Int): Int =
        if (multiplier == 100) base
        else ((base.toLong() * multiplier) / 100.0).roundToInt()

    private fun highestMultiplier(player: Player, prefix: String): Int =
        player.effectivePermissions
            .filter { it.value && it.permission.startsWith(prefix, ignoreCase = true) }
            .mapNotNull { it.permission.substring(prefix.length).toIntOrNull() }
            .maxOrNull() ?: 100
}
