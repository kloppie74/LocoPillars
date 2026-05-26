// Angel Feathers — holy white light and feathers drift gently upward
val loc = player.location.clone()
for (i in 0..7) {
    val height = 3.0 - (i * 0.3)
    val spread = i * 0.1
    loc.world?.spawnParticle(org.bukkit.Particle.DUST,
        loc.clone().add((Math.random()-0.5)*1.5, height, (Math.random()-0.5)*1.5),
        3, spread, 0.0, spread, 0.0,
        org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 255, 230), 1.2f))
    game.spawnParticles(loc.clone().add((Math.random()-0.5)*1.2, height, (Math.random()-0.5)*1.2),
        org.bukkit.Particle.CLOUD, 2, 0.1, 0.05, 0.1, 0.01)
}
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.END_ROD, 20, 0.3, 0.5, 0.3, 0.04)
game.spawnParticles(loc.clone().add(0.0, 1.5, 0.0), org.bukkit.Particle.TOTEM_OF_UNDYING, 15, 0.3, 0.3, 0.3, 0.08)
game.playSound(player, org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.4f)
