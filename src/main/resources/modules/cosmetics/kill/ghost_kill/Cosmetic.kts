// Ghost Haunt — ghostly soul particles rise from the kill location
val loc = victim?.location?.clone() ?: player.location.clone()
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.SOUL, 25, 0.3, 0.3, 0.3, 0.04)
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.SOUL_FIRE_FLAME, 20, 0.3, 0.4, 0.3, 0.03)
game.spawnParticles(loc.clone().add(0.0, 1.8, 0.0), org.bukkit.Particle.CLOUD, 15, 0.2, 0.2, 0.2, 0.02)
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 1.5, 0.0), 20,
    0.3, 0.4, 0.3, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(200, 255, 220), 1.0f))
game.spawnParticles(loc.clone().add(0.0, 2.2, 0.0), org.bukkit.Particle.END_ROD, 10, 0.2, 0.3, 0.2, 0.03)
game.playSound(player, org.bukkit.Sound.ENTITY_PHANTOM_AMBIENT, 0.7f, 1.2f)
game.sendTitle(player, "§f§lKILL!", "§7Ghost Haunt", 0, 25, 10)
