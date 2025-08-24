package org.Kloppie74.locoPillars.utils.scoreboards

import org.bukkit.Bukkit
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.Kloppie74.locoPillars.LocoPillars

class NewScoreboard(
    private val plugin: LocoPillars,
    private val scoreboardManager: ScoreboardManager
) : Listener {

    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            scoreboardManager.updateScoreboard(event.player)
        }, 10L)
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        scoreboardManager.clearScore(event.player)
    }

    fun updateAllScoreboards() {
        scoreboardManager.updateAllScoreboards()
    }
}