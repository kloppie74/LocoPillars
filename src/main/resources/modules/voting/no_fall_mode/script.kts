// No Fall Mode — disables fall damage for all players in this game instance.

game.sendTitle("§6§lNo Fall Mode!", "§7Fall damage is disabled!", 10, 60, 10)
game.broadcast("§6[Event] §eNo Fall Mode is now active! You can't take fall damage this round.")

game.getPlayers().forEach { player ->
    player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
}

game.onEvent(org.bukkit.event.entity.EntityDamageEvent::class.java) { event ->
    val entity = event.entity
    if (entity is org.bukkit.entity.Player &&
        game.isPlayerInGame(entity) &&
        event.cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL
    ) {
        event.isCancelled = true
    }
}
