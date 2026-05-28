package org.Kloppie74.locoPillars.game.voting

data class VotingGuiConfig(
    val title: String = "§8§l✦ Vote for Event! ✦",
    val rows: Int = 4,
    val voteSlots: List<Int> = listOf(10, 11, 12, 13, 14, 15, 16, 20, 21, 22),
    val borderMaterial: String = "PURPLE_STAINED_GLASS_PANE",
    val infoSlot: Int = 4,
    val closeSlot: Int = 31
)
