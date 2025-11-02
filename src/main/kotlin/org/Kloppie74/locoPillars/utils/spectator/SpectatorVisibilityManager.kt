package org.Kloppie74.locoPillars.utils.spectator

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.metadata.FixedMetadataValue
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.teams.GlobalVisibilityTicker
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object SpectatorVisibilityManager {
    private const val SPECTATOR_HIDING_KEY = "hiding_spectators"
    private val VISIBILITY_CACHE = ConcurrentHashMap<UUID, Boolean>()

    /**
     * FIXED: Toggle spectator visibility for other players (lobby etc.)
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

        // FIXED: Use non-conflicting priority for spectator visibility updates
        GlobalVisibilityTicker.queueUpdateWithPriority(player, "SPECTATOR", "toggle_spectator_visibility")

        val message = if (newState) {
            "&7Spectators are now &chidden&7."
        } else {
            "&7Spectators are now &avisible&7."
        }
        player.sendMessage(MSG.chatColors(message))
    }

    /**
     * FIXED: Update visibility for spectators (not in game)
     */
    @JvmStatic
    fun updateSpectatorVisibility(spectator: Player, plugin: LocoPillars) {
        GlobalVisibilityTicker.queueUpdateWithPriority(spectator, "SPECTATOR", "update_spectator_visibility")
    }

    /**
     * FIXED: Restore all visibility for a player (not spectating anymore)
     */
    @JvmStatic
    fun restoreNormalVisibility(player: Player, plugin: LocoPillars) {
        VISIBILITY_CACHE.remove(player.uniqueId)
        GlobalVisibilityTicker.queueUpdateWithPriority(player, "SPECTATOR", "restore_normal_visibility")
    }

    /**
     * FIXED: Updates visibility in game instances
     */
    @JvmStatic
    fun updateVisibilityForGameInstance(instance: org.Kloppie74.locoPillars.game.instance.GameInstance, plugin: LocoPillars) {
        val allPlayers = instance.players.mapNotNull { Bukkit.getPlayer(it) }

        // FIXED: Queue updates with proper priority for all players in the instance
        allPlayers.forEach { player ->
            GlobalVisibilityTicker.queueUpdateWithPriority(player, "SPECTATOR", "game_instance_update")
        }
    }

    /**
     * FIXED: Sets spectator visibility for specific player lists
     */
    @JvmStatic
    fun setSpectatorVisibility(target: Player, hideFrom: List<Player>, showTo: List<Player>, plugin: LocoPillars) {
        // FIXED: Queue updates for all affected players with proper priority
        GlobalVisibilityTicker.queueUpdateWithPriority(target, "SPECTATOR", "set_spectator_visibility_target")
        (hideFrom + showTo).forEach { player ->
            GlobalVisibilityTicker.queueUpdateWithPriority(player, "SPECTATOR", "set_spectator_visibility_viewer")
        }
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
     * FIXED: Sets hiding spectators state for a player
     */
    @JvmStatic
    fun setHidingSpectators(player: Player, hiding: Boolean) {
        player.setMetadata(
            SPECTATOR_HIDING_KEY,
            FixedMetadataValue(Bukkit.getPluginManager().getPlugin("LocoPillars")!!, hiding)
        )
        VISIBILITY_CACHE[player.uniqueId] = hiding

        // FIXED: Queue visibility update with proper priority
        GlobalVisibilityTicker.queueUpdateWithPriority(player, "SPECTATOR", "set_hiding_spectators")
    }

    /**
     * Clear visibility cache
     */
    @JvmStatic
    fun clearCache() {
        VISIBILITY_CACHE.clear()
    }
}