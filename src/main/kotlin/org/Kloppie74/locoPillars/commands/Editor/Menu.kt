package org.Kloppie74.locoPillars.commands.Editor

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.event.EventHandler
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.config.GameConfig
import java.util.*

object Menu : Listener {

    private val waitingMapName = mutableSetOf<UUID>()
    private val playerMapAction = mutableMapOf<UUID, MapAction>()
    private val playerCurrentMenu = mutableMapOf<UUID, MenuType>()
    private val playerEditingConfig = mutableMapOf<UUID, String>()
    private val waitingForValue = mutableMapOf<UUID, ConfigSetting>()

    enum class MenuType {
        MAIN, MAP_SELECTOR, CONFIG_EDITOR, TEAM_EDITOR, SPAWN_EDITOR, SETTINGS_EDITOR
    }

    enum class MapAction(val displayName: String) {
        EDIT("edit"),
        DELETE("delete"),
        CONFIG("configure")
    }

    data class ConfigSetting(
        val configName: String,
        val key: String,
        val displayName: String,
        val currentValue: Any?,
        val valueType: ValueType
    )

    enum class ValueType {
        INTEGER, DOUBLE, STRING, BOOLEAN
    }

    fun openMainMenu(player: Player) {
        playerCurrentMenu[player.uniqueId] = MenuType.MAIN
        val inv = Bukkit.createInventory(player, 45, MSG.chatColors("&8✦ &bPillars Admin Menu &8✦"))

        // Decorative border
        val border = makeItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", listOf())
        val borderSlots = arrayOf(0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,37,38,39,40,41,42,43,44)
        borderSlots.forEach { inv.setItem(it, border) }

        // Main menu buttons
        inv.setItem(20, makeMenuButton(Material.GREEN_WOOL, "&aCreate New Map", listOf(
            "&7Start creating a new arena map.",
            "&7You will be prompted for a map name.",
            "&7A new void world and config will be generated."
        )))

        inv.setItem(22, makeMenuButton(Material.PAPER, "&bEdit Map", listOf(
            "&7Edit an existing map.",
            "&7Choose from all available maps.",
            "&7Teleport to edit world and paste schematic."
        )))

        inv.setItem(24, makeMenuButton(Material.WRITABLE_BOOK, "&eConfig Editor", listOf(
            "&7Edit map configurations.",
            "&7Modify game settings, spawn points,",
            "&7team configurations and more."
        )))

        inv.setItem(29, makeMenuButton(Material.RED_WOOL, "&cDelete Map", listOf(
            "&7Delete an existing map.",
            "&7Removes config and all schematics.",
            "&7Cannot be undone!"
        )))

        inv.setItem(31, makeMenuButton(Material.BOOK, "&eList Maps", listOf(
            "&7View all available maps.",
            "&7Shows both config and schematic folders."
        )))

        inv.setItem(33, makeMenuButton(Material.COMPASS, "&dPosition Tool", listOf(
            "&7Get the position selection tool.",
            "&7Use to set spawn points and regions."
        )))

        inv.setItem(40, makeMenuButton(Material.BARRIER, "&cClose", listOf("&7Exit this menu.")))

        player.openInventory(inv)
    }

    fun openMapSelector(player: Player, action: MapAction) {
        playerCurrentMenu[player.uniqueId] = MenuType.MAP_SELECTOR
        playerMapAction[player.uniqueId] = action

        val maps = MapManager.getAllMapNames()
        val inv = Bukkit.createInventory(player, 54, MSG.chatColors("&8✦ &bSelect Map &8✦"))

        val icon = when (action) {
            MapAction.EDIT -> Material.PAPER
            MapAction.DELETE -> Material.RED_WOOL
            MapAction.CONFIG -> Material.WRITABLE_BOOK
        }

        var slot = 10
        maps.forEach { map ->
            if (slot < 54) {
                inv.setItem(slot, makeMenuButton(icon, "&f$map", listOf(
                    "&7Click to ${action.displayName} this map."
                )))
                slot++
                if (slot == 17) slot = 19
                if (slot == 26) slot = 28
                if (slot == 35) slot = 37
                if (slot == 44) slot = 46
            }
        }

        inv.setItem(49, makeMenuButton(Material.BARRIER, "&cBack", listOf("&7Return to Admin Menu.")))

        // Decorative border
        val border = makeItem(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", listOf())
        val borderSlots = arrayOf(0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,44,45,46,47,48,50,51,52,53)
        borderSlots.forEach { slot ->
            if (inv.getItem(slot) == null) inv.setItem(slot, border)
        }

        player.openInventory(inv)
    }

    fun openConfigEditor(player: Player, mapName: String) {
        playerCurrentMenu[player.uniqueId] = MenuType.CONFIG_EDITOR
        playerEditingConfig[player.uniqueId] = mapName

        val inv = Bukkit.createInventory(player, 54, MSG.chatColors("&8✦ &eConfig Editor: &f$mapName &8✦"))

        // Load current config
        val config = MapManager.getMapConfig(mapName)

        // Settings section
        inv.setItem(11, makeMenuButton(Material.GOLDEN_APPLE, "&eGame Settings", listOf(
            "&7Edit basic game settings like",
            "&7timers, height limits, and rules."
        )))

        inv.setItem(13, makeMenuButton(Material.PLAYER_HEAD, "&bSpawn Configuration", listOf(
            "&7Configure spawn locations,",
            "&7player limits, and spawn areas."
        )))

        inv.setItem(15, makeMenuButton(Material.WHITE_BANNER, "&dTeam Configuration", listOf(
            "&7Set up teams, colors, and",
            "&7individual player spawn points."
        )))

        inv.setItem(29, makeMenuButton(Material.CHEST, "&aItem Configuration", listOf(
            "&7Configure loot tables and",
            "&7item distribution settings."
        )))

        inv.setItem(31, makeMenuButton(Material.DIAMOND_PICKAXE, "&9World Settings", listOf(
            "&7Edit world-specific settings",
            "&7like build areas and restrictions."
        )))

        inv.setItem(33, makeMenuButton(Material.CLOCK, "&6Timer Settings", listOf(
            "&7Configure all game timers",
            "&7including lobby, cage, and game time."
        )))

        inv.setItem(40, makeMenuButton(Material.EMERALD, "&aSave Config", listOf(
            "&7Save all changes to file."
        )))

        inv.setItem(44, makeMenuButton(Material.BARRIER, "&cBack", listOf("&7Return to map selection.")))

        // Decorative border
        val border = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", listOf())
        val borderSlots = arrayOf(0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,37,38,39,41,42,43,45,46,47,48,49,50,51,52,53)
        borderSlots.forEach { inv.setItem(it, border) }

        player.openInventory(inv)
    }

    fun openSettingsEditor(player: Player, mapName: String) {
        playerCurrentMenu[player.uniqueId] = MenuType.SETTINGS_EDITOR

        val inv = Bukkit.createInventory(player, 54, MSG.chatColors("&8✦ &eGame Settings: &f$mapName &8✦"))
        val config = MapManager.getMapConfig(mapName)

        // Display Name
        inv.setItem(10, makeConfigButton(
            Material.NAME_TAG, "&eDisplay Name",
            config?.getDisplayName() ?: mapName,
            "&7The display name shown to players"
        ))

        // Build Height
        inv.setItem(11, makeConfigButton(
            Material.LADDER, "&eBuild Height",
            config?.getBuildHeight() ?: 60,
            "&7Maximum build height for players"
        ))

        // Build Range
        inv.setItem(12, makeConfigButton(
            Material.WOODEN_PICKAXE, "&eBuild Range",
            config?.getBuildRange() ?: 40,
            "&7Maximum build range from spawn"
        ))

        // Death Height
        inv.setItem(13, makeConfigButton(
            Material.SKELETON_SKULL, "&cDeath Height",
            config?.getDeathHeight() ?: -5,
            "&7Y level where players die from falling"
        ))

        // Min Players
        inv.setItem(19, makeConfigButton(
            Material.GREEN_DYE, "&aMinimum Players",
            config?.getMinPlayers() ?: 4,
            "&7Minimum players to start a game"
        ))

        // Max Players
        inv.setItem(20, makeConfigButton(
            Material.RED_DYE, "&cMaximum Players",
            config?.getMaxPlayers() ?: 8,
            "&7Maximum players in a game"
        ))

        // Max Teams
        inv.setItem(21, makeConfigButton(
            Material.WHITE_BANNER, "&dMax Teams",
            config?.getMaxTeams() ?: 8,
            "&7Maximum number of teams"
        ))

        // Players Per Team
        inv.setItem(22, makeConfigButton(
            Material.PLAYER_HEAD, "&bPlayers Per Team",
            config?.getPlayersPerTeam() ?: 1,
            "&7Maximum players per team"
        ))

        // Can Break Map Blocks
        inv.setItem(31, makeConfigButton(
            Material.TNT, "&6Break Map Blocks",
            config?.canBreakMapBlocks() ?: false,
            "&7Allow players to break map blocks"
        ))

        inv.setItem(49, makeMenuButton(Material.BARRIER, "&cBack", listOf("&7Return to config editor.")))

        // Decorative border
        val border = makeItem(Material.ORANGE_STAINED_GLASS_PANE, " ", listOf())
        val borderSlots = arrayOf(0,1,2,3,4,5,6,7,8,9,17,18,26,27,35,36,37,38,39,40,41,42,43,44,45,46,47,48,50,51,52,53)
        borderSlots.forEach { inv.setItem(it, border) }

        player.openInventory(inv)
    }

    fun openTimerSettings(player: Player, mapName: String) {
        val inv = Bukkit.createInventory(player, 36, MSG.chatColors("&8✦ &6Timer Settings: &f$mapName &8✦"))
        val config = MapManager.getMapConfig(mapName)

        // Lobby Timer
        inv.setItem(10, makeConfigButton(
            Material.CLOCK, "&eLobby Timer",
            config?.getLobbyTimer() ?: 10,
            "&7Seconds to wait in lobby before start"
        ))

        // Cage Timer
        inv.setItem(12, makeConfigButton(
            Material.IRON_BARS, "&eCage Timer",
            config?.getCageTimer() ?: 5,
            "&7Seconds players stay in cages"
        ))

        // Game Timer
        inv.setItem(14, makeConfigButton(
            Material.GOLDEN_APPLE, "&eGame Timer",
            config?.getGameTimer() ?: 900,
            "&7Total game time in seconds (900 = 15 min)"
        ))

        // Item Timer
        inv.setItem(16, makeConfigButton(
            Material.CHEST, "&eItem Timer",
            config?.getItemTimer() ?: 5,
            "&7Seconds between item spawns"
        ))

        inv.setItem(31, makeMenuButton(Material.BARRIER, "&cBack", listOf("&7Return to config editor.")))

        // Decorative border
        val border = makeItem(Material.YELLOW_STAINED_GLASS_PANE, " ", listOf())
        val borderSlots = arrayOf(0,1,2,3,4,5,6,7,8,9,17,18,26,27,28,29,30,32,33,34,35)
        borderSlots.forEach { inv.setItem(it, border) }

        player.openInventory(inv)
    }

    private fun makeConfigButton(material: Material, name: String, currentValue: Any, description: String): ItemStack {
        val valueText = when (currentValue) {
            is Boolean -> if (currentValue) "&aEnabled" else "&cDisabled"
            else -> "&f$currentValue"
        }

        return makeMenuButton(material, name, listOf(
            description,
            "",
            "&7Current Value: $valueText",
            "&eClick to change!"
        ))
    }

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val title = event.view.title
        event.isCancelled = true

        when {
            title == MSG.chatColors("&8✦ &bPillars Admin Menu &8✦") -> handleMainMenu(player, event.slot)
            title == MSG.chatColors("&8✦ &bSelect Map &8✦") -> handleMapSelector(player, event)
            title.startsWith(MSG.chatColors("&8✦ &eConfig Editor:")) -> handleConfigEditor(player, event.slot)
            title.startsWith(MSG.chatColors("&8✦ &eGame Settings:")) -> handleSettingsEditor(player, event)
            title.startsWith(MSG.chatColors("&8✦ &6Timer Settings:")) -> handleTimerSettings(player, event)
        }
    }

    private fun handleMainMenu(player: Player, slot: Int) {
        when (slot) {
            20 -> {
                player.closeInventory()
                player.sendMessage(MSG.chatColors("&aType the name for your new map in chat!"))
                waitingMapName.add(player.uniqueId)
            }
            22 -> {
                player.closeInventory()
                openMapSelector(player, MapAction.EDIT)
            }
            24 -> {
                player.closeInventory()
                openMapSelector(player, MapAction.CONFIG)
            }
            29 -> {
                player.closeInventory()
                openMapSelector(player, MapAction.DELETE)
            }
            31 -> {
                val maps = MapManager.getAllMapNames()
                player.sendMessage(MSG.chatColors("&eAvailable maps: &7${if (maps.isEmpty()) "none" else maps.joinToString(", ")}"))
            }
            33 -> {
                player.closeInventory()
                PositionTool.givePositionTool(player)
            }
            40 -> player.closeInventory()
        }
    }

    private fun handleMapSelector(player: Player, event: InventoryClickEvent) {
        if (event.slot == 49) {
            player.closeInventory()
            openMainMenu(player)
            playerMapAction.remove(player.uniqueId)
            return
        }

        val clicked = event.currentItem ?: return
        val mapName = clicked.itemMeta?.displayName?.replace("§f", "") ?: return
        val action = playerMapAction[player.uniqueId] ?: return

        if (!MapManager.getAllMapNames().contains(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' not found!"))
            return
        }

        when (action) {
            MapAction.EDIT -> {
                player.sendMessage(MSG.chatColors("&aTeleporting you to edit map: &f$mapName"))
                MapManager.teleportAndPasteSchematic(player, mapName)
            }
            MapAction.DELETE -> {
                MapManager.deleteMap(mapName)
                player.sendMessage(MSG.chatColors("&cMap '$mapName' has been deleted!"))
                openMainMenu(player)
            }
            MapAction.CONFIG -> {
                openConfigEditor(player, mapName)
            }
        }
        player.closeInventory()
        playerMapAction.remove(player.uniqueId)
    }

    private fun handleConfigEditor(player: Player, slot: Int) {
        val mapName = playerEditingConfig[player.uniqueId] ?: return

        when (slot) {
            11 -> openSettingsEditor(player, mapName)
            13 -> {
                player.sendMessage(MSG.chatColors("&eSpawn configuration coming soon!"))
                // TODO: Implement spawn editor
            }
            15 -> {
                player.sendMessage(MSG.chatColors("&eTeam configuration coming soon!"))
                // TODO: Implement team editor
            }
            29 -> {
                player.sendMessage(MSG.chatColors("&eItem configuration coming soon!"))
                // TODO: Implement item editor
            }
            31 -> {
                player.sendMessage(MSG.chatColors("&eWorld settings coming soon!"))
                // TODO: Implement world settings
            }
            33 -> openTimerSettings(player, mapName)
            40 -> {
                MapManager.saveMapConfig(mapName)
                player.sendMessage(MSG.chatColors("&aConfig saved successfully!"))
            }
            44 -> openMapSelector(player, MapAction.CONFIG)
        }
    }

    private fun handleSettingsEditor(player: Player, event: InventoryClickEvent) {
        val mapName = playerEditingConfig[player.uniqueId] ?: return
        val clicked = event.currentItem ?: return
        val meta = clicked.itemMeta ?: return
        val displayName = meta.displayName ?: return

        when (event.slot) {
            10 -> promptForValue(player, mapName, "DisplayName", "Display Name", ValueType.STRING)
            11 -> promptForValue(player, mapName, "BuildHeight", "Build Height", ValueType.INTEGER)
            12 -> promptForValue(player, mapName, "BuildRange", "Build Range", ValueType.INTEGER)
            13 -> promptForValue(player, mapName, "DeathHeight", "Death Height", ValueType.INTEGER)
            19 -> promptForValue(player, mapName, "MinimumPlayers", "Minimum Players", ValueType.INTEGER)
            20 -> promptForValue(player, mapName, "MaximumPlayers", "Maximum Players", ValueType.INTEGER)
            21 -> promptForValue(player, mapName, "MaxTeams", "Max Teams", ValueType.INTEGER)
            22 -> promptForValue(player, mapName, "PlayersPerTeam", "Players Per Team", ValueType.INTEGER)
            31 -> {
                val config = MapManager.getMapConfig(mapName)
                val newValue = !(config?.canBreakMapBlocks() ?: false)
                MapManager.updateConfigValue(mapName, "Can-Break-Map-Blocks", newValue)
                openSettingsEditor(player, mapName)
            }
            49 -> openConfigEditor(player, mapName)
        }
    }

    private fun handleTimerSettings(player: Player, event: InventoryClickEvent) {
        val mapName = playerEditingConfig[player.uniqueId] ?: return

        when (event.slot) {
            10 -> promptForValue(player, mapName, "LobbyTimer", "Lobby Timer", ValueType.INTEGER)
            12 -> promptForValue(player, mapName, "CageTimer", "Cage Timer", ValueType.INTEGER)
            14 -> promptForValue(player, mapName, "GameTimer", "Game Timer", ValueType.INTEGER)
            16 -> promptForValue(player, mapName, "ItemTimer", "Item Timer", ValueType.INTEGER)
            31 -> openConfigEditor(player, mapName)
        }
    }

    private fun promptForValue(player: Player, mapName: String, key: String, displayName: String, valueType: ValueType) {
        player.closeInventory()
        val config = MapManager.getMapConfig(mapName)
        val currentValue = when (key) {
            "DisplayName" -> config?.getDisplayName()
            "BuildHeight" -> config?.getBuildHeight()
            "BuildRange" -> config?.getBuildRange()
            "DeathHeight" -> config?.getDeathHeight()
            "MinimumPlayers" -> config?.getMinPlayers()
            "MaximumPlayers" -> config?.getMaxPlayers()
            "MaxTeams" -> config?.getMaxTeams()
            "PlayersPerTeam" -> config?.getPlayersPerTeam()
            "LobbyTimer" -> config?.getLobbyTimer()
            "CageTimer" -> config?.getCageTimer()
            "GameTimer" -> config?.getGameTimer()
            "ItemTimer" -> config?.getItemTimer()
            else -> null
        }

        waitingForValue[player.uniqueId] = ConfigSetting(mapName, key, displayName, currentValue, valueType)

        val typeText = when (valueType) {
            ValueType.INTEGER -> "whole number"
            ValueType.DOUBLE -> "decimal number"
            ValueType.STRING -> "text"
            ValueType.BOOLEAN -> "true or false"
        }

        player.sendMessage(MSG.chatColors("&eEnter new value for &f$displayName&e:"))
        player.sendMessage(MSG.chatColors("&7Current value: &f$currentValue"))
        player.sendMessage(MSG.chatColors("&7Expected type: &f$typeText"))
        player.sendMessage(MSG.chatColors("&7Type &ccancel &7to abort."))
    }

    @EventHandler
    fun onAdminChat(event: AsyncPlayerChatEvent) {
        val player = event.player
        val uuid = player.uniqueId
        val message = event.message.trim()

        // Handle map creation
        if (waitingMapName.contains(uuid)) {
            event.isCancelled = true
            if (message.isEmpty()) {
                player.sendMessage(MSG.chatColors("&cInvalid map name!"))
                return
            }
            MapManager.createMap(player, message)
            player.sendMessage(MSG.chatColors("&aMap '&f$message&a' created!"))
            waitingMapName.remove(uuid)
            return
        }

        // Handle value input
        val configSetting = waitingForValue[uuid]
        if (configSetting != null) {
            event.isCancelled = true

            if (message.equals("cancel", ignoreCase = true)) {
                waitingForValue.remove(uuid)
                player.sendMessage(MSG.chatColors("&cValue change cancelled."))
                return
            }

            try {
                val parsedValue = when (configSetting.valueType) {
                    ValueType.INTEGER -> message.toInt()
                    ValueType.DOUBLE -> message.toDouble()
                    ValueType.STRING -> message
                    ValueType.BOOLEAN -> message.toBoolean()
                }

                MapManager.updateConfigValue(configSetting.configName, configSetting.key, parsedValue)
                player.sendMessage(MSG.chatColors("&a${configSetting.displayName} updated to: &f$parsedValue"))
                waitingForValue.remove(uuid)

            } catch (e: Exception) {
                player.sendMessage(MSG.chatColors("&cInvalid value! Please enter a valid ${configSetting.valueType.name.lowercase()}."))
            }
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        val uuid = player.uniqueId

        // Clean up tracking maps
        playerCurrentMenu.remove(uuid)
        playerMapAction.remove(uuid)
        playerEditingConfig.remove(uuid)
    }

    private fun makeMenuButton(mat: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(mat)
        val meta = item.itemMeta
        meta?.setDisplayName(MSG.chatColors(name))
        meta?.lore = lore.map { MSG.chatColors(it) }
        item.itemMeta = meta
        return item
    }

    private fun makeItem(mat: Material, name: String, lore: List<String>): ItemStack {
        val item = ItemStack(mat)
        val meta = item.itemMeta
        meta?.setDisplayName(MSG.chatColors(name))
        meta?.lore = lore.map { MSG.chatColors(it) }
        item.itemMeta = meta
        return item
    }

    fun getAllMapNames(): List<String> = MapManager.getAllMapNames()
}