// Fireworks Victory — three fireworks burst around the winner
val loc = player.location.clone()
game.spawnFirework(loc.clone().add(1.0, 0.0, 0.0))
game.spawnFirework(loc.clone().add(-1.0, 0.0, 0.0))
game.spawnFirework(loc.clone().add(0.0, 0.5, 1.0))
game.sendTitle(player, "§6§lVICTORY!", "§eFireworks for the champion!", 5, 50, 15)
