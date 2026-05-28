// XP Shower — 10 levels of experience
player.giveExpLevels(10)
game.spawnParticles(player.location, org.bukkit.Particle.HAPPY_VILLAGER, 30)
game.playSound(player, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
game.sendTitle(player, "§a✦ XP Shower!", "§7+10 levels!", 5, 40, 10)
player.sendMessage("§a§l✦ XP Shower! §r§7You gained 10 experience levels!")
