// Ice Shatter — icy snow bursts at the victim's location
val loc = victim?.location?.clone() ?: player.location.clone()
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 1.0, 0.0), 40,
    0.4, 0.5, 0.4, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(140, 220, 255), 1.6f))
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 0.5, 0.0), 30,
    0.5, 0.3, 0.5, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(200, 240, 255), 1.2f))
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.SNOWFLAKE, 50, 0.5, 0.5, 0.5, 0.08)
game.spawnParticles(loc.clone().add(0.0, 1.5, 0.0), org.bukkit.Particle.SNOWFLAKE, 30, 0.3, 0.4, 0.3, 0.15)
game.playSound(player, org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.8f, 1.4f)
game.playSound(player, org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.6f, 0.9f)
game.sendTitle(player, "§b§lKILL!", "§3Ice Shatter", 0, 25, 10)
