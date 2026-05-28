// Galaxy Collapse — cosmic implosion as the player's essence dissolves
val loc = player.location.clone().add(0.0, 1.0, 0.0)
val colours = listOf(
    org.bukkit.Color.fromRGB(200, 140, 255),
    org.bukkit.Color.fromRGB(100, 180, 255),
    org.bukkit.Color.fromRGB(255, 230, 150),
    org.bukkit.Color.fromRGB(255, 255, 255)
)
for (i in 0..31) {
    val angle = i * (Math.PI / 16)
    val radius = 1.5 - (i * 0.04)
    val height = (i * 0.06) - 1.0
    val x = Math.cos(angle) * radius
    val z = Math.sin(angle) * radius
    loc.world?.spawnParticle(org.bukkit.Particle.DUST,
        loc.clone().add(x, height, z), 2, 0.0, 0.0, 0.0, 0.0,
        org.bukkit.Particle.DustOptions(colours[i % colours.size], 1.2f))
}
game.spawnParticles(loc.clone(), org.bukkit.Particle.END_ROD, 20, 0.3, 0.6, 0.3, 0.06)
game.spawnParticles(loc.clone(), org.bukkit.Particle.PORTAL, 25, 0.3, 0.4, 0.3, 0.4)
game.playSound(player, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.8f)
game.playSound(player, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.4f, 0.5f)
