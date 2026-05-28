// ============================================================
//  Floor is Lava — rising lava blocks
//  Lava fills one layer every 10 seconds within the arena,
//  rising from just above the death boundary up to the build
//  ceiling.  Placed blocks are removed on cleanup; any overflow
//  outside the arena bounds is swept by ArenaManager before the
//  schematic is re-pasted.
//
//  NOTE: arena geometry is read via the game.getArena*() helpers
//  rather than game.instance.arenaInstance.* because those inner
//  classes are obfuscated and their members are not resolvable
//  from runtime-compiled scripts.
// ============================================================

val world       = game.getArenaWorld()
val centerX     = game.getArenaCenterX()
val centerZ     = game.getArenaCenterZ()
val deathY      = game.getArenaDeathHeight()
val buildHeight = game.getArenaBuildHeight()
val buildRange  = game.getArenaBuildRange()

// --- Announce ---
game.sendTitle("§6§lFloor is Lava!", "§7The lava is rising!", 10, 60, 10)
game.broadcast("§6[Event] §eFloor is Lava! Lava is rising — climb higher!")

game.getPlayers().forEach { p ->
    p.playSound(p.location, org.bukkit.Sound.BLOCK_LAVA_AMBIENT, 1.0f, 1.0f)
}

// --- State ---
val placedBlocks = mutableListOf<org.bukkit.block.Block>()
var lavaY: Int = maxOf(world.minHeight, deathY + 2)

// --- Rise: 1 level every 10 s (200 ticks), 5 s initial delay (100 ticks) ---
game.scheduleRepeating(100L, 200L) {
    if (lavaY >= buildHeight - 1) return@scheduleRepeating

    // Fill every air-like block in the current layer with static lava
    // (applyPhysics=false prevents the lava from immediately flowing outward)
    for (bx in (centerX - buildRange)..(centerX + buildRange)) {
        for (bz in (centerZ - buildRange)..(centerZ + buildRange)) {
            val block = world.getBlockAt(bx, lavaY, bz)
            val mat   = block.type
            if (mat == org.bukkit.Material.AIR       ||
                mat == org.bukkit.Material.CAVE_AIR  ||
                mat == org.bukkit.Material.VOID_AIR  ||
                mat == org.bukkit.Material.WATER) {
                block.setType(org.bukkit.Material.LAVA, false)
                placedBlocks.add(block)
            }
        }
    }

    // Proximity warnings & ambient sound for each alive player
    game.getPlayers().forEach { p ->
        val gap: Double = p.location.y - lavaY.toDouble()
        when {
            gap <= 3.0  -> p.sendTitle("§c§l⚠ LAVA!", "§cGet higher NOW!", 0, 50, 10)
            gap <= 8.0  -> p.sendTitle("§e§lWARNING!", "§eLava rising below you!", 0, 40, 10)
            gap <= 15.0 -> p.sendTitle("", "§7Lava rising... (Y $lavaY)", 0, 25, 5)
        }
        // Volume scales with proximity (louder the closer the lava is)
        val vol: Float = 1.0f - (gap.toFloat() / 25f).coerceIn(0f, 0.85f)
        p.playSound(p.location, org.bukkit.Sound.BLOCK_LAVA_AMBIENT, vol, 1.0f)
    }

    lavaY++
}

// --- Cleanup: remove all blocks we placed when the event ends ---
game.onCleanup {
    placedBlocks.forEach { block ->
        if (block.type == org.bukkit.Material.LAVA) {
            block.setType(org.bukkit.Material.AIR, false)
        }
    }
    placedBlocks.clear()
}
