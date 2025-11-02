package org.Kloppie74.locoPillars.utils.vanish

import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.database.tables.VanishTable
import org.Kloppie74.locoPillars.database.tables.VanishState
import org.Kloppie74.locoPillars.utils.teams.GlobalVisibilityTicker
import org.bukkit.entity.Player
import org.bukkit.Bukkit
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * FIXED VERSION - Prevents race conditions with other visibility systems
 */
object VanishManager {
    private val stateCache = ConcurrentHashMap<UUID, VanishState>()
    private lateinit var vanishTable: VanishTable
    private var vanishListener: VanishListener? = null
    private lateinit var plugin: LocoPillars

    fun initialize(plugin: LocoPillars, listener: VanishListener) {
        this.plugin = plugin
        vanishTable = VanishTable(plugin.databaseManager)
        vanishTable.createTable()
        vanishListener = listener

        plugin.logger.info("[VANISH DEBUG] VanishManager initialized")
    }

    fun loadState(player: Player) {
        val dbState = vanishTable.loadState(player.uniqueId) ?: VanishState(false, false, false, mutableSetOf())
        stateCache[player.uniqueId] = dbState

        plugin.logger.info("[VANISH DEBUG] Loaded state for ${player.name}: vanished=${dbState.vanished}, hideMe=${dbState.hideMe}, hideOthers=${dbState.hideOthers}")
    }

    fun getState(player: Player): VanishState {
        return stateCache.getOrPut(player.uniqueId) {
            val state = vanishTable.loadState(player.uniqueId) ?: VanishState(false, false, false, mutableSetOf())
            plugin.logger.info("[VANISH DEBUG] Created new state for ${player.name}: vanished=${state.vanished}, hideMe=${state.hideMe}, hideOthers=${state.hideOthers}")
            state
        }
    }

    fun saveState(player: Player) {
        val state = stateCache[player.uniqueId] ?: return
        vanishTable.saveState(player.uniqueId, state)

        plugin.logger.info("[VANISH DEBUG] Saved state for ${player.name}: vanished=${state.vanished}, hideMe=${state.hideMe}, hideOthers=${state.hideOthers}")
    }

    fun setVanished(player: Player, vanished: Boolean) {
        val state = getState(player)
        val oldVanished = state.vanished
        state.vanished = vanished
        stateCache[player.uniqueId] = state
        saveState(player)

        plugin.logger.info("[VANISH DEBUG] ${player.name} vanish changed: $oldVanished -> $vanished")

        // CRITICAL FIX: Mark vanish priority WHILE vanished
        if (vanished) {
            GlobalVisibilityTicker.markPlayerAsVanishPriority(player)
        } else {
            GlobalVisibilityTicker.clearVanishPriorityFlag(player)
        }

        GlobalVisibilityTicker.clearPlayerFromQueue(player)
        applyVanishVisibilityDirectWithPriority(player)

        vanishListener?.updateBossBar(player)

        // Queue updates for other players
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            Bukkit.getOnlinePlayers().forEach { otherPlayer ->
                if (otherPlayer != player) {
                    GlobalVisibilityTicker.queueUpdateWithPriority(otherPlayer, "VANISH_UPDATE")
                }
            }
        }, 2L)
    }

    fun setHideMe(player: Player, hideMe: Boolean) {
        val state = getState(player)
        val oldHideMe = state.hideMe
        state.hideMe = hideMe
        stateCache[player.uniqueId] = state
        saveState(player)

        plugin.logger.info("[VANISH DEBUG] ${player.name} hideMe changed: $oldHideMe -> $hideMe")

        // CRITICAL FIX: Clear any pending updates
        GlobalVisibilityTicker.clearPlayerFromQueue(player)

        applyVanishVisibilityDirectWithPriority(player)
        vanishListener?.updateBossBar(player)

        // Queue updates for other players
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            Bukkit.getOnlinePlayers().forEach { otherPlayer ->
                if (otherPlayer != player) {
                    GlobalVisibilityTicker.queueUpdateWithPriority(otherPlayer, "VANISH_UPDATE")
                }
            }
        }, 2L)
    }

    fun setHideOthers(player: Player, hideOthers: Boolean) {
        val state = getState(player)
        val oldHideOthers = state.hideOthers
        state.hideOthers = hideOthers
        stateCache[player.uniqueId] = state
        saveState(player)

        plugin.logger.info("[VANISH DEBUG] ${player.name} hideOthers changed: $oldHideOthers -> $hideOthers")

        // CRITICAL FIX: Clear any pending updates
        GlobalVisibilityTicker.clearPlayerFromQueue(player)

        applyVanishVisibilityDirectWithPriority(player)
        vanishListener?.updateBossBar(player)
    }

    /**
     * FIXED: Direct visibility application with priority flag to prevent override
     */
    private fun applyVanishVisibilityDirectWithPriority(player: Player) {
        val playerState = getState(player)

        plugin.logger.info("[VANISH DEBUG] Applying PRIORITY visibility for ${player.name}")

        // Mark this as a high-priority vanish operation
        GlobalVisibilityTicker.markPlayerAsVanishPriority(player)

        Bukkit.getOnlinePlayers().forEach { otherPlayer ->
            if (otherPlayer == player) return@forEach

            val otherState = getState(otherPlayer)

            val playerShouldSeeOther = shouldPlayerSeePlayerDirect(player, otherPlayer, playerState, otherState)
            val otherShouldSeePlayer = shouldPlayerSeePlayerDirect(otherPlayer, player, otherState, playerState)

            try {
                if (playerShouldSeeOther) {
                    player.showPlayer(plugin, otherPlayer)
                } else {
                    player.hidePlayer(plugin, otherPlayer)
                }

                if (otherShouldSeePlayer) {
                    otherPlayer.showPlayer(plugin, player)
                } else {
                    otherPlayer.hidePlayer(plugin, player)
                }

                // DEBUG LOG for important changes
                if (!otherShouldSeePlayer || !playerShouldSeeOther) {
                    plugin.logger.info("[VANISH DEBUG] PRIORITY: ${player.name} <-> ${otherPlayer.name}: ${player.name} sees ${otherPlayer.name}=$playerShouldSeeOther, ${otherPlayer.name} sees ${player.name}=$otherShouldSeePlayer")
                }

            } catch (e: Exception) {
                plugin.logger.warning("[VANISH DEBUG] Error applying priority visibility: ${e.message}")
            }
        }

        // Handle flight
        if (playerState.vanished) {
            player.allowFlight = true
            if (!player.isFlying) {
                player.isFlying = true
            }
        }

        // Clear the priority flag after a delay
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            GlobalVisibilityTicker.clearVanishPriorityFlag(player)
        }, 10L)
    }

    /**
     * Direct visibility check - no other system interference
     */
    private fun shouldPlayerSeePlayerDirect(viewer: Player, target: Player, viewerState: VanishState, targetState: VanishState): Boolean {
        // If target is vanished or hideMe, viewer should not see them
        if (targetState.vanished || targetState.hideMe) {
            // Only admins with permission can see vanished players
            if (targetState.vanished && viewer.hasPermission("locopillars.vanish.seeothers")) {
                return true
            }
            return false
        }

        // If viewer is hiding others, they shouldn't see target unless target is in showOthers
        if (viewerState.hideOthers) {
            return viewerState.showOthers.contains(target.uniqueId)
        }

        // Default: viewer should see target
        return true
    }

    fun setShowOthers(player: Player, showOthers: Set<UUID>) {
        val state = getState(player)
        state.showOthers = showOthers.toMutableSet()
        stateCache[player.uniqueId] = state
        saveState(player)

        plugin.logger.info("[VANISH DEBUG] ${player.name} showOthers set to: ${showOthers.size} players")

        GlobalVisibilityTicker.clearPlayerFromQueue(player)
        applyVanishVisibilityDirectWithPriority(player)
        vanishListener?.updateBossBar(player)
    }

    fun addShowOther(player: Player, target: Player) {
        val state = getState(player)
        state.showOthers.add(target.uniqueId)
        stateCache[player.uniqueId] = state
        saveState(player)

        plugin.logger.info("[VANISH DEBUG] ${player.name} added ${target.name} to showOthers")

        GlobalVisibilityTicker.clearPlayerFromQueue(player)
        applyVanishVisibilityDirectWithPriority(player)
        vanishListener?.updateBossBar(player)
    }

    fun removeShowOther(player: Player, target: Player) {
        val state = getState(player)
        state.showOthers.remove(target.uniqueId)
        stateCache[player.uniqueId] = state
        saveState(player)

        plugin.logger.info("[VANISH DEBUG] ${player.name} removed ${target.name} from showOthers")

        GlobalVisibilityTicker.clearPlayerFromQueue(player)
        applyVanishVisibilityDirectWithPriority(player)
        vanishListener?.updateBossBar(player)
    }

    // Public getters
    fun isVanished(player: Player): Boolean = getState(player).vanished
    fun isHideMe(player: Player): Boolean = getState(player).hideMe
    fun isHideOthers(player: Player): Boolean = getState(player).hideOthers
    fun getShowOthers(player: Player): Set<UUID> = getState(player).showOthers

    fun shouldHideFromOthers(player: Player): Boolean {
        val state = getState(player)
        return state.vanished || state.hideMe
    }

    fun shouldPlayerSeePlayer(viewer: Player, target: Player): Boolean {
        val viewerState = getState(viewer)
        val targetState = getState(target)
        return shouldPlayerSeePlayerDirect(viewer, target, viewerState, targetState)
    }

    /**
     * FIXED: Now uses priority system
     */
    fun forceUpdateAllVisibility() {
        plugin.logger.info("[VANISH DEBUG] forceUpdateAllVisibility() called - APPLYING PRIORITY")
        Bukkit.getOnlinePlayers().forEach { player ->
            GlobalVisibilityTicker.clearPlayerFromQueue(player)
            applyVanishVisibilityDirectWithPriority(player)
        }
    }

    /**
     * FIXED: Now uses priority system
     */
    fun queueVisibilityUpdate(player: Player) {
        plugin.logger.info("[VANISH DEBUG] queueVisibilityUpdate() called for ${player.name} - APPLYING PRIORITY")
        GlobalVisibilityTicker.clearPlayerFromQueue(player)
        applyVanishVisibilityDirectWithPriority(player)
    }

    fun clearCache(player: Player) {
        plugin.logger.info("[VANISH DEBUG] Clearing cache for ${player.name}")
        stateCache.remove(player.uniqueId)
        GlobalVisibilityTicker.clearPlayerFromQueue(player)
        vanishListener?.removeBossBar(player)
    }

    fun clearAllCache() {
        plugin.logger.info("[VANISH DEBUG] Clearing all cache")
        stateCache.clear()
        GlobalVisibilityTicker.clearAllCaches()
    }
}