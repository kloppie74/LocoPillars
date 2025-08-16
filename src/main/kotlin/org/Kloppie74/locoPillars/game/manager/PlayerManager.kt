package org.Kloppie74.locoPillars.game.manager

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.Kloppie74.locoPillars.LocoPillars

class PlayerManager(private val plugin: LocoPillars) {

    fun preparePlayerForGame(player: Player) {
        plugin.logger.info("[PlayerManager] Preparing ${player.name} for game")

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

        plugin.logger.info("[PlayerManager] Player ${player.name} prepared successfully")
    }

    fun restorePlayerState(player: Player) {
        plugin.logger.info("[PlayerManager] Restoring state for ${player.name}")

        player.gameMode = GameMode.SURVIVAL
        player.health = 20.0
        player.foodLevel = 20
        player.saturation = 20f
        player.inventory.clear()

        // Clear potion effects
        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }

        // Give basic items for lobby
        player.inventory.addItem(ItemStack(Material.COOKED_BEEF, 32))

        plugin.logger.info("[PlayerManager] Player state restored for ${player.name}")
    }
}