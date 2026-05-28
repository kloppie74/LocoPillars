// Speed Rush — gives all players permanent Speed II for the whole game.

game.sendTitle("§e§lSpeed Rush!", "§7Everyone has Speed II!", 10, 60, 10)
game.broadcast("§e[Event] §eSpeed Rush! Everyone moves at twice the speed this round!")

game.getPlayers().forEach { player ->
    player.addPotionEffect(
        org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, Integer.MAX_VALUE, 1, false, false)
    )
    player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f)
}

game.onCleanup {
    game.getPlayers().forEach { player ->
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.SPEED)
    }
}
