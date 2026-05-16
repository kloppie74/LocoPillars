package org.Kloppie74.locoPillars.editor.listener

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.editor.AdminEditorManager
import org.Kloppie74.locoPillars.editor.EditorFileValidator
import org.Kloppie74.locoPillars.editor.EditorHotbarManager
import org.Kloppie74.locoPillars.editor.EditorSettingsGui
import org.Kloppie74.locoPillars.editor.SpawnPointEditor
import org.Kloppie74.locoPillars.game.map.EditorSessionManager
import org.Kloppie74.locoPillars.utils.MSG
import java.util.UUID

/**
 * EditorInteractionListener
 *
 * Handles right-click interactions for the editor hotbar items and
 * the built-in Position Tool (golden axe).  The Position Tool was
 * previously a standalone `PositionTool` object in the old
 * `commands/Editor` package — its logic is now integrated here so
 * there is a single, unified listener for all editor interactions.
 *
 * Hotbar interactions:
 * | Slot | Item               | Action                                      |
 * |------|--------------------|---------------------------------------------|
 * | 0    | Position Tool      | L-click: pos 1 / R-click: pos 2            |
 * | 1    | Spawn Setter       | Records player location as spawn point      |
 * | 2    | Schematic Tool     | Shows /editor session commands              |
 * | 3    | Validation Tool    | Runs [EditorFileValidator] report           |
 * | 4    | Config Editor      | Opens [EditorSettingsGui]                   |
 * | 5    | Border Visualizer  | Informational (no particle impl here)       |
 * | 8    | Exit Edit Mode     | Calls AdminEditorManager.disable()          |
 *
 * @param plugin Plugin instance for scheduling and logging.
 * @param adminEditorManager Tracks active edit-mode sessions.
 * @param hotbarManager Provides item-name constants for identification.
 * @param spawnPointEditor Handles spawn point persistence.
 * @param fileValidator Provides per-map validation reports.
 * @param editorSessionManager Used for schematic operations.
 * @param editorSettingsGui GUI for adjusting save parameters.
 */
class EditorInteractionListener(
    private val plugin: LocoPillars,
    private val adminEditorManager: AdminEditorManager,
    private val hotbarManager: EditorHotbarManager,
    private val spawnPointEditor: SpawnPointEditor,
    private val fileValidator: EditorFileValidator,
    private val editorSessionManager: EditorSessionManager,
    private val editorSettingsGui: EditorSettingsGui
) : Listener {

    // Per-player position selections (from the old PositionTool)
    private data class PositionData(var pos1: Location? = null, var pos2: Location? = null)
    private val positions = mutableMapOf<UUID, PositionData>()

    // -------------------------------------------------------------------------
    // Block / air interactions with hotbar tools
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (!adminEditorManager.isActive(player)) return

        val item = event.item ?: return
        if (!hotbarManager.isEditorItem(item)) return

        when (item.itemMeta?.displayName) {
            EditorHotbarManager.ITEM_POSITION_TOOL -> handlePositionTool(event, player)
            EditorHotbarManager.ITEM_SPAWN_SETTER  -> handleSpawnSetter(event, player)
            EditorHotbarManager.ITEM_VALIDATE_TOOL -> handleValidateTool(event, player)
            EditorHotbarManager.ITEM_CONFIG_EDITOR -> handleConfigEditor(event, player)
            EditorHotbarManager.ITEM_EXIT_EDITOR   -> handleExitEditor(event, player)
            EditorHotbarManager.ITEM_SCHEM_TOOL    -> handleSchemTool(event, player)
        }
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

    /** Clean up position data when the player disconnects. */
    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        positions.remove(event.player.uniqueId)
    }

    // -------------------------------------------------------------------------
    // Position Tool (integrated from old PositionTool object)
    // -------------------------------------------------------------------------

    private fun handlePositionTool(event: PlayerInteractEvent, player: Player) {
        event.isCancelled = true
        val block = event.clickedBlock ?: return
        val pos   = block.location
        val data  = positions.getOrPut(player.uniqueId) { PositionData() }

        when (event.action) {
            Action.LEFT_CLICK_BLOCK -> {
                data.pos1 = pos
                player.sendMessage(MSG.chatColors("&aPosition 1 set: &f${pos.blockX}, ${pos.blockY}, ${pos.blockZ}"))
                if (data.pos2 != null) {
                    player.sendMessage(MSG.chatColors("&7Selection: &e${calcVolume(data.pos1!!, data.pos2!!)} &7blocks"))
                }
            }
            Action.RIGHT_CLICK_BLOCK -> {
                if (player.isSneaking) {
                    data.pos1 = null
                    data.pos2 = null
                    player.sendMessage(MSG.chatColors("&ePositions cleared."))
                    return
                }
                data.pos2 = pos
                player.sendMessage(MSG.chatColors("&aPosition 2 set: &f${pos.blockX}, ${pos.blockY}, ${pos.blockZ}"))
                if (data.pos1 != null) {
                    val vol = calcVolume(data.pos1!!, data.pos2!!)
                    player.sendMessage(MSG.chatColors("&7Selection: &e$vol &7blocks"))
                    player.sendMessage(MSG.chatColors("&7Use &e/editor save &7after editing to save your schematic."))
                }
            }
            else -> {}
        }
    }

    /** Returns the position selections for [player], or null if not set. */
    fun getPos1(player: Player): Location? = positions[player.uniqueId]?.pos1
    fun getPos2(player: Player): Location? = positions[player.uniqueId]?.pos2

    private fun calcVolume(a: Location, b: Location): Int {
        val dx = Math.abs(a.blockX - b.blockX) + 1
        val dy = Math.abs(a.blockY - b.blockY) + 1
        val dz = Math.abs(a.blockZ - b.blockZ) + 1
        return dx * dy * dz
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

        val teamKey   = if (player.isSneaking) "Team1" else "Team1"
        val playerKey = "Player1"

        val location = event.clickedBlock?.location?.add(0.5, 1.0, 0.5)?.also {
            it.yaw   = player.location.yaw
            it.pitch = player.location.pitch
        } ?: player.location

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
            val reports = fileValidator.validateAll()
            player.sendMessage(MSG.chatColors("&6=== Validation Report (all maps) ==="))
            if (reports.isEmpty()) {
                player.sendMessage(MSG.chatColors("&7No maps found in maps folder."))
                return
            }
            reports.values.forEach { r -> player.sendMessage(MSG.chatColors(r.summary())) }
        } else {
            sendValidationReport(player, fileValidator.validate(mapName))
        }
    }

    private fun handleConfigEditor(event: PlayerInteractEvent, player: Player) {
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) return
        event.isCancelled = true

        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.chatColors("&cStart a map editing session first: &e/editor edit"))
            return
        }

        org.bukkit.Bukkit.getScheduler().runTask(plugin, Runnable { editorSettingsGui.open(player) })
    }

    private fun handleSchemTool(event: PlayerInteractEvent, player: Player) {
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) return
        event.isCancelled = true

        if (editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.chatColors("&6=== Schematic Tool ==="))
            player.sendMessage(MSG.chatColors("&e/editor save        &7- Save & close session"))
            player.sendMessage(MSG.chatColors("&e/editor cancel      &7- Cancel (no save)"))
            player.sendMessage(MSG.chatColors("&e/editor info        &7- Show session info"))
            player.sendMessage(MSG.chatColors("&e/editor resize <r>  &7- Adjust save radius"))
        } else {
            val mapName = adminEditorManager.getState(player)?.activeMapName
            if (mapName != null) {
                player.sendMessage(MSG.chatColors("&eOpen a full edit session with:"))
                player.sendMessage(MSG.chatColors("&e/editor edit $mapName"))
            } else {
                player.sendMessage(MSG.chatColors("&cNo map session active. Use &e/editor map <name> &cthen /editor edit."))
            }
        }
    }

    private fun handleExitEditor(event: PlayerInteractEvent, player: Player) {
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.RIGHT_CLICK_AIR) return
        event.isCancelled = true
        if (editorSessionManager.hasSession(player)) {
            editorSessionManager.cancelSession(player)
        }
        adminEditorManager.disable(player)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
