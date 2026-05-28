// Healing Aura — maximum healing combination
game.giveEffect(player, org.bukkit.potion.PotionEffectType.REGENERATION, 20, 2)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.ABSORPTION, 30, 3)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.SATURATION, 15, 1)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.HEALTH_BOOST, 30, 2)
game.spawnParticles(player.location, org.bukkit.Particle.HEART, 30)
game.playSound(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f)
game.sendTitle(player, "§d💖 Healing Aura!", "§7You feel invincible!", 5, 50, 10)
player.sendMessage("§d§l💖 Healing Aura! §r§7Maximum healing for 30 seconds!")
