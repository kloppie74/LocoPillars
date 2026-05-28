// Chaos Swap — teleport-swap with a random other player
val others = game.getPlayers().filter { it.uniqueId != player.uniqueId }
val target = others.randomOrNull()

if (target != null) {
    val playerLoc = player.location.clone()
    val targetLoc = target.location.clone()
    player.teleport(targetLoc)
    target.teleport(playerLoc)
    game.playSound(player, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
    game.playSound(target, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f)
    game.sendTitle(player, "§e⇄ Swapped!", "§7With ${target.name}!", 5, 40, 10)
    game.sendTitle(target, "§e⇄ Swapped!", "§7With ${player.name}!", 5, 40, 10)
    player.sendMessage("§e§l⇄ Chaos Swap! §r§7You swapped with §e${target.name}§7!")
    target.sendMessage("§e§l⇄ Chaos Swap! §r§7You swapped with §e${player.name}§7!")
} else {
    game.giveEffect(player, org.bukkit.potion.PotionEffectType.SPEED, 15, 2)
    game.sendTitle(player, "§e⇄ Nobody to swap with!", "§7Enjoy the speed boost instead!", 5, 40, 10)
    player.sendMessage("§e§l⇄ Chaos Swap! §r§7No other players... have some speed!")
}
