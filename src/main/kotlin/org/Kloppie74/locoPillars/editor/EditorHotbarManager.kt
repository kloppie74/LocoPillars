package org.Kloppie74.locoPillars.editor

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.Kloppie74.locoPillars.utils.MSG

/**
 * EditorHotbarManager
 *
 * Redesigned hotbar layout (slots 0–8):
 * ```
 * [0] Validation Tool   (amethyst shard) – validate active map files
 * [1] Spawn Setter      (name tag)       – place team spawn points (respects MaxTeams/PerTeam)
 * [2] Lobby Tool        (beacon)         – set lobby spawn + spawn-area corners
 * [3] Settings GUI      (comparator)     – open full map settings GUI
 * [4] Save & Close      (emerald)        – save schematic + end session
 * [5] Cancel Session    (redstone)       – cancel session without saving
 * [6] Refresh Holograms (end crystal)    – toggle/refresh spawn-point holograms
 * [7] Session Info      (filled map)     – print current session info to chat
 * [8] Switch Hotbar     (nether star)    – swap back to original hotbar
 * ```
 */
class EditorHotbarManager {

    companion object {
        const val ITEM_VALIDATE_TOOL  = "§eValidation Tool"
        const val ITEM_SPAWN_SETTER   = "§bSpawn Point Setter"
        const val ITEM_LOBBY_TOOL     = "§5Lobby Tool"
        const val ITEM_CONFIG_EDITOR  = "§aSettings GUI"
        const val ITEM_SAVE_CLOSE     = "§2Save & Close"
        const val ITEM_CANCEL_SESSION = "§cCancel Session"
        const val ITEM_HOLO_REFRESH   = "§dRefresh Holograms"
        const val ITEM_SESSION_INFO   = "§7Session Info"
        const val ITEM_SWITCH_HOTBAR  = "§6Switch Hotbar"
    }

    fun giveEditorHotbar(player: Player) {
        val inv = player.inventory

        // [0] Validation Tool
        inv.setItem(0, makeTool(Material.AMETHYST_SHARD, ITEM_VALIDATE_TOOL, listOf(
            "&7&oSlot 0  —  Validation Tool",
            "&7Right-click: &evalidate active map",
            "&7Checks settings.yml + region.schem for errors.",
            "&7No map selected &8→ &7validates ALL maps."
        )))

        // [1] Spawn Setter
        inv.setItem(1, makeTool(Material.NAME_TAG, ITEM_SPAWN_SETTER, listOf(
            "&7&oSlot 1  —  Spawn Point Setter",
            "&7Right-click block:  &aplace next spawn point",
            "&7Shift + right-click: &ecycle to next team",
            "&8Respects MaxTeams & PlayersPerTeam.",
            "&8Auto-advances slot and team when full."
        )))

        // [2] Lobby Tool
        inv.setItem(2, makeTool(Material.BEACON, ITEM_LOBBY_TOOL, listOf(
            "&7&oSlot 2  —  Lobby Tool",
            "&7Right-click block:         &aset lobby SpawnPoint",
            "&7Left-click block:          &aset Spawn-Area Loc1",
            "&7Shift + right-click block: &aset Spawn-Area Loc2",
            "&7Shift + left-click:        &eclear Spawn-Area",
            "&7Right-click air:           &7show lobby status"
        )))

        // [3] Settings GUI
        inv.setItem(3, makeTool(Material.COMPARATOR, ITEM_CONFIG_EDITOR, listOf(
            "&7&oSlot 3  —  Settings GUI",
            "&7Right-click: &eopen full map settings GUI",
            "&8All YML settings: save radius, border,",
            "&8timers, players/teams, display name, etc."
        )))

        // [4] Save & Close
        inv.setItem(4, makeTool(Material.EMERALD, ITEM_SAVE_CLOSE, listOf(
            "&7&oSlot 4  —  Save & Close",
            "&7Right-click: &asave schematic & end session",
            "&cYou will be teleported back to the lobby.",
            "&8Same as: &7/editor save"
        )))

        // [5] Cancel Session
        inv.setItem(5, makeTool(Material.REDSTONE, ITEM_CANCEL_SESSION, listOf(
            "&7&oSlot 5  —  Cancel Session",
            "&7Right-click: &ccancel without saving",
            "&cAll unsaved changes will be lost!",
            "&8Same as: &7/editor cancel"
        )))

        // [6] Refresh Holograms
        inv.setItem(6, makeTool(Material.END_CRYSTAL, ITEM_HOLO_REFRESH, listOf(
            "&7&oSlot 6  —  Holograms",
            "&7Right-click: &drefresh all spawn holograms",
            "&7Left-click:  &dtoggle holograms on/off",
            "&8Shows team + slot labels above each spawn."
        )))

        // [7] Session Info
        inv.setItem(7, makeTool(Material.FILLED_MAP, ITEM_SESSION_INFO, listOf(
            "&7&oSlot 7  —  Session Info",
            "&7Right-click: &7print session info to chat",
            "&8Map, save radius, border, timers, spawns."
        )))

        // [8] Switch Hotbar
        inv.setItem(8, makeTool(Material.NETHER_STAR, ITEM_SWITCH_HOTBAR, listOf(
            "&7&oSlot 8  —  Switch Hotbar",
            "&7Right-click: &eswitch to your original hotbar",
            "&7Your editor session stays active.",
            "&8Use &e/editor hotbar on &8to restore editor tools.",
            "&8Use &c/editor toggle &8to fully exit edit mode."
        )))

        player.updateInventory()
    }

    fun removeEditorHotbar(player: Player, snapshot: Map<Int, ItemStack?>) {
        val inv = player.inventory
        for (slot in 0..8) inv.setItem(slot, snapshot[slot])
        player.updateInventory()
    }

    fun isEditorItem(item: ItemStack?): Boolean {
        if (item == null || !item.hasItemMeta()) return false
        return when (item.itemMeta?.displayName) {
            ITEM_VALIDATE_TOOL,
            ITEM_SPAWN_SETTER,
            ITEM_LOBBY_TOOL,
            ITEM_CONFIG_EDITOR,
            ITEM_SAVE_CLOSE,
            ITEM_CANCEL_SESSION,
            ITEM_HOLO_REFRESH,
            ITEM_SESSION_INFO,
            ITEM_SWITCH_HOTBAR -> true
            else -> false
        }
    }

    private fun makeTool(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta!!
        meta.setDisplayName(MSG.chatColors(name))
        meta.lore = lore.map { MSG.chatColors(it) }
        item.itemMeta = meta
        return item
    }
}