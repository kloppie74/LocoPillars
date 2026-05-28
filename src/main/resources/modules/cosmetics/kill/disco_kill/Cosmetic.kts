// Disco Fever — flashing multicolour disco lights burst outward
val loc = victim?.location?.clone() ?: player.location.clone()
val discoColours = listOf(
    org.bukkit.Color.fromRGB(255, 0, 80),
    org.bukkit.Color.fromRGB(0, 255, 120),
    org.bukkit.Color.fromRGB(0, 120, 255),
    org.bukkit.Color.fromRGB(255, 200, 0),
    org.bukkit.Color.fromRGB(200, 0, 255),
    org.bukkit.Color.fromRGB(0, 255, 255)
)
for (i in 0..23) {
    val angle = i * (Math.PI / 12)
    val radius = 1.2
    val x = Math.cos(angle) * radius
    val z = Math.sin(angle) * radius
    val colour = discoColours[i % discoColours.size]
    loc.world?.spawnParticle(org.bukkit.Particle.DUST,
        loc.clone().add(x, 1.0, z), 3, 0.0, 0.1, 0.0, 0.0,
        org.bukkit.Particle.DustOptions(colour, 1.3f))
}
game.spawnParticles(loc.clone().add(0.0, 1.5, 0.0), org.bukkit.Particle.NOTE, 10, 0.5, 0.3, 0.5, 1.0)
game.playSound(player, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f)
game.playSound(player, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.5f)
game.sendTitle(player, "§a§lKILL!", "§eDisco Fever!", 0, 25, 10)
