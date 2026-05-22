package org.Kloppie74.locoPillars.utils.hotbar.listeners

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitRunnable
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.hotbar.HotbarItemManager
import org.Kloppie74.locoPillars.utils.hotbar.SpeedGui
import org.Kloppie74.locoPillars.utils.hotbar.HotbarUtils
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import org.Kloppie74.locoPillars.utils.spectator.SpectatorSpeedManager
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction

class ItemHotBarListener(
    private val plugin: LocoPillars,
    private val itemManager: HotbarItemManager
) : Listener {

    // ==================== PLAYER STATE EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (plugin.adminEditorManager.isActive(player)) return@Runnable
            HotbarUtils.setupPlayerHotbar(player, plugin)
        }, 10L)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        // Clear all caches for this player
        HotbarUtils.clearCache()
        SpectatorUtils.clearCache(player)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (plugin.adminEditorManager.isActive(player)) return@Runnable
            HotbarUtils.setupPlayerHotbar(player, plugin)
        }, 5L)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (plugin.adminEditorManager.isActive(player)) return@Runnable
            val oldType = HotbarUtils.getCurrentHotbarType(player, plugin)
            val newType = HotbarUtils.determineHotbarType(player, plugin)
            if (oldType != newType) HotbarUtils.handleHotbarTypeChange(player, oldType, newType, plugin)
        }, 5L)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        val fromWorld = event.from.name
        val toWorld = player.world.name

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (plugin.adminEditorManager.isActive(player)) return@Runnable
            SpectatorUtils.handleWorldChange(player, fromWorld, toWorld, plugin)
        }, 5L)
    }

    // ==================== INTERACTION EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val heldItem = player.inventory.itemInMainHand

        // Only handle locked hotbar items
        if (event.action.equals(Action.RIGHT_CLICK_AIR) || event.action.equals(Action.RIGHT_CLICK_BLOCK) ||
            event.action.equals(Action.LEFT_CLICK_AIR) || event.action.equals(Action.LEFT_CLICK_BLOCK)) {
            if (HotbarUtils.isLockedHotbarItem(heldItem, plugin)) {
                handleLockedItemInteraction(player, heldItem)
                event.isCancelled = true
            }
        }
        // Let SpectatorInteractionHandler handle spectator restrictions
    }

    private fun handleLockedItemInteraction(player: Player, item: ItemStack) {
        val type = HotbarUtils.getCurrentHotbarType(player, plugin)
        val items = itemManager.getCustomItemsForType(type.lowercase())

        items.forEach { custom ->
            if (custom.item != null && itemManager.isSameItem(item, custom.item)) {
                when {
                    // Speed selection GUI
                    custom.command != null && custom.command.contains("%speed%") -> {
                        SpeedGui.open(player)
                    }

                    // Command execution
                    custom.command != null -> {
                        val cmd = custom.command.removePrefix("/")
                        Bukkit.dispatchCommand(player, cmd)
                    }

                    // Speed change on hold (for spectators)
                    custom.speed != null && SpectatorUtils.isSpectator(player, plugin) -> {
                        val speedLevel = (custom.speed * 10).toInt().coerceIn(1, 7)
                        SpectatorSpeedManager.setPlayerSpeed(player, speedLevel)
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        val newItem = player.inventory.getItem(event.newSlot)

        // Only handle if it's a locked hotbar item and player is spectator
        if (HotbarUtils.isLockedHotbarItem(newItem, plugin) && SpectatorUtils.isSpectator(player, plugin)) {
            val type = HotbarUtils.getCurrentHotbarType(player, plugin)
            val items = itemManager.getCustomItemsForType(type.lowercase())

            items.forEach { custom ->
                if (custom.speed != null && custom.item != null && itemManager.isSameItem(newItem, custom.item)) {
                    // Apply speed through SpectatorSpeedManager
                    val speedLevel = (custom.speed * 10).toInt().coerceIn(1, 7)
                    SpectatorSpeedManager.setPlayerSpeed(player, speedLevel)
                }
            }
        }
    }

    // ==================== INVENTORY PROTECTION EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem

        if (SpeedGui.isSpeedGui(event.view.title)) {
            SpeedGui.handleClick(event)
            return
        }

        if (HotbarUtils.isLockedHotbarItem(clickedItem, plugin)) {
            event.isCancelled = true
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                HotbarUtils.setupPlayerHotbar(player, plugin)
            }, 1L)
            return
        }

        // FIX: Check that hotbarButton is valid before accessing inventory
        if (event.action == InventoryAction.HOTBAR_SWAP || event.action.name.contains("SWAP") ||
            event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) {

            if (event.hotbarButton >= 0 && event.hotbarButton < player.inventory.size) {
                val hotbarItem = player.inventory.getItem(event.hotbarButton)
                if (HotbarUtils.isLockedHotbarItem(hotbarItem, plugin)) {
                    event.isCancelled = true
                    Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                        HotbarUtils.setupPlayerHotbar(player, plugin)
                    }, 1L)
                    return
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        val droppedItem = event.itemDrop.itemStack

        // Prevent dropping of locked hotbar items
        if (HotbarUtils.isLockedHotbarItem(droppedItem, plugin)) {
            event.isCancelled = true

            // Refresh hotbar to ensure consistency
            object : BukkitRunnable() {
                override fun run() {
                    HotbarUtils.setupPlayerHotbar(player, plugin)
                }
            }.runTaskLater(plugin, 1L)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val player = event.player
        val mainHand = event.mainHandItem
        val offHand = event.offHandItem

        // Prevent swapping locked hotbar items
        if (HotbarUtils.isLockedHotbarItem(mainHand, plugin) ||
            HotbarUtils.isLockedHotbarItem(offHand, plugin)) {
            event.isCancelled = true

            // Refresh hotbar to ensure consistency
            object : BukkitRunnable() {
                override fun run() {
                    HotbarUtils.setupPlayerHotbar(player, plugin)
                }
            }.runTaskLater(plugin, 1L)
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Forces a hotbar refresh for a player
     */
    fun refreshPlayerHotbar(player: Player) {
        HotbarUtils.setupPlayerHotbar(player, plugin)
    }

    /**
     * Updates all players' hotbars
     */
    fun refreshAllHotbars() {
        Bukkit.getOnlinePlayers().forEach { player ->
            HotbarUtils.setupPlayerHotbar(player, plugin)
        }
    }

    /**
     * Handles external hotbar type changes (called from game manager)
     */
    fun handleTypeChange(player: Player, oldType: String, newType: String) {
        HotbarUtils.handleHotbarTypeChange(player, oldType, newType, plugin)
    }

    /**
     * Gets current hotbar type for a player
     */
    fun getCurrentHotbarType(player: Player): String {
        return HotbarUtils.getCurrentHotbarType(player, plugin)
    }

    /**
     * Checks if an item is locked
     */
    fun isLockedHotbarItem(item: ItemStack?): Boolean {
        return HotbarUtils.isLockedHotbarItem(item, plugin)
    }
}