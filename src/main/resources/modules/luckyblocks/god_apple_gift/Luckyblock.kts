// God Apple Gift — the legendary enchanted golden apple
game.giveItem(player, org.bukkit.inventory.ItemStack(org.bukkit.Material.ENCHANTED_GOLDEN_APPLE))
game.spawnParticles(player.location, org.bukkit.Particle.TOTEM_OF_UNDYING, 30)
game.playSound(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f)
game.sendTitle(player, "§6★ God Apple!", "§7The legends were true!", 5, 60, 10)
player.sendMessage("§6§l★ God Apple! §r§7You found the rarest treat in the land!")
