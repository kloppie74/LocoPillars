// Ice Throne — icy particles form a ring and snow blizzard swirls around winner
val loc = player.location.clone()
// Ring of ice at ground level
for (i in 0..23) {
    val angle = i * (Math.PI / 12)
    val r = 1.6
    val x = Math.cos(angle) * r
    val z = Math.sin(angle) * r
    loc.world?.spawnParticle(org.bukkit.Particle.DUST,
        loc.clone().add(x, 0.1, z), 3, 0.0, 0.0, 0.0, 0.0,
        org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(180, 235, 255), 1.3f))
}
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.SNOWFLAKE, 60, 1.0, 1.0, 1.0, 0.05)
game.spawnParticles(loc.clone().add(0.0, 2.0, 0.0), org.bukkit.Particle.SNOWFLAKE, 30, 0.8, 0.5, 0.8, 0.15)
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 1.5, 0.0), 30,
    0.4, 0.6, 0.4, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(220, 245, 255), 1.5f))
game.broadcastToGame("§b❄ §3${player.name} §bsits upon the Ice Throne! §b❄")
game.playSound(player, org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.5f, 0.6f)
game.playSound(player, org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.8f, 0.5f)
game.sendTitle(player, "§b§lVICTORY!", "§3Ice Throne", 5, 60, 20)
