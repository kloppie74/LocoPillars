package org.Kloppie74.locoPillars.utils.vanish

import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.database.tables.VanishTable
import org.Kloppie74.locoPillars.database.tables.VanishState
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Central vanish/hideMe/hideOthers manager.
 * Handles data, cache, and triggers bossbar display logic.
 */
object VanishManager {
    private val stateCache = ConcurrentHashMap<UUID, VanishState>()
    private lateinit var vanishTable: VanishTable
    private var vanishListener: VanishListener? = null

    fun initialize(plugin: LocoPillars, listener: VanishListener) {
        vanishTable = VanishTable(plugin.databaseManager)
        vanishTable.createTable()
        vanishListener = listener
    }

    fun loadState(player: Player) {
        val dbState = vanishTable.loadState(player.uniqueId) ?: VanishState(false, false, false, mutableSetOf())
        stateCache[player.uniqueId] = dbState
    }

    fun getState(player: Player): VanishState {
        return stateCache.getOrPut(player.uniqueId) { vanishTable.loadState(player.uniqueId) ?: VanishState(false, false, false, mutableSetOf()) }
    }

    fun saveState(player: Player) {
        val state = stateCache[player.uniqueId] ?: return
        vanishTable.saveState(player.uniqueId, state)
    }

    fun setVanished(player: Player, vanished: Boolean) {
        var state = getState(player)
        state.vanished = vanished
        stateCache[player.uniqueId] = state
        saveState(player)
        vanishListener?.updateBossBar(player)
    }

    fun setHideMe(player: Player, hideMe: Boolean) {
        var state = getState(player)
        state.hideMe = hideMe
        stateCache[player.uniqueId] = state
        saveState(player)
        vanishListener?.updateBossBar(player)
    }

    fun setHideOthers(player: Player, hideOthers: Boolean) {
        var state = getState(player)
        state.hideOthers = hideOthers
        stateCache[player.uniqueId] = state
        saveState(player)
        vanishListener?.updateBossBar(player)
    }

    fun setShowOthers(player: Player, showOthers: Set<UUID>) {
        var state = getState(player)
        state.showOthers = showOthers.toMutableSet()
        stateCache[player.uniqueId] = state
        saveState(player)
        vanishListener?.updateBossBar(player)
    }

    fun addShowOther(player: Player, target: Player) {
        var state = getState(player)
        state.showOthers.add(target.uniqueId)
        stateCache[player.uniqueId] = state
        saveState(player)
        vanishListener?.updateBossBar(player)
    }

    fun removeShowOther(player: Player, target: Player) {
        var state = getState(player)
        state.showOthers.remove(target.uniqueId)
        stateCache[player.uniqueId] = state
        saveState(player)
        vanishListener?.updateBossBar(player)
    }

    fun isVanished(player: Player): Boolean = getState(player).vanished
    fun isHideMe(player: Player): Boolean = getState(player).hideMe
    fun isHideOthers(player: Player): Boolean = getState(player).hideOthers
    fun getShowOthers(player: Player): Set<UUID> = getState(player).showOthers

    fun clearCache(player: Player) {
        stateCache.remove(player.uniqueId)
        vanishListener?.removeBossBar(player)
    }

    fun clearAllCache() {
        stateCache.clear()
    }
}