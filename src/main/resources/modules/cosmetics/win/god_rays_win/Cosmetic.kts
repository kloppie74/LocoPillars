// God Rays — divine golden beams rain down from above
val loc = player.location.clone()
val rayOffsets = listOf(
    Pair(0.0, 0.0), Pair(1.0, 0.8), Pair(-1.0, 0.8),
    Pair(0.8, -1.0), Pair(-0.8, -1.0), Pair(0.0, 1.5), Pair(0.0, -1.5)
)
for ((ox, oz) in rayOffsets) {
    for (h in 0..8) {
        val height = 8.0 - h.toDouble()
        val alpha = h / 8.0
        val r = (255 * (0.9 + alpha * 0.1)).toInt().coerceAtMost(255)
        val g = (200 * (0.7 + alpha * 0.3)).toInt().coerceAtMost(255)
        loc.world?.spawnParticle(org.bukkit.Particle.DUST,
            loc.clone().add(ox, height, oz), 2, 0.1, 0.0, 0.1, 0.0,
            org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(r, g, 0), 1.4f))
        game.spawnParticles(loc.clone().add(ox, height, oz), org.bukkit.Particle.END_ROD, 1, 0.1, 0.0, 0.1, 0.01)
    }
}
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.TOTEM_OF_UNDYING, 40, 0.5, 0.4, 0.5, 0.12)
game.spawnFirework(loc.clone().add(0.0, 1.0, 0.0))
game.broadcastToGame("§6§l☀ §e${player.name} §6§l is blessed by the Gods! §6§l☀")
game.playSound(player, org.bukkit.Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 0.8f)
game.playSound(player, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f)
game.sendTitle(player, "§6§lVICTORY!", "§eGod Rays", 5, 80, 20)
