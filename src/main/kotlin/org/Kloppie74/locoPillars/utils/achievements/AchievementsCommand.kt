package org.Kloppie74.locoPillars.utils.achievements

import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.Bukkit
import org.bukkit.inventory.Inventory
import org.bukkit.plugin.Plugin

/**
 * /achievements command: opens a GUI to show all achievements, with paging, sorting, filtering.
 * Always shows correct per-achievement progress!
 * Always uses BOOK, ENCHANTED_BOOK for unlocked.
 * Supports filtering (show only unlocked/not unlocked/all).
 * Unlocked achievements always at the end, sorted A-Z.
 * Shows rarity: % of players with achievement.
 */
class AchievementsCommand(
    private val manager: AchievementManager,
    private val plugin: Plugin
) : CommandExecutor, Listener {

    companion object {
        private const val GUI_TITLE = "§5Achievements"
        private const val ROWS = 6
        private const val SIZE = ROWS * 9

        private val BORDER_SLOTS: Set<Int> = buildSet {
            addAll(0..8)
            addAll(45..53)
            for (row in 0 until ROWS) {
                add(row * 9)
                add(row * 9 + 8)
            }
        }

        private val ACHIEVEMENT_SLOTS: List<Int> = buildList {
            for (row in 1..4) {
                for (col in 1..7) {
                    add(row * 9 + col)
                }
            }
        }

        private const val NEXT_PAGE_SLOT = 53
        private const val PREV_PAGE_SLOT = 45
        private const val FILTER_SLOT = 3
    }

    private val playerPages = mutableMapOf<Player, Int>()
    private val playerFilters = mutableMapOf<Player, FilterType>()

    private enum class FilterType(val display: String) {
        ALL("§aShow All"),
        UNLOCKED("§bUnlocked Only"),
        NOT_UNLOCKED("§cNot Unlocked Only");

        fun next(): FilterType = when (this) {
            ALL -> UNLOCKED
            UNLOCKED -> NOT_UNLOCKED
            NOT_UNLOCKED -> ALL
        }
    }

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players can use this command.")
            return true
        }
        playerFilters[sender] = FilterType.ALL
        openAchievementsGui(sender, 0)
        return true
    }

    fun openAchievementsGui(player: Player, page: Int = 0) {
        val filter = playerFilters[player] ?: FilterType.ALL

        // Get achievements, unlocked state, sort so unlocked always come last, both groups A-Z
        val allAchievements = manager.getAllAchievements().toList()
        val unlockedIds = manager.databaseManager.achievementsTable.getPlayerAchievements(player.uniqueId.toString()).toSet()

        val notUnlockedAchievements = allAchievements.filter { !unlockedIds.contains(it.achievementId) }
            .sortedBy { it.displayName.lowercase() }
        val unlockedAchievements = allAchievements.filter { unlockedIds.contains(it.achievementId) }
            .sortedBy { it.displayName.lowercase() }

        val achievementsSorted = notUnlockedAchievements + unlockedAchievements

        // Filter achievements as per filter
        val achievementsFiltered = achievementsSorted.filter {
            when (filter) {
                FilterType.ALL -> true
                FilterType.UNLOCKED -> unlockedIds.contains(it.achievementId)
                FilterType.NOT_UNLOCKED -> !unlockedIds.contains(it.achievementId)
            }
        }

        val achievementsPerPage = ACHIEVEMENT_SLOTS.size
        val maxPage = ((achievementsFiltered.size - 1) / achievementsPerPage)
        val pageIndex = page.coerceIn(0, maxPage)
        playerPages[player] = pageIndex

        val inventory: Inventory = Bukkit.createInventory(player, SIZE, GUI_TITLE)

        // Fill borders
        val grayGlass = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { setDisplayName("§7") }
        }
        BORDER_SLOTS.forEach { inventory.setItem(it, grayGlass) }

        // Filter item
        val filterItem = ItemStack(Material.HOPPER).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§eFilter: ${filter.display}")
                lore = listOf(
                    "§7Click to filter achievements",
                    "",
                    "§fCurrent: ${filter.display}",
                    "§8Unlocked achievements always shown last"
                )
            }
        }
        inventory.setItem(FILTER_SLOT, filterItem)

        // Cache total player count (for rarity)
        val totalPlayers = manager.databaseManager.playersTable.getTotalPlayersCount()

        // Fill achievements (paged)
        val startIdx = pageIndex * achievementsPerPage
        val endIdx = (startIdx + achievementsPerPage).coerceAtMost(achievementsFiltered.size)
        val pageAchievements = achievementsFiltered.subList(startIdx, endIdx)

        ACHIEVEMENT_SLOTS.forEachIndexed { i, slot ->
            val ach = pageAchievements.getOrNull(i) ?: return@forEachIndexed
            val isCompleted = unlockedIds.contains(ach.achievementId)
            val target = ach.targetValue ?: 1

            // FIX: always show correct progress per achievement!
            val progress = manager.databaseManager.achievementsTable.getAchievementProgress(player.uniqueId.toString(), ach.achievementId)
            val progressVal = if (isCompleted) target else (progress?.currentProgress ?: 0)
            val progressPercent = if (isCompleted) 100.0 else (progressVal * 100.0 / target).coerceIn(0.0, 100.0)

            // Rarity calculation: % of all players who unlocked
            val unlockedPlayerCount = manager.databaseManager.achievementsTable.getPlayerAchievementsForAchievement(ach.achievementId).size
            val rarityPercent = if (totalPlayers > 0) (unlockedPlayerCount * 100.0 / totalPlayers) else 0.0

            val item: ItemStack = if (isCompleted) {
                ItemStack(Material.ENCHANTED_BOOK)
            } else {
                ItemStack(Material.BOOK)
            }
            val meta: ItemMeta = item.itemMeta ?: return@forEachIndexed

            // Difficulty formatting
            val diffColor = when (ach.difficulty.lowercase()) {
                "easy" -> "§aEasy"
                "medium" -> "§eMedium"
                "hard" -> "§cHard"
                "legendary" -> "§6Legendary"
                else -> "§7${ach.difficulty.replaceFirstChar { it.uppercase() }}"
            }

            // Type formatting
            val typeColor = when (ach.achievementType.lowercase()) {
                "progressive" -> "§bProgressive"
                "secret" -> "§dSecret"
                "normal" -> "§fNormal"
                else -> "§7${ach.achievementType.replaceFirstChar { it.uppercase() }}"
            }

            // Prizes formatting
            val prizes = buildList {
                if (ach.coinReward > 0) add("§6● Coins: §e${ach.coinReward}")
                if (ach.experienceReward > 0) add("§b● XP: §a${ach.experienceReward}")
                ach.specialRewards?.let {
                    if (it.isNotBlank()) {
                        it.split(";").filter { cmd -> cmd.isNotBlank() }.forEach { cmd ->
                            add("§d● Command: §f${cmd.replace("%player%", player.name)}")
                        }
                    }
                }
            }

            meta.setDisplayName(
                if (ach.isHidden && !isCompleted) "§7Hidden Achievement"
                else if (isCompleted) "§aAchievement Unlocked!" else "§f${ach.displayName}"
            )

            meta.lore = buildList {
                // Description
                add("§7${manager.replaceAmount(ach.description, target)}")
                add("§f─────────────")
                // Prizes
                if (prizes.isNotEmpty()) {
                    add("§ePrizes:")
                    prizes.forEach { add(it) }
                } else {
                    add("§7No prizes")
                }
                add("§f─────────────")
                // Difficulty & Type & Category
                add("§7Difficulty: $diffColor")
                add("§7Type: $typeColor")
                add("§7Category: §9${ach.category.replaceFirstChar { it.uppercase() }}")
                // Rarity
                add("§7Rarity: §b${String.format("%.1f", rarityPercent)}% §8(${unlockedPlayerCount}/${totalPlayers})")
                // Progress bar (again, clearer at bottom)
                add("")
                add("§fProgress: §b$progressVal§7/§b$target §8(${String.format("%.1f", progressPercent)}%)")
                if (isCompleted) add("§aAchievement Unlocked!")
                else if (progressPercent >= 100.0) add("§aReady to claim!")
                // Extra
                if (ach.isSecret) add("§dSecret Achievement!")
                if (ach.isHidden) add("§8Hidden")
            }
            item.itemMeta = meta
            inventory.setItem(slot, item)
        }

        // Navigation arrows
        if (pageIndex > 0) {
            val prevArrow = ItemStack(Material.ARROW).apply {
                itemMeta = itemMeta?.apply {
                    setDisplayName("§7Previous Page")
                    lore = listOf("§8View previous achievements")
                }
            }
            inventory.setItem(PREV_PAGE_SLOT, prevArrow)
        }
        if (pageIndex < maxPage) {
            val nextArrow = ItemStack(Material.ARROW).apply {
                itemMeta = itemMeta?.apply {
                    setDisplayName("§7Next Page")
                    lore = listOf("§8View more achievements")
                }
            }
            inventory.setItem(NEXT_PAGE_SLOT, nextArrow)
        }

        // Overview item
        val unlocked = unlockedIds.size
        val total = allAchievements.size
        val completion = if (total > 0) unlocked * 100.0 / total else 0.0
        val overview = ItemStack(Material.NETHER_STAR).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName("§dAchievements Overview")
                lore = listOf(
                    "",
                    "§7Unlocked: §a$unlocked§7/§f$total",
                    "§7Completion: §b${String.format("%.1f", completion)}%",
                    "",
                    "§8Use the arrows to navigate pages"
                )
            }
        }
        inventory.setItem(4, overview)

        player.openInventory(inventory)
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        if (event.view.title != GUI_TITLE) return
        event.isCancelled = true

        val slot = event.rawSlot
        when (slot) {
            NEXT_PAGE_SLOT -> {
                val currentPage = playerPages[player] ?: 0
                openAchievementsGui(player, currentPage + 1)
            }
            PREV_PAGE_SLOT -> {
                val currentPage = playerPages[player] ?: 0
                openAchievementsGui(player, currentPage - 1)
            }
            FILTER_SLOT -> {
                val currentFilter = playerFilters[player] ?: FilterType.ALL
                val nextFilter = currentFilter.next()
                playerFilters[player] = nextFilter
                openAchievementsGui(player, 0)
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        playerPages.remove(player)
        playerFilters.remove(player)
    }
}