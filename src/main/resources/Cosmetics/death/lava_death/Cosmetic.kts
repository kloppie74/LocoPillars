// Lava Pool — molten lava erupts and splashes at the death location
val loc = player.location.clone()
game.spawnParticles(loc.clone().add(0.0, 0.1, 0.0), org.bukkit.Particle.LAVA, 25, 0.6, 0.0, 0.6, 0.0)
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.FLAME, 20, 0.4, 0.2, 0.4, 0.06)
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 0.3, 0.0), 30,
    0.6, 0.1, 0.6, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 110, 0), 1.6f))
game.spawnParticles(loc.clone().add(0.0, 1.2, 0.0), org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, 8, 0.3, 0.2, 0.3, 0.03)
game.spawnParticles(loc.clone().add(0.0, 0.8, 0.0), org.bukkit.Particle.LARGE_SMOKE, 6, 0.2, 0.1, 0.2, 0.04)
game.playSound(player, org.bukkit.Sound.BLOCK_LAVA_AMBIENT, 0.7f, 1.2f)
game.playSound(player, org.bukkit.Sound.BLOCK_FIRE_AMBIENT, 0.5f, 0.8f)
