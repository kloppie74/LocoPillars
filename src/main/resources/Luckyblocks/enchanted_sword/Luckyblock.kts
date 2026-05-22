// Enchanted Sword — Sharpness III + Fire Aspect II diamond sword
val sword = org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_SWORD)
val meta = sword.itemMeta
if (meta != null) {
    meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 3, true)
    meta.addEnchant(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 2, true)
    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true)
    sword.itemMeta = meta
}
game.giveItem(player, sword)
game.playSound(player, org.bukkit.Sound.ITEM_ARMOR_EQUIP_DIAMOND, 1.0f, 1.2f)
game.sendTitle(player, "§b⚔ Enchanted Sword!", "§7Sharpness + Fire Aspect!", 5, 50, 10)
player.sendMessage("§b§l⚔ Enchanted Sword! §r§7A blade worthy of a champion!")
