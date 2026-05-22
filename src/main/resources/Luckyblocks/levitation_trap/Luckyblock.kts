// Levitation Trap — briefly levitated then slow fall
game.giveEffect(player, org.bukkit.potion.PotionEffectType.LEVITATION, 4, 3)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.SLOW_FALLING, 10, 0)
game.spawnParticles(player.location, org.bukkit.Particle.END_ROD, 20)
game.playSound(player, org.bukkit.Sound.ENTITY_SHULKER_SHOOT, 1.0f, 1.2f)
game.sendTitle(player, "§f✨ Levitation!", "§7Up you go...", 5, 50, 10)
player.sendMessage("§f§l✨ Levitation Trap! §r§7You float up, then gently drift down.")
