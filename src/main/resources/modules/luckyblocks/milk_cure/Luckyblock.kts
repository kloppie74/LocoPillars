// Milk Cure — clears all effects and heals to full
player.activePotionEffects.map { it.type }.forEach { player.removePotionEffect(it) }
player.health = minOf(20.0, player.maxHealth)
player.foodLevel = 20
game.spawnParticles(player.location, org.bukkit.Particle.CLOUD, 15)
game.playSound(player, org.bukkit.Sound.ENTITY_GENERIC_DRINK, 1.0f, 1.0f)
game.sendTitle(player, "§f🥛 Milk Cure!", "§7All effects cleansed!", 5, 40, 10)
player.sendMessage("§f§l🥛 Milk Cure! §r§7Refreshing! Everything is gone.")
