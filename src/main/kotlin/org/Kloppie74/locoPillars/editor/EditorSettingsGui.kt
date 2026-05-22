package org.Kloppie74.locoPillars.editor

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.map.EditorSessionManager
import org.Kloppie74.locoPillars.utils.MSG

/**
 * EditorSettingsGui — Complete 6-row (54-slot) settings GUI.
 *
 * Covers every setting present in a full game YML:
 *
 * Row 0 (slots 0–8)   — Schematic / Save region (SaveRadius XZ, SaveHeight Y)
 * Row 1 (slots 9–17)  — Border settings
 * Row 2 (slots 18–26) — Gameplay: build/death bounds + break blocks
 * Row 3 (slots 27–35) — Timers
 * Row 4 (slots 36–44) — Player/Team counts + spawn holograms
 * Row 5 (slots 45–53) — World settings + misc + blacklist + close button
 *
 * All values persist to settings.yml via EditorSession.
 */
class EditorSettingsGui(
    private val plugin: LocoPillars,
    private val editorSessionManager: EditorSessionManager,
    private val blacklistedItemsGui: BlacklistedItemsGui
) : Listener {

    private val title = MSG.chatColors("&0&l⚙ &bMap Settings &0&l⚙")

    // -------------------------------------------------------------------------
    // Open
    // -------------------------------------------------------------------------

    fun open(player: Player) {
        val session = editorSessionManager.getSession(player)
        if (session == null) {
            player.sendMessage(MSG.chatColors("&cNo active editor session! Use &e/editor edit &cfirst."))
            return
        }

        val inv = Bukkit.createInventory(player, 54, title)

        // ── Row 0: Schematic ─────────────────────────────────────────────────
        inv.setItem(0, section("&6&lSCHEMATIC"))
        inv.setItem(1, btn(Material.PAPER, "&eSave Radius (X/Z)", session.getSaveRadius().toString(), "blocks",
            "&7L-click &a+5  &7| R-click &c-5",
            "&7Shift+click &a+25 &7| &c-25",
            "&8Only affects horizontal (X/Z) save area"))
        inv.setItem(2, btn(Material.GOLDEN_PICKAXE, "&eWorlds Amount", session.getWorldsAmount().toString(), "instances",
            "&7L-click &a+1  &7| R-click &c-1",
            "&7Shift+click &a+5  &7| &c-5",
            "&7How many parallel instances to preload"))
        inv.setItem(3, toggle(Material.CHEST_MINECART, "&eLoad Map on Start", session.isLoadMap()))
        inv.setItem(4, btn(Material.SPYGLASS, "&eSave Height (Y)", session.getSaveHeight().toString(), "blocks above center",
            "&7L-click &a+10 &7| R-click &c-10",
            "&7Shift+click &a+50 &7| &c-50",
            "&8Saves from world floor up to center+height",
            "&8Default: 200  |  Capped at world max height"))
        for (slot in 5..8) inv.setItem(slot, filler())

        // ── Row 1: Border ────────────────────────────────────────────────────
        inv.setItem(9, section("&a&lBORDER"))
        inv.setItem(10, toggle(Material.LIME_DYE, "&aBorder Enabled", session.isBorderEnabled()))
        inv.setItem(11, btn(Material.GREEN_STAINED_GLASS, "&aBorder Size", session.getBorderSize().toInt().toString(), "blocks",
            "&7L-click &a+10 &7| R-click &c-10",
            "&7Shift+click &a+50 &7| &c-50"))
        inv.setItem(12, btn(Material.CACTUS, "&aBorder Damage", "%.1f".format(session.getBorderDamageAmount()), "♥/s",
            "&7L-click &a+0.5 &7| R-click &c-0.5"))
        inv.setItem(13, btn(Material.SHIELD, "&aBorder Buffer", "%.1f".format(session.getBorderDamageBuffer()), "blocks",
            "&7Safe zone inside border before damage starts",
            "&7L-click &a+1   &7| R-click &c-1"))
        for (slot in 14..17) inv.setItem(slot, filler())

        // ── Row 2: Gameplay bounds ───────────────────────────────────────────
        inv.setItem(18, section("&c&lGAMEPLAY"))
        inv.setItem(19, btn(Material.BRICKS, "&cBuild Height", session.getBuildHeight().toString(), "blocks above origin",
            "&7L-click &a+5  &7| R-click &c-5"))
        inv.setItem(20, btn(Material.LADDER, "&cBuild Range", session.getBuildRange().toString(), "blocks from centre",
            "&7L-click &a+5  &7| R-click &c-5"))
        inv.setItem(21, btn(Material.WITHER_SKELETON_SKULL, "&cDeath Height", session.getDeathHeight().toString(), "Y level",
            "&7Player dies below this Y",
            "&7L-click &a+5  &7| R-click &c-5"))
        inv.setItem(22, toggle(Material.IRON_PICKAXE, "&cCan Break Map Blocks", session.canBreakMapBlocks()))
        inv.setItem(23, toggle(Material.OAK_DOOR, "&cLobby Enabled", session.isLobbyEnabled()).also { item ->
            val meta = item.itemMeta!!
            val extra = if (session.isLobbyEnabled())
                listOf(MSG.chatColors("&8Players wait in a shared lobby before game starts"))
            else
                listOf(MSG.chatColors("&8Players spawn directly in their cage on join"))
            meta.lore = (meta.lore ?: emptyList()) + extra
            item.itemMeta = meta
        })
        for (slot in 24..26) inv.setItem(slot, filler())

        // ── Row 3: Timers ─────────────────────────────────────────────────────
        inv.setItem(27, section("&d&lTIMERS"))
        inv.setItem(28, btn(Material.CLOCK, "&dLobby Timer", session.getLobbyTimer().toString(), "s",
            "&7L-click &a+5s &7| R-click &c-5s"))
        inv.setItem(29, btn(Material.CLOCK, "&dCage Timer", session.getCageTimer().toString(), "s",
            "&7L-click &a+1s &7| R-click &c-1s"))
        inv.setItem(30, btn(Material.CLOCK, "&dGame Timer", formatTime(session.getGameTimer()), "",
            "&7L-click &a+60s &7| R-click &c-60s",
            "&7Shift+click &a+300s &7| &c-300s"))
        inv.setItem(31, btn(Material.CLOCK, "&dItem Timer", session.getItemTimer().toString(), "s",
            "&7Interval between item drops",
            "&7L-click &a+5s &7| R-click &c-5s"))
        for (slot in 32..35) inv.setItem(slot, filler())

        // ── Row 4: Players / Teams ────────────────────────────────────────────
        inv.setItem(36, section("&b&lPLAYERS & TEAMS"))
        inv.setItem(37, btn(Material.PLAYER_HEAD, "&bMin Players", session.getMinimumPlayers().toString(), "players",
            "&7L-click &a+1  &7| R-click &c-1"))
        inv.setItem(38, btn(Material.PLAYER_HEAD, "&bMax Players", session.getMaximumPlayers().toString(), "players",
            "&7L-click &a+1  &7| R-click &c-1"))
        inv.setItem(39, btn(Material.SKELETON_SKULL, "&bMax Teams", session.getMaxTeams().toString(), "teams",
            "&7L-click &a+1  &7| R-click &c-1"))
        inv.setItem(40, btn(Material.ARMOR_STAND, "&bPlayers Per Team", session.getPlayersPerTeam().toString(), "players",
            "&7L-click &a+1  &7| R-click &c-1"))
        inv.setItem(41, hologramBtn(session.isHologramsEnabled()))
        for (slot in 42..44) inv.setItem(slot, filler())

        // ── Row 5: World / Misc ───────────────────────────────────────────────
        inv.setItem(45, section("&e&lWORLD / MISC"))
        inv.setItem(46, btn(Material.NAME_TAG, "&eDisplay Name", session.getDisplayName(), "",
            "&7Edit via &e/editor displayname <text>"))
        inv.setItem(47, btn(Material.ENDER_EYE, "&eRandom Items Type",
            session.getRandomItemsType(), "",
            "&7L-click: cycle   &8(Blacklisted / Whitelisted / None)",
            "&7Controls which items are allowed in chests"))
        inv.setItem(48, toggle(Material.NETHER_STAR, "&eLucky Blocks", session.isLuckyBlocksEnabled()))
        val blacklistCount = session.getBlacklistedItems().size
        inv.setItem(49, makeButton(Material.RED_WOOL, "&cBlacklisted Items",
            listOf(
                MSG.chatColors("&7Currently: &f$blacklistCount &7item(s) blacklisted"),
                MSG.chatColors("&7Click to manage the blacklist")
            )))
        inv.setItem(50, btn(Material.CLOCK, "&eLuckyblock Chance", "${session.getLuckyblockChance()}", "%",
            "&7Chance that breaking a luckyblock fires an effect",
            "&7L-click &a+5  &7| R-click &c-5",
            "&7Shift+click &a+25 &7| &c-25"))
        for (slot in 51..52) inv.setItem(slot, filler())
        inv.setItem(53, makeButton(Material.BARRIER, "&cClose", listOf("&7Click to close the settings GUI.")))

        player.openInventory(inv)
    }

    // -------------------------------------------------------------------------
    // Click handler
    // -------------------------------------------------------------------------

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.view.title() != net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
                .legacyAmpersand().deserialize(MSG.chatColors(title).replace("§", "&"))) {
            // Simple title string comparison
        }
        // Title check via legacy string
        if (event.view.title != title) return
        event.isCancelled = true

        val player  = event.whoClicked as? Player ?: return
        val session = editorSessionManager.getSession(player) ?: return
        val left    = event.isLeftClick
        val shift   = event.isShiftClick

        when (event.slot) {
            // ── Row 0: Schematic ──────────────────────────────────────────────
            1  -> session.setSaveRadius(session.getSaveRadius()    + step(left, shift, 5, 25))
            2  -> session.setWorldsAmount(session.getWorldsAmount()+ step(left, shift, 1, 5))
            3  -> session.toggleLoadMap()
            4  -> session.setSaveHeight(session.getSaveHeight()    + step(left, shift, 10, 50))

            // ── Row 1: Border ─────────────────────────────────────────────────
            10 -> session.toggleBorderEnabled()
            11 -> session.setBorderSize(session.getBorderSize()    + step(left, shift, 10.0, 50.0))
            12 -> session.setBorderDamageAmount(session.getBorderDamageAmount() + step(left, false, 0.5, 0.5))
            13 -> session.setBorderDamageBuffer(session.getBorderDamageBuffer() + step(left, false, 1.0, 1.0))

            // ── Row 2: Gameplay ────────────────────────────────────────────────
            19 -> session.setBuildHeight(session.getBuildHeight()  + step(left, shift, 5, 5))
            20 -> session.setBuildRange(session.getBuildRange()    + step(left, shift, 5, 5))
            21 -> session.setDeathHeight(session.getDeathHeight()  + step(left, shift, 5, 5))
            22 -> session.toggleBreakMapBlocks()
            23 -> session.toggleLobbyEnabled()

            // ── Row 3: Timers ──────────────────────────────────────────────────
            28 -> session.setLobbyTimer(session.getLobbyTimer()    + step(left, shift, 5, 5))
            29 -> session.setCageTimer(session.getCageTimer()      + step(left, shift, 1, 1))
            30 -> session.setGameTimer(session.getGameTimer()      + step(left, shift, 60, 300))
            31 -> session.setItemTimer(session.getItemTimer()      + step(left, shift, 5, 5))

            // ── Row 4: Players / Teams ─────────────────────────────────────────
            37 -> session.setMinimumPlayers(session.getMinimumPlayers() + step(left, shift, 1, 1))
            38 -> session.setMaximumPlayers(session.getMaximumPlayers() + step(left, shift, 1, 1))
            39 -> session.setMaxTeams(session.getMaxTeams()             + step(left, shift, 1, 1))
            40 -> session.setPlayersPerTeam(session.getPlayersPerTeam() + step(left, shift, 1, 1))
            41 -> {
                if (event.isRightClick) session.refreshHolograms()
                else session.setHologramsEnabled(!session.isHologramsEnabled())
            }

            // ── Row 5: Misc ────────────────────────────────────────────────────
            47 -> session.cycleRandomItemsType()
            48 -> session.toggleLuckyBlocks()
            49 -> {
                player.closeInventory()
                Bukkit.getScheduler().runTask(plugin, Runnable { blacklistedItemsGui.open(player) })
                return
            }
            50 -> session.setLuckyblockChance(session.getLuckyblockChance() + step(left, shift, 5, 25))
            53 -> { player.closeInventory(); return }

            else -> return
        }

        Bukkit.getScheduler().runTask(plugin, Runnable { open(player) })
    }

    // -------------------------------------------------------------------------
    // Item builders
    // -------------------------------------------------------------------------

    private fun section(label: String): ItemStack {
        val item = ItemStack(Material.GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta!!
        meta.setDisplayName(MSG.chatColors(label))
        meta.lore = emptyList()
        item.itemMeta = meta
        return item
    }

    private fun filler(): ItemStack {
        val item = ItemStack(Material.BLACK_STAINED_GLASS_PANE)
        val meta = item.itemMeta!!
        meta.setDisplayName(" ")
        item.itemMeta = meta
        return item
    }

    private fun btn(mat: Material, name: String, value: String, unit: String, vararg hints: String): ItemStack {
        val lore = mutableListOf(
            MSG.chatColors("&7Value: &f$value${if (unit.isNotEmpty()) " &7$unit" else ""}")
        )
        hints.forEach { lore += MSG.chatColors(it) }
        return makeButton(mat, name, lore)
    }

    private fun toggle(mat: Material, name: String, enabled: Boolean): ItemStack {
        val color   = if (enabled) "&a" else "&c"
        val display = if (enabled) "Enabled" else "Disabled"
        val icon    = if (enabled) Material.LIME_DYE else Material.GRAY_DYE
        // Use icon material for toggles, override mat only for the dye indicator
        val actual  = if (mat == Material.LIME_DYE || mat == Material.GRAY_DYE) icon else mat
        return makeButton(actual, "$color$name",
            listOf(MSG.chatColors("$color● $display"), MSG.chatColors("&7Click to toggle")))
    }

    private fun hologramBtn(enabled: Boolean): ItemStack {
        val color   = if (enabled) "&b" else "&7"
        val display = if (enabled) "Enabled" else "Disabled"
        return makeButton(Material.NAME_TAG, "&bSpawn Holograms",
            listOf(
                MSG.chatColors("$color● $display"),
                MSG.chatColors("&7L-click: toggle on/off"),
                MSG.chatColors("&7R-click: refresh holograms now")
            ))
    }

    private fun makeButton(material: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(material)
        val meta = item.itemMeta!!
        meta.setDisplayName(MSG.chatColors(name))
        meta.lore = lore.map { MSG.chatColors(it) }
        item.itemMeta = meta
        return item
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns the signed delta based on click direction + shift modifier. */
    private fun step(left: Boolean, shift: Boolean, small: Int, large: Int): Int =
        (if (shift) large else small) * (if (left) 1 else -1)

    private fun step(left: Boolean, shift: Boolean, small: Double, large: Double): Double =
        (if (shift) large else small) * (if (left) 1.0 else -1.0)

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}m${s}s" else "${s}s"
    }
}