// Galaxy Champion — cosmic star spiral wraps around the winner
val centre = player.location.clone().add(0.0, 1.0, 0.0)
val colours = listOf(
    org.bukkit.Color.fromRGB(200, 140, 255),
    org.bukkit.Color.fromRGB(100, 180, 255),
    org.bukkit.Color.fromRGB(255, 230, 150),
    org.bukkit.Color.fromRGB(255, 255, 255),
    org.bukkit.Color.fromRGB(150, 220, 255)
)
for (i in 0..47) {
    val angle = i * (Math.PI / 24)
    val radius = 1.8 - (i * 0.03)
    val height = (i * 0.08) - 1.0
    val x = Math.cos(angle) * radius
    val z = Math.sin(angle) * radius
    centre.world?.spawnParticle(org.bukkit.Particle.DUST,
        centre.clone().add(x, height, z), 2, 0.0, 0.0, 0.0, 0.0,
        org.bukkit.Particle.DustOptions(colours[i % colours.size], 1.3f))
}
game.spawnParticles(centre, org.bukkit.Particle.END_ROD, 25, 0.3, 0.8, 0.3, 0.06)
game.spawnParticles(centre, org.bukkit.Particle.FIREWORK, 20, 0.5, 0.5, 0.5, 0.1)
game.broadcastToGame("§d§l✦ §5${player.name} §d§lhas transcended the galaxy! §d§l✦")
game.playSound(player, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.5f)
game.playSound(player, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 0.6f)
game.sendTitle(player, "§d§lVICTORY!", "§5Galaxy Champion", 5, 60, 20)
