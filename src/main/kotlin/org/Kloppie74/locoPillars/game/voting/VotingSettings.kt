package org.Kloppie74.locoPillars.game.voting

import org.bukkit.Material
import java.io.File

data class VotingSettings(
    val id: String,
    val displayName: String,
    val description: String,
    val iconMaterial: Material,
    val iconCustomModelData: Int,
    val active: Boolean,
    val voteWeight: Int,
    val duration: Int,
    val announceTitle: String,
    val announceSubtitle: String,
    val announceChat: String,
    val scriptFile: File
)
