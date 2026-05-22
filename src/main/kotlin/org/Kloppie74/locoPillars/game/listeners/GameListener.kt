package org.Kloppie74.locoPillars.game.listeners

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import org.Kloppie74.locoPillars.database.dao.PlayerDAO
import org.Kloppie74.locoPillars.database.dao.StatsDAO
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.rewards.RewardProcessor
import org.bukkit.configuration.file.YamlConfiguration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Main game event listener handling all in-game mechanics.
 * Updated to use DAO pattern for database operations.
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
class GameListener(private val plugin: LocoPillars) : Listener {
    companion object {
        private const val POST_RESPAWN_DELAY_TICKS = 1L
    }

    private val playerPlacedBlocks = ConcurrentHashMap<UUID, MutableSet<Location>>()
    private val cageProtectedPlayers = ConcurrentHashMap<UUID, Boolean>()
    private val gameplayManager get() = plugin.gameManager.gameplayManager

    // Track killstreaks and last kill times for multi-kill logic
    private val playerKillStreaks = mutableMapOf<UUID, Int>()
    private val playerLastKillTimes = mutableMapOf<UUID, Long>()

    // =================== DEATH HANDLING ===================

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val instance = plugin.gameManager.getPlayerInstance(player)

        if (instance != null) {
            // Configure death event
            event.deathMessage = null
            event.droppedExp = 0
            event.keepInventory = false
            event.keepLevel = false

            // Clear placed blocks
            clearPlayerPlacedBlocks(instance.id, player.uniqueId)

            // Handle death through game manager (also records stats and sets spectator mode)
            plugin.gameManager.handlePlayerDeath(player)

            // Refresh spectator visibility
            val spectateListener = plugin.spectateListener
            instance.players.forEach { uuid ->
                plugin.server.getPlayer(uuid)?.let { spectateListener?.refreshPlayer(it) }
            }
            spectateListener?.refreshPlayer(player)
            plugin.spectatorInventoryManager.updateVisibilityForGameInstance(instance)

            // Reset killstreak
            playerKillStreaks[player.uniqueId] = 0
        }
    }

    // =================== RESPAWN HANDLING ===================

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val respawnLoc = plugin.gameManager.getEliminatedRespawnLocation(player.uniqueId)
        if (respawnLoc != null) {
            event.respawnLocation = respawnLoc
        }

        // Re-apply eliminated/spectator transition state atomically after respawn.
        object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline) return
                plugin.gameManager.handleEliminatedPlayerRespawn(player)
            }
        }.runTaskLater(plugin, POST_RESPAWN_DELAY_TICKS)
    }

    // =================== DAMAGE HANDLING ===================

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (event.entity !is Player) return

        val victim = event.entity as Player
        val instance = plugin.gameManager.getPlayerInstance(victim) ?: return
        val attacker = event.damager as? Player

        // Cancel if spectator involved
        if (SpectatorUtils.isSpectator(victim, plugin) ||
            (attacker != null && SpectatorUtils.isSpectator(attacker, plugin))) {
            event.isCancelled = true
            return
        }

        when (instance.state) {
            GameState.WAITING, GameState.STARTING -> {
                event.isCancelled = true
                attacker?.sendMessage(MSG.lang("gameplay.pvp_not_allowed"))
            }

            GameState.ACTIVE -> {
                // Track damage taken by victim
                gameplayManager.handleDamageTaken(victim, event.finalDamage)

                if (attacker != null) {
                    // Track damage dealt by attacker
                    gameplayManager.handleDamageDealt(attacker, event.finalDamage)

                    // Check if this hit will kill the victim
                    if (victim.health - event.finalDamage <= 0.0) {
                        handlePlayerKill(attacker, victim, instance)
                    } else {
                        // Track assist (non-killing hit)
                        gameplayManager.handlePlayerAssist(attacker)
                    }
                }
            }

            GameState.ENDED, GameState.RESETTING -> {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.entity !is Player) return

        val player = event.entity as Player
        val instance = plugin.gameManager.getPlayerInstance(player) ?: return

        if (SpectatorUtils.isSpectator(player, plugin)) {
            event.isCancelled = true
            return
        }

        when (instance.state) {
            GameState.WAITING, GameState.STARTING -> {
                event.isCancelled = true
            }

            GameState.ACTIVE -> {
                when (event.cause) {
                    EntityDamageEvent.DamageCause.VOID -> {
                        player.sendMessage(MSG.lang("gameplay.void_fell"))
                        gameplayManager.handleDamageTaken(player, event.finalDamage)
                    }
                    EntityDamageEvent.DamageCause.FALL -> {
                        gameplayManager.handleFallDamageTaken(player, event.finalDamage)
                    }
                    else -> {
                        gameplayManager.handleDamageTaken(player, event.finalDamage)
                    }
                }
            }

            GameState.ENDED, GameState.RESETTING -> {
                event.isCancelled = true
            }
        }
    }

    /**
     * Handles kill logic including killstreaks, multi-kills, and database stats.
     */
    private fun handlePlayerKill(attacker: Player, victim: Player, instance: GameInstance) {
        // Handle kill through gameplay manager (also records stats)
        gameplayManager.handlePlayerKill(attacker, victim)

        // === KILLSTREAK TRACKING ===
        val now = System.currentTimeMillis()
        val streak = (playerKillStreaks[attacker.uniqueId] ?: 0) + 1
        playerKillStreaks[attacker.uniqueId] = streak
        playerLastKillTimes[attacker.uniqueId] = now

        // Trigger killstreak events
        gameplayManager.onKillStreak(attacker, streak)

        // Multi-kill detection (within 5 seconds)
        val lastKillTime = playerLastKillTimes[attacker.uniqueId] ?: 0
        val timeSinceLastKill = now - lastKillTime

        if (timeSinceLastKill <= 5000) { // 5 seconds window for multi-kills
            when (streak) {
                2 -> gameplayManager.onDoubleKill(attacker)
                3 -> gameplayManager.onTripleKill(attacker)
                4 -> gameplayManager.onQuadKill(attacker)
            }
        }

        val rewardsSection = YamlConfiguration.loadConfiguration(instance.arenaInstance.config.file)
            .getConfigurationSection("Rewards")
        val remainingPlayers = instance.players.mapNotNull { plugin.server.getPlayer(it) }
        RewardProcessor.processKillRewards(plugin, attacker, victim, remainingPlayers, rewardsSection)
    }

    // =================== PLAYER JOIN/QUIT ===================

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        plugin.gameManager.clearEliminatedState(player.uniqueId)

        // Clear cage protection
        cageProtectedPlayers.remove(player.uniqueId)

        // Clear placed blocks for this player's instance
        val instance = plugin.gameManager.getPlayerInstance(player)
        if (instance != null) {
            clearPlayerPlacedBlocks(instance.id, player.uniqueId)
        }

        // Leave game — GameLeaveManager handles all stats and DB writes
        plugin.gameManager.leaveGame(player)

        // Refresh spectator visibility for all instances
        val spectateListener = plugin.spectateListener
        plugin.gameManager.getAllInstances().forEach { gameInstance ->
            gameInstance.players.forEach { uuid ->
                plugin.server.getPlayer(uuid)?.let { spectateListener?.refreshPlayer(it) }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        plugin.gameManager.clearEliminatedState(player.uniqueId)

        // Teleport to lobby spawn
        val lobbySpawn = plugin.configManager.getLobbySpawnLocation()
        if (lobbySpawn != null) {
            player.teleport(lobbySpawn)
        }

        // Reset player state - skip editors (creative enforcer handles their gamemode)
        if (!plugin.adminEditorManager.isActive(player)) {
            player.gameMode = GameMode.SURVIVAL
        }
        player.health = 20.0
        player.foodLevel = 20
        player.saturation = 20f
        // Reset persistent flags that survive server restarts or previous sessions
        player.isInvulnerable = false
        player.allowFlight = false
        player.isFlying = false
        player.isCollidable = true
        player.exp = 0f
        player.level = 0
        player.fireTicks = 0
        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }

        // Refresh spectator visibility
        val spectateListener = plugin.spectateListener
        plugin.gameManager.getAllInstances().forEach { instance ->
            instance.players.forEach { uuid ->
                plugin.server.getPlayer(uuid)?.let { spectateListener?.refreshPlayer(it) }
            }
        }
        spectateListener?.refreshPlayer(player)
    }

    // =================== BLOCK BREAK/PLACE ===================

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player)

        if (instance != null) {
            // Check if game is active and player is not spectator
            if (instance.state != GameState.ACTIVE || SpectatorUtils.isSpectator(player, plugin)) {
                event.isCancelled = true
                player.sendMessage(MSG.lang("gameplay.block_break_not_active"))
                return
            }

            // Check cage protection
            if (isCageProtected(player.uniqueId)) {
                val blockLoc = event.block.location
                if (isPlayerInCageArea(player, blockLoc)) {
                    event.isCancelled = true
                    player.sendMessage(MSG.lang("gameplay.block_break_cage"))
                    return
                }
            }

            // Check build boundaries
            val origin = instance.arenaInstance.origin
            val buildRange = instance.arenaInstance.config.getBuildRange()
            val buildHeight = instance.arenaInstance.config.getBuildHeight()
            val blockLoc = event.block.location

            val deltaX = kotlin.math.abs(blockLoc.x - origin.x)
            val deltaZ = kotlin.math.abs(blockLoc.z - origin.z)
            val maxY = origin.y + buildHeight

            if (deltaX > buildRange || deltaZ > buildRange || blockLoc.y > maxY) {
                event.isCancelled = true
                player.sendMessage(MSG.lang("gameplay.block_break_out_of_bounds"))
                return
            }

            // Check if player can break map blocks
            if (!instance.arenaInstance.config.canBreakMapBlocks()) {
                val playerPlacedBlocksForInstance = playerPlacedBlocks[instance.id]
                val isPlayerPlaced = playerPlacedBlocksForInstance?.contains(blockLoc) == true

                if (!isPlayerPlaced) {
                    event.isCancelled = true
                    player.sendMessage(MSG.lang("gameplay.block_break_player_placed_only"))
                    return
                }

                // Remove from placed blocks
                playerPlacedBlocksForInstance?.remove(blockLoc)
            } else {
                // Remove from placed blocks if exists
                playerPlacedBlocks[instance.id]?.remove(blockLoc)
            }

            // === DATABASE: Track block broken ===
            try {
                val playerId = PlayerDAO.getPlayerId(player.name)
                if (playerId != null) {
                    StatsDAO.incrementStat(playerId, "blocks_broken")
                }
            } catch (ex: Exception) {
                plugin.logger.warning("[GameListener] Error tracking block break: ${ex.message}")
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player)

        if (instance != null) {
            // Check if game is active and player is not spectator
            if (instance.state != GameState.ACTIVE || SpectatorUtils.isSpectator(player, plugin)) {
                event.isCancelled = true
                player.sendMessage(MSG.lang("gameplay.block_place_not_active"))
                return
            }

            // Check build boundaries
            val origin = instance.arenaInstance.origin
            val buildRange = instance.arenaInstance.config.getBuildRange()
            val buildHeight = instance.arenaInstance.config.getBuildHeight()
            val blockLoc = event.block.location

            val deltaX = kotlin.math.abs(blockLoc.x - origin.x)
            val deltaZ = kotlin.math.abs(blockLoc.z - origin.z)
            val maxY = origin.y + buildHeight

            if (deltaX > buildRange || deltaZ > buildRange || blockLoc.y > maxY) {
                event.isCancelled = true
                player.sendMessage(MSG.lang("gameplay.block_place_out_of_bounds"))
                return
            }

            // Track placed block
            val playerPlacedBlocksForInstance = playerPlacedBlocks.computeIfAbsent(instance.id) { mutableSetOf() }
            playerPlacedBlocksForInstance.add(blockLoc)

            // === DATABASE: Track block placed ===
            try {
                val playerId = PlayerDAO.getPlayerId(player.name)
                if (playerId != null) {
                    StatsDAO.incrementStat(playerId, "blocks_placed")
                }
            } catch (ex: Exception) {
                plugin.logger.warning("[GameListener] Error tracking block place: ${ex.message}")
            }
        }
    }

    // =================== MOVEMENT & BOUNDARIES ===================

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player) ?: return

        // Check void death
        val deathHeight = instance.arenaInstance.config.getDeathHeight()
        if (player.location.y <= deathHeight &&
            instance.state == GameState.ACTIVE &&
            !SpectatorUtils.isSpectator(player, plugin)) {
            player.health = 0.0
            player.sendMessage(MSG.lang("gameplay.void_fell"))
            return
        }

        // Check game boundaries during active game
        if (instance.state == GameState.ACTIVE) {
            val origin = instance.arenaInstance.origin
            val buildRange = instance.arenaInstance.config.getBuildRange()
            val buildHeight = instance.arenaInstance.config.getBuildHeight()

            val deltaX = kotlin.math.abs(player.location.x - origin.x)
            val deltaZ = kotlin.math.abs(player.location.z - origin.z)
            val maxY = origin.y + buildHeight
            val currentY = player.location.y

            if (deltaX > buildRange || deltaZ > buildRange || currentY > maxY) {
                // Teleport player back to safe location
                val safeLocation = origin.clone()
                safeLocation.y = origin.y + 10
                player.teleport(safeLocation)
                player.sendMessage(MSG.lang("gameplay.out_of_bounds"))
            }
        }
    }

    // =================== MISC EVENTS ===================

    @EventHandler(priority = EventPriority.HIGH)
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        if (event.entity !is Player) return

        val player = event.entity as Player
        val instance = plugin.gameManager.getPlayerInstance(player) ?: return

        // Cancel hunger for spectators or non-active games.
        // Keep food bar full but do NOT reset saturation — letting it deplete
        // naturally means players enter the ACTIVE phase with low saturation,
        // so hunger kicks in as soon as combat starts.
        if (SpectatorUtils.isSpectator(player, plugin) || instance.state != GameState.ACTIVE) {
            event.isCancelled = true
            player.foodLevel = 20
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player) ?: return

        if (SpectatorUtils.isSpectator(player, plugin)) {
            event.isCancelled = true
            return
        }

        when (instance.state) {
            GameState.WAITING, GameState.STARTING -> {
                event.isCancelled = true
                player.sendMessage(MSG.lang("gameplay.cannot_drop_items"))
            }
            GameState.ACTIVE -> {
                // Allow item dropping during active game
            }
            GameState.ENDED, GameState.RESETTING -> {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player) ?: return

        if (SpectatorUtils.isSpectator(player, plugin)) return

        if (instance.state == GameState.ACTIVE) {
            val toWorld = event.to?.world
            val gameWorld = instance.arenaInstance.world

            // Prevent teleporting out of game world
            if (toWorld != gameWorld) {
                if (event.cause == PlayerTeleportEvent.TeleportCause.COMMAND) {
                    event.isCancelled = true
                    player.sendMessage(MSG.lang("gameplay.cannot_teleport"))
                }
            }
        }
    }

    // =================== CAGE PROTECTION ===================

    /**
     * Enables or disables cage protection for a player.
     */
    fun setCageProtection(playerUUID: UUID, protected: Boolean) {
        if (protected) {
            cageProtectedPlayers[playerUUID] = true
            plugin.logger.info("[GameListener] Cage protection enabled for player: $playerUUID")
        } else {
            cageProtectedPlayers.remove(playerUUID)
            plugin.logger.info("[GameListener] Cage protection disabled for player: $playerUUID")
        }
    }

    private fun isCageProtected(playerUUID: UUID): Boolean {
        return cageProtectedPlayers.containsKey(playerUUID)
    }

    private fun isPlayerInCageArea(player: Player, blockLoc: Location): Boolean {
        val playerLoc = player.location
        val distance = playerLoc.distance(blockLoc)
        return distance <= 3.0 // 3 block radius around player
    }

    // =================== BLOCK TRACKING ===================

    private fun clearPlayerPlacedBlocks(instanceId: UUID, playerUUID: UUID) {
        // Currently not implemented - blocks are tracked per instance, not per player
        // Could be enhanced to track per-player blocks if needed
    }

    fun clearAllPlayerPlacedBlocks(instanceId: UUID) {
        val blocksCleared = playerPlacedBlocks.remove(instanceId)?.size ?: 0
        if (blocksCleared > 0) {
            plugin.logger.info("[GameListener] Cleared all $blocksCleared player-placed blocks for instance $instanceId")
        }
    }

    fun getPlayerPlacedBlocksCount(instanceId: UUID): Int {
        return playerPlacedBlocks[instanceId]?.size ?: 0
    }
}
