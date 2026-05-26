package org.Kloppie74.locoPillars.utils.teams

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Scoreboard
import org.bukkit.scoreboard.Team
import org.Kloppie74.locoPillars.LocoPillars
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * TeamManager die zorgt dat alle spelers elkaars teams/prefix/suffix zien.
 * FIXED: Tab en sidebar gebruiken VERSCHILLENDE prefixes om conflicten te voorkomen!
 */
class TeamManager(private val plugin: LocoPillars) {

    private val playerScoreboards = ConcurrentHashMap<UUID, Scoreboard>()
    private val playerTeams = ConcurrentHashMap<UUID, MutableSet<String>>()

    companion object {
        const val TAB_TEAM_PREFIX = "tab_"
        const val SIDEBAR_TEAM_PREFIX = "sb_"  // FIXED: Different prefix to avoid conflicts!
        const val MAX_TEAM_NAME_LENGTH = 16
    }

    /**
     * Haal of maak de scoreboard van een speler.
     */
    fun getPlayerScoreboard(player: Player): Scoreboard {
        return playerScoreboards.getOrPut(player.uniqueId) {
            debugLog("Creating scoreboard for ${player.name}")
            val scoreboard = Bukkit.getScoreboardManager().newScoreboard
            player.scoreboard = scoreboard
            scoreboard
        }
    }

    /**
     * Maak 1 team per speler voor TAB (prefix/suffix altijd goed zichtbaar in tab).
     */
    fun createTabTeam(targetPlayer: Player, prefix: String, suffix: String) {
        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (!viewer.isOnline) return@forEach
            try {
                val scoreboard = getPlayerScoreboard(viewer)
                val teamName = generateTeamName(TAB_TEAM_PREFIX, targetPlayer.name)
                val team = getOrCreateTeam(scoreboard, teamName)
                team.prefix(LegacyComponentSerializer.legacySection().deserialize(prefix))
                team.suffix(LegacyComponentSerializer.legacySection().deserialize(suffix))
                if (!team.hasEntry(targetPlayer.name)) team.addEntry(targetPlayer.name)
                trackTeam(viewer.uniqueId, teamName)
                debugLog("Applied tab team for ${targetPlayer.name} to ${viewer.name}'s scoreboard")
            } catch (e: Exception) {
                plugin.logger.warning("[TeamManager] Error creating tab team for ${targetPlayer.name} on ${viewer.name}'s scoreboard: ${e.message}")
            }
        }
    }

    /**
     * FIXED: Sidebar team met UNIEKE prefix die niet conflicteert met tab teams!
     */
    fun createSidebarTeam(player: Player, slotId: Int, entry: String, prefix: String, suffix: String) {
        val scoreboard = getPlayerScoreboard(player)
        // FIXED: Use UNIQUE team name that doesn't conflict with tab teams!
        val teamName = generateTeamName(SIDEBAR_TEAM_PREFIX, "${player.name}_${slotId}")
        val team = getOrCreateTeam(scoreboard, teamName)
        team.prefix(LegacyComponentSerializer.legacySection().deserialize(prefix))
        team.suffix(LegacyComponentSerializer.legacySection().deserialize(suffix))
        if (!team.hasEntry(entry)) {
            team.addEntry(entry)
        }
        trackTeam(player.uniqueId, teamName)
        player.scoreboard = scoreboard
        debugLog("Created UNIQUE sidebar team for ${player.name} slot $slotId entry=$entry with prefix='$prefix' suffix='$suffix'")
    }

    /**
     * FIXED: Sidebar cleanup per entry.
     */
    fun removeSidebarTeam(player: Player, slotId: Int, entry: String) {
        val scoreboard = getPlayerScoreboard(player)
        // FIXED: Use same unique naming pattern!
        val teamName = generateTeamName(SIDEBAR_TEAM_PREFIX, "${player.name}_${slotId}")
        val team = scoreboard.getTeam(teamName)
        if (team != null) {
            if (team.hasEntry(entry)) {
                team.removeEntry(entry)
                debugLog("Removed entry '$entry' from sidebar team $teamName for ${player.name}")
            }
            if (team.entries.isEmpty()) {
                team.unregister()
                untrackTeam(player.uniqueId, teamName)
                debugLog("Unregistered empty sidebar team $teamName for ${player.name}")
            }
        }
    }

    /**
     * FIXED: Create tab team and sidebar teams with DIFFERENT prefixes!
     */
    fun createTabAndSidebarTeam(player: Player, prefix: String, suffix: String, sidebarEntries: List<String>) {
        // Tab: 1 team per spelernaam (voor tab list)
        createTabTeam(player, prefix, suffix)
        // Sidebar: UNIEKE teams per entry (voor scoreboard display)
        sidebarEntries.forEachIndexed { idx, entry ->
            val slotId = idx + 1
            createSidebarTeam(player, slotId, entry, prefix, suffix)
        }
        debugLog("Created SEPARATED tab and sidebar teams for ${player.name} - sidebar entries: ${sidebarEntries.size}")
    }

    /**
     * Cleanup alle teams van type prefix.
     */
    fun cleanupTeamsByType(player: Player, teamPrefix: String) {
        val scoreboard = playerScoreboards[player.uniqueId] ?: return
        cleanupTeamsByPrefix(scoreboard, player.uniqueId, teamPrefix)
        debugLog("Cleaned up teams with prefix '$teamPrefix' for ${player.name}")
    }

    /**
     * Cleanup alle teams voor een speler: tab + sidebar.
     */
    fun cleanupPlayer(player: Player) {
        val playerUUID = player.uniqueId
        val scoreboard = playerScoreboards[playerUUID]
        if (scoreboard != null) {
            val teams = playerTeams[playerUUID]?.toList() ?: emptyList()
            teams.forEach { teamName ->
                try {
                    scoreboard.getTeam(teamName)?.unregister()
                } catch (e: Exception) {
                    debugLog("Error unregistering team $teamName: ${e.message}")
                }
            }
            debugLog("Cleaned up ${teams.size} teams for ${player.name}")
        }
        cleanupTabTeamForPlayer(player)
        playerTeams.remove(playerUUID)
        playerScoreboards.remove(playerUUID)
    }

    fun forceRefreshAllTeams() {
        debugLog("Force refreshing all team assignments")
        playerTeams.clear()
        playerScoreboards.clear()
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.isOnline) {
                getPlayerScoreboard(player)
            }
        }
        updateAllTabTeams()
        if (plugin.scoreboardManager.isEnabled()) {
            plugin.scoreboardManager.forceUpdateAllPlayers()
        }
    }

    fun updateAllTabTeams() {
        if (!plugin.tabManager.isEnabled()) return
        debugLog("Updating all tab teams for cross-player visibility")
        Bukkit.getOnlinePlayers().forEach { player ->
            if (player.isOnline) {
                plugin.tabManager.updatePlayerTab(player)
            }
        }
    }

    fun cleanupTabTeamForPlayer(targetPlayer: Player) {
        debugLog("Cleaning up tab teams for ${targetPlayer.name}")
        Bukkit.getOnlinePlayers().forEach { viewer ->
            if (!viewer.isOnline || viewer == targetPlayer) return@forEach
            try {
                val scoreboard = playerScoreboards[viewer.uniqueId] ?: return@forEach
                val teamName = generateTeamName(TAB_TEAM_PREFIX, targetPlayer.name)
                val team = scoreboard.getTeam(teamName)
                if (team != null) {
                    team.unregister()
                    untrackTeam(viewer.uniqueId, teamName)
                }
            } catch (e: Exception) {
                debugLog("Error cleaning up tab team for ${targetPlayer.name} from ${viewer.name}: ${e.message}")
            }
        }
    }

    fun getStats(): Map<String, Any> {
        val totalPlayers = playerScoreboards.size
        val totalTeams = playerTeams.values.sumOf { it.size }
        var tabTeams = 0
        var sidebarTeams = 0

        // Count teams by type
        playerTeams.values.forEach { teamSet ->
            teamSet.forEach { teamName ->
                when {
                    teamName.startsWith(TAB_TEAM_PREFIX) -> tabTeams++
                    teamName.startsWith(SIDEBAR_TEAM_PREFIX) -> sidebarTeams++
                }
            }
        }

        val activeScoreboards = playerScoreboards.size
        val playersWithTeams = playerTeams.keys.size

        debugLog("FIXED Stats: totalPlayers=$totalPlayers, totalTeams=$totalTeams, tabTeams=$tabTeams, sidebarTeams=$sidebarTeams")
        debugLog("Stats: activeScoreboards=$activeScoreboards, playersWithTeams=$playersWithTeams")

        // Debug team details
        playerTeams.forEach { (playerUUID, teams) ->
            val playerName = Bukkit.getPlayer(playerUUID)?.name ?: "Unknown"
            val tabCount = teams.count { it.startsWith(TAB_TEAM_PREFIX) }
            val sideCount = teams.count { it.startsWith(SIDEBAR_TEAM_PREFIX) }
            debugLog("Player $playerName: $tabCount tab teams, $sideCount SEPARATED sidebar teams")
        }

        return mapOf(
            "totalPlayers" to totalPlayers,
            "totalTeams" to totalTeams,
            "tabTeams" to tabTeams,
            "sidebarTeams" to sidebarTeams,
            "onlinePlayers" to Bukkit.getOnlinePlayers().size,
            "activeScoreboards" to activeScoreboards,
            "playersWithTeams" to playersWithTeams,
            "conflictsSeparated" to true // Indicates conflicts are fixed
        )
    }

    private fun generateTeamName(prefix: String, identifier: String): String {
        val fullName = prefix + identifier.replace("-", "").lowercase()
        return if (fullName.length > MAX_TEAM_NAME_LENGTH) {
            fullName.substring(0, MAX_TEAM_NAME_LENGTH)
        } else {
            fullName
        }
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
            try {
                scoreboard.getTeam(teamName)?.unregister()
            } catch (e: Exception) {
                debugLog("Error removing team $teamName: ${e.message}")
            }
            untrackTeam(playerUUID, teamName)
        }
    }

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[TeamManager] $message")
        }
    }
}