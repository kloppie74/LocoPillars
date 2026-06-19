package org.Kloppie74.locoPillars.commands

import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.database.dao.PlayerDAO
import org.Kloppie74.locoPillars.database.dao.StatsDAO
import org.Kloppie74.locoPillars.utils.BoostUtil
import org.Kloppie74.locoPillars.utils.MSG
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class ProfileCommand(private val plugin: LocoPillars) : CommandExecutor {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        val player = sender as? Player ?: run {
            sender.sendMessage("§cOnly players can use this command.")
            return true
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val playerId = PlayerDAO.getPlayerId(player.name) ?: run {
                    player.sendMessage(MSG.chatColors("&cCould not find your player data."))
                    return@Runnable
                }
                val stats = StatsDAO.findById(playerId) ?: run {
                    player.sendMessage(MSG.chatColors("&cNo stats found for your profile."))
                    return@Runnable
                }

                val level = stats.level
                val experience = stats.experience

                // XP formula (same as StatsCommand)
                val nextLevelXP = level * level * 100
                val currentLevelXP = (level - 1) * (level - 1) * 100
                val xpIntoLevel = (experience - currentLevelXP).coerceAtLeast(0)
                val xpNeeded = (nextLevelXP - currentLevelXP).coerceAtLeast(1)
                val xpToNext = (nextLevelXP - experience).coerceAtLeast(0)
                val progressPercent = (xpIntoLevel * 100.0 / xpNeeded).coerceIn(0.0, 100.0).toInt()

                val bar = progressBar(progressPercent)

                // Combat
                val kills = stats.totalKills
                val deaths = stats.totalDeaths
                val kd = if (deaths > 0) "%.2f".format(kills.toDouble() / deaths) else kills.toString()
                val wins = stats.gamesWon
                val gamesPlayed = stats.gamesPlayed
                val winRate = if (gamesPlayed > 0) "%.1f".format(wins * 100.0 / gamesPlayed) else "0.0"
                val bestKillstreak = stats.bestKillstreak

                // Currency
                val coins = stats.coins

                // Playtime
                val playtimeStr = formatTime(stats.totalPlaytime)

                // Boosters
                val xpMult = BoostUtil.getXpMultiplier(player)
                val coinsMult = BoostUtil.getCoinsMultiplier(player)
                val xpBoostLine = if (xpMult != 100)
                    MSG.chatColors("&7XP Booster: &a§l+${xpMult - 100}% &7extra XP")
                else
                    MSG.chatColors("&7XP Booster: &8No active booster")
                val coinsBoostLine = if (coinsMult != 100)
                    MSG.chatColors("&7Coins Booster: &a§l+${coinsMult - 100}% &7extra Coins")
                else
                    MSG.chatColors("&7Coins Booster: &8No active booster")

                val lines = mutableListOf<String>().apply {
                    add(MSG.chatColors("&8&m" + "▬".repeat(38)))
                    add(MSG.chatColors("&6&l ★ PROFILE &8— &e${player.name}"))
                    add(MSG.chatColors("&8&m" + "▬".repeat(38)))
                    add("")
                    add(MSG.chatColors(" &e&lPROGRESSION"))
                    add(MSG.chatColors("  &7Level: &6&l$level  &8|  &7XP: &b${fmt(experience)} &8/ &7${fmt(nextLevelXP)}"))
                    add(MSG.chatColors("  &8[&r$bar&8] &a$progressPercent%"))
                    add(MSG.chatColors("  &7Next level: &b${fmt(xpToNext)} XP"))
                    add("")
                    add(MSG.chatColors(" &c&lCOMBAT"))
                    add(MSG.chatColors("  &7Kills: &c${fmt(kills)}  &7Deaths: &f${fmt(deaths)}  &7K/D: &a$kd"))
                    add(MSG.chatColors("  &7Wins: &a${fmt(wins)} &8/ &f${fmt(gamesPlayed)} games &8(&a$winRate%&8)"))
                    add(MSG.chatColors("  &7Best Killstreak: &6⚡ $bestKillstreak"))
                    add("")
                    add(MSG.chatColors(" &e&lCURRENCY"))
                    add(MSG.chatColors("  &7Coins: &6${fmt(coins)}"))
                    add(MSG.chatColors("  &7Playtime: &b$playtimeStr"))
                    add("")
                    add(MSG.chatColors(" &b&lBOOSTERS"))
                    add("  $xpBoostLine")
                    add("  $coinsBoostLine")
                    add(MSG.chatColors("&8&m" + "▬".repeat(38)))
                }

                Bukkit.getScheduler().runTask(plugin, Runnable {
                    lines.forEach { player.sendMessage(it) }
                })
            } catch (ex: Exception) {
                plugin.logger.severe("[ProfileCommand] Error loading profile for ${player.name}: ${ex.message}")
                player.sendMessage(MSG.chatColors("&cAn error occurred while loading your profile."))
            }
        })
        return true
    }

    private fun progressBar(percent: Int, length: Int = 20): String {
        val filled = (percent * length / 100).coerceIn(0, length)
        return "§a" + "█".repeat(filled) + "§8" + "░".repeat(length - filled)
    }

    private fun fmt(n: Int): String = when {
        n >= 1_000_000 -> "${"%.1f".format(n / 1_000_000.0)}M"
        n >= 1_000     -> "${"%.1f".format(n / 1_000.0)}K"
        else           -> n.toString()
    }

    private fun formatTime(seconds: Long): String = when {
        seconds < 60   -> "${seconds}s"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
        else           -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
    }
}
