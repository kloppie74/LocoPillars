package org.Kloppie74.locoPillars.utils.spectator

import org.bukkit.entity.Player
import org.bukkit.event.*
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.*
import org.bukkit.event.hanging.HangingBreakByEntityEvent
import org.bukkit.event.inventory.*
import org.bukkit.event.player.*
import org.bukkit.event.vehicle.*
import org.bukkit.GameMode
import org.bukkit.scheduler.BukkitRunnable
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.hotbar.HotbarItemManager
import org.Kloppie74.locoPillars.utils.hotbar.SpeedGui
import org.Kloppie74.locoPillars.utils.hotbar.HotbarUtils
import org.Kloppie74.locoPillars.utils.teams.GlobalVisibilityTicker

class SpectatorInteractionHandler(
    private val plugin: LocoPillars,
    private val hotbarManager: HotbarItemManager
) : Listener {

    companion object {
        private val SPECTATOR_CACHE = ConcurrentHashMap<UUID, Long>()
        private const val CACHE_EXPIRY_MS = 5000L

        fun clearCache() {
            SPECTATOR_CACHE.clear()
        }
    }

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)

        object : BukkitRunnable() {
            override fun run() {
                val now = System.currentTimeMillis()
                SPECTATOR_CACHE.entries.removeIf { now - it.value > CACHE_EXPIRY_MS }
            }
        }.runTaskTimerAsynchronously(plugin, 600L, 600L)
    }

    fun isSpectator(player: Player?): Boolean {
        if (player == null) return false
        return SpectatorUtils.isSpectator(player, plugin)
    }

    private fun hasSpectatorOverride(player: Player?): Boolean {
        if (player == null) return false
        return player.isOp && player.gameMode == GameMode.CREATIVE
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        @Suppress("DEPRECATION")
        if (SpeedGui.isSpeedGui(event.view.title)) {
            return
        }

        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            val clickedItem = event.currentItem
            if (HotbarUtils.isLockedHotbarItem(clickedItem, plugin)) {
                if (event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
                    event.click.isShiftClick) {
                    event.isCancelled = true
                }
                return
            }
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryInteract(event: InventoryInteractEvent) {
        val player = event.whoClicked as? Player ?: return
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteractEntity(event: PlayerInteractEntityEvent) {
        val player = event.player
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteractAtEntity(event: PlayerInteractAtEntityEvent) {
        val player = event.player
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!isSpectator(player)) return

        if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.RIGHT_CLICK_BLOCK) {
            val item = player.inventory.itemInMainHand
            if (HotbarUtils.isLockedHotbarItem(item, plugin)) {
                return
            }
        }

        if ((event.action == Action.LEFT_CLICK_BLOCK ||
                    event.action == Action.RIGHT_CLICK_BLOCK ||
                    event.action == Action.PHYSICAL) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        val player = event.damager as? Player ?: return
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityDamage(event: EntityDamageEvent) {
        val player = event.entity as? Player ?: return
        if (isSpectator(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityTarget(event: EntityTargetEvent) {
        val player = event.target as? Player ?: return
        if (isSpectator(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        val player = event.entity as? Player ?: return
        if (isSpectator(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityPickup(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onItemDamage(event: PlayerItemDamageEvent) {
        val player = event.player
        if (isSpectator(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onItemConsume(event: PlayerItemConsumeEvent) {
        val player = event.player
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
            // Simply queue hotbar update - no scheduled task needed
            HotbarUtils.setupPlayerHotbar(player, plugin)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onVehicleDamage(event: VehicleDamageEvent) {
        val player = event.attacker as? Player ?: return
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onVehicleDestroy(event: VehicleDestroyEvent) {
        val player = event.attacker as? Player ?: return
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onVehicleEnter(event: VehicleEnterEvent) {
        val player = event.entered as? Player ?: return
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onVehicleCollision(event: VehicleEntityCollisionEvent) {
        val player = event.entity as? Player ?: return
        if (isSpectator(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onVehicleExit(event: VehicleExitEvent) {
        val player = event.exited as? Player ?: return
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            // Simply update hotbar - no scheduled task needed
            HotbarUtils.setupPlayerHotbar(player, plugin)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onHangingBreak(event: HangingBreakByEntityEvent) {
        val player = event.remover as? Player ?: return
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onArmorStandManipulate(event: PlayerArmorStandManipulateEvent) {
        val player = event.player
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerBucketEmpty(event: PlayerBucketEmptyEvent) {
        val player = event.player
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerBucketFill(event: PlayerBucketFillEvent) {
        val player = event.player
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerEditBook(event: PlayerEditBookEvent) {
        val player = event.player
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerFish(event: PlayerFishEvent) {
        val player = event.player
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onProjectileLaunch(event: ProjectileLaunchEvent) {
        val player = event.entity.shooter as? Player ?: return
        if (isSpectator(player) && !hasSpectatorOverride(player)) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val player = event.player
        val fromWorld = event.from.name
        val toWorld = player.world.name

        SPECTATOR_CACHE.remove(player.uniqueId)

        // Handle world change and queue visibility update
        SpectatorUtils.handleWorldChange(player, fromWorld, toWorld, plugin)
        GlobalVisibilityTicker.queueUpdate(player)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (isSpectator(player) && event.isSneaking) {
            // Simply update hotbar - no scheduled task needed
            HotbarUtils.setupPlayerHotbar(player, plugin)
        }
    }
}