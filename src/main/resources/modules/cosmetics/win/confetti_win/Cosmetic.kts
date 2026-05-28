// Party Confetti — colourful confetti rains around the winner
val loc = player.location.clone()
val colours = listOf(
    org.bukkit.Color.fromRGB(255, 80, 80), org.bukkit.Color.fromRGB(80, 255, 80),
    org.bukkit.Color.fromRGB(80, 150, 255), org.bukkit.Color.fromRGB(255, 220, 0),
    org.bukkit.Color.fromRGB(255, 80, 220), org.bukkit.Color.fromRGB(0, 255, 220)
)
for (c in colours) {
    loc.world?.spawnParticle(org.bukkit.Particle.DUST,
        loc.clone().add((Math.random()-0.5)*3, 3.0 + Math.random()*2, (Math.random()-0.5)*3),
        8, 0.3, 0.2, 0.3, 0.0,
        org.bukkit.Particle.DustOptions(c, 1.3f))
}
game.spawnParticles(loc.clone().add(0.0, 2.0, 0.0), org.bukkit.Particle.FIREWORK, 30, 0.8, 0.8, 0.8, 0.15)
game.spawnFirework(loc.clone().add(0.5, 0.0, 0.5))
game.spawnFirework(loc.clone().add(-0.5, 0.0, -0.5))
game.playSound(player, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.7f, 1.2f)
game.playSound(player, org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f)
game.sendTitle(player, "§e§lVICTORY!", "§6Party Time!", 5, 50, 20)
