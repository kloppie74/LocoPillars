// Vote GUI layout configuration.
// Edit this file to customise the voting menu your players see.
// The last expression must be a VotingGuiConfig object.
//
// Arguments are POSITIONAL (not named) because ProGuard strips constructor
// parameter names from the obfuscated jar, so named arguments cannot be
// resolved when this script is compiled at runtime.
//
// Order:
//   1) title          — menu title
//   2) rows           — inventory rows (1-6)
//   3) voteSlots      — slots for voting options (up to 10, skipping borders)
//   4) borderMaterial — material name for the border panes
//   5) infoSlot       — slot of the info book (vote counts)
//   6) closeSlot      — slot of the barrier close button

VotingGuiConfig(
    "§8§l✦ Vote for Event! ✦",
    4,
    listOf(10, 11, 12, 13, 14, 15, 16, 20, 21, 22),
    "PURPLE_STAINED_GLASS_PANE",
    4,
    31
)
