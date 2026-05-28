// Confetti Blast — multicolour dust explosion at the kill location
val loc = victim?.location?.clone() ?: player.location.clone()
val colours = listOf(
    org.bukkit.Color.fromRGB(255, 80, 80),
    org.bukkit.Color.fromRGB(80, 255, 80),
    org.bukkit.Color.fromRGB(80, 150, 255),
    org.bukkit.Color.fromRGB(255, 220, 0),
    org.bukkit.Color.fromRGB(255, 80, 220)
)
for (colour in colours) {
    loc.world?.spawnParticle(
        org.bukkit.Particle.DUST,
        loc.clone().add(0.0, 1.0, 0.0), 20,
        0.5, 0.5, 0.5, 0.0,
        org.bukkit.Particle.DustOptions(colour, 1.4f)
    )
}
game.spawnParticles(loc.clone().add(0.0, 1.5, 0.0), org.bukkit.Particle.FIREWORK, 30, 0.4, 0.5, 0.4, 0.12)
game.playSound(player, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.5f, 1.4f)
game.sendTitle(player, "§e§lKILL!", "§6Confetti Blast", 0, 25, 10)
