package org.Kloppie74.locoPillars.events

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitRunnable
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.instance.GameInstance
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.MSG
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class TimerEvent(private val plugin: LocoPillars) {

    private val gameTimers = ConcurrentHashMap<UUID, GameTimerInfo>()
    private val itemTimers = ConcurrentHashMap<UUID, ItemTimerInfo>()

    data class GameTimerInfo(
        val instance: GameInstance,
        val totalTime: Int,
        var timeLeft: Int,
        val timer: BukkitRunnable
    )

    data class ItemTimerInfo(
        val instance: GameInstance,
        val interval: Int,
        var timeUntilNext: Int,
        val timer: BukkitRunnable
    )

    init {
        startActionBarUpdater()
    }

    private fun startActionBarUpdater() {
        object : BukkitRunnable() {
            override fun run() {
                updateAllActionBars()
            }
        }.runTaskTimer(plugin, 0L, 20L)
    }

    fun startGameTimer(instance: GameInstance, gameTimeSeconds: Int) {
        debugLog("Starting game timer for instance ${instance.id}: ${gameTimeSeconds}s")

        stopGameTimer(instance.id)

        val timer = object : BukkitRunnable() {
            var timeLeft = gameTimeSeconds

            override fun run() {
                if (instance.state != GameState.ACTIVE) {
                    debugLog("Game timer cancelled - game no longer active")
                    stopGameTimer(instance.id)
                    return
                }

                gameTimers[instance.id]?.let { timerInfo ->
                    timerInfo.timeLeft = timeLeft
                }

                when {
                    timeLeft <= 0 -> {
                        debugLog("Game timer expired for instance ${instance.id}")
                        plugin.gameManager.endGameByTimer(instance)
                        stopGameTimer(instance.id)
                        return
                    }
                    timeLeft in arrayOf(300, 180, 120, 60, 30, 10, 5, 4, 3, 2, 1) -> {
                        val minutes = timeLeft / 60
                        val seconds = timeLeft % 60
                        val timeString = if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
                        broadcastToGame(instance, MSG.lang("game.timer.ending_in", "time" to timeString))
                        debugLog("Game timer warning: $timeString remaining for instance ${instance.id}")
                    }
                }

                timeLeft--
            }
        }

        timer.runTaskTimer(plugin, 0L, 20L)
        gameTimers[instance.id] = GameTimerInfo(instance, gameTimeSeconds, gameTimeSeconds, timer)
    }

    fun startItemTimer(instance: GameInstance, itemIntervalSeconds: Int) {
        debugLog("Starting item timer for instance ${instance.id}: every ${itemIntervalSeconds}s")

        stopItemTimer(instance.id)

        val timer = object : BukkitRunnable() {
            var timeUntilNext = itemIntervalSeconds

            override fun run() {
                if (instance.state != GameState.ACTIVE) {
                    debugLog("Item timer cancelled - game no longer active")
                    stopItemTimer(instance.id)
                    return
                }

                itemTimers[instance.id]?.let { timerInfo ->
                    timerInfo.timeUntilNext = timeUntilNext
                }

                if (timeUntilNext <= 0) {
                    plugin.gameManager.giveItemsToPlayers(instance)
                    timeUntilNext = itemIntervalSeconds
                }

                timeUntilNext--
            }
        }

        timer.runTaskTimer(plugin, 0L, 20L)
        itemTimers[instance.id] = ItemTimerInfo(instance, itemIntervalSeconds, itemIntervalSeconds, timer)
    }

    fun stopGameTimer(instanceId: UUID) {
        gameTimers[instanceId]?.let { timerInfo ->
            timerInfo.timer.cancel()
            gameTimers.remove(instanceId)
            debugLog("Game timer stopped for instance $instanceId")
        }
    }

    fun stopItemTimer(instanceId: UUID) {
        itemTimers[instanceId]?.let { timerInfo ->
            timerInfo.timer.cancel()
            itemTimers.remove(instanceId)
            debugLog("Item timer stopped for instance $instanceId")
        }
    }

    fun stopAllTimers(instanceId: UUID) {
        stopGameTimer(instanceId)
        stopItemTimer(instanceId)
    }

    private fun updateAllActionBars() {
        plugin.gameManager.getAllInstances().forEach { instance ->
            updateActionBarForInstance(instance)
        }
    }

    private fun updateActionBarForInstance(instance: GameInstance) {
        val players = getPlayersInGame(instance)
        if (players.isEmpty()) return

        val actionBarText = buildActionBarText(instance)

        players.forEach { player ->
            try {
                player.spigot().sendMessage(
                    net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    *net.md_5.bungee.api.chat.TextComponent.fromLegacyText(actionBarText)
                )
            } catch (e: Exception) {
                player.sendMessage(actionBarText)
            }
        }
    }

    private fun buildActionBarText(instance: GameInstance): String {
        val sb = StringBuilder()

        when (instance.state) {
            GameState.WAITING -> {
                val needed = instance.getMinPlayers() - instance.players.size
                sb.append(MSG.lang("actionbar.waiting", "needed" to needed.toString()))
            }

            GameState.STARTING -> {
                sb.append(MSG.lang("actionbar.starting"))
            }

            GameState.ACTIVE -> {
                gameTimers[instance.id]?.let { gameTimer ->
                    val minutes = gameTimer.timeLeft / 60
                    val seconds = gameTimer.timeLeft % 60
                    val timeString = String.format("%02d:%02d", minutes, seconds)
                    sb.append(MSG.lang("actionbar.active_timer", "time" to timeString))
                } ?: sb.append(MSG.lang("actionbar.active_timer_fallback"))

                itemTimers[instance.id]?.let { itemTimer ->
                    sb.append(MSG.lang("actionbar.active_separator"))
                    sb.append(MSG.lang("actionbar.active_items", "seconds" to itemTimer.timeUntilNext.toString()))
                }

                sb.append(MSG.lang("actionbar.active_separator"))
                sb.append(MSG.lang("actionbar.active_players", "count" to instance.players.size.toString()))
            }

            GameState.ENDED -> {
                sb.append(MSG.lang("actionbar.ended"))
            }

            GameState.RESETTING -> {
                sb.append(MSG.lang("actionbar.resetting"))
            }
        }

        return sb.toString()
    }

    private fun getPlayersInGame(instance: GameInstance): List<Player> {
        return instance.players.mapNotNull { playerId ->
            Bukkit.getPlayer(playerId)?.takeIf { it.isOnline }
        }
    }

    private fun broadcastToGame(instance: GameInstance, message: String) {
        getPlayersInGame(instance).forEach { it.sendMessage(message) }
    }

    fun cleanup() {
        gameTimers.values.forEach { it.timer.cancel() }
        gameTimers.clear()
        itemTimers.values.forEach { it.timer.cancel() }
        itemTimers.clear()
    }

    fun getGameTimeLeft(instanceId: UUID): Int? = gameTimers[instanceId]?.timeLeft

    fun getItemTimeLeft(instanceId: UUID): Int? = itemTimers[instanceId]?.timeUntilNext

    fun hasActiveGameTimer(instanceId: UUID): Boolean = gameTimers.containsKey(instanceId)

    fun hasActiveItemTimer(instanceId: UUID): Boolean = itemTimers.containsKey(instanceId)

    private fun debugLog(message: String) {
        if (plugin.configManager.debugMode) plugin.logger.info("[TimerEvent] $message")
    }
}
