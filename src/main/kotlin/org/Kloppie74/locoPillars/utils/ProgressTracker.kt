package org.Kloppie74.locoPillars.utils

import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import kotlin.math.roundToInt

class ProgressTracker(
    private val plugin: JavaPlugin,
    private val taskName: String,
    private val totalSteps: Int,
    private val showProgressBar: Boolean = true
) {
    private var currentStep = 0
    private val startTime = System.currentTimeMillis()
    private var lastUpdateTime = System.currentTimeMillis()
    private var updateTask: BukkitRunnable? = null
    private var lastPercentage = -1

    init {
        if (showProgressBar) {
            plugin.logger.info("[$taskName] Starting: $totalSteps operations to complete")
            startProgressUpdater()
        }
    }

    private fun startProgressUpdater() {
        updateTask = object : BukkitRunnable() {
            override fun run() {
                if (currentStep >= totalSteps) {
                    cancel()
                    return
                }

                val currentTime = System.currentTimeMillis()
                // Update every 2 seconds or when step changes significantly
                if (currentTime - lastUpdateTime >= 2000) {
                    updateProgress()
                    lastUpdateTime = currentTime
                }
            }
        }
        updateTask?.runTaskTimer(plugin, 0L, 40L) // Every 2 seconds
    }

    fun incrementStep(description: String = "") {
        currentStep++

        if (!showProgressBar) {
            // Just log the basic info without progress bar
            if (description.isNotEmpty()) {
                plugin.logger.info("[$taskName] Step $currentStep/$totalSteps: $description")
            }
            return
        }

        val percentage = ((currentStep.toDouble() / totalSteps) * 100).roundToInt()

        // Only update if percentage changed significantly
        if (percentage != lastPercentage && (percentage % 5 == 0 || percentage == 100)) {
            updateProgress(description)
            lastPercentage = percentage
        }
    }

    private fun updateProgress(description: String = "") {
        val percentage = ((currentStep.toDouble() / totalSteps) * 100).roundToInt()
        val progressBar = createProgressBar(percentage)
        val elapsedTime = System.currentTimeMillis() - startTime
        val eta = calculateETA(elapsedTime, currentStep, totalSteps)

        val message = buildString {
            append("[$taskName] $progressBar $percentage% ")
            append("($currentStep/$totalSteps)")
            if (description.isNotEmpty()) {
                append(" - $description")
            }
            if (eta != null) {
                append(" | ETA: ${formatTime(eta)}")
            }
        }

        plugin.logger.info(message)
    }

    private fun createProgressBar(percentage: Int, width: Int = 30): String {
        val filled = (percentage * width / 100).coerceIn(0, width)
        val empty = width - filled

        return buildString {
            append("§a")
            repeat(filled) { append("█") }
            append("§7")
            repeat(empty) { append("█") }
            append("§r")
        }
    }

    private fun calculateETA(elapsedMillis: Long, currentStep: Int, totalSteps: Int): Long? {
        if (currentStep <= 0) return null

        val avgTimePerStep = elapsedMillis.toDouble() / currentStep
        val remainingSteps = totalSteps - currentStep

        return (avgTimePerStep * remainingSteps).toLong()
    }

    private fun formatTime(millis: Long): String {
        val seconds = millis / 1000
        val minutes = seconds / 60
        val hours = minutes / 60

        return when {
            hours > 0 -> "${hours}h ${minutes % 60}m"
            minutes > 0 -> "${minutes}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    fun complete(finalMessage: String = "") {
        updateTask?.cancel()

        val totalTime = System.currentTimeMillis() - startTime
        val timeFormatted = formatTime(totalTime)

        val message = if (finalMessage.isNotEmpty()) {
            "[$taskName] ✓ Complete! $finalMessage (took $timeFormatted)"
        } else {
            "[$taskName] ✓ Complete! $totalSteps operations finished in $timeFormatted"
        }

        plugin.logger.info(message)
    }

    fun fail(errorMessage: String) {
        updateTask?.cancel()

        val totalTime = System.currentTimeMillis() - startTime
        val timeFormatted = formatTime(totalTime)

        plugin.logger.severe("[$taskName] ✗ Failed after $timeFormatted at step $currentStep/$totalSteps: $errorMessage")
    }

    fun getCurrentStep(): Int = currentStep
    fun getTotalSteps(): Int = totalSteps
    fun getPercentage(): Int = ((currentStep.toDouble() / totalSteps) * 100).roundToInt()
}