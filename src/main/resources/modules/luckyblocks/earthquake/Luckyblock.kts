// Earthquake — solid ground nearby turns to gravel
val loc = player.location
repeat(25) {
    val dx = Math.random() * 12 - 6
    val dz = Math.random() * 12 - 6
    val target = loc.clone().add(dx, -1.0, dz)
    if (target.block.type.isSolid && target.block.type != org.bukkit.Material.BEDROCK) {
        game.setBlock(target, org.bukkit.Material.GRAVEL)
    }
}
game.spawnParticles(loc, org.bukkit.Particle.SMOKE, 40)
game.playSound(player, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.4f)
game.sendTitle(player, "§8🌍 Earthquake!", "§7The ground crumbles!", 5, 50, 10)
player.sendMessage("§8§l🌍 Earthquake! §r§7The earth shatters around you!")
