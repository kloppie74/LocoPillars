// Double Strength — Strength + Resistance combo
game.giveEffect(player, org.bukkit.potion.PotionEffectType.STRENGTH, 20, 1)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.RESISTANCE, 20, 1)
game.spawnParticles(player.location, org.bukkit.Particle.FLAME, 20)
game.playSound(player, org.bukkit.Sound.ENTITY_EVOKER_CAST_SPELL, 1.0f, 0.8f)
game.sendTitle(player, "§c💪 Double Strength!", "§7Strong AND tough!", 5, 50, 10)
player.sendMessage("§c§l💪 Double Strength! §r§7You are unstoppable for 20 seconds!")
