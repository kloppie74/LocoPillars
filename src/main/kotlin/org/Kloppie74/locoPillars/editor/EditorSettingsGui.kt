package org.Kloppie74.locoPillars.editor

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.map.EditorSessionManager
import org.Kloppie74.locoPillars.utils.MSG

/**
 * EditorSettingsGui
 *
 * A 27-slot inventory GUI that allows admins to adjust schematic-save
 * parameters (save radius, border size) for the currently active
 * [org.Kloppie74.locoPillars.game.map.EditorSession].
 *
 * Open it with [open]; clicks are processed via the [onInventoryClick]
 * event handler (register this class as a Bukkit [Listener]).
 *
 * Layout:
 * ```
 * [ ][ ][ ][ ][ ][ ][ ][ ][ ]
 * [ ][-5][ ][Radius][ ][+5][ ][ ][ ]
 * [ ][ ][ ][ ][Border][ ][ ][ ][ ]
 * ```
 *
 * @param plugin Plugin instance, used for task scheduling.
 * @param editorSessionManager Provides access to the active [EditorSession].
 */
class EditorSettingsGui(
    private val plugin: LocoPillars,
    private val editorSessionManager: EditorSessionManager
) : Listener {

    private val title = MSG.chatColors("&8✦ &bEditor Settings &8✦")

    /** Opens the settings GUI for [player]. Requires an active editor session. */
    fun open(player: Player) {
        val session = editorSessionManager.getSession(player)
        if (session == null) {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            player.sendMessage(MSG.chatColors("&7Use &e/editor edit &7to start one."))
            return
        }

        val inventory = Bukkit.createInventory(player, 54, title)

        inventory.setItem(0, makeButton(Material.PAPER, "&eSave Radius",
            listOf("&7Current: &f${session.getSaveRadius()}",
                   "&7Left-click: &a+5",
                   "&7Right-click: &c-5",
                   "&7Shift-click: ±25")))
        inventory.setItem(1, makeButton(Material.LIME_DYE, "&aBorder Enabled",
            listOf("&7Current: &f${if (session.isBorderEnabled()) "enabled" else "disabled"}",
                   "&7Click to toggle")))
        inventory.setItem(2, makeButton(Material.GLASS, "&bBorder Size",
            listOf("&7Current: &f${session.getBorderSize().toInt()}",
                   "&7Left-click: &a+10",
                   "&7Right-click: &c-10",
                   "&7Shift-click: ±50")))
        inventory.setItem(3, makeButton(Material.CACTUS, "&2Border Damage Amount",
            listOf("&7Current: &f${"%.1f".format(session.getBorderDamageAmount())}",
                   "&7Left-click: &a+0.5",
                   "&7Right-click: &c-0.5")))
        inventory.setItem(4, makeButton(Material.SHIELD, "&9Border Damage Buffer",
            listOf("&7Current: &f${"%.1f".format(session.getBorderDamageBuffer())}",
                   "&7Left-click: &a+1.0",
                   "&7Right-click: &c-1.0")))

        inventory.setItem(9, makeButton(Material.BRICKS, "&6Build Height",
            listOf("&7Current: &f${session.getBuildHeight()}",
                   "&7Left-click: &a+5",
                   "&7Right-click: &c-5")))
        inventory.setItem(10, makeButton(Material.LADDER, "&6Build Range",
            listOf("&7Current: &f${session.getBuildRange()}",
                   "&7Left-click: &a+5",
                   "&7Right-click: &c-5")))
        inventory.setItem(11, makeButton(Material.WITHER_SKELETON_SKULL, "&6Death Height",
            listOf("&7Current: &f${session.getDeathHeight()}",
                   "&7Left-click: &a+5",
                   "&7Right-click: &c-5")))
        inventory.setItem(12, makeButton(Material.BEDROCK, "&6Can Break Map Blocks",
            listOf("&7Current: &f${if (session.canBreakMapBlocks()) "enabled" else "disabled"}",
                   "&7Click to toggle")))

        inventory.setItem(18, makeButton(Material.CLOCK, "&dLobby Timer",
            listOf("&7Current: &f${session.getLobbyTimer()}s",
                   "&7Left-click: &a+5s",
                   "&7Right-click: &c-5s")))
        inventory.setItem(19, makeButton(Material.CLOCK, "&dCage Timer",
            listOf("&7Current: &f${session.getCageTimer()}s",
                   "&7Left-click: &a+1s",
                   "&7Right-click: &c-1s")))
        inventory.setItem(20, makeButton(Material.CLOCK, "&dGame Timer",
            listOf("&7Current: &f${session.getGameTimer()}s",
                   "&7Left-click: &a+60s",
                   "&7Right-click: &c-60s")))
        inventory.setItem(21, makeButton(Material.CLOCK, "&dItem Timer",
            listOf("&7Current: &f${session.getItemTimer()}s",
                   "&7Left-click: &a+5s",
                   "&7Right-click: &c-5s")))

        inventory.setItem(27, makeButton(Material.NAME_TAG, "&bSpawn Holograms",
            listOf("&7Current: &f${if (session.isHologramsEnabled()) "enabled" else "disabled"}",
                   "&7Left-click: toggle",
                   "&7Right-click: refresh now")))
        inventory.setItem(31, makeButton(Material.BOOK, "&eEditor Help",
            listOf("&7Use &e/editor help",
                   "&7Use &e/editor hotbar on/off",
                   "&7Use &e/editor holograms on/off")))

        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title != title) return
        event.isCancelled = true

        val player  = event.whoClicked as? Player ?: return
        val session = editorSessionManager.getSession(player) ?: return
        val add = event.isLeftClick
        val large = event.isShiftClick
        when (event.slot) {
            0 -> {
                val delta = if (large) 25 else 5
                session.setSaveRadius(session.getSaveRadius() + if (add) delta else -delta)
            }
            1 -> session.toggleBorderEnabled()
            2 -> {
                val delta = if (large) 50.0 else 10.0
                session.setBorderSize(session.getBorderSize() + if (add) delta else -delta)
            }
            3 -> session.setBorderDamageAmount(session.getBorderDamageAmount() + if (add) 0.5 else -0.5)
            4 -> session.setBorderDamageBuffer(session.getBorderDamageBuffer() + if (add) 1.0 else -1.0)

            9 -> session.setBuildHeight(session.getBuildHeight() + if (add) 5 else -5)
            10 -> session.setBuildRange(session.getBuildRange() + if (add) 5 else -5)
            11 -> session.setDeathHeight(session.getDeathHeight() + if (add) 5 else -5)
            12 -> session.toggleBreakMapBlocks()

            18 -> session.setLobbyTimer(session.getLobbyTimer() + if (add) 5 else -5)
            19 -> session.setCageTimer(session.getCageTimer() + if (add) 1 else -1)
            20 -> session.setGameTimer(session.getGameTimer() + if (add) 60 else -60)
            21 -> session.setItemTimer(session.getItemTimer() + if (add) 5 else -5)

            27 -> {
                if (event.isRightClick) {
                    session.refreshHolograms()
                } else {
                    session.setHologramsEnabled(!session.isHologramsEnabled())
                }
            }
            31 -> {
                player.sendMessage(MSG.chatColors("&6=== Editor Quick Help ==="))
                player.sendMessage(MSG.chatColors("&e/editor edit &7- start/continue editing selected map"))
                player.sendMessage(MSG.chatColors("&e/editor map <name> &7- select active map"))
                player.sendMessage(MSG.chatColors("&e/editor hotbar on/off &7- show/hide editor tools"))
                player.sendMessage(MSG.chatColors("&e/editor holograms on/off &7- toggle spawn holograms"))
                player.sendMessage(MSG.chatColors("&e/editor save &7- save schematic and close session"))
            }
            else -> return
        }

        // Re-open the GUI on the next tick so the updated values are shown.
        Bukkit.getScheduler().runTask(plugin, Runnable { open(player) })
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun makeButton(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta?.setDisplayName(MSG.chatColors(name))
        meta?.lore = lore.map { MSG.chatColors(it) }
        item.itemMeta = meta
        return item
    }
}
