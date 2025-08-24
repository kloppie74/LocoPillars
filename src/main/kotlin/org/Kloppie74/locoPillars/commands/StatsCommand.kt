package org.Kloppie74.locoPillars.commands

import org.bukkit.Material
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.achievements.AchievementsCommand
import org.bukkit.Bukkit
import kotlin.math.*

class StatsCommand(
    private val plugin: LocoPillars,
    private val achievementsCommand: AchievementsCommand
) : CommandExecutor, Listener {

    companion object {
        private const val STATS_MAIN_TITLE = "&6⚡ Statistics - "
        private const val ACHIEVEMENTS_TITLE = "&5✨ Achievements - "
        private const val LEADERBOARDS_TITLE = "&e🏆 Leaderboards"
        private const val PLAYER_HISTORY_TITLE = "&b📊 Game History - "

        private const val CLOSE_BUTTON_SLOT = 49
        private const val ACHIEVEMENTS_BUTTON_SLOT = 48
        private const val LEADERBOARDS_BUTTON_SLOT = 50
        private const val BACK_BUTTON_SLOT = 45
        private const val REFRESH_BUTTON_SLOT = 53

        private enum class MenuType {
            MAIN_STATS, ACHIEVEMENTS, LEADERBOARDS, GAME_HISTORY
        }
    }

    private val activeMenus = mutableMapOf<Player, MenuType>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(MSG.chatColors("&cThis command can only be used by players!"))
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "achievements" -> openAchievementsGUIWithDelay(sender)
            "leaderboards" -> openLeaderboardsGUIWithDelay(sender)
            "history" -> openGameHistoryGUIWithDelay(sender)
            else -> openStatsGUIWithDelay(sender)
        }

        return true
    }

    // =================== MAIN STATS GUI ===================
    fun openStatsGUI(player: Player) {
        val db = plugin.databaseManager
        val playersTable = db.playersTable
        val statsTable = db.statsTable

        if (!playersTable.playerExists(player.name)) {
            player.sendMessage(MSG.chatColors("&cNo statistics found! Play some games first."))
            return
        }

        val dbPlayerId = playersTable.getPlayerId(player.name)
        if (dbPlayerId == null) {
            player.sendMessage(MSG.chatColors("&cPlayer not found in database!"))
            return
        }

        statsTable.createPlayerStats(dbPlayerId)
        val gamesPlayed = statsTable.getGamesPlayed(dbPlayerId)

        val inv = plugin.server.createInventory(player, 54, MSG.chatColors("$STATS_MAIN_TITLE${player.displayName}"))
        activeMenus[player] = MenuType.MAIN_STATS

        fillBorders(inv)

        val gameStats = GameStatsData(
            gamesPlayed = gamesPlayed,
            gamesWon = statsTable.getGamesWon(dbPlayerId),
            gamesLost = statsTable.getGamesLost(dbPlayerId),
            gamesQuit = statsTable.getGamesQuit(dbPlayerId)
        )

        val combatStats = CombatStatsData(
            kills = statsTable.getTotalKills(dbPlayerId),
            deaths = statsTable.getTotalDeaths(dbPlayerId),
            assists = statsTable.getTotalAssists(dbPlayerId),
            bestKillstreak = statsTable.getBestKillstreak(dbPlayerId),
            currentKillstreak = statsTable.getCurrentKillstreak(dbPlayerId)
        )

        val progressStats = ProgressStatsData(
            level = statsTable.getPlayerLevel(dbPlayerId),
            experience = statsTable.getPlayerExperience(dbPlayerId),
            coins = statsTable.getPlayerCoins(dbPlayerId),
            playtime = statsTable.getTotalPlayTime(dbPlayerId)
        )

        val buildingStats = BuildingStatsData(
            pillarsDestroyed = statsTable.getPillarsDestroyed(dbPlayerId),
            blocksPlaced = statsTable.getBlocksPlaced(dbPlayerId),
            blocksDestroyed = statsTable.getBlocksBroken(dbPlayerId),
            distanceMoved = statsTable.getTotalDistanceMoved(dbPlayerId)
        )

        val streakStats = StreakStatsData(
            currentWinStreak = statsTable.getCurrentWinStreak(dbPlayerId),
            bestWinStreak = statsTable.getBestWinStreak(dbPlayerId),
            longestGame = statsTable.getLongestGame(dbPlayerId),
            fastestWin = statsTable.getFastestWin(dbPlayerId)
        )

        // Main stat displays
        createOverviewStats(inv, 10, gameStats, combatStats, progressStats)
        createPerformanceStats(inv, 12, gameStats, combatStats)
        createProgressStats(inv, 14, progressStats)
        createBuildingStats(inv, 16, buildingStats)

        createStreakStats(inv, 28, streakStats)
        createTimeStats(inv, 30, progressStats, streakStats)
        createRankingStats(inv, 32, dbPlayerId, gameStats, combatStats)
        createAchievementPreview(inv, 34, dbPlayerId)

        createNavigationButtons(inv)

        player.openInventory(inv)
    }

    private fun openAchievementsGUIWithDelay(player: Player) {
        player.closeInventory()
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            achievementsCommand.openAchievementsGui(player)
            activeMenus[player] = MenuType.ACHIEVEMENTS
        }, 1L)
    }

    private fun openStatsGUIWithDelay(player: Player) {
        player.closeInventory()
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            openStatsGUI(player)
            activeMenus[player] = MenuType.MAIN_STATS
        }, 1L)
    }

    private fun openLeaderboardsGUIWithDelay(player: Player) {
        player.closeInventory()
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            openLeaderboardsGUI(player)
            activeMenus[player] = MenuType.LEADERBOARDS
        }, 1L)
    }

    private fun openGameHistoryGUIWithDelay(player: Player) {
        player.closeInventory()
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            openGameHistoryGUI(player)
            activeMenus[player] = MenuType.GAME_HISTORY
        }, 1L)
    }

    // =================== EVENT HANDLERS ===================
    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val menuType = activeMenus[player] ?: return

        event.isCancelled = true

        val clickedItem = event.currentItem ?: return
        val slot = event.slot

        when (menuType) {
            MenuType.MAIN_STATS -> handleMainStatsClick(player, slot, clickedItem)
            MenuType.ACHIEVEMENTS -> handleAchievementsClick(player, slot, clickedItem)
            MenuType.LEADERBOARDS -> handleLeaderboardsClick(player, slot, clickedItem)
            MenuType.GAME_HISTORY -> handleGameHistoryClick(player, slot, clickedItem)
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        activeMenus.remove(player)
    }

    private fun handleMainStatsClick(player: Player, slot: Int, item: ItemStack) {
        when (slot) {
            ACHIEVEMENTS_BUTTON_SLOT, 34 -> {
                openAchievementsGUIWithDelay(player)
                player.sendMessage(MSG.chatColors("&aOpening achievements..."))
            }
            LEADERBOARDS_BUTTON_SLOT -> {
                openLeaderboardsGUIWithDelay(player)
                player.sendMessage(MSG.chatColors("&eOpening leaderboards..."))
            }
            CLOSE_BUTTON_SLOT -> {
                player.closeInventory()
                player.sendMessage(MSG.chatColors("&7Statistics menu closed."))
            }
            REFRESH_BUTTON_SLOT -> {
                openStatsGUIWithDelay(player)
                player.sendMessage(MSG.chatColors("&aStatistics refreshed!"))
            }
        }
    }

    private fun handleAchievementsClick(player: Player, slot: Int, item: ItemStack) {
        when (slot) {
            BACK_BUTTON_SLOT -> {
                openStatsGUIWithDelay(player)
                player.sendMessage(MSG.chatColors("&7Returning to main statistics..."))
            }
            CLOSE_BUTTON_SLOT -> {
                player.closeInventory()
                player.sendMessage(MSG.chatColors("&7Achievements menu closed."))
            }
            REFRESH_BUTTON_SLOT -> {
                openAchievementsGUIWithDelay(player)
                player.sendMessage(MSG.chatColors("&aAchievements refreshed!"))
            }
            in 12..16 -> {
                val categoryName = extractCategoryFromItem(item)
                player.sendMessage(MSG.chatColors("&6Viewing $categoryName achievements..."))
            }
        }
    }

    private fun handleLeaderboardsClick(player: Player, slot: Int, item: ItemStack) {
        when (slot) {
            BACK_BUTTON_SLOT -> {
                openStatsGUIWithDelay(player)
                player.sendMessage(MSG.chatColors("&7Returning to main statistics..."))
            }
            CLOSE_BUTTON_SLOT -> {
                player.closeInventory()
                player.sendMessage(MSG.chatColors("&7Leaderboards menu closed."))
            }
            REFRESH_BUTTON_SLOT -> {
                openLeaderboardsGUIWithDelay(player)
                player.sendMessage(MSG.chatColors("&eLeaderboards refreshed!"))
            }
            in 10..16 -> {
                val leaderboardType = extractLeaderboardTypeFromItem(item)
                player.sendMessage(MSG.chatColors("&6Viewing detailed $leaderboardType rankings..."))
            }
        }
    }

    private fun handleGameHistoryClick(player: Player, slot: Int, item: ItemStack) {
        when (slot) {
            BACK_BUTTON_SLOT -> {
                openStatsGUIWithDelay(player)
                player.sendMessage(MSG.chatColors("&7Returning to main statistics..."))
            }
            CLOSE_BUTTON_SLOT -> {
                player.closeInventory()
                player.sendMessage(MSG.chatColors("&7Game history menu closed."))
            }
            REFRESH_BUTTON_SLOT -> {
                openGameHistoryGUIWithDelay(player)
                player.sendMessage(MSG.chatColors("&bGame history refreshed!"))
            }
        }
    }

    // =================== MAIN STATS GUI ===================


    // =================== LEADERBOARDS GUI ===================

    private fun openLeaderboardsGUI(player: Player) {
        val inv = plugin.server.createInventory(player, 54, MSG.chatColors(LEADERBOARDS_TITLE))
        activeMenus[player] = MenuType.LEADERBOARDS

        fillBorders(inv)

        try {
            // Different leaderboard categories
            val leaderboards = listOf(
                Triple("Wins", Material.GOLD_BLOCK, "wins"),
                Triple("Kill/Death Ratio", Material.DIAMOND_SWORD, "kd_ratio"),
                Triple("Games Played", Material.GRASS_BLOCK, "games_played"),
                Triple("Win Rate", Material.EMERALD, "win_rate"),
                Triple("Playtime", Material.CLOCK, "playtime"),
                Triple("Level", Material.EXPERIENCE_BOTTLE, "level"),
                Triple("Pillars Destroyed", Material.TNT, "pillars_destroyed"),
                Triple("Current Win Streak", Material.FIRE_CHARGE, "win_streak")
            )

            var slot = 10
            leaderboards.forEach { (name, material, type) ->
                if (slot > 16) return@forEach

                val leaderboardItem = createLeaderboardCategoryItem(name, material, type, player.name)
                inv.setItem(slot, leaderboardItem)
                slot++
            }

            // Personal ranking overview
            val personalRanking = createPersonalRankingItem(player)
            inv.setItem(31, personalRanking)

        } catch (e: Exception) {
            plugin.logger.warning("Failed to load leaderboards: ${e.message}")
            player.sendMessage(MSG.chatColors("&cFailed to load leaderboards. Please try again later."))
        }

        createNavigationButtons(inv, showBack = true)
        player.openInventory(inv)
    }

    // =================== GAME HISTORY GUI ===================

    private fun openGameHistoryGUI(player: Player) {
        val inv = plugin.server.createInventory(player, 54, MSG.chatColors("$PLAYER_HISTORY_TITLE${player.displayName}"))
        activeMenus[player] = MenuType.GAME_HISTORY

        fillBorders(inv)

        try {
            val dbPlayerId = plugin.databaseManager.playersTable.getPlayerId(player.name)
            if (dbPlayerId == null) {
                player.sendMessage(MSG.chatColors("&cPlayer not found in database!"))
                return
            }

            val statsTable = plugin.databaseManager.statsTable
            val historyItems = createGameHistoryItems(dbPlayerId, statsTable)

            var slot = 10
            historyItems.forEach { item ->
                if (slot <= 43 && slot % 9 != 0 && slot % 9 != 8) {
                    inv.setItem(slot, item)
                    slot++
                    if (slot % 9 == 8) slot += 2 // Skip border columns
                }
            }

        } catch (e: Exception) {
            plugin.logger.warning("Failed to load game history for ${player.name}: ${e.message}")
            player.sendMessage(MSG.chatColors("&cFailed to load game history. Please try again later."))
        }

        createNavigationButtons(inv, showBack = true)
        player.openInventory(inv)
    }


    // =================== UTILITY METHODS ===================

    private fun fillBorders(inv: Inventory) {
        val borderGlass = ItemStack(Material.BLACK_STAINED_GLASS_PANE).apply {
            itemMeta = itemMeta?.apply { setDisplayName("") }
        }

        // Top and bottom borders
        for (i in 0..8) inv.setItem(i, borderGlass.clone())
        for (i in 45..53) inv.setItem(i, borderGlass.clone())

        // Side borders
        listOf(9, 17, 18, 26, 27, 35, 36, 44).forEach {
            inv.setItem(it, borderGlass.clone())
        }
    }

    private fun createNavigationButtons(inv: Inventory, showBack: Boolean = false) {
        // Close button
        val close = ItemStack(Material.BARRIER).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&c✖ Close"))
                lore = listOf(MSG.chatColors("&7Click to close this menu"))
            }
        }
        inv.setItem(CLOSE_BUTTON_SLOT, close)

        // Back button (conditional)
        if (showBack) {
            val back = ItemStack(Material.ARROW).apply {
                itemMeta = itemMeta?.apply {
                    setDisplayName(MSG.chatColors("&7← Back"))
                    lore = listOf(MSG.chatColors("&7Return to main statistics"))
                }
            }
            inv.setItem(BACK_BUTTON_SLOT, back)
        }

        // Refresh button
        val refresh = ItemStack(Material.COMPASS).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&a⟲ Refresh"))
                lore = listOf(MSG.chatColors("&7Click to refresh data"))
            }
        }
        inv.setItem(REFRESH_BUTTON_SLOT, refresh)

        // Additional navigation for main menu
        if (!showBack) {
            val achievements = ItemStack(Material.NETHER_STAR).apply {
                itemMeta = itemMeta?.apply {
                    setDisplayName(MSG.chatColors("&5✨ Achievements"))
                    lore = listOf(MSG.chatColors("&7View your unlocked achievements"))
                }
            }
            inv.setItem(ACHIEVEMENTS_BUTTON_SLOT, achievements)

            val leaderboards = ItemStack(Material.GOLDEN_APPLE).apply {
                itemMeta = itemMeta?.apply {
                    setDisplayName(MSG.chatColors("&e🏆 Leaderboards"))
                    lore = listOf(MSG.chatColors("&7View server rankings"))
                }
            }
            inv.setItem(LEADERBOARDS_BUTTON_SLOT, leaderboards)
        }
    }

    // =================== ITEM CREATION METHODS ===================

    private fun createOverviewStats(
        inv: Inventory,
        slot: Int,
        gameStats: GameStatsData,
        combatStats: CombatStatsData,
        progressStats: ProgressStatsData
    ) {
        val winRate = if (gameStats.gamesPlayed > 0)
            (gameStats.gamesWon * 100.0 / gameStats.gamesPlayed) else 0.0

        val kdr = if (combatStats.deaths > 0)
            (combatStats.kills.toDouble() / combatStats.deaths) else combatStats.kills.toDouble()

        val overview = ItemStack(Material.EMERALD).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&a⭐ Player Overview"))
                lore = listOf(
                    "",
                    MSG.chatColors("&7Level: &6${progressStats.level} &8(${formatNumber(progressStats.experience)} XP)"),
                    MSG.chatColors("&7Coins: &e${formatNumber(progressStats.coins)}"),
                    MSG.chatColors("&7Playtime: &b${formatTime(progressStats.playtime)}"),
                    "",
                    MSG.chatColors("&7Win Rate: &a${"%.1f".format(winRate)}%"),
                    MSG.chatColors("&7K/D Ratio: &c${"%.2f".format(kdr)}"),
                    MSG.chatColors("&7Games: &f${formatNumber(gameStats.gamesPlayed)} played"),
                    "",
                    MSG.chatColors("&8Click for more details")
                )
            }
        }
        inv.setItem(slot, overview)
    }

    private fun createPerformanceStats(
        inv: Inventory,
        slot: Int,
        gameStats: GameStatsData,
        combatStats: CombatStatsData
    ) {
        val avgKillsPerGame = if (gameStats.gamesPlayed > 0)
            combatStats.kills.toDouble() / gameStats.gamesPlayed else 0.0

        val avgDeathsPerGame = if (gameStats.gamesPlayed > 0)
            combatStats.deaths.toDouble() / gameStats.gamesPlayed else 0.0

        val performance = ItemStack(Material.DIAMOND_SWORD).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&c⚔ Combat Performance"))
                lore = listOf(
                    "",
                    MSG.chatColors("&7Total Kills: &a${formatNumber(combatStats.kills)}"),
                    MSG.chatColors("&7Total Deaths: &c${formatNumber(combatStats.deaths)}"),
                    MSG.chatColors("&7Assists: &e${formatNumber(combatStats.assists)}"),
                    "",
                    MSG.chatColors("&7Avg Kills/Game: &a${"%.1f".format(avgKillsPerGame)}"),
                    MSG.chatColors("&7Avg Deaths/Game: &c${"%.1f".format(avgDeathsPerGame)}"),
                    "",
                    MSG.chatColors("&7Best Killstreak: &6${formatNumber(combatStats.bestKillstreak)}"),
                    MSG.chatColors("&7Current Streak: &f${formatNumber(combatStats.currentKillstreak)}"),
                    ""
                )
            }
        }
        inv.setItem(slot, performance)
    }

    private fun createProgressStats(
        inv: Inventory,
        slot: Int,
        progressStats: ProgressStatsData
    ) {
        val nextLevelXP = calculateNextLevelXP(progressStats.level)
        val currentLevelXP = calculateLevelXP(progressStats.level - 1)
        val progressToNext = progressStats.experience - currentLevelXP
        val neededForNext = nextLevelXP - currentLevelXP
        val progressPercent = if (neededForNext > 0)
            (progressToNext * 100.0 / neededForNext).coerceIn(0.0, 100.0) else 100.0

        val progress = ItemStack(Material.EXPERIENCE_BOTTLE).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&d📈 Progress & Rewards"))
                lore = listOf(
                    "",
                    MSG.chatColors("&7Current Level: &6${progressStats.level}"),
                    MSG.chatColors("&7Experience: &b${formatNumber(progressStats.experience)} XP"),
                    MSG.chatColors("&7Progress: &a${"%.1f".format(progressPercent)}% &8to level ${progressStats.level + 1}"),
                    "",
                    MSG.chatColors("&7Coins: &e${formatNumber(progressStats.coins)}"),
                    MSG.chatColors("&7Total Playtime: &b${formatTime(progressStats.playtime)}"),
                    "",
                    MSG.chatColors("&8Next level: ${formatNumber(nextLevelXP - progressStats.experience)} XP needed"),
                    ""
                )
            }
        }
        inv.setItem(slot, progress)
    }

    private fun createBuildingStats(
        inv: Inventory,
        slot: Int,
        buildingStats: BuildingStatsData
    ) {
        val totalBlocks = buildingStats.blocksPlaced + buildingStats.blocksDestroyed

        val building = ItemStack(Material.CRAFTING_TABLE).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&6🔨 Building & Destruction"))
                lore = listOf(
                    "",
                    MSG.chatColors("&7Pillars Destroyed: &c${formatNumber(buildingStats.pillarsDestroyed)}"),
                    MSG.chatColors("&7Blocks Placed: &a${formatNumber(buildingStats.blocksPlaced)}"),
                    MSG.chatColors("&7Blocks Destroyed: &c${formatNumber(buildingStats.blocksDestroyed)}"),
                    MSG.chatColors("&7Total Blocks: &f${formatNumber(totalBlocks)}"),
                    "",
                    MSG.chatColors("&7Distance Moved: &b${formatDistance(buildingStats.distanceMoved)}"),
                    "",
                    MSG.chatColors("&8Building Activity Level: ${getBuildingLevel(totalBlocks)}"),
                    ""
                )
            }
        }
        inv.setItem(slot, building)
    }

    private fun createStreakStats(
        inv: Inventory,
        slot: Int,
        streakStats: StreakStatsData
    ) {
        val streak = ItemStack(Material.FIRE_CHARGE).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&e🔥 Streaks & Records"))
                lore = listOf(
                    "",
                    MSG.chatColors("&7Current Win Streak: &a${formatNumber(streakStats.currentWinStreak)}"),
                    MSG.chatColors("&7Best Win Streak: &6${formatNumber(streakStats.bestWinStreak)}"),
                    "",
                    MSG.chatColors("&7Longest Game: &b${formatTime(streakStats.longestGame)}"),
                    MSG.chatColors("&7Fastest Win: &a${streakStats.fastestWin?.let { formatTime(it) } ?: "None"}"),
                    "",
                    MSG.chatColors("&8Streak Status: ${getStreakStatus(streakStats.currentWinStreak)}"),
                    ""
                )
            }
        }
        inv.setItem(slot, streak)
    }

    private fun createTimeStats(
        inv: Inventory,
        slot: Int,
        progressStats: ProgressStatsData,
        streakStats: StreakStatsData
    ) {
        val avgSessionTime = if (progressStats.playtime > 0) progressStats.playtime / 20 else 0L

        val timeStats = ItemStack(Material.CLOCK).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&b⏰ Time Statistics"))
                lore = listOf(
                    "",
                    MSG.chatColors("&7Total Playtime: &a${formatTime(progressStats.playtime)}"),
                    MSG.chatColors("&7Est. Avg Session: &f${formatTime(avgSessionTime)}"),
                    "",
                    MSG.chatColors("&7Longest Game: &6${formatTime(streakStats.longestGame)}"),
                    MSG.chatColors("&7Fastest Win: &a${streakStats.fastestWin?.let { formatTime(it) } ?: "No wins yet"}"),
                    "",
                    MSG.chatColors("&8Play Activity: ${getActivityLevel(progressStats.playtime)}"),
                    ""
                )
            }
        }
        inv.setItem(slot, timeStats)
    }

    private fun createRankingStats(
        inv: Inventory,
        slot: Int,
        playerId: String,
        gameStats: GameStatsData,
        combatStats: CombatStatsData
    ) {
        // Calculate player's approximate rankings
        val winsRank = calculateWinsRank(gameStats.gamesWon)
        val kdrRank = calculateKDRRank(combatStats.kills, combatStats.deaths)
        val gamesRank = calculateGamesRank(gameStats.gamesPlayed)

        val ranking = ItemStack(Material.GOLDEN_APPLE).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&e🏆 Server Rankings"))
                lore = listOf(
                    "",
                    MSG.chatColors("&7Wins Rank: &6#${winsRank}"),
                    MSG.chatColors("&7K/D Rank: &c#${kdrRank}"),
                    MSG.chatColors("&7Games Rank: &f#${gamesRank}"),
                    "",
                    MSG.chatColors("&8Click for detailed leaderboards"),
                    ""
                )
            }
        }
        inv.setItem(slot, ranking)
    }

    private fun createAchievementPreview(inv: Inventory, slot: Int, playerId: String) {
        try {
            val achievementsTable = plugin.databaseManager.achievementsTable
            val unlocked = achievementsTable.getPlayerAchievements(playerId).size
            val totalAchievements = achievementsTable.getAllAchievements().size
            val completionRate = if (totalAchievements > 0) (unlocked * 100.0 / totalAchievements) else 0.0

            val preview = ItemStack(Material.NETHER_STAR).apply {
                itemMeta = itemMeta?.apply {
                    setDisplayName(MSG.chatColors("&5✨ Achievement Preview"))
                    lore = listOf(
                        "",
                        MSG.chatColors("&7Unlocked: &a$unlocked/$totalAchievements"),
                        MSG.chatColors("&7Completion: &6${"%.1f".format(completionRate)}%"),
                        "",
                        MSG.chatColors("&8Click to view all achievements")
                    )
                }
            }
            inv.setItem(slot, preview)
        } catch (e: Exception) {
            val fallback = ItemStack(Material.BOOK).apply {
                itemMeta = itemMeta?.apply {
                    setDisplayName(MSG.chatColors("&7Achievements"))
                    lore = listOf(MSG.chatColors("&cUnavailable"))
                }
            }
            inv.setItem(slot, fallback)
        }
    }

    // =================== ACHIEVEMENT SPECIFIC METHODS ===================

    private fun createAchievementOverviewItem(unlockedCount: Int, totalCount: Int, completionPercentage: Double): ItemStack {
        return ItemStack(Material.EMERALD_BLOCK).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&a✨ Achievement Overview"))
                lore = listOf(
                    "",
                    MSG.chatColors("&7Total Unlocked: &a${unlockedCount}/${totalCount}"),
                    MSG.chatColors("&7Completion Rate: &6${"%.1f".format(completionPercentage)}%"),
                    "",
                    MSG.chatColors("&8Progress towards 100% completion"),
                    ""
                )
            }
        }
    }

    private fun createCategoryItem(category: String, unlocked: Int, total: Int, achievements: List<Any>): ItemStack {
        val material = when (category.uppercase()) {
            "GENERAL" -> Material.GRASS_BLOCK
            "COMBAT" -> Material.DIAMOND_SWORD
            "BUILDING" -> Material.CRAFTING_TABLE
            "SURVIVAL" -> Material.GOLDEN_APPLE
            "SPECIAL" -> Material.NETHER_STAR
            "SEASONAL" -> Material.PUMPKIN
            else -> Material.STONE
        }

        val completionRate = if (total > 0) (unlocked * 100.0 / total) else 0.0

        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&6${category.lowercase().replaceFirstChar { it.uppercase() }} Achievements"))
                lore = listOf(
                    "",
                    MSG.chatColors("&7Unlocked: &a${unlocked}/${total}"),
                    MSG.chatColors("&7Completion: &6${"%.1f".format(completionRate)}%"),
                    "",
                    MSG.chatColors("&8Click to view category details"),
                    ""
                )
            }
        }
    }

    private fun createRecentUnlocksItem(recentUnlocks: List<Any>): ItemStack {
        return ItemStack(Material.CLOCK).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&b🕐 Recent Unlocks"))
                val loreList = mutableListOf<String>()
                loreList.add("")
                loreList.add(MSG.chatColors("&7Last ${recentUnlocks.size} achievements:"))
                loreList.add("")

                // This would need to be implemented with proper recent unlock data
                repeat(min(3, recentUnlocks.size)) { i ->
                    loreList.add(MSG.chatColors("&8• &aRecent Achievement #${i + 1}"))
                }

                loreList.add("")
                lore = loreList
            }
        }
    }

    private fun displayAchievements(inv: Inventory, achievements: List<Any>, playerAchievements: List<String>, startSlot: Int) {
        // This would display individual achievements in the inventory
        // Implementation depends on achievement data structure
        var currentSlot = startSlot
        val maxSlot = 43

        achievements.take(15).forEach { achievement ->
            if (currentSlot > maxSlot) return@forEach
            if (currentSlot % 9 == 0 || currentSlot % 9 == 8) {
                currentSlot++ // Skip border slots
                if (currentSlot % 9 == 0) currentSlot++ // Skip next border too
            }

            val achievementItem = ItemStack(Material.PAPER).apply {
                itemMeta = itemMeta?.apply {
                    setDisplayName(MSG.chatColors("&eAchievement"))
                    lore = listOf(MSG.chatColors("&7Achievement details here"))
                }
            }
            inv.setItem(currentSlot, achievementItem)
            currentSlot++
        }
    }

    // =================== LEADERBOARD METHODS ===================

    private fun createLeaderboardCategoryItem(name: String, material: Material, type: String, playerName: String): ItemStack {
        return ItemStack(material).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&6${name} Leaderboard"))
                lore = listOf(
                    "",
                    MSG.chatColors("&7Click to view top players"),
                    MSG.chatColors("&7in this category"),
                    "",
                    MSG.chatColors("&8Category: $type"),
                    ""
                )
            }
        }
    }

    private fun createPersonalRankingItem(player: Player): ItemStack {
        return ItemStack(Material.PLAYER_HEAD).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&b👤 Your Rankings"))
                lore = listOf(
                    "",
                    MSG.chatColors("&7Your position across"),
                    MSG.chatColors("&7different leaderboards"),
                    "",
                    MSG.chatColors("&8Click for detailed breakdown"),
                    ""
                )
            }
        }
    }

    // =================== GAME HISTORY METHODS ===================

    private fun createGameHistoryItems(playerId: String, statsTable: Any): List<ItemStack> {
        // Create summary items for game history
        val items = mutableListOf<ItemStack>()

        // Recent performance summary
        items.add(ItemStack(Material.BOOK).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&b📊 Recent Performance"))
                lore = listOf(
                    "",
                    MSG.chatColors("&7Performance over last 10 games"),
                    MSG.chatColors("&8Data would be shown here"),
                    ""
                )
            }
        })

        // Win/Loss trends
        items.add(ItemStack(Material.MAP).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&a📈 Win/Loss Trends"))
                lore = listOf(
                    "",
                    MSG.chatColors("&7Trends and patterns"),
                    MSG.chatColors("&7in your game results"),
                    ""
                )
            }
        })

        // Best performances
        items.add(ItemStack(Material.GOLD_INGOT).apply {
            itemMeta = itemMeta?.apply {
                setDisplayName(MSG.chatColors("&6🏆 Best Performances"))
                lore = listOf(
                    "",
                    MSG.chatColors("&7Your top game results"),
                    MSG.chatColors("&7and achievements"),
                    ""
                )
            }
        })

        return items
    }

    // =================== UTILITY EXTRACTION METHODS ===================

    private fun extractCategoryFromItem(item: ItemStack): String {
        return item.itemMeta?.displayName?.let { displayName ->
            when {
                displayName.contains("General") -> "General"
                displayName.contains("Combat") -> "Combat"
                displayName.contains("Building") -> "Building"
                displayName.contains("Survival") -> "Survival"
                displayName.contains("Special") -> "Special"
                displayName.contains("Seasonal") -> "Seasonal"
                else -> "Unknown"
            }
        } ?: "Unknown"
    }

    private fun extractLeaderboardTypeFromItem(item: ItemStack): String {
        return item.itemMeta?.displayName?.let { displayName ->
            when {
                displayName.contains("Wins") -> "Wins"
                displayName.contains("Kill/Death") -> "K/D Ratio"
                displayName.contains("Games") -> "Games Played"
                displayName.contains("Win Rate") -> "Win Rate"
                displayName.contains("Playtime") -> "Playtime"
                displayName.contains("Level") -> "Level"
                displayName.contains("Pillars") -> "Pillars Destroyed"
                displayName.contains("Streak") -> "Win Streak"
                else -> "Unknown"
            }
        } ?: "Unknown"
    }

    // =================== CALCULATION METHODS ===================

    private fun calculateWinsRank(wins: Int): Int {
        // Simplified ranking calculation
        try {
            val totalPlayers = plugin.databaseManager.statsTable.getTotalPlayersWithMinGames(1)
            return max(1, totalPlayers / max(1, wins))
        } catch (e: Exception) {
            return 1
        }
    }

    private fun calculateKDRRank(kills: Int, deaths: Int): Int {
        // Simplified K/D ranking calculation
        try {
            val totalPlayers = plugin.databaseManager.statsTable.getTotalPlayersWithMinDeaths(1)
            val kdr = if (deaths > 0) kills.toDouble() / deaths else kills.toDouble()
            return max(1, (totalPlayers * (1.0 - min(kdr / 10.0, 0.9))).toInt())
        } catch (e: Exception) {
            return 1
        }
    }

    private fun calculateGamesRank(games: Int): Int {
        // Simplified games played ranking
        try {
            val totalPlayers = plugin.databaseManager.statsTable.getTotalPlayersWithMinGames(1)
            return max(1, totalPlayers / max(1, games / 10))
        } catch (e: Exception) {
            return 1
        }
    }

    private fun calculateNextLevelXP(currentLevel: Int): Int {
        return currentLevel * currentLevel * 100
    }

    private fun calculateLevelXP(level: Int): Int {
        return if (level <= 0) 0 else (level - 1) * (level - 1) * 100
    }

    // =================== FORMATTING METHODS ===================

    private fun formatNumber(number: Int): String {
        return when {
            number >= 1_000_000 -> "${"%.1f".format(number / 1_000_000.0)}M"
            number >= 1_000 -> "${"%.1f".format(number / 1_000.0)}K"
            else -> number.toString()
        }
    }

    private fun formatTime(seconds: Long): String {
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            seconds < 86400 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
            else -> "${seconds / 86400}d ${(seconds % 86400) / 3600}h"
        }
    }

    private fun formatDistance(distance: Double): String {
        return when {
            distance >= 1000.0 -> "${"%.1f".format(distance / 1000.0)}km"
            else -> "${distance.toInt()}m"
        }
    }

    private fun getBuildingLevel(totalBlocks: Int): String {
        return when {
            totalBlocks >= 10000 -> "&6Master Builder"
            totalBlocks >= 5000 -> "&eExperienced Builder"
            totalBlocks >= 1000 -> "&aActive Builder"
            totalBlocks >= 100 -> "&7Casual Builder"
            else -> "&8Beginner"
        }
    }

    private fun getStreakStatus(streak: Int): String {
        return when {
            streak >= 10 -> "&6Legendary Streak!"
            streak >= 5 -> "&aHot Streak!"
            streak >= 3 -> "&eGood Streak"
            streak >= 1 -> "&7On a Roll"
            else -> "&8No Current Streak"
        }
    }

    private fun getActivityLevel(playtime: Long): String {
        val hours = playtime / 3600
        return when {
            hours >= 100 -> "&6Very Active"
            hours >= 50 -> "&aActive Player"
            hours >= 20 -> "&eCasual Player"
            hours >= 5 -> "&7New Player"
            else -> "&8Just Started"
        }
    }

    // =================== DATA CLASSES ===================

    data class GameStatsData(
        val gamesPlayed: Int,
        val gamesWon: Int,
        val gamesLost: Int,
        val gamesQuit: Int
    )

    data class CombatStatsData(
        val kills: Int,
        val deaths: Int,
        val assists: Int,
        val bestKillstreak: Int,
        val currentKillstreak: Int
    )

    data class ProgressStatsData(
        val level: Int,
        val experience: Int,
        val coins: Int,
        val playtime: Long
    )

    data class BuildingStatsData(
        val pillarsDestroyed: Int,
        val blocksPlaced: Int,
        val blocksDestroyed: Int,
        val distanceMoved: Double
    )

    data class StreakStatsData(
        val currentWinStreak: Int,
        val bestWinStreak: Int,
        val longestGame: Long,
        val fastestWin: Long?
    )
}