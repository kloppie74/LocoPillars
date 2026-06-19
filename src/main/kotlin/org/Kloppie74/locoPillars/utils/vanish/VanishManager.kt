package org.Kloppie74.locoPillars.utils.vanish

import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.database.dao.VanishDAO
import org.Kloppie74.locoPillars.database.models.VanishState
import org.Kloppie74.locoPillars.game.arena.ArenaWorldManager
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import org.Kloppie74.locoPillars.utils.teams.GlobalVisibilityTicker
import org.bukkit.entity.Player
import org.bukkit.Bukkit
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * VanishManager - Manages vanish, hideMe, and hideOthers functionality.
 *
 * CHANGELOG (v2.0):
 * - Replaced VanishTable with VanishDAO for all database operations
 * - Removed direct table access, uses DAO pattern throughout
 * - Added proper error handling for all database operations
 * - Improved null-safety checks
 * - Better logging with ✓/✗/❌ indicators
 * - Cache now uses VanishState from database.models package
 * - All state modifications now use DAO methods
 * - Added comprehensive error recovery
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
object VanishManager {
    private val stateCache = ConcurrentHashMap<UUID, VanishState>()
    private var vanishListener: VanishListener? = null
    private lateinit var plugin: LocoPillars

    fun initialize(plugin: LocoPillars, listener: VanishListener) {
        this.plugin = plugin
        vanishListener = listener
        plugin.logger.info("[VanishManager] ✓ VanishManager initialized with DAO pattern")
    }

    // =================== STATE LOADING & CACHING ===================

    fun loadState(player: Player) {
        try {
            val dbState = VanishDAO.load(player.uniqueId) ?: VanishState(
                playerId = player.uniqueId.toString(),
                vanished = false,
                hideMe = false,
                hideOthers = false,
                showOthers = mutableSetOf()
            )
            stateCache[player.uniqueId] = dbState

            plugin.logger.info("[VanishManager] ✓ Loaded state for ${player.name}: vanished=${dbState.vanished}, hideMe=${dbState.hideMe}, hideOthers=${dbState.hideOthers}")
        } catch (e: Exception) {
            plugin.logger.severe("[VanishManager] ❌ Error loading state for ${player.name}: ${e.message}")
            e.printStackTrace()

            // Create default state on error
            val defaultState = VanishState(
                playerId = player.uniqueId.toString(),
                vanished = false,
                hideMe = false,
                hideOthers = false,
                showOthers = mutableSetOf()
            )
            stateCache[player.uniqueId] = defaultState
        }
    }

    fun getState(player: Player): VanishState {
        return stateCache.getOrPut(player.uniqueId) {
            try {
                val state = VanishDAO.load(player.uniqueId) ?: VanishState(
                    playerId = player.uniqueId.toString(),
                    vanished = false,
                    hideMe = false,
                    hideOthers = false,
                    showOthers = mutableSetOf()
                )
                plugin.logger.info("[VanishManager] ✓ Created new cached state for ${player.name}")
                state
            } catch (e: Exception) {
                plugin.logger.severe("[VanishManager] ❌ Error getting state for ${player.name}: ${e.message}")
                VanishState(
                    playerId = player.uniqueId.toString(),
                    vanished = false,
                    hideMe = false,
                    hideOthers = false,
                    showOthers = mutableSetOf()
                )
            }
        }
    }

    fun saveState(player: Player) {
        try {
            val state = stateCache[player.uniqueId] ?: return
            val saved = VanishDAO.save(player.uniqueId, state)

            if (saved) {
                plugin.logger.info("[VanishManager] ✓ Saved state for ${player.name}: vanished=${state.vanished}, hideMe=${state.hideMe}, hideOthers=${state.hideOthers}")
            } else {
                plugin.logger.warning("[VanishManager] ✗ Failed to save state for ${player.name}")
            }
        } catch (e: Exception) {
            plugin.logger.severe("[VanishManager] ❌ Error saving state for ${player.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    // =================== VANISH STATE MODIFICATIONS ===================

    fun setVanished(player: Player, vanished: Boolean) {
        try {
            val state = getState(player)
            val oldVanished = state.vanished
            state.vanished = vanished
            stateCache[player.uniqueId] = state

            // Save to database
            val saved = VanishDAO.save(player.uniqueId, state)
            if (!saved) {
                plugin.logger.warning("[VanishManager] ✗ Failed to save vanished state for ${player.name}")
            }

            plugin.logger.info("[VanishManager] ✓ ${player.name} vanish changed: $oldVanished -> $vanished")

            // Mark vanish priority while vanished
            if (vanished) {
                GlobalVisibilityTicker.markPlayerAsVanishPriority(player)
            } else {
                GlobalVisibilityTicker.clearVanishPriorityFlag(player)
            }

            GlobalVisibilityTicker.clearPlayerFromQueue(player)
            applyVanishVisibilityDirectWithPriority(player)
            triggerTabUpdate()

            vanishListener?.updateBossBar(player)

            // Queue updates for other players
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                Bukkit.getOnlinePlayers().forEach { otherPlayer ->
                    if (otherPlayer != player) {
                        GlobalVisibilityTicker.queueUpdateWithPriority(otherPlayer, "VANISH_UPDATE", "vanish_state_changed")
                    }
                }
            }, 2L)
        } catch (e: Exception) {
            plugin.logger.severe("[VanishManager] ❌ Error setting vanished for ${player.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    fun setHideMe(player: Player, hideMe: Boolean) {
        try {
            val state = getState(player)
            val oldHideMe = state.hideMe
            state.hideMe = hideMe
            stateCache[player.uniqueId] = state

            // Save to database
            val saved = VanishDAO.save(player.uniqueId, state)
            if (!saved) {
                plugin.logger.warning("[VanishManager] ✗ Failed to save hideMe state for ${player.name}")
            }

            plugin.logger.info("[VanishManager] ✓ ${player.name} hideMe changed: $oldHideMe -> $hideMe")

            GlobalVisibilityTicker.clearPlayerFromQueue(player)
            applyVanishVisibilityDirectWithPriority(player)
            triggerTabUpdate()
            vanishListener?.updateBossBar(player)

            // Queue updates for other players
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                Bukkit.getOnlinePlayers().forEach { otherPlayer ->
                    if (otherPlayer != player) {
                        GlobalVisibilityTicker.queueUpdateWithPriority(otherPlayer, "VANISH_UPDATE", "hide_me_changed")
                    }
                }
            }, 2L)
        } catch (e: Exception) {
            plugin.logger.severe("[VanishManager] ❌ Error setting hideMe for ${player.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    fun setHideOthers(player: Player, hideOthers: Boolean) {
        try {
            val state = getState(player)
            val oldHideOthers = state.hideOthers
            state.hideOthers = hideOthers
            stateCache[player.uniqueId] = state

            // Save to database
            val saved = VanishDAO.save(player.uniqueId, state)
            if (!saved) {
                plugin.logger.warning("[VanishManager] ✗ Failed to save hideOthers state for ${player.name}")
            }

            plugin.logger.info("[VanishManager] ✓ ${player.name} hideOthers changed: $oldHideOthers -> $hideOthers")

            GlobalVisibilityTicker.clearPlayerFromQueue(player)
            applyVanishVisibilityDirectWithPriority(player)
            triggerTabUpdate()
            vanishListener?.updateBossBar(player)
        } catch (e: Exception) {
            plugin.logger.severe("[VanishManager] ❌ Error setting hideOthers for ${player.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    // =================== SHOW OTHERS MANAGEMENT ===================

    fun setShowOthers(player: Player, showOthers: Set<UUID>) {
        try {
            val state = getState(player)
            state.showOthers = showOthers.toMutableSet()
            stateCache[player.uniqueId] = state

            // Save to database
            val saved = VanishDAO.save(player.uniqueId, state)
            if (!saved) {
                plugin.logger.warning("[VanishManager] ✗ Failed to save showOthers for ${player.name}")
            }

            plugin.logger.info("[VanishManager] ✓ ${player.name} showOthers set to: ${showOthers.size} players")

            GlobalVisibilityTicker.clearPlayerFromQueue(player)
            applyVanishVisibilityDirectWithPriority(player)
            triggerTabUpdate()
            vanishListener?.updateBossBar(player)
        } catch (e: Exception) {
            plugin.logger.severe("[VanishManager] ❌ Error setting showOthers for ${player.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    fun addShowOther(player: Player, target: Player) {
        try {
            val added = VanishDAO.addShowOther(player.uniqueId, target.uniqueId)
            if (added) {
                // Update cache
                val state = getState(player)
                state.showOthers.add(target.uniqueId)
                stateCache[player.uniqueId] = state

                plugin.logger.info("[VanishManager] ✓ ${player.name} added ${target.name} to showOthers")

                GlobalVisibilityTicker.clearPlayerFromQueue(player)
                applyVanishVisibilityDirectWithPriority(player)
                triggerTabUpdate()
                vanishListener?.updateBossBar(player)
            } else {
                plugin.logger.warning("[VanishManager] ✗ Failed to add ${target.name} to showOthers for ${player.name}")
            }
        } catch (e: Exception) {
            plugin.logger.severe("[VanishManager] ❌ Error adding showOther for ${player.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    fun removeShowOther(player: Player, target: Player) {
        try {
            val removed = VanishDAO.removeShowOther(player.uniqueId, target.uniqueId)
            if (removed) {
                // Update cache
                val state = getState(player)
                state.showOthers.remove(target.uniqueId)
                stateCache[player.uniqueId] = state

                plugin.logger.info("[VanishManager] ✓ ${player.name} removed ${target.name} from showOthers")

                GlobalVisibilityTicker.clearPlayerFromQueue(player)
                applyVanishVisibilityDirectWithPriority(player)
                triggerTabUpdate()
                vanishListener?.updateBossBar(player)
            } else {
                plugin.logger.warning("[VanishManager] ✗ Failed to remove ${target.name} from showOthers for ${player.name}")
            }
        } catch (e: Exception) {
            plugin.logger.severe("[VanishManager] ❌ Error removing showOther for ${player.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    // =================== VISIBILITY APPLICATION ===================

    /**
     * Direct visibility application with priority flag to prevent override.
     */
    private fun applyVanishVisibilityDirectWithPriority(player: Player) {
        try {
            val playerState = getState(player)

            plugin.logger.info("[VanishManager] Applying PRIORITY visibility for ${player.name}")

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

                    // Debug log for important changes
                    if (!otherShouldSeePlayer || !playerShouldSeeOther) {
                        plugin.logger.info("[VanishManager] PRIORITY: ${player.name} <-> ${otherPlayer.name}: ${player.name} sees ${otherPlayer.name}=$playerShouldSeeOther, ${otherPlayer.name} sees ${player.name}=$otherShouldSeePlayer")
                    }

                } catch (e: Exception) {
                    plugin.logger.warning("[VanishManager] ✗ Error applying priority visibility: ${e.message}")
                }
            }

            // Handle flight for vanished players
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

        } catch (e: Exception) {
            plugin.logger.severe("[VanishManager] ❌ Error in applyVanishVisibilityDirectWithPriority: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Direct visibility check - no other system interference.
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

    // =================== PUBLIC GETTERS ===================

    fun isVanished(player: Player): Boolean = getState(player).vanished
    fun isHideMe(player: Player): Boolean = getState(player).hideMe
    fun isHideOthers(player: Player): Boolean = getState(player).hideOthers
    fun getShowOthers(player: Player): Set<UUID> = getState(player).showOthers

    /**
     * True when [player] is in a game context (active game, spectating, or physically in
     * the shared game world). hideMe/hideOthers are lobby-only features — the commands
     * consult this to block usage during a game, so players can only hide in the lobby.
     */
    fun isInGameContext(player: Player): Boolean {
        if (plugin.gameManager.getPlayerInstance(player) != null) return true
        if (plugin.gameManager.getSpectatorInstance(player) != null) return true
        if (SpectatorUtils.isSpectating(player, plugin)) return true
        return player.world.name.equals(ArenaWorldManager.GAMEWORLD_NAME, ignoreCase = true)
    }

    fun shouldHideFromOthers(player: Player): Boolean {
        val state = getState(player)
        return state.vanished || state.hideMe
    }

    fun shouldPlayerSeePlayer(viewer: Player, target: Player): Boolean {
        val viewerState = getState(viewer)
        val targetState = getState(target)
        return shouldPlayerSeePlayerDirect(viewer, target, viewerState, targetState)
    }

    // =================== FORCE UPDATE METHODS ===================

    /**
     * Stuur na een vanish-wijziging ook een tab-update via ProtocolLib.
     * De normale applyVanishVisibilityDirect() werkt alleen op world-visibility
     * (hidePlayer/showPlayer) maar niet op de tab-lijst. ProtocolLibTabVisibility-
     * Manager leest de vanish-staat direct uit de cache, dus het volstaat om
     * een gewone updateTabVisibilityForAllPlayers() aan te roepen.
     */
    private fun triggerTabUpdate() {
        try {
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                if (plugin.isProtocolLibAvailable()) {
                    plugin.protocolLibTabVisibilityManager.updateTabVisibilityForAllPlayers()
                }
            }, 3L)
        } catch (_: Exception) {}
    }

    /**
     * Force update all visibility with priority system.
     */
    fun forceUpdateAllVisibility() {
        try {
            plugin.logger.info("[VanishManager] ✓ forceUpdateAllVisibility() called - APPLYING PRIORITY")
            Bukkit.getOnlinePlayers().forEach { player ->
                GlobalVisibilityTicker.clearPlayerFromQueue(player)
                applyVanishVisibilityDirectWithPriority(player)
            }
        } catch (e: Exception) {
            plugin.logger.severe("[VanishManager] ❌ Error in forceUpdateAllVisibility: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Queue visibility update with priority system.
     */
    fun queueVisibilityUpdate(player: Player) {
        try {
            plugin.logger.info("[VanishManager] ✓ queueVisibilityUpdate() called for ${player.name} - APPLYING PRIORITY")
            GlobalVisibilityTicker.clearPlayerFromQueue(player)
            applyVanishVisibilityDirectWithPriority(player)
        } catch (e: Exception) {
            plugin.logger.severe("[VanishManager] ❌ Error in queueVisibilityUpdate for ${player.name}: ${e.message}")
            e.printStackTrace()
        }
    }

    // =================== CACHE MANAGEMENT ===================

    fun clearCache(player: Player) {
        try {
            plugin.logger.info("[VanishManager] ✓ Clearing cache for ${player.name}")
            stateCache.remove(player.uniqueId)
            GlobalVisibilityTicker.clearPlayerFromQueue(player)
            vanishListener?.removeBossBar(player)
        } catch (e: Exception) {
            plugin.logger.warning("[VanishManager] ✗ Error clearing cache for ${player.name}: ${e.message}")
        }
    }

    fun clearAllCache() {
        try {
            plugin.logger.info("[VanishManager] ✓ Clearing all cache")
            stateCache.clear()
            GlobalVisibilityTicker.clearAllCaches()
        } catch (e: Exception) {
            plugin.logger.severe("[VanishManager] ❌ Error clearing all cache: ${e.message}")
            e.printStackTrace()
        }
    }
}