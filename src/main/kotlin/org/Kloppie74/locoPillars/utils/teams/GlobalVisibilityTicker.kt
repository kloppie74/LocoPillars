package org.Kloppie74.locoPillars.utils.teams

import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.database.dao.VanishDAO
import org.Kloppie74.locoPillars.game.arena.ArenaWorldManager
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import org.Kloppie74.locoPillars.utils.spectator.SpectatorVisibilityManager
import org.Kloppie74.locoPillars.utils.vanish.VanishManager
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * UPDATED GlobalVisibilityTicker integrated with ProtocolLib TabVisibilityManager
 *
 * This class coordinates visibility updates across multiple systems:
 * - VanishManager (highest priority)
 * - SpectatorVisibilityManager (medium priority)
 * - ProtocolLibTabVisibilityManager (lowest priority, tab-list only via ProtocolLib packets)
 * - TeamManager (formatting only, no visibility control)
 *
 * Key Changes:
 * - Integrates with ProtocolLibTabVisibilityManager for proper tab-list visibility
 * - Coordinates with TabManager for formatting updates
 * - Maintains priority system to prevent conflicts
 * - Uses ProtocolLib packets for reliable tab visibility control
 */
object GlobalVisibilityTicker {

    private lateinit var plugin: LocoPillars
    private var ticker: BukkitRunnable? = null
    private val updateQueue = ConcurrentHashMap<UUID, UpdateRequest>()
    private val lastUpdate = ConcurrentHashMap<UUID, Long>()
    private val vanishPriorityPlayers = ConcurrentHashMap<UUID, Long>() // Players with vanish priority
    private val spectatorPriorityPlayers = ConcurrentHashMap<UUID, Long>() // Players with spectator priority

    data class UpdateRequest(
        val timestamp: Long,
        val priority: String = "NORMAL",
        val reason: String = ""
    )

    // Priority levels (higher number = higher priority)
    private val PRIORITY_LEVELS = mapOf(
        "VANISH_UPDATE" to 100,
        "SPECTATOR_UPDATE" to 50,
        "PROTOCOL_TAB_UPDATE" to 30, // New priority for ProtocolLib tab updates
        "TAB_UPDATE" to 25,
        "GLOBAL" to 20,
        "NORMAL" to 10
    )

    fun initialize(pluginInstance: LocoPillars) {
        plugin = pluginInstance
        startTicker()
    }

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) plugin.logger.info("[GlobalVisibilityTicker] $message")
    }

    fun shutdown() {
        ticker?.cancel()
        ticker = null
    }

    private fun startTicker() {
        ticker?.cancel()

        ticker = object : BukkitRunnable() {
            override fun run() {
                processVisibilityUpdates()
            }
        }

        // Run every 3 ticks for better coordination with tab updates
        ticker?.runTaskTimer(plugin, 3L, 3L)
    }

    /**
     * Queue a player for visibility update with normal priority
     */
    fun queueUpdate(player: Player) {
        queueUpdateWithPriority(player, "NORMAL", "standard_update")
    }

    /**
     * Queue a player for visibility update with specified priority
     */
    fun queueUpdateWithPriority(player: Player, priority: String, reason: String = "") {
        val currentTime = System.currentTimeMillis()
        val playerUUID = player.uniqueId

        // Check priority conflicts
        if (hasHigherPriorityActive(playerUUID, priority)) {
            debugLog("Skipping $priority update for ${player.name} - higher priority active")
            return
        }

        val currentRequest = updateQueue[playerUUID]
        val newPriorityLevel = PRIORITY_LEVELS[priority] ?: 10
        val currentPriorityLevel = PRIORITY_LEVELS[currentRequest?.priority] ?: 0

        // Only replace if new priority is higher or equal
        if (newPriorityLevel >= currentPriorityLevel) {
            updateQueue[playerUUID] = UpdateRequest(currentTime, priority, reason)

            when (priority) {
                "VANISH_UPDATE" -> {
                    vanishPriorityPlayers[playerUUID] = currentTime
                    debugLog("Queued $priority update for ${player.name}: $reason")
                }
                "SPECTATOR_UPDATE" -> {
                    spectatorPriorityPlayers[playerUUID] = currentTime
                    debugLog("Queued $priority update for ${player.name}: $reason")
                }
                "PROTOCOL_TAB_UPDATE" -> {
                    debugLog("Queued ProtocolLib $priority update for ${player.name}: $reason")
                }
            }
        }
    }

    /**
     * Queue update for all online players
     */
    fun queueGlobalUpdate() {
        Bukkit.getOnlinePlayers().forEach { player ->
            queueUpdateWithPriority(player, "GLOBAL", "global_update")
        }
    }

    /**
     * Queue tab visibility update (lowest priority) - now uses ProtocolLib
     */
    fun queueTabUpdate(player: Player, reason: String = "tab_update") {
        queueUpdateWithPriority(player, "PROTOCOL_TAB_UPDATE", reason)
    }

    /**
     * Queue spectator visibility update (medium priority)
     */
    fun queueSpectatorUpdate(player: Player, reason: String = "spectator_update") {
        queueUpdateWithPriority(player, "SPECTATOR_UPDATE", reason)
    }

    fun clearPlayerFromQueue(player: Player) {
        val removed = updateQueue.remove(player.uniqueId)
        if (removed != null) debugLog("Cleared queued update for ${player.name} (was: ${removed.priority})")
    }

    fun markPlayerAsVanishPriority(player: Player) {
        vanishPriorityPlayers[player.uniqueId] = System.currentTimeMillis()
        debugLog("Marked ${player.name} as vanish priority")
    }

    fun clearVanishPriorityFlag(player: Player) {
        vanishPriorityPlayers.remove(player.uniqueId)
        debugLog("Cleared vanish priority for ${player.name}")
    }

    fun markPlayerAsSpectatorPriority(player: Player) {
        spectatorPriorityPlayers[player.uniqueId] = System.currentTimeMillis()
        debugLog("Marked ${player.name} as spectator priority")
    }

    fun clearSpectatorPriorityFlag(player: Player) {
        spectatorPriorityPlayers.remove(player.uniqueId)
        debugLog("Cleared spectator priority for ${player.name}")
    }

    /**
     * Check if a player has higher priority updates active
     */
    private fun hasHigherPriorityActive(playerUUID: UUID, requestedPriority: String): Boolean {
        val requestedLevel = PRIORITY_LEVELS[requestedPriority] ?: 10

        // Check vanish priority (highest)
        if (vanishPriorityPlayers.containsKey(playerUUID) && requestedLevel < PRIORITY_LEVELS["VANISH_UPDATE"]!!) {
            return true
        }

        // Check spectator priority (medium)
        if (spectatorPriorityPlayers.containsKey(playerUUID) && requestedLevel < PRIORITY_LEVELS["SPECTATOR_UPDATE"]!!) {
            return true
        }

        return false
    }

    /**
     * Process all queued visibility updates with enhanced priority system
     */
    private fun processVisibilityUpdates() {
        val currentTime = System.currentTimeMillis()
        val playersToUpdate = mutableSetOf<Player>()
        val priorityUpdates = mutableMapOf<Player, String>()
        val protocolTabUpdates = mutableSetOf<Player>()

        // Clean expired priority flags (after 5 seconds)
        cleanExpiredPriorityFlags(currentTime)

        val iterator = updateQueue.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val player = Bukkit.getPlayer(entry.key)

            if (player == null || !player.isOnline) {
                iterator.remove()
                continue
            }

            // Check if player has higher priority active and this is not that priority
            if (hasHigherPriorityActive(entry.key, entry.value.priority)) {
                debugLog("Skipping ${entry.value.priority} update for ${player.name} - higher priority active")
                iterator.remove()
                continue
            }

            // Only update if enough time has passed (except for high priority updates)
            val lastUpdateTime = lastUpdate[entry.key] ?: 0L
            val minDelay = when (entry.value.priority) {
                "VANISH_UPDATE" -> 0L
                "SPECTATOR_UPDATE" -> 50L
                "PROTOCOL_TAB_UPDATE" -> 100L
                "TAB_UPDATE" -> 100L
                else -> 200L
            }

            if (currentTime - lastUpdateTime >= minDelay) {
                playersToUpdate.add(player)
                priorityUpdates[player] = entry.value.priority

                // Track ProtocolLib tab updates separately
                if (entry.value.priority == "PROTOCOL_TAB_UPDATE") {
                    protocolTabUpdates.add(player)
                }

                lastUpdate[entry.key] = currentTime
                iterator.remove()
            }
        }

        // Process integrated visibility updates with priority awareness
        if (playersToUpdate.isNotEmpty()) {
            updateIntegratedVisibility(playersToUpdate, priorityUpdates, protocolTabUpdates)
        }
    }

    /**
     * Clean expired priority flags
     */
    private fun cleanExpiredPriorityFlags(currentTime: Long) {
        val expiredVanish = vanishPriorityPlayers.entries.filter { currentTime - it.value > 5000L }
        expiredVanish.forEach {
            vanishPriorityPlayers.remove(it.key)
            debugLog("Auto-cleared expired vanish priority for player ${it.key}")
        }

        val expiredSpectator = spectatorPriorityPlayers.entries.filter { currentTime - it.value > 3000L }
        expiredSpectator.forEach {
            spectatorPriorityPlayers.remove(it.key)
            debugLog("Auto-cleared expired spectator priority for player ${it.key}")
        }
    }

    /**
     * UPDATED: Integrated visibility update with ProtocolLib TabManager coordination
     */
    private fun updateIntegratedVisibility(
        playersToUpdate: Set<Player>,
        priorityMap: Map<Player, String>,
        protocolTabUpdates: Set<Player>
    ) {
        val allPlayers = Bukkit.getOnlinePlayers().toList()

        // Coordinate with TabManager for formatting updates (non-ProtocolLib)
        val tabFormatUpdates = playersToUpdate.filter {
            priorityMap[it] in listOf("TAB_UPDATE", "GLOBAL") && !protocolTabUpdates.contains(it)
        }

        if (tabFormatUpdates.isNotEmpty() && plugin.tabManager.isEnabled()) {
            tabFormatUpdates.forEach { player ->
                try {
                    plugin.tabManager.updatePlayerTab(player)
                } catch (e: Exception) {
                    plugin.logger.warning("[GlobalVisibilityTicker] Error updating tab format for ${player.name}: ${e.message}")
                }
            }
        }

        // Handle ProtocolLib tab visibility updates (highest priority for tab list)
        if (protocolTabUpdates.isNotEmpty() && plugin.tabManager.displayPerGameInstance) {
            try {
                if (plugin.isProtocolLibAvailable()) {
                    val protocolTabManager = plugin.protocolLibTabVisibilityManager
                    protocolTabUpdates.forEach { player ->
                        protocolTabManager.updateTabVisibilityForPlayer(player)
                    }
                    debugLog("Updated ProtocolLib tab visibility for ${protocolTabUpdates.size} players")
                }
            } catch (e: Exception) {
                plugin.logger.warning("[GlobalVisibilityTicker] Error updating ProtocolLib tab visibility: ${e.message}")
            }
        }

        // Process world visibility updates for all player pairs (Bukkit showPlayer/hidePlayer)
        for (viewer in allPlayers) {
            for (target in allPlayers) {
                if (viewer == target) continue

                // Only update if either player is in the update queue
                if (playersToUpdate.contains(viewer) || playersToUpdate.contains(target)) {

                    // Check priority conflicts
                    if (shouldSkipDueToPriority(viewer, target, priorityMap)) {
                        continue
                    }

                    val finalVisibility = determineIntegratedVisibility(viewer, target)
                    applyWorldVisibility(viewer, target, finalVisibility)
                }
            }
        }

        // Update flight status for vanished players
        playersToUpdate.forEach { player ->
            if (priorityMap[player] == "VANISH_UPDATE") {
                updateFlightStatusForVanish(player)
            }
        }
    }

    /**
     * Check if update should be skipped due to priority conflicts
     */
    private fun shouldSkipDueToPriority(viewer: Player, target: Player, priorityMap: Map<Player, String>): Boolean {
        val viewerPriority = priorityMap[viewer] ?: "NORMAL"
        val targetPriority = priorityMap[target] ?: "NORMAL"

        // Check if either player has vanish priority
        val viewerHasVanishPriority = vanishPriorityPlayers.containsKey(viewer.uniqueId)
        val targetHasVanishPriority = vanishPriorityPlayers.containsKey(target.uniqueId)

        if (viewerHasVanishPriority || targetHasVanishPriority) {
            // Skip non-vanish updates when vanish priority is active
            if (viewerPriority != "VANISH_UPDATE" && targetPriority != "VANISH_UPDATE") {
                return true
            }
        }

        // Check spectator priority
        val viewerHasSpectatorPriority = spectatorPriorityPlayers.containsKey(viewer.uniqueId)
        val targetHasSpectatorPriority = spectatorPriorityPlayers.containsKey(target.uniqueId)

        if ((viewerHasSpectatorPriority || targetHasSpectatorPriority) &&
            viewerPriority != "VANISH_UPDATE" && targetPriority != "VANISH_UPDATE") {
            // Skip low priority updates when spectator priority is active
            val viewerLevel = PRIORITY_LEVELS[viewerPriority] ?: 10
            val targetLevel = PRIORITY_LEVELS[targetPriority] ?: 10
            val spectatorLevel = PRIORITY_LEVELS["SPECTATOR_UPDATE"] ?: 50

            if (viewerLevel < spectatorLevel && targetLevel < spectatorLevel) {
                return true
            }
        }

        return false
    }

    /**
     * UPDATED: Determines final world visibility by checking all systems in priority order
     * Note: This only affects world visibility (showPlayer/hidePlayer), NOT tab list visibility
     */
    private fun determineIntegratedVisibility(viewer: Player, target: Player): Boolean? {
        try {
            // 1. HIGHEST PRIORITY: Vanish system rules
            val vanishResult = checkVanishRules(viewer, target)
            if (vanishResult != null) {
                return vanishResult
            }

            // 2. MEDIUM PRIORITY: Spectator system rules
            val spectatorResult = checkSpectatorRules(viewer, target)
            if (spectatorResult != null) {
                return spectatorResult
            }

            // 3. GAME-INSTANCE ISOLATION: lobby ziet lobby, game ziet eigen game.
            // Dit stuurt zowel WORLD- als TAB-zichtbaarheid aan, want hidePlayer/showPlayer
            // controleert beide tegelijk. (Voorheen deden losse ProtocolLib-packets dit,
            // maar die maakten ook entities onzichtbaar.)
            val gameResult = checkGameInstanceRules(viewer, target)
            if (gameResult != null) {
                return gameResult
            }

            // 4. DEFAULT: zichtbaar
            return true

        } catch (e: Exception) {
            plugin.logger.warning("[GlobalVisibilityTicker] Error determining visibility for ${viewer.name} -> ${target.name}: ${e.message}")
            return true // Default to visible if error
        }
    }

    /**
     * Game-instance isolatie (laagste prioriteit, na vanish & spectator).
     *
     * Regels:
     *   - displayPerGameInstance uit  → geen isolatie (iedereen ziet iedereen)
     *   - beide in de lobby (geen instance) → zichtbaar
     *   - één in een game, ander in lobby   → verborgen
     *   - beide in dezelfde game-instance   → zichtbaar
     *   - verschillende game-instances      → verborgen
     *
     * Returns null als isolatie uit staat (laat default-zichtbaar gelden).
     */
    private fun checkGameInstanceRules(viewer: Player, target: Player): Boolean? {
        try {
            if (!plugin.tabManager.displayPerGameInstance) return null

            val viewerInstance = plugin.gameManager.getPlayerInstance(viewer)
            val targetInstance = plugin.gameManager.getPlayerInstance(target)

            // Beide in de (main) lobby → altijd zichtbaar voor elkaar
            if (viewerInstance == null && targetInstance == null) return true

            // Eén in een game, de ander in de lobby → verborgen
            if (viewerInstance == null || targetInstance == null) return false

            // Beide in een game → alleen zichtbaar als het dezelfde instance is
            return viewerInstance == targetInstance
        } catch (e: Exception) {
            plugin.logger.warning("[GlobalVisibilityTicker] Error in game-instance rules: ${e.message}")
            return null
        }
    }

    /**
     * Check vanish system rules (HIGHEST PRIORITY)
     */
    private fun checkVanishRules(viewer: Player, target: Player): Boolean? {
        try {
            // Load vanish states using DAO
            val viewerState = try {
                VanishDAO.load(viewer.uniqueId)
            } catch (e: Exception) {
                plugin.logger.warning("[GlobalVisibilityTicker] Error loading vanish state for ${viewer.name}: ${e.message}")
                null
            }

            val targetState = try {
                VanishDAO.load(target.uniqueId)
            } catch (e: Exception) {
                plugin.logger.warning("[GlobalVisibilityTicker] Error loading vanish state for ${target.name}: ${e.message}")
                null
            }

            // If we couldn't load states, no vanish rules apply
            if (viewerState == null || targetState == null) {
                return null
            }

            // If target is vanished or hideMe
            if (targetState.vanished || targetState.hideMe) {
                // Admin permission check for vanished
                if (targetState.vanished && viewer.hasPermission("locopillars.vanish.seeothers")) {
                    return true
                }
                return false
            }

            // If viewer has hideOthers
            if (viewerState.hideOthers) {
                return viewerState.showOthers.contains(target.uniqueId)
            }

            // No vanish rules apply
            return null
        } catch (e: Exception) {
            plugin.logger.warning("[GlobalVisibilityTicker] Error in vanish rules check: ${e.message}")
            return null
        }
    }


    /**
     * Check spectator system rules (MEDIUM PRIORITY)
     */
    private fun checkSpectatorRules(viewer: Player, target: Player): Boolean? {
        try {
            // BELANGRIJK: gebruik isSpectating() (rauwe map) i.p.v. isSpectator().
            // Een mid-game geëlimineerde speler blijft tot het einde in instance.players
            // staan (inGame == true), waardoor isSpectator() false geeft. isSpectating()
            // herkent ze tóch als spectator, zodat ze verborgen blijven voor levende spelers.
            val viewerIsSpectator = SpectatorUtils.isSpectating(viewer, plugin)
            val targetIsSpectator = SpectatorUtils.isSpectating(target, plugin)

            // Handmatige "hide spectators"-toggle: verberg spectators voor wie dat aan heeft.
            if (targetIsSpectator && SpectatorVisibilityManager.isHidingSpectators(viewer)) {
                return false
            }

            // Spectators (geëlimineerd/dood) zijn verborgen voor levende spelers binnen
            // game-context. Dit overschrijft de game-instance-regel (die zou ze anders
            // zichtbaar maken omdat de spectator nog in dezelfde instance zit).
            // Gegate op game-context (pillars-wereld of actieve instance) zodat een
            // verouderde spectator-map-entry nooit een gewone lobby-speler verbergt.
            if (targetIsSpectator && !viewerIsSpectator) {
                val targetInGameContext = target.world.name.startsWith("pillars_") ||
                        target.world.name.equals(ArenaWorldManager.GAMEWORLD_NAME, ignoreCase = true) ||
                        plugin.gameManager.getPlayerInstance(target) != null ||
                        plugin.gameManager.getSpectatorInstance(target) != null
                if (targetInGameContext) return false
            }

            // A spectator SEES everyone in the same arena world (alive players + other spectators).
            // Matches both old per-arena worlds ("pillars_*") and the new single game world.
            if (viewerIsSpectator &&
                (viewer.world.name.startsWith("pillars_") ||
                 viewer.world.name.equals(ArenaWorldManager.GAMEWORLD_NAME, ignoreCase = true)) &&
                viewer.world.name == target.world.name) {
                return true
            }

            // Geen spectator-regel van toepassing → laat game-instance-regel beslissen.
            return null
        } catch (e: Exception) {
            plugin.logger.warning("[GlobalVisibilityTicker] Error in spectator rules check: ${e.message}")
            return null
        }
    }

    /**
     * Apply WORLD visibility between viewer and target if needed
     * UPDATED: This now only handles world visibility, NOT tab visibility
     */
    private fun applyWorldVisibility(viewer: Player, target: Player, shouldSee: Boolean?) {
        if (shouldSee == null) return

        try {
            // DO NOT override vanish priority: if target is vanished, always keep hidden from viewer
            val targetState = try {
                VanishDAO.load(target.uniqueId)
            } catch (e: Exception) {
                plugin.logger.warning("[GlobalVisibilityTicker] Error loading vanish state for ${target.name}: ${e.message}")
                null
            }

            // Check if target is vanished and viewer doesn't have permission to see vanished players
            if (targetState != null && targetState.vanished && !viewer.hasPermission("locopillars.vanish.seeothers")) {
                if (viewer.canSee(target)) {
                    viewer.hidePlayer(plugin, target)
                }
                return
            }

            // Apply the requested visibility
            val canCurrentlySee = viewer.canSee(target)
            if (shouldSee && !canCurrentlySee) {
                viewer.showPlayer(plugin, target)
            } else if (!shouldSee && canCurrentlySee) {
                viewer.hidePlayer(plugin, target)
            }
        } catch (e: Exception) {
            // Ignore errors - players might have disconnected
        }
    }

    /**
     * Update flight status ONLY for vanish
     */
    private fun updateFlightStatusForVanish(player: Player) {
        try {
            val vanishState = try {
                VanishDAO.load(player.uniqueId)
            } catch (e: Exception) {
                plugin.logger.warning("[GlobalVisibilityTicker] Error loading vanish state for ${player.name}: ${e.message}")
                return
            }

            // Only update flight if we successfully loaded vanish state
            if (vanishState != null && vanishState.vanished) {
                player.allowFlight = true
                if (!player.isFlying) {
                    player.isFlying = true
                }
            }
        } catch (e: Exception) {
            // Ignore flight errors
        }
    }

    /**
     * Clear all caches
     */
    fun clearAllCaches() {
        updateQueue.clear()
        lastUpdate.clear()
        vanishPriorityPlayers.clear()
        spectatorPriorityPlayers.clear()
    }

    /**
     * Remove player from all caches
     */
    fun removePlayer(player: Player) {
        val playerId = player.uniqueId
        updateQueue.remove(playerId)
        lastUpdate.remove(playerId)
        vanishPriorityPlayers.remove(playerId)
        spectatorPriorityPlayers.remove(playerId)
    }

    /**
     * Get visibility statistics for debugging
     */
    fun getVisibilityStats(): Map<String, Any> {
        return try {
            val onlinePlayers = Bukkit.getOnlinePlayers()
            val queueSize = updateQueue.size
            val cacheSize = lastUpdate.size
            val vanishPriorityCount = vanishPriorityPlayers.size
            val spectatorPriorityCount = spectatorPriorityPlayers.size

            mapOf(
                "onlinePlayers" to onlinePlayers.size,
                "queuedUpdates" to queueSize,
                "cachedPlayers" to cacheSize,
                "vanishPriorityPlayers" to vanishPriorityCount,
                "spectatorPriorityPlayers" to spectatorPriorityCount,
                "tickerActive" to (ticker != null && !ticker!!.isCancelled),
                "vanishSystemActive" to true,
                "spectatorSystemActive" to true,
                "tabManagerEnabled" to plugin.tabManager.isEnabled(),
                "tabPerGameInstanceEnabled" to plugin.tabManager.displayPerGameInstance,
                "protocolLibTabManagerActive" to plugin.isProtocolLibAvailable()
            )
        } catch (e: Exception) {
            mapOf("error" to "Failed to get stats: ${e.message}")
        }
    }
}