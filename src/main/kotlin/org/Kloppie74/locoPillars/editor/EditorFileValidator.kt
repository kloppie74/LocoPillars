package org.Kloppie74.locoPillars.editor

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.Kloppie74.locoPillars.editor.model.MapFileInfo
import org.Kloppie74.locoPillars.game.map.MapRepository
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import java.io.File

/**
 * EditorFileValidator
 *
 * Performs thorough validation of all files associated with a map entry.
 * Results are accumulated in a [ValidationReport] that distinguishes between
 * hard **errors** (which make the map unusable) and soft **warnings** (which
 * hint at suboptimal configuration).
 *
 * Typical checks:
 * - Existence of `settings.yml` and `region.schem`
 * - Parsability of `settings.yml`
 * - Required YAML keys (`SpawnLocations`, `SaveRadius`, `Settings.SpawnPoint`)
 * - Spawn point sanity (at least one team with at least one spawn)
 * - Schematic file size (too small → likely corrupt or empty)
 * - Game-type config files in `Data/Games/`
 *
 * @param plugin Plugin instance for logging.
 * @param configManager Used to read debug flag.
 * @param mapRepository Source of file paths.
 */
class EditorFileValidator(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val mapRepository: MapRepository
) {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Validates all files for [mapName].
     * @return A [ValidationReport] with errors and warnings.
     */
    fun validate(mapName: String): ValidationReport {
        val errors   = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val info     = mutableListOf<String>()

        val mapFolder = File(plugin.dataFolder, "maps/$mapName")
        if (!mapFolder.exists() || !mapFolder.isDirectory) {
            errors += "Map folder does not exist: $mapName"
            return ValidationReport(mapName, errors, warnings, info)
        }

        validateSettingsFile(mapName, errors, warnings, info)
        validateSchematicFile(mapName, warnings, info)

        return ValidationReport(mapName, errors, warnings, info)
    }

    /**
     * Validates a standalone game-config file (`.yml` in `Data/Games/`).
     * Returns a [ValidationReport] with `mapName` set to the file's base name.
     */
    fun validateGameConfig(file: File): ValidationReport {
        val name     = file.nameWithoutExtension
        val errors   = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val info     = mutableListOf<String>()

        if (!file.exists()) {
            errors += "Game config file not found: ${file.name}"
            return ValidationReport(name, errors, warnings, info)
        }

        val config = tryLoadYaml(file, errors) ?: return ValidationReport(name, errors, warnings, info)

        // SpawnLocations must be present
        if (!config.contains("SpawnLocations")) {
            errors += "Missing 'SpawnLocations' section"
        } else {
            val sl = config.getConfigurationSection("SpawnLocations")
            val minPlayers = sl?.getInt("MinimumPlayers", 0) ?: 0
            if (minPlayers < 2) warnings += "MinimumPlayers is very low ($minPlayers)"
        }

        // Settings section
        if (!config.contains("Settings")) {
            warnings += "Missing 'Settings' section – defaults will be used"
        }

        val schName = file.nameWithoutExtension
        val schFile = File(plugin.dataFolder, "Data/Schems/$schName.schem")
        if (!schFile.exists()) {
            warnings += "Schematic 'Data/Schems/$schName.schem' not found – game instances may fail to load"
        } else {
            info += "Schematic file found: ${schFile.length()} bytes"
        }

        return ValidationReport(name, errors, warnings, info)
    }

    /**
     * Runs [validate] on every known map and returns a map of name → report.
     */
    fun validateAll(): Map<String, ValidationReport> {
        return mapRepository.getAllMapNames().associateWith { validate(it) }
    }

    // -------------------------------------------------------------------------
    // Internal validators
    // -------------------------------------------------------------------------

    private fun validateSettingsFile(
        mapName: String,
        errors: MutableList<String>,
        warnings: MutableList<String>,
        info: MutableList<String>
    ) {
        val settingsFile = mapRepository.getMapSettingsFile(mapName)

        if (!settingsFile.exists()) {
            errors += "settings.yml not found"
            return
        }

        info += "settings.yml size: ${settingsFile.length()} bytes"

        val config = tryLoadYaml(settingsFile, errors) ?: return

        // SpawnLocations
        if (!config.contains("SpawnLocations")) {
            errors += "settings.yml: missing 'SpawnLocations' section"
        } else {
            val sl = config.getConfigurationSection("SpawnLocations")
            val reservedKeys = setOf("MinimumPlayers", "MaximumPlayers", "MaxTeams", "PlayersPerTeam")
            val teamKeys = sl?.getKeys(false)?.filterNot { it in reservedKeys } ?: emptySet()

            if (teamKeys.isEmpty()) {
                errors += "settings.yml: no teams defined in SpawnLocations"
            } else {
                var totalSpawns = 0
                teamKeys.forEach { teamKey ->
                    val teamSection = sl?.getConfigurationSection(teamKey)
                    val spawns = teamSection?.getKeys(false)?.count { it.startsWith("Player") } ?: 0
                    totalSpawns += spawns
                    if (spawns == 0) {
                        warnings += "Team '$teamKey' has no Player spawn slots"
                    }
                }
                info += "Total spawn slots: $totalSpawns across ${teamKeys.size} team(s)"
            }
        }

        // SaveRadius
        if (!config.contains("SaveRadius") && !config.contains("SchematicRadius")) {
            warnings += "settings.yml: SaveRadius not set (defaults to 50)"
        }

        // SpawnPoint
        if (!config.contains("Settings.SpawnPoint")) {
            warnings += "settings.yml: Settings.SpawnPoint not defined"
        }

        // Border
        if (config.getBoolean("Border.Enabled", false)) {
            val size = config.getDouble("Border.Size", 0.0)
            if (size <= 0) warnings += "settings.yml: Border is enabled but Border.Size is 0 or negative"
        }
    }

    private fun validateSchematicFile(
        mapName: String,
        warnings: MutableList<String>,
        info: MutableList<String>
    ) {
        val schemFile = mapRepository.getMapSchematicFile(mapName)
        if (!schemFile.exists()) {
            warnings += "region.schem not found – map will start as empty void world"
            return
        }

        val sizeBytes = schemFile.length()
        info += "region.schem size: $sizeBytes bytes"

        if (sizeBytes < 100) {
            warnings += "region.schem is suspiciously small ($sizeBytes bytes) – may be corrupt or empty"
        }
    }

    private fun tryLoadYaml(file: File, errors: MutableList<String>): YamlConfiguration? {
        return try {
            YamlConfiguration.loadConfiguration(file)
        } catch (e: Exception) {
            errors += "YAML parse error in ${file.name}: ${e.message}"
            null
        }
    }

    // -------------------------------------------------------------------------
    // Report
    // -------------------------------------------------------------------------

    /**
     * Result of a validation run.
     *
     * @param mapName The map/config name that was validated.
     * @param errors  Hard problems that prevent the map from loading correctly.
     * @param warnings Soft hints about missing optional data.
     * @param info     Informational notes (e.g. file sizes).
     */
    data class ValidationReport(
        val mapName: String,
        val errors: List<String>,
        val warnings: List<String>,
        val info: List<String>
    ) {
        /** `true` when there are no hard errors. */
        val isValid: Boolean get() = errors.isEmpty()

        /** Returns a concise summary string for use in chat or logs. */
        fun summary(): String {
            return if (isValid) {
                "&a✓ $mapName is valid" + (if (warnings.isNotEmpty()) " (${warnings.size} warning(s))" else "")
            } else {
                "&c✗ $mapName has ${errors.size} error(s)" + (if (warnings.isNotEmpty()) " and ${warnings.size} warning(s)" else "")
            }
        }
    }
}
