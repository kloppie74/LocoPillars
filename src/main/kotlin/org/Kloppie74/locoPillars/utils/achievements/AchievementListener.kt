package org.Kloppie74.locoPillars.utils.achievements

import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.player.*
import org.bukkit.event.block.*
import org.bukkit.event.entity.*
import org.bukkit.entity.Player
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.player.PlayerPickupItemEvent
import org.Kloppie74.locoPillars.LocoPillars

/**
 * Listener for achievement triggers.
 * Only reads and writes achievement progress via AchievementsTable.
 * Also provides public methods for game logic triggers (like WIN_GAME etc).
 */
class AchievementListener(
    private val manager: AchievementManager,
    private val plugin: LocoPillars
) : Listener {

    private val lastBlockCoords = mutableMapOf<String, Triple<Int, Int, Int>>()
    private val lastPlaytimeUpdate = mutableMapOf<String, Long>()

    /**
     * Update all active achievements for this trigger for the player.
     * Each achievement tracks its own progress, stored in AchievementsTable.
     */
    private fun update(trigger: AchievementTrigger, player: Player, increment: Int = 1) {
        val playerId = player.uniqueId.toString()
        val relevantAchievements = manager.getAllAchievements().filter {
            it.requirementType == trigger.name && it.isActive
        }
        for (achievement in relevantAchievements) {
            val oldProgress = manager.getAchievementProgress(playerId, achievement)
            val newProgress = oldProgress + increment
            manager.onPlayerAchievementProgress(player, achievement.achievementId, newProgress)
        }
    }

    // ============== Bukkit Events ==============

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        update(AchievementTrigger.BLOCKS_PLACED, event.player, 1)
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        update(AchievementTrigger.BLOCKS_BROKEN, event.player, 1)
    }

    @EventHandler
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val playerId = player.uniqueId.toString()
        val curBlock = Triple(player.location.blockX, player.location.blockY, player.location.blockZ)
        val lastBlock = lastBlockCoords[playerId]
        var distance = 0
        if (lastBlock == null || curBlock != lastBlock) {
            if (lastBlock == null ||
                curBlock.first != lastBlock.first ||
                curBlock.third != lastBlock.third
            ) {
                distance = if (lastBlock == null) 1 else (
                        kotlin.math.abs(curBlock.first - lastBlock.first) +
                                kotlin.math.abs(curBlock.third - lastBlock.third)
                        )
                if (distance >= 1) {
                    update(AchievementTrigger.WALK_DISTANCE, player, distance)
                }
            }
            lastBlockCoords[playerId] = curBlock
        }
    }

    @EventHandler
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val damager = event.damager
        val entity = event.entity
        if (damager is Player) onDamageDealt(damager, event.finalDamage.toInt())
        if (entity is Player) onDamageTaken(entity, event.finalDamage.toInt())
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        val entity = event.entity
        if (entity is Player) {
            if (event.cause == EntityDamageEvent.DamageCause.FALL) {
                onFallDamageTaken(entity, event.finalDamage.toInt())
            } else {
                onDamageTaken(entity, event.finalDamage.toInt())
            }
        }
    }

    @EventHandler
    fun onPlayerExpChange(event: PlayerExpChangeEvent) {
        update(AchievementTrigger.EXPERIENCE_EARNED, event.player, event.amount)
    }

    @EventHandler
    fun onPlayerLevelChange(event: PlayerLevelChangeEvent) {
        val diff = event.newLevel - event.oldLevel
        if (diff > 0) update(AchievementTrigger.LEVEL_UP, event.player, diff)
    }

    @EventHandler
    fun onPlayerPickupItem(event: PlayerPickupItemEvent) {
        update(AchievementTrigger.ITEMS_COLLECTED, event.player, event.item.itemStack.amount)
    }

    @EventHandler
    fun onPlayerItemCraft(event: CraftItemEvent) {
        val player = event.whoClicked as? Player ?: return
        update(AchievementTrigger.ITEMS_CRAFTED, player, event.recipe.result.amount)
    }

    // --- Track playtime (time_played) ---
    @EventHandler
    fun onPlayerJoin(event: PlayerJoinEvent) {
        lastPlaytimeUpdate[event.player.uniqueId.toString()] = System.currentTimeMillis()
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        handlePlaytimeUpdate(event.player)
        lastPlaytimeUpdate.remove(event.player.uniqueId.toString())
    }

    @EventHandler
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        handlePlaytimeUpdate(event.player)
    }

    @EventHandler
    fun onPlayerMovePlaytime(event: PlayerMoveEvent) {
        val player = event.player
        val playerId = player.uniqueId.toString()
        val now = System.currentTimeMillis()
        val last = lastPlaytimeUpdate[playerId] ?: now
        if (now - last >= 60_000) {
            update(AchievementTrigger.TIME_PLAYED, player, 1) // 1 minute increment
            lastPlaytimeUpdate[playerId] = now
        }
    }

    private fun handlePlaytimeUpdate(player: Player) {
        val playerId = player.uniqueId.toString()
        val now = System.currentTimeMillis()
        val last = lastPlaytimeUpdate[playerId] ?: now
        val minutes = ((now - last) / 60_000).toInt()
        if (minutes > 0) {
            update(AchievementTrigger.TIME_PLAYED, player, minutes)
        }
    }

    // ====== Achievement trigger methods for game logic or manual call ======
    fun onWinGame(player: Player) = update(AchievementTrigger.WIN_GAME, player, 1)
    fun onLoseGame(player: Player) = update(AchievementTrigger.LOSE_GAME, player, 1)
    fun onQuitGame(player: Player) = update(AchievementTrigger.QUIT_GAME, player, 1)
    fun onKillPlayer(player: Player) = update(AchievementTrigger.KILL_PLAYER, player, 1)
    fun onDeath(player: Player) = update(AchievementTrigger.DEATH, player, 1)
    fun onAssist(player: Player) = update(AchievementTrigger.ASSIST, player, 1)
    fun onGamePlayed(player: Player) = update(AchievementTrigger.GAMES_PLAYED, player, 1)
    fun onGameWon(player: Player) = update(AchievementTrigger.GAMES_WON, player, 1)
    fun onGameLost(player: Player) = update(AchievementTrigger.GAMES_LOST, player, 1)
    fun onGameQuit(player: Player) = update(AchievementTrigger.GAMES_QUIT, player, 1)
    fun onDoubleKill(player: Player) = update(AchievementTrigger.DOUBLE_KILL, player, 1)
    fun onTripleKill(player: Player) = update(AchievementTrigger.TRIPLE_KILL, player, 1)
    fun onQuadKill(player: Player) = update(AchievementTrigger.QUAD_KILL, player, 1)
    fun onPerfectGame(player: Player) = update(AchievementTrigger.PERFECT_GAME, player, 1)
    fun onComebackWin(player: Player) = update(AchievementTrigger.COMEBACK_WIN, player, 1)
    fun onFastestWin(player: Player) = update(AchievementTrigger.FASTEST_WIN, player, 1)
    fun onLongestGame(player: Player) = update(AchievementTrigger.LONGEST_GAME, player, 1)
    fun onKillStreak(player: Player, amount: Int = 1) = update(AchievementTrigger.KILL_STREAK, player, amount)
    fun onWinStreak(player: Player, amount: Int = 1) = update(AchievementTrigger.WIN_STREAK, player, amount)
    fun onLossStreak(player: Player, amount: Int = 1) = update(AchievementTrigger.LOSS_STREAK, player, amount)
    fun onLevelUp(player: Player, amount: Int = 1) = update(AchievementTrigger.LEVEL_UP, player, amount)
    fun onCoinsEarned(player: Player, amount: Int) = update(AchievementTrigger.COINS_EARNED, player, amount)
    fun onExperienceEarned(player: Player, amount: Int) = update(AchievementTrigger.EXPERIENCE_EARNED, player, amount)
    fun onTeamWin(player: Player) = update(AchievementTrigger.TEAM_WIN, player, 1)
    fun onTeamLoss(player: Player) = update(AchievementTrigger.TEAM_LOSS, player, 1)
    fun onArenasPlayed(player: Player) = update(AchievementTrigger.ARENAS_PLAYED, player, 1)
    fun onFallDamageTaken(player: Player, amount: Int) = update(AchievementTrigger.FALL_DAMAGE_TAKEN, player, amount)
    fun onDamageTaken(player: Player, amount: Int) = update(AchievementTrigger.DAMAGE_TAKEN, player, amount)
    fun onDamageDealt(player: Player, amount: Int) = update(AchievementTrigger.DAMAGE_DEALT, player, amount)
}