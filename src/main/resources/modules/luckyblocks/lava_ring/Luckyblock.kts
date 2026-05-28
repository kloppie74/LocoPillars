// Lava Ring — places lava in a ring 4 blocks away
game.giveEffect(player, org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 20, 0)
val loc = player.location
repeat(16) {
    val angle = it * (Math.PI * 2 / 16)
    val target = loc.clone().add(Math.cos(angle) * 4, -1.0, Math.sin(angle) * 4)
    game.setBlock(target, org.bukkit.Material.LAVA)
}
game.sendTitle(player, "§c🌋 Lava Ring!", "§7You're fireproof... they aren't!", 5, 50, 10)
player.sendMessage("§c§l🌋 Lava Ring! §r§7A ring of lava surrounds you!")
game.playSound(player, org.bukkit.Sound.BLOCK_LAVA_POP, 1.0f, 0.8f)
