// Wither Awakens — spawns a Wither 5 blocks above
val spawnLoc = player.location.clone().add(0.0, 5.0, 0.0)
game.spawnMob(org.bukkit.entity.EntityType.WITHER, spawnLoc)
game.broadcastToGame("§4§l☠ WITHER AWAKENS! §r§c${player.name} §4has unleashed a Wither!")
game.sendTitle(player, "§4☠ WITHER AWAKENS!", "§cRUN!", 5, 60, 10)
player.sendMessage("§4§l☠ Wither Awakens! §r§cYou've unleashed something terrible...")
game.playSound(player, org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f)
