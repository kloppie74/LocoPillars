// Thunder God — massive storm + full combat buffing
val loc = player.location
repeat(20) {
    val angle = Math.random() * Math.PI * 2
    val dist = Math.random() * 8
    val offset = loc.clone().add(Math.cos(angle) * dist, 0.0, Math.sin(angle) * dist)
    game.spawnLightningEffect(offset)
}
game.spawnLightning(loc.clone().add(0.0, 5.0, 0.0))
// Full god-mode buffs
game.giveEffect(player, org.bukkit.potion.PotionEffectType.STRENGTH, 45, 2)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.RESISTANCE, 45, 2)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.SPEED, 45, 2)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.REGENERATION, 45, 1)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.ABSORPTION, 45, 3)
game.broadcastToGame("§e§l⚡ THUNDER GOD! §r§e${player.name} §7has claimed divine power!")
game.sendTitle(player, "§e⚡ THUNDER GOD!", "§7The sky bows to you!", 5, 70, 10)
player.sendMessage("§e§l⚡ Thunder God! §r§7You are unstoppable for 45 seconds!")
