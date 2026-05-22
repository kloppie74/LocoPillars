// Bone Storm — wither skeleton horde attacks
game.spawnMobs(org.bukkit.entity.EntityType.WITHER_SKELETON, player.location, 4)
game.spawnMobs(org.bukkit.entity.EntityType.SKELETON, player.location, 3)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.WITHER, 5, 0)
game.spawnParticles(player.location, org.bukkit.Particle.SMOKE, 30)
game.playSound(player, org.bukkit.Sound.ENTITY_WITHER_SKELETON_HURT, 1.0f, 0.8f)
game.sendTitle(player, "§8💀 Bone Storm!", "§7Skeletal warriors rise!", 5, 50, 10)
player.sendMessage("§8§l💀 Bone Storm! §r§7They hunger for your bones!")
