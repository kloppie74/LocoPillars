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
        // Editor players are NEVER spectators — they build freely in their edit world.
        if (plugin.adminEditorManager.isActive(player)) return
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
        GlobalVisibilityTicker.queueUpdateWithPriority(player, "SPECTATOR_UPDATE", "set_as_spectator")

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
        GlobalVisibilityTicker.queueUpdateWithPriority(player, "SPECTATOR_UPDATE", "remove_spectator_status")
    }

    /**
     * Is a player currently a spectator? (NOT in a game and in map)
     */
    @JvmStatic
    fun isSpectator(player: Player?, plugin: LocoPillars): Boolean {
        if (player == null) return false
        // Editor players are never spectators, no matter what world they are in.
        if (plugin.adminEditorManager.isActive(player)) return false
        val inSpectatorMap = SPECTATOR_MAP[player.uniqueId] == true
        val inGame = plugin.gameManager.getPlayerInstance(player) != null
        return inSpectatorMap && !inGame
    }

    /**
     * Is a player flagged as spectating, REGARDLESS of whether they are still in a
     * game instance? Returns true for eliminated/dead-in-game spectators too.
     *
     * isSpectator() vereist !inGame, maar een speler die mid-game wordt uitgeschakeld
     * blijft tot het einde van de game in instance.players staan. Voor zichtbaarheid
     * (spectators verbergen voor levende spelers) moeten we ze tóch als spectator
     * herkennen — daarvoor is deze methode.
     */
    @JvmStatic
    fun isSpectating(player: Player?, plugin: LocoPillars): Boolean {
        if (player == null) return false
        if (plugin.adminEditorManager.isActive(player)) return false
        return SPECTATOR_MAP[player.uniqueId] == true
    }

    /**
     * FIXED: Handles world change events for spectator logic
     */
    @JvmStatic
    fun handleWorldChange(player: Player, fromWorld: String, toWorld: String, plugin: LocoPillars) {
        // Editor players manage their own state — don't run any spectator transitions for them.
        if (plugin.adminEditorManager.isActive(player)) return
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
            GlobalVisibilityTicker.queueUpdateWithPriority(player, "SPECTATOR_UPDATE", "spectator_world_change")
        }
    }

    /**
     * Mark a player as a spectator in the map only — no property changes.
     * Used when a player dies mid-game so visibility systems treat them as spectator
     * before they have actually respawned.
     */
    @JvmStatic
    fun markAsEliminatedSpectator(player: Player) {
        SPECTATOR_MAP[player.uniqueId] = true
    }

    /**
     * Clear the spectator cache for the specified player
     */
    @JvmStatic
    fun clearCache(player: Player) {
        SPECTATOR_MAP.remove(player.uniqueId)
    }

    @JvmStatic
    fun ensurePlayerAlive(player: Player, plugin: LocoPillars, context: String): Boolean {
        if (!player.isDead) return true
        return try {
            player.spigot().respawn()
            if (player.isDead) {
                plugin.logger.warning("[$context] Failed to force respawn ${player.name}: player still dead")
                false
            } else {
                true
            }
        } catch (ex: Exception) {
            plugin.logger.warning("[$context] Failed to force respawn ${player.name}: ${ex.message}")
            false
        }
    }
}
