package org.Kloppie74.locoPillars.worldregion

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.MSG

/**
 * Enforces arena XZ boundaries for players in active game instances.
 *
 * Replaces WorldGuard's EXIT = DENY flag with a pure-Bukkit implementation.
 *
 * Rules:
 *  • Only applies when [getPlayerInstance] returns a non-null game instance.
 *  • Skipped during [GameState.ENDED] / [GameState.RESETTING] so that
 *    the plugin can freely teleport players back to the lobby.
 *  • [PlayerMoveEvent] — cancels movement that would cross the XZ boundary.
 *    Optimised: only runs when the player actually crosses a block boundary on X or Z.
 *  • [PlayerTeleportEvent] — only intercepts player-initiated teleports
 *    (ENDER_PEARL, CHORUS_FRUIT). Plugin-triggered teleports (cause = PLUGIN)
 *    are always allowed so that leave/respawn/spectator flows work correctly.
 */
class WorldRegionListener(private val plugin: LocoPillars) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val to = event.to ?: return

        // Optimisation: skip if still in the same XZ block
        val from = event.from
        if (from.blockX == to.blockX && from.blockZ == to.blockZ) return

        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player) ?: return

        // Don't block movement once the game is ending — we need to teleport players out
        val state = instance.state
        if (state == GameState.ENDED || state == GameState.RESETTING) return

        val region = plugin.worldRegionManager.getRegion(instance.arenaInstance.regionId) ?: return

        if (!region.containsXZ(to)) {
            event.isCancelled = true
            player.sendMessage(MSG.chatColors("&cYou cannot leave the arena!"))
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        // Only intercept player-initiated teleport types that could bypass the boundary.
        // PLUGIN cause is explicitly excluded so that game leave / spectator teleports work.
        val cause = event.cause
        if (cause != PlayerTeleportEvent.TeleportCause.ENDER_PEARL &&
            cause != PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) return

        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player) ?: return

        val state = instance.state
        if (state == GameState.ENDED || state == GameState.RESETTING) return

        val region = plugin.worldRegionManager.getRegion(instance.arenaInstance.regionId) ?: return
        val to = event.to ?: return

        if (!region.containsXZ(to)) {
            event.isCancelled = true
            player.sendMessage(MSG.chatColors("&cYou cannot leave the arena!"))
        }
    }
}
