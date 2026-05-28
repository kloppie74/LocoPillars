// Skull Shower — dark skull particles and wither effects burst outward
val loc = player.location.clone().add(0.0, 0.5, 0.0)
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone(), 40,
    0.4, 0.5, 0.4, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(20, 10, 10), 1.8f))
game.spawnParticles(loc.clone(), org.bukkit.Particle.LARGE_SMOKE, 20, 0.4, 0.4, 0.4, 0.08)
game.spawnParticles(loc.clone(), org.bukkit.Particle.WITCH, 15, 0.4, 0.4, 0.4, 0.12)
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.SOUL, 20, 0.4, 0.4, 0.4, 0.05)
for (i in 0..7) {
    val angle = i * (Math.PI / 4)
    val r = 1.2
    val x = Math.cos(angle) * r
    val z = Math.sin(angle) * r
    loc.world?.spawnParticle(org.bukkit.Particle.DUST,
        loc.clone().add(x, 0.5, z), 4, 0.0, 0.2, 0.0, 0.0,
        org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(40, 20, 40), 1.5f))
}
game.playSound(player, org.bukkit.Sound.ENTITY_WITHER_AMBIENT, 0.7f, 1.2f)
game.playSound(player, org.bukkit.Sound.ENTITY_SKELETON_DEATH, 0.6f, 0.8f)
