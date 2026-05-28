// Treasure Chest — showers the player with valuable loot
game.giveItem(player, org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 3))
game.giveItem(player, org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLD_INGOT, 8))
game.giveItem(player, org.bukkit.inventory.ItemStack(org.bukkit.Material.IRON_SWORD))
game.giveItem(player, org.bukkit.inventory.ItemStack(org.bukkit.Material.COOKED_BEEF, 16))
game.spawnParticles(player.location, org.bukkit.Particle.HAPPY_VILLAGER, 20)
game.playSound(player, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
game.sendTitle(player, "§6★ Treasure!", "§7Riches beyond belief!", 5, 50, 10)
player.sendMessage("§6§l★ Treasure Chest! §r§7You struck it rich!")
