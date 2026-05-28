package org.Kloppie74.locoPillars.game.voting

import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.scheduler.BukkitTask
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance

class VotingGameContext(
    private val plugin: LocoPillars,
    val instance: GameInstance
) {
    private val listener = object : Listener {}
    private val tasks = mutableListOf<BukkitTask>()
    private val cleanupActions = mutableListOf<() -> Unit>()

    fun sendTitle(title: String, subtitle: String, fadeIn: Int = 10, stay: Int = 60, fadeOut: Int = 10) {
        getPlayers().forEach { it.sendTitle(title, subtitle, fadeIn, stay, fadeOut) }
    }

    fun broadcast(message: String) {
        getPlayers().forEach { it.sendMessage(message) }
    }

    fun getPlayers(): List<Player> =
        instance.players.mapNotNull { Bukkit.getPlayer(it) }.filter { it.isOnline }

    fun isPlayerInGame(player: Player): Boolean = player.uniqueId in instance.players

    // ── Arena accessors ───────────────────────────────────────────────────────
    // Voting scripts (e.g. floor_is_lava) need arena geometry, but GameInstance /
    // ArenaInstance / GameConfig are obfuscated by ProGuard — their member names do
    // NOT survive into the runtime-compiled .kts scripts. VotingGameContext IS kept
    // (-keep public *), so exposing the values through these helpers lets scripts
    // reach arena data by stable, non-obfuscated method names.

    /** The Bukkit world this game instance is running in. */
    fun getArenaWorld(): World = instance.arenaInstance.world

    /** Absolute block X of the arena spawn centre (origin + configured offset). */
    fun getArenaCenterX(): Int =
        instance.arenaInstance.origin.blockX + instance.arenaInstance.config.getSpawnCenterX().toInt()

    /** Absolute block Z of the arena spawn centre (origin + configured offset). */
    fun getArenaCenterZ(): Int =
        instance.arenaInstance.origin.blockZ + instance.arenaInstance.config.getSpawnCenterZ().toInt()

    /** Y level at/below which players are considered to have fallen out. */
    fun getArenaDeathHeight(): Int = instance.arenaInstance.config.getDeathHeight()

    /** Maximum Y players are allowed to build to. */
    fun getArenaBuildHeight(): Int = instance.arenaInstance.config.getBuildHeight()

    /** Horizontal half-extent (in blocks) of the buildable arena area. */
    fun getArenaBuildRange(): Int = instance.arenaInstance.config.getBuildRange()

    fun <T : Event> onEvent(clazz: Class<T>, handler: (T) -> Unit) {
        Bukkit.getPluginManager().registerEvent(
            clazz,
            listener,
            EventPriority.NORMAL,
            { _, event -> @Suppress("UNCHECKED_CAST") if (clazz.isInstance(event)) handler(event as T) },
            plugin
        )
    }

    fun scheduleRepeating(delayTicks: Long, periodTicks: Long, task: () -> Unit): BukkitTask {
        val t = plugin.server.scheduler.runTaskTimer(plugin, Runnable { task() }, delayTicks, periodTicks)
        tasks.add(t)
        return t
    }

    fun scheduleDelayed(delayTicks: Long, task: () -> Unit): BukkitTask {
        val t = plugin.server.scheduler.runTaskLater(plugin, Runnable { task() }, delayTicks)
        tasks.add(t)
        return t
    }

    fun onCleanup(action: () -> Unit) {
        cleanupActions.add(action)
    }

    fun cleanup() {
        cleanupActions.forEach { try { it() } catch (_: Exception) {} }
        cleanupActions.clear()
        HandlerList.unregisterAll(listener)
        tasks.forEach { try { it.cancel() } catch (_: Exception) {} }
        tasks.clear()
    }
}
