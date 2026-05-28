// Freeze Trap — frozen solid!
game.giveEffect(player, org.bukkit.potion.PotionEffectType.SLOWNESS, 10, 5)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.MINING_FATIGUE, 10, 2)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.BLINDNESS, 4, 0)
game.spawnParticles(player.location, org.bukkit.Particle.SNOWFLAKE, 40)
game.playSound(player, org.bukkit.Sound.BLOCK_GLASS_BREAK, 1.0f, 0.5f)
game.sendTitle(player, "§b❄ Frozen!", "§7You can barely move!", 5, 50, 10)
player.sendMessage("§b§l❄ Freeze Trap! §r§7You're frozen solid!")
