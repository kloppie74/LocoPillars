// Neon Glow — electric neon rings pulse around the winner
val loc = player.location.clone()
val neonColours = listOf(
    org.bukkit.Color.fromRGB(0, 255, 80),
    org.bukkit.Color.fromRGB(255, 0, 200),
    org.bukkit.Color.fromRGB(0, 200, 255),
    org.bukkit.Color.fromRGB(255, 220, 0)
)
for (ring in 0..3) {
    val ringHeight = ring * 0.7
    for (i in 0..23) {
        val angle = i * (Math.PI / 12)
        val r = 1.2
        val x = Math.cos(angle) * r
        val z = Math.sin(angle) * r
        val colour = neonColours[ring]
        loc.world?.spawnParticle(org.bukkit.Particle.DUST,
            loc.clone().add(x, ringHeight + 0.2, z), 2, 0.0, 0.0, 0.0, 0.0,
            org.bukkit.Particle.DustOptions(colour, 1.1f))
    }
}
game.spawnParticles(loc.clone().add(0.0, 1.5, 0.0), org.bukkit.Particle.ELECTRIC_SPARK, 25, 0.5, 0.8, 0.5, 0.08)
game.playSound(player, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f)
game.playSound(player, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 2.0f)
game.sendTitle(player, "§a§lVICTORY!", "§2Neon Glow", 5, 55, 20)
