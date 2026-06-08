package org.Kloppie74.locoPillars.utils.teams.tab

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.utils.teams.GlobalVisibilityTicker

/**
 * Tab-list zichtbaarheid.
 *
 * BELANGRIJK — architectuurwijziging:
 * Deze klasse stuurde vroeger rauwe PLAYER_INFO_REMOVE / PLAYER_INFO_UPDATE packets om
 * spelers uit de tab-lijst te filteren. Dat veroorzaakte een ernstige bug: in MC 1.20+
 * heeft de client de player-info entry nodig om het speler-ENTITY te renderen. Door de
 * remove-packets verdwenen spelers dus niet alleen uit de tab, maar werden ze ook
 * volledig ONZICHTBAAR in de wereld — overal (lobby, game, spectators).
 *
 * Bovendien botste het direct manipuleren van de player-info lijst met Bukkit's eigen
 * hidePlayer/showPlayer-tracking (dubbele controle over dezelfde lijst).
 *
 * Oplossing: alle zichtbaarheid (zowel WERELD als TAB) loopt nu via Bukkit
 * hidePlayer/showPlayer, gecoördineerd door één autoriteit: GlobalVisibilityTicker.
 * Die past vanish-, spectator- én game-instance-regels toe. hidePlayer/showPlayer
 * stuurt wereld- én tab-zichtbaarheid consistent samen aan.
 *
 * Deze klasse blijft bestaan als dunne delegator zodat bestaande aanroepen blijven
 * werken, maar verstuurt zélf geen packets meer.
 */
class ProtocolLibTabVisibilityManager(private val plugin: LocoPillars) {

    init {
        debugLog("Tab visibility delegating to GlobalVisibilityTicker (no direct packets)")
    }

    fun updateTabVisibilityForPlayer(player: Player?) {
        if (player == null || !player.isOnline) return
        GlobalVisibilityTicker.queueUpdate(player)
    }

    fun updateTabVisibilityForAllPlayers() {
        GlobalVisibilityTicker.queueGlobalUpdate()
    }

    fun forceUpdateAllPlayers() {
        GlobalVisibilityTicker.queueGlobalUpdate()
    }

    fun onPlayerJoin(player: Player) {
        // Wereld- + tab-zichtbaarheid voor iedereen herberekenen na een join.
        GlobalVisibilityTicker.queueGlobalUpdate()
        // Kleine nazorg zodat een net-ingelogde speler zeker correct staat.
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.isOnline) GlobalVisibilityTicker.queueGlobalUpdate()
        }, 10L)
    }

    fun onPlayerQuit(player: Player) {
        GlobalVisibilityTicker.removePlayer(player)
        GlobalVisibilityTicker.queueGlobalUpdate()
    }

    fun onPlayerJoinGame(player: Player?, gameInstance: GameInstance?) {
        if (player == null || !player.isOnline) return
        debugLog("${player.name} joined game ${gameInstance?.id ?: "?"} - requeue visibility")
        GlobalVisibilityTicker.queueGlobalUpdate()
        // Game-plugin voegt de speler soms pas ná deze callback toe aan instance.players;
        // een korte her-queue zorgt dat de isolatie de juiste instance ziet.
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.isOnline) GlobalVisibilityTicker.queueGlobalUpdate()
        }, 5L)
    }

    fun onPlayerLeaveGame(player: Player?, gameInstance: GameInstance?) {
        if (player == null || !player.isOnline) return
        debugLog("${player.name} left game - requeue visibility")
        GlobalVisibilityTicker.queueGlobalUpdate()
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (player.isOnline) GlobalVisibilityTicker.queueGlobalUpdate()
        }, 5L)
    }

    fun getVisibilityStats(): Map<String, Any> {
        return try {
            val online  = Bukkit.getOnlinePlayers()
            val inGames = online.count { try { plugin.gameManager.getPlayerInstance(it) != null } catch (_: Exception) { false } }
            mapOf(
                "totalPlayers"           to online.size,
                "playersInGames"         to inGames,
                "playersInLobby"         to (online.size - inGames),
                "mode"                   to "delegated-to-GlobalVisibilityTicker",
                "perGameInstanceEnabled" to isPerGameInstanceEnabled()
            )
        } catch (e: Exception) {
            mapOf("error" to "Failed to get stats: ${e.message}")
        }
    }

    fun cleanup() {
        debugLog("Cleanup (no packet state to clear)")
    }

    private fun isPerGameInstanceEnabled(): Boolean {
        return try { plugin.tabManager.isEnabled() && plugin.tabManager.displayPerGameInstance }
               catch (_: Exception) { false }
    }

    private fun debugLog(message: String) {
        try {
            if (plugin.configManager.debugMode) plugin.logger.info("[ProtocolTabVisibility] $message")
        } catch (_: Exception) {}
    }
}
