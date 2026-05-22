package org.Kloppie74.locoPillars.utils.luckyblocks.engine

import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance

/**
 * Game API injected into every Luckyblock.kts script as `game`.
 * The breaking player is injected as `player`.
 */
class LuckyblockContext(
    val blockLocation: Location,
    private val game: GameInstance?,
    private val plugin: LocoPillars
) {

    // ── Player access ──────────────────────────────────────────────────────────

    fun getPlayers(): List<Player> =
        game?.players?.mapNotNull { Bukkit.getPlayer(it) }
            ?: Bukkit.getOnlinePlayers().toList()

    fun getAlivePlayers(): List<Player> = getPlayers()

    fun randomPlayer(): Player? = getPlayers().randomOrNull()

    // ── World effects ──────────────────────────────────────────────────────────

    fun spawnLightning(location: Location) {
        location.world?.strikeLightning(location)
    }

    fun spawnLightningEffect(location: Location) {
        location.world?.strikeLightningEffect(location)
    }

    fun spawnExplosion(location: Location, power: Float = 3.0f, fire: Boolean = false) {
        location.world?.createExplosion(location, power, fire, false)
    }

    fun spawnMob(type: EntityType, location: Location): Entity? =
        location.world?.spawnEntity(location, type)

    fun spawnMobs(type: EntityType, location: Location, count: Int): List<Entity> {
        val world = location.world ?: return emptyList()
        return (1..count).mapNotNull { world.spawnEntity(location, type) }
    }

    fun spawnFirework(location: Location) {
        val world = location.world ?: return
        val fw = world.spawnEntity(location, EntityType.FIREWORK_ROCKET) as? Firework ?: return
        val meta = fw.fireworkMeta
        meta.addEffect(
            FireworkEffect.builder()
                .withColor(Color.fromRGB((Math.random() * 16777215).toInt()))
                .withFade(Color.fromRGB((Math.random() * 16777215).toInt()))
                .withTrail()
                .with(FireworkEffect.Type.values().random())
                .build()
        )
        meta.power = 1
        fw.fireworkMeta = meta
    }

    fun spawnParticles(location: Location, particle: Particle, count: Int) {
        location.world?.spawnParticle(particle, location, count)
    }

    // ── Player interactions ────────────────────────────────────────────────────

    fun giveEffect(player: Player, type: PotionEffectType, durationSeconds: Int, amplifier: Int = 0) {
        player.addPotionEffect(PotionEffect(type, durationSeconds * 20, amplifier))
    }

    fun removeEffect(player: Player, type: PotionEffectType) {
        player.removePotionEffect(type)
    }

    fun giveItem(player: Player, item: ItemStack) {
        if (player.inventory.firstEmpty() != -1) {
            player.inventory.addItem(item)
        } else {
            player.world.dropItemNaturally(player.location, item)
        }
    }

    fun teleport(player: Player, location: Location) {
        player.teleport(location)
    }

    fun randomTeleport(player: Player, radius: Double = 50.0) {
        val base = player.location
        val x = base.x + (Math.random() * radius * 2 - radius)
        val z = base.z + (Math.random() * radius * 2 - radius)
        val target = Location(base.world, x, base.y, z, base.yaw, base.pitch)
        target.y = (base.world?.getHighestBlockYAt(target) ?: base.blockY).toDouble() + 1.0
        player.teleport(target)
    }

    fun teleportToRandomPlayer(player: Player) {
        val others = getPlayers().filter { it.uniqueId != player.uniqueId }
        others.randomOrNull()?.let { player.teleport(it.location) }
    }

    // ── Messages and display ───────────────────────────────────────────────────

    fun sendMessage(player: Player, message: String) {
        player.sendMessage(message)
    }

    fun broadcastToGame(message: String) {
        getPlayers().forEach { it.sendMessage(message) }
    }

    fun sendTitle(player: Player, title: String, subtitle: String = "", fadeIn: Int = 10, stay: Int = 70, fadeOut: Int = 20) {
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut)
    }

    fun playSound(player: Player, sound: Sound, volume: Float = 1.0f, pitch: Float = 1.0f) {
        player.playSound(player.location, sound, volume, pitch)
    }

    // ── Blocks ─────────────────────────────────────────────────────────────────

    fun setBlock(location: Location, material: Material) {
        location.block.type = material
    }

    fun setBlockRelative(offsetX: Int, offsetY: Int, offsetZ: Int, material: Material) {
        blockLocation.clone().add(offsetX.toDouble(), offsetY.toDouble(), offsetZ.toDouble()).block.type = material
    }

    // ── Game info ──────────────────────────────────────────────────────────────

    fun getGame(): GameInstance? = game
    fun isInGame(): Boolean = game != null
    fun getWorld(): World? = blockLocation.world
}
