package org.Kloppie74.locoPillars.database.models

import java.time.LocalDateTime

data class Achievement(
    val achievementId: String,
    val achievementName: String,
    val displayName: String,
    val description: String,
    val category: String,
    val achievementType: String,
    val targetValue: Int?,
    val requirementType: String,
    val difficulty: String,
    val rarityPercentage: Double,
    val experienceReward: Int,
    val coinReward: Int,
    val specialRewards: String?,
    val iconMaterial: String,
    val iconCustomModelData: Int?,
    val displayOrder: Int,
    val isHidden: Boolean,
    val isSecret: Boolean,
    val isActive: Boolean,
    val unlockMessage: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)

data class PlayerAchievement(
    val id: Long,
    val playerId: String,
    val achievementId: String,
    val unlockedAt: LocalDateTime,
    val progressValue: Int,
    val isCompleted: Boolean
)

data class AchievementProgress(
    val playerId: String,
    val achievementId: String,
    val currentProgress: Int,
    val targetProgress: Int,
    val progressPercentage: Double,
    val lastUpdated: LocalDateTime
) {
    fun isComplete(): Boolean = currentProgress >= targetProgress
    fun getPercentageInt(): Int = progressPercentage.toInt()
}