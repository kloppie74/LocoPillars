// Baby Creeper — spawns a baby (non-silent) creeper
val creeper = game.spawnMob(org.bukkit.entity.EntityType.CREEPER, player.location.clone().add(1.0, 0.0, 1.0))
if (creeper is org.bukkit.entity.Creeper) {
    creeper.isPowered = true
}
game.sendTitle(player, "§a💀 Baby Creeper!", "§7It's so... cute?", 5, 40, 10)
player.sendMessage("§a§l💀 Baby Creeper! §r§7Don't let it fool you!")
game.playSound(player, org.bukkit.Sound.ENTITY_CREEPER_HURT, 0.8f, 1.5f)
