// Lightning Storm — ring of lightning strikes, then one direct hit
val loc = player.location
repeat(8) {
    val angle = it * (Math.PI * 2 / 8)
    val offset = loc.clone().add(Math.cos(angle) * 4, 0.0, Math.sin(angle) * 4)
    game.spawnLightningEffect(offset)
}
game.spawnLightning(loc)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.SLOWNESS, 3, 1)
game.sendTitle(player, "§e⚡ Lightning Storm!", "§7The sky splits open!", 5, 50, 10)
player.sendMessage("§e§l⚡ Lightning Storm! §r§7You're caught in the storm!")
