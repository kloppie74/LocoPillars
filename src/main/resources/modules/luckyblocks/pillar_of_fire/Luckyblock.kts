// Pillar of Fire — cross pattern of lightning strikes
val loc = player.location
listOf(1.0 to 0.0, 2.0 to 0.0, 3.0 to 0.0,
       -1.0 to 0.0, -2.0 to 0.0, -3.0 to 0.0,
       0.0 to 1.0, 0.0 to 2.0, 0.0 to 3.0,
       0.0 to -1.0, 0.0 to -2.0, 0.0 to -3.0).forEach { (dx, dz) ->
    game.spawnLightningEffect(loc.clone().add(dx, 0.0, dz))
}
game.giveEffect(player, org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 10, 0)
game.spawnParticles(loc, org.bukkit.Particle.FLAME, 40)
game.sendTitle(player, "§6🔥 Pillar of Fire!", "§7The cross ignites!", 5, 50, 10)
player.sendMessage("§6§l🔥 Pillar of Fire! §r§7Lightning forms a fiery cross!")
