// Elytra Mode — gives every player an elytra and prevents them from equipping a chestplate.
//
// NOTE: we call inventory.setChestplate(...) / inventory.getChestplate() explicitly
// instead of using the `chestplate` property. In this Paper API the getter and setter
// have mismatched nullability, so Kotlin exposes `chestplate` as a read-only val —
// property assignment fails to compile. Clearing uses an AIR ItemStack rather than
// null because the setter parameter is non-null.

val elytra = ItemStack(org.bukkit.Material.ELYTRA)
val empty  = ItemStack(org.bukkit.Material.AIR)

game.sendTitle("§b§lElytra Mode!", "§7Glide your way to victory!", 10, 60, 10)
game.broadcast("§b[Event] §eElytra Mode active! Everyone gets an elytra — no chestplate allowed!")

game.getPlayers().forEach { player ->
    player.inventory.setChestplate(elytra.clone())
    player.updateInventory()
    player.playSound(player.location, org.bukkit.Sound.ITEM_ARMOR_EQUIP_ELYTRA, 1.0f, 1.0f)
}

// Every 5 ticks: restore elytra if a player managed to remove it
game.scheduleRepeating(0L, 5L) {
    game.getPlayers().forEach { player ->
        val current = player.inventory.chestplate
        if (current == null || current.type != org.bukkit.Material.ELYTRA) {
            player.inventory.setChestplate(elytra.clone())
            player.updateInventory()
        }
    }
}

// Restore to an empty chestplate slot on cleanup
game.onCleanup {
    game.getPlayers().forEach { player ->
        val current = player.inventory.chestplate
        if (current != null && current.type == org.bukkit.Material.ELYTRA) {
            player.inventory.setChestplate(empty.clone())
            player.updateInventory()
        }
    }
}
