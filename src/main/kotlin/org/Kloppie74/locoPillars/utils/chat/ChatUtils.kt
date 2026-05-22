package org.Kloppie74.locoPillars.utils.chat

import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.utils.MSG
import org.Kloppie74.locoPillars.utils.spectator.SpectatorUtils
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ChatUtils(private val plugin: LocoPillars) {
    private val configManager get() = plugin.configManager
    private val gameManager get() = plugin.gameManager

    // Chat cooldowns per player
    private val chatCooldowns = ConcurrentHashMap<UUID, Long>()

    // Per-player cooldown overrides (null = use global configManager value)
    private val playerCooldownOverrides = ConcurrentHashMap<UUID, Int>()

    // Social spy toggle per player
    private val socialSpyToggle = ConcurrentHashMap<UUID, Boolean>()

    // Team chat toggle per player
    private val teamChatToggle = ConcurrentHashMap<UUID, Boolean>()

    /**
     * Format a chat message with placeholders
     */
    fun formatMessage(format: String, player: Player, message: String, teamName: String? = null, chatType: String? = null): String {
        var formatted = format
            .replace("{player}", player.name)
            .replace("{displayname}", player.displayName ?: player.name)
            .replace("{message}", message)
            .replace("{team}", teamName ?: "Unknown")
            .replace("{type}", chatType ?: "Unknown")
            .replace("{world}", player.world.name)

        formatted = PlaceholderAPI.setPlaceholders(player, formatted)

        formatted = MSG.chatColors(formatted)

        return formatted
    }

    /**
     * Get the appropriate chat format based on player state.
     * Spectator check MUST come first — eliminated players have null playerInstance.
     */
    fun getChatFormat(player: Player): String {
        // Check spectator before instance: eliminated players have instance == null
        if (SpectatorUtils.isSpectator(player, plugin)) return configManager.gameSpectatorFormat

        val instance = gameManager.getPlayerInstance(player)
        return when {
            instance == null -> configManager.lobbyChatFormat
            instance.state == GameState.WAITING || instance.state == GameState.STARTING -> configManager.gameLobbyFormat
            instance.state == GameState.ACTIVE && isTeamChatEnabled(player) -> configManager.teamChatFormat
            instance.state == GameState.ACTIVE -> configManager.globalChatFormat
            else -> configManager.lobbyChatFormat
        }
    }

    /**
     * Get the team name for a player in a game instance
     */
    fun getPlayerTeam(player: Player): String? {
        val instance = gameManager.getPlayerInstance(player) ?: return null
        val origin = instance.arenaInstance.origin
        val teams = instance.arenaInstance.config.teams

        return teams.find { team ->
            team.spawns.any { spawn ->
                player.location.distance(spawn.toAbsolute(origin)) < 5.0
            }
        }?.name
    }

    /**
     * Get recipients for a chat message based on context.
     * Spectator check MUST come first — eliminated players have instance == null,
     * so checking instance first would route them to lobby chat by mistake.
     */
    fun getChatRecipients(sender: Player, message: String): Set<Player> {
        val recipients = mutableSetOf<Player>()

        // Spectator chat: check BEFORE instance lookup
        if (SpectatorUtils.isSpectator(sender, plugin)) {
            val spectatorInstance = gameManager.getSpectatorInstance(sender)
            if (spectatorInstance != null) {
                // Route to all spectators watching the same game
                spectatorInstance.spectators
                    .mapNotNull { Bukkit.getPlayer(it) }
                    .forEach { recipients.add(it) }
            }
            // If no game instance found (edge case), fall through to lobby behaviour below.
            // Otherwise return the spectator set immediately.
            if (recipients.isNotEmpty()) return recipients
        }

        val instance = gameManager.getPlayerInstance(sender)

        when {
            // Lobby chat - all players not in a game (and not spectating)
            instance == null -> {
                recipients.addAll(Bukkit.getOnlinePlayers().filter {
                    gameManager.getPlayerInstance(it) == null
                })
            }

            // Team chat - only team members in the same game
            instance.state == GameState.ACTIVE && isTeamChatEnabled(sender) -> {
                val senderTeam = getPlayerTeam(sender)
                if (senderTeam != null) {
                    recipients.addAll(instance.players.mapNotNull {
                        Bukkit.getPlayer(it)
                    }.filter {
                        !SpectatorUtils.isSpectator(it, plugin) && getPlayerTeam(it) == senderTeam
                    })
                }
            }

            // Game chat - all players in the same game instance
            else -> {
                recipients.addAll(instance.players.mapNotNull {
                    Bukkit.getPlayer(it)
                })
            }
        }

        return recipients
    }

    /**
     * Send a chat message to appropriate recipients
     */
    fun sendChatMessage(sender: Player, message: String): Boolean {
        if (!configManager.chatEnabled) {
            sender.sendMessage(ChatColor.RED.toString() + "Chat is currently disabled.")
            return false
        }

        // Check cooldown
        if (!checkChatCooldown(sender)) {
            val remainingTime = getCooldownRemaining(sender)
            sender.sendMessage(ChatColor.RED.toString() + "Please wait ${remainingTime}s before sending another message.")
            return false
        }

        // Check message length
        if (message.length > configManager.maxChatLength) {
            sender.sendMessage(ChatColor.RED.toString() + "Message too long! Maximum ${configManager.maxChatLength} characters.")
            return false
        }

        // Filter profanity if enabled
        val filteredMessage = if (configManager.profanityFilterEnabled) {
            filterProfanity(message)
        } else {
            message
        }

        val recipients = getChatRecipients(sender, filteredMessage)
        val teamName = getPlayerTeam(sender)
        val chatType = getChatType(sender)
        val format = getChatFormat(sender)

        val formattedMessage = formatMessage(format, sender, filteredMessage, teamName, chatType)

        // Send to recipients
        recipients.forEach { recipient ->
            recipient.sendMessage(formattedMessage)
        }

        // Send to social spy users
        sendToSocialSpy(sender, filteredMessage, chatType, formattedMessage, recipients)

        // Log chat if enabled
        if (configManager.chatLogEnabled) {
            logChatMessage(sender, filteredMessage, chatType, recipients.size)
        }

        // Set cooldown
        setChatCooldown(sender)

        return true
    }

    /**
     * Get the chat type for social spy.
     * Spectator check MUST come first — eliminated players have instance == null.
     */
    private fun getChatType(player: Player): String {
        if (SpectatorUtils.isSpectator(player, plugin)) return "Spectator"

        val instance = gameManager.getPlayerInstance(player)
        return when {
            instance == null -> "Lobby"
            instance.state == GameState.WAITING || instance.state == GameState.STARTING -> "Game-Lobby"
            instance.state == GameState.ACTIVE && isTeamChatEnabled(player) -> "Team"
            instance.state == GameState.ACTIVE -> "Game"
            else -> "Unknown"
        }
    }

    /**
     * Send message to social spy users
     */
    private fun sendToSocialSpy(
        sender: Player,
        message: String,
        chatType: String,
        originalMessage: String,
        recipients: Set<Player>
    ) {
        if (!configManager.socialSpyEnabled) return

        val socialSpyMessage = formatMessage(
            configManager.socialSpyFormat,
            sender,
            message,
            chatType = chatType
        )

        Bukkit.getOnlinePlayers()
            .filter { it != sender }
            .filter { it.hasPermission("locopillars.chat.socialspy") }
            .filter { isSocialSpyEnabled(it) }
            .filter { !recipients.contains(it) } // <<< Belangrijk: alleen als ze het NIET kregen via gewone chat
            .forEach { it.sendMessage(socialSpyMessage) }
    }

    /**
     * Log chat message
     */
    private fun logChatMessage(sender: Player, message: String, chatType: String, recipientCount: Int) {
        plugin.logger.info("[Chat] [$chatType] ${sender.name}: $message (sent to $recipientCount players)")
    }

    /**
     * Filter profanity from message
     */
    private fun filterProfanity(message: String): String {
        var filteredMessage = message
        val profanityWords = configManager.getProfanityWords()

        profanityWords.forEach { word ->
            val replacement = "*".repeat(word.length)
            filteredMessage = filteredMessage.replace(word, replacement, ignoreCase = true)
        }

        return filteredMessage
    }

    /**
     * Returns the effective cooldown for this player:
     * 0 if they have bypass permission, their personal override if set, otherwise the global value.
     */
    private fun getEffectiveCooldownSeconds(player: Player): Int {
        if (player.hasPermission("locopillars.chat.bypass-cooldown")) return 0
        return playerCooldownOverrides.getOrDefault(player.uniqueId, configManager.chatCooldownSeconds)
    }

    /**
     * Set a per-player cooldown override. Use seconds = 0 to remove the cooldown entirely.
     * Pass seconds < 0 to delete the override (reverts to global default).
     */
    fun setPlayerCooldownOverride(player: Player, seconds: Int) {
        if (seconds < 0) {
            playerCooldownOverrides.remove(player.uniqueId)
        } else {
            playerCooldownOverrides[player.uniqueId] = seconds
        }
    }

    /**
     * Remove a player's personal cooldown override AND reset their active cooldown timer.
     */
    fun clearPlayerCooldownOverride(player: Player) {
        playerCooldownOverrides.remove(player.uniqueId)
        chatCooldowns.remove(player.uniqueId)
    }

    /**
     * Returns the current per-player override in seconds, or null if none is set.
     */
    fun getPlayerCooldownOverride(player: Player): Int? = playerCooldownOverrides[player.uniqueId]

    /**
     * Check if player is on chat cooldown
     */
    fun checkChatCooldown(player: Player): Boolean {
        val effectiveCooldown = getEffectiveCooldownSeconds(player)
        if (effectiveCooldown <= 0) return true

        val lastChatTime = chatCooldowns[player.uniqueId] ?: 0L
        val cooldownMs = effectiveCooldown * 1000L
        return (System.currentTimeMillis() - lastChatTime) >= cooldownMs
    }

    /**
     * Set chat cooldown for player
     */
    private fun setChatCooldown(player: Player) {
        if (getEffectiveCooldownSeconds(player) > 0) {
            chatCooldowns[player.uniqueId] = System.currentTimeMillis()
        }
    }

    /**
     * Get remaining cooldown time in seconds
     */
    fun getCooldownRemaining(player: Player): Int {
        val effectiveCooldown = getEffectiveCooldownSeconds(player)
        if (effectiveCooldown <= 0) return 0

        val lastChatTime = chatCooldowns[player.uniqueId] ?: 0L
        val currentTime = System.currentTimeMillis()
        val cooldownMs = effectiveCooldown * 1000L
        val elapsedMs = currentTime - lastChatTime

        return if (elapsedMs >= cooldownMs) {
            0
        } else {
            ((cooldownMs - elapsedMs) / 1000).toInt() + 1
        }
    }

    /**
     * Toggle social spy for a player
     */
    fun toggleSocialSpy(player: Player): Boolean {
        if (!player.hasPermission("locopillars.chat.socialspy")) {
            player.sendMessage(ChatColor.RED.toString() + "You don't have permission to use social spy.")
            return false
        }

        val enabled = !isSocialSpyEnabled(player)
        socialSpyToggle[player.uniqueId] = enabled

        val status = if (enabled) "${ChatColor.GREEN}enabled" else "${ChatColor.RED}disabled"
        player.sendMessage(ChatColor.GRAY.toString() + "Social spy $status.")

        return enabled
    }

    /**
     * Check if social spy is enabled for a player
     */
    fun isSocialSpyEnabled(player: Player): Boolean {
        return socialSpyToggle.getOrDefault(player.uniqueId, true) // Default enabled for permission holders
    }

    /**
     * Toggle team chat for a player
     */
    fun toggleTeamChat(player: Player): Boolean {
        val instance = gameManager.getPlayerInstance(player)
        if (instance == null || instance.state != GameState.ACTIVE) {
            player.sendMessage(ChatColor.RED.toString() + "Team chat is only available during active games.")
            return false
        }

        if (SpectatorUtils.isSpectator(player, plugin)) {
            player.sendMessage(ChatColor.RED.toString() + "Spectators cannot use team chat.")
            return false
        }

        val enabled = !isTeamChatEnabled(player)
        teamChatToggle[player.uniqueId] = enabled

        val status = if (enabled) "${ChatColor.GREEN}enabled" else "${ChatColor.RED}disabled"
        player.sendMessage(ChatColor.GRAY.toString() + "Team chat $status.")

        return enabled
    }

    /**
     * Check if team chat is enabled for a player
     */
    fun isTeamChatEnabled(player: Player): Boolean {
        return teamChatToggle.getOrDefault(player.uniqueId, false) // Default disabled
    }

    /**
     * Send join message
     */
    fun sendJoinMessage(player: Player, isGameJoin: Boolean = false) {
        val format = if (isGameJoin) configManager.gameJoinFormat else configManager.joinMessageFormat
        val message = formatMessage(format, player, "")

        val recipients = if (isGameJoin) {
            val instance = gameManager.getPlayerInstance(player)
            instance?.players?.mapNotNull { Bukkit.getPlayer(it) } ?: emptyList()
        } else {
            Bukkit.getOnlinePlayers().filter { gameManager.getPlayerInstance(it) == null }
        }

        recipients.forEach { it.sendMessage(message) }

        if (configManager.chatLogEnabled) {
            plugin.logger.info("[Chat] [${if (isGameJoin) "Game-Join" else "Join"}] ${player.name} joined")
        }
    }

    /**
     * Send leave message
     */
    fun sendLeaveMessage(player: Player, isGameLeave: Boolean = false) {
        val format = if (isGameLeave) configManager.gameLeaveFormat else configManager.leaveMessageFormat
        val message = formatMessage(format, player, "")

        val recipients = if (isGameLeave) {
            val instance = gameManager.getPlayerInstance(player)
            instance?.players?.mapNotNull { Bukkit.getPlayer(it) } ?: emptyList()
        } else {
            Bukkit.getOnlinePlayers().filter { gameManager.getPlayerInstance(it) == null }
        }

        recipients.forEach { it.sendMessage(message) }

        if (configManager.chatLogEnabled) {
            plugin.logger.info("[Chat] [${if (isGameLeave) "Game-Leave" else "Leave"}] ${player.name} left")
        }
    }

    /**
     * Send announcement to specific group
     */
    fun sendAnnouncement(message: String, recipients: Collection<Player>) {
        val formattedMessage = ChatColor.translateAlternateColorCodes('&', message)
        recipients.forEach { it.sendMessage(formattedMessage) }
    }

    /**
     * Send announcement to lobby players
     */
    fun sendLobbyAnnouncement(message: String) {
        val lobbyPlayers = Bukkit.getOnlinePlayers().filter {
            gameManager.getPlayerInstance(it) == null
        }
        sendAnnouncement(message, lobbyPlayers)
    }

    /**
     * Send announcement to game instance players
     */
    fun sendGameAnnouncement(instanceId: UUID, message: String) {
        val instance = gameManager.getAllInstances().find { it.id == instanceId }
        if (instance != null) {
            val gamePlayers = instance.players.mapNotNull { Bukkit.getPlayer(it) }
            sendAnnouncement(message, gamePlayers)
        }
    }

    /**
     * Send announcement to all players
     */
    fun sendGlobalAnnouncement(message: String) {
        sendAnnouncement(message, Bukkit.getOnlinePlayers())
    }

    /**
     * Clear player data on disconnect
     */
    fun clearPlayerData(player: Player) {
        chatCooldowns.remove(player.uniqueId)
        socialSpyToggle.remove(player.uniqueId)
        teamChatToggle.remove(player.uniqueId)
        playerCooldownOverrides.remove(player.uniqueId)
    }

    /**
     * Get chat statistics
     */
    fun getChatStats(): Map<String, Any> {
        return mapOf(
            "players_with_cooldown" to chatCooldowns.size,
            "players_with_cooldown_override" to playerCooldownOverrides.size,
            "social_spy_users" to socialSpyToggle.values.count { it },
            "team_chat_users" to teamChatToggle.values.count { it },
            "chat_enabled" to configManager.chatEnabled,
            "social_spy_enabled" to configManager.socialSpyEnabled,
            "profanity_filter_enabled" to configManager.profanityFilterEnabled,
            "chat_cooldown_seconds" to configManager.chatCooldownSeconds,
            "max_chat_length" to configManager.maxChatLength
        )
    }

    /**
     * Cleanup method for plugin disable
     */
    fun cleanup() {
        chatCooldowns.clear()
        socialSpyToggle.clear()
        teamChatToggle.clear()
        playerCooldownOverrides.clear()
    }

    /**
     * Debug information
     */
    fun debugLog(message: String) {
        if (configManager.debugMode) {
            plugin.logger.info("[ChatUtils] $message")
        }
    }
}