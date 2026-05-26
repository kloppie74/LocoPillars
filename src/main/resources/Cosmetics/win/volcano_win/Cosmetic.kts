// Volcano Eruption — lava and fire burst upward around the winner
val loc = player.location.clone()
// Expanding lava ring
for (i in 0..15) {
    val angle = i * (Math.PI / 8)
    val r = 1.5
    val x = Math.cos(angle) * r
    val z = Math.sin(angle) * r
    game.spawnParticles(loc.clone().add(x, 0.3, z), org.bukkit.Particle.LAVA, 2, 0.0, 0.0, 0.0, 0.0)
}
// Rising eruption core
for (h in 0..5) {
    val height = h * 0.7
    val radius = 0.3 + (h * 0.1)
    game.spawnParticles(loc.clone().add(0.0, height, 0.0), org.bukkit.Particle.FLAME, 10, radius, 0.05, radius, 0.06)
    game.spawnParticles(loc.clone().add(0.0, height, 0.0), org.bukkit.Particle.LAVA, 3, radius, 0.1, radius, 0.0)
}
game.spawnParticles(loc.clone().add(0.0, 4.0, 0.0), org.bukkit.Particle.EXPLOSION_EMITTER, 3, 0.3, 0.1, 0.3, 0.0)
game.spawnParticles(loc.clone().add(0.0, 3.0, 0.0), org.bukkit.Particle.LARGE_SMOKE, 20, 0.5, 0.3, 0.5, 0.06)
game.broadcastToGame("§c🌋 §6${player.name} §cerupted to victory! §c🌋")
game.playSound(player, org.bukkit.Sound.BLOCK_LAVA_AMBIENT, 0.8f, 0.5f)
game.playSound(player, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.8f)
game.sendTitle(player, "§c§lVICTORY!", "§6Volcano Eruption", 5, 65, 20)
