// Rainbow Burst — a full-spectrum arc bursts outward on death
val loc = player.location.clone().add(0.0, 0.5, 0.0)
val rainbow = listOf(
    org.bukkit.Color.fromRGB(255, 0, 0),
    org.bukkit.Color.fromRGB(255, 127, 0),
    org.bukkit.Color.fromRGB(255, 255, 0),
    org.bukkit.Color.fromRGB(0, 200, 0),
    org.bukkit.Color.fromRGB(0, 80, 255),
    org.bukkit.Color.fromRGB(140, 0, 200)
)
for ((idx, colour) in rainbow.withIndex()) {
    val r = 0.4 + idx * 0.25
    for (i in 0..15) {
        val angle = i * (Math.PI / 8)
        loc.world?.spawnParticle(org.bukkit.Particle.DUST,
            loc.clone().add(Math.cos(angle)*r, idx*0.15, Math.sin(angle)*r),
            3, 0.0, 0.0, 0.0, 0.0,
            org.bukkit.Particle.DustOptions(colour, 1.2f))
    }
}
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.FIREWORK, 15, 0.5, 0.3, 0.5, 0.1)
game.playSound(player, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.5f, 1.5f)
