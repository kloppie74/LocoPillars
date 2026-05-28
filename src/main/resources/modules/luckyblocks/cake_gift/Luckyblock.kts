// Cake Gift — three cakes, a sweet surprise
repeat(3) { game.giveItem(player, org.bukkit.inventory.ItemStack(org.bukkit.Material.CAKE)) }
game.spawnParticles(player.location, org.bukkit.Particle.HEART, 10)
game.playSound(player, org.bukkit.Sound.ENTITY_VILLAGER_YES, 1.0f, 1.2f)
game.sendTitle(player, "§e🎂 Cake!", "§7Have some cake!", 5, 40, 10)
player.sendMessage("§e§l🎂 Cake Gift! §r§7Three whole cakes, just for you!")
