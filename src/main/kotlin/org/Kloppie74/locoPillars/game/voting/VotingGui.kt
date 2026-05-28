package org.Kloppie74.locoPillars.game.voting

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.utils.MSG
import java.util.UUID

class VotingGui(
    private val plugin: LocoPillars,
    private val manager: VotingManager
) : Listener {

    private val openSessions = mutableMapOf<UUID, Pair<Inventory, GameInstance>>()
    private var cachedConfig: Pair<VotingGuiConfig, Long>? = null

    fun open(player: Player, instance: GameInstance) {
        val config = loadGuiConfig()
        val modules = manager.loader.getAll()
        val rows = config.rows.coerceIn(1, 6)
        val size = rows * 9

        val inv = Bukkit.createInventory(null, size, config.title)
        fillBorder(inv, config.borderMaterial)

        val voteCounts = manager.getVoteCounts(instance)
        val playerVote = manager.getPlayerVote(player, instance)

        modules.forEachIndexed { index, module ->
            val slot = config.voteSlots.getOrNull(index) ?: return@forEachIndexed
            if (slot >= size) return@forEachIndexed

            val item = ItemStack(module.iconMaterial)
            val meta = item.itemMeta!!
            if (module.iconCustomModelData > 0) meta.setCustomModelData(module.iconCustomModelData)
            val voteCount = voteCounts[module.id] ?: 0
            val isVoted = playerVote == module.id
            meta.setDisplayName(if (isVoted) "§a§l✔ ${module.displayName}" else "§f${module.displayName}")
            meta.lore = mutableListOf<String>().also { l ->
                l.add("§7${module.description}")
                l.add("")
                l.add("§7Votes: §e$voteCount")
                l.add("")
                if (isVoted) {
                    l.add("§a§lYOU VOTED FOR THIS")
                    l.add("§7Right-click to unvote")
                } else {
                    l.add("§e▶ Click to vote")
                }
            }
            item.itemMeta = meta
            inv.setItem(slot, item)
        }

        // Info item
        if (config.infoSlot >= 0 && config.infoSlot < size) {
            val info = ItemStack(Material.BOOK)
            val infoMeta = info.itemMeta!!
            infoMeta.setDisplayName("§b§lVoting Info")
            val totalVotes = voteCounts.values.sum()
            infoMeta.lore = mutableListOf<String>().also { l ->
                l.add("§7Total votes cast: §e$totalVotes")
                l.add("§7Players in game: §e${instance.players.size}")
                l.add("")
                val leading = voteCounts.maxByOrNull { it.value }
                if (leading != null) {
                    val leadName = manager.loader.get(leading.key)?.displayName ?: leading.key
                    l.add("§7Leading: §a$leadName §7(§e${leading.value} vote${if (leading.value != 1) "s" else ""}§7)")
                } else {
                    l.add("§7No votes cast yet!")
                }
                l.add("")
                l.add("§7The winning event activates")
                l.add("§7when cages open!")
            }
            info.itemMeta = infoMeta
            inv.setItem(config.infoSlot, info)
        }

        // Close button
        if (config.closeSlot >= 0 && config.closeSlot < size) {
            val close = ItemStack(Material.BARRIER)
            val closeMeta = close.itemMeta!!
            closeMeta.setDisplayName("§c§lClose")
            close.itemMeta = closeMeta
            inv.setItem(config.closeSlot, close)
        }

        player.openInventory(inv)
        openSessions[player.uniqueId] = Pair(inv, instance)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val (inv, instance) = openSessions[player.uniqueId] ?: return
        if (event.inventory != inv) return
        event.isCancelled = true

        val config = loadGuiConfig()
        val slot = event.rawSlot

        if (slot == config.closeSlot) {
            player.closeInventory()
            return
        }

        // Voting window may have closed mid-session (start-timer fired). Refuse silently
        // and dismiss the menu so the player isn't left clicking a stale GUI.
        if (!manager.isVotingOpen(instance)) {
            player.sendMessage(MSG.lang("pillars.vote.voting_closed"))
            player.closeInventory()
            return
        }

        val modules = manager.loader.getAll()
        val moduleIndex = config.voteSlots.indexOf(slot)
        if (moduleIndex < 0 || moduleIndex >= modules.size) return

        val module = modules[moduleIndex]
        val currentVote = manager.getPlayerVote(player, instance)

        if (event.isRightClick && currentVote == module.id) {
            manager.unvote(player, instance)
            player.sendMessage(MSG.chatColors("&7Removed your vote for &f${module.displayName}&7."))
        } else {
            manager.castVote(player, instance, module.id)
            player.sendMessage(MSG.chatColors("&aVoted for: &f${module.displayName}"))
        }
        open(player, instance)
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        openSessions.remove((event.player as? Player)?.uniqueId)
    }

    /**
     * Force-closes every open voting menu tied to [instance]. Called when the
     * game transitions out of the voting window (start-timer fires / cage
     * teleport happens) so players cannot keep voting via a stale GUI.
     */
    fun closeAllForInstance(instance: GameInstance) {
        val toClose = openSessions.entries.filter { it.value.second.id == instance.id }.toList()
        toClose.forEach { (uuid, _) ->
            val player = Bukkit.getPlayer(uuid) ?: return@forEach
            player.closeInventory()
        }
    }

    private fun loadGuiConfig(): VotingGuiConfig {
        val guiFile = manager.loader.getGuiFile()
        if (!guiFile.exists()) return VotingGuiConfig()
        val modified = guiFile.lastModified()
        val cached = cachedConfig
        if (cached != null && cached.second == modified) return cached.first
        return try {
            val config = manager.loader.scriptEngine.evaluateGuiConfig(guiFile)
            cachedConfig = Pair(config, modified)
            config
        } catch (_: Exception) {
            VotingGuiConfig()
        }
    }

    private fun fillBorder(inv: Inventory, materialName: String) {
        val material = try { Material.valueOf(materialName.uppercase()) } catch (_: Exception) { Material.GRAY_STAINED_GLASS_PANE }
        val pane = ItemStack(material)
        val meta = pane.itemMeta!!
        meta.setDisplayName(" ")
        pane.itemMeta = meta
        val size = inv.size
        for (i in 0 until 9) inv.setItem(i, pane)
        for (i in size - 9 until size) inv.setItem(i, pane)
        for (i in 0 until size step 9) inv.setItem(i, pane)
        for (i in 8 until size step 9) inv.setItem(i, pane)
    }
}
