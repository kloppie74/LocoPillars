// Pumpkin Head — equips a carved pumpkin, brief blindness for the experience
val pumpkin = org.bukkit.inventory.ItemStack(org.bukkit.Material.CARVED_PUMPKIN)
player.inventory.setHelmet(pumpkin)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.BLINDNESS, 2, 0)
game.sendTitle(player, "§a🎃 Pumpkin Head!", "§7Spooky!", 5, 50, 10)
player.sendMessage("§a§l🎃 Pumpkin Head! §r§7You look absolutely ridiculous!")
game.playSound(player, org.bukkit.Sound.ENTITY_PLAYER_HURT, 1.0f, 0.5f)
