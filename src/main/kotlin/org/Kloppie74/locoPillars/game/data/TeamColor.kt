package org.Kloppie74.locoPillars.game.data

import org.bukkit.Color

enum class TeamColor {
    BLACK, DARK_BLUE, DARK_GREEN, DARK_AQUA, DARK_RED, DARK_PURPLE, GOLD, GRAY, YELLOW, BLUE, GREEN, RED;

    fun toBukkitColor(): Color {
        return when (this) {
            BLACK -> Color.BLACK
            DARK_BLUE -> Color.NAVY
            DARK_GREEN -> Color.GREEN
            DARK_AQUA -> Color.AQUA
            DARK_RED -> Color.MAROON
            DARK_PURPLE -> Color.PURPLE
            GOLD -> Color.YELLOW
            GRAY -> Color.GRAY
            YELLOW -> Color.YELLOW
            BLUE -> Color.BLUE
            GREEN -> Color.LIME
            RED -> Color.RED
        }
    }

    fun toChatColor(): org.bukkit.ChatColor {
        return when (this) {
            BLACK -> org.bukkit.ChatColor.BLACK
            DARK_BLUE -> org.bukkit.ChatColor.DARK_BLUE
            DARK_GREEN -> org.bukkit.ChatColor.DARK_GREEN
            DARK_AQUA -> org.bukkit.ChatColor.DARK_AQUA
            DARK_RED -> org.bukkit.ChatColor.DARK_RED
            DARK_PURPLE -> org.bukkit.ChatColor.DARK_PURPLE
            GOLD -> org.bukkit.ChatColor.GOLD
            GRAY -> org.bukkit.ChatColor.GRAY
            YELLOW -> org.bukkit.ChatColor.YELLOW
            BLUE -> org.bukkit.ChatColor.BLUE
            GREEN -> org.bukkit.ChatColor.GREEN
            RED -> org.bukkit.ChatColor.RED
        }
    }
}