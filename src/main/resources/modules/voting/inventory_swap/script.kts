// Inventory Swap — rotates all players' storage inventories every 30 seconds.

game.sendTitle("§d§lInventory Swap!", "§7Inventories shuffle every 30s!", 10, 60, 10)
game.broadcast("§d[Event] §eInventory Swap! Everyone's items will be shuffled every 30 seconds!")

game.getPlayers().forEach { player ->
    player.playSound(player.location, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f)
}

// First swap after 30s, then every 30s
game.scheduleRepeating(600L, 600L) {
    val players = game.getPlayers()
    if (players.size < 2) return@scheduleRepeating

    val shuffled = players.shuffled()
    // Snapshot all storage contents before swapping
    val snapshots = shuffled.map { player ->
        player.inventory.storageContents.map { it?.clone() }.toTypedArray()
    }
    // Rotate: each player gets the next player's inventory
    shuffled.forEachIndexed { i, player ->
        player.inventory.storageContents = snapshots[(i + 1) % shuffled.size]
        player.updateInventory()
    }
    game.broadcast("§d[Event] §eInventories have been swapped!")
    shuffled.forEach { player ->
        player.playSound(player.location, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.8f)
    }
}
