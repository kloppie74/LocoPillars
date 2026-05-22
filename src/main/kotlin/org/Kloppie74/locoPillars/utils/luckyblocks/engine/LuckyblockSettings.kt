package org.Kloppie74.locoPillars.utils.luckyblocks.engine

import org.bukkit.Material

enum class LuckyblockRarity(val weight: Int, val color: String) {
    COMMON(35, "§f"),
    UNCOMMON(30, "§a"),
    RARE(20, "§9"),
    EPIC(10, "§5"),
    LEGENDARY(5, "§6")
}

data class LuckyblockIconSettings(
    val material: Material,
    val customModelData: Int
)

data class LuckyblockSettings(
    val id: String,
    val displayName: String,
    val description: String,
    val icon: LuckyblockIconSettings,
    val blockType: String,
    val rarity: LuckyblockRarity
)
