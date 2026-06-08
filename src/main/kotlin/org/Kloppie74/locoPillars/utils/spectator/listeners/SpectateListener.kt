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

    /**
     * Hotbar-types die bij een actieve game horen. Voor deze types mag de hotbar NOOIT
     * opnieuw gegeven worden bij teleport/respawn, want givePlayerItems() doet
     * inventory.clear() en zou zo de game-items van de speler wissen.
     */
    private val GAME_HOTBAR_TYPES = setOf("game", "game_lobby", "cage")

    // ==================== PLAYER JOIN/LEAVE EVENTS ====================

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (plugin.adminEditorManager.isActive(player)) return@Runnable
            HotbarUtils.setupPlayerHotbar(player, plugin)
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
            if (plugin.adminEditorManager.isActive(player)) return@Runnable
            // FIX: tijdens een game NOOIT de hotbar opnieuw geven (zou inventory wissen).
            if (HotbarUtils.getCurrentHotbarType(player, plugin) in GAME_HOTBAR_TYPES) {
                updateVisibilityForPlayer(player)
                return@Runnable
            }
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
            if (plugin.adminEditorManager.isActive(player)) return@Runnable
            val oldType = HotbarUtils.getCurrentHotbarType(player, plugin)

            // FIX: als de speler in een game-gerelateerde hotbar zit (game/game_lobby/cage)
            // mag een teleport NOOIT setupPlayerHotbar aanroepen — dat wist via
            // givePlayerItems() de volledige inventory. Alleen visibility verversen.
            if (oldType in GAME_HOTBAR_TYPES) {
                updateVisibilityForPlayer(player)
                return@Runnable
            }

            HotbarUtils.setupPlayerHotbar(player, plugin)
            val newType = HotbarUtils.getCurrentHotbarType(player, plugin)
            if (oldType != newType) HotbarUtils.handleHotbarTypeChange(player, oldType, newType, plugin)
            updateVisibilityForPlayer(player)
        }, 5L)
    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerChangedWorld(event: PlayerChangedWorldEvent) {
        val player = event.player
        val fromWorld = event.from.name
        val toWorld = player.world.name

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            if (plugin.adminEditorManager.isActive(player)) return@Runnable
            SpectatorUtils.handleWorldChange(player, fromWorld, toWorld, plugin)
            updateVisibilityForPlayer(player)
        }, 5L)
    }

    // ==================== INTERACTION EVENTS ====================

    @EventHandler(priority = EventPriority.NORMAL)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        // BELANGRIJK: hotbar-item interacties worden UITSLUITEND door ItemHotBarListener
        // afgehandeld (die heeft een hand-check + cooldown). Vroeger voerde DEZE listener
        // het commando óók uit → dubbele uitvoering. En als dat commando synchroon de
        // game-state wijzigt (bijv. /join), voerde deze tweede listener meteen het NIEUWE
        // item in de nieuwe hotbar uit. Daarom hier niets meer met hotbar-items doen.
        return
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