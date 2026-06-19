package org.Kloppie74.locoPillars.game.manager

import org.bukkit.Bukkit
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
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.SchematicCageHelper
import org.Kloppie74.locoPillars.utils.spectator.listeners.SpectateListener
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import org.Kloppie74.locoPillars.utils.hotbar.HotbarUtils
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import org.Kloppie74.locoPillars.database.dao.PlayerDAO
import org.Kloppie74.locoPillars.database.dao.StatsDAO
import org.Kloppie74.locoPillars.database.dao.ArenaStatsDAO
import org.Kloppie74.locoPillars.database.dao.LeaderboardCacheDAO
import org.Kloppie74.locoPillars.game.rewards.RewardProcessor
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import org.bukkit.Location
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashMap

/**
 * Manages all gameplay mechanics including game start, end, and player interactions.
 *
 * @author Kloppie74
 * @since 2.0-SNAPSHOT
 */
class GameplayManager(
    private val plugin: LocoPillars,
    private val timerEvent: TimerEvent,
    private val configManager: ConfigManager
) {
    companion object {
        private const val MAX_RESPAWN_ATTEMPTS = 3
        private const val TICKS_PER_SECOND = 20L
    }

    private data class EliminatedPlayerSession(
        val instance: GameInstance,
        var respawnLocation: Location,
        var keepAsSpectator: Boolean = true,
        var respawnAttempts: Int = 0
    )

    private val gameTimers = mutableMapOf<UUID, BukkitRunnable>()
    var spectateListener: SpectateListener? = null
    private val playerManager = PlayerManager(plugin)

    private val instanceItemDistribution = mutableMapOf<UUID, MutableSet<UUID>>()
    private val achievementListener get() = plugin.achievementListener
    private val gameStartTimes = HashMap<UUID, Long>()

    // Central source of truth for eliminated players (death -> respawn -> spectator/lobby transition)
    private val eliminatedPlayerSessions = ConcurrentHashMap<UUID, EliminatedPlayerSession>()

    // Auto-queue: tracks dead players waiting to rejoin — playerId -> gameTypeName
    private val pendingAutoQueue = ConcurrentHashMap<UUID, String>()

    fun cancelAutoQueue(playerId: UUID): Boolean = pendingAutoQueue.remove(playerId) != null

    /**
     * Used when a player aborts the post-match countdown via /pillars leave. Cleans
     * up spectator state and elimination tracking, then teleports them to lobby
     * regardless of how the post-match phase had been pacing them.
     */
    fun exitPostMatchToLobby(player: Player) {
        clearEliminatedState(player.uniqueId)
        SpectatorUtils.removeSpectatorStatus(player, plugin)
        SpectatorUtils.clearCache(player)
        Bukkit.getOnlinePlayers().forEach { online ->
            online.showPlayer(plugin, player)
            player.showPlayer(plugin, online)
        }
        val lobbyRespawn = plugin.configManager.getLobbySpawnLocation()
            ?: plugin.server.worlds.first().spawnLocation
        player.teleport(lobbyRespawn)
    }

    private fun debugLog(message: String) {
        if (configManager.debugMode) plugin.logger.info("[GameplayManager] $message")
    }

    /**
     * Effective auto-queue check: server master switch AND player toggle must both be on.
     */
    fun isAutoQueueEnabled(player: Player): Boolean {
        if (!plugin.configManager.autoQueueEnabled) return false
        return plugin.playerPreferences.isAutoQueueEnabled(player.uniqueId)
    }

    /**
     * 5-second post-game countdown while the player watches as a spectator,
     * ending with an auto-queue join into a fresh instance. Falls back to a
     * lobby teleport if no compatible game is available. The player is tracked
     * in [pendingAutoQueue] so /pillars leave can cancel before it fires.
     */
    private fun scheduleAutoQueue(player: Player, gameTypeName: String, lobbyRespawn: Location) {
        val displayName = plugin.configs.firstOrNull { it.name == gameTypeName }?.getDisplayName() ?: gameTypeName
        player.sendMessage(MSG.lang("pillars.autoqueue.start", "type" to displayName))
        for (seconds in 5 downTo 1) {
            val delay = (6 - seconds) * 20L
            object : BukkitRunnable() {
                override fun run() {
                    if (!pendingAutoQueue.containsKey(player.uniqueId) || !player.isOnline) return
                    player.sendTitle("§a§l$seconds", "§7Rejoining $displayName...", 0, 22, 3)
                }
            }.runTaskLater(plugin, delay)
        }
        object : BukkitRunnable() {
            override fun run() {
                if (!pendingAutoQueue.remove(player.uniqueId, gameTypeName)) return
                if (!player.isOnline) return
                if (plugin.gameManager.getPlayerInstance(player) != null) return
                debugLog("Auto-queue firing for ${player.name} -> $gameTypeName")
                // Drop spectator status + any lingering elimination session so the join
                // flow can take over the player's gamemode / hotbar from a clean baseline.
                clearEliminatedState(player.uniqueId)
                SpectatorUtils.removeSpectatorStatus(player, plugin)
                SpectatorUtils.clearCache(player)
                Bukkit.getOnlinePlayers().forEach { online ->
                    online.showPlayer(plugin, player)
                    player.showPlayer(plugin, online)
                }
                val joined = plugin.gameManager.joinGame(player, gameTypeName)
                if (joined == null) {
                    // No game available right now — return to lobby so the player
                    // isn't left stranded in the (now-resetting) arena world.
                    player.sendMessage(MSG.lang("pillars.autoqueue.no_game", "type" to displayName))
                    player.teleport(lobbyRespawn)
                }
            }
        }.runTaskLater(plugin, 100L) // 5s
    }

    /**
     * 10-second post-game countdown for players with auto-queue OFF. They watch
     * the arena as a spectator for longer, then get returned to the main lobby.
     */
    private fun scheduleLobbyReturn(player: Player, lobbyRespawn: Location) {
        for (seconds in 10 downTo 1) {
            val delay = (11 - seconds) * 20L
            object : BukkitRunnable() {
                override fun run() {
                    if (!player.isOnline) return
                    player.sendTitle("§e§l$seconds", "§7Returning to lobby...", 0, 22, 3)
                }
            }.runTaskLater(plugin, delay)
        }
        object : BukkitRunnable() {
            override fun run() {
                if (!player.isOnline) return
                if (plugin.gameManager.getPlayerInstance(player) != null) return
                clearEliminatedState(player.uniqueId)
                SpectatorUtils.removeSpectatorStatus(player, plugin)
                SpectatorUtils.clearCache(player)
                Bukkit.getOnlinePlayers().forEach { online ->
                    online.showPlayer(plugin, player)
                    player.showPlayer(plugin, online)
                }
                player.teleport(lobbyRespawn)
            }
        }.runTaskLater(plugin, 200L) // 10s
    }


    // =================== ACHIEVEMENT & STATS WRAPPERS ===================

    fun onGameWon(player: Player, gameTime: Long? = null) {
        achievementListener.onGameWon(player)
        achievementListener.onWinGame(player)
        updatePlayerStats(player, "game_won") { playerId ->
            StatsDAO.incrementStat(playerId, "games_won")
            StatsDAO.addWin(playerId)
            if (gameTime != null) StatsDAO.updateFastestWin(playerId, gameTime)
        }
    }

    fun onGameLost(player: Player, gameTime: Long? = null) {
        achievementListener.onGameLost(player)
        achievementListener.onLoseGame(player)
        updatePlayerStats(player, "game_lost") { playerId ->
            StatsDAO.incrementStat(playerId, "games_lost")
            StatsDAO.addLoss(playerId)
            if (gameTime != null) StatsDAO.updateLongestGame(playerId, gameTime)
        }
    }

    fun onGameQuit(player: Player) {
        achievementListener.onGameQuit(player)
        updatePlayerStats(player, "game_quit") { playerId -> StatsDAO.incrementStat(playerId, "games_quit") }
    }

    fun onDoubleKill(player: Player) {
        achievementListener.onDoubleKill(player)
        updatePlayerStats(player, "double_kill") { playerId -> StatsDAO.incrementStat(playerId, "double_kills") }
    }

    fun onTripleKill(player: Player) {
        achievementListener.onTripleKill(player)
        updatePlayerStats(player, "triple_kill") { playerId -> StatsDAO.incrementStat(playerId, "triple_kills") }
    }

    fun onQuadKill(player: Player) {
        achievementListener.onQuadKill(player)
        updatePlayerStats(player, "quad_kill") { playerId -> StatsDAO.incrementStat(playerId, "quad_kills") }
    }

    fun onPerfectGame(player: Player) {
        achievementListener.onPerfectGame(player)
        updatePlayerStats(player, "perfect_game") { playerId -> StatsDAO.incrementStat(playerId, "perfect_games") }
    }

    fun onComebackWin(player: Player) {
        achievementListener.onComebackWin(player)
        updatePlayerStats(player, "comeback_win") { playerId -> StatsDAO.incrementStat(playerId, "comeback_wins") }
    }

    fun onFastestWin(player: Player, winTime: Long) {
        achievementListener.onFastestWin(player)
        updatePlayerStats(player, "fastest_win") { playerId -> StatsDAO.updateFastestWin(playerId, winTime) }
    }

    fun onLongestGame(player: Player, gameTime: Long) {
        achievementListener.onLongestGame(player)
        updatePlayerStats(player, "longest_game") { playerId -> StatsDAO.updateLongestGame(playerId, gameTime) }
    }

    fun onKillStreak(player: Player, amount: Int = 1) {
        achievementListener.onKillStreak(player, amount)
        updatePlayerStats(player, "kill_streak") { playerId -> StatsDAO.addKill(playerId) }
    }

    fun onWinStreak(player: Player, amount: Int = 1) {
        achievementListener.onWinStreak(player, amount)
        // StatsDAO.addWin already called by onGameWon — do not double-count the streak
    }

    fun onLossStreak(player: Player, amount: Int = 1) {
        achievementListener.onLossStreak(player, amount)
        // StatsDAO.addLoss already called by onGameLost — do not double-count the streak
    }

    // =================== GAME DURATION TRACKING ===================

    fun markGameStart(instance: GameInstance) { gameStartTimes[instance.id] = System.currentTimeMillis() }

    fun getGameDuration(instance: GameInstance): Long {
        val startTime = gameStartTimes[instance.id]
        return if (startTime != null) (System.currentTimeMillis() - startTime) / 1000 else 0
    }

    fun removeGameStart(instance: GameInstance) { gameStartTimes.remove(instance.id) }

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
                    broadcastToGame(instance, MSG.lang("game.countdown.cancelled"))
                    cancel()
                    return
                }
                when (timeLeft) {
                    in arrayOf(10, 5, 4, 3, 2, 1) -> {
                        broadcastToGame(instance, MSG.lang("game.countdown.starting_in", "seconds" to timeLeft.toString()))
                        playersInGame(instance).forEach { player ->
                            player.sendTitle(
                                MSG.lang("game.countdown.title_starting"),
                                MSG.lang("game.countdown.title_countdown", "seconds" to timeLeft.toString()),
                                0, 20, 0)
                        }
                    }
                    0 -> { startGame(instance); cancel(); return }
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
        instance.cagesOpen = false // reset in case of back-to-back games on same instance

        // Force-update scoreboards immediately for all players so the Game layout
        // (with if-condition team lines) shows up without waiting for the 20-tick auto-update.
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            instance.players.mapNotNull { plugin.server.getPlayer(it) }.forEach { p ->
                if (p.isOnline) plugin.scoreboardManager.updatePlayerScoreboard(p)
            }
        }, 2L)
        // Voting window closes the moment the start-timer fires. Force-close any open
        // voting GUIs so players can't vote from a stale menu during the cage countdown.
        if (plugin.isVotingReady) {
            plugin.votingGui.closeAllForInstance(instance)
            plugin.votingManager.deactivateEffect(instance) // clean up any leftover from prior game
        }
        broadcastToGame(instance, MSG.lang("game.start.started"))

        plugin.spectatorInventoryManager.updateVisibilityForGameInstance(instance)

        val gameListener = plugin.gameListener
        gameListener.clearAllPlayerPlacedBlocks(instance.id)

        if (instance.arenaInstance.config.isLobbyEnabled()) {
            clearSpawnAreasForGame(instance)
            val leafCmd = configManager.leafUICommand.takeIf { configManager.leafUIEnabled && it.isNotBlank() }
            if (leafCmd != null) {
                // Loading screen voor elke speler → verbergt de teleport naar de cage
                playersInGame(instance).forEach { player ->
                    Bukkit.dispatchCommand(
                        Bukkit.getConsoleSender(),
                        leafCmd.replace("{player}", player.name)
                    )
                }
                // Na 10 ticks (0.5s) is het loading screen zichtbaar → teleport + cage-timer starten
                object : BukkitRunnable() {
                    override fun run() {
                        if (instance.state != GameState.ACTIVE) return
                        assignPlayersToTeams(instance)
                        startCageTimer(instance)
                    }
                }.runTaskLater(plugin, 10L)
            } else {
                assignPlayersToTeams(instance)
                startCageTimer(instance)
            }
        } else {
            startCageTimer(instance)
        }

        val gameTimer = instance.arenaInstance.config.getGameTimer()
        timerEvent.startGameTimer(instance, gameTimer)
        updateGameStartStats(instance)
        markGameStart(instance)

        val rewardsSection = YamlConfiguration.loadConfiguration(instance.arenaInstance.config.file)
            .getConfigurationSection("Rewards")
        RewardProcessor.processStartRewards(plugin, playersInGame(instance), rewardsSection)

        debugLog("Game ${instance.id} started successfully")
    }

    private fun updateGameStartStats(instance: GameInstance) {
        try {
            val arenaName = instance.arenaInstance.config.name
            val arenaUpdated = ArenaStatsDAO.createOrUpdate(arenaName, instance.getDisplayName())
            if (arenaUpdated) debugLog("✓ Updated arena stats for: $arenaName")

            var playersUpdated = 0
            instance.players.forEach { playerUUID ->
                val player = Bukkit.getPlayer(playerUUID)
                if (player != null) {
                    val playerId = PlayerDAO.getPlayerId(player.name)
                    if (playerId != null) {
                        val gamesPlayedIncremented = StatsDAO.incrementStat(playerId, "games_played")
                        if (gamesPlayedIncremented) playersUpdated++
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
                        broadcastToGame(instance, MSG.lang("game.cage.opening_in", "seconds" to timeLeft.toString()))
                        playersInGame(instance).forEach { player ->
                            player.sendTitle(
                                MSG.lang("game.countdown.title_starting"),
                                MSG.lang("game.countdown.title_countdown", "seconds" to timeLeft.toString()),
                                0, 20, 0)
                        }
                    }
                    0 -> {
                        broadcastToGame(instance, MSG.lang("game.cage.opened"))
                        // Mark cages as open BEFORE calling setupPlayerHotbar so
                        // determineHotbarType returns "game" and players receive game items.
                        instance.cagesOpen = true

                        // Verwijder alle dropped items en XP-orbs binnen de arena-grenzen
                        val origin = instance.arenaInstance.origin
                        val radius = instance.arenaInstance.config.getSaveRadius().toDouble()
                        origin.world?.entities?.forEach { entity ->
                            if (entity is org.bukkit.entity.Item || entity is org.bukkit.entity.ExperienceOrb) {
                                val loc = entity.location
                                if (kotlin.math.abs(loc.x - origin.x) <= radius &&
                                    kotlin.math.abs(loc.z - origin.z) <= radius) {
                                    entity.remove()
                                }
                            }
                        }

                        playersInGame(instance).forEach { player ->
                            player.sendTitle(MSG.lang("game.cage.go_title"), "", 0, 20, 10)
                            player.removePotionEffect(PotionEffectType.SLOWNESS)
                            player.removePotionEffect(PotionEffectType.JUMP_BOOST)
                            // Guarantee combat-ready state: isInvulnerable must be false the
                            // moment cages open regardless of any prior hotbar-state residue.
                            player.isInvulnerable = false
                            plugin.gameListener.setCageProtection(player.uniqueId, false)
                            // Switch hotbar from game_lobby to game items now that cages are open.
                            HotbarUtils.setupPlayerHotbar(player, plugin)
                        }
                        removeCagesForInstance(instance)
                        val itemTimer = instance.arenaInstance.config.getItemTimer()
                        timerEvent.startItemTimer(instance, itemTimer)
                        if (plugin.isVotingReady) {
                            plugin.votingManager.activateWinner(instance)
                        }
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
        debugLog("Assigning players to teams (party-aware)")
        val teams         = instance.arenaInstance.config.teams
        val playersPerTeam = instance.arenaInstance.config.getPlayersPerTeam()

        // Group players by party so that party-mates are placed in the same team
        // (or split evenly across adjacent teams if the party is larger than the team capacity).
        val rawPlayers   = instance.players.toMutableList().shuffled()
        val partyGroups  = buildPartyGroups(rawPlayers)

        // Split any party that is larger than playersPerTeam into fair sub-groups,
        // then flatten into an ordered list of sub-groups (largest first).
        val subGroups = mutableListOf<List<UUID>>()
        partyGroups.forEach { group -> subGroups.addAll(splitFairly(group, playersPerTeam)) }
        subGroups.sortByDescending { it.size }

        // Track how many spawn slots have been used on each team.
        // LinkedHashMap preserves the config order so teams fill predictably.
        val slotsUsed = LinkedHashMap<Team, Int>()
        teams.forEach { slotsUsed[it] = 0 }

        for (subGroup in subGroups) {
            val team = findTeamForGroup(slotsUsed, subGroup.size, playersPerTeam) ?: continue
            val baseSlot = slotsUsed[team]!!

            subGroup.forEachIndexed { i, playerId ->
                val spawnIndex = baseSlot + i
                if (spawnIndex >= team.spawns.size) return@forEachIndexed
                val player = Bukkit.getPlayer(playerId) ?: return@forEachIndexed
                if (!player.isOnline) return@forEachIndexed

                val spawnLocation = team.spawns[spawnIndex].toAbsolute(instance.arenaInstance.origin)
                player.teleport(spawnLocation)
                instance.trackTeamAssignment(playerId, team)
                placeCageAtSpawnLocation(player.uniqueId, spawnLocation)
                setupPlayerForTeam(player, team)
                applyCageEffects(player)
                plugin.gameListener.setCageProtection(player.uniqueId, true)
                player.sendMessage(MSG.lang("game.team.assigned", "team" to team.name))
                debugLog("Player ${player.name} → team ${team.name} (spawn $spawnIndex)")
            }
            slotsUsed[team] = baseSlot + subGroup.size
        }

        debugLog("Party-aware team assignment completed:")
        teams.forEach { team -> debugLog("  ${team.name}: ${instance.getPlayersInTeam(team).size} players") }
    }

    /**
     * Groups [players] by party.  Players not in a party each form their own
     * single-player group.  Groups are sorted largest-first.
     */
    private fun buildPartyGroups(players: List<UUID>): List<List<UUID>> {
        val byParty = mutableMapOf<UUID, MutableList<UUID>>()   // partyId → members in this game
        val solos   = mutableListOf<UUID>()

        for (uuid in players) {
            val player = Bukkit.getPlayer(uuid)
            val party  = if (player != null) plugin.partyManager.getParty(player) else null
            if (party != null) {
                byParty.getOrPut(party.id) { mutableListOf() }.add(uuid)
            } else {
                solos.add(uuid)
            }
        }

        val result = mutableListOf<List<UUID>>()
        byParty.values.forEach { result.add(it) }
        solos.forEach { result.add(listOf(it)) }
        result.sortByDescending { it.size }
        return result
    }

    /**
     * Splits [group] into sub-lists of at most [teamCapacity] players,
     * distributing the remainder evenly so the sizes are as equal as possible.
     *
     * Example: 5 players, cap 4  →  [3, 2]  (not [4, 1])
     * Example: 7 players, cap 4  →  [4, 3]
     * Example: 9 players, cap 4  →  [3, 3, 3]
     */
    private fun splitFairly(group: List<UUID>, teamCapacity: Int): List<List<UUID>> {
        if (group.size <= teamCapacity) return listOf(group)
        val numGroups = Math.ceil(group.size.toDouble() / teamCapacity).toInt()
        val base      = group.size / numGroups
        val remainder = group.size % numGroups
        var offset    = 0
        return (0 until numGroups).map { i ->
            val size = if (i < remainder) base + 1 else base
            group.subList(offset, offset + size).also { offset += size }
        }
    }

    /**
     * Finds the team with the most used slots that still has at least
     * [groupSize] open spawn slots.  Prefers partially-filled teams so
     * that teams fill up before new ones are started.
     */
    private fun findTeamForGroup(
        slotsUsed: Map<Team, Int>,
        groupSize: Int,
        teamCapacity: Int
    ): Team? = slotsUsed.entries
        .filter { (team, used) ->
            val available = minOf(teamCapacity - used, team.spawns.size - used)
            available >= groupSize
        }
        .maxByOrNull { (_, used) -> used }   // prefer more-filled team
        ?.key

    fun assignPlayerDirectlyToCage(player: Player, instance: GameInstance): Boolean {
        val teams = instance.arenaInstance.config.teams
        val playersPerTeam = instance.arenaInstance.config.getPlayersPerTeam()

        for (team in teams) {
            val assignedCount = instance.getPlayersInTeam(team).size
            if (assignedCount < playersPerTeam && assignedCount < team.spawns.size) {
                val spawnLocation = team.spawns[assignedCount].toAbsolute(instance.arenaInstance.origin)
                player.teleport(spawnLocation)
                instance.trackTeamAssignment(player.uniqueId, team)
                placeCageAtSpawnLocation(player.uniqueId, spawnLocation)
                setupPlayerForTeam(player, team)
                applyCageEffects(player)
                plugin.gameListener.setCageProtection(player.uniqueId, true)
                player.sendMessage(MSG.lang("game.team.assigned", "team" to team.name))
                debugLog("Player ${player.name} directly assigned to team ${team.name} (no-lobby mode)")
                return true
            }
        }
        debugLog("No team slots available for ${player.name} in instance ${instance.id}")
        return false
    }

    private fun placeCageAtSpawnLocation(playerUUID: UUID, spawnLocation: org.bukkit.Location) {
        val cageFile: File? = if (plugin.configManager.cosmeticsEnabled && plugin.isCosmeticManagerReady) {
            val player = Bukkit.getPlayer(playerUUID)
            if (player != null) plugin.cosmeticManager.getCageSchemForPlayer(player)
            else File(plugin.dataFolder, "modules/cosmetics/cages/default/Cage.schem").takeIf { it.exists() }
        } else {
            File(plugin.dataFolder, "modules/cosmetics/cages/default/Cage.schem").takeIf { it.exists() }
        }

        if (cageFile == null) {
            debugLog("No cage file found for UUID: $playerUUID — skipping cage placement")
            return
        }
        // Remove any previously placed cage for this player before placing the new one.
        // Covers the case where undo info still exists (e.g. cosmetic changed between rounds).
        if (SchematicCageHelper.hasCageSessions(playerUUID)) {
            debugLog("Existing cage sessions found for UUID: $playerUUID — undoing before re-placing")
            val undid = SchematicCageHelper.undoCageForPlayer(plugin, configManager, playerUUID)
            if (!undid) SchematicCageHelper.forceRemoveCageForPlayer(plugin, configManager, playerUUID)
        }
        val success = SchematicCageHelper.pasteCageForPlayer(plugin, configManager, playerUUID, cageFile, spawnLocation)
        if (success) debugLog("✅ Cage placed at spawn for UUID: $playerUUID (${cageFile.name})")
        else plugin.logger.severe("[GameplayManager] ❌ Failed to place cage for UUID: $playerUUID")
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
                    if (forceSuccess) debugLog("✅ Cage force removed for: $playerName")
                    else plugin.logger.severe("[GameplayManager] ❌ Force cage removal also failed for: $playerName")
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
        leatherMeta.setColor(team.color.toBukkitColor())
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
        val isGhostMode = instance.arenaInstance.config.isGhostMode()
        val itemRandomizer = ItemRandomizer(instance.arenaInstance.config.getAsFileConfiguration())
        val playersWhoReceivedItems = instanceItemDistribution.computeIfAbsent(instance.id) { mutableSetOf() }

        if (playersWhoReceivedItems.size >= playersInGame.size) {
            playersWhoReceivedItems.clear()
            debugLog("All players received items, starting new distribution cycle for instance ${instance.id}")
        }

        playersInGame.forEach { player ->
            if (!playersWhoReceivedItems.contains(player.uniqueId)) {
                // GhoasModus: 30% kans op 3x TNT in plaats van normaal item
                if (isGhostMode && Math.random() < 0.30) {
                    player.inventory.addItem(ItemStack(Material.TNT, 3))
                    debugLog("GhostMode: gave 3x TNT to ${player.name}")
                } else {
                    val randomItem = itemRandomizer.getRandomItem()
                    val isCommandItem = randomItem.type == Material.COMMAND_BLOCK
                    if (isCommandItem) itemRandomizer.executeRandomAction(player.name)
                    else {
                        val itemCopy = randomItem.clone()
                        itemCopy.amount = 1
                        player.inventory.addItem(itemCopy)
                    }
                }
                playersWhoReceivedItems.add(player.uniqueId)
            }
        }
        debugLog("Item distribution: ${playersWhoReceivedItems.size}/${playersInGame.size} players received items this cycle")
    }

    // =================== PLAYER DEATH & ELIMINATION ===================

    fun handlePlayerDeath(player: Player, playerToInstance: MutableMap<UUID, GameInstance>): Boolean {
        val instance = playerToInstance[player.uniqueId] ?: return false
        if (instance.state != GameState.ACTIVE) return false

        val gameListener = plugin.gameListener
        gameListener.setCageProtection(player.uniqueId, false)
        if (SchematicCageHelper.hasCageSessions(player.uniqueId)) {
            SchematicCageHelper.clearCageInfoForPlayer(plugin, configManager, player.uniqueId)
        }

        // Capture lobby spawn BEFORE removing from playerToInstance (needs the instance reference)
        val gameLobbySpawn = instance.arenaInstance.config.getSpawnTeleportLocation(instance.arenaInstance.origin)

        instance.players.remove(player.uniqueId)
        playerToInstance.remove(player.uniqueId)
        // Mark in spectator map immediately so GlobalVisibilityTicker hides this player
        // from alive players before they have respawned and been formally set as spectator.
        SpectatorUtils.markAsEliminatedSpectator(player)

        broadcastToGame(instance, MSG.lang("game.elimination.broadcast", "player" to player.name))
        player.sendMessage(MSG.lang("game.elimination.self"))

        updatePlayerEliminationStats(player, instance)

        // Track as dead spectator and register central eliminated session.
        instance.spectators.add(player.uniqueId)
        eliminatedPlayerSessions[player.uniqueId] = EliminatedPlayerSession(
            instance = instance,
            respawnLocation = gameLobbySpawn.clone()
        )
        instance.players.mapNotNull { Bukkit.getPlayer(it) }.forEach { alivePlayer ->
            alivePlayer.hidePlayer(plugin, player)
        }

        // Ensure death-screen resolves so post-respawn state handling can run reliably.
        scheduleForcedRespawn(player.uniqueId)

        val spectateListener = plugin.spectateListener
        instance.players.forEach { uuid ->
            val p = Bukkit.getPlayer(uuid)
            if (p != null) spectateListener?.refreshPlayer(p)
        }
        spectateListener?.refreshPlayer(player)

        when {
            instance.players.isEmpty() ->
                endGame(instance, MSG.lang("game.end.no_players"), playerToInstance)
            instance.players.size >= 1 && checkOneTeamLeft(instance) -> {
                val winners = instance.players.mapNotNull { Bukkit.getPlayer(it) }
                val teamName = getTeamName(instance, winners)
                endGame(instance, MSG.lang("game.end.winner_team", "team" to (teamName ?: winners.firstOrNull()?.name ?: "Team")), playerToInstance)
            }
            instance.players.size == 1 -> {
                val winner = Bukkit.getPlayer(instance.players.first())
                if (winner != null) endGame(instance, MSG.lang("game.end.winner_elimination", "winner" to winner.name), playerToInstance)
                else endGame(instance, MSG.lang("game.end.game_ended"), playerToInstance)
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
            val deathAdded = StatsDAO.addDeath(playerId)
            if (deathAdded) debugLog("✓ Recorded elimination death for ${player.name}")
            achievementListener.onDeath(player)
            val arenaName = instance.arenaInstance.config.name
            val arenaStatsUpdated = ArenaStatsDAO.createOrUpdatePlayerArenaStats(playerId, arenaName)
            if (arenaStatsUpdated) debugLog("✓ Updated arena stats for ${player.name}")
        } catch (ex: Exception) {
            plugin.logger.severe("[GameplayManager] ❌ Error updating elimination stats for ${player.name}: ${ex.message}")
            ex.printStackTrace()
        }
    }

    fun handlePlayerKill(killer: Player, victim: Player) {
        // Only record the kill here. The victim's death is recorded by updatePlayerEliminationStats()
        // when the actual PlayerDeathEvent fires — recording it here too would double-count deaths.
        try {
            val killerId = PlayerDAO.getPlayerId(killer.name)
            if (killerId != null) {
                val killAdded = StatsDAO.addKill(killerId)
                if (killAdded) { debugLog("✓ Recorded kill for ${killer.name}"); achievementListener.onKillPlayer(killer) }
            } else plugin.logger.warning("[GameplayManager] ✗ Could not find killerId for ${killer.name}")
        } catch (ex: Exception) {
            plugin.logger.severe("[GameplayManager] ❌ Error updating kill stats: ${ex.message}")
            ex.printStackTrace()
        }
    }

    fun handlePlayerAssist(assistant: Player) {
        updatePlayerStats(assistant, "assist") { playerId -> StatsDAO.addAssist(playerId); achievementListener.onAssist(assistant) }
    }

    fun handleDamageDealt(player: Player, amount: Double) {
        updatePlayerStats(player, "damage_dealt") { playerId -> StatsDAO.addDamageDealt(playerId, amount); achievementListener.onDamageDealt(player, amount.toInt()) }
    }

    fun handleDamageTaken(player: Player, amount: Double) {
        updatePlayerStats(player, "damage_taken") { playerId -> StatsDAO.addDamageTaken(playerId, amount); achievementListener.onDamageTaken(player, amount.toInt()) }
    }

    fun handleFallDamageTaken(player: Player, amount: Double) {
        updatePlayerStats(player, "fall_damage") { playerId -> StatsDAO.addFallDamage(playerId, amount); achievementListener.onFallDamageTaken(player, amount.toInt()) }
    }

    // =================== GAME END ===================

    fun endGameByTimer(instance: GameInstance, playerToInstance: MutableMap<UUID, GameInstance>) {
        debugLog("Game ${instance.id} ended by timer")
        instance.players.forEach { playerId -> val player = Bukkit.getPlayer(playerId); if (player != null) debugLog("Player ${player.name} survived to timeout") }
        endGame(instance, MSG.lang("game.end.time_up"), playerToInstance)
    }

    fun endGame(instance: GameInstance, reason: String, playerToInstance: MutableMap<UUID, GameInstance>) {
        debugLog("Ending game ${instance.id}: $reason")

        val durationSeconds = getGameDuration(instance)
        instance.state = GameState.ENDED
        if (plugin.isVotingReady) {
            plugin.votingGui.closeAllForInstance(instance)
            plugin.votingManager.deactivateEffect(instance)
        }
        timerEvent.stopAllTimers(instance.id)
        gameTimers[instance.id]?.cancel()
        gameTimers.remove(instance.id)
        removeGameStart(instance)
        instanceItemDistribution.remove(instance.id)

        val gameListener = plugin.gameListener
        instance.players.forEach { playerUUID -> gameListener.setCageProtection(playerUUID, false) }
        instance.players.forEach { playerUUID ->
            if (SchematicCageHelper.hasCageSessions(playerUUID)) {
                SchematicCageHelper.clearCageInfoForPlayer(plugin, configManager, playerUUID)
            }
        }
        gameListener.clearAllPlayerPlacedBlocks(instance.id)

        // BedModus cleanup
        (instance.players + instance.spectators).forEach { plugin.bedModeManager.clearPlayer(it) }

        broadcastToGame(instance, MSG.lang("game.end.game_over"))
        broadcastToGame(instance, reason)

        val winners = getWinningPlayers(instance)
        val survivingLosers = instance.players.mapNotNull { Bukkit.getPlayer(it) }.filter { it !in winners }
        val deadSpectators = instance.spectators.mapNotNull { Bukkit.getPlayer(it) }.filter { it.isOnline }

        // Stats, achievements, cosmetics for winners
        winners.forEach { winner ->
            onGameWon(winner, durationSeconds)
            winner.fallDistance = 0f
            if (plugin.configManager.cosmeticsEnabled && plugin.isCosmeticManagerReady) {
                plugin.cosmeticManager.fireWin(winner, instance)
            }
            onFastestWin(winner, durationSeconds)
            onLongestGame(winner, durationSeconds)
            onWinStreak(winner)
        }

        // Stats for losers still alive
        survivingLosers.forEach { loser ->
            onGameLost(loser, durationSeconds)
            onLossStreak(loser)
        }

        // Eliminated players also lost the game — record games_lost and update their loss streak
        deadSpectators.forEach { spectator ->
            onGameLost(spectator, null)
            onLossStreak(spectator)
        }

        updateGameEndStats(instance)

        // Update leaderboard cache asynchronously so all ranks reflect this game's results
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                LeaderboardCacheDAO.updateCache()
            } catch (e: Exception) {
                plugin.logger.warning("[GameplayManager] Failed to update leaderboard cache: ${e.message}")
            }
        })

        val rewardsSection = YamlConfiguration.loadConfiguration(instance.arenaInstance.config.file)
            .getConfigurationSection("Rewards")
        RewardProcessor.processEndRewards(plugin, winners, survivingLosers, rewardsSection)

        // Drop alive players from the active-instance index so /pillars status etc. report
        // them as out-of-game during the post-match countdown.
        val spectateListener = plugin.spectateListener
        instance.players.toList().forEach { uuid ->
            playerToInstance.remove(uuid)
            val p = Bukkit.getPlayer(uuid)
            if (p != null) {
                spectateListener?.refreshPlayer(p)
                plugin.teamsListener.onPlayerLeaveGame(p)
                spectateListener?.onPlayerGameStatusChange(p)
            }
        }
        instance.players.clear()

        // Move alive players (winners + still-alive losers) into spectator mode so they
        // float above the arena during the post-match window instead of being teleported
        // straight to lobby or — worse — getting "ghosted" into the next game without ever
        // seeing the result of this one.
        (winners + survivingLosers).forEach { player ->
            if (player.isOnline) {
                playerManager.restorePlayerState(player)
                SpectatorUtils.setPlayerAsSpectator(player, plugin)
                player.fallDistance = 0f
            }
        }

        // Dead spectators stay in their existing spectator state. We intentionally do NOT
        // clear [eliminatedPlayerSessions] here — the player who triggered endGame by
        // dying is still on the death screen, and [handleEliminatedPlayerRespawn] needs
        // that session entry to put them into spectator mode on respawn. The session
        // entry is cleaned up later by [scheduleAutoQueue] / [scheduleLobbyReturn] when
        // the post-match countdown completes.
        deadSpectators.forEach { spectator ->
            spectator.fallDistance = 0f
        }
        instance.spectators.clear()

        // Unified post-match flow for ALL participants (winners + alive losers + dead
        // spectators). Each player is auto-queued OR returned to lobby based on the
        // server master switch + their personal /pillars autoqueue preference.
        //
        // Pacing:
        //   • Auto-queue ON  → spectate the arena for 5s, then join a fresh instance.
        //   • Auto-queue OFF → spectate the arena for 10s, then teleport to lobby.
        val autoQueueGameType = instance.arenaInstance.template.name
        val lobbyRespawn = plugin.configManager.getLobbySpawnLocation() ?: plugin.server.worlds.first().spawnLocation
        val allParticipants = winners + survivingLosers + deadSpectators

        allParticipants.forEach { player ->
            if (!player.isOnline) return@forEach
            player.sendTitle("§c§lGAME OVER", "§7Match complete", 5, 30, 10)
            if (isAutoQueueEnabled(player)) {
                pendingAutoQueue[player.uniqueId] = autoQueueGameType
                scheduleAutoQueue(player, autoQueueGameType, lobbyRespawn)
            } else {
                scheduleLobbyReturn(player, lobbyRespawn)
            }
        }

        // Reset arena after the longest possible countdown (10s lobby return) + 1s buffer.
        // This way the arena stays viewable during the entire spectator phase.
        object : BukkitRunnable() {
            override fun run() { resetInstance(instance) }
        }.runTaskLater(plugin, 220L)
    }

    /**
     * Tears down a half-started game without declaring a winner. Used when the cage
     * countdown is running but the player count drops below the minimum — there is
     * nothing to "win", so we just unwind the start side-effects and return the
     * instance to WAITING so the remaining player(s) can wait for more joiners.
     *
     * This is the no-lobby-mode analogue to [GameLeaveManager]'s STARTING-state cancel.
     */
    fun abortPreCageStart(instance: GameInstance, playerToInstance: MutableMap<UUID, GameInstance>) {
        timerEvent.stopAllTimers(instance.id)
        gameTimers[instance.id]?.cancel()
        gameTimers.remove(instance.id)
        removeGameStart(instance)
        instanceItemDistribution.remove(instance.id)
        if (plugin.isVotingReady) {
            plugin.votingManager.deactivateEffect(instance)
        }

        val gameListener = plugin.gameListener
        instance.players.toList().forEach { playerUUID ->
            gameListener.setCageProtection(playerUUID, false)
            if (SchematicCageHelper.hasCageSessions(playerUUID)) {
                SchematicCageHelper.forceRemoveCageForPlayer(plugin, configManager, playerUUID)
            }
            val player = Bukkit.getPlayer(playerUUID)
            if (player != null && player.isOnline) {
                player.removePotionEffect(PotionEffectType.SLOWNESS)
                player.removePotionEffect(PotionEffectType.JUMP_BOOST)
                player.isInvulnerable = false
            }
        }
        instance.cagesOpen = false
        instance.state = GameState.WAITING
        debugLog("Pre-cage start aborted for instance ${instance.id} — back to WAITING")
    }

    private fun getWinningPlayers(instance: GameInstance): List<Player> {
        if (instance.players.isEmpty()) return emptyList()
        val remainingTeams = instance.getRemainingTeams()
        if (remainingTeams.size != 1) return instance.players.mapNotNull { Bukkit.getPlayer(it) }
        val winningTeam = remainingTeams.first()
        return instance.getAlivePlayersInTeam(winningTeam).mapNotNull { Bukkit.getPlayer(it) }
    }

    private fun checkOneTeamLeft(instance: GameInstance): Boolean = instance.getRemainingTeams().size == 1

    private fun getTeamName(instance: GameInstance, players: List<Player>): String? {
        if (players.isEmpty()) return null
        return instance.getPlayerTeam(players.first().uniqueId)?.name
    }

    private fun updateGameEndStats(instance: GameInstance) {
        try {
            val arenaName = instance.arenaInstance.config.name
            val updated = ArenaStatsDAO.createOrUpdate(arenaName, instance.getDisplayName())
            if (updated) debugLog("✓ Updated game end stats for arena: $arenaName")
            else plugin.logger.warning("[GameplayManager] ✗ Failed to update game end stats for arena: $arenaName")
        } catch (ex: Exception) {
            plugin.logger.severe("[GameplayManager] ❌ Error updating game end stats: ${ex.message}")
            ex.printStackTrace()
        }
    }

    private fun resetInstance(instance: GameInstance) {
        instance.state = GameState.RESETTING
        // Recycle point: an instance keeps the same id when reused, and votes are keyed
        // by instance id. Wipe ALL voting state here so the finished game's votes/effect
        // can never bleed into the next game on this instance — otherwise, if nobody
        // votes next round, getWinner() would keep replaying the previous game's choice.
        // (deactivateEffect only resets votes when an effect was active, so we also call
        // resetVotes unconditionally to cover games where no effect ever activated.)
        if (plugin.isVotingReady) {
            plugin.votingManager.deactivateEffect(instance)
            plugin.votingManager.resetVotes(instance)
        }
        plugin.arenaManager.releaseInstance(instance.arenaInstance)
        instance.reset()
    }

    // =================== UTILITY METHODS ===================

    private fun scheduleForcedRespawn(playerId: UUID, delayTicks: Long = 1L) {
        object : BukkitRunnable() {
            override fun run() {
                val session = eliminatedPlayerSessions[playerId] ?: return
                val player = Bukkit.getPlayer(playerId) ?: return
                if (!player.isOnline) return
                val recovered = SpectatorUtils.ensurePlayerAlive(player, plugin, "GameplayManager")
                if (!recovered) {
                    session.respawnAttempts += 1
                    if (session.respawnAttempts < MAX_RESPAWN_ATTEMPTS) {
                        val nextDelay = TICKS_PER_SECOND * (session.respawnAttempts + 1)
                        scheduleForcedRespawn(playerId, nextDelay)
                    } else {
                        plugin.logger.warning("[GameplayManager] Player ${player.name} could not be force-respawned after elimination; clearing eliminated state")
                        clearEliminatedState(playerId)
                    }
                }
            }
        }.runTaskLater(plugin, delayTicks)
    }

    fun getEliminatedRespawnLocation(uuid: UUID): Location? {
        return eliminatedPlayerSessions[uuid]?.respawnLocation?.clone()
    }

    fun clearEliminatedState(uuid: UUID) {
        val session = eliminatedPlayerSessions.remove(uuid)
        session?.instance?.spectators?.remove(uuid)
    }

    fun handleEliminatedPlayerRespawn(player: Player): Boolean {
        if (!player.isOnline) return false
        // If the player is still on the death screen, defer until the actual respawn fires.
        // Calling restorePlayerState (and thus player.spigot().respawn()) while inside a
        // PlayerDeathEvent handler chain corrupts the entity state → ghost player.
        // scheduleForcedRespawn (1-tick delay) will trigger the PlayerRespawnEvent safely,
        // which then re-invokes this function once the player is alive.
        if (player.isDead) return false
        val session = eliminatedPlayerSessions[player.uniqueId] ?: return false
        val instance = session.instance

        if (shouldMaintainAsInGameSpectator(session)) {
            playerManager.restorePlayerState(player)
            SpectatorUtils.setPlayerAsSpectator(player, plugin)
            enforceInGameSpectatorVisibility(instance, player)
            plugin.spectateListener?.refreshPlayer(player)
            return true
        }

        // Game ended: set up as flying spectator so the player can watch the end sequence.
        // The 5-second endGame countdown will perform the actual lobby teleport.
        if (instance.state == GameState.ENDED || instance.state == GameState.RESETTING) {
            playerManager.restorePlayerState(player)
            SpectatorUtils.setPlayerAsSpectator(player, plugin)
            enforceInGameSpectatorVisibility(instance, player)
            plugin.spectateListener?.refreshPlayer(player)
            return true
        }

        // Normal mid-game elimination: teleport to lobby immediately.
        playerManager.restorePlayerState(player)
        SpectatorUtils.removeSpectatorStatus(player, plugin)
        SpectatorUtils.clearCache(player)
        Bukkit.getOnlinePlayers().forEach { online ->
            online.showPlayer(plugin, player)
            player.showPlayer(plugin, online)
        }
        player.teleport(session.respawnLocation)
        instance.spectators.remove(player.uniqueId)
        eliminatedPlayerSessions.remove(player.uniqueId)
        plugin.spectateListener?.refreshPlayer(player)
        return true
    }

    private fun shouldMaintainAsInGameSpectator(session: EliminatedPlayerSession): Boolean {
        val instance = session.instance
        return session.keepAsSpectator &&
                instance.state == GameState.ACTIVE
    }

    fun enforceInGameSpectatorVisibility(instance: GameInstance, spectator: Player) {
        val aliveGamePlayers = instance.players.mapNotNull { Bukkit.getPlayer(it) }
        aliveGamePlayers.forEach { alivePlayer ->
            alivePlayer.hidePlayer(plugin, spectator)
            spectator.showPlayer(plugin, alivePlayer)
        }
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
            if (spawnAreaManager.validateSpawnArea(instance)) spawnAreaManager.clearSpawnArea(instance)
            else debugLog("Spawn area validation failed, skipping clear")
        }
    }

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
        eliminatedPlayerSessions.clear()
        SchematicCageHelper.cleanupAllCageSessions(plugin, configManager)
        gameStartTimes.clear()
    }
}
