// Phoenix Rising — a pillar of fire and embers erupts from the winner
val loc = player.location.clone()
// Rising fire pillar
for (h in 0..6) {
    val height = h * 0.7
    val radius = (6 - h) * 0.15 + 0.2
    for (j in 0..7) {
        val angle = j * (Math.PI / 4)
        val x = Math.cos(angle) * radius
        val z = Math.sin(angle) * radius
        game.spawnParticles(loc.clone().add(x, height, z), org.bukkit.Particle.FLAME, 3, 0.05, 0.1, 0.05, 0.04)
    }
}
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.LAVA, 15, 0.5, 0.5, 0.5, 0.0)
game.spawnParticles(loc.clone().add(0.0, 2.0, 0.0), org.bukkit.Particle.FIREWORK, 25, 0.5, 0.5, 0.5, 0.15)
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 2.5, 0.0), 20,
    0.4, 0.4, 0.4, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 100, 0), 1.8f))
game.spawnFirework(loc.clone().add(0.0, 1.0, 0.0))
game.broadcastToGame("§6§l🔥 §e${player.name} §6§lhas risen like a Phoenix! §6§l🔥")
game.playSound(player, org.bukkit.Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.7f)
game.playSound(player, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 0.7f, 0.9f)
game.sendTitle(player, "§6§lVICTORY!", "§cPhoenix Rising", 5, 70, 20)
