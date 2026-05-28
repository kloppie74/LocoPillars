// Firework Frenzy — launches a burst of colorful fireworks around the player
repeat(5) {
    val offset = player.location.clone().add(
        Math.random() * 4 - 2,
        0.0,
        Math.random() * 4 - 2
    )
    game.spawnFirework(offset)
}
game.playSound(player, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f)
game.sendTitle(player, "§6★ Firework Frenzy!", "§7Celebrate with colorful explosions!", 5, 50, 10)
player.sendMessage("§6§l★ Firework Frenzy! §r§7The sky is yours!")
