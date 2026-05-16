package org.Kloppie74.locoPillars.editor

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.Kloppie74.locoPillars.utils.MSG

/**
 * EditorHotbarManager
 *
 * Responsible for giving and removing the set of editor tools from an admin's
 * hotbar when edit mode is toggled.
 *
 * Hotbar layout (slots 0–8):
 * ```
 * [0] Position Tool (golden axe)      – select region corners
 * [1] Spawn Setter (name tag)         – place / update spawn points
 * [2] Schematic Tool (compass)        – manage schematic operations
 * [3] Validation Tool (book)          – run map file validation
 * [4] Config Editor (writable book)   – open settings.yml editor menu
 * [5] Border Visualizer (blaze rod)   – toggle border particles
 * [6] — empty —
 * [7] — empty —
 * [8] Exit Edit Mode (barrier)        – shortcut to disable edit mode
 * ```
 */
class EditorHotbarManager {

    companion object {
        // NBT display names used to identify our items in event handlers.
        const val ITEM_POSITION_TOOL = "§6Position Selection Tool"
        const val ITEM_SPAWN_SETTER  = "§bSpawn Point Setter"
        const val ITEM_SCHEM_TOOL    = "§dSchematic Tool"
        const val ITEM_VALIDATE_TOOL = "§eValidation Tool"
        const val ITEM_CONFIG_EDITOR = "§aConfig Editor"
        const val ITEM_BORDER_VIZ    = "§3Border Visualizer"
        const val ITEM_EXIT_EDITOR   = "§cExit Edit Mode"
    }

    /**
     * Gives the editor hotbar to [player] in slots 0–8.
     * Only replaces slots 0–6 and slot 8; slots 6–7 are left empty to
     * keep some free space.
     */
    fun giveEditorHotbar(player: Player) {
        val inv = player.inventory
        inv.setItem(0, makeTool(Material.GOLDEN_AXE,   ITEM_POSITION_TOOL,
            listOf("&7Left-click: set position 1",
                   "&7Right-click: set position 2",
                   "&7Shift+right-click: cycle mode")))
        inv.setItem(1, makeTool(Material.NAME_TAG,      ITEM_SPAWN_SETTER,
            listOf("&7Right-click a block to set",
                   "&7the current team spawn point.",
                   "&7Use &e/editor spawn &7to manage.")))
        inv.setItem(2, makeTool(Material.COMPASS,       ITEM_SCHEM_TOOL,
            listOf("&7Save or load the schematic",
                    "&7for the active map.",
                    "&7Use &e/editor edit/save/cancel &7to manage.")))
        inv.setItem(3, makeTool(Material.BOOK,          ITEM_VALIDATE_TOOL,
            listOf("&7Right-click to run a full",
                   "&7validation report on the active map.")))
        inv.setItem(4, makeTool(Material.WRITABLE_BOOK, ITEM_CONFIG_EDITOR,
            listOf("&7Right-click to open the map",
                   "&7settings GUI.")))
        inv.setItem(5, makeTool(Material.BLAZE_ROD,    ITEM_BORDER_VIZ,
            listOf("&7Right-click to toggle border",
                   "&7particle visualisation.")))
        inv.setItem(6, null)
        inv.setItem(7, null)
        inv.setItem(8, makeTool(Material.BARRIER,       ITEM_EXIT_EDITOR,
            listOf("&cRight-click to exit edit mode.",
                   "&7Your original hotbar will",
                   "&7be restored.")))

        player.updateInventory()
    }

    /**
     * Restores [player]'s hotbar from the [snapshot] taken before edit mode was
     * activated, and clears any remaining editor items.
     */
    fun removeEditorHotbar(player: Player, snapshot: Map<Int, ItemStack?>) {
        val inv = player.inventory
        for (slot in 0..8) {
            inv.setItem(slot, snapshot[slot])
        }
        player.updateInventory()
    }

    /**
     * Returns `true` if [item] is one of the editor hotbar tools (identified by
     * display name).
     */
    fun isEditorItem(item: ItemStack?): Boolean {
        if (item == null || !item.hasItemMeta()) return false
        val name = item.itemMeta.displayName
        return name == ITEM_POSITION_TOOL
            || name == ITEM_SPAWN_SETTER
            || name == ITEM_SCHEM_TOOL
            || name == ITEM_VALIDATE_TOOL
            || name == ITEM_CONFIG_EDITOR
            || name == ITEM_BORDER_VIZ
            || name == ITEM_EXIT_EDITOR
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeTool(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta?.setDisplayName(MSG.chatColors(name))
        meta?.lore = lore.map { MSG.chatColors(it) }
        item.itemMeta = meta
        return item
    }
}
