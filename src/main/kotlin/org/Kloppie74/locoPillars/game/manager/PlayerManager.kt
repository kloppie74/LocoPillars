package org.Kloppie74.locoPillars.game.manager

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.Kloppie74.locoPillars.LocoPillars

class PlayerManager(private val plugin: LocoPillars) {

    fun preparePlayerForGame(player: Player) {
        debugLog("Preparing ${player.name} for game")

        player.gameMode = GameMode.SURVIVAL
        player.health = 20.0
        player.foodLevel = 20
        player.saturation = 20f
        player.inventory.clear()
        player.enderChest.clear()
        player.exp = 0f
        player.level = 0

        // Clear potion effects
        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }

        debugLog("Player ${player.name} prepared successfully")
    }

    fun restorePlayerState(player: Player) {
        debugLog("Restoring state for ${player.name}")

        player.gameMode = GameMode.SURVIVAL
        player.health = 20.0
        player.foodLevel = 20
        player.saturation = 20f
        player.inventory.clear()

        // Clear potion effects
        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }

        debugLog("Player state restored for ${player.name}")
    }

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[PlayerManager] $message")
        }
    }
}