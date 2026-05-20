package org.Kloppie74.locoPillars.game.manager

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils

class PlayerManager(private val plugin: LocoPillars) {

    fun preparePlayerForGame(player: Player) {
        debugLog("Preparing ${player.name} for game")
        forceRespawnIfDead(player)

        // Remove from spectator map first so state is clean
        SpectatorUtils.clearCache(player)

        player.gameMode = GameMode.SURVIVAL
        player.health = 20.0
        player.foodLevel = 20
        player.saturation = 20f
        player.inventory.clear()
        player.enderChest.clear()
        player.exp = 0f
        player.level = 0

        // Reset flight/combat state that may persist from previous games or spectator mode
        player.isInvulnerable = false
        player.allowFlight = false
        player.isFlying = false
        player.isCollidable = true
        player.fallDistance = 0f
        player.fireTicks = 0

        // Clear potion effects
        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }

        // Un-hide this player for all online players (and show all players to them).
        // Undoes any hidePlayer calls from a previous game where this player died as a spectator.
        Bukkit.getOnlinePlayers().forEach { onlinePlayer ->
            onlinePlayer.showPlayer(plugin, player)
            player.showPlayer(plugin, onlinePlayer)
        }

        debugLog("Player ${player.name} prepared successfully")
    }

    fun restorePlayerState(player: Player) {
        debugLog("Restoring state for ${player.name}")
        forceRespawnIfDead(player)

        player.gameMode = GameMode.SURVIVAL
        player.health = 20.0
        player.foodLevel = 20
        player.saturation = 20f
        player.inventory.clear()
        player.enderChest.clear()
        player.exp = 0f
        player.level = 0

        // Reset ALL persistent state flags — these are NOT reset by setting gameMode
        player.isInvulnerable = false
        player.allowFlight = false
        player.isFlying = false
        player.isCollidable = true
        player.fallDistance = 0f
        player.fireTicks = 0

        // Clear potion effects
        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }

        debugLog("Player state restored for ${player.name}")
    }

    private fun forceRespawnIfDead(player: Player) {
        val wasDead = player.isDead
        if (SpectatorUtils.ensurePlayerAlive(player, plugin, "PlayerManager") && wasDead) {
            debugLog("Forced respawn for ${player.name}")
        }
    }

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[PlayerManager] $message")
        }
    }
}
