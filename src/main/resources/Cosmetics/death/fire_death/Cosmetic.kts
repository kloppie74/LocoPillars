// Inferno Death — fire erupts as the player dies
val loc = player.location.clone()
for (i in 0..11) {
    val angle = i * (Math.PI / 6)
    val r = 0.8
    val x = Math.cos(angle) * r
    val z = Math.sin(angle) * r
    for (h in 0..3) {
        game.spawnParticles(loc.clone().add(x, h * 0.5, z), org.bukkit.Particle.FLAME, 5, 0.1, 0.05, 0.1, 0.05)
    }
}
game.spawnParticles(loc.clone().add(0.0, 1.5, 0.0), org.bukkit.Particle.LAVA, 12, 0.4, 0.3, 0.4, 0.0)
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.FIREWORK, 20, 0.3, 0.5, 0.3, 0.1)
game.spawnParticles(loc.clone().add(0.0, 2.5, 0.0), org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, 8, 0.3, 0.1, 0.3, 0.03)
game.playSound(player, org.bukkit.Sound.ITEM_FIRECHARGE_USE, 0.7f, 1.0f)
game.playSound(player, org.bukkit.Sound.ENTITY_BLAZE_DEATH, 0.5f, 1.2f)
