// Smoke Cloud — a thick billowing cloud of smoke on death
val loc = player.location.clone()
for (h in 0..4) {
    val height = h * 0.5
    val radius = 0.8 - (h * 0.1)
    game.spawnParticles(loc.clone().add(0.0, height, 0.0), org.bukkit.Particle.LARGE_SMOKE, 12, radius, 0.1, radius, 0.03)
    game.spawnParticles(loc.clone().add(0.0, height, 0.0), org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, 5, radius * 0.8, 0.1, radius * 0.8, 0.02)
}
game.spawnParticles(loc.clone().add(0.0, 0.3, 0.0), org.bukkit.Particle.EXPLOSION, 4, 0.2, 0.1, 0.2, 0.04)
game.playSound(player, org.bukkit.Sound.BLOCK_CAMPFIRE_CRACKLE, 0.6f, 0.7f)
