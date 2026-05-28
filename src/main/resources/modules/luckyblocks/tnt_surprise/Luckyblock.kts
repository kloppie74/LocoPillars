// TNT Surprise — brief resistance then kaboom!
game.giveEffect(player, org.bukkit.potion.PotionEffectType.RESISTANCE, 3, 4)
game.spawnExplosion(player.location.clone().add(0.0, 1.0, 0.0), 3.5f, false)
game.sendTitle(player, "§c💥 BOOM!", "§7The block explodes!", 5, 40, 10)
player.sendMessage("§c§l💥 TNT Surprise! §r§7Should have seen that coming...")
