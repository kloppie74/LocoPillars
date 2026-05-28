// Dirt Tower — builds a 5-block dirt column and teleports player on top
val base = player.location.block.location
for (i in 0..4) {
    game.setBlock(base.clone().add(0.0, i.toDouble(), 0.0), org.bukkit.Material.DIRT)
}
player.teleport(base.clone().add(0.5, 5.5, 0.5))
game.playSound(player, org.bukkit.Sound.BLOCK_GRASS_PLACE, 1.0f, 1.0f)
game.sendTitle(player, "§a⬆ Dirt Tower!", "§7Up, up, up!", 5, 40, 10)
player.sendMessage("§a§l⬆ Dirt Tower! §r§7A classic!")
