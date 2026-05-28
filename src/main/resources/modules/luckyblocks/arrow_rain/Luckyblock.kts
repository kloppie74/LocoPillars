// Arrow Rain — arrows fall from 10 blocks above in a small area
val loc = player.location
val world = loc.world
if (world != null) {
    repeat(15) {
        val x = Math.random() * 8 - 4
        val z = Math.random() * 8 - 4
        val spawnLoc = loc.clone().add(x, 10.0, z)
        val arrow = world.spawnArrow(spawnLoc, org.bukkit.util.Vector(0.0, -1.0, 0.0), 1.5f, 2.0f)
        arrow.shooter = player
    }
}
game.playSound(player, org.bukkit.Sound.ENTITY_ARROW_SHOOT, 1.0f, 0.8f)
game.sendTitle(player, "§c↓ Arrow Rain!", "§7Duck!", 5, 40, 10)
player.sendMessage("§c§l↓ Arrow Rain! §r§7Arrows fall from the sky!")
