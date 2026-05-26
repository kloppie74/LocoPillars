// Blood Splatter — crimson particle burst at the victim's location
val loc = victim?.location?.clone() ?: player.location.clone()
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 0.8, 0.0), 50,
    0.5, 0.6, 0.5, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(200, 10, 10), 1.8f))
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 1.5, 0.0), 25,
    0.3, 0.3, 0.3, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(150, 0, 0), 1.2f))
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.DAMAGE_INDICATOR, 20, 0.4, 0.5, 0.4, 0.1)
game.playSound(player, org.bukkit.Sound.ENTITY_PLAYER_HURT, 0.6f, 0.8f)
game.sendTitle(player, "§c§lKILL!", "§4Blood Splatter", 0, 25, 10)
