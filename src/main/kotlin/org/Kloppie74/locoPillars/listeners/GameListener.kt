package org.Kloppie74.locoPillars.listeners

import org.bukkit.ChatColor
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.data.GameState

class GameListener(private val plugin: LocoPillars) : Listener {

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity
        val instance = plugin.gameManager.getPlayerInstance(player)

        if (instance != null) {
            // Handle game death
            event.deathMessage = null // Suppress default death message
            event.drops.clear() // Clear drops in game
            event.droppedExp = 0

            // Handle death through GameManager
            plugin.gameManager.handlePlayerDeath(player)
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player)

        if (instance != null) {
            // If player is still in game, teleport to spectator area or lobby
            if (instance.state == GameState.ACTIVE) {
                // Player died and should be out of the game
                // GameManager already handled this in handlePlayerDeath
                return
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerMove(event: PlayerMoveEvent) {
        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player) ?: return

        // Check if player fell below death height
        val deathHeight = instance.arenaInstance.config.getDeathHeight()
        if (player.location.y <= deathHeight && instance.state == GameState.ACTIVE) {
            // Player fell into void
            player.health = 0.0
            player.sendMessage("${ChatColor.RED}You fell into the void!")
        }

        // Check if player is outside build range during active game
        if (instance.state == GameState.ACTIVE) {
            val origin = instance.arenaInstance.origin
            val buildRange = instance.arenaInstance.config.getBuildRange()
            val buildHeight = instance.arenaInstance.config.getBuildHeight()

            val deltaX = kotlin.math.abs(player.location.x - origin.x)
            val deltaZ = kotlin.math.abs(player.location.z - origin.z)

            if (deltaX > buildRange || deltaZ > buildRange || player.location.y > origin.y + buildHeight) {
                // Teleport player back to safe area
                val safeLocation = origin.clone()
                safeLocation.y = origin.y + 10
                player.teleport(safeLocation)
                player.sendMessage("${ChatColor.RED}You cannot go outside the game area!")
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockBreak(event: BlockBreakEvent) {
        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player)

        if (instance != null) {
            // Check if player can break blocks
            if (instance.state != GameState.ACTIVE) {
                event.isCancelled = true
                player.sendMessage("${ChatColor.RED}You cannot break blocks while the game is not active!")
                return
            }

            // Check if map blocks can be broken
            if (!instance.arenaInstance.config.canBreakMapBlocks()) {
                // Allow only player-placed blocks to be broken
                // This would require tracking placed blocks, for now allow all
                // TODO: Implement block tracking system
            }

            // Check if block is within build area
            val origin = instance.arenaInstance.origin
            val buildRange = instance.arenaInstance.config.getBuildRange()
            val buildHeight = instance.arenaInstance.config.getBuildHeight()

            val blockLoc = event.block.location
            val deltaX = kotlin.math.abs(blockLoc.x - origin.x)
            val deltaZ = kotlin.math.abs(blockLoc.z - origin.z)

            if (deltaX > buildRange || deltaZ > buildRange || blockLoc.y > origin.y + buildHeight) {
                event.isCancelled = true
                player.sendMessage("${ChatColor.RED}You cannot break blocks outside the game area!")
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player)

        if (instance != null) {
            // Check if player can place blocks
            if (instance.state != GameState.ACTIVE) {
                event.isCancelled = true
                player.sendMessage("${ChatColor.RED}You cannot place blocks while the game is not active!")
                return
            }

            // Check if block is within build area
            val origin = instance.arenaInstance.origin
            val buildRange = instance.arenaInstance.config.getBuildRange()
            val buildHeight = instance.arenaInstance.config.getBuildHeight()

            val blockLoc = event.block.location
            val deltaX = kotlin.math.abs(blockLoc.x - origin.x)
            val deltaZ = kotlin.math.abs(blockLoc.z - origin.z)

            if (deltaX > buildRange || deltaZ > buildRange || blockLoc.y > origin.y + buildHeight) {
                event.isCancelled = true
                player.sendMessage("${ChatColor.RED}You cannot place blocks outside the game area!")
                return
            }

            // TODO: Track placed blocks for later cleanup
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityDamage(event: EntityDamageEvent) {
        if (event.entity !is Player) return

        val player = event.entity as Player
        val instance = plugin.gameManager.getPlayerInstance(player) ?: return

        when (instance.state) {
            GameState.WAITING, GameState.STARTING -> {
                // Cancel damage during waiting/starting phase
                event.isCancelled = true
            }
            GameState.ACTIVE -> {
                // Allow damage during active game
                // Check for special damage types
                when (event.cause) {
                    EntityDamageEvent.DamageCause.VOID -> {
                        // Handle void damage
                        player.sendMessage("${ChatColor.RED}You fell into the void!")
                    }
                    EntityDamageEvent.DamageCause.FALL -> {
                        // Check if no-fall mode is active
                        // TODO: Implement voting system for special modes
                    }
                    else -> {
                        // Normal damage
                    }
                }
            }
            GameState.ENDED, GameState.RESETTING -> {
                // Cancel damage during end/reset phase
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onEntityDamageByEntity(event: EntityDamageByEntityEvent) {
        if (event.entity !is Player || event.damager !is Player) return

        val victim = event.entity as Player
        val attacker = event.damager as Player

        val victimInstance = plugin.gameManager.getPlayerInstance(victim)
        val attackerInstance = plugin.gameManager.getPlayerInstance(attacker)

        // Both players must be in the same game instance
        if (victimInstance == null || attackerInstance == null || victimInstance != attackerInstance) {
            event.isCancelled = true
            return
        }

        val instance = victimInstance

        when (instance.state) {
            GameState.WAITING, GameState.STARTING -> {
                // No PvP during waiting/starting
                event.isCancelled = true
                attacker.sendMessage("${ChatColor.RED}PvP is not allowed yet!")
            }
            GameState.ACTIVE -> {
                // Allow PvP during active game
                // TODO: Check for team damage if same team
            }
            GameState.ENDED, GameState.RESETTING -> {
                // No PvP during end/reset
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onFoodLevelChange(event: FoodLevelChangeEvent) {
        if (event.entity !is Player) return

        val player = event.entity as Player
        val instance = plugin.gameManager.getPlayerInstance(player) ?: return

        // Keep food level full during non-active states
        if (instance.state != GameState.ACTIVE) {
            event.isCancelled = true
            player.foodLevel = 20
            player.saturation = 20f
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player) ?: return

        // Prevent item dropping during certain states
        when (instance.state) {
            GameState.WAITING, GameState.STARTING -> {
                event.isCancelled = true
                player.sendMessage("${ChatColor.RED}You cannot drop items right now!")
            }
            GameState.ACTIVE -> {
                // Allow item dropping during active game
            }
            GameState.ENDED, GameState.RESETTING -> {
                event.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        val instance = plugin.gameManager.getPlayerInstance(player) ?: return

        // Prevent players from teleporting out of game area during active game
        if (instance.state == GameState.ACTIVE) {
            val toWorld = event.to?.world
            val gameWorld = instance.arenaInstance.world

            if (toWorld != gameWorld) {
                // Player trying to teleport to different world
                if (event.cause == PlayerTeleportEvent.TeleportCause.COMMAND) {
                    event.isCancelled = true
                    player.sendMessage("${ChatColor.RED}You cannot teleport while in a game! Use /pillars leave to leave the game.")
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        // Remove player from game when they quit
        plugin.gameManager.leaveGame(player)
    }

    @EventHandler(priority = EventPriority.LOW)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Make sure player is not stuck in an arena world
        if (player.world.name.startsWith("pillars_")) {
            val mainWorld = plugin.server.worlds.firstOrNull { !it.name.startsWith("pillars_") }
            if (mainWorld != null) {
                player.teleport(mainWorld.spawnLocation)
                player.sendMessage("${ChatColor.YELLOW}You were teleported back to the main world.")
            }
        }

        // Reset player state
        player.gameMode = GameMode.SURVIVAL
        player.health = 20.0
        player.foodLevel = 20
        player.saturation = 20f

        // Clear potion effects
        player.activePotionEffects.forEach { effect ->
            player.removePotionEffect(effect.type)
        }
    }
}