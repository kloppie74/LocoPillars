package org.Kloppie74.locoPillars.luckyblocks

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.config.ConfigManager
import org.Kloppie74.locoPillars.luckyblocks.engine.LuckyBlockConfig
import org.Kloppie74.locoPillars.luckyblocks.engine.LuckyBlockEngine
import org.Kloppie74.locoPillars.utils.MSG

class EngineLuckyBlocksCommand(private val config: ConfigManager, private val engine: LuckyBlockEngine) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): Boolean {
        if (sender !is Player) {
            sender.sendMessage("Only players can use this command!")
            return true
        }

        when {
            args.isEmpty() || args[0].equals("menu", ignoreCase = true) -> {
                if (!sender.hasPermission("locopillars.luckyblocks.menu")) {
                    sender.sendMessage("§cYou do not have permission to open the LuckyBlocks menu!")
                    return true
                }
                LuckyBlocksMenu.openMenu(config, sender)
                return true
            }

            args[0].equals("get", ignoreCase = true) -> {
                if (!sender.hasPermission("locopillars.luckyblocks.get")) {
                    sender.sendMessage("§cYou do not have permission to get LuckyBlocks!")
                    return true
                }

                val luckyBlock = if (args.size > 1) {
                    engine.getLuckyBlockItem(args[1]) ?: engine.getRandomLuckyBlockItem()
                } else {
                    engine.getRandomLuckyBlockItem()
                }

                if (luckyBlock != null) {
                    sender.inventory.addItem(luckyBlock)
                    sender.sendMessage("§aYou received a LuckyBlock!")
                } else {
                    sender.sendMessage("§cNo LuckyBlocks available!")
                }
                return true
            }

            args[0].equals("give", ignoreCase = true) -> {
                if (!sender.hasPermission("locopillars.luckyblocks.give")) {
                    sender.sendMessage("§cYou do not have permission to give LuckyBlocks!")
                    return true
                }

                if (args.size < 2) {
                    sender.sendMessage("§cUsage: /luckyblocks give <player> [id]")
                    return true
                }

                val targetPlayer = Bukkit.getPlayer(args[1])
                if (targetPlayer == null) {
                    sender.sendMessage("§cPlayer not found: ${args[1]}")
                    return true
                }

                val luckyBlock = if (args.size > 2) {
                    engine.getLuckyBlockItem(args[2]) ?: engine.getRandomLuckyBlockItem()
                } else {
                    engine.getRandomLuckyBlockItem()
                }

                if (luckyBlock != null) {
                    targetPlayer.inventory.addItem(luckyBlock)
                    targetPlayer.sendMessage("§aYou received a LuckyBlock!")
                    sender.sendMessage("§aGave LuckyBlock to ${targetPlayer.name}!")
                } else {
                    sender.sendMessage("§cNo LuckyBlocks available!")
                }
                return true
            }

            args[0].equals("list", ignoreCase = true) -> {
                if (!sender.hasPermission("locopillars.luckyblocks.list")) {
                    sender.sendMessage("§cYou do not have permission to list LuckyBlocks!")
                    return true
                }

                val configs = engine.getAllConfigs()
                sender.sendMessage("§6Available LuckyBlocks (${configs.size}):")
                configs.forEach { (id, config) ->
                    sender.sendMessage("§e- §f$id §7(${config.rarity}) §8- §7${config.description}")
                }
                return true
            }

            args[0].equals("reload", ignoreCase = true) -> {
                if (!sender.hasPermission("locopillars.luckyblocks.reload")) {
                    sender.sendMessage("§cYou do not have permission to reload LuckyBlocks!")
                    return true
                }

                engine.reloadConfigs()
                sender.sendMessage("§aLuckyBlocks configurations reloaded!")
                return true
            }

            args[0].equals("getblock", ignoreCase = true) -> {
                if (!sender.hasPermission("locopillars.luckyblocks.getblock")) {
                    sender.sendMessage("§cYou do not have permission to get LuckyBlock heads!")
                    return true
                }

                // Get a traditional LuckyBlock head from Utils (not engine item)
                val luckyBlockHead = LuckyBlocksUtil.getLuckyBlock(config, sender)
                sender.inventory.addItem(luckyBlockHead)
                sender.sendMessage("§aYou received a traditional LuckyBlock head!")
                return true
            }

            args[0].equals("info", ignoreCase = true) -> {
                if (args.size < 2) {
                    sender.sendMessage("§cUsage: /luckyblocks info <id>")
                    return true
                }

                val config = engine.getAllConfigs()[args[1]]
                if (config == null) {
                    sender.sendMessage("§cLuckyBlock not found: ${args[1]}")
                    return true
                }

                sender.sendMessage("§6LuckyBlock Info: §e${config.displayName}")
                sender.sendMessage("§7ID: §f${config.id}")
                sender.sendMessage("§7Description: §f${config.description}")
                sender.sendMessage("§7Rarity: §f${config.rarity}")
                sender.sendMessage("§7Effects: §f${config.effects.size}")
                sender.sendMessage("§7Conditions: §f${config.conditions.size}")
                sender.sendMessage("§7Filters: §f${config.filters.size}")
                sender.sendMessage("§7Triggers: §f${config.triggers.size}")

                return true
            }

            else -> {
                sender.sendMessage("§cUsage:")
                sender.sendMessage("§7/luckyblocks menu §8- Open the LuckyBlocks menu")
                sender.sendMessage("§7/luckyblocks get [id] §8- Get a LuckyBlock")
                sender.sendMessage("§7/luckyblocks getblock §8- Get a traditional LuckyBlock head")
                sender.sendMessage("§7/luckyblocks give <player> [id] §8- Give a LuckyBlock")
                sender.sendMessage("§7/luckyblocks list §8- List all LuckyBlocks")
                sender.sendMessage("§7/luckyblocks info <id> §8- Show LuckyBlock info")
                sender.sendMessage("§7/luckyblocks reload §8- Reload configurations")
                return true
            }
        }
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("menu", "get", "getblock", "give", "list", "info", "reload")
                .filter { it.startsWith(args[0], ignoreCase = true) }
        }

        if (args.size == 2) {
            when (args[0].lowercase()) {
                "give" -> {
                    return Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                }
                "get", "info" -> {
                    return engine.getAllConfigs().keys
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                }
            }
        }

        if (args.size == 3 && args[0].equals("give", ignoreCase = true)) {
            return engine.getAllConfigs().keys
                .filter { it.startsWith(args[2], ignoreCase = true) }
        }

        return emptyList()
    }
}

// Updated menu system to work with the engine
object EngineLuckyBlocksMenu {

    fun openMenu(engine: LuckyBlockEngine, player: Player, page: Int = 0) {
        val configs = engine.getAllConfigs().values.toList().sortedBy { it.displayName }
        val menuTitle = "LuckyBlocks Engine"
        val rows = 6
        val cols = 9
        val blocksPerPage = (rows - 2) * (cols - 2) // 7x4 = 28

        val maxPage = kotlin.math.max(0, ((configs.size - 1) / blocksPerPage))
        val currentPage = kotlin.math.min(kotlin.math.max(page, 0), maxPage)

        val inv = Bukkit.createInventory(null, rows * cols, "$menuTitle - Page ${currentPage + 1}/${maxPage + 1}")

        // Fill borders
        for (slot in 0 until rows * cols) {
            if (isBorder(slot, rows, cols)) {
                val item = org.bukkit.inventory.ItemStack(org.bukkit.Material.GRAY_STAINED_GLASS_PANE)
                val meta = item.itemMeta
                meta?.setDisplayName(" ")
                item.itemMeta = meta
                inv.setItem(slot, item)
            }
        }

        // Add LuckyBlocks
        val startIndex = currentPage * blocksPerPage
        val endIndex = kotlin.math.min(startIndex + blocksPerPage, configs.size)
        var blockSlot = 0

        for (i in startIndex until endIndex) {
            val config = configs[i]
            val slot = getSlotFromIndex(blockSlot, cols)
            val item = createMenuItemFromConfig(config, engine)
            inv.setItem(slot, item)
            blockSlot++
        }

        // Navigation arrows
        if (currentPage > 0) {
            val leftArrow = org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW)
            val leftMeta = leftArrow.itemMeta
            leftMeta?.setDisplayName(MSG.chatColors("&bPrevious Page"))
            leftArrow.itemMeta = leftMeta
            inv.setItem(getArrowLeftSlot(rows, cols), leftArrow)
        }

        if (currentPage < maxPage) {
            val rightArrow = org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW)
            val rightMeta = rightArrow.itemMeta
            rightMeta?.setDisplayName(MSG.chatColors("&bNext Page"))
            rightArrow.itemMeta = rightMeta
            inv.setItem(getArrowRightSlot(rows, cols), rightArrow)
        }

        player.openInventory(inv)
    }

    private fun createMenuItemFromConfig(config: LuckyBlockConfig, engine: LuckyBlockEngine): org.bukkit.inventory.ItemStack {
        val materialName = config.icon.material.uppercase()
        val material = try {
            org.bukkit.Material.valueOf(materialName)
        } catch (e: Exception) {
            org.bukkit.Material.PLAYER_HEAD
        }

        val item = org.bukkit.inventory.ItemStack(material)
        val meta = item.itemMeta ?: return item

        // Set custom model data if present
        config.icon.customModelData?.let { customModelData ->
            try {
                meta.setCustomModelData(customModelData)
            } catch (e: Exception) {
                // Ignore if not supported
            }
        }

        meta.setDisplayName(MSG.chatColors("&e${config.displayName} &7[${config.rarity}]"))

        val lore = mutableListOf<String>()
        if (config.description.isNotBlank()) {
            lore.add(MSG.chatColors("&f${config.description}"))
        }
        lore.add("")
        lore.add(MSG.chatColors("&8Rarity: &b${config.rarity}"))
        lore.add(MSG.chatColors("&8Effects: &7${config.effects.size}"))
        lore.add(MSG.chatColors("&8Conditions: &7${config.conditions.size}"))
        lore.add(MSG.chatColors("&8Filters: &7${config.filters.size}"))
        lore.add(MSG.chatColors("&8Triggers: &7${config.triggers.size}"))
        lore.add("")
        lore.add(MSG.chatColors("&eClick to get this LuckyBlock!"))
        lore.add(MSG.chatColors("&8ID: &7${config.id}"))

        meta.lore = lore

        // Store the config ID for click handling
        meta.persistentDataContainer.set(
            org.bukkit.NamespacedKey.minecraft("luckyblock_config_id"),
            org.bukkit.persistence.PersistentDataType.STRING,
            config.id
        )

        item.itemMeta = meta
        return item
    }

    private fun isBorder(slot: Int, rows: Int, cols: Int): Boolean {
        val row = slot / cols
        val col = slot % cols
        return row == 0 || row == rows - 1 || col == 0 || col == cols - 1
    }

    private fun getSlotFromIndex(index: Int, cols: Int): Int {
        val row = index / 7 + 1
        val col = index % 7 + 1
        return row * cols + col
    }

    private fun getArrowLeftSlot(rows: Int, cols: Int): Int = ((rows / 2) * cols)
    private fun getArrowRightSlot(rows: Int, cols: Int): Int = ((rows / 2) * cols) + cols - 1
}

// Integration helper for existing codebase
object LuckyBlockEngineIntegration {

    fun initializeEngine(plugin: org.bukkit.plugin.Plugin, configManager: ConfigManager): LuckyBlockEngine {
        val engine = LuckyBlockEngine(plugin, configManager)
        engine.initialize()
        return engine
    }

    fun registerEngineListeners(plugin: org.bukkit.plugin.Plugin, engine: LuckyBlockEngine) {
        Bukkit.getPluginManager().registerEvents(engine, plugin)
    }

    fun createEngineCommand(configManager: ConfigManager, engine: LuckyBlockEngine): EngineLuckyBlocksCommand {
        return EngineLuckyBlocksCommand(configManager, engine)
    }

    fun convertOldLuckyBlockItem(oldItem: org.bukkit.inventory.ItemStack, engine: LuckyBlockEngine): org.bukkit.inventory.ItemStack? {
        // Convert old-style LuckyBlock items to new engine format
        if (!LuckyBlocksUtil.isLuckyBlockItem(oldItem)) return null

        // Get a random new LuckyBlock to replace the old one
        return engine.getRandomLuckyBlockItem()
    }

    fun migrateOldLuckyBlocks(engine: LuckyBlockEngine) {
        // Migration utility to convert old data to new format
        val plugin = engine.javaClass.getDeclaredField("plugin").let { field ->
            field.isAccessible = true
            field.get(engine) as org.bukkit.plugin.Plugin
        }

        plugin.logger.info("[LuckyBlockEngine] Starting migration from old LuckyBlock system...")

        // Clear old cache if needed
        val placedBlocks = LuckyBlocksUtil.getPlacedLuckyBlocksCache()
        plugin.logger.info("[LuckyBlockEngine] Found ${placedBlocks.size} placed LuckyBlocks to migrate")

        // The placed blocks cache is still valid and will work with the new engine
        plugin.logger.info("[LuckyBlockEngine] Migration completed successfully!")
    }
}

// Example configurations for quick setup
object LuckyBlockExamples {

    fun createExampleConfigs(): List<String> {
        return listOf(
            createFireworkExample(),
            createTreasureExample(),
            createCurseExample(),
            createTeleportExample(),
            createMobSpawnExample()
        )
    }

    private fun createFireworkExample(): String {
        return """
id: firework_celebration
display_name: "Firework Celebration"
description: "Launches beautiful fireworks into the sky!"
rarity: "COMMON"
icon:
  material: "FIREWORK_ROCKET"
  custom_model_data: 1
block_type: "LUCKY_BLOCK"
effects:
  - type: "SUMMON_FIREWORKS"
    amount: 15
    colors: ["RED", "BLUE", "GREEN", "YELLOW", "PURPLE"]
    power: 3
    message: "&dCelebrate with fireworks! 🎆"
  - type: "PLAY_SOUND"
    sound: "ENTITY_FIREWORK_ROCKET_LAUNCH"
    volume: 1.0
    pitch: 1.2
conditions:
  - player_y: ">60"
filters:
  - time: "NIGHT"
        """.trimIndent()
    }

    private fun createTreasureExample(): String {
        return """
id: treasure_chest
display_name: "Treasure Chest"
description: "Gives valuable items to lucky players!"
rarity: "UNCOMMON"
icon:
  material: "CHEST"
  custom_model_data: 2
block_type: "LUCKY_BLOCK"
effects:
  - type: "GIVE_ITEM"
    material: "DIAMOND"
    amount: 5
    name: "&bLucky Diamond"
    lore: 
      - "&7Found in a Lucky Treasure Chest!"
    message: "&aYou found diamonds! 💎"
  - type: "GIVE_ITEM"
    material: "EMERALD"
    amount: 3
    name: "&aLucky Emerald"
    message: "&aEmeralds for the lucky!"
  - type: "GIVE_XP"
    amount: 250
conditions:
  - player_health: ">=10"
  - has_not: "DIAMOND"
filters:
  - biome: "!NETHER"
  - random_chance: "<0.3"
        """.trimIndent()
    }

    private fun createCurseExample(): String {
        return """
id: cursed_block
display_name: "Cursed Block"
description: "Beware! This block brings misfortune..."
rarity: "RARE"
icon:
  material: "OBSIDIAN"
  custom_model_data: 3
block_type: "LUCKY_BLOCK"
effects:
  - type: "GIVE_EFFECT"
    effect: "WITHER"
    duration: 100
    amplifier: 1
    message: "&cYou have been cursed! 💀"
  - type: "SPAWN_MOB"
    mob_type: "ZOMBIE"
    amount: 3
    radius: 5
    aggro: true
  - type: "SET_FIRE"
    duration: 60
    radius: 3
conditions:
  - time: "NIGHT"
  - player_health: ">5"
filters:
  - weather: "!CLEAR"
  - random_chance: "<0.15"
triggers:
  - event: "ITEM_RIGHT_CLICK"
    item_name: "&cCursed Wand"
    effect:
      type: "CAST_SPELL"
      spell: ["WITHER", "BLINDNESS", "POISON"]
      radius: 8
      message: "&cDark magic spreads..."
        """.trimIndent()
    }

    private fun createTeleportExample(): String {
        return """
id: teleport_surprise
display_name: "Teleport Surprise"
description: "Teleports you to an unexpected location!"
rarity: "UNCOMMON"
icon:
  material: "ENDER_PEARL"
  custom_model_data: 4
block_type: "LUCKY_BLOCK"
effects:
  - type: "TELEPORT"
    radius: 100
    message: "&dWhoosh! Where are you now? ✨"
  - type: "GIVE_EFFECT"
    effect: "SLOWFALL"
    duration: 200
    amplifier: 0
  - type: "SUMMON_PARTICLES"
    type: "PORTAL"
    amount: 100
conditions:
  - is_on_team: "!NONE"
filters:
  - world: "!nether"
  - world: "!end"
        """.trimIndent()
    }

    private fun createMobSpawnExample(): String {
        return """
id: mob_invasion
display_name: "Mob Invasion"
description: "Spawns various mobs around you!"
rarity: "LEGENDARY"
icon:
  material: "ZOMBIE_HEAD"
  custom_model_data: 5
block_type: "LUCKY_BLOCK"
effects:
  - type: "SPAWN_MOB"
    mob_type: "ZOMBIE"
    amount: 5
    radius: 8
    aggro: false
    message: "&cMob invasion incoming! 🧟"
  - type: "SPAWN_MOB"
    mob_type: "SKELETON"
    amount: 3
    radius: 8
    aggro: false
  - type: "SPAWN_MOB"
    mob_type: "CREEPER"
    amount: 2
    radius: 8
    aggro: false
  - type: "PLAY_SOUND"
    sound: "ENTITY_ZOMBIE_AMBIENT"
    volume: 2.0
    pitch: 0.8
conditions:
  - player_y: ">50"
  - is_in_combat: "false"
filters:
  - biome: "!PEACEFUL"
  - time: "NIGHT"
  - random_chance: "<0.05"
        """.trimIndent()
    }
}

// Utility class for debugging and testing
object LuckyBlockDebugger {

    fun debugPlayer(player: org.bukkit.entity.Player, engine: LuckyBlockEngine): List<String> {
        val debug = mutableListOf<String>()

        debug.add("=== LuckyBlock Debug Info ===")
        debug.add("Player: ${player.name}")
        debug.add("Location: ${player.location.blockX}, ${player.location.blockY}, ${player.location.blockZ}")
        debug.add("World: ${player.world.name}")
        debug.add("Biome: ${player.location.block.biome}")
        debug.add("Health: ${player.health}/${player.maxHealth}")
        debug.add("Is Sneaking: ${player.isSneaking}")
        debug.add("Is Flying: ${player.isFlying}")
        debug.add("Time: ${player.world.time}")
        debug.add("Weather: ${getWeatherString(player.world)}")
        debug.add("")
        debug.add("=== Available LuckyBlocks ===")

        val configs = engine.getAllConfigs()
        configs.forEach { (id, config) ->
            debug.add("$id: ${config.displayName} (${config.rarity})")

            // Check conditions
            val conditionsPassed = config.conditions.all { condition ->
                engine.getConditionRegistry().checkCondition(player, condition.type, condition.value)
            }
            debug.add("  Conditions: ${if (conditionsPassed) "✓ PASS" else "✗ FAIL"}")

            // Check filters
            val filtersPassed = config.filters.all { filter ->
                engine.getFilterRegistry().checkFilter(player, filter.type, filter.value)
            }
            debug.add("  Filters: ${if (filtersPassed) "✓ PASS" else "✗ FAIL"}")

            debug.add("  Overall: ${if (conditionsPassed && filtersPassed) "✓ ELIGIBLE" else "✗ NOT ELIGIBLE"}")
            debug.add("")
        }

        return debug
    }

    private fun getWeatherString(world: org.bukkit.World): String {
        return when {
            !world.hasStorm() && !world.isThundering -> "CLEAR"
            world.hasStorm() && !world.isThundering -> "RAIN"
            world.hasStorm() && world.isThundering -> "THUNDER"
            else -> "UNKNOWN"
        }
    }

    fun testLuckyBlock(player: org.bukkit.entity.Player, engine: LuckyBlockEngine, luckyBlockId: String): Boolean {
        val config = engine.getAllConfigs()[luckyBlockId]
        if (config == null) {
            player.sendMessage("§cLuckyBlock not found: $luckyBlockId")
            return false
        }

        player.sendMessage("§6Testing LuckyBlock: §e${config.displayName}")

        // Check filters
        val filtersPassed = config.filters.all { filter ->
            val result = engine.getFilterRegistry().checkFilter(player, filter.type, filter.value)
            player.sendMessage("§7Filter ${filter.type}=${filter.value}: ${if (result) "§a✓" else "§c✗"}")
            result
        }

        // Check conditions
        val conditionsPassed = config.conditions.all { condition ->
            val result = engine.getConditionRegistry().checkCondition(player, condition.type, condition.value)
            player.sendMessage("§7Condition ${condition.type}=${condition.value}: ${if (result) "§a✓" else "§c✗"}")
            result
        }

        if (filtersPassed && conditionsPassed) {
            player.sendMessage("§a✓ All checks passed! Executing effects...")

            // Execute effects
            config.effects.forEach { effect ->
                try {
                    engine.getEffectRegistry().executeEffect(player, player.location, effect.type, effect.properties)
                    player.sendMessage("§7Effect ${effect.type}: §a✓")
                } catch (e: Exception) {
                    player.sendMessage("§7Effect ${effect.type}: §c✗ ${e.message}")
                }
            }

            return true
        } else {
            player.sendMessage("§c✗ Checks failed. LuckyBlock will not trigger.")
            return false
        }
    }
}