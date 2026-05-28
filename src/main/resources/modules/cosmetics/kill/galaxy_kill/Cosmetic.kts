// Galaxy Collapse — cosmic spiral of stars around the kill location
val loc = victim?.location?.clone() ?: player.location.clone()
val centre = loc.clone().add(0.0, 1.0, 0.0)
val starColours = listOf(
    org.bukkit.Color.fromRGB(200, 150, 255),
    org.bukkit.Color.fromRGB(100, 180, 255),
    org.bukkit.Color.fromRGB(255, 230, 150),
    org.bukkit.Color.fromRGB(255, 255, 255)
)
for (i in 0..31) {
    val angle = i * (Math.PI / 16)
    val radius = 1.5 - (i * 0.045)
    val height = i * 0.09
    val x = Math.cos(angle) * radius
    val z = Math.sin(angle) * radius
    val colour = starColours[i % starColours.size]
    centre.world?.spawnParticle(org.bukkit.Particle.DUST,
        centre.clone().add(x, height, z), 2, 0.0, 0.0, 0.0, 0.0,
        org.bukkit.Particle.DustOptions(colour, 1.2f))
}
game.spawnParticles(centre, org.bukkit.Particle.END_ROD, 20, 0.2, 0.5, 0.2, 0.08)
game.spawnParticles(centre, org.bukkit.Particle.FIREWORK, 15, 0.3, 0.4, 0.3, 0.1)
game.playSound(player, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.5f)
game.playSound(player, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.7f)
game.sendTitle(player, "§d§lKILL!", "§5Galaxy Collapse", 0, 30, 10)
