// Sand Avalanche — sand items fall from above in a random spread
val loc = player.location
repeat(12) {
    val x = Math.random() * 6 - 3
    val z = Math.random() * 6 - 3
    val dropLoc = loc.clone().add(x, 10.0, z)
    player.world.dropItem(dropLoc, org.bukkit.inventory.ItemStack(org.bukkit.Material.SAND, (1..4).random()))
}
game.playSound(player, org.bukkit.Sound.BLOCK_GRAVEL_BREAK, 1.0f, 0.8f)
game.sendTitle(player, "§e🏜 Sand Avalanche!", "§7Watch out above!", 5, 40, 10)
player.sendMessage("§e§l🏜 Sand Avalanche! §r§7The sky is falling... as sand!")
