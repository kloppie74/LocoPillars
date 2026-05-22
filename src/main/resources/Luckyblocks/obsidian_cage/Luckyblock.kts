// Obsidian Cage — builds a 3x3 obsidian box around the player
val loc = player.location
for (x in -1..1) {
    for (z in -1..1) {
        for (y in -1..3) {
            val outer = (x == -1 || x == 1 || z == -1 || z == 1)
            val topOrBottom = (y == -1 || y == 3)
            if (outer || topOrBottom) {
                game.setBlock(loc.clone().add(x.toDouble(), y.toDouble(), z.toDouble()), org.bukkit.Material.OBSIDIAN)
            }
        }
    }
}
game.sendTitle(player, "§8🔒 Obsidian Cage!", "§7You're trapped!", 5, 50, 10)
player.sendMessage("§8§l🔒 Obsidian Cage! §r§7Good luck breaking that!")
game.playSound(player, org.bukkit.Sound.BLOCK_ANVIL_LAND, 1.0f, 0.5f)
