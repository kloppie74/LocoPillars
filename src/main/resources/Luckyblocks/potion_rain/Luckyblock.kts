// Potion Rain — heals and buffs the player
game.giveEffect(player, org.bukkit.potion.PotionEffectType.REGENERATION, 15, 1)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.ABSORPTION, 20, 1)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.SATURATION, 10, 0)
game.spawnParticles(player.location, org.bukkit.Particle.HEART, 20)
game.playSound(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
game.sendTitle(player, "§d♥ Blessed!", "§7Health flows through you!", 5, 50, 10)
player.sendMessage("§d§l♥ Potion Rain! §r§7You feel incredible!")
