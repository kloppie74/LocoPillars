// Diamond Helmet Gift — Protection IV diamond helmet
val helmet = org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_HELMET)
val meta = helmet.itemMeta
if (meta != null) {
    meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true)
    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true)
    helmet.itemMeta = meta
}
game.giveItem(player, helmet)
game.playSound(player, org.bukkit.Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1.0f, 1.0f)
game.sendTitle(player, "§b🪖 Diamond Helmet!", "§7Protection IV!", 5, 50, 10)
player.sendMessage("§b§l🪖 Diamond Helmet Gift! §r§7Your head is safe now!")
