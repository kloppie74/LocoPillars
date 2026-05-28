// Victory Tornado — an upward-spiralling vortex of celebratory particles
val loc = player.location.clone()
val angleStep = Math.PI / 10
for (i in 0..39) {
    val angle = i * angleStep
    val height = i * 0.15
    val radius = 0.4 + Math.sin(height * 0.5) * 0.6
    val x = Math.cos(angle) * radius
    val z = Math.sin(angle) * radius
    val colour = when (i % 4) {
        0 -> org.bukkit.Color.fromRGB(0, 200, 255)
        1 -> org.bukkit.Color.fromRGB(0, 255, 180)
        2 -> org.bukkit.Color.fromRGB(100, 255, 255)
        else -> org.bukkit.Color.fromRGB(50, 150, 255)
    }
    loc.world?.spawnParticle(org.bukkit.Particle.DUST,
        loc.clone().add(x, height, z), 2, 0.0, 0.0, 0.0, 0.0,
        org.bukkit.Particle.DustOptions(colour, 1.2f))
    game.spawnParticles(loc.clone().add(x, height, z), org.bukkit.Particle.CLOUD, 1, 0.02, 0.02, 0.02, 0.01)
}
game.spawnParticles(loc.clone().add(0.0, 3.0, 0.0), org.bukkit.Particle.FIREWORK, 15, 0.5, 0.3, 0.5, 0.15)
game.broadcastToGame("§3🌪 §b${player.name} §3spins to victory with a tornado! §3🌪")
game.playSound(player, org.bukkit.Sound.ENTITY_PHANTOM_FLAP, 0.8f, 0.5f)
game.playSound(player, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.2f)
game.sendTitle(player, "§3§lVICTORY!", "§bVictory Tornado", 5, 60, 20)
