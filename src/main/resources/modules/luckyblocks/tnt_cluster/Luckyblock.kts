// TNT Cluster — 4 explosions in cardinal directions around the player
game.giveEffect(player, org.bukkit.potion.PotionEffectType.RESISTANCE, 3, 4)
val loc = player.location
listOf(4.0 to 0.0, -4.0 to 0.0, 0.0 to 4.0, 0.0 to -4.0).forEach { (dx, dz) ->
    game.spawnExplosion(loc.clone().add(dx, 0.0, dz), 2.5f, false)
}
game.sendTitle(player, "§c💥💥 TNT Cluster!", "§7KABOOM x4!", 5, 40, 10)
player.sendMessage("§c§l💥 TNT Cluster! §r§7Explosions in all directions!")
