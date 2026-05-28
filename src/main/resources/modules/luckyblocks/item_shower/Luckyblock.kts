// Item Shower — random items drop from above
val randomMaterials = listOf(
    org.bukkit.Material.IRON_INGOT, org.bukkit.Material.COAL, org.bukkit.Material.STRING,
    org.bukkit.Material.ARROW, org.bukkit.Material.BREAD, org.bukkit.Material.BONE,
    org.bukkit.Material.FEATHER, org.bukkit.Material.WHEAT, org.bukkit.Material.APPLE,
    org.bukkit.Material.STICK
)
repeat(8) {
    val mat = randomMaterials.random()
    val item = org.bukkit.inventory.ItemStack(mat, (1..6).random())
    player.world.dropItemNaturally(player.location.clone().add(Math.random() * 4 - 2, 2.0, Math.random() * 4 - 2), item)
}
game.playSound(player, org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1.0f, 0.8f)
game.sendTitle(player, "§6📦 Item Shower!", "§7Free stuff!", 5, 40, 10)
player.sendMessage("§6§l📦 Item Shower! §r§7Random items rain down around you!")
