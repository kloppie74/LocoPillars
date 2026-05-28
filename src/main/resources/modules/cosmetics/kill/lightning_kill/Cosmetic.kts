// Thunder Kill — strike lightning at the victim's last known position
val loc = victim?.location ?: player.location
game.spawnLightningEffect(loc)
game.playSound(player, org.bukkit.Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.0f)
game.spawnParticles(player.location.add(0.0, 1.5, 0.0), org.bukkit.Particle.ELECTRIC_SPARK, 30, 0.4, 0.8, 0.4, 0.05)
