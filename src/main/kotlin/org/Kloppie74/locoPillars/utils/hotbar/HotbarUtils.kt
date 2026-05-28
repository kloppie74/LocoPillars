package org.Kloppie74.locoPillars.utils.hotbar

import org.bukkit.entity.Player
import org.bukkit.GameMode
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object HotbarUtils {
    private val HOTBAR_CACHE = ConcurrentHashMap<UUID, String>()
    private const val CACHE_EXPIRY_MS = 3000L
    private val CACHE_TIMESTAMPS = ConcurrentHashMap<UUID, Long>()

    /**
     * Main method to setup player hotbar based on current state.
     * Skips entirely for active editor players — the editor manages
     * their gamemode and hotbar independently.
     */
    @JvmStatic
    fun setupPlayerHotbar(player: Player, plugin: LocoPillars) {
        // Editor players keep Creative + editor hotbar; don't touch them here
        if (plugin.adminEditorManager.isActive(player)) return

        val hotbarType = determineHotbarType(player, plugin)

        HOTBAR_CACHE[player.uniqueId] = hotbarType
        CACHE_TIMESTAMPS[player.uniqueId] = System.currentTimeMillis()

        plugin.hotbarItemManager.givePlayerItems(player)

        applyPlayerStateForHotbarType(player, hotbarType, plugin)

        player.updateInventory()
    }

    /**
     * Determines what hotbar type a player should have
     */
    @JvmStatic
    fun determineHotbarType(player: Player, plugin: LocoPillars): String {
        val worldName = player.world.name
        val instance = plugin.gameManager.getPlayerInstance(player)

        return when {
            worldName.equals(plugin.configManager.lobbyWorld, ignoreCase = true) -> "lobby"
            worldName.startsWith("pillars_") && instance == null -> "spectate"
            instance != null -> {
                when (instance.state) {
                    GameState.WAITING -> "game_lobby"
                    GameState.STARTING -> "game_lobby"
                    GameState.ACTIVE -> {
                        if (SpectatorUtils.isSpectator(player, plugin)) {
                            "spectate"
                        } else if (!instance.cagesOpen) {
                            // Cages still up — keep game_lobby hotbar so teleporting
                            // players to their spawns does NOT trigger an inventory wipe.
                            "game_lobby"
                        } else {
                            "game"
                        }
                    }
                    GameState.ENDED, GameState.RESETTING -> "spectate"
                }
            }
            else -> "lobby"
        }
    }

    /**
     * Gets current hotbar type from cache or determines it
     */
    @JvmStatic
    fun getCurrentHotbarType(player: Player, plugin: LocoPillars): String {
        val uuid = player.uniqueId
        val now = System.currentTimeMillis()

        val cachedType = HOTBAR_CACHE[uuid]
        val timestamp = CACHE_TIMESTAMPS[uuid]

        if (cachedType != null && timestamp != null && (now - timestamp) < CACHE_EXPIRY_MS) {
            return cachedType
        }

        val newType = determineHotbarType(player, plugin)
        HOTBAR_CACHE[uuid] = newType
        CACHE_TIMESTAMPS[uuid] = now

        return newType
    }

    /**
     * Applies player state (gamemode, flight, etc.) based on hotbar type.
     * Never touches editor players — their state is managed by AdminEditorManager.
     */
    @JvmStatic
    fun applyPlayerStateForHotbarType(player: Player, hotbarType: String, plugin: LocoPillars) {
        if (plugin.adminEditorManager.isActive(player)) return

        when (hotbarType.lowercase()) {
            "spectate" -> {
                if (SpectatorUtils.isSpectator(player, plugin)) {
                    player.gameMode = GameMode.SURVIVAL
                    player.allowFlight = true
                    player.isFlying = true
                    player.flySpeed = 0.1f
                    player.isInvulnerable = true
                    player.isCollidable = false
                } else {
                    player.gameMode = GameMode.SURVIVAL
                    player.allowFlight = false
                    player.isFlying = false
                    player.flySpeed = 0.1f
                    player.walkSpeed = 0.2f
                    player.isInvulnerable = false
                    player.isCollidable = true
                }
            }
            "lobby" -> {
                player.gameMode = GameMode.SURVIVAL
                player.allowFlight = false
                player.isFlying = false
                player.flySpeed = 0.1f
                player.walkSpeed = 0.2f
                player.isInvulnerable = false
                player.isCollidable = true
            }
            "game_lobby", "cage" -> {
                // NOTE: do NOT set isInvulnerable = true here.
                // Damage during WAITING/STARTING is already blocked by the
                // onEntityDamage event handler.  Setting isInvulnerable = true
                // leaks into the ACTIVE phase and makes players unkillable.
                player.gameMode = GameMode.SURVIVAL
                player.allowFlight = false
                player.isFlying = false
                player.flySpeed = 0.1f
                player.walkSpeed = 0.2f
                player.isInvulnerable = false
                player.isCollidable = true
            }
            "game" -> {
                player.gameMode = GameMode.SURVIVAL
                player.allowFlight = false
                player.isFlying = false
                player.flySpeed = 0.1f
                player.walkSpeed = 0.2f
                player.isInvulnerable = false
                player.isCollidable = true
            }
        }
    }

    /**
     * Checks if an item is a locked hotbar item
     */
    @JvmStatic
    fun isLockedHotbarItem(item: ItemStack?, plugin: LocoPillars): Boolean {
        if (item == null) return false
        val meta = item.itemMeta ?: return false
        val nbtKey = NamespacedKey(plugin, "locoHotbarLocked")
        return meta.persistentDataContainer.has(nbtKey, PersistentDataType.BYTE)
    }

    @JvmStatic
    fun setLockedHotbarNbt(item: ItemStack, plugin: LocoPillars): ItemStack {
        val meta = item.itemMeta
        val nbtKey = NamespacedKey(plugin, "locoHotbarLocked")
        meta?.persistentDataContainer?.set(nbtKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    /**
     * Handles hotbar type transitions
     */
    @JvmStatic
    fun handleHotbarTypeChange(player: Player, oldType: String, newType: String, plugin: LocoPillars) {
        if (oldType == newType) return

        HOTBAR_CACHE.remove(player.uniqueId)
        CACHE_TIMESTAMPS.remove(player.uniqueId)

        setupPlayerHotbar(player, plugin)

        when {
            newType == "spectate" && oldType != "spectate" -> {
                SpectatorUtils.setPlayerAsSpectator(player, plugin)
            }
            oldType == "spectate" && newType != "spectate" -> {
                SpectatorUtils.removeSpectatorStatus(player, plugin)
            }
        }
    }

    /**
     * Clear the hotbar cache
     */
    @JvmStatic
    fun clearCache() {
        HOTBAR_CACHE.clear()
        CACHE_TIMESTAMPS.clear()
    }
}