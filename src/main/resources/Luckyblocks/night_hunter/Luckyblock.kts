// Night Hunter — night vision + speed combo
game.giveEffect(player, org.bukkit.potion.PotionEffectType.NIGHT_VISION, 30, 0)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.SPEED, 30, 1)
game.spawnParticles(player.location, org.bukkit.Particle.WITCH, 15)
game.playSound(player, org.bukkit.Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.2f)
game.sendTitle(player, "§9🦅 Night Hunter!", "§7See and run in the dark!", 5, 40, 10)
player.sendMessage("§9§l🦅 Night Hunter! §r§730 seconds of night vision + speed!")
