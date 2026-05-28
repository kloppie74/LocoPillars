// Time Freeze — crystalline blue-white particles expand outward slowly
val loc = player.location.clone()
val freezeColours = listOf(
    org.bukkit.Color.fromRGB(180, 240, 255),
    org.bukkit.Color.fromRGB(230, 250, 255),
    org.bukkit.Color.fromRGB(100, 200, 240),
    org.bukkit.Color.fromRGB(255, 255, 255)
)
// Expanding sphere of ice crystals
for (i in 0..31) {
    val theta = i * (Math.PI / 16)
    val phi = i * (Math.PI / 8)
    val r = 2.0
    val x = r * Math.sin(theta) * Math.cos(phi)
    val y = r * Math.cos(theta)
    val z = r * Math.sin(theta) * Math.sin(phi)
    val colour = freezeColours[i % freezeColours.size]
    loc.world?.spawnParticle(org.bukkit.Particle.DUST,
        loc.clone().add(x, y + 1.0, z), 2, 0.0, 0.0, 0.0, 0.0,
        org.bukkit.Particle.DustOptions(colour, 1.2f))
}
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.SNOWFLAKE, 40, 1.0, 1.0, 1.0, 0.03)
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.END_ROD, 20, 1.0, 0.8, 1.0, 0.04)
game.broadcastToGame("§b❄ §3${player.name} §bhas frozen time itself with their victory! §b❄")
game.playSound(player, org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.5f, 0.4f)
game.playSound(player, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.6f)
game.sendTitle(player, "§b§lVICTORY!", "§3Time Freeze", 5, 70, 20)
