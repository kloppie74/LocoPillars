// Void Consumed — dark portal-like energy implodes at the death location
val loc = player.location.clone().add(0.0, 0.5, 0.0)
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone(), 60,
    0.5, 0.5, 0.5, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(5, 0, 15), 2.0f))
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone(), 30,
    0.3, 0.3, 0.3, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(60, 0, 90), 1.5f))
game.spawnParticles(loc.clone(), org.bukkit.Particle.PORTAL, 50, 0.4, 0.4, 0.4, 0.5)
game.spawnParticles(loc.clone(), org.bukkit.Particle.SQUID_INK, 20, 0.4, 0.3, 0.4, 0.08)
game.spawnParticles(loc.clone().add(0.0, 1.5, 0.0), org.bukkit.Particle.LARGE_SMOKE, 15, 0.3, 0.2, 0.3, 0.05)
game.playSound(player, org.bukkit.Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.0f)
game.playSound(player, org.bukkit.Sound.ENTITY_ENDERMAN_SCREAM, 0.4f, 0.7f)
