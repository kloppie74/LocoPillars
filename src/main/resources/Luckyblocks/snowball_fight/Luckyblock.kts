// Snowball Fight — 64 snowballs, let the chaos begin
game.giveItem(player, org.bukkit.inventory.ItemStack(org.bukkit.Material.SNOWBALL, 64))
game.spawnParticles(player.location, org.bukkit.Particle.SNOWFLAKE, 25)
game.playSound(player, org.bukkit.Sound.BLOCK_SNOW_PLACE, 1.0f, 1.5f)
game.sendTitle(player, "§b❄ Snowball Fight!", "§764 snowballs ready!", 5, 40, 10)
player.sendMessage("§b§l❄ Snowball Fight! §r§7Throw them at your enemies!")
