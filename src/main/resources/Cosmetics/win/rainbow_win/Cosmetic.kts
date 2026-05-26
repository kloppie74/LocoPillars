// Rainbow Burst — several coloured particle types around the winner
val loc = player.location.clone().add(0.0, 1.0, 0.0)
game.spawnParticles(loc, org.bukkit.Particle.DUST_COLOR_TRANSITION, 25, 0.8, 0.8, 0.8, 0.0)
game.spawnParticles(loc, org.bukkit.Particle.HAPPY_VILLAGER, 20, 0.6, 1.0, 0.6, 0.05)
game.spawnParticles(loc, org.bukkit.Particle.TOTEM_OF_UNDYING, 30, 0.7, 0.7, 0.7, 0.1)
game.spawnFirework(player.location)
game.playSound(player, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.8f, 1.0f)
game.sendTitle(player, "§d§lRainbow Victory!", "§7You're a star!", 5, 60, 15)
