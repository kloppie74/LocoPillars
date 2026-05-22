// Guardian Swarm — 5 guardians materialize around the player
game.spawnMobs(org.bukkit.entity.EntityType.GUARDIAN, player.location, 5)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.MINING_FATIGUE, 10, 2)
game.spawnParticles(player.location, org.bukkit.Particle.DRIPPING_WATER, 30)
game.playSound(player, org.bukkit.Sound.ENTITY_GUARDIAN_AMBIENT, 1.0f, 0.7f)
game.sendTitle(player, "§3👁 Guardian Swarm!", "§7Their laser eyes burn!", 5, 50, 10)
player.sendMessage("§3§l👁 Guardian Swarm! §r§7The ocean's soldiers attack!")
