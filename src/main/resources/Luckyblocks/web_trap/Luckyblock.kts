// Web Trap — places cobwebs in a small ring around the player
val loc = player.location
for (x in -1..1) {
    for (z in -1..1) {
        if (x == 0 && z == 0) continue
        val target = loc.clone().add(x.toDouble(), 0.0, z.toDouble())
        if (target.block.type == org.bukkit.Material.AIR) {
            game.setBlock(target, org.bukkit.Material.COBWEB)
        }
    }
}
game.sendTitle(player, "§7🕸 Web Trap!", "§7Sticky...", 5, 40, 10)
player.sendMessage("§7§l🕸 Web Trap! §r§7You're stuck in a web!")
game.playSound(player, org.bukkit.Sound.BLOCK_COBWEB_BREAK, 1.0f, 0.5f)
