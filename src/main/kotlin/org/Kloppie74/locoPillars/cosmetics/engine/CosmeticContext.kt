package org.Kloppie74.locoPillars.cosmetics.engine

import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance

/**
 * Context injected into every cosmetic .kts script.
 *
 * Injected variables:
 *   - `player`  — the player who owns the cosmetic (killer / winner / dying player)
 *   - `victim`  — only for KILL cosmetics: the player who was killed (null otherwise)
 *   - `game`    — the [CosmeticContext] API object (this class)
 *   - `bukkit`  — direct access to the Bukkit API singleton
 */
class CosmeticContext(
    val playerLocation: Location,
    private val gameInstance: GameInstance?,
    private val plugin: LocoPillars
) {
    /** Direct access to Bukkit from scripts */
    val bukkit: Server get() = Bukkit.getServer()

    // ── Player helpers ─────────────────────────────────────────────────────────

    fun getPlayers(): List<Player> =
        gameInstance?.players?.mapNotNull { Bukkit.getPlayer(it) }
            ?: Bukkit.getOnlinePlayers().toList()

    fun randomPlayer(): Player? = getPlayers().randomOrNull()

    // ── Effects ────────────────────────────────────────────────────────────────

    fun spawnParticles(location: Location, particle: Particle, count: Int, offsetX: Double = 0.0, offsetY: Double = 0.0, offsetZ: Double = 0.0, speed: Double = 0.0) {
        location.world?.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, speed)
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

    fun spawnLightning(location: Location) {
        location.world?.strikeLightning(location)
    }

    fun spawnLightningEffect(location: Location) {
        location.world?.strikeLightningEffect(location)
    }

    fun spawnExplosion(location: Location, power: Float = 2.0f, fire: Boolean = false) {
        location.world?.createExplosion(location, power, fire, false)
    }

    fun spawnMob(type: EntityType, location: Location): Entity? =
        location.world?.spawnEntity(location, type)

    fun giveEffect(player: Player, type: PotionEffectType, durationSeconds: Int, amplifier: Int = 0) {
        player.addPotionEffect(PotionEffect(type, durationSeconds * 20, amplifier))
    }

    fun removeEffect(player: Player, type: PotionEffectType) {
        player.removePotionEffect(type)
    }

    fun giveItem(player: Player, item: ItemStack) {
        if (player.inventory.firstEmpty() != -1) player.inventory.addItem(item)
        else player.world.dropItemNaturally(player.location, item)
    }

    fun setBlock(location: Location, material: Material) {
        location.block.type = material
    }

    // ── Messages ───────────────────────────────────────────────────────────────

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

    // ── Scheduling ────────────────────────────────────────────────────────────

    /**
     * Schedule a task to run [delayTicks] ticks from now.
     * Useful for timed cosmetic effects (e.g. removing a spawned entity after a delay).
     */
    fun scheduleTask(delayTicks: Long, task: () -> Unit) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable { task() }, delayTicks)
    }

    /**
     * Schedule a repeating task. Returns the task ID so it can be cancelled.
     */
    fun scheduleRepeating(delayTicks: Long, periodTicks: Long, task: () -> Unit): Int {
        return plugin.server.scheduler.runTaskTimer(plugin, Runnable { task() }, delayTicks, periodTicks).taskId
    }

    fun cancelTask(taskId: Int) {
        plugin.server.scheduler.cancelTask(taskId)
    }

    // ── Game info ──────────────────────────────────────────────────────────────

    fun getGameInstance(): GameInstance? = gameInstance
    fun isInGame(): Boolean = gameInstance != null
    fun getWorld(): World? = playerLocation.world
}
