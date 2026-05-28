// Wolf Pack — 4 tamed wolves loyal to the player
val wolves = game.spawnMobs(org.bukkit.entity.EntityType.WOLF, player.location, 4)
wolves.forEach { entity ->
    val wolf = entity as? org.bukkit.entity.Wolf ?: return@forEach
    wolf.isTamed = true
    wolf.owner = player
    wolf.isAngry = false
}
game.playSound(player, org.bukkit.Sound.ENTITY_WOLF_GROWL, 1.0f, 1.0f)
game.sendTitle(player, "§6🐺 Wolf Pack!", "§74 wolves join your side!", 5, 50, 10)
player.sendMessage("§6§l🐺 Wolf Pack! §r§7Your loyal wolf pack is ready to fight!")
