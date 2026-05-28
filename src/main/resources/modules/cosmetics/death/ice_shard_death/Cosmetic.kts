// Ice Shards — crystalline shards burst outward from the death point
val loc = player.location.clone().add(0.0, 0.5, 0.0)
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone(), 50,
    0.8, 0.6, 0.8, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(160, 220, 255), 1.8f))
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone(), 30,
    0.5, 0.4, 0.5, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(220, 245, 255), 1.3f))
game.spawnParticles(loc.clone(), org.bukkit.Particle.SNOWFLAKE, 40, 0.6, 0.4, 0.6, 0.1)
game.spawnParticles(loc.clone(), org.bukkit.Particle.SNOWFLAKE, 20, 0.7, 0.5, 0.7, 0.2)
game.playSound(player, org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.9f, 1.2f)
game.playSound(player, org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.7f, 1.0f)
