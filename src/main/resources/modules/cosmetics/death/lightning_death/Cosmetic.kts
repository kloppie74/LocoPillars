// Lightning Strike — thunder and sparks mark the death
val loc = player.location.clone()
game.spawnLightningEffect(loc.clone())
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.FIREWORK, 30, 0.3, 0.5, 0.3, 0.15)
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 1.0, 0.0), 20,
    0.3, 0.6, 0.3, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 255, 150), 1.5f))
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.LARGE_SMOKE, 10, 0.3, 0.2, 0.3, 0.04)
game.playSound(player, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.0f)
