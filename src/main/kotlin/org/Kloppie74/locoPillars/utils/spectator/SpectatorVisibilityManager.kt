package org.Kloppie74.locoPillars.utils.spectator

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.vanish.VanishManager
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object SpectatorVisibilityManager {
    private const val SPECTATOR_HIDING_KEY = "hiding_spectators"
    private val VISIBILITY_CACHE = ConcurrentHashMap<UUID, Boolean>()

    /**
     * Toggle spectator visibility for other players (lobby etc.)
     */
    @JvmStatic
    fun toggleSpectatorsVisibility(player: Player, plugin: LocoPillars) {
        val instance = plugin.gameManager.getPlayerInstance(player)
        val lobbyWorld = plugin.configManager.lobbyWorld

        if (instance != null) return // In a game
        if (player.world.name.equals(lobbyWorld, ignoreCase = true)) return // In lobby

        val currentlyHiding = isHidingSpectators(player)
        val newState = !currentlyHiding

        setHidingSpectators(player, newState)
        updateVisibilityForAllPlayers(player, newState, plugin)

        val message = if (newState) {
            "&7Spectators are now &chidden&7."
        } else {
            "&7Spectators are now &avisible&7."
        }
        player.sendMessage(MSG.chatColors(message))
    }

    /**
     * Update visibility for spectators (not in game)
     */
    @JvmStatic
    fun updateSpectatorVisibility(spectator: Player, plugin: LocoPillars) {
        for (otherPlayer in spectator.world.players) {
            if (otherPlayer == spectator) continue

            val otherInstance = plugin.gameManager.getPlayerInstance(otherPlayer)

            if (otherInstance != null) {
                // Other player is in a game - hide vanished players only!
                if (VanishManager.isVanished(spectator)) {
                    otherPlayer.hidePlayer(plugin, spectator)
                } else {
                    otherPlayer.showPlayer(plugin, spectator)
                }
            } else {
                // Both are spectators/lobby - respect hiding preferences and vanish
                if (VanishManager.isVanished(spectator)) {
                    otherPlayer.hidePlayer(plugin, spectator)
                } else {
                    val isHidingSpectators = isHidingSpectators(otherPlayer)
                    if (isHidingSpectators) {
                        otherPlayer.hidePlayer(plugin, spectator)
                    } else {
                        otherPlayer.showPlayer(plugin, spectator)
                    }
                }
            }
        }
    }

    /**
     * Restore all visibility for a player (not spectating anymore)
     */
    @JvmStatic
    fun restoreNormalVisibility(player: Player, plugin: LocoPillars) {
        for (otherPlayer in player.world.players) {
            if (otherPlayer != player) {
                otherPlayer.showPlayer(plugin, player)
                player.showPlayer(plugin, otherPlayer)
            }
        }
        // Clear cache
        VISIBILITY_CACHE.remove(player.uniqueId)
    }

    /**
     * Updates visibility in game instances (alive vs dead players)
     * -- Always display all alive/dead players to each other,
     * -- except for vanished players (never shown)
     * -- ShowOthers/hideOthers is IGNORED in game instance!
     */
    @JvmStatic
    fun updateVisibilityForGameInstance(instance: org.Kloppie74.locoPillars.game.instance.GameInstance, plugin: LocoPillars) {
        val allPlayers = instance.players.mapNotNull { Bukkit.getPlayer(it) }
        // Only players that are NOT vanished participate in game visibility
        val visiblePlayers = allPlayers.filter { !VanishManager.isVanished(it) }
        val deadPlayers = visiblePlayers.filter { SpectatorUtils.isSpectator(it, plugin) }
        val alivePlayers = visiblePlayers.filter { !SpectatorUtils.isSpectator(it, plugin) }

        // Hide dead players from alive players (but only if not vanished)
        for (alive in alivePlayers) {
            for (dead in deadPlayers) {
                alive.showPlayer(plugin, dead)
            }
        }

        // Show alive players to each other
        for (alive in alivePlayers) {
            for (otherAlive in alivePlayers) {
                if (alive != otherAlive) {
                    alive.showPlayer(plugin, otherAlive)
                }
            }
        }

        // Spectators see all non-vanished players
        for (dead in deadPlayers) {
            for (p in visiblePlayers) {
                if (dead != p) {
                    dead.showPlayer(plugin, p)
                }
            }
        }

        // Hide vanished players from everyone
        val vanishedPlayers = allPlayers.filter { VanishManager.isVanished(it) }
        for (p in allPlayers) {
            for (vanished in vanishedPlayers) {
                p.hidePlayer(plugin, vanished)
            }
        }
    }

    /**
     * Update visibility for all players in world (lobby/spectator)
     */
    private fun updateVisibilityForAllPlayers(player: Player, hideSpectators: Boolean, plugin: LocoPillars) {
        for (otherPlayer in player.world.players) {
            if (otherPlayer == player) continue
            // Always hide vanished players
            if (VanishManager.isVanished(otherPlayer)) {
                player.hidePlayer(plugin, otherPlayer)
                continue
            }

            val otherInstance = plugin.gameManager.getPlayerInstance(otherPlayer)
            if (otherInstance == null) { // Other player is spectating/lobby
                if (hideSpectators) {
                    player.hidePlayer(plugin, otherPlayer)
                } else {
                    player.showPlayer(plugin, otherPlayer)
                }
            } else {
                player.showPlayer(plugin, otherPlayer)
            }
        }
    }

    /**
     * Sets spectator visibility for specific player lists
     */
    @JvmStatic
    fun setSpectatorVisibility(target: Player, hideFrom: List<Player>, showTo: List<Player>, plugin: LocoPillars) {
        hideFrom.forEach { it.hidePlayer(plugin, target) }
        showTo.forEach { it.showPlayer(plugin, target) }
    }

    /**
     * Checks if a player is hiding spectators
     */
    @JvmStatic
    fun isHidingSpectators(player: Player): Boolean {
        // Check cache first
        VISIBILITY_CACHE[player.uniqueId]?.let { return it }

        // Check metadata
        val hiding = if (player.hasMetadata(SPECTATOR_HIDING_KEY)) {
            player.getMetadata(SPECTATOR_HIDING_KEY)[0].asBoolean()
        } else {
            false
        }

        // Cache result
        VISIBILITY_CACHE[player.uniqueId] = hiding
        return hiding
    }

    /**
     * Sets hiding spectators state for a player
     */
    @JvmStatic
    fun setHidingSpectators(player: Player, hiding: Boolean) {
        player.setMetadata(
            SPECTATOR_HIDING_KEY,
            FixedMetadataValue(Bukkit.getPluginManager().getPlugin("LocoPillars")!!, hiding)
        )
        VISIBILITY_CACHE[player.uniqueId] = hiding
    }

    /**
     * Clear visibility cache
     */
    @JvmStatic
    fun clearCache() {
        VISIBILITY_CACHE.clear()
    }
}