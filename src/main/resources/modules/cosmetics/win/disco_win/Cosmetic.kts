// Disco Party — wild colour explosion bursts around the winner
val loc = player.location.clone()
val discoColours = listOf(
    org.bukkit.Color.fromRGB(255, 0, 80), org.bukkit.Color.fromRGB(0, 255, 120),
    org.bukkit.Color.fromRGB(0, 120, 255), org.bukkit.Color.fromRGB(255, 200, 0),
    org.bukkit.Color.fromRGB(200, 0, 255), org.bukkit.Color.fromRGB(0, 255, 255),
    org.bukkit.Color.fromRGB(255, 140, 0), org.bukkit.Color.fromRGB(180, 255, 0)
)
for (i in 0..31) {
    val angle = i * (Math.PI / 16)
    val r = 1.5 + Math.sin(i.toDouble()) * 0.3
    val x = Math.cos(angle) * r
    val z = Math.sin(angle) * r
    val height = 0.5 + (i * 0.07)
    val colour = discoColours[i % discoColours.size]
    loc.world?.spawnParticle(org.bukkit.Particle.DUST,
        loc.clone().add(x, height, z), 3, 0.0, 0.0, 0.0, 0.0,
        org.bukkit.Particle.DustOptions(colour, 1.3f))
}
game.spawnParticles(loc.clone().add(0.0, 2.5, 0.0), org.bukkit.Particle.NOTE, 15, 0.8, 0.3, 0.8, 1.0)
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.FIREWORK, 20, 0.5, 0.5, 0.5, 0.12)
game.playSound(player, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1.6f)
game.playSound(player, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.5f)
game.playSound(player, org.bukkit.Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.2f)
game.sendTitle(player, "§a§lVICTORY!", "§eDisco Party!", 5, 55, 15)
