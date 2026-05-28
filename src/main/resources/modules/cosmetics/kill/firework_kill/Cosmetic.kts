// Firework Kill cosmetic — launch a firework at the killer's location
game.spawnFirework(player.location.add(0.0, 0.5, 0.0))
game.spawnParticles(player.location.add(0.0, 1.0, 0.0), org.bukkit.Particle.FIREWORK, 20, 0.5, 0.5, 0.5, 0.1)
