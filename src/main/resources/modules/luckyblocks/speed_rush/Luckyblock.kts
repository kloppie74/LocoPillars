// Speed Rush — super speed and jump boost for 30 seconds
game.giveEffect(player, org.bukkit.potion.PotionEffectType.SPEED, 30, 3)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.JUMP_BOOST, 30, 3)
game.spawnParticles(player.location, org.bukkit.Particle.SWEEP_ATTACK, 15)
game.playSound(player, org.bukkit.Sound.ENTITY_HORSE_GALLOP, 1.0f, 1.5f)
game.sendTitle(player, "§a⚡ Speed Rush!", "§730 seconds of turbo!", 5, 50, 10)
player.sendMessage("§a§l⚡ Speed Rush! §r§7You feel incredibly fast!")
