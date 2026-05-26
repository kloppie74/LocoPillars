// Flower Bloom — colourful floral particles bloom at the death location
val loc = player.location.clone()
val flowerColours = listOf(
    org.bukkit.Color.fromRGB(255, 100, 150),
    org.bukkit.Color.fromRGB(255, 200, 50),
    org.bukkit.Color.fromRGB(100, 220, 100),
    org.bukkit.Color.fromRGB(150, 100, 255),
    org.bukkit.Color.fromRGB(255, 150, 50)
)
for ((idx, colour) in flowerColours.withIndex()) {
    val angle = idx * (Math.PI * 2 / flowerColours.size)
    val r = 0.8
    val x = Math.cos(angle) * r
    val z = Math.sin(angle) * r
    loc.world?.spawnParticle(org.bukkit.Particle.DUST,
        loc.clone().add(x, 0.1, z), 8, 0.15, 0.1, 0.15, 0.0,
        org.bukkit.Particle.DustOptions(colour, 1.3f))
    loc.world?.spawnParticle(org.bukkit.Particle.DUST,
        loc.clone().add(x * 0.5, 0.4, z * 0.5), 5, 0.1, 0.2, 0.1, 0.0,
        org.bukkit.Particle.DustOptions(colour, 1.0f))
}
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.COMPOSTER, 15, 0.5, 0.2, 0.5, 0.0)
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.HAPPY_VILLAGER, 10, 0.4, 0.4, 0.4, 0.0)
game.playSound(player, org.bukkit.Sound.BLOCK_GRASS_PLACE, 0.7f, 1.2f)
game.playSound(player, org.bukkit.Sound.BLOCK_AZALEA_LEAVES_PLACE, 0.5f, 1.0f)
