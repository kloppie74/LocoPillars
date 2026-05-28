// Ender Chest Gift — an ender chest dropped into inventory
game.giveItem(player, org.bukkit.inventory.ItemStack(org.bukkit.Material.ENDER_CHEST))
game.spawnParticles(player.location, org.bukkit.Particle.PORTAL, 25)
game.playSound(player, org.bukkit.Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 1.0f)
game.sendTitle(player, "§5📦 Ender Chest!", "§7Your precious belongings...", 5, 50, 10)
player.sendMessage("§5§l📦 Ender Chest Gift! §r§7A portable vault, just for you!")
