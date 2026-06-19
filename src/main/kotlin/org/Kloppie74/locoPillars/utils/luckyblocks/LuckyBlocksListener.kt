package org.Kloppie74.locoPillars.utils.luckyblocks

import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryType
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import org.Kloppie74.locoPillars.utils.MSG

class LuckyBlocksListener(private val config: ConfigManager) : Listener {

    @EventHandler
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return
        val clickedItem = event.currentItem
        val cursorItem = event.cursor

        // Prevent placing LuckyBlock in helmet slot
        if (event.slotType == InventoryType.SlotType.ARMOR &&
            event.rawSlot == 5 &&
            LuckyBlocksUtil.isLuckyBlockItem(cursorItem)
        ) {
            event.isCancelled = true
            player.sendMessage(MSG.chatColors("&cYou cannot wear a LuckyBlock on your head!"))
            return
        }

        // Prevent shift-clicking LuckyBlock into helmet slot
        if (event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY &&
            LuckyBlocksUtil.isLuckyBlockItem(clickedItem)
        ) {
            if (player.inventory.helmet == null) {
                event.isCancelled = true
                player.sendMessage(MSG.chatColors("&cYou cannot wear a LuckyBlock on your head!"))
                return
            }
        }

        // Prevent hotbar swap into helmet slot
        if (event.action == InventoryAction.HOTBAR_SWAP &&
            event.rawSlot == 5
        ) {
            val hotbarItem = player.inventory.getItem(event.hotbarButton)
            if (LuckyBlocksUtil.isLuckyBlockItem(hotbarItem)) {
                event.isCancelled = true
                player.sendMessage(MSG.chatColors("&cYou cannot wear a LuckyBlock on your head!"))
                return
            }
        }

        // Prevent swap with cursor into helmet slot
        if (event.action == InventoryAction.SWAP_WITH_CURSOR &&
            event.slotType == InventoryType.SlotType.ARMOR &&
            event.rawSlot == 5 &&
            LuckyBlocksUtil.isLuckyBlockItem(cursorItem)
        ) {
            event.isCancelled = true
            player.sendMessage(MSG.chatColors("&cYou cannot wear a LuckyBlock on your head!"))
            return
        }
    }

    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val item = event.itemInHand

        // Check if it's any type of LuckyBlock item
        if (LuckyBlocksUtil.isLuckyBlockItem(item)) {

            // Check if it's a traditional LuckyBlock (from getblock command)
            if (LuckyBlocksUtil.isTraditionalLuckyBlockItem(item)) {
                val glassTexture = LuckyBlocksUtil.getGlassTextureFromSkull(item, config)
                val glassType = glassTexture?.glass ?: Material.GLASS

                val block: Block = event.blockPlaced
                block.type = glassType

                // Add to cache as TRADITIONAL lucky block
                LuckyBlocksUtil.cacheLuckyBlock(block.location)
                if (LuckyBlocksUtil.isGuaranteedLuckyBlockItem(item)) {
                    LuckyBlocksUtil.cacheGuaranteedLuckyBlock(block.location)
                }

                // Spawn armorstand with texture
                if (glassTexture != null && glassTexture.texture.isNotEmpty()) {
                    LuckyBlocksUtil.spawnLuckyBlockArmorStand(block.location, glassTexture.texture)
                }

                player.sendMessage(MSG.chatColors("&aYou placed a LuckyBlock!"))
            } else {
                // Engine LuckyBlock - let the engine handle it
                player.sendMessage(MSG.chatColors("&aYou placed a LuckyBlock!"))
            }
        }
    }

    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        val block = event.block
        val player = event.player

        // Check if this block is a placed traditional LuckyBlock
        if (LuckyBlocksUtil.isPlacedLuckyBlock(block.location)) {
            event.isCancelled = true

            // Remove from cache and cleanup
            LuckyBlocksUtil.removeLuckyBlock(block.location)
            LuckyBlocksUtil.removeLuckyBlockArmorStand(block.location)

            // Remove the block
            block.type = Material.AIR

            LuckyBlocksUtil.handleLuckyBlockBreak(player, block.location)
        }
    }
}