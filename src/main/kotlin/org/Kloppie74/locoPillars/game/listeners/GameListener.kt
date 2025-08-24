package org.Kloppie74.locoPillars.game.listeners

import org.bukkit.ChatColor
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
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class GameListener(private val plugin: LocoPillars) : Listener {

    private val playerPlacedBlocks = ConcurrentHashMap<UUID, MutableSet<Location>>()
    private val cageProtectedPlayers = ConcurrentHashMap<UUID, Boolean>()
    private val gameplayManager get() = plugin.gameManager.gameplayManager

    // Track killstreaks and last kill times for multi-kill logic
    private val playerKillStreaks = mutableMapOf<UUID, Int>()
    private val playerLastKillTimes = mutableMapOf<UUID, Long>()

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val instance = plugin.gameManager.getPlayerInstance(player)

        if (instance != null) {
            event.deathMessage = null
            event.droppedExp = 0
            event.keepInventory = false
            event.keepLevel = false

            clearPlayerPlacedBlocks(instance.id, player.uniqueId)
            plugin.gameManager.handlePlayerDeath(player)

            val spectateListener = plugin.spectateListener
            instance.players.forEach { uuid ->
                plugin.server.getPlayer(uuid)?.let { spectateListener?.refreshPlayer(it) }
            }
            spectateListener?.refreshPlayer(player)
            plugin.spectatorInventoryManager.updateVisibilityForGameInstance(instance)

            // FIXED: Only track death stats, not game loss (that happens when game actually ends)
            val statsTracker = plugin.databaseManager.StatsEventTracker
            val playerId = statsTracker.getPlayerIdByName(player.name)
            if (playerId != null) {
                plugin.databaseManager.statsTable.addDeath(playerId)
                plugin.achievementListener.onDeath(player)
            }

            playerKillStreaks[player.uniqueId] = 0 // Reset killstreak
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (event.entity !is Player) return
        val victim = event.entity as Player
        val instance = plugin.gameManager.getPlayerInstance(victim) ?: return
        val attacker = event.damager as? Player

        if (SpectatorUtils.isSpectator(victim, plugin) ||
            (attacker != null && SpectatorUtils.isSpectator(attacker, plugin))) {
            event.isCancelled = true
            return
        }

        when (instance.state) {
            GameState.WAITING, GameState.STARTING -> {
                event.isCancelled = true
                attacker?.sendMessage("${ChatColor.RED}PvP is not allowed yet!")
            }
            GameState.ACTIVE -> {
                gameplayManager.handleDamageTaken(victim, event.finalDamage)
                if (attacker != null) {
                    gameplayManager.handleDamageDealt(attacker, event.finalDamage)
                    // Track kill & streaks if the hit will kill
                    if (victim.health - event.finalDamage <= 0.0) {
                        gameplayManager.handlePlayerKill(attacker, victim)
                        val now = System.currentTimeMillis()
                        val streak = (playerKillStreaks[attacker.uniqueId] ?: 0) + 1
                        playerKillStreaks[attacker.uniqueId] = streak
                        playerLastKillTimes[attacker.uniqueId] = now

                        gameplayManager.onKillStreak(attacker, streak)
                        when (streak) {
                            2 -> gameplayManager.onDoubleKill(attacker)
                            3 -> gameplayManager.onTripleKill(attacker)
                            4 -> gameplayManager.onQuadKill(attacker)
                        }
                        // Perfect/comeback can be triggered here
                    }
                    // Track assist
                    gameplayManager.handlePlayerAssist(attacker)
                }
            }
            GameState.ENDED, GameState.RESETTING -> event.isCancelled = true
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
            GameState.WAITING, GameState.STARTING -> event.isCancelled = true
            GameState.ACTIVE -> {
                when (event.cause) {
                    EntityDamageEvent.DamageCause.VOID -> player.sendMessage("${ChatColor.RED}You fell into the void!")
                    EntityDamageEvent.DamageCause.FALL -> gameplayManager.handleFallDamageTaken(player, event.finalDamage)
                    else -> gameplayManager.handleDamageTaken(player, event.finalDamage)
                }
            }
            GameState.ENDED, GameState.RESETTING -> event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        cageProtectedPlayers.remove(player.uniqueId)
        val instance = plugin.gameManager.getPlayerInstance(player)
        if (instance != null) clearPlayerPlacedBlocks(instance.id, player.uniqueId)
        plugin.gameManager.leaveGame(player)
        gameplayManager.onGameQuit(player)
        val spectateListener = plugin.spectateListener
        plugin.gameManager.getAllInstances().forEach { instance ->
            instance.players.forEach { uuid ->
                plugin.server.getPlayer(uuid)?.let { spectateListener?.refreshPlayer(it) }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        val lobbySpawn = plugin.configManager.getLobbySpawnLocation()
        if (lobbySpawn != null) player.teleport(lobbySpawn)
        player.gameMode = GameMode.SURVIVAL
        player.health = 20.0
        player.foodLevel = 20
        player.saturation = 20f
        player.activePotionEffects.forEach { effect -> player.removePotionEffect(effect.type) }
        val spectateListener = plugin.spectateListener
        plugin.gameManager.getAllInstances().forEach { instance ->
            instance.players.forEach { uuid ->
                plugin.server.getPlayer(uuid)?.let { spectateListener?.refreshPlayer(it) }
            }
        }
        spectateListener?.refreshPlayer(player)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player)
        if (instance != null) {
            if (instance.state != GameState.ACTIVE || SpectatorUtils.isSpectator(player, plugin)) {
                event.isCancelled = true
                player.sendMessage("${ChatColor.RED}You cannot break blocks while the game is not active!")
                return
            }
            if (isCageProtected(player.uniqueId)) {
                val blockLoc = event.block.location
                if (isPlayerInCageArea(player, blockLoc)) {
                    event.isCancelled = true
                    player.sendMessage("${ChatColor.RED}You cannot break cage blocks until the game starts!")
                    return
                }
            }
            val origin = instance.arenaInstance.origin
            val buildRange = instance.arenaInstance.config.getBuildRange()
            val buildHeight = instance.arenaInstance.config.getBuildHeight()
            val blockLoc = event.block.location
            val deltaX = kotlin.math.abs(blockLoc.x - origin.x)
            val deltaZ = kotlin.math.abs(blockLoc.z - origin.z)
            val maxY = origin.y + buildHeight
            if (deltaX > buildRange || deltaZ > buildRange || blockLoc.y > maxY) {
                event.isCancelled = true
                player.sendMessage("${ChatColor.RED}You cannot break blocks outside the game area!")
                plugin.logger.info("[GameListener] Player ${player.name} tried to break block outside area: deltaX=$deltaX, deltaZ=$deltaZ, blockY=${blockLoc.y}, maxY=$maxY")
                return
            }
            if (!instance.arenaInstance.config.canBreakMapBlocks()) {
                val playerPlacedBlocksForInstance = playerPlacedBlocks[instance.id]
                val isPlayerPlaced = playerPlacedBlocksForInstance?.contains(blockLoc) == true
                if (!isPlayerPlaced) {
                    event.isCancelled = true
                    player.sendMessage("${ChatColor.RED}You can only break blocks that you placed!")
                    plugin.logger.info("[GameListener] Player ${player.name} tried to break map block at $blockLoc (Can-Break-Map-Blocks: false)")
                    return
                }
                playerPlacedBlocksForInstance?.remove(blockLoc)
                plugin.logger.info("[GameListener] Player ${player.name} broke their own placed block at $blockLoc")
            } else {
                playerPlacedBlocks[instance.id]?.remove(blockLoc)
            }
            gameplayManager.handleDamageDealt(player, 0.0) // Example: or custom stat for blocks broken
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player)
        if (instance != null) {
            if (instance.state != GameState.ACTIVE || SpectatorUtils.isSpectator(player, plugin)) {
                event.isCancelled = true
                player.sendMessage("${ChatColor.RED}You cannot place blocks while the game is not active!")
                return
            }
            val origin = instance.arenaInstance.origin
            val buildRange = instance.arenaInstance.config.getBuildRange()
            val buildHeight = instance.arenaInstance.config.getBuildHeight()
            val blockLoc = event.block.location
            val deltaX = kotlin.math.abs(blockLoc.x - origin.x)
            val deltaZ = kotlin.math.abs(blockLoc.z - origin.z)
            val maxY = origin.y + buildHeight
            if (deltaX > buildRange || deltaZ > buildRange || blockLoc.y > maxY) {
                event.isCancelled = true
                player.sendMessage("${ChatColor.RED}You cannot place blocks outside the game area!")
                plugin.logger.info("[GameListener] Player ${player.name} tried to place block outside area: deltaX=$deltaX, deltaZ=$deltaZ, blockY=${blockLoc.y}, maxY=$maxY")
                return
            }
            val playerPlacedBlocksForInstance = playerPlacedBlocks.computeIfAbsent(instance.id) { mutableSetOf() }
            playerPlacedBlocksForInstance.add(blockLoc)
            plugin.logger.info("[GameListener] Player ${player.name} placed block at $blockLoc (tracked)")
            gameplayManager.handleDamageDealt(player, 0.0) // Example: or custom stat for blocks placed
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player) ?: return
        val deathHeight = instance.arenaInstance.config.getDeathHeight()
        if (player.location.y <= deathHeight &&
            instance.state == GameState.ACTIVE &&
            !SpectatorUtils.isSpectator(player, plugin)) {
            player.health = 0.0
            player.sendMessage("${ChatColor.RED}You fell into the void!")
        }
        if (instance.state == GameState.ACTIVE) {
            val origin = instance.arenaInstance.origin
            val buildRange = instance.arenaInstance.config.getBuildRange()
            val buildHeight = instance.arenaInstance.config.getBuildHeight()
            val deltaX = kotlin.math.abs(player.location.x - origin.x)
            val deltaZ = kotlin.math.abs(player.location.z - origin.z)
            val maxY = origin.y + buildHeight
            val currentY = player.location.y
            if (deltaX > buildRange || deltaZ > buildRange || currentY > maxY) {
                plugin.logger.info("[GameListener] Teleport-REASON: OUTSIDE AREA for ${player.name}: deltaX=$deltaX, deltaZ=$deltaZ, currentY=$currentY, maxY=$maxY")
                val safeLocation = origin.clone()
                safeLocation.y = origin.y + 10
                player.teleport(safeLocation)
                player.sendMessage("${ChatColor.RED}You cannot go outside the game area!")
                plugin.logger.info("[GameListener] Player ${player.name} moved outside build area: deltaX=$deltaX, deltaZ=$deltaZ, currentY=$currentY, maxY=$maxY")
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        if (event.entity !is Player) return
        val player = event.entity as Player
        val instance = plugin.gameManager.getPlayerInstance(player) ?: return
        if (SpectatorUtils.isSpectator(player, plugin) || instance.state != GameState.ACTIVE) {
            event.isCancelled = true
            player.foodLevel = 20
            player.saturation = 20f
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
                player.sendMessage("${ChatColor.RED}You cannot drop items right now!")
            }
            GameState.ACTIVE -> {}
            GameState.ENDED, GameState.RESETTING -> event.isCancelled = true
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
            if (toWorld != gameWorld) {
                if (event.cause == PlayerTeleportEvent.TeleportCause.COMMAND) {
                    event.isCancelled = true
                    player.sendMessage("${ChatColor.RED}You cannot teleport while in a game! Use /pillars leave to leave the game.")
                }
            }
        }
    }

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
        return distance <= 3.0
    }

    private fun clearPlayerPlacedBlocks(instanceId: UUID, playerUUID: UUID) {}

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