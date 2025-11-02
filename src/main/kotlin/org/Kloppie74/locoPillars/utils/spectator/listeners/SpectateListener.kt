package org.Kloppie74.locoPillars.utils.spectator.listeners

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.entity.PlayerDeathEvent as EntityPlayerDeathEvent
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.hotbar.HotbarItemManager
import org.Kloppie74.locoPillars.utils.hotbar.SpeedGui
import org.Kloppie74.locoPillars.utils.hotbar.HotbarUtils
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import org.Kloppie74.locoPillars.utils.spectator.SpectatorSpeedManager
import org.Kloppie74.locoPillars.utils.spectator.SpectatorVisibilityManager

class SpectateListener(
    private val plugin: LocoPillars,
    private val itemManager: HotbarItemManager
) : Listener {

    // ==================== PLAYER JOIN/LEAVE EVENTS ====================

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            // Let HotbarUtils handle everything - it will coordinate with SpectatorUtils if needed
            HotbarUtils.setupPlayerHotbar(player, plugin)

            // Update visibility for all players
            updateGlobalVisibility()
        }, 10L)
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        // Clean up player from all caches
        SpectatorUtils.clearCache(player)
        HotbarUtils.clearCache()
    }

    // ==================== RESPAWN & TELEPORT EVENTS ====================

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            // Let HotbarUtils determine the correct state and handle everything
            HotbarUtils.setupPlayerHotbar(player, plugin)
            updateVisibilityForPlayer(player)
        }, 5L)
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player

        // Don't interfere with spectator menu teleportation
        if (event.cause == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            return
        }

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            // Check if hotbar type changed and handle accordingly
            val oldType = HotbarUtils.getCurrentHotbarType(player, plugin)
            HotbarUtils.setupPlayerHotbar(player, plugin)
            val newType = HotbarUtils.getCurrentHotbarType(player, plugin)

            if (oldType != newType) {
                HotbarUtils.handleHotbarTypeChange(player, oldType, newType, plugin)
            }

            updateVisibilityForPlayer(player)
        }, 5L)
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        val fromWorld = event.from.name
        val toWorld = player.world.name

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            // Let SpectatorUtils handle world changes - it coordinates with HotbarUtils
            SpectatorUtils.handleWorldChange(player, fromWorld, toWorld, plugin)
            updateVisibilityForPlayer(player)
        }, 5L)
    }

    // ==================== DEATH EVENT ====================

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerDeath(event: EntityPlayerDeathEvent) {
        val player = event.entity
        val instance = plugin.gameManager.getPlayerInstance(player)

        if (instance != null) {
            // Player died in a game - they should become a spectator in that game
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                // Set as spectator in the game world
                SpectatorUtils.setPlayerAsSpectator(player, plugin)

                // Update visibility for the whole game instance
                plugin.spectatorInventoryManager.updateVisibilityForGameInstance(instance)
            }, 1L)
        }
    }

    // ==================== INTERACTION EVENTS ====================

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val heldItem = player.inventory.itemInMainHand

        // Check if this is a locked hotbar item
        if (HotbarUtils.isLockedHotbarItem(heldItem, plugin)) {
            // Let the item system handle the interaction
            handleHotbarItemInteraction(player, heldItem, event)
            event.isCancelled = true
            return
        }

        // If player is spectator and not using custom items, let SpectatorInteractionHandler manage it
        if (SpectatorUtils.isSpectator(player, plugin)) {
            // SpectatorInteractionHandler will handle this automatically
            return
        }
    }

    private fun handleHotbarItemInteraction(player: Player, item: org.bukkit.inventory.ItemStack, event: PlayerInteractEvent) {
        val type = HotbarUtils.getCurrentHotbarType(player, plugin)
        val items = itemManager.getCustomItemsForType(type.lowercase())

        items.forEach { custom ->
            if (custom.item != null && itemManager.isSameItem(item, custom.item)) {
                when {
                    custom.command != null && custom.command.contains("%speed%") -> {
                        // Open speed GUI
                        SpeedGui.open(player)
                    }
                    custom.command != null -> {
                        // Execute command
                        val cmd = custom.command.removePrefix("/")
                        player.performCommand(cmd)
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerItemHeld(event: PlayerItemHeldEvent) {
        val player = event.player
        val newItem = player.inventory.getItem(event.newSlot)

        // Only handle if it's a spectator and using custom items
        if (SpectatorUtils.isSpectator(player, plugin) && HotbarUtils.isLockedHotbarItem(newItem, plugin)) {
            val type = HotbarUtils.getCurrentHotbarType(player, plugin)
            val items = itemManager.getCustomItemsForType(type.lowercase())

            items.forEach { custom ->
                if (custom.speed != null && custom.item != null && itemManager.isSameItem(newItem, custom.item)) {
                    // Let SpectatorSpeedManager handle the speed change
                    val speedLevel = (custom.speed * 10).toInt().coerceIn(1, 7)
                    SpectatorSpeedManager.setPlayerSpeed(player, speedLevel)
                }
            }
        }
    }

    // ==================== INVENTORY EVENTS ====================

    @EventHandler(priority = EventPriority.NORMAL)
    fun onInventoryClick(event: InventoryClickEvent) {
        // Let SpeedGui handle its own events
        SpeedGui.handleClick(event)

        // SpectatorInteractionHandler will handle other inventory restrictions automatically
    }

    // ==================== VISIBILITY MANAGEMENT ====================

    private fun updateVisibilityForPlayer(player: Player) {
        val instance = plugin.gameManager.getPlayerInstance(player)

        if (instance != null) {
            // Player is in a game - update visibility for that instance
            plugin.spectatorInventoryManager.updateVisibilityForGameInstance(instance)
        } else {
            // Player is not in a game - handle lobby/spectator visibility
            if (SpectatorUtils.isSpectator(player, plugin)) {
                SpectatorVisibilityManager.updateSpectatorVisibility(player, plugin)
            } else {
                // Player is in lobby - restore normal visibility
                SpectatorVisibilityManager.restoreNormalVisibility(player, plugin)
            }
        }
    }

    private fun updateGlobalVisibility() {
        // Update visibility for all online players
        Bukkit.getOnlinePlayers().forEach { player ->
            updateVisibilityForPlayer(player)
        }
    }

    // ==================== PUBLIC UTILITY METHODS ====================

    /**
     * Called externally when game state changes
     */
    fun onGameStateChange(instance: org.Kloppie74.locoPillars.game.instance.GameInstance) {
        // Update visibility for all players in the instance
        plugin.spectatorInventoryManager.updateVisibilityForGameInstance(instance)

        // Update hotbar for all players in the instance
        instance.players.mapNotNull { Bukkit.getPlayer(it) }.forEach { player ->
            HotbarUtils.setupPlayerHotbar(player, plugin)
        }
    }

    /**
     * Called when a player joins or leaves a game
     */
    fun onPlayerGameStatusChange(player: Player) {
        // Clear caches to force refresh
        SpectatorUtils.clearCache(player)
        HotbarUtils.clearCache()

        // Update player's state
        HotbarUtils.setupPlayerHotbar(player, plugin)
        updateVisibilityForPlayer(player)
    }

    /**
     * Forces a complete refresh for a player
     */
    fun refreshPlayer(player: Player) {
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val instance = plugin.gameManager.getPlayerInstance(player)
            if (instance != null) {
                updateVisibilityForPlayer(player)
            } else {
                HotbarUtils.setupPlayerHotbar(player, plugin)
                updateVisibilityForPlayer(player)
            }
        }, 1L)
    }
}