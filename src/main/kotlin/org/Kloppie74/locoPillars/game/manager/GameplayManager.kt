package org.Kloppie74.locoPillars.game.manager

import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.game.data.Team
import org.Kloppie74.locoPillars.events.TimerEvent
import org.Kloppie74.locoPillars.utils.ItemRandomizer
import org.Kloppie74.locoPillars.utils.SchematicCageHelper
import org.Kloppie74.locoPillars.utils.spectator.listeners.SpectateListener
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import org.Kloppie74.locoPillars.database.dao.PlayerDAO
import org.Kloppie74.locoPillars.database.dao.StatsDAO
import org.Kloppie74.locoPillars.database.dao.ArenaStatsDAO
import java.io.File
import java.util.*
import kotlin.collections.HashMap

/**
 * Manages all gameplay mechanics including game start, end, and player interactions.
 *
 * CHANGELOG (v2.0):
 * - Replaced all StatsEventTracker and statsTable calls with DAO pattern
 * - Added proper error handling for all database operations
 * - Improved logging with ✓/✗/❌ indicators
 * - Separated database logic into helper methods
 * - Enhanced stat tracking with try-catch blocks
 * - Added null-safety checks for all player lookups
 * - Refactored achievement + stats wrapper methods
 * - Better separation of concerns (DB operations vs game logic)
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
class GameplayManager(
    private val plugin: LocoPillars,
    private val timerEvent: TimerEvent,
    private val configManager: ConfigManager
) {
    private val gameTimers = mutableMapOf<UUID, BukkitRunnable>()
    var spectateListener: SpectateListener? = null

    private val instanceItemDistribution = mutableMapOf<UUID, MutableSet<UUID>>()
    private val achievementListener get() = plugin.achievementListener

    // Track start time per instance for game duration
    private val gameStartTimes = HashMap<UUID, Long>()

    private fun debugLog(message: String) {
        if (configManager.debugMode) {
            plugin.logger.info("[GameplayManager] $message")
        }
    }

    // =================== ACHIEVEMENT & STATS WRAPPERS ===================

    fun onGameWon(player: Player, gameTime: Long? = null) {
        achievementListener.onGameWon(player)
        achievementListener.onWinGame(player)

        updatePlayerStats(player, "game_won") { playerId ->
            StatsDAO.incrementStat(playerId, "games_won")
            StatsDAO.addWin(playerId)
            if (gameTime != null) {
                StatsDAO.updateFastestWin(playerId, gameTime)
            }
        }
    }

    fun onGameLost(player: Player, gameTime: Long? = null) {
        achievementListener.onGameLost(player)
        achievementListener.onLoseGame(player)

        updatePlayerStats(player, "game_lost") { playerId ->
            StatsDAO.incrementStat(playerId, "games_lost")
            StatsDAO.addLoss(playerId)
            if (gameTime != null) {
                StatsDAO.updateLongestGame(playerId, gameTime)
            }
        }
    }

    fun onGameQuit(player: Player) {
        achievementListener.onGameQuit(player)

        updatePlayerStats(player, "game_quit") { playerId ->
            StatsDAO.incrementStat(playerId, "games_quit")
        }
    }

    fun onDoubleKill(player: Player) {
        achievementListener.onDoubleKill(player)

        updatePlayerStats(player, "double_kill") { playerId ->
            StatsDAO.incrementStat(playerId, "double_kills")
        }
    }

    fun onTripleKill(player: Player) {
        achievementListener.onTripleKill(player)

        updatePlayerStats(player, "triple_kill") { playerId ->
            StatsDAO.incrementStat(playerId, "triple_kills")
        }
    }

    fun onQuadKill(player: Player) {
        achievementListener.onQuadKill(player)

        updatePlayerStats(player, "quad_kill") { playerId ->
            StatsDAO.incrementStat(playerId, "quad_kills")
        }
    }

    fun onPerfectGame(player: Player) {
        achievementListener.onPerfectGame(player)

        updatePlayerStats(player, "perfect_game") { playerId ->
            StatsDAO.incrementStat(playerId, "perfect_games")
        }
    }

    fun onComebackWin(player: Player) {
        achievementListener.onComebackWin(player)

        updatePlayerStats(player, "comeback_win") { playerId ->
            StatsDAO.incrementStat(playerId, "comeback_wins")
        }
    }

    fun onFastestWin(player: Player, winTime: Long) {
        achievementListener.onFastestWin(player)

        updatePlayerStats(player, "fastest_win") { playerId ->
            StatsDAO.updateFastestWin(playerId, winTime)
        }
    }

    fun onLongestGame(player: Player, gameTime: Long) {
        achievementListener.onLongestGame(player)

        updatePlayerStats(player, "longest_game") { playerId ->
            StatsDAO.updateLongestGame(playerId, gameTime)
        }
    }

    fun onKillStreak(player: Player, amount: Int = 1) {
        achievementListener.onKillStreak(player, amount)

        updatePlayerStats(player, "kill_streak") { playerId ->
            StatsDAO.addKill(playerId)
        }
    }

    fun onWinStreak(player: Player, amount: Int = 1) {
        achievementListener.onWinStreak(player, amount)

        updatePlayerStats(player, "win_streak") { playerId ->
            StatsDAO.addWin(playerId)
        }
    }

    fun onLossStreak(player: Player, amount: Int = 1) {
        achievementListener.onLossStreak(player, amount)

        updatePlayerStats(player, "loss_streak") { playerId ->
            StatsDAO.addLoss(playerId)
        }
    }

    // =================== GAME DURATION TRACKING ===================

    fun markGameStart(instance: GameInstance) {
        gameStartTimes[instance.id] = System.currentTimeMillis()
    }

    fun getGameDuration(instance: GameInstance): Long {
        val startTime = gameStartTimes[instance.id]
        return if (startTime != null) {
            (System.currentTimeMillis() - startTime) / 1000 // seconds
        } else 0
    }

    fun removeGameStart(instance: GameInstance) {
        gameStartTimes.remove(instance.id)
    }

    // =================== LOBBY & GAME START ===================

    fun startLobbyCountdown(instance: GameInstance) {
        debugLog("Starting lobby countdown for instance ${instance.id}")

        if (instance.state != GameState.WAITING) {
            debugLog("Cannot start countdown, instance state is: ${instance.state}")
            return
        }

        instance.state = GameState.STARTING
        plugin.arenaManager.occupyInstance(instance.arenaInstance)

        val lobbyTimer = instance.arenaInstance.config.getLobbyTimer()

        val timer = object : BukkitRunnable() {
            var timeLeft = lobbyTimer

            override fun run() {
                if (instance.players.size < instance.getMinPlayers()) {
                    instance.state = GameState.WAITING
                    broadcastToGame(instance, "${ChatColor.RED}Not enough players! Countdown cancelled.")
                    cancel()
                    return
                }

                when (timeLeft) {
                    in arrayOf(10, 5, 4, 3, 2, 1) -> {
                        broadcastToGame(instance, "${ChatColor.YELLOW}Game starting in ${timeLeft} seconds!")
                        playersInGame(instance).forEach { player ->
                            player.sendTitle("${ChatColor.YELLOW}Game starting in:", "${ChatColor.WHITE}$timeLeft", 0, 20, 0)
                        }
                    }
                    0 -> {
                        startGame(instance)
                        cancel()
                        return
                    }
                }

                timeLeft--
            }
        }

        timer.runTaskTimer(plugin, 0L, 20L)
        gameTimers[instance.id] = timer
    }

    fun startGame(instance: GameInstance) {
        debugLog("Starting game for instance ${instance.id}")

        instance.state = GameState.ACTIVE
        broadcastToGame(instance, "${ChatColor.GREEN}Game started! Fight for victory!")

        plugin.spectatorInventoryManager.updateVisibilityForGameInstance(instance)

        clearSpawnAreasForGame(instance)

        val gameListener = plugin.gameListener
        gameListener.clearAllPlayerPlacedBlocks(instance.id)

        assignPlayersToTeams(instance)

        startCageTimer(instance)

        val gameTimer = instance.arenaInstance.config.getGameTimer()
        timerEvent.startGameTimer(instance, gameTimer)

        updateGameStartStats(instance)
        markGameStart(instance)

        debugLog("Game ${instance.id} started successfully")
    }

    private fun updateGameStartStats(instance: GameInstance) {
        try {
            val arenaName = instance.arenaInstance.config.name

            // Update arena stats
            val arenaUpdated = ArenaStatsDAO.createOrUpdate(arenaName, instance.getDisplayName())
            if (arenaUpdated) {
                debugLog("✓ Updated arena stats for: $arenaName")
            }

            // Update player stats
            var playersUpdated = 0
            instance.players.forEach { playerUUID ->
                val player = Bukkit.getPlayer(playerUUID)
                if (player != null) {
                    val playerId = PlayerDAO.getPlayerId(player.name)
                    if (playerId != null) {
                        // Increment games played
                        val gamesPlayedIncremented = StatsDAO.incrementStat(playerId, "games_played")
                        if (gamesPlayedIncremented) {
                            playersUpdated++
                        }

                        // Trigger achievement
                        achievementListener.onGamePlayed(player)
                    }
                }
            }

            debugLog("✓ Updated game start stats - Arena: $arenaName, Players: $playersUpdated/${instance.players.size}")

        } catch (ex: Exception) {
            plugin.logger.severe("[GameplayManager] ❌ Error updating game start stats: ${ex.message}")
            ex.printStackTrace()
        }
    }

    private fun startCageTimer(instance: GameInstance) {
        val cageTimer = instance.arenaInstance.config.getCageTimer()

        val timer = object : BukkitRunnable() {
            var timeLeft = cageTimer

            override fun run() {
                when (timeLeft) {
                    in arrayOf(5, 4, 3, 2, 1) -> {
                        broadcastToGame(instance, "${ChatColor.YELLOW}Cages open in ${timeLeft} seconds!")
                        playersInGame(instance).forEach { player ->
                            player.sendTitle("${ChatColor.YELLOW}Game starting in:", "${ChatColor.WHITE}$timeLeft", 0, 20, 0)
                        }
                    }
                    0 -> {
                        broadcastToGame(instance, "${ChatColor.GREEN}Cages opened! The battle begins!")
                        playersInGame(instance).forEach { player ->
                            player.sendTitle("${ChatColor.GREEN}GO!", "", 0, 20, 10)
                            player.removePotionEffect(PotionEffectType.SLOWNESS)
                            player.removePotionEffect(PotionEffectType.JUMP_BOOST)

                            val gameListener = plugin.gameListener
                            gameListener.setCageProtection(player.uniqueId, false)
                        }

                        removeCagesForInstance(instance)

                        val itemTimer = instance.arenaInstance.config.getItemTimer()
                        timerEvent.startItemTimer(instance, itemTimer)
                        cancel()
                        return
                    }
                }

                timeLeft--
            }
        }

        timer.runTaskTimer(plugin, 0L, 20L)
    }

    // =================== TEAM ASSIGNMENT ===================

    private fun assignPlayersToTeams(instance: GameInstance) {
        debugLog("Assigning players to teams")

        val teams = instance.arenaInstance.config.teams
        val players = instance.players.toList()
        val playersPerTeam = instance.arenaInstance.config.getPlayersPerTeam()

        var playerIndex = 0
        var teamIndex = 0

        while (playerIndex < players.size && teamIndex < teams.size) {
            val team = teams[teamIndex]
            val teamPlayers = mutableListOf<UUID>()

            repeat(minOf(playersPerTeam, team.spawns.size, players.size - playerIndex)) {
                teamPlayers.add(players[playerIndex])
                playerIndex++
            }

            teamPlayers.forEachIndexed { spawnIndex, playerId ->
                val player = Bukkit.getPlayer(playerId)
                if (player != null && player.isOnline) {
                    val spawnLocation = team.spawns[spawnIndex].toAbsolute(instance.arenaInstance.origin)
                    player.teleport(spawnLocation)

                    // Track team assignment in GameInstance
                    instance.trackTeamAssignment(playerId, team)

                    placeCageAtSpawnLocation(player.uniqueId, spawnLocation)

                    setupPlayerForTeam(player, team)
                    applyCageEffects(player)

                    val gameListener = plugin.gameListener
                    gameListener.setCageProtection(player.uniqueId, true)

                    player.sendMessage("${ChatColor.GREEN}You are on team: ${team.name}")

                    debugLog("Player ${player.name} assigned to team ${team.name}")
                }
            }

            teamIndex++
        }

        debugLog("Team assignment completed. Teams with players:")
        teams.forEach { team ->
            val teamPlayers = instance.getPlayersInTeam(team)
            debugLog("  ${team.name}: ${teamPlayers.size} players")
        }
    }

    private fun placeCageAtSpawnLocation(playerUUID: UUID, spawnLocation: org.bukkit.Location) {
        val cagesFolder = File(plugin.dataFolder, "Cages")
        if (!cagesFolder.exists()) {
            cagesFolder.mkdirs()
            debugLog("Created Cages folder: ${cagesFolder.absolutePath}")
            debugLog("Please add Standard-Cage.schem to this folder")
            return
        }

        val cageFile = File(cagesFolder, "Standard-Cage.schem")
        if (!cageFile.exists()) {
            debugLog("Standard cage file not found: ${cageFile.absolutePath}")
            return
        }

        val success = SchematicCageHelper.pasteCageForPlayer(
            plugin,
            configManager,
            playerUUID,
            cageFile,
            spawnLocation
        )

        if (success) {
            debugLog("✅ Cage placed at spawn for UUID: $playerUUID")
        } else {
            plugin.logger.severe("[GameplayManager] ❌ Failed to place cage for UUID: $playerUUID")
        }
    }

    private fun removeCagesForInstance(instance: GameInstance) {
        debugLog("Removing cages for instance ${instance.id}")

        var undoCount = 0
        instance.players.forEach { playerUUID ->
            val player = Bukkit.getPlayer(playerUUID)
            val playerName = player?.name ?: "Unknown"

            if (SchematicCageHelper.hasCageSessions(playerUUID)) {
                val success = SchematicCageHelper.undoCageForPlayer(plugin, configManager, playerUUID)
                if (success) {
                    undoCount++
                    debugLog("✅ Cage removed for: $playerName")
                } else {
                    plugin.logger.severe("[GameplayManager] ❌ Failed to remove cage for: $playerName")
                    val forceSuccess = SchematicCageHelper.forceRemoveCageForPlayer(plugin, configManager, playerUUID)
                    if (forceSuccess) {
                        debugLog("✅ Cage force removed for: $playerName")
                    } else {
                        plugin.logger.severe("[GameplayManager] ❌ Force cage removal also failed for: $playerName")
                    }
                }
            }
        }
        debugLog("Cage removal completed: $undoCount/${instance.players.size} cages removed")
    }

    private fun setupPlayerForTeam(player: Player, team: Team) {
        val helmet = ItemStack(Material.LEATHER_HELMET)
        val chestplate = ItemStack(Material.LEATHER_CHESTPLATE)
        val leggings = ItemStack(Material.LEATHER_LEGGINGS)
        val boots = ItemStack(Material.LEATHER_BOOTS)

        val leatherMeta = helmet.itemMeta as org.bukkit.inventory.meta.LeatherArmorMeta
        val bukkitColor = team.color.toBukkitColor()
        leatherMeta.setColor(bukkitColor)

        helmet.itemMeta = leatherMeta
        chestplate.itemMeta = leatherMeta.clone()
        leggings.itemMeta = leatherMeta.clone()
        boots.itemMeta = leatherMeta.clone()

        player.equipment?.helmet = helmet
        player.equipment?.chestplate = chestplate
        player.equipment?.leggings = leggings
        player.equipment?.boots = boots

        player.inventory.clear()
    }

    private fun applyCageEffects(player: Player) {
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, Int.MAX_VALUE, 255, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, Int.MAX_VALUE, 200, false, false))
    }

    // =================== ITEM DISTRIBUTION ===================

    fun giveItemsToPlayers(instance: GameInstance) {
        val playersInGame = playersInGame(instance)
        val itemRandomizer = ItemRandomizer(instance.arenaInstance.config.getAsFileConfiguration())

        val playersWhoReceivedItems = instanceItemDistribution.computeIfAbsent(instance.id) { mutableSetOf() }

        if (playersWhoReceivedItems.size >= playersInGame.size) {
            playersWhoReceivedItems.clear()
            debugLog("All players received items, starting new distribution cycle for instance ${instance.id}")
        }

        playersInGame.forEach { player ->
            val randomItem = itemRandomizer.getRandomItem()
            val isCommandItem = randomItem.type == Material.COMMAND_BLOCK

            if (!playersWhoReceivedItems.contains(player.uniqueId)) {
                if (isCommandItem) {
                    itemRandomizer.executeRandomAction(player.name)
                } else {
                    val itemCopy = randomItem.clone()
                    itemCopy.amount = 1
                    player.inventory.addItem(itemCopy)
                }
                playersWhoReceivedItems.add(player.uniqueId)
            }
        }

        debugLog("Item distribution: ${playersWhoReceivedItems.size}/${playersInGame.size} players received items this cycle")
    }

    // =================== PLAYER DEATH & ELIMINATION ===================

    fun handlePlayerDeath(player: Player, playerToInstance: MutableMap<UUID, GameInstance>, playerManager: PlayerManager): Boolean {
        val instance = playerToInstance[player.uniqueId] ?: return false

        if (instance.state != GameState.ACTIVE) return false

        val gameListener = plugin.gameListener
        gameListener.setCageProtection(player.uniqueId, false)

        if (SchematicCageHelper.hasCageSessions(player.uniqueId)) {
            SchematicCageHelper.clearCageInfoForPlayer(plugin, configManager, player.uniqueId)
        }

        instance.players.remove(player.uniqueId)
        playerToInstance.remove(player.uniqueId)

        broadcastToGame(instance, "${ChatColor.RED}${player.name} has been eliminated!")
        player.sendMessage("${ChatColor.RED}You have been eliminated! Better luck next time!")

        // Update elimination stats (death only, not game loss)
        updatePlayerEliminationStats(player, instance)

        SpectatorUtils.setPlayerAsSpectator(player, plugin)
        val gameLobbySpawn = instance.arenaInstance.config.getSpawnTeleportLocation(instance.arenaInstance.origin)
        player.teleport(gameLobbySpawn)

        playerManager.restorePlayerState(player)

        val spectateListener = plugin.spectateListener
        instance.players.forEach { uuid ->
            val p = Bukkit.getPlayer(uuid)
            if (p != null) spectateListener?.refreshPlayer(p)
        }
        spectateListener?.refreshPlayer(player)

        when {
            instance.players.isEmpty() -> {
                endGame(instance, "No players remaining!", playerToInstance)
            }
            instance.players.size >= 1 && checkOneTeamLeft(instance) -> {
                val winners = instance.players.mapNotNull { Bukkit.getPlayer(it) }
                val teamName = getTeamName(instance, winners)
                endGame(instance, "${teamName ?: winners.firstOrNull()?.name ?: "Team"} wins!", playerToInstance)
            }
            instance.players.size == 1 -> {
                val winner = Bukkit.getPlayer(instance.players.first())
                if (winner != null) {
                    endGame(instance, "${winner.name} wins!", playerToInstance)
                } else {
                    endGame(instance, "Game ended!", playerToInstance)
                }
            }
        }

        return true
    }

    private fun updatePlayerEliminationStats(player: Player, instance: GameInstance) {
        try {
            val playerId = PlayerDAO.getPlayerId(player.name)
            if (playerId == null) {
                plugin.logger.warning("[GameplayManager] ✗ Could not find playerId for ${player.name}")
                return
            }

            // Add death (but NOT game loss - that happens at game end)
            val deathAdded = StatsDAO.addDeath(playerId)
            if (deathAdded) {
                debugLog("✓ Recorded elimination death for ${player.name}")
            }

            // Trigger achievement
            achievementListener.onDeath(player)

            // Update player arena stats
            val arenaName = instance.arenaInstance.config.name
            val arenaStatsUpdated = ArenaStatsDAO.createOrUpdatePlayerArenaStats(playerId, arenaName)
            if (arenaStatsUpdated) {
                debugLog("✓ Updated arena stats for ${player.name}")
            }

        } catch (ex: Exception) {
            plugin.logger.severe("[GameplayManager] ❌ Error updating elimination stats for ${player.name}: ${ex.message}")
            ex.printStackTrace()
        }
    }

    fun handlePlayerKill(killer: Player, victim: Player) {
        try {
            val killerId = PlayerDAO.getPlayerId(killer.name)
            val victimId = PlayerDAO.getPlayerId(victim.name)

            if (killerId != null) {
                val killAdded = StatsDAO.addKill(killerId)
                if (killAdded) {
                    debugLog("✓ Recorded kill for ${killer.name}")
                    achievementListener.onKillPlayer(killer)
                }
            } else {
                plugin.logger.warning("[GameplayManager] ✗ Could not find killerId for ${killer.name}")
            }

            if (victimId != null) {
                val deathAdded = StatsDAO.addDeath(victimId)
                if (deathAdded) {
                    debugLog("✓ Recorded death for ${victim.name}")
                    achievementListener.onDeath(victim)
                }
            } else {
                plugin.logger.warning("[GameplayManager] ✗ Could not find victimId for ${victim.name}")
            }

        } catch (ex: Exception) {
            plugin.logger.severe("[GameplayManager] ❌ Error updating kill/death stats: ${ex.message}")
            ex.printStackTrace()
        }
    }

    fun handlePlayerAssist(assistant: Player) {
        updatePlayerStats(assistant, "assist") { playerId ->
            StatsDAO.addAssist(playerId)
            achievementListener.onAssist(assistant)
        }
    }

    fun handleDamageDealt(player: Player, amount: Double) {
        updatePlayerStats(player, "damage_dealt") { playerId ->
            StatsDAO.addDamageDealt(playerId, amount)
            achievementListener.onDamageDealt(player, amount.toInt())
        }
    }

    fun handleDamageTaken(player: Player, amount: Double) {
        updatePlayerStats(player, "damage_taken") { playerId ->
            StatsDAO.addDamageTaken(playerId, amount)
            achievementListener.onDamageTaken(player, amount.toInt())
        }
    }

    fun handleFallDamageTaken(player: Player, amount: Double) {
        updatePlayerStats(player, "fall_damage") { playerId ->
            StatsDAO.addFallDamage(playerId, amount)
            achievementListener.onFallDamageTaken(player, amount.toInt())
        }
    }

    // =================== GAME END ===================

    fun endGameByTimer(instance: GameInstance, playerToInstance: MutableMap<UUID, GameInstance>, playerManager: PlayerManager) {
        debugLog("Game ${instance.id} ended by timer")

        instance.players.forEach { playerId ->
            val player = Bukkit.getPlayer(playerId)
            if (player != null) {
                debugLog("Player ${player.name} survived to timeout")
            }
        }

        endGame(instance, "Time's up! Game ended in a draw!", playerToInstance)
    }

    fun endGame(instance: GameInstance, reason: String, playerToInstance: MutableMap<UUID, GameInstance>) {
        debugLog("Ending game ${instance.id}: $reason")

        val durationSeconds = getGameDuration(instance)
        instance.state = GameState.ENDED

        timerEvent.stopAllTimers(instance.id)
        gameTimers[instance.id]?.cancel()
        gameTimers.remove(instance.id)
        removeGameStart(instance)

        instanceItemDistribution.remove(instance.id)

        val gameListener = plugin.gameListener
        instance.players.forEach { playerUUID ->
            gameListener.setCageProtection(playerUUID, false)
        }

        instance.players.forEach { playerUUID ->
            if (SchematicCageHelper.hasCageSessions(playerUUID)) {
                SchematicCageHelper.clearCageInfoForPlayer(plugin, configManager, playerUUID)
            }
        }

        gameListener.clearAllPlayerPlacedBlocks(instance.id)

        broadcastToGame(instance, "${ChatColor.GOLD}=== GAME OVER ===")
        broadcastToGame(instance, "${ChatColor.YELLOW}$reason")

        // Determine winners and losers
        val winners = getWinningPlayers(instance)
        if (winners.isNotEmpty()) {
            winners.forEach { winner ->
                onGameWon(winner, durationSeconds)
                winner.gameMode = GameMode.ADVENTURE
                winner.isInvulnerable = true
                winner.fallDistance = 0f
            }
            winners.forEach { winner ->
                onFastestWin(winner, durationSeconds)
                onLongestGame(winner, durationSeconds)
                onWinStreak(winner)
            }
        }

        // All others are losers
        val losers = instance.players.mapNotNull { Bukkit.getPlayer(it) }.filter { !winners.contains(it) }
        losers.forEach { loser ->
            onGameLost(loser, durationSeconds)
            onLossStreak(loser)
            SpectatorUtils.setPlayerAsSpectator(loser, plugin)
            val gameLobbySpawn = instance.arenaInstance.config.getSpawnTeleportLocation(instance.arenaInstance.origin)
            loser.teleport(gameLobbySpawn)
        }

        updateGameEndStats(instance)

        // Restore state for all players
        instance.players.forEach { playerId ->
            val player = Bukkit.getPlayer(playerId)
            if (player != null && player.isOnline) {
                val playerManager = PlayerManager(plugin)
                playerManager.restorePlayerState(player)
                val spectateListener = plugin.spectateListener
                spectateListener?.refreshPlayer(player)
            }
        }

        val spectateListener = plugin.spectateListener
        instance.players.forEach { uuid ->
            val p = Bukkit.getPlayer(uuid)
            if (p != null) spectateListener?.refreshPlayer(p)
            playerToInstance.remove(uuid)
        }

        instance.players.clear()

        object : BukkitRunnable() {
            override fun run() {
                resetInstance(instance)
            }
        }.runTaskLater(plugin, 100L)
    }

    private fun getWinningPlayers(instance: GameInstance): List<Player> {
        if (instance.players.isEmpty()) return emptyList()

        val remainingTeams = instance.getRemainingTeams()
        if (remainingTeams.size != 1) {
            return instance.players.mapNotNull { Bukkit.getPlayer(it) }
        }

        val winningTeam = remainingTeams.first()
        return instance.getAlivePlayersInTeam(winningTeam).mapNotNull { Bukkit.getPlayer(it) }
    }

    private fun checkOneTeamLeft(instance: GameInstance): Boolean {
        val remainingTeams = instance.getRemainingTeams()
        return remainingTeams.size == 1
    }

    private fun getTeamName(instance: GameInstance, players: List<Player>): String? {
        if (players.isEmpty()) return null
        val firstPlayerTeam = instance.getPlayerTeam(players.first().uniqueId)
        return firstPlayerTeam?.name
    }

    private fun updateGameEndStats(instance: GameInstance) {
        try {
            val arenaName = instance.arenaInstance.config.name
            val updated = ArenaStatsDAO.createOrUpdate(arenaName, instance.getDisplayName())
            if (updated) {
                debugLog("✓ Updated game end stats for arena: $arenaName")
            } else {
                plugin.logger.warning("[GameplayManager] ✗ Failed to update game end stats for arena: $arenaName")
            }
        } catch (ex: Exception) {
            plugin.logger.severe("[GameplayManager] ❌ Error updating game end stats: ${ex.message}")
            ex.printStackTrace()
        }
    }

    private fun resetInstance(instance: GameInstance) {
        instance.state = GameState.RESETTING
        plugin.arenaManager.releaseInstance(instance.arenaInstance)
        instance.reset()
    }

    // =================== UTILITY METHODS ===================

    fun cancelCountdown(instanceId: UUID) {
        gameTimers[instanceId]?.cancel()
        gameTimers.remove(instanceId)
    }

    private fun broadcastToGame(instance: GameInstance, message: String) {
        playersInGame(instance).forEach { player -> player.sendMessage(message) }
    }

    private fun playersInGame(instance: GameInstance): List<Player> {
        return instance.players.mapNotNull { playerId ->
            Bukkit.getPlayer(playerId)?.takeIf { it.isOnline }
        }
    }

    private fun clearSpawnAreasForGame(instance: GameInstance) {
        if (instance.arenaInstance.config.hasSpawnArea()) {
            debugLog("Clearing spawn area for game start")
            val spawnAreaManager = org.Kloppie74.locoPillars.game.manager.SpawnAreaManager(plugin)
            if (spawnAreaManager.validateSpawnArea(instance)) {
                spawnAreaManager.clearSpawnArea(instance)
            } else {
                debugLog("Spawn area validation failed, skipping clear")
            }
        }
    }

    /**
     * Helper method to update player stats with proper error handling.
     */
    private fun updatePlayerStats(player: Player, statType: String, statsUpdate: (String) -> Unit) {
        try {
            val playerId = PlayerDAO.getPlayerId(player.name)
            if (playerId != null) {
                statsUpdate(playerId)
                debugLog("✓ Updated $statType for ${player.name}")
            } else {
                plugin.logger.warning("[GameplayManager] ✗ Could not find playerId for ${player.name} ($statType)")
            }
        } catch (ex: Exception) {
            plugin.logger.severe("[GameplayManager] ❌ Error updating $statType for ${player.name}: ${ex.message}")
            ex.printStackTrace()
        }
    }

    fun cleanup() {
        gameTimers.values.forEach { it.cancel() }
        gameTimers.clear()
        instanceItemDistribution.clear()
        SchematicCageHelper.cleanupAllCageSessions(plugin, configManager)
        val gameListener = plugin.gameListener
        if (gameListener != null) {
            debugLog("Clearing all cage protection during cleanup")
        }
        gameStartTimes.clear()
    }
}