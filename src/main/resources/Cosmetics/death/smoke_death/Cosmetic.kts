// Smoke Bomb — dense smoke cloud
val loc = player.location.clone().add(0.0, 1.0, 0.0)
game.spawnParticles(loc, org.bukkit.Particle.SMOKE, 40, 0.6, 0.8, 0.6, 0.04)
game.spawnParticles(loc, org.bukkit.Particle.LARGE_SMOKE, 15, 0.5, 0.5, 0.5, 0.02)
game.playSound(player, org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.2f)
