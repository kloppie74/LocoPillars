package org.Kloppie74.locoPillars.cosmetics.provider

import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.database.DatabaseManager
import org.Kloppie74.locoPillars.database.dao.PlayerDAO
import org.Kloppie74.locoPillars.database.dao.StatsDAO

/**
 * Uses the built-in `lp_stats.coins` column as the currency source.
 * No external plugins required.
 */
class SelfCoinsProvider : CoinsProvider {

    override fun isAvailable(): Boolean = DatabaseManager.isConnected()

    override fun getCoins(player: Player): Int {
        val id = PlayerDAO.getPlayerId(player.name) ?: return 0
        return try { StatsDAO.getIntStat(id, "coins") } catch (_: Exception) { 0 }
    }

    override fun addCoins(player: Player, amount: Int): Boolean {
        val id = PlayerDAO.getPlayerId(player.name) ?: return false
        return try { StatsDAO.addCoins(id, amount) } catch (_: Exception) { false }
    }

    override fun removeCoins(player: Player, amount: Int): Boolean {
        val current = getCoins(player)
        if (current < amount) return false
        val id = PlayerDAO.getPlayerId(player.name) ?: return false
        return try { StatsDAO.removeCoins(id, amount) } catch (_: Exception) { false }
    }
}
