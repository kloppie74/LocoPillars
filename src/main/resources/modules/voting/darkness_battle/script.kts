// Darkness Battle — applies a Blindness + Darkness pulse to all players every 8 seconds.

game.sendTitle("§8§lDarkness Battle!", "§7You can barely see anything!", 10, 60, 10)
game.broadcast("§8[Event] §eDarkness Battle! Blindness pulses every 8 seconds — fight in the dark!")

game.getPlayers().forEach { player ->
    player.playSound(player.location, org.bukkit.Sound.AMBIENT_CAVE, 1.0f, 0.5f)
}

// Apply immediately and then every 8 seconds (160 ticks)
game.scheduleRepeating(0L, 160L) {
    game.getPlayers().forEach { player ->
        player.addPotionEffect(
            org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 170, 0, false, false)
        )
        player.addPotionEffect(
            org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS, 170, 0, false, false)
        )
    }
}

game.onCleanup {
    game.getPlayers().forEach { player ->
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)
        player.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS)
    }
}
