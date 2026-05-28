// Rainbow Shower — a full spectrum of colour rains down
val loc = player.location.clone()
val rainbow = listOf(
    org.bukkit.Color.fromRGB(255, 0, 0),
    org.bukkit.Color.fromRGB(255, 127, 0),
    org.bukkit.Color.fromRGB(255, 255, 0),
    org.bukkit.Color.fromRGB(0, 255, 0),
    org.bukkit.Color.fromRGB(0, 0, 255),
    org.bukkit.Color.fromRGB(75, 0, 130),
    org.bukkit.Color.fromRGB(200, 0, 255)
)
for ((idx, colour) in rainbow.withIndex()) {
    val ringRadius = 0.15 * idx + 0.3
    for (i in 0..15) {
        val angle = i * (Math.PI / 8)
        val x = Math.cos(angle) * ringRadius
        val z = Math.sin(angle) * ringRadius
        for (h in 0..3) {
            loc.world?.spawnParticle(org.bukkit.Particle.DUST,
                loc.clone().add(x, 3.0 + h * 0.5, z), 3, 0.0, 0.0, 0.0, 0.0,
                org.bukkit.Particle.DustOptions(colour, 1.1f))
        }
    }
}
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.FIREWORK, 25, 0.4, 0.3, 0.4, 0.12)
game.playSound(player, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.7f, 1.4f)
game.playSound(player, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.2f)
game.sendTitle(player, "§d§lVICTORY!", "§aRainbow Shower", 5, 60, 20)
