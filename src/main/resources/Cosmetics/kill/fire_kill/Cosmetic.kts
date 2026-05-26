// Fire Pillar — column of flame erupts at the kill location
val loc = victim?.location?.clone() ?: player.location.clone()
for (i in 0..4) {
    val height = i * 0.8
    game.spawnParticles(loc.clone().add(0.0, height, 0.0), org.bukkit.Particle.FLAME, 15, 0.3, 0.1, 0.3, 0.06)
    game.spawnParticles(loc.clone().add(0.0, height, 0.0), org.bukkit.Particle.LAVA, 4, 0.2, 0.1, 0.2, 0.0)
}
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.CAMPFIRE_COSY_SMOKE, 10, 0.3, 0.2, 0.3, 0.04)
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.FIREWORK, 20, 0.3, 0.8, 0.3, 0.1)
game.playSound(player, org.bukkit.Sound.ITEM_FIRECHARGE_USE, 0.8f, 0.9f)
game.playSound(player, org.bukkit.Sound.ENTITY_BLAZE_SHOOT, 0.5f, 0.8f)
game.sendTitle(player, "§6§lKILL!", "§cFire Pillar", 0, 25, 10)
