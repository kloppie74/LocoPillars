// Dragon Rider — spawn an Ender Dragon, mount the winner on it for 6 seconds
val spawnLoc = player.location.clone().add(0.0, 6.0, 0.0)
val world = spawnLoc.world ?: player.world
val dragon = world.spawnEntity(spawnLoc, org.bukkit.entity.EntityType.ENDER_DRAGON) as? org.bukkit.entity.EnderDragon
dragon?.let { d ->
    d.phase = org.bukkit.entity.EnderDragon.Phase.HOVER
    d.isInvulnerable = true
    d.isSilent = false
    d.addPassenger(player)
    game.sendTitle(player, "§5§l🐉 VICTORY! 🐉", "§dYou're riding the dragon!", 5, 90, 15)
    game.broadcastToGame("§5§l★ §d${player.name} §5is riding an Ender Dragon! §5§l★")
    game.playSound(player, org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.9f, 0.8f)
    // After 5 seconds: eject & remove dragon
    game.scheduleTask(100L) {
        d.passengers.toList().forEach { d.removePassenger(it) }
        d.remove()
        game.playSound(player, org.bukkit.Sound.ENTITY_ENDER_DRAGON_DEATH, 0.7f, 1.2f)
        // Celebrate after dismount
        for (j in 0..4) {
            game.spawnFirework(player.location.clone().add(
                (Math.random() - 0.5) * 4, 0.5, (Math.random() - 0.5) * 4))
        }
    }
}
