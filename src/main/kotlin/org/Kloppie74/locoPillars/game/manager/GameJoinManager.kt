package org.Kloppie74.locoPillars.game.manager

import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import org.Kloppie74.locoPillars.database.dao.PlayerDAO
import org.Kloppie74.locoPillars.database.dao.StatsDAO
import org.Kloppie74.locoPillars.database.dao.ArenaStatsDAO
import org.Kloppie74.locoPillars.utils.MSG
import java.time.LocalDateTime
import java.util.*

/**
 * Manages player joining logic for game instances.
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

    fun joinGame(player: Player, gameTypeName: String, gameplayManager: GameplayManager): GameInstance? {
        debugLog("Player ${player.name} attempting to join game type: $gameTypeName")

        if (playerToInstance.containsKey(player.uniqueId)) {
            debugLog("Player ${player.name} is already in a game")
            player.sendMessage(MSG.lang("pillars.join.already_in_game"))
            return null
        }

        var playerId = ensurePlayerInDatabase(player) ?: run {
            player.sendMessage(MSG.lang("game.join.error_db"))
            return null
        }

        val instanceManager = GameInstanceManager(plugin, instances, plugin.configManager)
        val instance = instanceManager.getAvailableInstance(gameTypeName) ?: run {
            plugin.logger.warning("[GameJoinManager] No available games found for ${player.name} requesting $gameTypeName")
            player.sendMessage(MSG.lang("game.join.error_no_game", "type" to gameTypeName))
            return null
        }

        if (instance.players.size >= instance.getMaxPlayers()) {
            debugLog("Game instance ${instance.id} is full (${instance.players.size}/${instance.getMaxPlayers()})")
            player.sendMessage(MSG.lang("game.join.error_full"))
            return null
        }

        debugLog("Adding player ${player.name} to instance ${instance.id}")
        val wasEmpty = instance.players.isEmpty()
        instance.players.add(player.uniqueId)
        playerToInstance[player.uniqueId] = instance
        val playersAfterJoin = instance.players.size
        debugLog("Player added to instance, total players: $playersAfterJoin/${instance.getMaxPlayers()}")

        if (wasEmpty) debugLog("First player joined, keeping arena available for more players")

        val playerManager = PlayerManager(plugin)
        playerManager.preparePlayerForGame(player)
        debugLog("Player ${player.name} prepared for game")

        val lobbyEnabled = instance.arenaInstance.config.isLobbyEnabled()

        if (lobbyEnabled) {
            // ── Lobby-enabled path: send player to shared waiting area ─────────
            val spawnArea = instance.arenaInstance.config.getSpawnTeleportLocation(instance.arenaInstance.origin)
            if (!validateSpawnArea(player, instance, spawnArea)) {
                instance.players.remove(player.uniqueId)
                playerToInstance.remove(player.uniqueId)
                return null
            }

            if (plugin.configManager.debugMode) logTeleportDebug(player, instance, spawnArea)

            val teleportSuccess = player.teleport(spawnArea)
            if (!teleportSuccess) {
                plugin.logger.severe("[GameJoinManager] ✗ Teleport FAILED for ${player.name}!")
                player.sendMessage(MSG.lang("game.join.error_teleport"))
                instance.players.remove(player.uniqueId)
                playerToInstance.remove(player.uniqueId)
                return null
            }

            debugLog("✓ Teleport SUCCESS for ${player.name}")
            if (plugin.configManager.debugMode) scheduleLocationDebugLog(player)

            updateDatabaseOnJoin(player, playerId, instance)

            broadcastToGame(instance, MSG.lang("game.join.broadcast",
                "player" to player.name,
                "current" to playersAfterJoin.toString(),
                "max" to instance.getMaxPlayers().toString()))
            player.sendMessage(MSG.lang("game.join.welcome", "game" to instance.getDisplayName()))

            if (playersAfterJoin >= instance.getMinPlayers() && instance.state == GameState.WAITING) {
                debugLog("Minimum players reached ($playersAfterJoin/${instance.getMinPlayers()}), starting lobby countdown")
                gameplayManager.startLobbyCountdown(instance)
            } else {
                val needed = instance.getMinPlayers() - playersAfterJoin
                player.sendMessage(MSG.lang("pillars.join.waiting_for_players", "needed" to needed.toString()))
                debugLog("Waiting for more players: $playersAfterJoin/${instance.getMinPlayers()} minimum")
            }
        } else {
            // ── Lobby-disabled path: assign directly to team cage ─────────────
            val assigned = gameplayManager.assignPlayerDirectlyToCage(player, instance)
            if (!assigned) {
                player.sendMessage(MSG.lang("game.join.error_no_team_slots"))
                instance.players.remove(player.uniqueId)
                playerToInstance.remove(player.uniqueId)
                return null
            }

            updateDatabaseOnJoin(player, playerId, instance)

            broadcastToGame(instance, MSG.lang("game.join.broadcast",
                "player" to player.name,
                "current" to playersAfterJoin.toString(),
                "max" to instance.getMaxPlayers().toString()))
            player.sendMessage(MSG.lang("game.join.welcome", "game" to instance.getDisplayName()))

            if (playersAfterJoin >= instance.getMinPlayers() && instance.state == GameState.WAITING) {
                debugLog("No-lobby: minimum players reached ($playersAfterJoin/${instance.getMinPlayers()}), starting game")
                gameplayManager.startGame(instance)
            } else {
                val needed = instance.getMinPlayers() - playersAfterJoin
                player.sendMessage(MSG.lang("pillars.join.waiting_for_players", "needed" to needed.toString()))
                debugLog("No-lobby: waiting for more players: $playersAfterJoin/${instance.getMinPlayers()} minimum")
            }
        }

        SpectatorUtils.removeSpectatorStatus(player, plugin)
        plugin.spectatorInventoryManager.updateVisibilityForGameInstance(instance)

        return instance
    }

    private fun ensurePlayerInDatabase(player: Player): String? {
        try {
            var playerId = PlayerDAO.getPlayerId(player.name)
            if (playerId == null) {
                val playerRecord = PlayerDAO.create(
                    playerName = player.name,
                    displayName = player.displayName,
                    uuid = player.uniqueId.toString()
                )
                if (playerRecord != null) {
                    playerId = playerRecord.playerId
                    val statsCreated = StatsDAO.create(playerId)
                    if (statsCreated) debugLog("✓ Created new player in database: ${player.name} (ID: $playerId)")
                    else plugin.logger.warning("[GameJoinManager] ✗ Failed to create stats for new player: ${player.name}")
                } else {
                    plugin.logger.severe("[GameJoinManager] ✗ Failed to create player in database: ${player.name}")
                    return null
                }
            } else {
                // Only update display name here — last_seen and session_count are handled by PlayerJoinListener
                PlayerDAO.updateDisplayName(playerId, player.displayName ?: player.name)
                debugLog("✓ Updated existing player in database: ${player.name} (ID: $playerId)")
            }
            return playerId
        } catch (ex: Exception) {
            plugin.logger.severe("[GameJoinManager] ❌ Database error for player ${player.name}: ${ex.message}")
            ex.printStackTrace()
            return null
        }
    }

    private fun updateDatabaseOnJoin(player: Player, playerId: String, instance: GameInstance) {
        try {
            val activeSet = PlayerDAO.setActiveStatus(playerId, true)
            if (!activeSet) plugin.logger.warning("[GameJoinManager] ✗ Failed to set active status for ${player.name}")

            val arenaName = instance.arenaInstance.config.name
            val arenaCreated = ArenaStatsDAO.createOrUpdate(arenaName, instance.getDisplayName())
            if (arenaCreated) debugLog("✓ Updated arena stats for: $arenaName")

            val playerArenaCreated = ArenaStatsDAO.createOrUpdatePlayerArenaStats(playerId, arenaName)
            if (playerArenaCreated) debugLog("✓ Ensured player arena stats for ${player.name} in $arenaName")

            achievementListener.onGamePlayed(player)
            debugLog("✓ Updated database stats for player join: ${player.name}")
        } catch (ex: Exception) {
            plugin.logger.severe("[GameJoinManager] ❌ Error updating database on join for ${player.name}: ${ex.message}")
            ex.printStackTrace()
        }
    }

    private fun validateSpawnArea(player: Player, instance: GameInstance, spawnArea: org.bukkit.Location): Boolean {
        if (spawnArea.world == null) {
            plugin.logger.severe("[GameJoinManager] ✗ ERROR: Spawn area world is NULL!")
            player.sendMessage(MSG.lang("game.join.error_spawn_null"))
            return false
        }
        if (spawnArea.world != instance.arenaInstance.world) {
            plugin.logger.severe("[GameJoinManager] ✗ ERROR: Spawn area world mismatch!")
            plugin.logger.severe("[GameJoinManager]   Expected: ${instance.arenaInstance.world.name}")
            plugin.logger.severe("[GameJoinManager]   Got: ${spawnArea.world?.name}")
            player.sendMessage(MSG.lang("game.join.error_spawn_mismatch"))
            return false
        }

        val origin     = instance.arenaInstance.origin
        val buildRange = instance.arenaInstance.config.getBuildRange()

        val withinXRange = spawnArea.x >= origin.x - buildRange && spawnArea.x <= origin.x + buildRange
        val withinZRange = spawnArea.z >= origin.z - buildRange && spawnArea.z <= origin.z + buildRange

        if (!withinXRange || !withinZRange) {
            plugin.logger.severe("[GameJoinManager] ✗ ERROR: SpawnArea outside game XZ boundaries!")
            plugin.logger.severe("[GameJoinManager]   Spawn: x=${spawnArea.x}, z=${spawnArea.z}")
            plugin.logger.severe("[GameJoinManager]   Origin: x=${origin.x}, z=${origin.z}, BuildRange: $buildRange")
            player.sendMessage(MSG.lang("game.join.error_spawn_boundary"))
            return false
        }
        return true
    }

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
        }, 100L)
    }

    private fun broadcastToGame(instance: GameInstance, message: String) {
        val players = getPlayersInGame(instance)
        debugLog("Broadcasting to ${players.size} players: $message")
        players.forEach { it.sendMessage(message) }
    }

    private fun getPlayersInGame(instance: GameInstance): List<Player> {
        return instance.players.mapNotNull { playerId ->
            plugin.server.getPlayer(playerId)?.takeIf { it.isOnline }
        }
    }

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) plugin.logger.info("[GameJoinManager] $message")
    }
}
