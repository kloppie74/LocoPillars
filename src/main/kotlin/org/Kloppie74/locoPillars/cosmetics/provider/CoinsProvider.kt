package org.Kloppie74.locoPillars.cosmetics.provider

import org.bukkit.entity.Player

/**
 * Abstraction over the coins/currency system used for the cosmetics shop.
 * Implementations: [SelfCoinsProvider] (internal) and [PlayerPointsCoinsProvider].
 */
interface CoinsProvider {
    /** Returns the coin balance for [player]. */
    fun getCoins(player: Player): Int

    /** Adds [amount] coins to [player]. Returns true on success. */
    fun addCoins(player: Player, amount: Int): Boolean

    /** Removes [amount] coins from [player]. Returns true if the player had enough. */
    fun removeCoins(player: Player, amount: Int): Boolean

    /** Whether the provider backend is currently available. */
    fun isAvailable(): Boolean
}
