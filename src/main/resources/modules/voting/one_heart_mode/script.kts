// One Heart Mode — reduces every player's max health to 2 HP (1 heart).
//
// Uses the modern Attribute API (Attribute.MAX_HEALTH) instead of the deprecated
// Player.setMaxHealth(...) so the script compiles cleanly against Paper 1.21.x.

game.sendTitle("§4§lOne Heart Mode!", "§7One hit and you're dead!", 10, 60, 10)
game.broadcast("§4[Event] §eOne Heart Mode! Everyone has only 1 heart. Be careful!")

game.getPlayers().forEach { player ->
    player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue = 2.0
    player.health = 2.0
    player.playSound(player.location, org.bukkit.Sound.BLOCK_ANVIL_LAND, 0.5f, 1.5f)
}

// Restore original max health when the game ends
game.onCleanup {
    game.getPlayers().forEach { player ->
        player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.baseValue = 20.0
        if (player.health > 20.0) player.health = 20.0
    }
}
