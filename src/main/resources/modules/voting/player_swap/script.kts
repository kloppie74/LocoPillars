// Player Swap — rotates all player positions every 20 seconds.

game.sendTitle("§5§lPlayer Swap!", "§7Positions shuffle every 20s!", 10, 60, 10)
game.broadcast("§5[Event] §ePlayer Swap! Everyone will be teleported to a random position every 20 seconds!")

game.getPlayers().forEach { player ->
    player.playSound(player.location, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.5f)
}

// First swap after 20s, then every 20s
game.scheduleRepeating(400L, 400L) {
    val players = game.getPlayers()
    if (players.size < 2) return@scheduleRepeating

    val shuffled = players.shuffled()
    val locations = shuffled.map { it.location.clone() }
    // Rotate: each player goes to the next player's former position
    shuffled.forEachIndexed { i, player ->
        player.teleport(locations[(i + 1) % shuffled.size])
        player.playSound(player.location, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.2f)
    }
    game.broadcast("§5[Event] §ePositions have been swapped!")
}
