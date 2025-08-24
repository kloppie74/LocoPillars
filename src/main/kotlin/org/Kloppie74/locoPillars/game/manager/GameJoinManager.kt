package org.Kloppie74.locoPillars.game.manager

import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import java.util.*

class GameJoinManager(
    private val plugin: LocoPillars,
    private val instances: MutableList<GameInstance>,
    private val playerToInstance: MutableMap<UUID, GameInstance>
) {

    private val achievementListener get() = plugin.achievementListener
    private val statsTable get() = plugin.databaseManager.statsTable

    fun joinGame(player: Player, gameTypeName: String, gameplayManager: GameplayManager): GameInstance? {
        debugLog("Player ${player.name} attempting to join game type: $gameTypeName")

        if (playerToInstance.containsKey(player.uniqueId)) {
            debugLog("Player ${player.name} is already in a game")
            player.sendMessage("${ChatColor.RED}You are already in a game! Use /pillars leave to leave your current game.")
            return null
        }

        // Ensure player exists in database and update session info
        val statsTracker = plugin.databaseManager.StatsEventTracker
        var playerId = statsTracker.getPlayerIdByName(player.name)

        if (playerId == null) {
            // Gebruik de echte UUID!
            playerId = statsTracker.createPlayer(player.name, player.displayName, player.uniqueId.toString())
            if (playerId != null) {
                statsTracker.createPlayerStats(playerId)
                debugLog("Created new player in database: ${player.name} (ID: $playerId)")
            } else {
                plugin.logger.severe("[GameJoinManager] Failed to create player in database: ${player.name}")
                player.sendMessage("${ChatColor.RED}Database error occurred. Please try again.")
                return null
            }
        } else {
            statsTracker.updateDisplayName(playerId, player.displayName ?: player.name)
            statsTracker.updateLastSeen(playerId)
            statsTracker.incrementSessionCount(playerId)
            debugLog("Updated existing player in database: ${player.name} (ID: $playerId)")
        }

        val instanceManager = GameInstanceManager(plugin, instances, plugin.configManager)
        val instance = instanceManager.getAvailableInstance(gameTypeName) ?: run {
            plugin.logger.warning("[GameJoinManager] No available games found for ${player.name} requesting $gameTypeName")
            player.sendMessage("${ChatColor.RED}No available games found for type: $gameTypeName")
            return null
        }

        if (instance.players.size >= instance.getMaxPlayers()) {
            debugLog("Game instance ${instance.id} is full (${instance.players.size}/${instance.getMaxPlayers()})")
            player.sendMessage("${ChatColor.RED}This game is full!")
            return null
        }

        debugLog("Adding player ${player.name} to instance ${instance.id}")

        val wasEmpty = instance.players.isEmpty()

        instance.players.add(player.uniqueId)
        playerToInstance[player.uniqueId] = instance

        val playersAfterJoin = instance.players.size
        debugLog("Player added to instance, total players: $playersAfterJoin/${instance.getMaxPlayers()}")

        if (wasEmpty) {
            debugLog("First player joined, keeping arena available for more players")
        }

        val playerManager = PlayerManager(plugin)
        playerManager.preparePlayerForGame(player)
        debugLog("Player ${player.name} prepared for game")

        val spawnArea = instance.arenaInstance.config.getSpawnTeleportLocation(instance.arenaInstance.origin)

        // Check dat spawnArea binnen speelveld valt
        val origin = instance.arenaInstance.origin
        val buildRange = instance.arenaInstance.config.getBuildRange()
        val buildHeight = instance.arenaInstance.config.getBuildHeight()
        if (spawnArea.x < origin.x - buildRange || spawnArea.x > origin.x + buildRange ||
            spawnArea.z < origin.z - buildRange || spawnArea.z > origin.z + buildRange ||
            spawnArea.y < origin.y || spawnArea.y > origin.y + buildHeight) {
            plugin.logger.severe("[GameJoinManager] ERROR: SpawnArea buiten speelveld! spawnArea=$spawnArea origin=$origin buildRange=$buildRange buildHeight=$buildHeight")
            player.sendMessage("${ChatColor.RED}Error: SpawnArea buiten speelveld!")
            instance.players.remove(player.uniqueId)
            playerToInstance.remove(player.uniqueId)
            return null
        }

        // Only log teleport debug info if debug mode is enabled
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[GameJoinManager] ===== TELEPORT DEBUG INFO =====")
            plugin.logger.info("[GameJoinManager] Player: ${player.name}")
            plugin.logger.info("[GameJoinManager] Game Type: $gameTypeName")
            plugin.logger.info("[GameJoinManager] Instance ID: ${instance.id}")
            plugin.logger.info("[GameJoinManager] Arena Origin: ${instance.arenaInstance.origin}")
            plugin.logger.info("[GameJoinManager]   Origin World: ${instance.arenaInstance.origin.world?.name}")
            plugin.logger.info("[GameJoinManager]   Origin X: ${instance.arenaInstance.origin.x}")
            plugin.logger.info("[GameJoinManager]   Origin Y: ${instance.arenaInstance.origin.y}")
            plugin.logger.info("[GameJoinManager]   Origin Z: ${instance.arenaInstance.origin.z}")
            plugin.logger.info("[GameJoinManager] Calculated Spawn Area: $spawnArea")
            plugin.logger.info("[GameJoinManager]   Spawn World: ${spawnArea.world?.name}")
            plugin.logger.info("[GameJoinManager]   Spawn X: ${spawnArea.x}")
            plugin.logger.info("[GameJoinManager]   Spawn Y: ${spawnArea.y}")
            plugin.logger.info("[GameJoinManager]   Spawn Z: ${spawnArea.z}")
            plugin.logger.info("[GameJoinManager] Player current location before teleport:")
            plugin.logger.info("[GameJoinManager]   Current World: ${player.world.name}")
            plugin.logger.info("[GameJoinManager]   Current X: ${player.location.x}")
            plugin.logger.info("[GameJoinManager]   Current Y: ${player.location.y}")
            plugin.logger.info("[GameJoinManager]   Current Z: ${player.location.z}")
            plugin.logger.info("[GameJoinManager] ================================")
        }

        if (spawnArea.world == null) {
            plugin.logger.severe("[GameJoinManager] ERROR: Spawn area world is NULL!")
            player.sendMessage("${ChatColor.RED}Error: Invalid spawn location (world is null)")
            instance.players.remove(player.uniqueId)
            playerToInstance.remove(player.uniqueId)
            return null
        }

        if (spawnArea.world != instance.arenaInstance.world) {
            plugin.logger.severe("[GameJoinManager] ERROR: Spawn area world mismatch!")
            plugin.logger.severe("[GameJoinManager]   Expected: ${instance.arenaInstance.world.name}")
            plugin.logger.severe("[GameJoinManager]   Got: ${spawnArea.world?.name}")
        }

        // Teleport één keer!
        val teleportSuccess = player.teleport(spawnArea)

        if (teleportSuccess) {
            debugLog("✓ Teleport SUCCESS")
            if (plugin.configManager.debugMode) {
                plugin.logger.info("[GameJoinManager] Player location after teleport:")
                plugin.logger.info("[GameJoinManager]   New World: ${player.world.name}")
                plugin.logger.info("[GameJoinManager]   New X: ${player.location.x}")
                plugin.logger.info("[GameJoinManager]   New Y: ${player.location.y}")
                plugin.logger.info("[GameJoinManager]   New Z: ${player.location.z}")

                // Log de locatie van de speler nogmaals na 5 seconden alleen in debug mode
                plugin.server.scheduler.runTaskLater(plugin, Runnable {
                    val refreshedPlayer = plugin.server.getPlayer(player.uniqueId)
                    if (refreshedPlayer != null && refreshedPlayer.isOnline) {
                        plugin.logger.info("[GameJoinManager] [5sec later] Player location:")
                        plugin.logger.info("[GameJoinManager]   World: ${refreshedPlayer.world.name}")
                        plugin.logger.info("[GameJoinManager]   X: ${refreshedPlayer.location.x}")
                        plugin.logger.info("[GameJoinManager]   Y: ${refreshedPlayer.location.y}")
                        plugin.logger.info("[GameJoinManager]   Z: ${refreshedPlayer.location.z}")
                    } else {
                        plugin.logger.info("[GameJoinManager] [5sec later] Player ${player.name} is offline or null")
                    }
                }, 100L) // 100 ticks = 5 seconden
            }
        } else {
            plugin.logger.severe("[GameJoinManager] ✗ Teleport FAILED!")
            player.sendMessage("${ChatColor.RED}Error: Failed to teleport to game area")
            instance.players.remove(player.uniqueId)
            playerToInstance.remove(player.uniqueId)
            return null
        }

        broadcastToGame(
            instance,
            "${ChatColor.GREEN}${player.name} joined the game! ($playersAfterJoin/${instance.getMaxPlayers()})"
        )
        player.sendMessage("${ChatColor.GREEN}You joined ${instance.getDisplayName()}!")

        // Update database stats for joining a game
        if (playerId != null) {
            // Set player as active
            statsTracker.setActiveStatus(playerId, true)

            // Update arena stats - player joined
            val arenaName = instance.arenaInstance.config.name
            statsTracker.createOrUpdateArenaStats(arenaName, instance.getDisplayName())
            statsTracker.createOrUpdatePlayerArenaStats(playerId, arenaName)

            // Achievement + Stats: Track join (games played already tracked at game start)
            // No extra stats increment here (tracked at game start), but if you want to track raw joins:
            // statsTable.incrementGamesPlayed(playerId)
            achievementListener.onGamePlayed(player)
            debugLog("Updated database stats for player join: ${player.name}")
        }

        if (playersAfterJoin >= instance.getMinPlayers() && instance.state == GameState.WAITING) {
            debugLog("Minimum players reached ($playersAfterJoin/${instance.getMinPlayers()}), starting lobby countdown")
            gameplayManager.startLobbyCountdown(instance)
        } else {
            debugLog("Waiting for more players: $playersAfterJoin/${instance.getMinPlayers()} minimum")
        }

        SpectatorUtils.removeSpectatorStatus(player, plugin)
        // FIX: update visibility voor alle spelers in deze instance (join)
        plugin.spectatorInventoryManager.updateVisibilityForGameInstance(instance)

        return instance
    }

    private fun broadcastToGame(instance: GameInstance, message: String) {
        val players = getPlayersInGame(instance)
        debugLog("Broadcasting to ${players.size} players: $message")
        players.forEach { player ->
            player.sendMessage(message)
        }
    }

    private fun getPlayersInGame(instance: GameInstance): List<Player> {
        return instance.players.mapNotNull { playerId ->
            plugin.server.getPlayer(playerId)?.takeIf { it.isOnline }
        }
    }

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[GameJoinManager] $message")
        }
    }
}