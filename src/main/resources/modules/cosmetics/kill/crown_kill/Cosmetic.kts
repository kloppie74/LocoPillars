// Crown of Victory — golden rain effect from above the killer
val base = player.location.clone().add(0.0, 3.0, 0.0)
game.spawnParticles(base, org.bukkit.Particle.TOTEM_OF_UNDYING, 40, 0.6, 0.3, 0.6, 0.15)
game.spawnParticles(player.location.add(0.0, 0.5, 0.0), org.bukkit.Particle.END_ROD, 20, 0.4, 0.8, 0.4, 0.08)
game.playSound(player, org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.0f)
game.sendTitle(player, "§6§lKILL!", "§eCrown of Victory", 0, 30, 10)
