package org.Kloppie74.locoPillars.utils.vanish

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.*
import org.bukkit.boss.BossBar
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.Kloppie74.locoPillars.LocoPillars
import java.util.*

/**
 * Handles vanish/hideMe/hideOthers bossbar display and visibility.
 * Always displays max 1 bossbar if any of the states are active.
 * The bossbar is live updated on all relevant events and state changes.
 */
class VanishListener(private val plugin: LocoPillars) : Listener {

    private val bossBars: MutableMap<UUID, BossBar> = mutableMapOf()

    // ===== PLAYER EVENTS =====

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        VanishManager.loadState(event.player)
        updateVisibility(event.player)
        updateBossBar(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        VanishManager.saveState(event.player)
        VanishManager.clearCache(event.player)
        removeBossBar(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        updateVisibility(event.player)
        updateBossBar(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        updateVisibility(event.player)
        updateBossBar(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerGameModeChange(event: PlayerGameModeChangeEvent) {
        updateVisibility(event.player)
        updateBossBar(event.player)
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        updateVisibility(event.player)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            updateBossBar(event.player)
        }, 5L)
    }

    // ===== VISIBILITY AND BOSSBAR =====

    /**
     * Handles Bukkit visibility and flight for the player based on vanish state.
     */
    private fun updateVisibility(player: Player) {
        val state = VanishManager.getState(player)
        if (state.vanished) {
            for (other in Bukkit.getOnlinePlayers()) {
                if (other != player && !other.hasPermission("locopillars.vanish.seeothers")) {
                    other.hidePlayer(plugin, player)
                }
            }
            player.allowFlight = true
            player.isFlying = true
        } else {
            for (other in Bukkit.getOnlinePlayers()) {
                other.showPlayer(plugin, player)
            }
            if (player.gameMode.name != "CREATIVE" && player.gameMode.name != "SPECTATOR") {
                player.allowFlight = false
                player.isFlying = false
            }
        }
    }

    /**
     * Displays a bossbar if the player is vanished, hidden, or hiding others.
     * Only one bossbar, all info in one line.
     */
    fun updateBossBar(player: Player) {
        val state = VanishManager.getState(player)
        val showBar = state.vanished || state.hideMe || state.hideOthers

        // Remove bar if not needed
        if (!showBar) {
            removeBossBar(player)
            return
        }

        val bossBar = bossBars.getOrPut(player.uniqueId) {
            Bukkit.createBossBar("LocoPillars Visibility", BarColor.BLUE, BarStyle.SOLID)
        }

        val textParts = mutableListOf<String>()
        var color = BarColor.GREEN
        var progress = 1.0

        if (state.vanished) {
            textParts.add("§c§lVanished")
            color = BarColor.RED
            progress = 1.0
        }
        if (state.hideMe) {
            textParts.add("§e§lHidden from others")
            if (!state.vanished) {
                color = BarColor.YELLOW
                progress = 0.7
            }
        }
        if (state.hideOthers) {
            textParts.add("§6§lHiding other players")
            if (!state.vanished && !state.hideMe) {
                color = BarColor.BLUE
                progress = 0.5
            }
        }

        val title = textParts.joinToString(" §8| ")
        bossBar.setTitle(title)
        bossBar.setColor(color)
        bossBar.setProgress(progress)

        bossBar.removePlayer(player)
        bossBar.addPlayer(player)
    }

    /**
     * Removes bossbar for a player.
     */
    fun removeBossBar(player: Player) {
        bossBars[player.uniqueId]?.removePlayer(player)
        bossBars.remove(player.uniqueId)
    }

    /**
     * Utility: update bossbar for all online players.
     */
    fun updateAllBossBars() {
        Bukkit.getOnlinePlayers().forEach { updateBossBar(it) }
    }
}