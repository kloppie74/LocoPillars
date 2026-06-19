package org.Kloppie74.locoPillars.commands

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.database.dao.PlayerDAO
import org.Kloppie74.locoPillars.database.dao.StatsDAO
import org.Kloppie74.locoPillars.utils.BoostUtil
import org.Kloppie74.locoPillars.utils.MSG

/**
 * /pillarsadmin — administrative commands for managing player currencies & stats.
 *
 * All subcommands require `locopillars.admin`.
 *
 * Subcommands:
 *   addcoins    <player> <amount>   Add coins to a player
 *   removecoins <player> <amount>   Remove coins from a player
 *   setcoins    <player> <amount>   Set a player's coins to an exact value
 *   addxp       <player> <amount>   Add experience to a player
 *   setxp       <player> <amount>   Set a player's experience to an exact value
 *   setlevel    <player> <level>    Set a player's level
 *   stats       <player>            Show coins / XP / level for a player
 *   help                            Show this help
 *
 * Intended use in Rewards commands (RewardProcessor dispatches these as console):
 *   pillarsadmin addcoins %player% 50
 *   pillarsadmin addxp    %player% 10
 */
class PillarsAdminCommand(private val plugin: LocoPillars) : CommandExecutor, TabCompleter {

    private val permission = "locopillars.admin"

    private val subcommands = listOf(
        "addcoins", "removecoins", "setcoins",
        "addxp", "setxp", "setlevel",
        "stats", "help"
    )

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(MSG.lang("pillarsadmin.no_permission"))
            return true
        }

        if (args.isEmpty()) { showHelp(sender); return true }

        when (args[0].lowercase()) {
            "addcoins"    -> handleCurrency(sender, args, CurrencyOp.ADD,    "coins")
            "removecoins" -> handleCurrency(sender, args, CurrencyOp.REMOVE, "coins")
            "setcoins"    -> handleCurrency(sender, args, CurrencyOp.SET,    "coins")
            "addxp"       -> handleCurrency(sender, args, CurrencyOp.ADD,    "xp")
            "setxp"       -> handleCurrency(sender, args, CurrencyOp.SET,    "xp")
            "setlevel"    -> handleCurrency(sender, args, CurrencyOp.SET,    "level")
            "stats"       -> handleStats(sender, args)
            "help"        -> showHelp(sender)
            else -> {
                sender.sendMessage(MSG.lang("general.unknown_subcommand", "input" to args[0]))
                showHelp(sender)
            }
        }
        return true
    }

    // =========================================================================
    // Currency operations
    // =========================================================================

    private enum class CurrencyOp { ADD, REMOVE, SET }

    private fun handleCurrency(sender: CommandSender, args: Array<out String>, op: CurrencyOp, type: String) {
        if (args.size < 3) {
            val usageKey = when (type) {
                "coins" -> when (op) {
                    CurrencyOp.ADD    -> "pillarsadmin.usage.addcoins"
                    CurrencyOp.REMOVE -> "pillarsadmin.usage.removecoins"
                    CurrencyOp.SET    -> "pillarsadmin.usage.setcoins"
                }
                "xp"    -> if (op == CurrencyOp.ADD) "pillarsadmin.usage.addxp" else "pillarsadmin.usage.setxp"
                "level" -> "pillarsadmin.usage.setlevel"
                else    -> "pillarsadmin.usage.addcoins"
            }
            sender.sendMessage(MSG.lang(usageKey))
            return
        }

        val targetName = args[1]
        val amount = args[2].toIntOrNull()?.takeIf { it >= 0 } ?: run {
            sender.sendMessage(MSG.lang("pillarsadmin.error.invalid_amount", "input" to args[2]))
            return
        }

        // Run async so we don't block the main thread with DB calls
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val playerId = PlayerDAO.getPlayerId(targetName) ?: run {
                    sender.sendMessage(MSG.lang("pillarsadmin.error.player_not_found", "player" to targetName))
                    return@Runnable
                }

                val onlineTarget = Bukkit.getPlayer(targetName)
                val boostedCoins = if (op == CurrencyOp.ADD && type == "coins")
                    onlineTarget?.let { BoostUtil.applyCoins(it, amount) } ?: amount else amount
                val boostedXp = if (op == CurrencyOp.ADD && type == "xp")
                    onlineTarget?.let { BoostUtil.applyXp(it, amount) } ?: amount else amount

                val success = when (type) {
                    "coins" -> when (op) {
                        CurrencyOp.ADD    -> StatsDAO.addCoins(playerId, boostedCoins)
                        CurrencyOp.REMOVE -> StatsDAO.removeCoins(playerId, amount)
                        CurrencyOp.SET    -> StatsDAO.updateIntStat(playerId, "coins", amount)
                    }
                    "xp"    -> when (op) {
                        CurrencyOp.ADD -> StatsDAO.addExperience(playerId, boostedXp)
                        else           -> StatsDAO.updateIntStat(playerId, "experience", amount)
                    }
                    "level" -> StatsDAO.updateLevel(playerId, amount)
                    else    -> false
                }

                if (!success) {
                    sender.sendMessage(MSG.lang("pillarsadmin.error.db_failed", "player" to targetName))
                    return@Runnable
                }

                // Fetch new value for the confirmation message
                val newVal = when (type) {
                    "coins" -> StatsDAO.getIntStat(playerId, "coins")
                    "xp"    -> StatsDAO.getIntStat(playerId, "experience")
                    "level" -> StatsDAO.getIntStat(playerId, "level")
                    else    -> amount
                }

                val msgKey = when (type) {
                    "coins" -> when (op) {
                        CurrencyOp.ADD    -> "pillarsadmin.success.addcoins"
                        CurrencyOp.REMOVE -> "pillarsadmin.success.removecoins"
                        CurrencyOp.SET    -> "pillarsadmin.success.setcoins"
                    }
                    "xp"    -> if (op == CurrencyOp.ADD) "pillarsadmin.success.addxp" else "pillarsadmin.success.setxp"
                    "level" -> "pillarsadmin.success.setlevel"
                    else    -> "pillarsadmin.success.addcoins"
                }

                sender.sendMessage(MSG.lang(msgKey,
                    "player"  to targetName,
                    "amount"  to amount.toString(),
                    "new_val" to newVal.toString()))

                // Notify the target if online
                onlineTarget?.let { target ->
                    val effectiveAmount = when {
                        type == "coins" && op == CurrencyOp.ADD -> boostedCoins
                        type == "xp"    && op == CurrencyOp.ADD -> boostedXp
                        else -> amount
                    }
                    target.sendMessage(MSG.lang(
                        when (type) {
                            "coins" -> when (op) {
                                CurrencyOp.ADD    -> "pillarsadmin.notify.coins_added"
                                CurrencyOp.REMOVE -> "pillarsadmin.notify.coins_removed"
                                CurrencyOp.SET    -> "pillarsadmin.notify.coins_set"
                            }
                            "xp"    -> if (op == CurrencyOp.ADD) "pillarsadmin.notify.xp_added" else "pillarsadmin.notify.xp_set"
                            "level" -> "pillarsadmin.notify.level_set"
                            else    -> "pillarsadmin.notify.coins_added"
                        },
                        "amount"  to effectiveAmount.toString(),
                        "new_val" to newVal.toString()
                    ))
                    // Show booster bonus line when a multiplier was active
                    if (type == "coins" && op == CurrencyOp.ADD && boostedCoins != amount) {
                        val bonus = boostedCoins - amount
                        target.sendMessage(MSG.chatColors("&8(+$bonus Coins Booster &8— &b${BoostUtil.getCoinsMultiplier(target) - 100}% active&8)"))
                    } else if (type == "xp" && op == CurrencyOp.ADD && boostedXp != amount) {
                        val bonus = boostedXp - amount
                        target.sendMessage(MSG.chatColors("&8(+$bonus XP Booster &8— &b${BoostUtil.getXpMultiplier(target) - 100}% active&8)"))
                    }
                    // Level-up check when XP was added
                    if (type == "xp" && op == CurrencyOp.ADD && success) {
                        val playerId2 = PlayerDAO.getPlayerId(target.name)
                        if (playerId2 != null) {
                            val newLevel = StatsDAO.checkAndApplyLevelUp(playerId2)
                            if (newLevel != null) {
                                Bukkit.getScheduler().runTask(plugin, Runnable {
                                    target.sendTitle("§6§l★ LEVEL UP!", "§eYou reached level §6§l$newLevel§e!", 10, 60, 10)
                                    target.sendMessage("§6§l★ Level Up! §eYou are now level §6§l$newLevel§e!")
                                    target.playSound(target.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.5f)
                                })
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                plugin.logger.severe("[PillarsAdminCommand] DB error: ${e.message}")
                sender.sendMessage(MSG.lang("pillarsadmin.error.db_failed", "player" to targetName))
            }
        })
    }

    // =========================================================================
    // Stats view
    // =========================================================================

    private fun handleStats(sender: CommandSender, args: Array<out String>) {
        if (args.size < 2) { sender.sendMessage(MSG.lang("pillarsadmin.usage.stats")); return }
        val targetName = args[1]

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            try {
                val playerId = PlayerDAO.getPlayerId(targetName) ?: run {
                    sender.sendMessage(MSG.lang("pillarsadmin.error.player_not_found", "player" to targetName))
                    return@Runnable
                }
                val stats = StatsDAO.findById(playerId) ?: run {
                    sender.sendMessage(MSG.lang("pillarsadmin.error.no_stats", "player" to targetName))
                    return@Runnable
                }
                sender.sendMessage(MSG.lang("pillarsadmin.stats.header", "player" to targetName))
                sender.sendMessage(MSG.lang("pillarsadmin.stats.coins",  "value" to stats.coins.toString()))
                sender.sendMessage(MSG.lang("pillarsadmin.stats.xp",     "value" to stats.experience.toString()))
                sender.sendMessage(MSG.lang("pillarsadmin.stats.level",  "value" to stats.level.toString()))
                sender.sendMessage(MSG.lang("pillarsadmin.stats.wins",   "value" to stats.gamesWon.toString()))
                sender.sendMessage(MSG.lang("pillarsadmin.stats.kills",  "value" to stats.totalKills.toString()))
            } catch (e: Exception) {
                plugin.logger.severe("[PillarsAdminCommand] DB error: ${e.message}")
                sender.sendMessage(MSG.lang("pillarsadmin.error.db_failed", "player" to targetName))
            }
        })
    }

    // =========================================================================
    // Help
    // =========================================================================

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage(MSG.lang("pillarsadmin.help.header"))
        sender.sendMessage(MSG.lang("pillarsadmin.help.addcoins"))
        sender.sendMessage(MSG.lang("pillarsadmin.help.removecoins"))
        sender.sendMessage(MSG.lang("pillarsadmin.help.setcoins"))
        sender.sendMessage(MSG.lang("pillarsadmin.help.addxp"))
        sender.sendMessage(MSG.lang("pillarsadmin.help.setxp"))
        sender.sendMessage(MSG.lang("pillarsadmin.help.setlevel"))
        sender.sendMessage(MSG.lang("pillarsadmin.help.stats"))
        sender.sendMessage(MSG.lang("pillarsadmin.help.rewards_hint"))
    }

    // =========================================================================
    // Tab completion
    // =========================================================================

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!sender.hasPermission(permission)) return emptyList()
        return when (args.size) {
            1 -> subcommands.filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> Bukkit.getOnlinePlayers().map { it.name }
                .filter { it.startsWith(args[1], ignoreCase = true) }
            3 -> when (args[0].lowercase()) {
                "addcoins", "removecoins", "setcoins" ->
                    listOf("10", "50", "100", "250", "500", "1000")
                        .filter { it.startsWith(args[2]) }
                "addxp", "setxp" ->
                    listOf("5", "10", "25", "50", "100", "250")
                        .filter { it.startsWith(args[2]) }
                "setlevel" ->
                    listOf("1", "5", "10", "25", "50", "100")
                        .filter { it.startsWith(args[2]) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}
