package org.Kloppie74.locoPillars.utils.teams.scoreboard

import me.neznamy.tab.api.TabAPI
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.ScoreboardManager
import java.util.concurrent.ConcurrentHashMap

object DisplayNameAboveHead {
    private val playerScoreboards = ConcurrentHashMap<String, Scoreboard>(64, 0.75f, 2)
    private val isTabEnabled = Bukkit.getPluginManager().isPluginEnabled("TAB")

    fun setNick(player: Player?, nick: String?) {
        if (player == null) return
        val playerName = player.name
        if (isTabEnabled) {
            updateTabNameTag(playerName, nick, null)
        } else {
            updateVanillaNameTag(player, nick ?: "", "")
        }
    }

    fun removeNick(player: Player?) {
        if (player == null) return
        val playerName = player.name
        if (isTabEnabled) {
            updateTabNameTag(playerName, null, null)
        } else {
            updateVanillaNameTag(player, "", "")
        }
    }

    private fun updateTabNameTag(playerName: String, prefix: String?, suffix: String?) {
        val api = TabAPI.getInstance()
        val nameTagManager = api.nameTagManager ?: return
        val tabPlayer = api.getPlayer(playerName) ?: return
        nameTagManager.setPrefix(tabPlayer, prefix)
        nameTagManager.setSuffix(tabPlayer, suffix)
    }

    private fun updateVanillaNameTag(player: Player, prefix: String?, suffix: String?) {
        val manager = Bukkit.getScoreboardManager()
        val playerName = player.name
        Bukkit.getOnlinePlayers().forEach { online ->
            if (!online.canSee(player)) return@forEach
            val scoreboard = getPlayerScoreboard(online, manager)
            var team = scoreboard.getTeam(playerName)
            if (team == null) {
                team = scoreboard.registerNewTeam(playerName)
            }
            team.prefix(LegacyComponentSerializer.legacySection().deserialize(prefix ?: ""))
            team.suffix(LegacyComponentSerializer.legacySection().deserialize(suffix ?: ""))
            if (!team.hasEntry(playerName)) {
                team.addEntry(playerName)
            }
            online.scoreboard = scoreboard
        }
    }

    private fun getPlayerScoreboard(player: Player, manager: ScoreboardManager): Scoreboard {
        val uuidString = player.uniqueId.toString()
        val scoreboard = playerScoreboards[uuidString]
        return if (scoreboard == null || scoreboard == manager.mainScoreboard) {
            val newBoard = manager.newScoreboard
            playerScoreboards[uuidString] = newBoard
            newBoard
        } else {
            scoreboard
        }
    }

    fun cleanupPlayerScoreboard(playerUUID: String) {
        playerScoreboards.remove(playerUUID)
    }
}