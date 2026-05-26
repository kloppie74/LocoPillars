// Rocket Launch — a power-2 firework launches from the kill location
val loc = victim?.location?.clone() ?: player.location.clone()
val world = loc.world ?: player.world
val fw = world.spawnEntity(loc.clone(), org.bukkit.entity.EntityType.FIREWORK_ROCKET) as? org.bukkit.entity.Firework
fw?.let {
    val meta = it.fireworkMeta
    val randomColour = org.bukkit.Color.fromRGB((Math.random() * 16777215).toInt())
    meta.addEffect(
        org.bukkit.FireworkEffect.builder()
            .withColor(randomColour)
            .withFade(org.bukkit.Color.WHITE)
            .withTrail()
            .withFlicker()
            .with(org.bukkit.FireworkEffect.Type.values().random())
            .build()
    )
    meta.power = 2
    it.fireworkMeta = meta
    it.velocity = org.bukkit.util.Vector(0.0, 1.5, 0.0)
}
game.spawnParticles(loc.clone().add(0.0, 0.5, 0.0), org.bukkit.Particle.SMOKE, 10, 0.2, 0.0, 0.2, 0.04)
game.playSound(player, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.0f)
game.sendTitle(player, "§6§lKILL!", "§eRocket Launch", 0, 25, 10)
