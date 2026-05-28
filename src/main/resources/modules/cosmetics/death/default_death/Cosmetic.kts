// Default Death — smoke puff
game.spawnParticles(player.location.add(0.0, 1.0, 0.0), org.bukkit.Particle.SMOKE, 12, 0.3, 0.5, 0.3, 0.03)
game.playSound(player, org.bukkit.Sound.ENTITY_PLAYER_DEATH, 0.5f, 1.0f)
