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
import org.bukkit.inventory.EquipmentSlot
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
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class ItemHotBarListener(
    private val plugin: LocoPillars,
    private val itemManager: HotbarItemManager
) : Listener {

    /**
     * Types that belong to an active game session.
     * A teleport that would take a player OUT of these types is blocked —
     * transitions are handled explicitly by onPlayerJoinGame / onPlayerLeaveGame.
     */
    private val GAME_HOTBAR_TYPES = setOf("game", "game_lobby", "cage")

    /**
     * Per-player interaction cooldown to prevent double-fires.
     * Bukkit fires PlayerInteractEvent twice per click (main-hand + off-hand),
     * and clients can send rapid repeat packets. 250 ms blocks both.
     */
    private val interactCooldowns = ConcurrentHashMap<UUID, Long>()
    private val INTERACT_COOLDOWN_MS = 250L

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
        HotbarUtils.clearCache()
        SpectatorUtils.clearCache(player)
        interactCooldowns.remove(player.uniqueId)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (plugin.adminEditorManager.isActive(player)) return@Runnable
            // FIX: Never wipe game inventory on respawn. During ACTIVE game the game
            // plugin manages the inventory; setupPlayerHotbar() → givePlayerItems()
            // would call inventory.clear() and destroy all game items.
            val currentType = HotbarUtils.getCurrentHotbarType(player, plugin)
            if (currentType in GAME_HOTBAR_TYPES) return@Runnable
            HotbarUtils.setupPlayerHotbar(player, plugin)
        }, 5L)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (plugin.adminEditorManager.isActive(player)) return@Runnable

            val oldType = HotbarUtils.getCurrentHotbarType(player, plugin)

            // FIX: Als de speler in een game-gerelateerde hotbar-type zit (game,
            // game_lobby, cage), mag een teleport NOOIT een inventory-wipe veroorzaken.
            // Daadwerkelijke transities (join/leave game) worden afgehandeld door
            // onPlayerJoinGame / onPlayerLeaveGame — NIET door de teleport-handler.
            //
            // Zonder deze check: determineHotbarType() kan tijdens een in-game teleport
            // kortdurend "spectate" of "lobby" teruggeven (timing/world-change), waarna
            // handleHotbarTypeChange() → givePlayerItems() → inventory.clear() alles wist.
            if (oldType in GAME_HOTBAR_TYPES) return@Runnable

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

        // FIX: Bukkit fires PlayerInteractEvent TWICE per click — once for HAND and
        // once for OFF_HAND. Only process the main-hand event to avoid executing
        // commands twice.
        if (event.hand != EquipmentSlot.HAND) return

        val action = event.action
        val isClick = action == Action.RIGHT_CLICK_AIR  || action == Action.RIGHT_CLICK_BLOCK ||
                      action == Action.LEFT_CLICK_AIR   || action == Action.LEFT_CLICK_BLOCK

        if (isClick && HotbarUtils.isLockedHotbarItem(heldItem, plugin)) {
            // Cancel first — prevent block placement / block breaking
            event.isCancelled = true

            // FIX: cooldown — blocks the second rapid-fire duplicate packet that some
            // clients send and prevents accidental double-execution within 250 ms.
            val now = System.currentTimeMillis()
            val lastUse = interactCooldowns[player.uniqueId] ?: 0L
            if (now - lastUse < INTERACT_COOLDOWN_MS) return
            interactCooldowns[player.uniqueId] = now

            handleLockedItemInteraction(player, heldItem, action)
        }
    }

    private fun handleLockedItemInteraction(player: Player, item: ItemStack, action: Action) {
        val type  = HotbarUtils.getCurrentHotbarType(player, plugin)
        val items = itemManager.getCustomItemsForType(type.lowercase())

        items.forEach { custom ->
            if (custom.item != null && itemManager.isSameItem(item, custom.item)) {
                when {
                    // Speed selection GUI
                    custom.command != null && custom.command.contains("%speed%") -> {
                        SpeedGui.open(player)
                    }

                    // Command execution (both LMB and RMB trigger commands)
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
        val player   = event.player
        val newItem  = player.inventory.getItem(event.newSlot)

        if (HotbarUtils.isLockedHotbarItem(newItem, plugin) && SpectatorUtils.isSpectator(player, plugin)) {
            val type  = HotbarUtils.getCurrentHotbarType(player, plugin)
            val items = itemManager.getCustomItemsForType(type.lowercase())

            items.forEach { custom ->
                if (custom.speed != null && custom.item != null && itemManager.isSameItem(newItem, custom.item)) {
                    val speedLevel = (custom.speed * 10).toInt().coerceIn(1, 7)
                    SpectatorSpeedManager.setPlayerSpeed(player, speedLevel)
                }
            }
        }
    }

    // ==================== INVENTORY PROTECTION EVENTS ====================

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player      = event.whoClicked as? Player ?: return
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
        val player      = event.player
        val droppedItem = event.itemDrop.itemStack

        if (HotbarUtils.isLockedHotbarItem(droppedItem, plugin)) {
            event.isCancelled = true
            object : BukkitRunnable() {
                override fun run() { HotbarUtils.setupPlayerHotbar(player, plugin) }
            }.runTaskLater(plugin, 1L)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val player   = event.player
        val mainHand = event.mainHandItem
        val offHand  = event.offHandItem

        if (HotbarUtils.isLockedHotbarItem(mainHand, plugin) ||
            HotbarUtils.isLockedHotbarItem(offHand, plugin)) {
            event.isCancelled = true
            object : BukkitRunnable() {
                override fun run() { HotbarUtils.setupPlayerHotbar(player, plugin) }
            }.runTaskLater(plugin, 1L)
        }
    }

    // ==================== UTILITY METHODS ====================

    fun refreshPlayerHotbar(player: Player) {
        HotbarUtils.setupPlayerHotbar(player, plugin)
    }

    fun refreshAllHotbars() {
        Bukkit.getOnlinePlayers().forEach { HotbarUtils.setupPlayerHotbar(it, plugin) }
    }

    fun handleTypeChange(player: Player, oldType: String, newType: String) {
        HotbarUtils.handleHotbarTypeChange(player, oldType, newType, plugin)
    }

    fun getCurrentHotbarType(player: Player): String {
        return HotbarUtils.getCurrentHotbarType(player, plugin)
    }

    fun isLockedHotbarItem(item: ItemStack?): Boolean {
        return HotbarUtils.isLockedHotbarItem(item, plugin)
    }
}
