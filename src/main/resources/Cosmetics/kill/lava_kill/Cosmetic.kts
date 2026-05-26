// Lava Burst — molten particle explosion at the kill location
val loc = victim?.location?.clone() ?: player.location.clone()
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.LAVA, 20, 0.4, 0.3, 0.4, 0.0)
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.FLAME, 30, 0.5, 0.3, 0.5, 0.08)
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 1.0, 0.0), 35,
    0.4, 0.4, 0.4, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 120, 0), 1.6f))
game.spawnParticles(loc.clone().add(0.0, 1.5, 0.0), org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, 8, 0.3, 0.2, 0.3, 0.03)
game.spawnParticles(loc.clone().add(0.0, 2.0, 0.0), org.bukkit.Particle.LARGE_SMOKE, 10, 0.2, 0.1, 0.2, 0.04)
game.playSound(player, org.bukkit.Sound.BLOCK_LAVA_AMBIENT, 0.7f, 1.3f)
game.playSound(player, org.bukkit.Sound.ENTITY_BLAZE_DEATH, 0.5f, 0.8f)
game.sendTitle(player, "§6§lKILL!", "§cLava Burst", 0, 25, 10)
