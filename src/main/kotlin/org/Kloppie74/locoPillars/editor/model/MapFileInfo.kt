package org.Kloppie74.locoPillars.editor.model

import java.io.File

/**
 * Snapshot of metadata about a map's files on disk.
 *
 * Used by [org.Kloppie74.locoPillars.editor.GameFileService] and the
 * [org.Kloppie74.locoPillars.editor.EditorFileValidator] to communicate status
 * without requiring callers to read from disk again.
 *
 * @param mapName The logical name of the map (folder name under `maps/`).
 * @param settingsFile Reference to the `settings.yml` file.
 * @param schematicFile Reference to the `region.schem` file.
 * @param settingsExists Whether the settings file exists on disk.
 * @param schematicExists Whether the schematic file exists on disk.
 * @param settingsSizeBytes Size of the settings file in bytes, or 0 if missing.
 * @param schematicSizeBytes Size of the schematic file in bytes, or 0 if missing.
 */
data class MapFileInfo(
    val mapName: String,
    val settingsFile: File,
    val schematicFile: File,
    val settingsExists: Boolean,
    val schematicExists: Boolean,
    val settingsSizeBytes: Long = 0L,
    val schematicSizeBytes: Long = 0L
) {
    /** True only when both required files are present. */
    val isComplete: Boolean get() = settingsExists && schematicExists

    /** Human-readable summary for logging or chat. */
    fun summary(): String {
        val yml = if (settingsExists) "&asettings.yml (${settingsSizeBytes}B)" else "&cmissing settings.yml"
        val schem = if (schematicExists) "&aregion.schem (${schematicSizeBytes}B)" else "&cmissing region.schem"
        return "&e$mapName&7: $yml&7, $schem"
    }
}
