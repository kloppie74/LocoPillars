// Ultra Jump — gives all players permanent Jump Boost III for the whole game.

game.sendTitle("§a§lUltra Jump!", "§7Jump Boost III activated!", 10, 60, 10)
game.broadcast("§a[Event] §eUltra Jump! Everyone has Jump Boost III this round!")

game.getPlayers().forEach { player ->
    player.addPotionEffect(
        org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 2, false, false)
    )
    player.playSound(player.location, org.bukkit.Sound.ENTITY_SLIME_JUMP, 1.0f, 0.8f)
}

game.onCleanup {
    game.getPlayers().forEach { player ->
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.JUMP_BOOST)
    }
}
