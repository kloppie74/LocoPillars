// Random Teleport — warps the player to a random spot within 60 blocks
game.randomTeleport(player, 60.0)
game.playSound(player, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
game.spawnParticles(player.location, org.bukkit.Particle.PORTAL, 30)
game.sendTitle(player, "§b↯ Warped!", "§7Somewhere over the rainbow...", 5, 50, 10)
player.sendMessage("§b§l↯ Random Teleport! §r§7You've been flung into the unknown!")
