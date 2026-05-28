// Star Shower — falling star streaks rain around the winner
val loc = player.location.clone()
val starColours = listOf(
    org.bukkit.Color.fromRGB(255, 255, 200),
    org.bukkit.Color.fromRGB(255, 220, 120),
    org.bukkit.Color.fromRGB(200, 240, 255),
    org.bukkit.Color.fromRGB(255, 180, 255)
)
// Shooting stars at different heights and angles
for (i in 0..19) {
    val offsetX = (Math.random() - 0.5) * 4.0
    val offsetZ = (Math.random() - 0.5) * 4.0
    val startHeight = 5.0 + Math.random() * 3.0
    val colour = starColours[i % starColours.size]
    // Trail down to ground
    for (step in 0..5) {
        val h = startHeight - (step * 0.8)
        loc.world?.spawnParticle(org.bukkit.Particle.DUST,
            loc.clone().add(offsetX * (step * 0.1), h, offsetZ * (step * 0.1)), 2, 0.0, 0.0, 0.0, 0.0,
            org.bukkit.Particle.DustOptions(colour, 1.2f))
    }
}
game.spawnParticles(loc.clone().add(0.0, 3.0, 0.0), org.bukkit.Particle.END_ROD, 30, 1.5, 1.5, 1.5, 0.05)
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.FIREWORK, 25, 0.8, 0.8, 0.8, 0.12)
game.playSound(player, org.bukkit.Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.8f, 1.2f)
game.playSound(player, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 0.8f)
game.sendTitle(player, "§e§lVICTORY!", "§6Star Shower", 5, 60, 20)
