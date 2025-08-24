package org.Kloppie74.locoPillars.utils.spectator

import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.Kloppie74.locoPillars.utils.MSG
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object SpectatorSpeedManager {
    private val PLAYER_SPEED_LEVELS = ConcurrentHashMap<UUID, Int>()
    private const val DEFAULT_SPEED_LEVEL = 1
    private const val MIN_SPEED_LEVEL = 1
    private const val MAX_SPEED_LEVEL = 7

    /**
     * Sets the spectator speed for a player
     */
    @JvmStatic
    fun setPlayerSpeed(player: Player, speedLevel: Int) {
        val clampedLevel = Math.max(MIN_SPEED_LEVEL, Math.min(MAX_SPEED_LEVEL, speedLevel))
        PLAYER_SPEED_LEVELS[player.uniqueId] = clampedLevel

        // Calculate speeds
        val walkSpeed = Math.min(1.0f, 0.2f + (clampedLevel * 0.08f))
        val flySpeed = Math.min(1.0f, 0.1f + (clampedLevel * 0.09f))

        // Apply speeds
        player.walkSpeed = walkSpeed
        player.flySpeed = flySpeed

        // Handle potion effects
        player.removePotionEffect(PotionEffectType.SPEED)
        if (clampedLevel > 1) {
            player.addPotionEffect(
                PotionEffect(
                    PotionEffectType.SPEED,
                    Integer.MAX_VALUE,
                    clampedLevel - 1,
                    false,
                    false
                )
            )
        }

        player.sendMessage(MSG.chatColors("&aSpectator speed set to &e$clampedLevel&a!"))
    }

    /**
     * Gets the current speed level for a player
     */
    @JvmStatic
    fun getCurrentSpeedLevel(player: Player): Int {
        val uuid = player.uniqueId
        PLAYER_SPEED_LEVELS[uuid]?.let { return it }

        // Calculate from current walk speed
        val walkSpeed = player.walkSpeed
        val speedLevel = Math.max(1, Math.min(7, Math.round((walkSpeed - 0.2f) / 0.08f) + 1))
        PLAYER_SPEED_LEVELS[uuid] = speedLevel
        return speedLevel
    }

    /**
     * Applies default spectator speed
     */
    @JvmStatic
    fun applyDefaultSpeed(player: Player) {
        val currentLevel = PLAYER_SPEED_LEVELS[player.uniqueId] ?: DEFAULT_SPEED_LEVEL
        setPlayerSpeed(player, currentLevel)
    }

    /**
     * Resets player speed to normal values
     */
    @JvmStatic
    fun resetSpeed(player: Player) {
        player.walkSpeed = 0.2f
        player.flySpeed = 0.1f
        player.removePotionEffect(PotionEffectType.SPEED)
        PLAYER_SPEED_LEVELS.remove(player.uniqueId)
    }

    /**
     * Handles speed selection from GUI or hotbar item
     */
    @JvmStatic
    fun handleSpeedSelection(player: Player, speedLevel: Int) {
        if (speedLevel in MIN_SPEED_LEVEL..MAX_SPEED_LEVEL) {
            setPlayerSpeed(player, speedLevel)
        }
    }

    /**
     * Gets available speed levels
     */
    @JvmStatic
    fun getAvailableSpeedLevels(): IntRange = MIN_SPEED_LEVEL..MAX_SPEED_LEVEL

    /**
     * Gets speed value for a given level
     */
    @JvmStatic
    fun getSpeedValueForLevel(level: Int): Float {
        val clampedLevel = Math.max(MIN_SPEED_LEVEL, Math.min(MAX_SPEED_LEVEL, level))
        return 0.1f + (clampedLevel * 0.09f) // Fly speed calculation
    }

    /**
     * Clear speed cache
     */
    @JvmStatic
    fun clearCache() {
        PLAYER_SPEED_LEVELS.clear()
    }
}