// Arcane Dissipate — magic sparks and enchantment runes scatter outward
val loc = player.location.clone().add(0.0, 0.5, 0.0)
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone(), 35,
    0.4, 0.5, 0.4, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(180, 60, 255), 1.5f))
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 0.5, 0.0), 25,
    0.3, 0.3, 0.3, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(100, 200, 255), 1.2f))
game.spawnParticles(loc.clone(), org.bukkit.Particle.ENCHANT, 30, 0.5, 0.5, 0.5, 1.5)
game.spawnParticles(loc.clone(), org.bukkit.Particle.WITCH, 15, 0.4, 0.4, 0.4, 0.1)
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.END_ROD, 10, 0.3, 0.5, 0.3, 0.05)
game.playSound(player, org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 1.2f)
game.playSound(player, org.bukkit.Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.4f, 1.5f)
