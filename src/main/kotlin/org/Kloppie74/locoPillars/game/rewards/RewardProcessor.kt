package org.Kloppie74.locoPillars.game.rewards

import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player

object RewardProcessor {

    private fun debugLog(plugin: LocoPillars, msg: String) {
        if (plugin.configManager.debugMode) plugin.logger.info("[RewardProcessor] $msg")
    }

    // -------------------------------------------------------------------------
    // Public entry points
    // -------------------------------------------------------------------------

    fun processStartRewards(plugin: LocoPillars, players: List<Player>, configSection: ConfigurationSection?) {
        val section = configSection?.getConfigurationSection("OnStartGameRewards") ?: return
        if (!section.getBoolean("enabled", true)) return
        val placeholders = emptyMap<String, String>()
        players.forEach { p ->
            dispatchSection(plugin, section, p, placeholders + ("player" to p.name))
        }
    }

    fun processKillRewards(plugin: LocoPillars, killer: Player, victim: Player, remainingPlayers: List<Player>, configSection: ConfigurationSection?) {
        val killSection = configSection?.getConfigurationSection("OnKillRewards") ?: return
        if (!killSection.getBoolean("enabled", true)) return

        val byPlayer = killSection.getConfigurationSection("killed_by_player") ?: return
        val basePlaceholders = mapOf("killer" to killer.name, "death" to victim.name, "player" to killer.name)

        byPlayer.getConfigurationSection("killer")?.let { s ->
            dispatchSection(plugin, s, killer, basePlaceholders + ("player" to killer.name))
        }
        byPlayer.getConfigurationSection("killed_player")?.let { s ->
            dispatchSection(plugin, s, victim, basePlaceholders + ("player" to victim.name))
        }
        byPlayer.getConfigurationSection("remaining_players")?.let { s ->
            remainingPlayers.filter { it != killer && it != victim }.forEach { p ->
                dispatchSection(plugin, s, p, basePlaceholders + ("player" to p.name))
            }
        }
    }

    fun processLeaveRewards(plugin: LocoPillars, leftPlayer: Player, remainingPlayers: List<Player>, configSection: ConfigurationSection?) {
        val leaveSection = configSection?.getConfigurationSection("OnLeaveRewards") ?: return
        if (!leaveSection.getBoolean("enabled", true)) return
        val base = mapOf("leftplayer" to leftPlayer.name, "player" to leftPlayer.name)

        leaveSection.getConfigurationSection("left_player")?.let { s ->
            dispatchSection(plugin, s, leftPlayer, base)
        }
        leaveSection.getConfigurationSection("remaining_players")?.let { s ->
            remainingPlayers.forEach { p ->
                dispatchSection(plugin, s, p, base + ("player" to p.name))
            }
        }
    }

    fun processEndRewards(plugin: LocoPillars, winners: List<Player>, spectators: List<Player>, configSection: ConfigurationSection?) {
        val endSection = configSection?.getConfigurationSection("OnEndGameRewards") ?: return
        if (!endSection.getBoolean("enabled", true)) return

        val teamName = winners.firstOrNull()?.let {
            plugin.gameManager.getPlayerInstance(it)?.getPlayerTeam(it.uniqueId)?.name
        } ?: ""

        endSection.getConfigurationSection("winners")?.let { s ->
            winners.forEach { p ->
                dispatchSection(plugin, s, p, mapOf("player" to p.name, "winnerteamname" to teamName))
            }
        }
        endSection.getConfigurationSection("spectators")?.let { s ->
            spectators.forEach { p ->
                dispatchSection(plugin, s, p, mapOf("player" to p.name, "winnerteamname" to teamName))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private fun dispatchSection(plugin: LocoPillars, section: ConfigurationSection, player: Player, placeholders: Map<String, String>) {
        val messages = section.getStringList("messages")
        val commands = section.getStringList("commands")

        messages.forEach { raw ->
            val text = applyPlaceholders(raw, placeholders)
            player.sendMessage(MSG.chatColors(text))
            debugLog(plugin, "Sent message to ${player.name}: $text")
        }
        commands.forEach { raw ->
            val cmd = applyPlaceholders(raw, placeholders)
            if (cmd.isNotBlank()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)
                debugLog(plugin, "Dispatched command: $cmd")
            }
        }
    }

    private fun applyPlaceholders(text: String, placeholders: Map<String, String>): String {
        var result = text
        placeholders.forEach { (key, value) -> result = result.replace("%$key%", value) }
        return result
    }
}
