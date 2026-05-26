// Death Tornado — a spiralling column of smoke rises from the death point
val base = player.location.clone()
val angleStep = Math.PI / 8
for (i in 0..20) {
    val angle = i * angleStep
    val height = i * 0.2
    val radius = 0.7 - (height * 0.04)
    val x = Math.cos(angle) * radius
    val z = Math.sin(angle) * radius
    val spawnLoc = base.clone().add(x, height, z)
    game.spawnParticles(spawnLoc, org.bukkit.Particle.SMOKE, 2, 0.04, 0.04, 0.04, 0.01)
    game.spawnParticles(spawnLoc, org.bukkit.Particle.ASH, 1, 0.02, 0.02, 0.02, 0.01)
}
game.spawnParticles(base.clone().add(0.0, 0.3, 0.0), org.bukkit.Particle.EXPLOSION, 5, 0.3, 0.1, 0.3, 0.04)
game.playSound(player, org.bukkit.Sound.ENTITY_PHANTOM_FLAP, 0.6f, 0.7f)
