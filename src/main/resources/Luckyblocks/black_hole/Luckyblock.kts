// Black Hole — pulls all nearby players toward the breaker
val center = player.location
val world = center.world
if (world != null) {
    val nearby = world.getNearbyEntities(center, 15.0, 15.0, 15.0)
    nearby.filterIsInstance<org.bukkit.entity.Player>()
        .filter { it.uniqueId != player.uniqueId }
        .forEach { p ->
            val dir = center.toVector().subtract(p.location.toVector()).normalize().multiply(2.0)
            p.velocity = dir
            game.sendTitle(p, "§8🌀 Black Hole!", "§7You're being pulled in!", 5, 30, 5)
        }
}
game.spawnParticles(center, org.bukkit.Particle.PORTAL, 80)
game.giveEffect(player, org.bukkit.potion.PotionEffectType.RESISTANCE, 5, 4)
game.broadcastToGame("§8§l🌀 BLACK HOLE! §r§8${player.name} §7has become a singularity!")
game.sendTitle(player, "§8🌀 Black Hole!", "§7Everything orbits around you!", 5, 60, 10)
player.sendMessage("§8§l🌀 Black Hole! §r§7You pull all nearby players toward you!")
