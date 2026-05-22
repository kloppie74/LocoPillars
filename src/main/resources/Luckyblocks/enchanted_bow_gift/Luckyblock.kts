// Enchanted Bow Gift — Power IV + Infinity bow
val bow = org.bukkit.inventory.ItemStack(org.bukkit.Material.BOW)
val meta = bow.itemMeta
if (meta != null) {
    meta.addEnchant(org.bukkit.enchantments.Enchantment.POWER, 4, true)
    meta.addEnchant(org.bukkit.enchantments.Enchantment.INFINITY, 1, true)
    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true)
    bow.itemMeta = meta
}
game.giveItem(player, bow)
game.giveItem(player, org.bukkit.inventory.ItemStack(org.bukkit.Material.ARROW))
game.playSound(player, org.bukkit.Sound.ENTITY_ARROW_SHOOT, 1.0f, 1.3f)
game.sendTitle(player, "§a🏹 Enchanted Bow!", "§7Power + Infinity!", 5, 50, 10)
player.sendMessage("§a§l🏹 Enchanted Bow Gift! §r§7Shoot infinitely with Power IV!")
