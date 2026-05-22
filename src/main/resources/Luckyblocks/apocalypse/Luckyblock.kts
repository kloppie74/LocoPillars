// Apocalypse — total chaos unleashed upon the world
val loc = player.location

// Massive central explosion
game.spawnExplosion(loc, 6.0f, false)

// Lightning everywhere in a 20-block radius
repeat(25) {
    val angle = Math.random() * Math.PI * 2
    val dist = Math.random() * 20
    val offset = loc.clone().add(Math.cos(angle) * dist, 0.0, Math.sin(angle) * dist)
    game.spawnLightning(offset)
}

// Mob army
game.spawnMobs(org.bukkit.entity.EntityType.ZOMBIE, loc, 10)
game.spawnMobs(org.bukkit.entity.EntityType.SKELETON, loc, 8)
game.spawnMobs(org.bukkit.entity.EntityType.CREEPER, loc, 5)
game.spawnMobs(org.bukkit.entity.EntityType.WITHER_SKELETON, loc, 4)
game.spawnMobs(org.bukkit.entity.EntityType.SPIDER, loc, 4)

// All players get blinded and nausea
game.getPlayers().forEach { p ->
    game.giveEffect(p, org.bukkit.potion.PotionEffectType.BLINDNESS, 5, 0)
    game.giveEffect(p, org.bukkit.potion.PotionEffectType.NAUSEA, 5, 0)
    game.sendTitle(p, "§4☠ APOCALYPSE!", "§c${player.name} broke it...", 5, 60, 10)
}

game.broadcastToGame("§4§l☠ ☠ ☠ APOCALYPSE ☠ ☠ ☠ §r§c${player.name} §4has unleashed the apocalypse!")
player.sendMessage("§4§l☠ Apocalypse! §r§cCongratulations on destroying everything.")
game.playSound(player, org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f)
