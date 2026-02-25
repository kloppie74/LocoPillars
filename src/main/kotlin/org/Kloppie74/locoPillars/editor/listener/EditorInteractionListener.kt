package org.Kloppie74.locoPillars.editor.listener

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.commands.admin.MapEditorMenu
import org.Kloppie74.locoPillars.editor.AdminEditorManager
import org.Kloppie74.locoPillars.editor.EditorFileValidator
import org.Kloppie74.locoPillars.editor.EditorHotbarManager
import org.Kloppie74.locoPillars.editor.SpawnPointEditor
import org.Kloppie74.locoPillars.game.map.EditorSessionManager
import org.Kloppie74.locoPillars.utils.MSG

/**
 * EditorInteractionListener
 *
 * Handles right-click and inventory-click interactions for the editor hotbar
 * items given to an admin while edit mode is active.
 *
 * Bound item interactions:
 * | Slot | Item               | Action                                      |
 * |------|--------------------|---------------------------------------------|
 * | 0    | Position Tool      | Delegated to [PositionTool] (existing)      |
 * | 1    | Spawn Setter       | Records player location as spawn point      |
 * | 2    | Schematic Tool     | Delegates to session save/load menu         |
 * | 3    | Validation Tool    | Runs [EditorFileValidator] report           |
 * | 4    | Config Editor      | Opens [MapEditorMenu]                       |
 * | 5    | Border Visualizer  | Toggles border particle display             |
 * | 8    | Exit Edit Mode     | Calls AdminEditorManager.disable()          |
 *
 * @param plugin Plugin instance for scheduling and logging.
 * @param adminEditorManager Tracks active edit-mode sessions.
 * @param hotbarManager Provides item-name constants for identification.
 * @param spawnPointEditor Handles spawn point persistence.
 * @param fileValidator Provides per-map validation reports.
 * @param editorSessionManager Used for schematic operations.
 * @param mapEditorMenu GUI for map settings.
 */
class EditorInteractionListener(
    private val plugin: LocoPillars,
    private val adminEditorManager: AdminEditorManager,
    private val hotbarManager: EditorHotbarManager,
    private val spawnPointEditor: SpawnPointEditor,
    private val fileValidator: EditorFileValidator,
    private val editorSessionManager: EditorSessionManager,
    private val mapEditorMenu: MapEditorMenu
) : Listener {

    // -------------------------------------------------------------------------
    // Block / air interactions with hotbar tools
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!adminEditorManager.isActive(player)) return

        val item = event.item ?: return
        if (!hotbarManager.isEditorItem(item)) return

        // Position tool (slot 0) is handled by the existing PositionTool listener.
        // We only intercept the other tools here.
        when (item.itemMeta?.displayName) {
            EditorHotbarManager.ITEM_SPAWN_SETTER -> handleSpawnSetter(event, player)
            EditorHotbarManager.ITEM_VALIDATE_TOOL -> handleValidateTool(event, player)
            EditorHotbarManager.ITEM_CONFIG_EDITOR -> handleConfigEditor(event, player)
            EditorHotbarManager.ITEM_EXIT_EDITOR   -> handleExitEditor(event, player)
            EditorHotbarManager.ITEM_SCHEM_TOOL    -> handleSchemTool(event, player)
        }
        // ITEM_BORDER_VIZ and ITEM_POSITION_TOOL have no block-interaction handler here.
    }

    // -------------------------------------------------------------------------
    // Prevent dropping editor items
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    fun onItemDrop(event: PlayerDropItemEvent) {
        if (!adminEditorManager.isActive(event.player)) return
        if (hotbarManager.isEditorItem(event.itemDrop.itemStack)) {
            event.isCancelled = true
            event.player.sendMessage(MSG.chatColors("&cEditor tools cannot be dropped."))
        }
    }

    // -------------------------------------------------------------------------
    // Individual tool handlers
    // -------------------------------------------------------------------------

    private fun handleSpawnSetter(event: PlayerInteractEvent, player: Player) {
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) return
        event.isCancelled = true

        val mapName = adminEditorManager.getState(player)?.activeMapName ?: run {
            player.sendMessage(MSG.chatColors("&cNo active map selected. Use &e/editor map <name> &cfirst."))
            return
        }

        // Parse team/slot from sneaking vs normal click
        val teamKey   = if (player.isSneaking) promptLastTeamInput(player) else "Team1"
        val playerKey = "Player1"

        val clickedBlock = event.clickedBlock
        val location = if (clickedBlock != null) {
            clickedBlock.location.add(0.5, 1.0, 0.5).also {
                it.yaw   = player.location.yaw
                it.pitch = player.location.pitch
            }
        } else {
            player.location
        }

        val success = spawnPointEditor.setSpawnPoint(mapName, teamKey, playerKey, location)
        if (success) {
            player.sendMessage(MSG.chatColors("&a✓ Spawn point set: &e$teamKey/$playerKey &7@ " +
                "&b(${location.blockX}, ${location.blockY}, ${location.blockZ})"))
            player.sendMessage(MSG.chatColors("&7Tip: use &e/editor spawn set <team> <slot> &7for full control."))
        } else {
            player.sendMessage(MSG.chatColors("&c✗ Failed to save spawn point. Check console for details."))
        }
    }

    private fun handleValidateTool(event: PlayerInteractEvent, player: Player) {
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) return
        event.isCancelled = true

        val mapName = adminEditorManager.getState(player)?.activeMapName
        if (mapName == null) {
            // Validate all maps
            val reports = fileValidator.validateAll()
            player.sendMessage(MSG.chatColors("&6=== Validation Report (all maps) ==="))
            if (reports.isEmpty()) {
                player.sendMessage(MSG.chatColors("&7No maps found in maps folder."))
                return
            }
            reports.values.forEach { r ->
                player.sendMessage(MSG.chatColors(r.summary()))
            }
        } else {
            // Validate the active map
            val report = fileValidator.validate(mapName)
            sendValidationReport(player, report)
        }
    }

    private fun handleConfigEditor(event: PlayerInteractEvent, player: Player) {
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) return
        event.isCancelled = true

        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.chatColors("&cStart a map editing session first: &e/pillars admin map edit <name>"))
            return
        }

        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable { mapEditorMenu.open(player) })
    }

    private fun handleSchemTool(event: PlayerInteractEvent, player: Player) {
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) return
        event.isCancelled = true

        if (editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.chatColors("&6=== Schematic Tool ==="))
            player.sendMessage(MSG.chatColors("&e/pillars admin map save    &7- Save current session"))
            player.sendMessage(MSG.chatColors("&e/pillars admin map info    &7- Show session info"))
            player.sendMessage(MSG.chatColors("&e/pillars admin map resize <r> &7- Adjust save radius"))
        } else {
            val mapName = adminEditorManager.getState(player)?.activeMapName
            if (mapName != null) {
                player.sendMessage(MSG.chatColors("&eOpen a full edit session with:"))
                player.sendMessage(MSG.chatColors("&e/pillars admin map edit $mapName"))
            } else {
                player.sendMessage(MSG.chatColors("&cNo map session active. Use &e/editor map <name> &cto select one."))
            }
        }
    }

    private fun handleExitEditor(event: PlayerInteractEvent, player: Player) {
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) return
        event.isCancelled = true

        adminEditorManager.disable(player)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Very simple fallback that returns a default team key.
     * A future iteration could open a sign or chat prompt.
     */
    private fun promptLastTeamInput(player: Player): String = "Team1"

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
            player.sendMessage(MSG.chatColors("&7Info:"))
            report.info.forEach { player.sendMessage(MSG.chatColors("  &7- $it")) }
        }
    }
}
