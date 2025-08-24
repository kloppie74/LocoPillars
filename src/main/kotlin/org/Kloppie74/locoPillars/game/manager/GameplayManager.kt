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
import org.Kloppie74.locoPillars.utils.achievements.AchievementListener
import org.Kloppie74.locoPillars.utils.spectator.listeners.SpectateListener
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import org.Kloppie74.locoPillars.config.ConfigManager
import java.io.File
import java.util.*
import kotlin.collections.HashMap

class GameplayManager(
    private val plugin: LocoPillars,
    private val timerEvent: TimerEvent,
    private val configManager: ConfigManager
) {
    private val gameTimers = mutableMapOf<UUID, BukkitRunnable>()
    var spectateListener: SpectateListener? = null

    private val instanceItemDistribution = mutableMapOf<UUID, MutableSet<UUID>>()
    private val achievementListener: AchievementListener
        get() = plugin.achievementListener
    private val statsTable get() = plugin.databaseManager.statsTable

    // Track start time per instance for game duration
    private val gameStartTimes = HashMap<UUID, Long>()

    private fun debugLog(message: String) {
        if (configManager.debugMode) {
            plugin.logger.info("[GameplayManager] $message")
        }
    }

    // ---- ACHIEVEMENT & STATS WRAPPERS ----

    fun onGameWon(player: Player, gameTime: Long? = null) {
        achievementListener.onGameWon(player)
        achievementListener.onWinGame(player)
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) {
            statsTable.incrementGamesWon(playerId)
            statsTable.addWin(playerId)
            if (gameTime != null) statsTable.updateFastestWin(playerId, gameTime)
        }
    }

    fun onGameLost(player: Player, gameTime: Long? = null) {
        achievementListener.onGameLost(player)
        achievementListener.onLoseGame(player)
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) {
            statsTable.incrementGamesLost(playerId)
            statsTable.addLoss(playerId)
            if (gameTime != null) statsTable.updateLongestGame(playerId, gameTime)
        }
    }

    fun onGameQuit(player: Player) {
        achievementListener.onGameQuit(player)
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) statsTable.incrementGamesQuit(playerId)
    }

    fun onDoubleKill(player: Player) {
        achievementListener.onDoubleKill(player)
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) statsTable.addDoubleKill(playerId)
    }

    fun onTripleKill(player: Player) {
        achievementListener.onTripleKill(player)
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) statsTable.addTripleKill(playerId)
    }

    fun onQuadKill(player: Player) {
        achievementListener.onQuadKill(player)
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) statsTable.addQuadKill(playerId)
    }

    fun onPerfectGame(player: Player) {
        achievementListener.onPerfectGame(player)
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) statsTable.addPerfectGame(playerId)
    }

    fun onComebackWin(player: Player) {
        achievementListener.onComebackWin(player)
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) statsTable.addComebackWin(playerId)
    }

    fun onFastestWin(player: Player, winTime: Long) {
        achievementListener.onFastestWin(player)
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) statsTable.updateFastestWin(playerId, winTime)
    }

    fun onLongestGame(player: Player, gameTime: Long) {
        achievementListener.onLongestGame(player)
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) statsTable.updateLongestGame(playerId, gameTime)
    }

    fun onKillStreak(player: Player, amount: Int = 1) {
        achievementListener.onKillStreak(player, amount)
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) statsTable.addKill(playerId)
    }

    fun onWinStreak(player: Player, amount: Int = 1) {
        achievementListener.onWinStreak(player, amount)
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) statsTable.addWin(playerId)
    }

    fun onLossStreak(player: Player, amount: Int = 1) {
        achievementListener.onLossStreak(player, amount)
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) statsTable.addLoss(playerId)
    }

    // ---- Game duration tracking ----
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

    // ---- REST VAN DE CLASS ----

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
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val arenaName = instance.arenaInstance.config.name

        statsTracker.createOrUpdateArenaStats(arenaName, instance.getDisplayName())

        instance.players.forEach { playerId ->
            val player = Bukkit.getPlayer(playerId)
            if (player != null) {
                val playerDbId = statsTracker.getPlayerIdByName(player.name)
                if (playerDbId != null) {
                    statsTable.incrementGamesPlayed(playerDbId)
                    achievementListener.onGamePlayed(player)
                }
            }
        }

        debugLog("Updated database stats for game start - Arena: $arenaName, Players: ${instance.players.size}")
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

                    placeCageAtSpawnLocation(player.uniqueId, spawnLocation)

                    setupPlayerForTeam(player, team)
                    applyCageEffects(player)

                    val gameListener = plugin.gameListener
                    gameListener.setCageProtection(player.uniqueId, true)

                    player.sendMessage("${ChatColor.GREEN}You are on team: ${team.name}")
                }
            }

            teamIndex++
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

        // FIXED: Only update elimination stats here, not game loss
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
            // If only players of one team remain, that team wins
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

    // FIXED: New method for elimination stats only
    private fun updatePlayerEliminationStats(player: Player, instance: GameInstance) {
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)

        if (playerId != null) {
            statsTable.addDeath(playerId)
            // Don't increment gamesLost here - that happens when the actual game ends
            achievementListener.onDeath(player)
            val arenaName = instance.arenaInstance.config.name
            statsTracker.createOrUpdatePlayerArenaStats(playerId, arenaName)
            debugLog("Updated elimination stats for player: ${player.name}")
        }
    }

    // FIXED: Keep the original method but rename it for clarity
    private fun updatePlayerDeathStats(player: Player, instance: GameInstance) {
        // This method is now only for actual game loss, not elimination
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)

        if (playerId != null) {
            statsTable.addDeath(playerId)
            statsTable.incrementGamesLost(playerId)
            achievementListener.onDeath(player)
            val arenaName = instance.arenaInstance.config.name
            statsTracker.createOrUpdatePlayerArenaStats(playerId, arenaName)
            debugLog("Updated death/game loss stats for player: ${player.name}")
        }
    }

    fun handlePlayerKill(killer: Player, victim: Player) {
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val killerId = statsTracker.getPlayerIdByName(killer.name)
        val victimId = statsTracker.getPlayerIdByName(victim.name)

        if (killerId != null) {
            statsTable.addKill(killerId)
            achievementListener.onKillPlayer(killer)
            debugLog("Updated kill stats for player: ${killer.name}")
        }
        if (victimId != null) {
            statsTable.addDeath(victimId)
            achievementListener.onDeath(victim)
            debugLog("Updated death stats for player: ${victim.name}")
        }
    }

    fun handlePlayerAssist(assistant: Player) {
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val assistantId = statsTracker.getPlayerIdByName(assistant.name)
        if (assistantId != null) {
            statsTable.addAssist(assistantId)
            achievementListener.onAssist(assistant)
            debugLog("Updated assist stats for player: ${assistant.name}")
        }
    }

    fun handleDamageDealt(player: Player, amount: Double) {
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) {
            statsTable.addDamageDealt(playerId, amount)
            achievementListener.onDamageDealt(player, amount.toInt())
        }
    }

    fun handleDamageTaken(player: Player, amount: Double) {
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) {
            statsTable.addDamageTaken(playerId, amount)
            achievementListener.onDamageTaken(player, amount.toInt())
        }
    }

    fun handleFallDamageTaken(player: Player, amount: Double) {
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val playerId = statsTracker.getPlayerIdByName(player.name)
        if (playerId != null) {
            statsTable.addFallDamage(playerId, amount)
            achievementListener.onFallDamageTaken(player, amount.toInt())
        }
    }

    fun endGameByTimer(instance: GameInstance, playerToInstance: MutableMap<UUID, GameInstance>, playerManager: PlayerManager) {
        debugLog("Game ${instance.id} ended by timer")

        instance.players.forEach { playerId ->
            val player = Bukkit.getPlayer(playerId)
            if (player != null) {
                val playerDbId = plugin.databaseManager.StatsEventTracker.getPlayerIdByName(player.name)
                if (playerDbId != null) {
                    debugLog("Player ${player.name} survived to timeout")
                }
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

        // --- WIN LOGIC ---
        val winners = getWinningPlayers(instance)
        if (winners.isNotEmpty()) {
            winners.forEach { winner ->
                onGameWon(winner, durationSeconds)
                winner.gameMode = GameMode.ADVENTURE
                winner.isInvulnerable = true
                winner.fallDistance = 0f
                // NIET teleporteren!
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
            loser.teleport(gameLobbySpawn) // Alleen verliezers teleporteren!
        }

        updateGameEndStats(instance)

        // Restore state voor alle spelers (optioneel: alleen voor verliezers)
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
        // Find the team of the first remaining player
        val firstPlayer = instance.players.firstOrNull() ?: return emptyList()
        val playerObj = Bukkit.getPlayer(firstPlayer) ?: return emptyList()
        val teamName = getTeamName(instance, listOf(playerObj)) ?: return listOf(playerObj)
        // All remaining players in the same team win
        val winners = instance.players.mapNotNull { Bukkit.getPlayer(it) }
            .filter { getTeamName(instance, listOf(it)) == teamName }
        return winners
    }

    private fun checkOneTeamLeft(instance: GameInstance): Boolean {
        val teamsLeft = instance.players.mapNotNull { Bukkit.getPlayer(it) }
            .map { getTeamName(instance, listOf(it)) }
            .distinct()
            .filterNotNull()
        return teamsLeft.size == 1
    }

    private fun getTeamName(instance: GameInstance, players: List<Player>): String? {
        val origin = instance.arenaInstance.origin
        val teams = instance.arenaInstance.config.teams
        return teams.find { team ->
            players.all { player ->
                team.spawns.any { spawn -> player.location.distance(spawn.toAbsolute(origin)) < 2.0 }
            }
        }?.name
    }

    private fun updateGameEndStats(instance: GameInstance) {
        val statsTracker = plugin.databaseManager.StatsEventTracker
        val arenaName = instance.arenaInstance.config.name
        statsTracker.createOrUpdateArenaStats(arenaName, instance.getDisplayName())
        debugLog("Updated game end stats for arena: $arenaName")
    }

    private fun resetInstance(instance: GameInstance) {
        instance.state = GameState.RESETTING
        plugin.arenaManager.releaseInstance(instance.arenaInstance)
        instance.reset()
    }

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