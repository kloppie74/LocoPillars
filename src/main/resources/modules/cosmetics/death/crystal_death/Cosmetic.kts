// Crystal Shatter — amethyst crystal shards burst in all directions
val loc = player.location.clone().add(0.0, 0.5, 0.0)
val crystalColours = listOf(
    org.bukkit.Color.fromRGB(190, 100, 255),
    org.bukkit.Color.fromRGB(150, 60, 220),
    org.bukkit.Color.fromRGB(230, 170, 255),
    org.bukkit.Color.fromRGB(100, 200, 255)
)
for (i in 0..23) {
    val angle = i * (Math.PI / 12)
    val r = 0.6 + Math.random() * 0.8
    val x = Math.cos(angle) * r
    val z = Math.sin(angle) * r
    val h = Math.random() * 1.2
    val colour = crystalColours[i % crystalColours.size]
    loc.world?.spawnParticle(org.bukkit.Particle.DUST,
        loc.clone().add(x, h, z), 4, 0.0, 0.0, 0.0, 0.0,
        org.bukkit.Particle.DustOptions(colour, 1.4f))
}
game.spawnParticles(loc.clone(), org.bukkit.Particle.ELECTRIC_SPARK, 20, 0.4, 0.4, 0.4, 0.06)
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.END_ROD, 15, 0.3, 0.4, 0.3, 0.06)
game.playSound(player, org.bukkit.Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.9f, 1.0f)
game.playSound(player, org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.5f, 1.5f)
