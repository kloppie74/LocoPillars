// Arrow Shower — arrows rain down around the kill location
val loc = victim?.location?.clone() ?: player.location.clone()
val world = loc.world ?: player.world
for (i in 0..5) {
    val offsetX = (Math.random() - 0.5) * 2.0
    val offsetZ = (Math.random() - 0.5) * 2.0
    val spawnLoc = loc.clone().add(offsetX, 4.0, offsetZ)
    val arrow = world.spawnEntity(spawnLoc, org.bukkit.entity.EntityType.ARROW) as? org.bukkit.entity.Arrow
    arrow?.velocity = org.bukkit.util.Vector(offsetX * -0.1, -0.8, offsetZ * -0.1)
    arrow?.shooter = null
    // Auto-remove arrow after 2 seconds
    game.scheduleTask(40L) { arrow?.remove() }
}
game.spawnParticles(loc.clone().add(0.0, 1.0, 0.0), org.bukkit.Particle.CRIT, 20, 0.5, 0.5, 0.5, 0.1)
game.playSound(player, org.bukkit.Sound.ENTITY_ARROW_HIT, 0.7f, 0.8f)
game.sendTitle(player, "§e§lKILL!", "§6Arrow Shower", 0, 25, 10)
