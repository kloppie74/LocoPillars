// Lightning Storm — bolts of lightning ring around the winner
val loc = player.location.clone()
val offsets = listOf(
    Pair(2.0, 0.0), Pair(-2.0, 0.0), Pair(0.0, 2.0), Pair(0.0, -2.0),
    Pair(1.5, 1.5), Pair(-1.5, 1.5)
)
for ((ox, oz) in offsets) {
    game.spawnLightningEffect(loc.clone().add(ox, 0.0, oz))
}
game.spawnParticles(loc.clone().add(0.0, 1.5, 0.0), org.bukkit.Particle.FIREWORK, 30, 0.8, 0.5, 0.8, 0.15)
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 2.0, 0.0), 25,
    0.5, 0.5, 0.5, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(220, 220, 80), 1.5f))
game.broadcastToGame("§e⚡ §6${player.name} §ecalls down the lightning storm! §e⚡")
game.playSound(player, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.9f, 1.0f)
game.sendTitle(player, "§e§lVICTORY!", "§6Lightning Storm", 5, 60, 20)
