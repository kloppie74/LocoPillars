// Soul Farewell — soul particles rise and drift upward
val loc = player.location.clone().add(0.0, 0.5, 0.0)
game.spawnParticles(loc, org.bukkit.Particle.SOUL, 20, 0.4, 0.3, 0.4, 0.04)
game.spawnParticles(loc.add(0.0, 1.0, 0.0), org.bukkit.Particle.SOUL_FIRE_FLAME, 15, 0.3, 0.5, 0.3, 0.03)
game.spawnParticles(loc.add(0.0, 1.0, 0.0), org.bukkit.Particle.END_ROD, 10, 0.2, 1.0, 0.2, 0.05)
game.playSound(player, org.bukkit.Sound.BLOCK_SOUL_SAND_BREAK, 0.7f, 0.8f)
