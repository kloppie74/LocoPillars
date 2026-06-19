package org.Kloppie74.locoPillars.worldregion

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils

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
        val from = event.from
        val player = event.player

        // Resolve the arena this player belongs to — either as an active participant
        // or as a spectator watching it. handlePlayerDeath() removes eliminated players
        // from playerToInstance but keeps them in the instance's spectators list, so a
        // null active-instance + spectator flag reliably identifies a spectator.
        val activeInstance = plugin.gameManager.getPlayerInstance(player)
        val instance = activeInstance
            ?: (if (SpectatorUtils.isSpectating(player, plugin)) plugin.gameManager.getSpectatorInstance(player) else null)
            ?: return

        val state = instance.state
        if (state == GameState.ENDED || state == GameState.RESETTING) return

        val isSpectator = activeInstance == null

        // ── VOID RECOVERY (spectators only) ──
        // Active players who drop below DeathHeight are eliminated by GameListener.
        // Spectators are invulnerable + flying, so without this they would fall into
        // the void forever. Teleport them back to the arena spawn instead.
        // Checked BEFORE the XZ-block optimisation below, since a straight-down fall
        // keeps the same X/Z block and would otherwise be skipped.
        val deathHeight = instance.arenaInstance.config.getDeathHeight()
        if (isSpectator && to.y <= deathHeight) {
            val safe = instance.arenaInstance.config.getSpawnTeleportLocation(instance.arenaInstance.origin)
            // Guard against a teleport loop if the spawn is (mis)configured at/below the void line.
            if (safe.y <= deathHeight) safe.y = deathHeight + 5.0
            // Preserve the spectator's facing so the teleport isn't disorienting.
            safe.yaw = player.location.yaw
            safe.pitch = player.location.pitch
            player.teleport(safe)
            return
        }

        // ── XZ BOUNDARY ENFORCEMENT ──
        // Optimisation: only check when the player actually crosses a block boundary on X or Z.
        if (from.blockX == to.blockX && from.blockZ == to.blockZ) return
        val region = plugin.worldRegionManager.getRegion(instance.arenaInstance.regionId) ?: return
        if (!region.containsXZ(to)) {
            event.isCancelled = true
            // Active players get a notice; spectators are bounced silently.
            if (!isSpectator) player.sendMessage(MSG.chatColors("&cYou cannot leave the arena!"))
        }
    }

    @Suppress("DEPRECATION")
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
        val to = event.to

        if (!region.containsXZ(to)) {
            event.isCancelled = true
            player.sendMessage(MSG.chatColors("&cYou cannot leave the arena!"))
        }
    }
}
