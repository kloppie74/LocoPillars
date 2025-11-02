package org.Kloppie74.locoPillars.utils.spectator

import org.bukkit.entity.Player
import org.bukkit.GameMode
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.teams.GlobalVisibilityTicker
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object SpectatorUtils {
    private val SPECTATOR_MAP = ConcurrentHashMap<UUID, Boolean>()

    /**
     * Set player as spectator (only if NOT in a game!)
     */
    @JvmStatic
    fun setPlayerAsSpectator(player: Player, plugin: LocoPillars) {
        val inGame = plugin.gameManager.getPlayerInstance(player) != null
        if (inGame) return

        SPECTATOR_MAP[player.uniqueId] = true

        player.gameMode = GameMode.SURVIVAL
        player.allowFlight = true
        player.isFlying = true
        player.isInvulnerable = true
        player.isCollidable = false

        SpectatorSpeedManager.applyDefaultSpeed(player)
        plugin.hotbarItemManager.givePlayerItems(player)

        // FIXED: Use non-conflicting priority for spectator updates
        GlobalVisibilityTicker.queueUpdateWithPriority(player, "SPECTATOR", "set_as_spectator")

        player.sendMessage(MSG.chatColors("&7You are now a &espectator&7."))
    }

    /**
     * Remove spectator status and restore all properties (only if NOT in a game!)
     */
    @JvmStatic
    fun removeSpectatorStatus(player: Player, plugin: LocoPillars) {
        val inGame = plugin.gameManager.getPlayerInstance(player) != null
        if (inGame) return

        SPECTATOR_MAP.remove(player.uniqueId)

        player.gameMode = GameMode.SURVIVAL
        player.allowFlight = false
        player.isFlying = false
        player.isInvulnerable = false
        player.isCollidable = true

        SpectatorSpeedManager.resetSpeed(player)
        plugin.hotbarItemManager.givePlayerItems(player)

        // FIXED: Use non-conflicting priority for spectator updates
        GlobalVisibilityTicker.queueUpdateWithPriority(player, "SPECTATOR", "remove_spectator_status")
    }

    /**
     * Is a player currently a spectator? (NOT in a game and in map)
     */
    @JvmStatic
    fun isSpectator(player: Player?, plugin: LocoPillars): Boolean {
        if (player == null) return false
        val inSpectatorMap = SPECTATOR_MAP[player.uniqueId] == true
        val inGame = plugin.gameManager.getPlayerInstance(player) != null
        return inSpectatorMap && !inGame
    }

    /**
     * FIXED: Handles world change events for spectator logic
     */
    @JvmStatic
    fun handleWorldChange(player: Player, fromWorld: String, toWorld: String, plugin: LocoPillars) {
        val wasSpectator = isSpectator(player, plugin)

        if (!fromWorld.startsWith("pillars_") && toWorld.startsWith("pillars_")) {
            setPlayerAsSpectator(player, plugin)
        } else if (fromWorld.startsWith("pillars_") && !toWorld.startsWith("pillars_")) {
            removeSpectatorStatus(player, plugin)
        } else if (wasSpectator) {
            setPlayerAsSpectator(player, plugin)
        } else {
            plugin.hotbarItemManager.givePlayerItems(player)
            // FIXED: Use non-conflicting priority for world change updates
            GlobalVisibilityTicker.queueUpdateWithPriority(player, "WORLD_CHANGE", "spectator_world_change")
        }
    }

    /**
     * Clear the spectator cache for the specified player
     */
    @JvmStatic
    fun clearCache(player: Player) {
        SPECTATOR_MAP.remove(player.uniqueId)
    }
}