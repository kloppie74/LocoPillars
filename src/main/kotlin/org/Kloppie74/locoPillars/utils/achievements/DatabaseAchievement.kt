package org.Kloppie74.locoPillars.utils.achievements

/**
 * Data class representing one achievement definition to be saved in the database.
 */
data class DatabaseAchievement(
    val achievementId: String,
    val achievementName: String,
    val displayName: String,
    val description: String,
    val category: String,
    val achievementType: String,
    val targetValue: Int?,
    val requirementType: String, // Enum name!
    val difficulty: String,
    val rarityPercentage: Double,
    val experienceReward: Int,
    val coinReward: Int,
    val specialRewards: String?, // Can be JSON, expand as needed
    val iconMaterial: String,
    val iconCustomModelData: Int?,
    val displayOrder: Int,
    val isHidden: Boolean,
    val isSecret: Boolean,
    val isActive: Boolean,
    val unlockMessage: String?
)