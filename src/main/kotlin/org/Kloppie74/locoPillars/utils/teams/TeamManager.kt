package org.Kloppie74.locoPillars.utils.teams

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import org.Kloppie74.locoPillars.LocoPillars
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * TeamManager — beheert scoreboard-teams uitsluitend voor de TAB-lijst.
 *
 * Sidebar-teams zijn verwijderd. ScoreboardManager gebruikt nu Paper 1.21's
 * Score.customName(Component) API, waardoor sidebar-teams volledig overbodig zijn.
 * TeamManager beheert alleen nog "tab_"-teams voor de tab-lijst prefix/suffix en
 * de sorteervolgorde.
 */
class TeamManager(private val plugin: LocoPillars) {

    private val playerScoreboards = ConcurrentHashMap<UUID, Scoreboard>()
    private val playerTeams       = ConcurrentHashMap<UUID, MutableSet<String>>()

    companion object {
        const val TAB_TEAM_PREFIX     = "tab_"
        const val MAX_TEAM_NAME_LENGTH = 16
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scoreboard management
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Haal de scoreboard van een speler op (of maak een nieuwe).
     * Wordt door zowel TeamManager (tab-teams) als ScoreboardManager (sidebar objective) gebruikt.
     */
    fun getPlayerScoreboard(player: Player): Scoreboard {
        return playerScoreboards.getOrPut(player.uniqueId) {
            debugLog("Creating scoreboard for ${player.name}")
            val scoreboard = Bukkit.getScoreboardManager().newScoreboard
            player.scoreboard = scoreboard
            scoreboard
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Tab-team management
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Maak of update het tab-team voor [targetPlayer] op elk online-speler scoreboard.
     *
     * @param sortIndex  Positie in de sorteer-volgorde (0 = hoogste rang, 99 = laagste).
     *   De team-naam wordt "tab_<00-99>_<naam>". Minecraft sorteert de tab-lijst
     *   alfabetisch op team-naam, dus een lager cijfer = hoger in de lijst.
     */
    fun createTabTeam(targetPlayer: Player, prefix: String, suffix: String, sortIndex: Int = 99) {
        val sortPrefix = sortIndex.coerceIn(0, 99).toString().padStart(2, '0')
        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (!viewer.isOnline) return@forEach
            try {
                val scoreboard = getPlayerScoreboard(viewer)

                // Verwijder speler uit eventuele bestaande tab-teams (rang-wijziging)
                scoreboard.teams
                    .filter { it.name.startsWith(TAB_TEAM_PREFIX) && it.hasEntry(targetPlayer.name) }
                    .forEach { it.removeEntry(targetPlayer.name) }

                val teamName = generateTeamName(TAB_TEAM_PREFIX, "${sortPrefix}_${targetPlayer.name}")
                val team     = getOrCreateTeam(scoreboard, teamName)
                team.prefix(LegacyComponentSerializer.legacySection().deserialize(prefix))
                team.suffix(LegacyComponentSerializer.legacySection().deserialize(suffix))
                if (!team.hasEntry(targetPlayer.name)) team.addEntry(targetPlayer.name)
                trackTeam(viewer.uniqueId, teamName)
                debugLog("Tab team for ${targetPlayer.name} (sort=$sortPrefix) on ${viewer.name}'s scoreboard")
            } catch (e: Exception) {
                plugin.logger.warning("[TeamManager] Error creating tab team for ${targetPlayer.name} on ${viewer.name}: ${e.message}")
            }
        }
    }

    /**
     * Ruim de tab-teams voor [targetPlayer] op van elk ander speler-scoreboard.
     * Zoekt op entry (niet op naam) zodat elk sort-prefix formaat werkt.
     */
    fun cleanupTabTeamForPlayer(targetPlayer: Player) {
        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (!viewer.isOnline || viewer == targetPlayer) return@forEach
            try {
                val scoreboard = playerScoreboards[viewer.uniqueId] ?: return@forEach
                scoreboard.teams
                    .filter { it.name.startsWith(TAB_TEAM_PREFIX) && it.hasEntry(targetPlayer.name) }
                    .forEach { team ->
                        team.unregister()
                        untrackTeam(viewer.uniqueId, team.name)
                    }
            } catch (e: Exception) {
                debugLog("Error cleaning tab team for ${targetPlayer.name} from ${viewer.name}: ${e.message}")
            }
        }
    }

    /**
     * Ruim alle teams van het opgegeven type (prefix) op voor één speler.
     */
    fun cleanupTeamsByType(player: Player, teamPrefix: String) {
        val scoreboard = playerScoreboards[player.uniqueId] ?: return
        cleanupTeamsByPrefix(scoreboard, player.uniqueId, teamPrefix)
        debugLog("Cleaned up teams with prefix '$teamPrefix' for ${player.name}")
    }

    /**
     * Ruim ALLE teams en scoreboards voor een speler op (bij disconnect).
     */
    fun cleanupPlayer(player: Player) {
        val playerUUID = player.uniqueId
        val scoreboard = playerScoreboards[playerUUID]
        if (scoreboard != null) {
            val teams = playerTeams[playerUUID]?.toList() ?: emptyList()
            teams.forEach { teamName ->
                try { scoreboard.getTeam(teamName)?.unregister() } catch (e: Exception) {
                    debugLog("Error unregistering team $teamName: ${e.message}")
                }
            }
            debugLog("Cleaned up ${teams.size} tab teams for ${player.name}")
        }
        cleanupTabTeamForPlayer(player)
        playerTeams.remove(playerUUID)
        playerScoreboards.remove(playerUUID)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Bulk updates
    // ──────────────────────────────────────────────────────────────────────

    fun forceRefreshAllTeams() {
        debugLog("Force refreshing all team assignments")
        playerTeams.clear()
        playerScoreboards.clear()
        Bukkit.getOnlinePlayers().forEach { if (it.isOnline) getPlayerScoreboard(it) }
        updateAllTabTeams()
        if (plugin.scoreboardManager.isEnabled()) {
            plugin.scoreboardManager.forceUpdateAllPlayers()
        }
    }

    fun updateAllTabTeams() {
        if (!plugin.tabManager.isEnabled()) return
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.isOnline) plugin.tabManager.updatePlayerTab(player)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Stats
    // ──────────────────────────────────────────────────────────────────────

    fun getStats(): Map<String, Any> {
        val totalTeams = playerTeams.values.sumOf { it.size }
        val tabTeams   = playerTeams.values.sumOf { set -> set.count { it.startsWith(TAB_TEAM_PREFIX) } }
        return mapOf(
            "totalPlayers"       to playerScoreboards.size,
            "totalTeams"         to totalTeams,
            "tabTeams"           to tabTeams,
            "sidebarTeams"       to 0,  // removed — ScoreboardManager uses Score.customName()
            "onlinePlayers"      to Bukkit.getOnlinePlayers().size,
            "activeScoreboards"  to playerScoreboards.size,
            "playersWithTeams"   to playerTeams.keys.size
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────────────

    private fun generateTeamName(prefix: String, identifier: String): String {
        val fullName = prefix + identifier.replace("-", "").lowercase()
        return if (fullName.length > MAX_TEAM_NAME_LENGTH) fullName.substring(0, MAX_TEAM_NAME_LENGTH)
               else fullName
    }

    private fun getOrCreateTeam(scoreboard: Scoreboard, teamName: String): Team {
        return scoreboard.getTeam(teamName) ?: scoreboard.registerNewTeam(teamName)
    }

    private fun trackTeam(playerUUID: UUID, teamName: String) {
        playerTeams.getOrPut(playerUUID) { ConcurrentHashMap.newKeySet() }.add(teamName)
    }

    private fun untrackTeam(playerUUID: UUID, teamName: String) {
        playerTeams[playerUUID]?.remove(teamName)
    }

    private fun cleanupTeamsByPrefix(scoreboard: Scoreboard, playerUUID: UUID, prefix: String) {
        val teams = playerTeams[playerUUID]?.filter { it.startsWith(prefix) } ?: return
        teams.forEach { teamName ->
            try { scoreboard.getTeam(teamName)?.unregister() } catch (e: Exception) {
                debugLog("Error removing team $teamName: ${e.message}")
            }
            untrackTeam(playerUUID, teamName)
        }
    }

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) plugin.logger.info("[TeamManager] $message")
    }
}
