// Mass Teleport — every player in the game is teleported randomly
game.getPlayers().forEach { p ->
    game.randomTeleport(p, 80.0)
    game.playSound(p, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
    game.spawnParticles(p.location, org.bukkit.Particle.PORTAL, 20)
}
game.broadcastToGame("§b§l↯ Mass Teleport! §r§b${player.name} §7scattered everyone!")
game.sendTitle(player, "§b↯ Mass Teleport!", "§7You scattered everyone!", 5, 50, 10)
