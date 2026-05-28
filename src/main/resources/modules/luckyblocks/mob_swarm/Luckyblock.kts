// Mob Swarm — spawns hostile mobs at the player's location
val loc = player.location
game.spawnMobs(org.bukkit.entity.EntityType.ZOMBIE, loc, 4)
game.spawnMobs(org.bukkit.entity.EntityType.SKELETON, loc, 3)
game.spawnMobs(org.bukkit.entity.EntityType.SPIDER, loc, 2)
game.sendTitle(player, "§c☠ Mob Swarm!", "§7They're everywhere!", 5, 50, 10)
player.sendMessage("§c§l☠ Mob Swarm! §r§7Monsters surge from the earth!")
game.playSound(player, org.bukkit.Sound.ENTITY_ZOMBIE_AMBIENT, 1.0f, 0.8f)
