// Grim Reaper — dark souls, lightning effect, and death particles
val loc = victim?.location?.clone() ?: player.location.clone()
game.spawnLightningEffect(loc.clone())
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.SOUL, 30, 0.4, 0.3, 0.4, 0.05)
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.SOUL_FIRE_FLAME, 20, 0.3, 0.5, 0.3, 0.04)
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 1.2, 0.0), 35,
    0.4, 0.6, 0.4, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(10, 10, 10), 1.8f))
game.spawnParticles(loc.clone().add(0.0, 2.0, 0.0), org.bukkit.Particle.LARGE_SMOKE, 20, 0.3, 0.3, 0.3, 0.05)
game.spawnParticles(loc.clone().add(0.0, 1.5, 0.0), org.bukkit.Particle.WITCH, 15, 0.3, 0.4, 0.3, 0.1)
game.broadcastToGame("§8☠ §7${player.name} §8has claimed a soul!")
game.playSound(player, org.bukkit.Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.8f)
game.playSound(player, org.bukkit.Sound.ENTITY_WITHER_DEATH, 0.4f, 1.5f)
game.sendTitle(player, "§8§lKILL!", "§7☠ Grim Reaper ☠", 5, 40, 15)
