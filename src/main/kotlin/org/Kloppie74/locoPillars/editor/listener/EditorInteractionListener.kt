package org.Kloppie74.locoPillars.editor.listener

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerToggleSneakEvent
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.editor.AdminEditorManager
import org.Kloppie74.locoPillars.editor.EditorFileValidator
import org.Kloppie74.locoPillars.editor.EditorHotbarManager
import org.Kloppie74.locoPillars.editor.EditorSettingsGui
import org.Kloppie74.locoPillars.editor.LobbyLocationEditor
import org.Kloppie74.locoPillars.editor.SpawnPointEditor
import org.Kloppie74.locoPillars.game.map.EditorSessionManager
import org.Kloppie74.locoPillars.utils.MSG
import java.util.UUID

/**
 * EditorInteractionListener
 *
 * Handles all hotbar item interactions for the redesigned editor hotbar:
 *
 * | Slot | Item             | Action                                        |
 * |------|------------------|-----------------------------------------------|
 * | 0    | Validation Tool  | Right-click → validate active / all maps      |
 * | 1    | Spawn Setter     | Right-click block → place spawn point         |
 * |      |                  | Shift + right-click → cycle team              |
 * | 2    | Lobby Tool       | Right-click block → lobby spawn               |
 * |      |                  | Left-click block → Loc1                       |
 * |      |                  | Shift + right-click block → Loc2              |
 * |      |                  | Shift + left-click → clear area               |
 * | 3    | Settings GUI     | Right-click → open full settings GUI          |
 * | 4    | Save & Close     | Right-click → /editor save                    |
 * | 5    | Cancel Session   | Right-click → /editor cancel                  |
 * | 6    | Refresh Holograms| Right-click → refresh  /  Left-click → toggle|
 * | 7    | Session Info     | Right-click → print info to chat              |
 * | 8    | Switch Hotbar    | Right-click → swap to original hotbar         |
 */
class EditorInteractionListener(
    private val plugin: LocoPillars,
    private val adminEditorManager: AdminEditorManager,
    private val hotbarManager: EditorHotbarManager,
    private val spawnPointEditor: SpawnPointEditor,
    private val fileValidator: EditorFileValidator,
    private val editorSessionManager: EditorSessionManager,
    private val editorSettingsGui: EditorSettingsGui,
    private val lobbyLocationEditor: LobbyLocationEditor
) : Listener {

    // -------------------------------------------------------------------------
    // Per-player state
    // -------------------------------------------------------------------------

    private data class PositionData(var pos1: Location? = null, var pos2: Location? = null)
    private val positions      = mutableMapOf<UUID, PositionData>()
    private val spawnSetterTeam = mutableMapOf<UUID, Int>()
    private val spawnSetterSlot = mutableMapOf<UUID, Int>()
    private val lobbyAreaLoc1  = mutableMapOf<UUID, Location>()

    // -------------------------------------------------------------------------
    // Main dispatcher
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!adminEditorManager.isActive(player)) return

        val item = event.item ?: return
        if (!hotbarManager.isEditorItem(item)) return

        when (item.itemMeta?.displayName) {
            EditorHotbarManager.ITEM_VALIDATE_TOOL  -> handleValidate(event, player)
            EditorHotbarManager.ITEM_SPAWN_SETTER   -> handleSpawnSetter(event, player)
            EditorHotbarManager.ITEM_LOBBY_TOOL     -> handleLobbyTool(event, player)
            EditorHotbarManager.ITEM_LOBBY_DISABLED -> handleLobbyToggleEnable(event, player)
            EditorHotbarManager.ITEM_CONFIG_EDITOR  -> handleSettingsGui(event, player)
            EditorHotbarManager.ITEM_SAVE_CLOSE     -> handleSaveClose(event, player)
            EditorHotbarManager.ITEM_CANCEL_SESSION -> handleCancelSession(event, player)
            EditorHotbarManager.ITEM_HOLO_REFRESH   -> handleHolograms(event, player)
            EditorHotbarManager.ITEM_SESSION_INFO   -> handleSessionInfo(event, player)
            EditorHotbarManager.ITEM_SWITCH_HOTBAR  -> handleSwitchHotbar(event, player)
        }
    }

    // -------------------------------------------------------------------------
    // Slot 0 — Validation Tool
    // -------------------------------------------------------------------------

    private fun handleValidate(event: PlayerInteractEvent, player: Player) {
        if (!isRightClick(event)) return
        event.isCancelled = true

        val mapName = adminEditorManager.getState(player)?.activeMapName
        if (mapName == null) {
            val reports = fileValidator.validateAll()
            player.sendMessage(MSG.lang("editor.validate.header_all_hotbar", "count" to reports.size.toString()))
            if (reports.isEmpty()) { player.sendMessage(MSG.lang("editor.validate.no_maps")); return }
            reports.values.forEach { r -> player.sendMessage(MSG.chatColors(r.summary())) }
        } else {
            sendValidationReport(player, fileValidator.validate(mapName))
        }
    }

    // -------------------------------------------------------------------------
    // Slot 1 — Spawn Point Setter
    // -------------------------------------------------------------------------

    private fun handleSpawnSetter(event: PlayerInteractEvent, player: Player) {
        if (!isRightClick(event)) return
        event.isCancelled = true

        val mapName = adminEditorManager.getState(player)?.activeMapName ?: run {
            player.sendMessage(MSG.lang("editor.spawn.no_active_map"))
            return
        }

        val session   = editorSessionManager.getSession(player)
        val maxTeams  = session?.getMaxTeams()       ?: 8
        val perTeam   = session?.getPlayersPerTeam() ?: 1

        if (player.isSneaking) {
            val current = spawnSetterTeam.getOrDefault(player.uniqueId, 1)
            val next    = if (current >= maxTeams) 1 else current + 1
            spawnSetterTeam[player.uniqueId] = next
            spawnSetterSlot[player.uniqueId] = 1
            player.sendMessage(MSG.lang("editor.spawn_setter.cycled_team", "team" to next.toString()))
            player.sendMessage(MSG.lang("editor.spawn_setter.cycled_team_info", "max_teams" to maxTeams.toString(), "per_team" to perTeam.toString()))
            return
        }

        val teamNum     = spawnSetterTeam.getOrDefault(player.uniqueId, 1).coerceIn(1, maxTeams)
        val slotNum     = spawnSetterSlot.getOrDefault(player.uniqueId, 1).coerceIn(1, perTeam)
        spawnSetterTeam[player.uniqueId] = teamNum
        spawnSetterSlot[player.uniqueId] = slotNum

        val teamKey   = "Team$teamNum"
        val playerKey = "Player$slotNum"
        val location  = (event.clickedBlock?.location?.add(0.5, 1.0, 0.5) ?: player.location).also {
            it.yaw   = player.location.yaw
            it.pitch = player.location.pitch
        }

        if (!spawnPointEditor.setSpawnPoint(mapName, teamKey, playerKey, location)) {
            player.sendMessage(MSG.lang("editor.spawn_setter.place_failed"))
            return
        }

        player.sendMessage(MSG.lang("editor.spawn_setter.placed",
            "team" to teamKey, "slot" to playerKey,
            "x" to location.blockX.toString(), "y" to location.blockY.toString(), "z" to location.blockZ.toString()))

        val nextSlot = slotNum + 1
        if (nextSlot > perTeam) {
            val nextTeam = teamNum + 1
            if (nextTeam > maxTeams) {
                spawnSetterTeam[player.uniqueId] = 1
                spawnSetterSlot[player.uniqueId] = 1
                player.sendMessage(MSG.lang("editor.spawn_setter.all_done", "teams" to maxTeams.toString(), "per_team" to perTeam.toString()))
            } else {
                spawnSetterTeam[player.uniqueId] = nextTeam
                spawnSetterSlot[player.uniqueId] = 1
                player.sendMessage(MSG.lang("editor.spawn_setter.team_done", "team" to teamNum.toString(), "next_team" to nextTeam.toString()))
            }
        } else {
            spawnSetterSlot[player.uniqueId] = nextSlot
            player.sendMessage(MSG.lang("editor.spawn_setter.next_slot", "team" to teamKey, "slot" to nextSlot.toString()))
        }
    }

    // -------------------------------------------------------------------------
    // Slot 2 — Lobby Tool
    // -------------------------------------------------------------------------

    private fun handleLobbyTool(event: PlayerInteractEvent, player: Player) {
        event.isCancelled = true

        val mapName = adminEditorManager.getState(player)?.activeMapName ?: run {
            player.sendMessage(MSG.lang("editor.lobby.no_active_map_short"))
            return
        }

        val block = event.clickedBlock

        when {
            player.isSneaking && event.action == Action.LEFT_CLICK_AIR -> {
                if (lobbyLocationEditor.setLobbyEnabled(mapName, false)) {
                    hotbarManager.updateLobbySlot(player, false)
                    player.sendMessage(MSG.chatColors("&c⚠ Lobby DISABLED for &f$mapName&c."))
                    player.sendMessage(MSG.chatColors("&7Players will now spawn directly in their cage on join."))
                    player.sendMessage(MSG.chatColors("&7Click the &cred barrier &7in slot 2 to re-enable the lobby."))
                } else {
                    player.sendMessage(MSG.chatColors("&cFailed to save lobby state. Is the map loaded?"))
                }
            }

            player.isSneaking && isLeftClick(event) -> {
                lobbyAreaLoc1.remove(player.uniqueId)
                if (lobbyLocationEditor.clearLobbyArea(mapName))
                    player.sendMessage(MSG.lang("editor.lobby.area_cleared", "name" to mapName))
                else
                    player.sendMessage(MSG.lang("editor.lobby.area_clear_failed"))
            }

            player.isSneaking && event.action == Action.RIGHT_CLICK_BLOCK && block != null -> {
                val loc1 = lobbyAreaLoc1[player.uniqueId] ?: run {
                    player.sendMessage(MSG.lang("editor.lobby.loc1_required"))
                    return
                }
                val loc2 = block.location
                if (lobbyLocationEditor.setLobbyArea(mapName, loc1, loc2)) {
                    lobbyAreaLoc1.remove(player.uniqueId)
                    player.sendMessage(MSG.lang("editor.lobby.area_saved_short"))
                    player.sendMessage(MSG.lang("editor.lobby.area_loc1", "x" to loc1.blockX.toString(), "y" to loc1.blockY.toString(), "z" to loc1.blockZ.toString()))
                    player.sendMessage(MSG.lang("editor.lobby.area_loc2", "x" to loc2.blockX.toString(), "y" to loc2.blockY.toString(), "z" to loc2.blockZ.toString()))
                } else {
                    player.sendMessage(MSG.lang("editor.lobby.area_save_failed"))
                }
            }

            !player.isSneaking && event.action == Action.RIGHT_CLICK_BLOCK && block != null -> {
                val loc = block.location.add(0.5, 1.0, 0.5).also {
                    it.yaw   = player.location.yaw
                    it.pitch = player.location.pitch
                }
                if (lobbyLocationEditor.setLobbySpawn(mapName, loc))
                    player.sendMessage(MSG.lang("editor.lobby.spawn_set_short",
                        "x" to loc.blockX.toString(), "y" to loc.blockY.toString(),
                        "z" to loc.blockZ.toString(), "yaw" to "%.1f".format(loc.yaw)))
                else
                    player.sendMessage(MSG.lang("editor.lobby.spawn_set_short_failed"))
            }

            !player.isSneaking && event.action == Action.LEFT_CLICK_BLOCK && block != null -> {
                lobbyAreaLoc1[player.uniqueId] = block.location
                player.sendMessage(MSG.lang("editor.lobby.loc1_set",
                    "x" to block.x.toInt().toString(), "y" to block.y.toInt().toString(), "z" to block.z.toInt().toString()))
                player.sendMessage(MSG.lang("editor.lobby.loc1_hint"))
            }

            !player.isSneaking && event.action == Action.RIGHT_CLICK_AIR -> {
                lobbyLocationEditor.showLobbyStatus(player, mapName)
                lobbyAreaLoc1[player.uniqueId]?.let { pending ->
                    player.sendMessage(MSG.lang("editor.lobby.pending_loc1",
                        "x" to pending.blockX.toString(), "y" to pending.blockY.toString(), "z" to pending.blockZ.toString()))
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Slot 2 — Lobby Disabled (toggle-enable handler)
    // -------------------------------------------------------------------------

    private fun handleLobbyToggleEnable(event: PlayerInteractEvent, player: Player) {
        event.isCancelled = true
        val mapName = adminEditorManager.getState(player)?.activeMapName ?: run {
            player.sendMessage(MSG.lang("editor.lobby.no_active_map_short"))
            return
        }
        if (lobbyLocationEditor.setLobbyEnabled(mapName, true)) {
            hotbarManager.updateLobbySlot(player, true)
            player.sendMessage(MSG.chatColors("&a✔ Lobby ENABLED for &f$mapName&a."))
            player.sendMessage(MSG.chatColors("&7Players will use the shared waiting area on join."))
            player.sendMessage(MSG.chatColors("&7Configure &eSpawnPoint &7and &eSpawn-Area &7with the Lobby Tool (slot 2)."))
        } else {
            player.sendMessage(MSG.chatColors("&cFailed to save lobby state. Is the map loaded?"))
        }
    }

    // -------------------------------------------------------------------------
    // Slot 3 — Settings GUI
    // -------------------------------------------------------------------------

    private fun handleSettingsGui(event: PlayerInteractEvent, player: Player) {
        if (!isRightClick(event)) return
        event.isCancelled = true

        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.lang("editor.settings.no_session_short"))
            return
        }
        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable { editorSettingsGui.open(player) })
    }

    // -------------------------------------------------------------------------
    // Slot 4 — Save & Close
    // -------------------------------------------------------------------------

    private fun handleSaveClose(event: PlayerInteractEvent, player: Player) {
        if (!isRightClick(event)) return
        event.isCancelled = true

        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.lang("editor.session.no_session_save"))
            return
        }
        player.sendMessage(MSG.lang("editor.session.saving"))
        editorSessionManager.saveAndEndSession(player)
        adminEditorManager.disable(player)
    }

    // -------------------------------------------------------------------------
    // Slot 5 — Cancel Session
    // -------------------------------------------------------------------------

    private fun handleCancelSession(event: PlayerInteractEvent, player: Player) {
        if (!isRightClick(event)) return
        event.isCancelled = true

        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.lang("editor.session.no_session_cancel"))
            return
        }
        editorSessionManager.cancelSession(player)
        adminEditorManager.disable(player)
    }

    // -------------------------------------------------------------------------
    // Slot 6 — Refresh Holograms
    // -------------------------------------------------------------------------

    private fun handleHolograms(event: PlayerInteractEvent, player: Player) {
        event.isCancelled = true
        val session = editorSessionManager.getSession(player) ?: run {
            player.sendMessage(MSG.lang("editor.session.no_session_info"))
            return
        }

        if (event.action == Action.RIGHT_CLICK_BLOCK || event.action == Action.RIGHT_CLICK_AIR) {
            session.refreshHolograms()
        } else if (isLeftClick(event)) {
            session.setHologramsEnabled(!session.isHologramsEnabled())
        }
    }

    // -------------------------------------------------------------------------
    // Slot 7 — Session Info
    // -------------------------------------------------------------------------

    private fun handleSessionInfo(event: PlayerInteractEvent, player: Player) {
        if (!isRightClick(event)) return
        event.isCancelled = true

        val session = editorSessionManager.getSession(player)
        if (session == null) {
            player.sendMessage(MSG.lang("editor.session.no_session_info"))
            return
        }
        session.showInfo()
    }

    // -------------------------------------------------------------------------
    // Slot 8 — Switch Hotbar
    // -------------------------------------------------------------------------

    private fun handleSwitchHotbar(event: PlayerInteractEvent, player: Player) {
        if (!isRightClick(event)) return
        event.isCancelled = true

        val state = adminEditorManager.getState(player) ?: return
        hotbarManager.removeEditorHotbar(player, state.hotbarSlotSnapshot)
        state.hotbarEnabled = false

        player.sendMessage(MSG.lang("editor.hotbar_switch.hidden"))
        player.sendMessage(MSG.lang("editor.hotbar_switch.restore_hint"))
        player.sendMessage(MSG.lang("editor.hotbar_switch.exit_hint"))
    }

    // -------------------------------------------------------------------------
    // Drop / inventory-move protection
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    fun onItemDrop(event: PlayerDropItemEvent) {
        if (!adminEditorManager.isActive(event.player)) return
        if (hotbarManager.isEditorItem(event.itemDrop.itemStack)) {
            event.isCancelled = true
            event.player.sendActionBar(MSG.lang("editor.hotbar_protection.cannot_drop"))
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (!adminEditorManager.isActive(player)) return
        // Allow clicks inside the settings GUI itself
        if (event.view.title.contains("Map Settings", ignoreCase = true)) return
        val clicked = event.currentItem
        val cursor  = event.cursor
        if (hotbarManager.isEditorItem(clicked) || hotbarManager.isEditorItem(cursor)) {
            event.isCancelled = true
            player.sendActionBar(MSG.lang("editor.hotbar_protection.cannot_move"))
        }
    }

    // -------------------------------------------------------------------------
    // Shift → Creative fix
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onPlayerToggleSneak(event: PlayerToggleSneakEvent) {
        val player = event.player
        if (!adminEditorManager.isActive(player)) return
        if (player.gameMode != GameMode.CREATIVE) {
            player.gameMode = GameMode.CREATIVE
        }
    }

    // -------------------------------------------------------------------------
    // Cleanup on quit
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        positions.remove(uuid)
        spawnSetterTeam.remove(uuid)
        spawnSetterSlot.remove(uuid)
        lobbyAreaLoc1.remove(uuid)
    }

    // -------------------------------------------------------------------------
    // Helpers (kept for any external use)
    // -------------------------------------------------------------------------

    fun getPos1(player: Player): Location? = positions[player.uniqueId]?.pos1
    fun getPos2(player: Player): Location? = positions[player.uniqueId]?.pos2

    private fun isRightClick(event: PlayerInteractEvent) =
        event.action == Action.RIGHT_CLICK_BLOCK || event.action == Action.RIGHT_CLICK_AIR

    private fun isLeftClick(event: PlayerInteractEvent) =
        event.action == Action.LEFT_CLICK_BLOCK || event.action == Action.LEFT_CLICK_AIR

    private fun sendValidationReport(player: Player, report: EditorFileValidator.ValidationReport) {
        player.sendMessage(MSG.lang("editor.validate.header_single", "name" to report.mapName))
        if (report.isValid) {
            player.sendMessage(MSG.lang("editor.validate.valid"))
        } else {
            player.sendMessage(MSG.lang("editor.validate.errors_header", "count" to report.errors.size.toString()))
            report.errors.forEach { player.sendMessage(MSG.lang("editor.validate.error_entry", "error" to it)) }
        }
        if (report.warnings.isNotEmpty()) {
            player.sendMessage(MSG.lang("editor.validate.warnings_header", "count" to report.warnings.size.toString()))
            report.warnings.forEach { player.sendMessage(MSG.lang("editor.validate.warning_entry", "warning" to it)) }
        }
        if (plugin.configManager.debugMode && report.info.isNotEmpty()) {
            report.info.forEach { player.sendMessage(MSG.chatColors("  &7- $it")) }
        }
    }
}