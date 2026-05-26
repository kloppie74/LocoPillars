// Ghost Rise — ghostly soul ascends from the death location
val loc = player.location.clone()
for (h in 0..5) {
    val height = h * 0.6
    game.spawnParticles(loc.clone().add(0.0, height, 0.0), org.bukkit.Particle.SOUL, 8, 0.3, 0.1, 0.3, 0.02)
    game.spawnParticles(loc.clone().add(0.0, height + 0.3, 0.0), org.bukkit.Particle.SOUL_FIRE_FLAME, 5, 0.2, 0.1, 0.2, 0.02)
}
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 1.5, 0.0), 20,
    0.2, 0.8, 0.2, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(200, 255, 210), 1.0f))
game.spawnParticles(loc.clone().add(0.0, 3.0, 0.0), org.bukkit.Particle.CLOUD, 10, 0.3, 0.2, 0.3, 0.02)
game.playSound(player, org.bukkit.Sound.ENTITY_PHANTOM_AMBIENT, 0.6f, 1.4f)
