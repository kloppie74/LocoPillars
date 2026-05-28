// Angel's Judgment — golden holy light descends on the kill location
val loc = victim?.location?.clone() ?: player.location.clone()
for (i in 0..4) {
    val height = 4.0 - (i * 0.8)
    game.spawnParticles(loc.clone().add(0.0, height, 0.0), org.bukkit.Particle.END_ROD, 8, 0.3, 0.1, 0.3, 0.02)
}
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 2.0, 0.0), 40,
    0.3, 1.5, 0.3, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 255, 200), 1.5f))
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.TOTEM_OF_UNDYING, 30, 0.3, 0.3, 0.3, 0.1)
game.spawnParticles(loc.clone().add(0.0, 2.5, 0.0), org.bukkit.Particle.FIREWORK, 15, 0.2, 0.2, 0.2, 0.06)
game.spawnLightningEffect(loc.clone())
game.playSound(player, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.7f, 1.4f)
game.playSound(player, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.4f, 1.6f)
game.sendTitle(player, "§f§lKILL!", "§eAngel's Judgment", 5, 35, 10)
