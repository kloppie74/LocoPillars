// Diamond Kit — full diamond armor set + sword
listOf(
    org.bukkit.Material.DIAMOND_HELMET,
    org.bukkit.Material.DIAMOND_CHESTPLATE,
    org.bukkit.Material.DIAMOND_LEGGINGS,
    org.bukkit.Material.DIAMOND_BOOTS,
    org.bukkit.Material.DIAMOND_SWORD
).forEach { mat ->
    val item = org.bukkit.inventory.ItemStack(mat)
    val meta = item.itemMeta
    if (meta != null) {
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true)
        item.itemMeta = meta
    }
    game.giveItem(player, item)
}
game.playSound(player, org.bukkit.Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1.0f, 1.0f)
game.sendTitle(player, "§b💎 Diamond Kit!", "§7Full gear acquired!", 5, 60, 10)
player.sendMessage("§b§l💎 Diamond Kit! §r§7Armor up — you got the full set!")
