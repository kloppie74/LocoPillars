package org.Kloppie74.locoPillars.game.manager

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import org.Kloppie74.locoPillars.database.dao.PlayerDAO
import org.Kloppie74.locoPillars.database.dao.StatsDAO
import org.Kloppie74.locoPillars.database.dao.ArenaStatsDAO
import java.time.LocalDateTime
import java.util.*

/**
 * Manages player joining logic for game instances.
 * Updated to use DAO pattern for database operations.
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
class GameJoinManager(
    private val plugin: LocoPillars,
    private val instances: MutableList<GameInstance>,
    private val playerToInstance: MutableMap<UUID, GameInstance>
) {

    private val achievementListener get() = plugin.achievementListener

    /**
     * Main method to join a player to a game instance.
     * Handles all database setup, validation, and game state updates.
     */
    fun joinGame(player: Player, gameTypeName: String, gameplayManager: GameplayManager): GameInstance? {
        debugLog("Player ${player.name} attempting to join game type: $gameTypeName")

        // === 1. CHECK IF ALREADY IN GAME ===
        if (playerToInstance.containsKey(player.uniqueId)) {
            debugLog("Player ${player.name} is already in a game")
            player.sendMessage("${ChatColor.RED}You are already in a game! Use /pillars leave to leave your current game.")
            return null
        }

        // === 2. ENSURE PLAYER EXISTS IN DATABASE ===
        var playerId = ensurePlayerInDatabase(player) ?: run {
            player.sendMessage("${ChatColor.RED}Database error occurred. Please try again.")
            return null
        }

        // === 3. FIND AVAILABLE GAME INSTANCE ===
        val instanceManager = GameInstanceManager(plugin, instances, plugin.configManager)
        val instance = instanceManager.getAvailableInstance(gameTypeName) ?: run {
            plugin.logger.warning("[GameJoinManager] No available games found for ${player.name} requesting $gameTypeName")
            player.sendMessage("${ChatColor.RED}No available games found for type: $gameTypeName")
            return null
        }

        // === 4. CHECK IF INSTANCE IS FULL ===
        if (instance.players.size >= instance.getMaxPlayers()) {
            debugLog("Game instance ${instance.id} is full (${instance.players.size}/${instance.getMaxPlayers()})")
            player.sendMessage("${ChatColor.RED}This game is full!")
            return null
        }

        // === 5. ADD PLAYER TO INSTANCE ===
        debugLog("Adding player ${player.name} to instance ${instance.id}")
        val wasEmpty = instance.players.isEmpty()

        instance.players.add(player.uniqueId)
        playerToInstance[player.uniqueId] = instance

        val playersAfterJoin = instance.players.size
        debugLog("Player added to instance, total players: $playersAfterJoin/${instance.getMaxPlayers()}")

        if (wasEmpty) {
            debugLog("First player joined, keeping arena available for more players")
        }

        // === 6. PREPARE PLAYER FOR GAME ===
        val playerManager = PlayerManager(plugin)
        playerManager.preparePlayerForGame(player)
        debugLog("Player ${player.name} prepared for game")

        // === 7. VALIDATE AND TELEPORT TO SPAWN ===
        val spawnArea = instance.arenaInstance.config.getSpawnTeleportLocation(instance.arenaInstance.origin)

        if (!validateSpawnArea(player, instance, spawnArea)) {
            // Validation failed, remove player from instance
            instance.players.remove(player.uniqueId)
            playerToInstance.remove(player.uniqueId)
            return null
        }

        // Log detailed teleport info if debug mode enabled
        if (plugin.configManager.debugMode) {
            logTeleportDebug(player, instance, spawnArea)
        }

        // Perform teleport
        val teleportSuccess = player.teleport(spawnArea)

        if (!teleportSuccess) {
            plugin.logger.severe("[GameJoinManager] ✗ Teleport FAILED for ${player.name}!")
            player.sendMessage("${ChatColor.RED}Error: Failed to teleport to game area")
            instance.players.remove(player.uniqueId)
            playerToInstance.remove(player.uniqueId)
            return null
        }

        debugLog("✓ Teleport SUCCESS for ${player.name}")

        // Debug: Log player location after 5 seconds (only in debug mode)
        if (plugin.configManager.debugMode) {
            scheduleLocationDebugLog(player)
        }

        // === 8. UPDATE DATABASE STATS ===
        updateDatabaseOnJoin(player, playerId, instance)

        // === 9. BROADCAST JOIN MESSAGE ===
        broadcastToGame(
            instance,
            "${ChatColor.GREEN}${player.name} joined the game! ($playersAfterJoin/${instance.getMaxPlayers()})"
        )
        player.sendMessage("${ChatColor.GREEN}You joined ${instance.getDisplayName()}!")

        // === 10. CHECK IF GAME CAN START ===
        if (playersAfterJoin >= instance.getMinPlayers() && instance.state == GameState.WAITING) {
            debugLog("Minimum players reached ($playersAfterJoin/${instance.getMinPlayers()}), starting lobby countdown")
            gameplayManager.startLobbyCountdown(instance)
        } else {
            debugLog("Waiting for more players: $playersAfterJoin/${instance.getMinPlayers()} minimum")
        }

        // === 11. UPDATE SPECTATOR VISIBILITY ===
        SpectatorUtils.removeSpectatorStatus(player, plugin)
        plugin.spectatorInventoryManager.updateVisibilityForGameInstance(instance)

        return instance
    }

    // =================== DATABASE OPERATIONS ===================

    /**
     * Ensures player exists in database, creates if necessary.
     * Returns playerId or null on failure.
     */
    private fun ensurePlayerInDatabase(player: Player): String? {
        try {
            var playerId = PlayerDAO.getPlayerId(player.name)

            if (playerId == null) {
                // Player doesn't exist, create new record
                val playerRecord = PlayerDAO.create(
                    playerName = player.name,
                    displayName = player.displayName,
                    uuid = player.uniqueId.toString()
                )

                if (playerRecord != null) {
                    playerId = playerRecord.playerId

                    // Create stats record
                    val statsCreated = StatsDAO.create(playerId)
                    if (statsCreated) {
                        debugLog("✓ Created new player in database: ${player.name} (ID: $playerId)")
                    } else {
                        plugin.logger.warning("[GameJoinManager] ✗ Failed to create stats for new player: ${player.name}")
                    }
                } else {
                    plugin.logger.severe("[GameJoinManager] ✗ Failed to create player in database: ${player.name}")
                    return null
                }
            } else {
                // Player exists, update their info
                PlayerDAO.updateDisplayName(playerId, player.displayName ?: player.name)
                PlayerDAO.updateLastSeen(playerId, LocalDateTime.now())
                PlayerDAO.incrementSessionCount(playerId)
                debugLog("✓ Updated existing player in database: ${player.name} (ID: $playerId)")
            }

            return playerId
        } catch (ex: Exception) {
            plugin.logger.severe("[GameJoinManager] ❌ Database error for player ${player.name}: ${ex.message}")
            ex.printStackTrace()
            return null
        }
    }

    /**
     * Updates database stats when a player joins a game.
     */
    private fun updateDatabaseOnJoin(player: Player, playerId: String, instance: GameInstance) {
        try {
            // Set player as active
            val activeSet = PlayerDAO.setActiveStatus(playerId, true)
            if (!activeSet) {
                plugin.logger.warning("[GameJoinManager] ✗ Failed to set active status for ${player.name}")
            }

            // Update arena stats
            val arenaName = instance.arenaInstance.config.name
            val arenaCreated = ArenaStatsDAO.createOrUpdate(arenaName, instance.getDisplayName())
            if (arenaCreated) {
                debugLog("✓ Updated arena stats for: $arenaName")
            }

            // Create player arena stats if doesn't exist
            val playerArenaCreated = ArenaStatsDAO.createOrUpdatePlayerArenaStats(playerId, arenaName)
            if (playerArenaCreated) {
                debugLog("✓ Ensured player arena stats for ${player.name} in $arenaName")
            }

            // Trigger achievement check
            achievementListener.onGamePlayed(player)

            debugLog("✓ Updated database stats for player join: ${player.name}")

        } catch (ex: Exception) {
            plugin.logger.severe("[GameJoinManager] ❌ Error updating database on join for ${player.name}: ${ex.message}")
            ex.printStackTrace()
        }
    }

    // =================== SPAWN VALIDATION ===================

    /**
     * Validates that the spawn area is within game boundaries.
     */
    private fun validateSpawnArea(player: Player, instance: GameInstance, spawnArea: org.bukkit.Location): Boolean {
        // Check if world is null
        if (spawnArea.world == null) {
            plugin.logger.severe("[GameJoinManager] ✗ ERROR: Spawn area world is NULL!")
            player.sendMessage("${ChatColor.RED}Error: Invalid spawn location (world is null)")
            return false
        }

        // Check if spawn area is in correct world
        if (spawnArea.world != instance.arenaInstance.world) {
            plugin.logger.severe("[GameJoinManager] ✗ ERROR: Spawn area world mismatch!")
            plugin.logger.severe("[GameJoinManager]   Expected: ${instance.arenaInstance.world.name}")
            plugin.logger.severe("[GameJoinManager]   Got: ${spawnArea.world?.name}")
            player.sendMessage("${ChatColor.RED}Error: Spawn location world mismatch")
            return false
        }

        // Check if spawn area is within game boundaries
        val origin = instance.arenaInstance.origin
        val buildRange = instance.arenaInstance.config.getBuildRange()
        val buildHeight = instance.arenaInstance.config.getBuildHeight()

        val withinXRange = spawnArea.x >= origin.x - buildRange && spawnArea.x <= origin.x + buildRange
        val withinZRange = spawnArea.z >= origin.z - buildRange && spawnArea.z <= origin.z + buildRange
        val withinYRange = spawnArea.y >= origin.y && spawnArea.y <= origin.y + buildHeight

        if (!withinXRange || !withinZRange || !withinYRange) {
            plugin.logger.severe("[GameJoinManager] ✗ ERROR: SpawnArea outside game boundaries!")
            plugin.logger.severe("[GameJoinManager]   Spawn: x=${spawnArea.x}, y=${spawnArea.y}, z=${spawnArea.z}")
            plugin.logger.severe("[GameJoinManager]   Origin: x=${origin.x}, y=${origin.y}, z=${origin.z}")
            plugin.logger.severe("[GameJoinManager]   BuildRange: $buildRange, BuildHeight: $buildHeight")
            player.sendMessage("${ChatColor.RED}Error: Spawn area outside game boundaries!")
            return false
        }

        return true
    }

    // =================== DEBUG LOGGING ===================

    /**
     * Logs detailed teleport debug information.
     */
    private fun logTeleportDebug(player: Player, instance: GameInstance, spawnArea: org.bukkit.Location) {
        debugLog("===== TELEPORT DEBUG INFO =====")
        debugLog("Player: ${player.name}")
        debugLog("Instance ID: ${instance.id}")
        debugLog("Arena Origin: ${instance.arenaInstance.origin}")
        debugLog("  Origin World: ${instance.arenaInstance.origin.world?.name}")
        debugLog("  Origin X: ${instance.arenaInstance.origin.x}")
        debugLog("  Origin Y: ${instance.arenaInstance.origin.y}")
        debugLog("  Origin Z: ${instance.arenaInstance.origin.z}")
        debugLog("Calculated Spawn Area: $spawnArea")
        debugLog("  Spawn World: ${spawnArea.world?.name}")
        debugLog("  Spawn X: ${spawnArea.x}")
        debugLog("  Spawn Y: ${spawnArea.y}")
        debugLog("  Spawn Z: ${spawnArea.z}")
        debugLog("Player current location before teleport:")
        debugLog("  Current World: ${player.world.name}")
        debugLog("  Current X: ${player.location.x}")
        debugLog("  Current Y: ${player.location.y}")
        debugLog("  Current Z: ${player.location.z}")
        debugLog("===============================")
    }

    /**
     * Schedules a debug log of player location after 5 seconds.
     */
    private fun scheduleLocationDebugLog(player: Player) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            val refreshedPlayer = plugin.server.getPlayer(player.uniqueId)
            if (refreshedPlayer != null && refreshedPlayer.isOnline) {
                debugLog("[5sec later] Player location:")
                debugLog("  World: ${refreshedPlayer.world.name}")
                debugLog("  X: ${refreshedPlayer.location.x}")
                debugLog("  Y: ${refreshedPlayer.location.y}")
                debugLog("  Z: ${refreshedPlayer.location.z}")
            } else {
                debugLog("[5sec later] Player ${player.name} is offline or null")
            }
        }, 100L) // 100 ticks = 5 seconds
    }

    // =================== UTILITY METHODS ===================

    /**
     * Broadcasts a message to all players in a game instance.
     */
    private fun broadcastToGame(instance: GameInstance, message: String) {
        val players = getPlayersInGame(instance)
        debugLog("Broadcasting to ${players.size} players: $message")
        players.forEach { player ->
            player.sendMessage(message)
        }
    }

    /**
     * Gets all online players in a game instance.
     */
    private fun getPlayersInGame(instance: GameInstance): List<Player> {
        return instance.players.mapNotNull { playerId ->
            plugin.server.getPlayer(playerId)?.takeIf { it.isOnline }
        }
    }

    /**
     * Logs debug message if debug mode is enabled.
     */
    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[GameJoinManager] $message")
        }
    }
}