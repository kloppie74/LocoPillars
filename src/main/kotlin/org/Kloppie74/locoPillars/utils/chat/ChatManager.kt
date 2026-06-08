package org.Kloppie74.locoPillars.utils.chat

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.chat.listeners.ChatListener
import org.Kloppie74.locoPillars.utils.chat.commands.ChatCommand

/**
 * Main manager for the chat system
 * Coordinates ChatUtils, ChatListener, and ChatCommand
 */
class ChatManager(private val plugin: LocoPillars) {

    lateinit var chatUtils: ChatUtils
        private set

    private lateinit var chatListener: ChatListener
    private lateinit var chatCommand: ChatCommand

    fun initialize() {
        plugin.logger.info("[ChatManager] Initializing chat system...")

        try {
            // Initialize ChatUtils
            chatUtils = ChatUtils(plugin)
            plugin.logger.info("[ChatManager] ✓ ChatUtils initialized")

            // Initialize and register ChatListener
            chatListener = ChatListener(plugin, chatUtils)
            Bukkit.getPluginManager().registerEvents(chatListener, plugin)
            plugin.logger.info("[ChatManager] ✓ ChatListener registered")

            // Initialize and register ChatCommand
            chatCommand = ChatCommand(plugin, chatUtils)

            // Register chat admin command
            val chatAdminCommand = plugin.getCommand("chatadmin")
            if (chatAdminCommand != null) {
                chatAdminCommand.setExecutor(chatCommand)
                chatAdminCommand.tabCompleter = chatCommand
                plugin.logger.info("[ChatManager] ✓ ChatCommand registered (/chatadmin)")
            } else {
                plugin.logger.warning("[ChatManager] ⚠ Failed to register /chatadmin command - not found in plugin.yml")
            }

            // Register simple chat commands
            registerSimpleCommands()

            plugin.logger.info("[ChatManager] Chat system initialized successfully!")

            // Log configuration summary
            if (plugin.configManager.debugMode) {
                logConfigSummary()
            }

        } catch (e: Exception) {
            plugin.logger.severe("[ChatManager] Failed to initialize chat system: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    /**
     * Register simple chat commands like /socialspy, /teamchat
     */
    private fun registerSimpleCommands() {
        // Social Spy command
        val socialSpyCommand = plugin.getCommand("socialspy")
        if (socialSpyCommand != null) {
            socialSpyCommand.setExecutor { sender, _, _, _ ->
                if (sender is Player) {
                    chatUtils.toggleSocialSpy(sender)
                } else {
                    sender.sendMessage("§cThis command can only be used by players.")
                }
                true
            }
            plugin.logger.info("[ChatManager] ✓ /socialspy command registered")
        }

        // Team Chat command
        val teamChatCommand = plugin.getCommand("teamchat")
        if (teamChatCommand != null) {
            teamChatCommand.setExecutor { sender, _, _, _ ->
                if (sender is Player) {
                    chatUtils.toggleTeamChat(sender)
                } else {
                    sender.sendMessage("§cThis command can only be used by players.")
                }
                true
            }
            plugin.logger.info("[ChatManager] ✓ /teamchat command registered")
        }
    }

    /**
     * Log current configuration summary
     */
    private fun logConfigSummary() {
        plugin.logger.info("[ChatManager] === Chat Configuration Summary ===")
        plugin.logger.info("[ChatManager] Chat enabled: ${plugin.configManager.chatEnabled}")
        plugin.logger.info("[ChatManager] Lobby chat: ${plugin.configManager.lobbyChatEnabled}")
        plugin.logger.info("[ChatManager] Game chat: ${plugin.configManager.gameChatEnabled}")
        plugin.logger.info("[ChatManager] Spectator chat: ${plugin.configManager.spectatorChatEnabled}")
        plugin.logger.info("[ChatManager] Team chat: ${plugin.configManager.teamChatEnabled}")
        plugin.logger.info("[ChatManager] Social spy: ${plugin.configManager.socialSpyEnabled}")
        plugin.logger.info("[ChatManager] Chat logging: ${plugin.configManager.chatLogEnabled}")
        plugin.logger.info("[ChatManager] Cooldown: ${plugin.configManager.chatCooldownSeconds}s")
        plugin.logger.info("[ChatManager] Max length: ${plugin.configManager.maxChatLength}")
        plugin.logger.info("[ChatManager] Profanity filter: ${plugin.configManager.profanityFilterEnabled}")
        plugin.logger.info("[ChatManager] ===================================")
    }

    /**
     * Handle player joining game
     */
    fun onPlayerJoinGame(player: Player) {
        chatUtils.sendJoinMessage(player, isGameJoin = true)
        chatUtils.debugLog("Player ${player.name} joined a game")
    }

    /**
     * Handle player leaving game
     */
    fun onPlayerLeaveGame(player: Player) {
        chatUtils.sendLeaveMessage(player, isGameLeave = true)
        chatUtils.debugLog("Player ${player.name} left a game")
    }

    /**
     * Send system message to specific players
     */
    fun sendSystemMessage(message: String, players: Collection<Player>) {
        val formattedMessage = "§7[System] §f$message"
        chatUtils.sendAnnouncement(formattedMessage, players)
    }

    /**
     * Send system message to lobby
     */
    fun sendSystemMessageToLobby(message: String) {
        val lobbyPlayers = Bukkit.getOnlinePlayers().filter {
            plugin.gameManager.getPlayerInstance(it) == null
        }
        sendSystemMessage(message, lobbyPlayers)
    }

    /**
     * Send system message to game instance
     */
    fun sendSystemMessageToGame(instanceId: java.util.UUID, message: String) {
        val instance = plugin.gameManager.getAllInstances().find { it.id == instanceId }
        if (instance != null) {
            val gamePlayers = instance.players.mapNotNull { Bukkit.getPlayer(it) }
            sendSystemMessage(message, gamePlayers)
        }
    }

    /**
     * Get chat statistics
     */
    fun getStats(): Map<String, Any> {
        return chatUtils.getChatStats()
    }

    /**
     * Check if chat is enabled
     */
    fun isChatEnabled(): Boolean {
        return plugin.configManager.chatEnabled
    }

    /**
     * Check if social spy is enabled globally
     */
    fun isSocialSpyEnabled(): Boolean {
        return plugin.configManager.socialSpyEnabled
    }

    /**
     * Get player's remaining chat cooldown
     */
    fun getPlayerCooldown(player: Player): Int {
        return chatUtils.getCooldownRemaining(player)
    }

    /**
     * Check if player has social spy enabled
     */
    fun isPlayerSocialSpyEnabled(player: Player): Boolean {
        return chatUtils.isSocialSpyEnabled(player)
    }

    /**
     * Check if player has team chat enabled
     */
    fun isPlayerTeamChatEnabled(player: Player): Boolean {
        return chatUtils.isTeamChatEnabled(player)
    }

    /**
     * Toggle team chat for the given player (delegates to ChatUtils).
     */
    fun toggleTeamChat(player: Player) {
        chatUtils.toggleTeamChat(player)
    }

    /**
     * Force send a chat message (bypass cooldowns and filters)
     */
    fun forceSendMessage(sender: Player, message: String) {
        val recipients = chatUtils.getChatRecipients(sender, message)
        val teamName = chatUtils.getPlayerTeam(sender)
        val format = chatUtils.getChatFormat(sender)

        val formattedMessage = chatUtils.formatMessage(format, sender, message, teamName)

        recipients.forEach { recipient ->
            recipient.sendMessage(formattedMessage)
        }

        if (plugin.configManager.chatLogEnabled) {
            plugin.logger.info("[Chat] [FORCED] ${sender.name}: $message")
        }
    }

    /**
     * Cleanup method for plugin disable
     */
    fun cleanup() {
        try {
            // Unregister event listeners
            HandlerList.unregisterAll(chatListener)
            plugin.logger.info("[ChatManager] Event listeners unregistered")

            // Cleanup ChatUtils
            if (::chatUtils.isInitialized) {
                chatUtils.cleanup()
                plugin.logger.info("[ChatManager] ChatUtils cleaned up")
            }

            plugin.logger.info("[ChatManager] Chat system cleanup completed")
        } catch (e: Exception) {
            plugin.logger.severe("[ChatManager] Error during cleanup: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Reload chat system
     */
    fun reload() {
        plugin.logger.info("[ChatManager] Reloading chat system...")

        try {
            // Configuration will be reloaded by ConfigManager
            plugin.configManager.reloadConfig()

            if (plugin.configManager.debugMode) {
                logConfigSummary()
            }

            plugin.logger.info("[ChatManager] Chat system reloaded successfully")
        } catch (e: Exception) {
            plugin.logger.severe("[ChatManager] Error during reload: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Get debug information
     */
    fun getDebugInfo(): Map<String, Any> {
        val stats = chatUtils.getChatStats()
        val config = mapOf(
            "lobby_format" to plugin.configManager.lobbyChatFormat,
            "game_lobby_format" to plugin.configManager.gameLobbyFormat,
            "spectator_format" to plugin.configManager.gameSpectatorFormat,
            "team_format" to plugin.configManager.teamChatFormat,
            "global_format" to plugin.configManager.globalChatFormat,
            "social_spy_format" to plugin.configManager.socialSpyFormat
        )

        return mapOf(
            "statistics" to stats,
            "configuration" to config,
            "initialized" to ::chatUtils.isInitialized,
            "online_players" to Bukkit.getOnlinePlayers().size,
            "active_games" to plugin.gameManager.getActiveGames().size
        )
    }
}