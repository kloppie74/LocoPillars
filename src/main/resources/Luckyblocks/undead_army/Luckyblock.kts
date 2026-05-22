// Undead Army — massive undead horde
val loc = player.location
game.spawnMobs(org.bukkit.entity.EntityType.ZOMBIE, loc, 8)
game.spawnMobs(org.bukkit.entity.EntityType.SKELETON, loc, 6)
game.spawnMobs(org.bukkit.entity.EntityType.WITHER_SKELETON, loc, 4)
game.spawnMobs(org.bukkit.entity.EntityType.ZOMBIE_VILLAGER, loc, 3)
game.spawnMobs(org.bukkit.entity.EntityType.DROWNED, loc, 3)
// Give player brief resistance to survive initial shock
game.giveEffect(player, org.bukkit.potion.PotionEffectType.RESISTANCE, 3, 4)
game.broadcastToGame("§8§l☠ UNDEAD ARMY! §r§8${player.name} §7has raised the dead!")
game.sendTitle(player, "§8☠ UNDEAD ARMY!", "§724 undead attack!", 5, 70, 10)
player.sendMessage("§8§l☠ Undead Army! §r§7You've raised an army of 24 undead!")
game.playSound(player, org.bukkit.Sound.ENTITY_WITHER_DEATH, 0.8f, 0.5f)
