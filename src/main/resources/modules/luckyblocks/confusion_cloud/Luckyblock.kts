// Confusion Cloud — brief nausea and blindness
game.giveEffect(player, org.bukkit.potion.PotionEffectType.NAUSEA, 5, 0)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.BLINDNESS, 3, 0)
game.spawnParticles(player.location, org.bukkit.Particle.SQUID_INK, 20)
game.playSound(player, org.bukkit.Sound.ENTITY_WITCH_HURT, 1.0f, 0.7f)
game.sendTitle(player, "§5🌀 Confusion!", "§7What is happening?!", 5, 40, 10)
player.sendMessage("§5§l🌀 Confusion Cloud! §r§7The world spins around you!")
