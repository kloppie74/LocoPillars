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
        val centerX = config.getDouble("Settings.SpawnPoint.X", 0.0)
        val centerZ = config.getDouble("Settings.SpawnPoint.Z", 0.0)
        val buildRange = config.getInt("Settings.BuildRange", 50)
        val buildHeight = config.getInt("Settings.BuildHeight", 80)
        val saveRadius = config.getInt("SaveRadius", config.getInt("SchematicRadius", 50))

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
                    val playerKeys = teamSection?.getKeys(false)?.filter { it.startsWith("Player") } ?: emptySet()
                    totalSpawns += playerKeys.size
                    if (playerKeys.isEmpty()) {
                        warnings += "Team '$teamKey' has no Player spawn slots"
                    }
                    playerKeys.forEach { playerKey ->
                        val ps = teamSection?.getConfigurationSection(playerKey) ?: return@forEach
                        val sx = ps.getDouble("X")
                        val sz = ps.getDouble("Z")
                        val dxz = Math.max(Math.abs(sx - centerX), Math.abs(sz - centerZ))
                        if (dxz > buildRange) {
                            warnings += "Spawn $teamKey/$playerKey is ${dxz.toInt()} blocks from center — exceeds BuildRange ($buildRange)"
                        }
                        if (dxz > saveRadius) {
                            errors += "Spawn $teamKey/$playerKey is ${dxz.toInt()} blocks from center — exceeds SaveRadius ($saveRadius), may be outside saved schematic!"
                        }
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

        // BuildRange vs SaveRadius
        if (buildRange > saveRadius) {
            warnings += "BuildRange ($buildRange) > SaveRadius ($saveRadius) — players can build outside the saved schematic area"
        }

        // BuildHeight sanity
        if (buildHeight < 10) {
            warnings += "BuildHeight ($buildHeight) is very low — players may not be able to build at all"
        }

        // Lobby settings
        val lobbyEnabled = config.getBoolean("Settings.LobbyEnabled", true)
        if (lobbyEnabled) {
            val hasSpawn = config.contains("Settings.SpawnPoint.X")
            val hasArea  = config.contains("Settings.Spawn-Area.Loc1") && config.contains("Settings.Spawn-Area.Loc2")
            if (!hasSpawn) errors += "LobbyEnabled=true but Settings.SpawnPoint is missing — players have no lobby spawn!"
            if (!hasArea)  warnings += "LobbyEnabled=true but Settings.Spawn-Area is not defined — no spawn-area restriction"
            if (hasArea) {
                val lx1 = config.getDouble("Settings.Spawn-Area.Loc1.X")
                val lz1 = config.getDouble("Settings.Spawn-Area.Loc1.Z")
                val lx2 = config.getDouble("Settings.Spawn-Area.Loc2.X")
                val lz2 = config.getDouble("Settings.Spawn-Area.Loc2.Z")
                val areaMaxXZ = maxOf(Math.abs(lx1 - centerX), Math.abs(lz1 - centerZ),
                                      Math.abs(lx2 - centerX), Math.abs(lz2 - centerZ))
                if (areaMaxXZ > buildRange) {
                    warnings += "Spawn-Area corner is ${areaMaxXZ.toInt()} blocks from center — outside BuildRange ($buildRange)"
                }
            }
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
