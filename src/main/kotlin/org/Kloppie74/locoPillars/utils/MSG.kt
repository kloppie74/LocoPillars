package org.Kloppie74.locoPillars.utils

import net.md_5.bungee.api.ChatColor

object MSG {

    fun chatColors(message: String?): String {
        if (message == null) return ""

        var processed = message

        // Handle &#RRGGBB format (must be done BEFORE replacing & with §)
        val ampHexPattern = Regex("&#([a-fA-F0-9]{6})")
        processed = ampHexPattern.replace(processed) { matchResult ->
            val hexCode = matchResult.groupValues[1]
            buildString {
                append("§x")
                hexCode.forEach { c -> append("§").append(c) }
            }
        }

        // Handle hex color codes in #RRGGBB format (after ampersand hex processing)
        val regularHexPattern = Regex("#([a-fA-F0-9]{6})")
        processed = regularHexPattern.replace(processed) { matchResult ->
            val hexCode = matchResult.groupValues[1]
            buildString {
                append("§x")
                hexCode.forEach { c -> append("§").append(c) }
            }
        }

        // Replace only valid & codes (&a..&f, &0..&9, &k..&o, &r)
        val validColorCodePattern = Regex("&([0-9a-fk-orA-FK-OR])")
        processed = validColorCodePattern.replace(processed) { matchResult ->
            "§${matchResult.groupValues[1]}"
        }

        // Leave any other & intact (including &&)
        // Already handled: if it is not a valid code it stays as &

        // Process gradient pattern if found
        val gradientPattern = Regex("<gradient:(#[a-fA-F0-9]{6}),(#[a-fA-F0-9]{6})>(.*?)</gradient>")
        var gradientMatch = gradientPattern.find(processed)
        while (gradientMatch != null) {
            val startColor = gradientMatch.groupValues[1]
            val endColor = gradientMatch.groupValues[2]
            val text = gradientMatch.groupValues[3]

            val gradientText = applyGradient(startColor, endColor, text)
            if (processed != null) {
                processed = processed.replace(gradientMatch.value, gradientText)
            }
            gradientMatch = processed?.let { gradientPattern.find(it) }
        }

        // Final translation to handle any remaining color codes
        return ChatColor.translateAlternateColorCodes('§', processed)
    }

    private fun applyGradient(startColor: String, endColor: String, text: String): String {
        val gradientText = StringBuilder()
        val length = minOf(text.length, 16)

        for (i in 0 until length) {
            val ratio = if (length > 1) i.toFloat() / (length - 1) else 0f
            val interpolatedColor = interpolateColor(startColor, endColor, ratio)
            gradientText.append(ChatColor.of(interpolatedColor)).append(text[i])
        }

        return gradientText.toString()
    }

    private fun interpolateColor(startColor: String, endColor: String, ratio: Float): String {
        val startR = startColor.substring(1, 3).toInt(16)
        val startG = startColor.substring(3, 5).toInt(16)
        val startB = startColor.substring(5, 7).toInt(16)

        val endR = endColor.substring(1, 3).toInt(16)
        val endG = endColor.substring(3, 5).toInt(16)
        val endB = endColor.substring(5, 7).toInt(16)

        val red = (startR + (endR - startR) * ratio).toInt()
        val green = (startG + (endG - startG) * ratio).toInt()
        val blue = (startB + (endB - startB) * ratio).toInt()

        return String.format("#%02x%02x%02x", red, green, blue)
    }
}