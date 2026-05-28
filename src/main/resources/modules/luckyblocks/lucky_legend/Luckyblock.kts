// Lucky Legend — the ultimate blessing
// All good effects
game.giveEffect(player, org.bukkit.potion.PotionEffectType.REGENERATION, 60, 2)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.STRENGTH, 60, 2)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.SPEED, 60, 2)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.RESISTANCE, 60, 2)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.ABSORPTION, 60, 3)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 60, 0)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.JUMP_BOOST, 60, 2)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.NIGHT_VISION, 60, 0)

// God apple + diamond kit
game.giveItem(player, org.bukkit.inventory.ItemStack(org.bukkit.Material.ENCHANTED_GOLDEN_APPLE, 3))
listOf(org.bukkit.Material.DIAMOND_HELMET, org.bukkit.Material.DIAMOND_CHESTPLATE,
       org.bukkit.Material.DIAMOND_LEGGINGS, org.bukkit.Material.DIAMOND_BOOTS,
       org.bukkit.Material.DIAMOND_SWORD).forEach { mat ->
    val item = org.bukkit.inventory.ItemStack(mat)
    val meta = item.itemMeta
    if (meta != null) {
        meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, 4, true)
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 3, true)
        item.itemMeta = meta
    }
    game.giveItem(player, item)
}

// Firework celebration
repeat(12) {
    val offset = player.location.clone().add(Math.random() * 8 - 4, 0.0, Math.random() * 8 - 4)
    game.spawnFirework(offset)
}

game.broadcastToGame("§6§l★★★ LUCKY LEGEND! ★★★ §r§e${player.name} §7has been chosen by fate!")
game.sendTitle(player, "§6★ LUCKY LEGEND! ★", "§eYou are the chosen one!", 5, 80, 15)
player.sendMessage("§6§l★ LUCKY LEGEND! §r§7Everything good, all at once. You are unstoppable!")
game.playSound(player, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)
