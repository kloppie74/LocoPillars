// Angelic Ascension — holy light rains down and feathers spiral upward
val loc = player.location.clone()
// Downward beams of golden-white light
for (h in 0..5) {
    val height = 5.0 - (h * 0.8)
    loc.world?.spawnParticle(org.bukkit.Particle.DUST,
        loc.clone().add(0.0, height, 0.0), 8, 0.5, 0.1, 0.5, 0.0,
        org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(255, 255, 220), 1.3f))
    game.spawnParticles(loc.clone().add(0.0, height, 0.0), org.bukkit.Particle.END_ROD, 5, 0.4, 0.0, 0.4, 0.02)
}
// Spiralling feathers at player level
for (i in 0..15) {
    val angle = i * (Math.PI / 8)
    val r = 1.2
    game.spawnParticles(loc.clone().add(Math.cos(angle) * r, (i * 0.15), Math.sin(angle) * r),
        org.bukkit.Particle.CLOUD, 2, 0.05, 0.05, 0.05, 0.01)
}
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.TOTEM_OF_UNDYING, 30, 0.4, 0.4, 0.4, 0.1)
game.spawnLightningEffect(loc.clone())
game.broadcastToGame("§f✦ §e${player.name} §fhas achieved angelic victory! §f✦")
game.playSound(player, org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.2f)
game.playSound(player, org.bukkit.Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.3f, 2.0f)
game.sendTitle(player, "§f§lVICTORY!", "§eAngelic Ascension", 5, 60, 20)
