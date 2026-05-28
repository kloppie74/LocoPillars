// Void Corruption — darkness and void energy swallows the kill location
val loc = victim?.location?.clone() ?: player.location.clone()
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 1.0, 0.0), 60,
    0.5, 0.6, 0.5, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(5, 0, 15), 2.0f))
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 1.5, 0.0), 30,
    0.3, 0.4, 0.3, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(50, 0, 80), 1.5f))
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.SQUID_INK, 25, 0.4, 0.4, 0.4, 0.1)
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.PORTAL, 40, 0.4, 0.5, 0.4, 0.5)
game.spawnParticles(loc.clone().add(0.0, 2.0, 0.0), org.bukkit.Particle.LARGE_SMOKE, 15, 0.3, 0.2, 0.3, 0.04)
game.spawnLightningEffect(loc.clone())
game.broadcastToGame("§8✦ §7${player.name} §8erased ${victim?.name ?: "someone"} from existence!")
game.playSound(player, org.bukkit.Sound.ENTITY_ENDERMAN_SCREAM, 0.7f, 0.5f)
game.playSound(player, org.bukkit.Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.0f)
game.sendTitle(player, "§0§lKILL!", "§8Void Corruption", 5, 40, 15)
