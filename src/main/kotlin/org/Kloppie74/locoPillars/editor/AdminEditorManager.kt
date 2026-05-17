package org.Kloppie74.locoPillars.editor

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.editor.model.EditModeState
import org.Kloppie74.locoPillars.utils.MSG
import java.util.UUID

/**
 * AdminEditorManager
 *
 * Centrale registry voor actieve edit-mode spelers.
 *
 * ### Fix 1 – Hotbar NA world switch
 * [enable] geeft de hotbar NIET meer direct als [deferHotbar] true is.
 * Roep [onPlayerArrivedInEditWorld] aan vanuit de [PlayerChangedWorldEvent]
 * listener zodra de speler in de edit world is geland.
 * Gebruik `deferHotbar = true` vanuit EditorSessionManager.finishSessionStart()
 * zodat de interne teleport de hotbar niet weggooit.
 *
 * ### Fix 2 – Creative-mode enforcer werkt nu
 * [BukkitTask] ipv raw Int task-ID. Vergeet NIET [startCreativeModeEnforcer]
 * aan te roepen in LocoPillars.onEnable()!
 */
class AdminEditorManager(
    private val plugin: LocoPillars,
    private val hotbarManager: EditorHotbarManager
) {
    private val activeModes = mutableMapOf<UUID, EditModeState>()

    // Fix 2: BukkitTask ipv raw Int
    private var enforcerTask: BukkitTask? = null

    // -------------------------------------------------------------------------
    // Fix 2: Creative-mode enforcer
    // -------------------------------------------------------------------------

    /**
     * Start de repeating task (elke 20 ticks) die editors in CREATIVE houdt.
     * **Aanroepen in LocoPillars.onEnable()** — staat daar momenteel nog niet!
     */
    fun startCreativeModeEnforcer() {
        if (enforcerTask != null) return

        enforcerTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            activeModes.keys.toList().forEach { uuid ->
                val player = plugin.server.getPlayer(uuid) ?: return@forEach
                if (!player.isOnline) return@forEach
                if (player.gameMode != GameMode.CREATIVE) {
                    player.gameMode = GameMode.CREATIVE
                    player.sendActionBar(MSG.chatColors(
                        "&e⚡ Edit mode active — gamemode restored to Creative."))
                }
            }
        }, 20L, 20L)
    }

    /** Stop de enforcer task. Aanroepen in onDisable(). */
    fun stopCreativeModeEnforcer() {
        enforcerTask?.cancel()
        enforcerTask = null
    }

    // -------------------------------------------------------------------------
    // Toggle / Enable / Disable
    // -------------------------------------------------------------------------

    fun toggle(player: Player): Boolean {
        return if (isActive(player)) { disable(player); false }
        else { enable(player); true }
    }

    /**
     * Activeer edit mode voor [player].
     *
     * @param mapName     Optionele map-naam.
     * @param targetWorld Optionele wereld voor directe teleport. Hotbar wordt
     *                    dan uitgesteld tot [onPlayerArrivedInEditWorld].
     * @param deferHotbar Forceer hotbar-uitstel ook zónder targetWorld.
     *                    Gebruik dit vanuit [EditorSessionManager] zodat de
     *                    interne teleport in finishSessionStart() de hotbar
     *                    niet weggooit.
     */
    fun enable(
        player: Player,
        mapName: String? = null,
        targetWorld: org.bukkit.World? = null,
        deferHotbar: Boolean = false
    ) {
        if (isActive(player)) {
            player.sendMessage(MSG.chatColors(
                "&eEdit mode is already active! Use &e/editor toggle &eto exit."))
            player.sendMessage(MSG.chatColors("&7Need help? Use &e/editor help &7or &e/editor info"))
            return
        }

        // Snapshot huidige hotbar vóór we hem overschrijven
        val snapshot = mutableMapOf<Int, ItemStack?>()
        for (slot in 0..8) snapshot[slot] = player.inventory.getItem(slot)?.clone()

        val state = EditModeState(
            playerUuid          = player.uniqueId,
            activeMapName       = mapName,
            hotbarSlotSnapshot  = snapshot,
            originalGameMode    = player.gameMode,
            originalAllowFlight = player.allowFlight,
            hotbarEnabled       = false   // wordt pas true als we echt geven
        )
        activeModes[player.uniqueId] = state

        player.gameMode    = GameMode.CREATIVE
        player.allowFlight = true

        if (targetWorld != null) {
            player.teleport(targetWorld.spawnLocation)
            // Hotbar + berichten komen via onPlayerArrivedInEditWorld()
            return
        }

        if (!deferHotbar) {
            // Geen world-switch verwacht → hotbar direct geven
            hotbarManager.giveEditorHotbar(player)
            state.hotbarEnabled = true
            sendEnableMessages(player, mapName)
        }
        // Bij deferHotbar=true wacht EditorSessionManager op finishSessionStart
        // die vervolgens onPlayerArrivedInEditWorld() triggert via PlayerChangedWorldEvent

        debugLog("Edit mode enabled for ${player.name}" + (mapName?.let { " (map: $it)" } ?: ""))
    }

    /**
     * ### Fix 1
     * Aanroepen vanuit [org.Kloppie74.locoPillars.editor.listener.EditorModeListener]
     * bij PlayerChangedWorldEvent, wanneer de speler in de edit world is geland.
     *
     * Geeft de hotbar en welkomstberichten pas ná de world-switch zodat
     * Minecraft de inventory niet meer kan resetten.
     */
    fun onPlayerArrivedInEditWorld(player: Player) {
        val state = activeModes[player.uniqueId] ?: return
        if (state.hotbarEnabled) return   // al gegeven via het directe pad

        hotbarManager.giveEditorHotbar(player)
        state.hotbarEnabled = true
        sendEnableMessages(player, state.activeMapName)
        debugLog("Hotbar given to ${player.name} after world switch.")
    }

    /**
     * Deactiveer edit mode en herstel de originele hotbar.
     * Geeft `false` als edit mode niet actief was.
     */
    fun disable(player: Player): Boolean {
        val state = activeModes.remove(player.uniqueId) ?: run {
            player.sendMessage(MSG.chatColors("&cEdit mode is not active!"))
            return false
        }

        hotbarManager.removeEditorHotbar(player, state.hotbarSlotSnapshot)
        player.gameMode    = state.originalGameMode
        player.allowFlight = state.originalAllowFlight

        player.sendMessage(MSG.chatColors("&c✦ Edit Mode &cdeactivated."))
        player.sendMessage(MSG.chatColors("&7Your original hotbar has been restored."))

        debugLog("Edit mode disabled for ${player.name} na ${state.activeDurationSeconds()}s")
        return true
    }

    // -------------------------------------------------------------------------
    // State queries
    // -------------------------------------------------------------------------

    fun isActive(player: Player): Boolean        = activeModes.containsKey(player.uniqueId)
    fun getState(player: Player): EditModeState? = activeModes[player.uniqueId]

    fun setActiveMap(player: Player, mapName: String?) {
        activeModes[player.uniqueId]?.activeMapName = mapName
    }

    fun getActiveEditors(): Map<UUID, EditModeState> = activeModes.toMap()

    fun setHotbarEnabled(player: Player, enabled: Boolean): Boolean {
        val state = activeModes[player.uniqueId] ?: return false
        if (enabled == state.hotbarEnabled) return true

        if (enabled) {
            hotbarManager.giveEditorHotbar(player)
            player.sendMessage(MSG.chatColors("&aEditor hotbar enabled."))
            player.sendMessage(MSG.chatColors(
                "&7Tip: Use slot 8 (Barrier) or &e/editor toggle &7to leave edit mode."))
        } else {
            for (slot in 0..8) player.inventory.setItem(slot, null)
            player.updateInventory()
            player.sendMessage(MSG.chatColors("&eEditor hotbar hidden."))
            player.sendMessage(MSG.chatColors("&7Use &a/editor hotbar on &7to show it again."))
        }

        state.hotbarEnabled = enabled
        return true
    }

    fun toggleHotbar(player: Player): Boolean {
        val state = activeModes[player.uniqueId] ?: return false
        return setHotbarEnabled(player, !state.hotbarEnabled)
    }

    fun isHotbarEnabled(player: Player): Boolean =
        activeModes[player.uniqueId]?.hotbarEnabled == true

    // -------------------------------------------------------------------------
    // Cleanup
    // -------------------------------------------------------------------------

    fun cleanupAll() {
        stopCreativeModeEnforcer()
        activeModes.keys.toList().forEach { uuid ->
            val player = plugin.server.getPlayer(uuid)
            if (player != null && player.isOnline) disable(player)
            else activeModes.remove(uuid)
        }
        debugLog("All edit mode sessions cleaned up")
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun sendEnableMessages(player: Player, mapName: String?) {
        player.sendMessage(MSG.chatColors("&a✦ &lEdit Mode Activated"))
        player.sendMessage(MSG.chatColors(
            "&7You are now in Creative mode and your editor hotbar is ready."))
        if (mapName != null) {
            player.sendMessage(MSG.chatColors("&7Selected map: &e$mapName"))
            player.sendMessage(MSG.chatColors(
                "&7Tip: use &e/editor settings &7to configure map parameters."))
        } else {
            player.sendMessage(MSG.chatColors("&7Step 1: &e/editor map <name> &7— select a map"))
            player.sendMessage(MSG.chatColors("&7Step 2: &e/editor edit     &7— open the edit world"))
            player.sendMessage(MSG.chatColors("&7Step 3: &e/editor settings  &7— configure parameters"))
        }
        player.sendMessage(MSG.chatColors(
            "&7Use &e/editor help &7for all commands, or &e/editor toggle &7to exit."))
    }

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) {
            plugin.logger.info("[AdminEditorManager] $message")
        }
    }
}