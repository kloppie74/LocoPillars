// Double Health — sets every player's max health to 40 HP (20 hearts).
//
// Uses the modern Attribute API (Attribute.MAX_HEALTH) instead of the deprecated
// Player.setMaxHealth(...) so the script compiles cleanly against Paper 1.21.x.

game.sendTitle("§c§lDouble Health!", "§7You now have 20 hearts!", 10, 60, 10)
game.broadcast("§c[Event] §eDouble Health active! Everyone now has 20 hearts!")

game.getPlayers().forEach { player ->
    player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue = 40.0
    player.health = 40.0
    player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.8f)
}

// Restore original max health when the game ends
game.onCleanup {
    game.getPlayers().forEach { player ->
        player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue = 20.0
        if (player.health > 20.0) player.health = 20.0
    }
}
