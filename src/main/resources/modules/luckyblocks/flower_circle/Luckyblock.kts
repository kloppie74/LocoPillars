// Flower Circle — places a ring of random flowers around the player
val flowers = listOf(
    org.bukkit.Material.POPPY, org.bukkit.Material.DANDELION,
    org.bukkit.Material.BLUE_ORCHID, org.bukkit.Material.ALLIUM,
    org.bukkit.Material.CORNFLOWER, org.bukkit.Material.OXEYE_DAISY,
    org.bukkit.Material.AZURE_BLUET, org.bukkit.Material.PINK_TULIP
)
val loc = player.location
repeat(12) {
    val angle = it * (Math.PI * 2 / 12)
    val target = loc.clone().add(Math.cos(angle) * 3, 0.0, Math.sin(angle) * 3)
    if (target.clone().add(0.0, -1.0, 0.0).block.type.isSolid && target.block.type == org.bukkit.Material.AIR) {
        game.setBlock(target, flowers.random())
    }
}
game.spawnParticles(loc, org.bukkit.Particle.HEART, 15)
game.playSound(player, org.bukkit.Sound.ENTITY_BEE_LOOP, 1.0f, 1.0f)
game.sendTitle(player, "§a🌸 Flower Circle!", "§7Nature blooms!", 5, 40, 10)
player.sendMessage("§a§l🌸 Flower Circle! §r§7Beauty surrounds you!")
