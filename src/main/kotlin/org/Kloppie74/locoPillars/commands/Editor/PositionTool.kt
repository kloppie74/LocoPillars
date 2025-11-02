package org.Kloppie74.locoPillars.commands.Editor

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.Location
import org.Kloppie74.locoPillars.utils.MSG
import java.util.*

object PositionTool : Listener {

    private val playerPositions = mutableMapOf<UUID, PositionData>()
    private val playerModes = mutableMapOf<UUID, ToolMode>()

    data class PositionData(
        var pos1: Location? = null,
        var pos2: Location? = null,
        var lastClickTime: Long = 0
    )

    enum class ToolMode(val displayName: String, val description: String) {
        AREA_SELECT("Area Selection", "Select two corners for schematic/region"),
        SPAWN_SET("Spawn Setter", "Set team spawn points"),
        SPAWN_AREA("Spawn Area", "Set spawn protection area")
    }

    fun givePositionTool(player: Player) {
        val tool = ItemStack(Material.GOLDEN_AXE)
        val meta = tool.itemMeta
        meta?.setDisplayName(MSG.chatColors("&6Position Selection Tool"))
        meta?.lore = listOf(
            MSG.chatColors("&7Left click: Set position 1"),
            MSG.chatColors("&7Right click: Set position 2"),
            MSG.chatColors("&7Shift + Right click: Change mode"),
            MSG.chatColors(""),
            MSG.chatColors("&eMode: &f${getCurrentMode(player).displayName}"),
            MSG.chatColors("&7${getCurrentMode(player).description}")
        )
        tool.itemMeta = meta

        player.inventory.addItem(tool)
        player.sendMessage(MSG.chatColors("&aYou received the position selection tool!"))
        player.sendMessage(MSG.chatColors("&7Use left and right click to select positions."))
        player.sendMessage(MSG.chatColors("&7Shift + Right click to change modes."))

        // Initialize player data
        if (!playerPositions.containsKey(player.uniqueId)) {
            playerPositions[player.uniqueId] = PositionData()
        }
        if (!playerModes.containsKey(player.uniqueId)) {
            playerModes[player.uniqueId] = ToolMode.AREA_SELECT
        }
    }

    private fun getCurrentMode(player: Player): ToolMode {
        return playerModes[player.uniqueId] ?: ToolMode.AREA_SELECT
    }

    private fun cycleMode(player: Player) {
        val currentMode = getCurrentMode(player)
        val newMode = when (currentMode) {
            ToolMode.AREA_SELECT -> ToolMode.SPAWN_SET
            ToolMode.SPAWN_SET -> ToolMode.SPAWN_AREA
            ToolMode.SPAWN_AREA -> ToolMode.AREA_SELECT
        }

        playerModes[player.uniqueId] = newMode
        player.sendMessage(MSG.chatColors("&eMode changed to: &f${newMode.displayName}"))
        player.sendMessage(MSG.chatColors("&7${newMode.description}"))

        // Update tool lore
        player.inventory.contents.forEach { item ->
            if (item?.type == Material.GOLDEN_AXE && item.hasItemMeta() &&
                item.itemMeta.displayName == MSG.chatColors("&6Position Selection Tool")) {

                val meta = item.itemMeta
                meta?.lore = listOf(
                    MSG.chatColors("&7Left click: Set position 1"),
                    MSG.chatColors("&7Right click: Set position 2"),
                    MSG.chatColors("&7Shift + Right click: Change mode"),
                    MSG.chatColors(""),
                    MSG.chatColors("&eMode: &f${newMode.displayName}"),
                    MSG.chatColors("&7${newMode.description}")
                )
                item.itemMeta = meta
            }
        }
    }

    @EventHandler
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        val item = event.item ?: return

        // Check if it's our position tool
        if (item.type != Material.GOLDEN_AXE || !item.hasItemMeta() ||
            item.itemMeta.displayName != MSG.chatColors("&6Position Selection Tool")) {
            return
        }

        event.isCancelled = true
        val clickedBlock = event.clickedBlock ?: return
        val location = clickedBlock.location
        val mode = getCurrentMode(player)

        when (event.action) {
            Action.LEFT_CLICK_BLOCK -> {
                handleLeftClick(player, location, mode)
            }
            Action.RIGHT_CLICK_BLOCK -> {
                if (player.isSneaking) {
                    cycleMode(player)
                } else {
                    handleRightClick(player, location, mode)
                }
            }
            else -> return
        }
    }

    private fun handleLeftClick(player: Player, location: Location, mode: ToolMode) {
        val posData = playerPositions.getOrPut(player.uniqueId) { PositionData() }

        when (mode) {
            ToolMode.AREA_SELECT -> {
                posData.pos1 = location
                player.sendMessage(MSG.chatColors("&aPosition 1 set: &f${location.blockX}, ${location.blockY}, ${location.blockZ}"))

                if (posData.pos2 != null) {
                    val volume = calculateVolume(posData.pos1!!, posData.pos2!!)
                    player.sendMessage(MSG.chatColors("&7Selection: &e$volume &7blocks"))
                }
            }
            ToolMode.SPAWN_SET -> {
                posData.pos1 = location
                player.sendMessage(MSG.chatColors("&aSpawn position set: &f${location.blockX}, ${location.blockY}, ${location.blockZ}"))
                player.sendMessage(MSG.chatColors("&7Use &e/pillars admin addspawn <mapname> <teamname> &7to save this spawn"))
            }
            ToolMode.SPAWN_AREA -> {
                posData.pos1 = location
                player.sendMessage(MSG.chatColors("&aSpawn area corner 1 set: &f${location.blockX}, ${location.blockY}, ${location.blockZ}"))
            }
        }
    }

    private fun handleRightClick(player: Player, location: Location, mode: ToolMode) {
        val posData = playerPositions.getOrPut(player.uniqueId) { PositionData() }

        when (mode) {
            ToolMode.AREA_SELECT -> {
                posData.pos2 = location
                player.sendMessage(MSG.chatColors("&aPosition 2 set: &f${location.blockX}, ${location.blockY}, ${location.blockZ}"))

                if (posData.pos1 != null) {
                    val volume = calculateVolume(posData.pos1!!, posData.pos2!!)
                    player.sendMessage(MSG.chatColors("&7Selection: &e$volume &7blocks"))
                    player.sendMessage(MSG.chatColors("&7Use &e/pillars admin saveschem <mapname> <schemname> &7to save"))
                }
            }
            ToolMode.SPAWN_SET -> {
                val spawnLoc = location.add(0.5, 1.0, 0.5)
                spawnLoc.yaw = player.location.yaw
                spawnLoc.pitch = player.location.pitch

                player.sendMessage(MSG.chatColors("&aSpawn location set with your rotation"))
                player.sendMessage(MSG.chatColors("&7Position: &f${spawnLoc.blockX}, ${spawnLoc.blockY}, ${spawnLoc.blockZ}"))
                player.sendMessage(MSG.chatColors("&7Rotation: &f${String.format("%.1f", spawnLoc.yaw)}, ${String.format("%.1f", spawnLoc.pitch)}"))

                posData.pos1 = spawnLoc
            }
            ToolMode.SPAWN_AREA -> {
                posData.pos2 = location
                player.sendMessage(MSG.chatColors("&aSpawn area corner 2 set: &f${location.blockX}, ${location.blockY}, ${location.blockZ}"))

                if (posData.pos1 != null) {
                    val volume = calculateVolume(posData.pos1!!, posData.pos2!!)
                    player.sendMessage(MSG.chatColors("&7Spawn area: &e$volume &7blocks"))
                    player.sendMessage(MSG.chatColors("&7Use &e/pillars admin setspawnarea <mapname> &7to save"))
                }
            }
        }
    }

    private fun calculateVolume(pos1: Location, pos2: Location): Int {
        val minX = minOf(pos1.blockX, pos2.blockX)
        val maxX = maxOf(pos1.blockX, pos2.blockX)
        val minY = minOf(pos1.blockY, pos2.blockY)
        val maxY = maxOf(pos1.blockY, pos2.blockY)
        val minZ = minOf(pos1.blockZ, pos2.blockZ)
        val maxZ = maxOf(pos1.blockZ, pos2.blockZ)

        return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1)
    }

    fun getPos1(player: Player): Location? {
        return playerPositions[player.uniqueId]?.pos1
    }

    fun getPos2(player: Player): Location? {
        return playerPositions[player.uniqueId]?.pos2
    }

    fun clearPositions(player: Player) {
        playerPositions[player.uniqueId]?.let {
            it.pos1 = null
            it.pos2 = null
        }
        player.sendMessage(MSG.chatColors("&ePositions cleared."))
    }

    @EventHandler
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val uuid = event.player.uniqueId
        playerPositions.remove(uuid)
        playerModes.remove(uuid)
    }

    fun showPositionInfo(player: Player) {
        val posData = playerPositions[player.uniqueId]
        val mode = getCurrentMode(player)

        player.sendMessage(MSG.chatColors("&6=== Position Tool Info ==="))
        player.sendMessage(MSG.chatColors("&eMode: &f${mode.displayName}"))
        player.sendMessage(MSG.chatColors("&7${mode.description}"))

        if (posData?.pos1 != null) {
            val pos1 = posData.pos1!!
            player.sendMessage(MSG.chatColors("&aPosition 1: &f${pos1.blockX}, ${pos1.blockY}, ${pos1.blockZ}"))
        } else {
            player.sendMessage(MSG.chatColors("&7Position 1: &cNot set"))
        }

        if (posData?.pos2 != null) {
            val pos2 = posData.pos2!!
            player.sendMessage(MSG.chatColors("&aPosition 2: &f${pos2.blockX}, ${pos2.blockY}, ${pos2.blockZ}"))
        } else {
            player.sendMessage(MSG.chatColors("&7Position 2: &cNot set"))
        }

        if (posData?.pos1 != null && posData.pos2 != null) {
            val volume = calculateVolume(posData.pos1!!, posData.pos2!!)
            player.sendMessage(MSG.chatColors("&7Selection volume: &e$volume &7blocks"))
        }
    }
}