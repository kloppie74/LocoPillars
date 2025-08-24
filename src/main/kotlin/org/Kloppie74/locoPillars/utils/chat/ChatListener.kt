package org.Kloppie74.locoPillars.utils.chat.listeners

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.chat.ChatUtils
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils

class ChatListener(private val plugin: LocoPillars, private val chatUtils: ChatUtils) : Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerChat(event: AsyncPlayerChatEvent) {
        // Cancel the default chat handling
        event.isCancelled = true

        val player = event.player
        val message = event.message

        // Handle the chat message through our custom system
        chatUtils.sendChatMessage(player, message)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        // Cancel default join message
        event.joinMessage = null

        // Send custom join message
        chatUtils.sendJoinMessage(player)

        // Debug log
        chatUtils.debugLog("Player ${player.name} joined - sending custom join message")
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        // Cancel default quit message
        event.quitMessage = null

        // Send custom leave message
        chatUtils.sendLeaveMessage(player)

        // Clear player data
        chatUtils.clearPlayerData(player)

        // Debug log
        chatUtils.debugLog("Player ${player.name} quit - sending custom leave message and clearing data")
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        val commandLine = event.message
        val command = commandLine.lowercase()

        // Only handle actual chat commands, not /chatadmin or others
        when {
            command.equals("/socialspy") || command.equals("/spy") -> {
                event.isCancelled = true
                chatUtils.toggleSocialSpy(player)
            }
            command.equals("/teamchat") || command.equals("/tc") -> {
                event.isCancelled = true
                chatUtils.toggleTeamChat(player)
            }
        }
    }
}