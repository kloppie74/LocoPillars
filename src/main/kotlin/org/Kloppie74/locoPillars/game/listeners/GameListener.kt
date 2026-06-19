package org.Kloppie74.locoPillars.game.listeners

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.block.data.type.Bed as BedData
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.block.Block
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
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
import org.Kloppie74.locoPillars.utils.luckyblocks.LuckyBlocksUtil
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

    /**
     * Players whose death was caused by another player in this tick.
     * Used to skip not_killed_by_player rewards for player kills (which are
     * already handled in handlePlayerKill via processKillRewards).
     */
    private val killedByPlayerSet: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    // =================== DEATH HANDLING ===================

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val instance = plugin.gameManager.getPlayerInstance(player)

        if (instance != null) {
            event.deathMessage = null
            event.droppedExp = 0

            // BedModus: speler heeft een bed → respawn daar, geen eliminatie
            if (instance.state == GameState.ACTIVE
                && instance.arenaInstance.config.isBedMode()
                && plugin.bedModeManager.hasActiveBed(player.uniqueId))
            {
                event.keepInventory = true
                event.keepLevel = true
                killedByPlayerSet.remove(player.uniqueId) // geen "not killed by player" reward
                playerKillStreaks[player.uniqueId] = 0
                val bedLoc = plugin.bedModeManager.getBedHeadLocation(player.uniqueId)!!
                plugin.bedModeManager.markPendingBedRespawn(player.uniqueId, bedLoc)
                if (plugin.configManager.cosmeticsEnabled && plugin.isCosmeticManagerReady) {
                    plugin.cosmeticManager.fireDeath(player, instance)
                }
                player.sendMessage(MSG.chatColors("&6&lBED RESPAWN &7— you will respawn at your bed!"))
                return
            }

            // Normale dood
            event.keepInventory = false
            event.keepLevel = false

            // Clear placed blocks
            clearPlayerPlacedBlocks(instance.id, player.uniqueId)

            // Fire DEATH cosmetic before processing the death
            if (plugin.configManager.cosmeticsEnabled && plugin.isCosmeticManagerReady) {
                plugin.cosmeticManager.fireDeath(player, instance)
            }

            // If this death was NOT caused by a player, fire not_killed_by_player rewards.
            if (instance.state == GameState.ACTIVE && !killedByPlayerSet.remove(player.uniqueId)) {
                val rewardsSection = YamlConfiguration.loadConfiguration(instance.arenaInstance.config.file)
                    .getConfigurationSection("Rewards")
                val remainingPlayers = instance.players.mapNotNull { plugin.server.getPlayer(it) }
                RewardProcessor.processNotKilledByPlayerRewards(plugin, player, remainingPlayers, rewardsSection)
            }

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

        // BedModus respawn
        val bedLoc = plugin.bedModeManager.consumePendingBedRespawn(player.uniqueId)
        if (bedLoc != null) {
            event.respawnLocation = bedLoc
            object : BukkitRunnable() {
                override fun run() {
                    if (!player.isOnline) return
                    player.isInvulnerable = false
                    player.allowFlight = false
                    player.isFlying = false
                    // Korte respawn-bescherming (3 seconden resistance IV)
                    player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 60, 4, false, false))
                    player.sendTitle("§a§lRESPAWNED!", "§7Your bed is still safe.", 0, 30, 10)
                }
            }.runTaskLater(plugin, POST_RESPAWN_DELAY_TICKS)
            return
        }

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
        // Fire KILL cosmetic for the attacker
        if (plugin.configManager.cosmeticsEnabled && plugin.isCosmeticManagerReady) {
            plugin.cosmeticManager.fireKill(attacker, victim, instance)
        }

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

        // Mark victim as killed-by-player BEFORE the death event fires so
        // onPlayerDeath knows NOT to trigger not_killed_by_player rewards.
        killedByPlayerSet.add(victim.uniqueId)

        val rewardsSection = YamlConfiguration.loadConfiguration(instance.arenaInstance.config.file)
            .getConfigurationSection("Rewards")
        val remainingPlayers = instance.players.mapNotNull { plugin.server.getPlayer(it) }
        RewardProcessor.processKillRewards(plugin, attacker, victim, remainingPlayers, rewardsSection)
    }

    // =================== PLAYER JOIN/QUIT ===================

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        plugin.bedModeManager.clearPlayer(player.uniqueId)
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
        // Editor players can freely break any block in their edit world
        if (plugin.adminEditorManager.isActive(player)) return
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

            // Check if player can break this block.
            // Player-placed blocks are always breakable. Original map blocks are breakable
            // only when Can-Break-Map-Blocks is on, OR when this exact block was whitelisted
            // with the editor's Breakable-Block selector (stored as an origin-relative offset).
            // Lucky block glass (placed via /luckyblocks or baked into the schematic) is
            // handled separately so it always bypasses the map-protection restriction.
            val placed = playerPlacedBlocks[instance.id]
            val isPlayerPlaced = placed?.contains(blockLoc) == true

            when {
                // Placed lucky block (from /luckyblocks getluckyblock).
                // May also be in playerPlacedBlocks — keep both caches consistent.
                // Do NOT cancel: LuckyBlocksListener (NORMAL priority) runs next and
                // triggers the lucky block effect + removes the block itself.
                LuckyBlocksUtil.isPlacedLuckyBlock(blockLoc) -> {
                    placed?.remove(blockLoc)
                }

                isPlayerPlaced -> {
                    placed?.remove(blockLoc)
                }

                else -> {
                    val config = instance.arenaInstance.config

                    // Schematic lucky block: a glass block baked into the pasted arena
                    // map that was NOT placed by a player.  When LuckyblockEnabled is
                    // true, breaking it triggers the lucky block engine instead of a
                    // normal block drop and bypasses the Can-Break-Map-Blocks restriction.
                    if (config.isLuckyBlocksEnabled()
                        && LuckyBlocksUtil.isLuckyBlockMaterial(event.block.type, plugin.configManager)) {
                        event.isDropItems = false
                        event.block.type = org.bukkit.Material.AIR
                        LuckyBlocksUtil.handleLuckyBlockBreak(player, blockLoc, instance)
                        try {
                            val playerId = PlayerDAO.getPlayerId(player.name)
                            if (playerId != null) StatsDAO.incrementStat(playerId, "blocks_broken")
                        } catch (ex: Exception) {
                            plugin.logger.warning("[GameListener] Error tracking lucky block break: ${ex.message}")
                        }
                        return
                    }

                    val whitelisted = config.hasBreakableWhitelist() && config.isBreakableWhitelisted(
                        blockLoc.blockX - origin.blockX,
                        blockLoc.blockY - origin.blockY,
                        blockLoc.blockZ - origin.blockZ
                    )
                    if (!config.canBreakMapBlocks() && !whitelisted) {
                        event.isCancelled = true
                        player.sendMessage(MSG.lang("gameplay.block_break_player_placed_only"))
                        return
                    }
                }
            }

            // BedModus: als een bed-blok gesloopt wordt → notificeer BedModeManager
            if (instance.arenaInstance.config.isBedMode() && event.block.type.name.endsWith("_BED")) {
                val bd = event.block.blockData as? BedData
                if (bd != null) {
                    val headLoc = when (bd.part) {
                        BedData.Part.HEAD -> event.block.location
                        else -> event.block.getRelative(bd.facing).location
                    }
                    val companionLoc = when (bd.part) {
                        BedData.Part.HEAD -> event.block.getRelative(bd.facing.oppositeFace).location
                        else -> event.block.getRelative(bd.facing).location
                    }
                    // Companion-blok ook uit tracking verwijderen (Minecraft verwijdert het stil)
                    placed?.remove(companionLoc)
                    val result = plugin.bedModeManager.onBedBroken(headLoc)
                    if (result != null) {
                        val (ownerUUID, _) = result
                        val owner = plugin.server.getPlayer(ownerUUID)
                        val ownerName = owner?.name ?: "Unknown"
                        val destroyerName = player.name
                        val msg = MSG.chatColors("&c&l✘ &c${destroyerName} &cdestroyed the bed of &c&l${ownerName}&c!")
                        (instance.players + instance.spectators).mapNotNull { plugin.server.getPlayer(it) }.forEach { p ->
                            p.sendMessage(msg)
                        }
                        owner?.sendTitle("§c§l✘ BED DESTROYED!", "§7You can no longer respawn!", 0, 60, 10)
                    }
                }
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
        // Editor players can freely place any block in their edit world
        if (plugin.adminEditorManager.isActive(player)) return
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

            // BedModus: track beide bed-blokken en registreer bij BedModeManager
            if (instance.arenaInstance.config.isBedMode() && event.block.type.name.endsWith("_BED")) {
                val bd = event.block.blockData as? BedData
                if (bd != null) {
                    val headLoc: Location
                    val footLoc: Location
                    when (bd.part) {
                        BedData.Part.HEAD -> {
                            headLoc = event.block.location
                            footLoc = event.block.getRelative(bd.facing.oppositeFace).location
                        }
                        else -> {
                            footLoc = event.block.location
                            headLoc = event.block.getRelative(bd.facing).location
                        }
                    }
                    // Voeg ook het companion-blok toe aan placed tracking
                    playerPlacedBlocksForInstance.add(headLoc)
                    playerPlacedBlocksForInstance.add(footLoc)
                    plugin.bedModeManager.placeBed(player.uniqueId, headLoc, footLoc)
                    player.sendMessage(MSG.chatColors("&a&lBED PLACED &7— this is now your respawn point!"))
                }
            }

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

    // =================== EXPLOSIONS ===================

    /** TNT, creepers, end crystals, etc. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityExplode(event: EntityExplodeEvent) {
        filterExplosionBlocks(event.location, event.blockList())
    }

    /** Beds / respawn anchors detonating in the wrong dimension, etc. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockExplode(event: BlockExplodeEvent) {
        filterExplosionBlocks(event.block.location, event.blockList())
    }

    /**
     * Protects original map blocks from explosions when Can-BlowUp-Map-Blocks is off.
     * Player-placed blocks are always allowed to blow up (and are untracked when they do).
     * In GhostMode: explosions vernietigen ook map-blokken (chaos-modus).
     * If the explosion is not inside any arena instance, nothing is changed.
     */
    private fun filterExplosionBlocks(center: Location, blocks: MutableList<Block>) {
        val instance = getInstanceAt(center) ?: return
        val placed = playerPlacedBlocks[instance.id]
        val config = instance.arenaInstance.config

        val destroyAll = config.canBlowUpMapBlocks() || config.isGhostMode()
        if (destroyAll) {
            // Explosions may level the map; stop tracking any player-placed blocks destroyed.
            if (placed != null) blocks.forEach { placed.remove(it.location) }
            // BedModus: check of er bed-blokken vernietigd worden
            if (config.isBedMode()) notifyBedsInExplosion(instance, blocks)
            return
        }

        // Keep only player-placed blocks in the blast; every original map block is protected.
        val iterator = blocks.iterator()
        while (iterator.hasNext()) {
            val block = iterator.next()
            if (placed == null || !placed.remove(block.location)) {
                iterator.remove() // map block → protected
            }
        }
        // BedModus: ook hier checken (player-placed bed-blokken kunnen wél opblazen)
        if (config.isBedMode()) notifyBedsInExplosion(instance, blocks)
    }

    private fun notifyBedsInExplosion(instance: GameInstance, blocks: List<Block>) {
        for (block in blocks) {
            if (!block.type.name.endsWith("_BED")) continue
            val bd = block.blockData as? BedData ?: continue
            val headLoc = when (bd.part) {
                BedData.Part.HEAD -> block.location
                else -> block.getRelative(bd.facing).location
            }
            val result = plugin.bedModeManager.onBedBroken(headLoc) ?: continue
            val (ownerUUID, _) = result
            val owner = plugin.server.getPlayer(ownerUUID)
            val ownerName = owner?.name ?: "Unknown"
            val msg = MSG.chatColors("&c&l✘ &cThe bed of &c&l${ownerName} &cwas destroyed by an explosion!")
            (instance.players + instance.spectators).mapNotNull { plugin.server.getPlayer(it) }.forEach { p ->
                p.sendMessage(msg)
            }
            owner?.sendTitle("§c§l✘ BED DESTROYED!", "§7Explosion! You can no longer respawn.", 0, 60, 10)
        }
    }

    /**
     * Finds the game instance whose arena region contains [loc]. Parallel instances share
     * one world at different origins, so we match by world + horizontal distance to origin
     * (bounded by the schematic save radius).
     */
    private fun getInstanceAt(loc: Location): GameInstance? {
        return plugin.gameManager.getAllInstances().firstOrNull { inst ->
            val origin = inst.arenaInstance.origin
            val radius = inst.arenaInstance.config.getSaveRadius().toDouble()
            loc.world == origin.world &&
                kotlin.math.abs(loc.x - origin.x) <= radius &&
                kotlin.math.abs(loc.z - origin.z) <= radius
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

        // Enforce XZ arena boundaries during active game.
        // Y height is a build limit only — flying above the map is allowed,
        // only the void (below deathHeight, checked above) kills.
        if (instance.state == GameState.ACTIVE) {
            val origin = instance.arenaInstance.origin
            val buildRange = instance.arenaInstance.config.getBuildRange()

            val deltaX = kotlin.math.abs(player.location.x - origin.x)
            val deltaZ = kotlin.math.abs(player.location.z - origin.z)

            if (deltaX > buildRange || deltaZ > buildRange) {
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
