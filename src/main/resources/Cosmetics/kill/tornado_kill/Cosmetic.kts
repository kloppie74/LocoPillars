// Tornado — spiralling column of particles rises from kill location
val base = victim?.location?.clone() ?: player.location.clone()
val angleStep = Math.PI / 8
for (i in 0..24) {
    val angle = i * angleStep
    val height = i * 0.16
    val radius = 0.8 - (height * 0.06)
    val x = Math.cos(angle) * radius
    val z = Math.sin(angle) * radius
    val spawnLoc = base.clone().add(x, height, z)
    game.spawnParticles(spawnLoc, org.bukkit.Particle.CLOUD, 2, 0.05, 0.05, 0.05, 0.01)
    game.spawnParticles(spawnLoc, org.bukkit.Particle.SWEEP_ATTACK, 1, 0.0, 0.0, 0.0, 0.0)
}
game.spawnParticles(base.clone().add(0.0, 2.0, 0.0), org.bukkit.Particle.EXPLOSION, 8, 0.3, 0.1, 0.3, 0.05)
game.playSound(player, org.bukkit.Sound.ENTITY_PHANTOM_FLAP, 0.8f, 0.6f)
game.playSound(player, org.bukkit.Sound.BLOCK_PISTON_EXTEND, 0.5f, 0.3f)
game.sendTitle(player, "§3§lKILL!", "§bTornado", 0, 30, 10)
