package org.Kloppie74.locoPillars.game.manager

import org.bukkit.Bukkit
import org.bukkit.ChatColor
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
import java.io.File
import java.util.*

class GameplayManager(
    private val plugin: LocoPillars,
    private val timerEvent: TimerEvent
) {
    private val gameTimers = mutableMapOf<UUID, BukkitRunnable>()

    fun startLobbyCountdown(instance: GameInstance) {
        plugin.logger.info("[GameplayManager] Starting lobby countdown for instance ${instance.id}")

        if (instance.state != GameState.WAITING) {
            plugin.logger.warning("[GameplayManager] Cannot start countdown, instance state is: ${instance.state}")
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
        plugin.logger.info("[GameplayManager] Starting game for instance ${instance.id}")

        instance.state = GameState.ACTIVE
        broadcastToGame(instance, "${ChatColor.GREEN}Game started! Fight for victory!")

        // Clear spawn areas before assigning players
        clearSpawnAreasForGame(instance)

        // Assign players to teams, teleport to spawn points and place cages
        assignPlayersToTeams(instance)

        // Start cage timer
        startCageTimer(instance)

        // Start game timer using TimerEvent
        val gameTimer = instance.arenaInstance.config.getGameTimer()
        timerEvent.startGameTimer(instance, gameTimer)

        plugin.logger.info("[GameplayManager] Game ${instance.id} started successfully")
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
                            // Remove cage effects
                            player.removePotionEffect(PotionEffectType.SLOWNESS)
                            player.removePotionEffect(PotionEffectType.JUMP_BOOST)
                        }

                        // FIXED: Remove cages using proper UUID system with VASTE sessions
                        removeCagesForInstance(instance)

                        // Start item timer
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
        plugin.logger.info("[GameplayManager] Assigning players to teams")

        val teams = instance.arenaInstance.config.teams
        val players = instance.players.toList()
        val playersPerTeam = instance.arenaInstance.config.getPlayersPerTeam()

        var playerIndex = 0
        var teamIndex = 0

        while (playerIndex < players.size && teamIndex < teams.size) {
            val team = teams[teamIndex]
            val teamPlayers = mutableListOf<UUID>()

            // Assign players to this team
            repeat(minOf(playersPerTeam, team.spawns.size, players.size - playerIndex)) {
                teamPlayers.add(players[playerIndex])
                playerIndex++
            }

            // Teleport team players to spawn points and setup
            teamPlayers.forEachIndexed { spawnIndex, playerId ->
                val player = Bukkit.getPlayer(playerId)
                if (player != null && player.isOnline) {
                    val spawnLocation = team.spawns[spawnIndex].toAbsolute(instance.arenaInstance.origin)
                    player.teleport(spawnLocation)

                    // FIXED: Place cage at EXACT spawn location using NEW cage helper
                    placeCageAtSpawnLocation(player.uniqueId, spawnLocation)

                    setupPlayerForTeam(player, team)
                    applyCageEffects(player)
                    player.sendMessage("${ChatColor.GREEN}You are on team: ${team.name}")
                }
            }

            teamIndex++
        }
    }

    private fun placeCageAtSpawnLocation(playerUUID: UUID, spawnLocation: org.bukkit.Location) {
        // Look for cage files in Cages folder
        val cagesFolder = File(plugin.dataFolder, "Cages")
        if (!cagesFolder.exists()) {
            cagesFolder.mkdirs()
            plugin.logger.warning("[GameplayManager] Created Cages folder: ${cagesFolder.absolutePath}")
            plugin.logger.warning("[GameplayManager] Please add Standard-Cage.schem to this folder")
            return
        }

        val cageFile = File(cagesFolder, "Standard-Cage.schem")
        if (!cageFile.exists()) {
            plugin.logger.warning("[GameplayManager] Standard cage file not found: ${cageFile.absolutePath}")
            return
        }

        // FIXED: Paste cage at exact spawn location using NEW cage helper
        val success = SchematicCageHelper.pasteCageForPlayer(
            plugin,
            playerUUID,
            cageFile,
            spawnLocation
        )

        if (success) {
            plugin.logger.info("[GameplayManager] ✓ Cage placed at spawn for UUID: $playerUUID")
        } else {
            plugin.logger.severe("[GameplayManager] ✗ Failed to place cage for UUID: $playerUUID")
        }
    }

    private fun removeCagesForInstance(instance: GameInstance) {
        plugin.logger.info("[GameplayManager] Removing cages for instance ${instance.id}")

        var undoCount = 0
        instance.players.forEach { playerUUID ->
            val player = Bukkit.getPlayer(playerUUID)
            val playerName = player?.name ?: "Unknown"

            if (SchematicCageHelper.hasCageSessions(playerUUID)) {
                val success = SchematicCageHelper.undoCageForPlayer(plugin, playerUUID)
                if (success) {
                    undoCount++
                    plugin.logger.info("[GameplayManager] ✓ Cage removed for: $playerName")
                } else {
                    plugin.logger.severe("[GameplayManager] ✗ Failed to remove cage for: $playerName")
                    // Fallback: force remove cage!
                    val forceSuccess = SchematicCageHelper.forceRemoveCageForPlayer(plugin, playerUUID)
                    if (forceSuccess) {
                        plugin.logger.info("[GameplayManager] ✓ Cage force removed for: $playerName")
                    } else {
                        plugin.logger.severe("[GameplayManager] ✗ Force cage removal also failed for: $playerName")
                    }
                }
            }
        }
        plugin.logger.info("[GameplayManager] Cage removal completed: $undoCount/${instance.players.size} cages removed")
    }

    private fun setupPlayerForTeam(player: Player, team: Team) {
        // Give team-colored leather armor
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

        // Give starting items
        giveStartingItems(player)
    }

    private fun applyCageEffects(player: Player) {
        // Apply slowness and jump boost to simulate cage
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, Int.MAX_VALUE, 255, false, false))
        player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, Int.MAX_VALUE, 200, false, false))
    }

    private fun giveStartingItems(player: Player) {
        player.inventory.clear()

        // Basic starting items
        player.inventory.addItem(ItemStack(Material.STONE_SWORD))
        player.inventory.addItem(ItemStack(Material.BOW))
        player.inventory.addItem(ItemStack(Material.ARROW, 32))
        player.inventory.addItem(ItemStack(Material.COOKED_BEEF, 16))
        player.inventory.addItem(ItemStack(Material.GOLDEN_APPLE, 2))
    }

    private val instanceItemDistribution = mutableMapOf<UUID, MutableSet<UUID>>()

    fun giveItemsToPlayers(instance: GameInstance) {
        val playersInGame = playersInGame(instance)
        val itemRandomizer = ItemRandomizer(instance.arenaInstance.config.getAsFileConfiguration())

        // Get or create the set for this specific instance
        val playersWhoReceivedItems = instanceItemDistribution.computeIfAbsent(instance.id) { mutableSetOf() }

        // FIXED: Alleen clear als ALLE spelers een item hebben gehad
        if (playersWhoReceivedItems.size >= playersInGame.size) {
            playersWhoReceivedItems.clear()
            plugin.logger.info("[GameplayManager] All players received items, starting new distribution cycle for instance ${instance.id}")
        }

        // Choose ONE random item for ALL players this cycle
        val randomItem = itemRandomizer.getRandomItem()
        val isCommandItem = randomItem.type == Material.COMMAND_BLOCK

        playersInGame.forEach { player ->
            // Check if player already got an item this cycle
            if (!playersWhoReceivedItems.contains(player.uniqueId)) {
                if (isCommandItem) {
                    // Execute command for this player
                    itemRandomizer.executeRandomAction(player.name)
                    player.sendMessage("${ChatColor.GOLD}🎁 You received something special!")
                } else {
                    // Give the same item to all players
                    val itemCopy = randomItem.clone()
                    itemCopy.amount = 1
                    player.inventory.addItem(itemCopy)
                    val itemName = randomItem.type.name.lowercase().replace("_", " ")
                    player.sendMessage("${ChatColor.GOLD}🎁 You received: ${ChatColor.YELLOW}$itemName!")
                }

                // Mark player as having received an item
                playersWhoReceivedItems.add(player.uniqueId)
            }
        }

        plugin.logger.info("[GameplayManager] Item distribution: ${playersWhoReceivedItems.size}/${playersInGame.size} players received items this cycle")
    }

    fun handlePlayerDeath(player: Player, playerToInstance: MutableMap<UUID, GameInstance>, playerManager: PlayerManager): Boolean {
        val instance = playerToInstance[player.uniqueId] ?: return false

        if (instance.state != GameState.ACTIVE) return false

        // Clean up any remaining cage sessions for this player
        if (SchematicCageHelper.hasCageSessions(player.uniqueId)) {
            SchematicCageHelper.clearCageInfoForPlayer(plugin, player.uniqueId)
        }

        instance.players.remove(player.uniqueId)
        playerToInstance.remove(player.uniqueId)

        // Broadcast death message
        broadcastToGame(instance, "${ChatColor.RED}${player.name} has been eliminated!")
        player.sendMessage("${ChatColor.RED}You have been eliminated! Better luck next time!")

        // Teleport to main world
        val mainWorld = Bukkit.getWorlds().firstOrNull { !it.name.startsWith("pillars_") }
        if (mainWorld != null) {
            player.teleport(mainWorld.spawnLocation)
        }

        playerManager.restorePlayerState(player)

        // Check for game end conditions
        when {
            instance.players.isEmpty() -> {
                endGame(instance, "No players remaining!", playerToInstance)
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

    fun endGameByTimer(instance: GameInstance, playerToInstance: MutableMap<UUID, GameInstance>, playerManager: PlayerManager) {
        endGame(instance, "Time's up! Game ended in a draw!", playerToInstance)
    }

    fun endGame(instance: GameInstance, reason: String, playerToInstance: MutableMap<UUID, GameInstance>) {
        plugin.logger.info("[GameplayManager] Ending game ${instance.id}: $reason")

        instance.state = GameState.ENDED

        // Cancel all timers
        timerEvent.stopAllTimers(instance.id)
        gameTimers[instance.id]?.cancel()
        gameTimers.remove(instance.id)

        instanceItemDistribution.remove(instance.id)

        // Clean up any remaining cage sessions for all players
        instance.players.forEach { playerUUID ->
            if (SchematicCageHelper.hasCageSessions(playerUUID)) {
                SchematicCageHelper.clearCageInfoForPlayer(plugin, playerUUID)
            }
        }

        // Broadcast end message
        broadcastToGame(instance, "${ChatColor.GOLD}=== GAME OVER ===")
        broadcastToGame(instance, "${ChatColor.YELLOW}$reason")

        // Handle winners
        if (instance.players.isNotEmpty()) {
            instance.players.forEach { playerId ->
                val winner = Bukkit.getPlayer(playerId)
                if (winner != null) {
                    winner.sendMessage("${ChatColor.GREEN}Congratulations! You won!")
                    winner.sendTitle("${ChatColor.GOLD}VICTORY!", reason, 10, 60, 20)
                }
            }
        }

        // Clear all players
        val playersToRemove = instance.players.toList()
        val playerManager = PlayerManager(plugin)
        playersToRemove.forEach { playerId ->
            playerToInstance.remove(playerId)
            val player = Bukkit.getPlayer(playerId)
            if (player != null && player.isOnline) {
                playerManager.restorePlayerState(player)
                val mainWorld = Bukkit.getWorlds().firstOrNull { !it.name.startsWith("pillars_") }
                if (mainWorld != null) {
                    player.teleport(mainWorld.spawnLocation)
                }
            }
        }

        instance.players.clear()

        // Reset instance after delay
        object : BukkitRunnable() {
            override fun run() {
                resetInstance(instance)
            }
        }.runTaskLater(plugin, 100L)
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
        playersInGame(instance).forEach { player ->
            player.sendMessage(message)
        }
    }

    private fun playersInGame(instance: GameInstance): List<Player> {
        return instance.players.mapNotNull { playerId ->
            Bukkit.getPlayer(playerId)?.takeIf { it.isOnline }
        }
    }

    private fun clearSpawnAreasForGame(instance: GameInstance) {
        if (instance.arenaInstance.config.hasSpawnArea()) {
            plugin.logger.info("[GameplayManager] Clearing spawn area for game start")
            val spawnAreaManager = SpawnAreaManager(plugin)

            // Validate spawn area first
            if (spawnAreaManager.validateSpawnArea(instance)) {
                // Clear spawn area (use async version to prevent lag)
                spawnAreaManager.clearSpawnArea(instance)
            } else {
                plugin.logger.warning("[GameplayManager] Spawn area validation failed, skipping clear")
            }
        }
    }

    fun cleanup() {
        gameTimers.values.forEach { it.cancel() }
        gameTimers.clear()
        instanceItemDistribution.clear() // Clear item distribution tracking
        SchematicCageHelper.cleanupAllCageSessions(plugin)
    }
}