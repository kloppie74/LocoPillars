// Explosion Kill — visual explosion effect (no block damage)
val loc = victim?.location ?: player.location
game.spawnExplosion(loc, 0f, false) // 0 power = purely visual
game.spawnParticles(loc.add(0.0, 1.0, 0.0), org.bukkit.Particle.EXPLOSION, 5, 0.5, 0.5, 0.5, 0.0)
game.spawnParticles(player.location.add(0.0, 1.0, 0.0), org.bukkit.Particle.LAVA, 12, 0.3, 0.5, 0.3, 0.1)
game.playSound(player, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.3f)
