// Blood Pool — crimson spreads outward from the player's death location
val loc = player.location.clone()
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 0.1, 0.0), 60,
    0.8, 0.0, 0.8, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(180, 0, 0), 1.6f))
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 0.5, 0.0), 30,
    0.4, 0.3, 0.4, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(220, 20, 20), 1.2f))
game.spawnParticles(loc.clone().add(0.0, 0.8, 0.0), org.bukkit.Particle.DAMAGE_INDICATOR, 15, 0.3, 0.3, 0.3, 0.05)
game.playSound(player, org.bukkit.Sound.ENTITY_PLAYER_HURT, 0.5f, 0.6f)
