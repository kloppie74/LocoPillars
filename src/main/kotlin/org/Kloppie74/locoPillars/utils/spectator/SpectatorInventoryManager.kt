package org.Kloppie74.locoPillars.utils.spectator

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.configuration.file.YamlConfiguration
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.hotbar.HotbarItemManager
import org.Kloppie74.locoPillars.utils.hotbar.SpeedGui
import org.Kloppie74.locoPillars.utils.hotbar.HotbarUtils
import org.Kloppie74.locoPillars.utils.teams.GlobalVisibilityTicker
import java.io.File

class SpectatorInventoryManager(
    private val plugin: LocoPillars,
    private val hotbarManager: HotbarItemManager
) : Listener {

    companion object {
        fun clearCache() {
            // Clear any local caches
            SpectatorSpeedManager.clearCache()
            SpectatorVisibilityManager.clearCache()
        }
    }

    private val itemsConfig: YamlConfiguration

    init {
        plugin.server.pluginManager.registerEvents(this, plugin)

        // Load items.yml config
        val itemsFile = File(plugin.dataFolder, "items.yml")
        if (!itemsFile.exists()) {
            itemsFile.writeText(getDefaultItemsConfig())
        }
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile)
    }

    private fun getDefaultItemsConfig(): String {
        return """
Menu:
  Speed:
    Title: "§8● §fSpeed Selection §8●"
    Border:
      Item: "BLACK_STAINED_GLASS_PANE"
      Display-name: " "
      Lores: []
    Filler:
      Item: "GRAY_STAINED_GLASS_PANE"
      Display-name: " "
      Lores: []
    Speeds:
      1:
        Item: "FEATHER"
        Display-name: "§bSpeed §f1 §7(§e0.1§7)"
        Lores:
          - "§7Click to set speed to §e0.1"
        Slot: 10
        SpeedLevel: 1
      2:
        Item: "FEATHER"
        Display-name: "§bSpeed §f2 §7(§e0.2§7)"
        Lores:
          - "§7Click to set speed to §e0.2"
        Slot: 11
        SpeedLevel: 2
      3:
        Item: "FEATHER"
        Display-name: "§bSpeed §f3 §7(§e0.3§7)"
        Lores:
          - "§7Click to set speed to §e0.3"
        Slot: 12
        SpeedLevel: 3
      4:
        Item: "FEATHER"
        Display-name: "§bSpeed §f4 §7(§e0.4§7)"
        Lores:
          - "§7Click to set speed to §e0.4"
        Slot: 13
        SpeedLevel: 4
      5:
        Item: "FEATHER"
        Display-name: "§bSpeed §f5 §7(§e0.5§7)"
        Lores:
          - "§7Click to set speed to §e0.5"
        Slot: 14
        SpeedLevel: 5
      6:
        Item: "FEATHER"
        Display-name: "§bSpeed §f6 §7(§e0.6§7)"
        Lores:
          - "§7Click to set speed to §e0.6"
        Slot: 15
        SpeedLevel: 6
      7:
        Item: "FEATHER"
        Display-name: "§bSpeed §f7 §7(§e0.7§7)"
        Lores:
          - "§7Click to set speed to §e0.7"
        Slot: 16
        SpeedLevel: 7
    Close:
      Item: "BARRIER"
      Display-name: "§cClose Menu"
      Lores:
        - "§7Click to close this menu"
      Slot: 22
        """.trimIndent()
    }

    @EventHandler
    fun onRespawn(event: PlayerRespawnEvent) {
        // Queue hotbar setup - no scheduled task needed
        HotbarUtils.setupPlayerHotbar(event.player, plugin)
        GlobalVisibilityTicker.queueUpdate(event.player)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        // Delegate speed GUI handling to SpeedGui
        SpeedGui.handleClick(event)
    }

    @EventHandler
    fun onTeleportMenu(event: PlayerTeleportEvent) {
        val player = event.player
        if (event.cause != PlayerTeleportEvent.TeleportCause.SPECTATE) return

        val instance = plugin.gameManager.getPlayerInstance(player)
        if (instance == null && player.world.name.startsWith("pillars_")) {
            event.isCancelled = true
            // Queue hotbar setup - no scheduled task needed
            HotbarUtils.setupPlayerHotbar(player, plugin)
            GlobalVisibilityTicker.queueUpdate(player)
        }
    }

    /**
     * Checks and updates inventory through HotbarUtils
     */
    fun checkAndUpdateInventory(player: Player) {
        HotbarUtils.setupPlayerHotbar(player, plugin)
        GlobalVisibilityTicker.queueUpdate(player)
    }

    /**
     * Gives spectator inventory through HotbarUtils
     */
    fun giveSpectatorInventory(player: Player) {
        HotbarUtils.setupPlayerHotbar(player, plugin)
        GlobalVisibilityTicker.queueUpdate(player)
    }

    /**
     * Opens speed GUI through SpeedGui
     */
    fun openSpeedGUI(player: Player) {
        SpeedGui.open(player)
    }

    /**
     * Sets spectator visibility through SpectatorVisibilityManager
     */
    fun setSpectatorVisibility(target: Player, hideFrom: List<Player>, showTo: List<Player>) {
        SpectatorVisibilityManager.setSpectatorVisibility(target, hideFrom, showTo, plugin)
    }

    /**
     * Checks if player is hiding spectators through SpectatorVisibilityManager
     */
    fun isHidingSpectators(player: Player): Boolean {
        return SpectatorVisibilityManager.isHidingSpectators(player)
    }

    /**
     * Sets hiding spectators state through SpectatorVisibilityManager
     */
    fun setHidingSpectators(player: Player, hiding: Boolean) {
        SpectatorVisibilityManager.setHidingSpectators(player, hiding)
    }

    /**
     * Gets current speed level through SpectatorSpeedManager
     */
    fun getCurrentSpeedLevel(player: Player): Int {
        return SpectatorSpeedManager.getCurrentSpeedLevel(player)
    }

    /**
     * Sets player speed through SpectatorSpeedManager
     */
    fun setPlayerSpeed(player: Player, speedLevel: Int) {
        SpectatorSpeedManager.setPlayerSpeed(player, speedLevel)
    }

    /**
     * Toggles spectator visibility through SpectatorVisibilityManager
     */
    fun toggleSpectatorVisibility(player: Player) {
        SpectatorVisibilityManager.toggleSpectatorsVisibility(player, plugin)
    }

    /**
     * Updates visibility for game instance through SpectatorVisibilityManager
     */
    fun updateVisibilityForGameInstance(instance: org.Kloppie74.locoPillars.game.instance.GameInstance) {
        SpectatorVisibilityManager.updateVisibilityForGameInstance(instance, plugin)
    }
}