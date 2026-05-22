// Pig Ride — spawns a saddled pig and makes the player ride it
val pig = game.spawnMob(org.bukkit.entity.EntityType.PIG, player.location)
if (pig is org.bukkit.entity.Pig) {
    pig.inventory.saddle = org.bukkit.inventory.ItemStack(org.bukkit.Material.SADDLE)
    pig.addPassenger(player)
    game.giveItem(player, org.bukkit.inventory.ItemStack(org.bukkit.Material.CARROT_ON_A_STICK))
}
game.playSound(player, org.bukkit.Sound.ENTITY_PIG_SADDLE, 1.0f, 1.0f)
game.sendTitle(player, "§d🐷 Pig Ride!", "§7Giddy up!", 5, 40, 10)
player.sendMessage("§d§l🐷 Pig Ride! §r§7Use a carrot on a stick to steer!")
