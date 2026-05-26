// Magic Surge — arcane sparks and enchantment glyphs burst outward
val loc = victim?.location?.clone() ?: player.location.clone()
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 1.0, 0.0), 40,
    0.4, 0.5, 0.4, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(180, 60, 255), 1.5f))
loc.world?.spawnParticle(org.bukkit.Particle.DUST, loc.clone().add(0.0, 1.5, 0.0), 25,
    0.3, 0.3, 0.3, 0.0,
    org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(100, 200, 255), 1.2f))
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.ENCHANT, 40, 0.5, 0.5, 0.5, 2.0)
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.WITCH, 20, 0.4, 0.4, 0.4, 0.1)
game.spawnParticles(loc.clone().add(0.0, 1.5, 0.0), org.bukkit.Particle.END_ROD, 15, 0.4, 0.6, 0.4, 0.06)
game.playSound(player, org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.0f)
game.playSound(player, org.bukkit.Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.5f, 1.2f)
game.sendTitle(player, "§5§lKILL!", "§dMagic Surge", 0, 30, 10)
