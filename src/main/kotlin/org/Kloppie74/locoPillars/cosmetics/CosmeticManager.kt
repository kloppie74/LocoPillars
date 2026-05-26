package org.Kloppie74.locoPillars.cosmetics

import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.cosmetics.database.CosmeticDAO
import org.Kloppie74.locoPillars.cosmetics.engine.*
import org.Kloppie74.locoPillars.cosmetics.gui.CosmeticsSelectGui
import org.Kloppie74.locoPillars.cosmetics.gui.CosmeticsShopGui
import org.Kloppie74.locoPillars.cosmetics.provider.CoinsProvider
import org.Kloppie74.locoPillars.cosmetics.provider.PlayerPointsCoinsProvider
import org.Kloppie74.locoPillars.cosmetics.provider.SelfCoinsProvider
import org.Kloppie74.locoPillars.database.dao.PlayerDAO
import org.Kloppie74.locoPillars.game.instance.GameInstance
import java.io.File

/**
 * Central cosmetics manager.
 * Wire in by calling the fire* methods at the appropriate game events.
 */
class CosmeticManager(private val plugin: LocoPillars) {

    /** GUI instances — created after init() to avoid circular dependencies. */
    lateinit var shopGui: CosmeticsShopGui
        private set
    lateinit var selectGui: CosmeticsSelectGui
        private set

    val loader = CosmeticLoader(plugin)

    /** Coins provider — SELF or PLAYERPOINTS, picked from config. */
    val coinsProvider: CoinsProvider by lazy {
        when (plugin.configManager.cosmeticsCoinsProvider.uppercase()) {
            "PLAYERPOINTS" -> PlayerPointsCoinsProvider()
            else           -> SelfCoinsProvider()
        }
    }

    private fun debugLog(msg: String) {
        if (plugin.configManager.debugMode) plugin.logger.info("[CosmeticManager] $msg")
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    fun init() {
        loader.loadAll()
        shopGui   = CosmeticsShopGui(plugin, this)
        selectGui = CosmeticsSelectGui(plugin, this)
        plugin.server.pluginManager.registerEvents(shopGui,   plugin)
        plugin.server.pluginManager.registerEvents(selectGui, plugin)
        plugin.logger.info("[Cosmetics] ${loader.count()} cosmetics loaded. Provider: ${plugin.configManager.cosmeticsCoinsProvider}")
    }

    fun reload() {
        loader.reload()
        plugin.logger.info("[Cosmetics] Reloaded — ${loader.count()} cosmetics")
    }

    // ── Ownership & selection ──────────────────────────────────────────────────

    /** Returns true if the player owns a specific cosmetic (free cosmetics are always owned). */
    fun owns(player: Player, cosmetic: CosmeticDefinition): Boolean {
        if (cosmetic.price == 0 && cosmetic.permission.isEmpty()) return true
        val playerId = PlayerDAO.getPlayerId(player.name) ?: return false
        return try { CosmeticDAO.owns(playerId, cosmetic.id, cosmetic.type) } catch (_: Exception) { false }
    }

    /** Grants a cosmetic to a player without charging coins. */
    fun grant(player: Player, cosmeticId: String, type: CosmeticType): Boolean {
        val playerId = PlayerDAO.getPlayerId(player.name) ?: return false
        return try { CosmeticDAO.grant(playerId, cosmeticId, type) } catch (_: Exception) { false }
    }

    /**
     * Attempts to purchase a cosmetic for a player.
     * @return a [PurchaseResult] describing the outcome.
     */
    fun purchase(player: Player, cosmetic: CosmeticDefinition): PurchaseResult {
        if (owns(player, cosmetic)) return PurchaseResult.ALREADY_OWNED
        if (!cosmetic.permission.isNullOrEmpty() && !player.hasPermission(cosmetic.permission))
            return PurchaseResult.NO_PERMISSION

        val playerId = PlayerDAO.getPlayerId(player.name) ?: return PurchaseResult.ERROR

        // Level check
        try {
            val level = org.Kloppie74.locoPillars.database.dao.StatsDAO.getIntStat(playerId, "level")
            if (level < cosmetic.requiredLevel) return PurchaseResult.LEVEL_TOO_LOW
        } catch (_: Exception) {}

        // Coins check & deduct
        if (cosmetic.price > 0) {
            val balance = coinsProvider.getCoins(player)
            if (balance < cosmetic.price) return PurchaseResult.NOT_ENOUGH_COINS
            if (!coinsProvider.removeCoins(player, cosmetic.price)) return PurchaseResult.ERROR
        }

        return try {
            CosmeticDAO.grant(playerId, cosmetic.id, cosmetic.type)
            PurchaseResult.SUCCESS
        } catch (_: Exception) {
            // Refund on DB error
            if (cosmetic.price > 0) coinsProvider.addCoins(player, cosmetic.price)
            PurchaseResult.ERROR
        }
    }

    /** Sets the active cosmetic for a player of a specific type. */
    fun setSelected(player: Player, cosmetic: CosmeticDefinition): Boolean {
        if (!owns(player, cosmetic)) return false
        val playerId = PlayerDAO.getPlayerId(player.name) ?: return false
        return try { CosmeticDAO.setSelected(playerId, cosmetic.type, cosmetic.id) } catch (_: Exception) { false }
    }

    /** Clears the active cosmetic selection for a type. */
    fun clearSelected(player: Player, type: CosmeticType): Boolean {
        val playerId = PlayerDAO.getPlayerId(player.name) ?: return false
        return try { CosmeticDAO.clearSelected(playerId, type) } catch (_: Exception) { false }
    }

    /** Returns the currently active cosmetic for a player, or null if none equipped. */
    fun getSelected(player: Player, type: CosmeticType): CosmeticDefinition? {
        val playerId = PlayerDAO.getPlayerId(player.name) ?: return null
        val id = try { CosmeticDAO.getSelected(playerId, type) } catch (_: Exception) { return null } ?: return null
        return loader.get(type, id)
    }

    fun getOwnedIds(player: Player, type: CosmeticType): Set<String> {
        val playerId = PlayerDAO.getPlayerId(player.name) ?: return emptySet()
        return try { CosmeticDAO.getOwned(playerId, type) } catch (_: Exception) { emptySet() }
    }

    // ── Game hooks ─────────────────────────────────────────────────────────────

    /**
     * Fire the KILL cosmetic for [killer] when they kill [victim].
     * Call from GameListener after a kill is confirmed.
     */
    fun fireKill(killer: Player, victim: Player?, gameInstance: GameInstance?) {
        val def = getSelected(killer, CosmeticType.KILL) ?: return
        val scriptFile = def.scriptFile ?: return
        debugLog("Firing KILL cosmetic '${def.id}' for ${killer.name}")
        val context = CosmeticContext(killer.location.clone(), gameInstance, plugin)
        loader.scriptEngine.executeKill(def.id, scriptFile, killer, victim, context)
    }

    /**
     * Fire the WIN cosmetic for [winner].
     * Call from GameplayManager.endGame for each winning player.
     */
    fun fireWin(winner: Player, gameInstance: GameInstance?) {
        val def = getSelected(winner, CosmeticType.WIN) ?: return
        val scriptFile = def.scriptFile ?: return
        debugLog("Firing WIN cosmetic '${def.id}' for ${winner.name}")
        val context = CosmeticContext(winner.location.clone(), gameInstance, plugin)
        loader.scriptEngine.executeSimple(def.id, scriptFile, winner, context)
    }

    /**
     * Fire the DEATH cosmetic for [dying].
     * Call from GameListener's PlayerDeathEvent handler.
     */
    fun fireDeath(dying: Player, gameInstance: GameInstance?) {
        val def = getSelected(dying, CosmeticType.DEATH) ?: return
        val scriptFile = def.scriptFile ?: return
        debugLog("Firing DEATH cosmetic '${def.id}' for ${dying.name}")
        val context = CosmeticContext(dying.location.clone(), gameInstance, plugin)
        loader.scriptEngine.executeSimple(def.id, scriptFile, dying, context)
    }

    /**
     * Returns the cage schematic file for [player], falling back to the standard cage.
     * Call from GameplayManager.placeCageAtSpawnLocation.
     */
    fun getCageSchemForPlayer(player: Player): File? {
        val selected = getSelected(player, CosmeticType.CAGE)
        if (selected?.schemFile != null && selected.schemFile.exists()) {
            debugLog("Using cage '${selected.id}' for ${player.name}")
            return selected.schemFile
        }
        // Fall back to the original Standard-Cage.schem
        val fallback = File(plugin.dataFolder, "Cages/Standard-Cage.schem")
        return if (fallback.exists()) fallback else null
    }

    // ── Coins helpers (convenience wrappers) ──────────────────────────────────

    fun getCoins(player: Player): Int = coinsProvider.getCoins(player)
    fun addCoins(player: Player, amount: Int): Boolean = coinsProvider.addCoins(player, amount)
    fun removeCoins(player: Player, amount: Int): Boolean = coinsProvider.removeCoins(player, amount)

    enum class PurchaseResult {
        SUCCESS, ALREADY_OWNED, NOT_ENOUGH_COINS, LEVEL_TOO_LOW, NO_PERMISSION, ERROR
    }
}
