// Leap Launch — launches the player upward with slow fall
player.velocity = org.bukkit.util.Vector(0.0, 2.5, 0.0)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.SLOW_FALLING, 8, 0)
game.playSound(player, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 0.5f)
game.sendTitle(player, "§e↑ Launch!", "§7To the moon!", 5, 40, 10)
player.sendMessage("§e§l↑ Leap Launch! §r§7Wheeeee! (Slow fall included)")
