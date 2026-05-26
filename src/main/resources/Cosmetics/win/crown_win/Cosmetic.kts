// Royal Crown — golden crown of particles hovers above the winner
val loc = player.location.clone()
val crownY = 2.5
val radius = 0.5
for (i in 0..15) {
    val angle = i * (Math.PI / 8)
    val x = Math.cos(angle) * radius
    val z = Math.sin(angle) * radius
    loc.world?.spawnParticle(org.bukkit.Particle.DUST,
        loc.clone().add(x, crownY, z), 3, 0.0, 0.05, 0.0, 0.0,
        org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 200, 0), 1.2f))
    if (i % 4 == 0) {
        loc.world?.spawnParticle(org.bukkit.Particle.DUST,
            loc.clone().add(x, crownY + 0.3, z), 2, 0.0, 0.0, 0.0, 0.0,
            org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 240, 100), 1.0f))
    }
}
game.spawnParticles(loc.clone().add(0.0, crownY - 0.5, 0.0), org.bukkit.Particle.TOTEM_OF_UNDYING, 20, 0.3, 0.2, 0.3, 0.08)
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.FIREWORK, 20, 0.3, 0.3, 0.3, 0.1)
game.broadcastToGame("§6★ ${player.name} §ewears the Crown of Victory! §6★")
game.playSound(player, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f)
game.sendTitle(player, "§6§lVICTORY!", "§eRoyal Crown", 5, 60, 20)
