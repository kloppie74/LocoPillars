package org.Kloppie74.locoPillars.utils.achievements

/**
 * Enum of all valid achievement triggers/statistics.
 * Only real database/stat fields!
 */
enum class AchievementTrigger(val yamlKey: String) {
    BLOCKS_PLACED("blocks_placed"),
    BLOCKS_BROKEN("blocks_broken"),
    WALK_DISTANCE("walk_distance"),
    WIN_GAME("win_game"),
    LOSE_GAME("lose_game"),
    QUIT_GAME("quit_game"),
    KILL_PLAYER("kill_player"),
    DEATH("death"),
    ASSIST("assist"),
    GAMES_PLAYED("games_played"),
    GAMES_WON("games_won"),
    GAMES_LOST("games_lost"),
    GAMES_QUIT("games_quit"),
    DOUBLE_KILL("double_kill"),
    TRIPLE_KILL("triple_kill"),
    QUAD_KILL("quad_kill"),
    PERFECT_GAME("perfect_game"),
    COMEBACK_WIN("comeback_win"),
    FASTEST_WIN("fastest_win"),
    LONGEST_GAME("longest_game"),
    KILL_STREAK("kill_streak"),
    WIN_STREAK("win_streak"),
    LOSS_STREAK("loss_streak"),
    LEVEL_UP("level_up"),
    COINS_EARNED("coins_earned"),
    EXPERIENCE_EARNED("experience_earned"),
    DAMAGE_DEALT("damage_dealt"),
    DAMAGE_TAKEN("damage_taken"),
    FALL_DAMAGE_TAKEN("fall_damage_taken"),
    TIME_PLAYED("time_played"),
    ITEMS_COLLECTED("items_collected"),
    ITEMS_CRAFTED("items_crafted"),
    TEAM_WIN("team_win"),
    TEAM_LOSS("team_loss"),
    ARENAS_PLAYED("arenas_played");

    companion object {
        fun fromYamlKey(key: String): AchievementTrigger? =
            values().find { it.yamlKey.equals(key, ignoreCase = true) }

        fun isValid(key: String): Boolean = fromYamlKey(key) != null
    }
}