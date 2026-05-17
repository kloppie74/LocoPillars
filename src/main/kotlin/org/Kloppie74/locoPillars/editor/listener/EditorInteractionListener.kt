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
            player.sendMessage(MSG.chatColors("&6=== Validation Report — All Maps (${reports.size}) ==="))
            if (reports.isEmpty()) { player.sendMessage(MSG.chatColors("&7No maps found.")); return }
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
            player.sendMessage(MSG.chatColors("&cNo active map! Use &e/editor map <name> &cfirst."))
            return
        }

        val session   = editorSessionManager.getSession(player)
        val maxTeams  = session?.getMaxTeams()       ?: 8
        val perTeam   = session?.getPlayersPerTeam() ?: 1

        // Shift + right → cycle team, reset slot
        if (player.isSneaking) {
            val current = spawnSetterTeam.getOrDefault(player.uniqueId, 1)
            val next    = if (current >= maxTeams) 1 else current + 1
            spawnSetterTeam[player.uniqueId] = next
            spawnSetterSlot[player.uniqueId] = 1
            player.sendMessage(MSG.chatColors(
                "&eSpawn Setter &8→ &bTeam$next &8/ &bPlayer1"))
            player.sendMessage(MSG.chatColors(
                "&8($maxTeams teams × $perTeam per team)  Right-click a block to place."))
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
            player.sendMessage(MSG.chatColors("&c✗ Failed to save spawn point. Check console."))
            return
        }

        player.sendMessage(MSG.chatColors(
            "&a✓ &e$teamKey&7/&e$playerKey &7@ &b(${location.blockX}, ${location.blockY}, ${location.blockZ})"))

        // Auto-advance slot → team → done
        val nextSlot = slotNum + 1
        if (nextSlot > perTeam) {
            val nextTeam = teamNum + 1
            if (nextTeam > maxTeams) {
                spawnSetterTeam[player.uniqueId] = 1
                spawnSetterSlot[player.uniqueId] = 1
                player.sendMessage(MSG.chatColors("&a&l✦ All spawn points placed! &8($maxTeams × $perTeam)"))
            } else {
                spawnSetterTeam[player.uniqueId] = nextTeam
                spawnSetterSlot[player.uniqueId] = 1
                player.sendMessage(MSG.chatColors(
                    "&7Team $teamNum done! Next &8→ &eTeam$nextTeam/Player1 &8(Shift+click to skip)"))
            }
        } else {
            spawnSetterSlot[player.uniqueId] = nextSlot
            player.sendMessage(MSG.chatColors(
                "&7Next &8→ &e$teamKey/Player$nextSlot &8| Shift+click to switch team"))
        }
    }

    // -------------------------------------------------------------------------
    // Slot 2 — Lobby Tool
    // -------------------------------------------------------------------------

    private fun handleLobbyTool(event: PlayerInteractEvent, player: Player) {
        event.isCancelled = true

        val mapName = adminEditorManager.getState(player)?.activeMapName ?: run {
            player.sendMessage(MSG.chatColors("&cNo active map! Use &e/editor map <name> &cfirst."))
            return
        }

        val block = event.clickedBlock

        when {
            // Shift + left → clear lobby area
            player.isSneaking && isLeftClick(event) -> {
                lobbyAreaLoc1.remove(player.uniqueId)
                if (lobbyLocationEditor.clearLobbyArea(mapName))
                    player.sendMessage(MSG.chatColors("&eSpawn-Area cleared for &f$mapName&e."))
                else
                    player.sendMessage(MSG.chatColors("&c✗ Failed to clear Spawn-Area."))
            }

            // Shift + right + block → Loc2
            player.isSneaking && event.action == Action.RIGHT_CLICK_BLOCK && block != null -> {
                val loc1 = lobbyAreaLoc1[player.uniqueId] ?: run {
                    player.sendMessage(MSG.chatColors("&cSet Loc1 first: &eleft-click &ca block."))
                    return
                }
                val loc2 = block.location
                if (lobbyLocationEditor.setLobbyArea(mapName, loc1, loc2)) {
                    lobbyAreaLoc1.remove(player.uniqueId)
                    player.sendMessage(MSG.chatColors("&a✓ Spawn-Area saved:"))
                    player.sendMessage(MSG.chatColors(
                        "  &7Loc1 &8→ &b(${loc1.blockX}, ${loc1.blockY}, ${loc1.blockZ})"))
                    player.sendMessage(MSG.chatColors(
                        "  &7Loc2 &8→ &b(${loc2.blockX}, ${loc2.blockY}, ${loc2.blockZ})"))
                } else {
                    player.sendMessage(MSG.chatColors("&c✗ Failed to save Spawn-Area."))
                }
            }

            // Right + block (no sneak) → set lobby SpawnPoint
            !player.isSneaking && event.action == Action.RIGHT_CLICK_BLOCK && block != null -> {
                val loc = block.location.add(0.5, 1.0, 0.5).also {
                    it.yaw   = player.location.yaw
                    it.pitch = player.location.pitch
                }
                if (lobbyLocationEditor.setLobbySpawn(mapName, loc))
                    player.sendMessage(MSG.chatColors(
                        "&a✓ Lobby SpawnPoint &8→ &b(${loc.blockX}, ${loc.blockY}, ${loc.blockZ}) " +
                                "&7yaw:&f${"%.1f".format(loc.yaw)}"))
                else
                    player.sendMessage(MSG.chatColors("&c✗ Failed to save SpawnPoint."))
            }

            // Left + block (no sneak) → set Loc1
            !player.isSneaking && event.action == Action.LEFT_CLICK_BLOCK && block != null -> {
                lobbyAreaLoc1[player.uniqueId] = block.location
                player.sendMessage(MSG.chatColors(
                    "&aSpawn-Area Loc1 &8→ &b(${block.x.toInt()}, ${block.y.toInt()}, ${block.z.toInt()})"))
                player.sendMessage(MSG.chatColors(
                    "&7Now &eShift + right-click &7another block to set Loc2."))
            }

            // Right-click air → show status
            !player.isSneaking && event.action == Action.RIGHT_CLICK_AIR -> {
                lobbyLocationEditor.showLobbyStatus(player, mapName)
                lobbyAreaLoc1[player.uniqueId]?.let { pending ->
                    player.sendMessage(MSG.chatColors(
                        "&7Pending Loc1: &b(${pending.blockX}, ${pending.blockY}, ${pending.blockZ})"))
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Slot 3 — Settings GUI
    // -------------------------------------------------------------------------

    private fun handleSettingsGui(event: PlayerInteractEvent, player: Player) {
        if (!isRightClick(event)) return
        event.isCancelled = true

        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.chatColors("&cStart a session first: &e/editor edit"))
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
            player.sendMessage(MSG.chatColors("&cNo active session to save!"))
            return
        }
        player.sendMessage(MSG.chatColors("&eSaving... please wait."))
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
            player.sendMessage(MSG.chatColors("&cNo active session to cancel!"))
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
            player.sendMessage(MSG.chatColors("&cNo active session!"))
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
            player.sendMessage(MSG.chatColors("&cNo active session!"))
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

        player.sendMessage(MSG.chatColors("&6✦ &eEditor hotbar hidden — original items restored."))
        player.sendMessage(MSG.chatColors("&8› Use &e/editor hotbar on &8to get editor tools back."))
        player.sendMessage(MSG.chatColors("&8› Use &c/editor toggle &8to fully exit edit mode."))
    }

    // -------------------------------------------------------------------------
    // Drop / inventory-move protection
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    fun onItemDrop(event: PlayerDropItemEvent) {
        if (!adminEditorManager.isActive(event.player)) return
        if (hotbarManager.isEditorItem(event.itemDrop.itemStack)) {
            event.isCancelled = true
            event.player.sendActionBar(MSG.chatColors("&cEditor tools cannot be dropped!"))
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
            player.sendActionBar(MSG.chatColors("&cEditor tools cannot be moved!"))
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
        player.sendMessage(MSG.chatColors("&6=== Validation: ${report.mapName} ==="))
        if (report.isValid) {
            player.sendMessage(MSG.chatColors("&a✓ Map is valid"))
        } else {
            player.sendMessage(MSG.chatColors("&c✗ Errors (${report.errors.size}):"))
            report.errors.forEach { player.sendMessage(MSG.chatColors("  &c- $it")) }
        }
        if (report.warnings.isNotEmpty()) {
            player.sendMessage(MSG.chatColors("&eWarnings (${report.warnings.size}):"))
            report.warnings.forEach { player.sendMessage(MSG.chatColors("  &e- $it")) }
        }
        if (plugin.configManager.debugMode && report.info.isNotEmpty()) {
            report.info.forEach { player.sendMessage(MSG.chatColors("  &7- $it")) }
        }
    }
}