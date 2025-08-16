package org.Kloppie74.locoPillars.game.template

import org.bukkit.plugin.java.JavaPlugin
import org.Kloppie74.locoPillars.game.config.GameConfig
import org.Kloppie74.locoPillars.game.arena.ArenaTemplate
import java.io.File

class TemplateManager(private val plugin: JavaPlugin) {

    fun createDefaultTemplates() {
        plugin.logger.info("[TemplateManager] Creating default template structure...")

        val gamesFolder = File(plugin.dataFolder, "Games")
        val schemsFolder = File(plugin.dataFolder, "Schems")

        // Create folders
        if (!gamesFolder.exists()) {
            gamesFolder.mkdirs()
            plugin.logger.info("[TemplateManager] Created Games folder")
        }

        if (!schemsFolder.exists()) {
            schemsFolder.mkdirs()
            plugin.logger.info("[TemplateManager] Created Schems folder")
        }

        // Create example template files if they don't exist
        createExampleTemplate("example-solo", gamesFolder, schemsFolder)
        createExampleTemplate("example-team", gamesFolder, schemsFolder)

        plugin.logger.info("[TemplateManager] Default template structure created!")
        plugin.logger.info("[TemplateManager] Please place your .schem files in: ${schemsFolder.absolutePath}")
        plugin.logger.info("[TemplateManager] Config files (.yml) go in: ${gamesFolder.absolutePath}")
    }

    private fun createExampleTemplate(templateName: String, gamesFolder: File, schemsFolder: File) {
        val configFile = File(gamesFolder, "$templateName.yml")
        val readmeFile = File(schemsFolder, "$templateName.schem.readme")

        if (!configFile.exists()) {
            plugin.logger.info("[TemplateManager] Creating example config: $templateName.yml")

            val configContent = when (templateName) {
                "example-solo" -> createSoloExampleConfig()
                "example-team" -> createTeamExampleConfig()
                else -> createBasicExampleConfig(templateName)
            }

            configFile.writeText(configContent)
            plugin.logger.info("[TemplateManager] Created: ${configFile.absolutePath}")
        }

        if (!readmeFile.exists()) {
            plugin.logger.info("[TemplateManager] Creating schematic readme: $templateName.schem.readme")

            val readmeContent = """
# Schematic File Required: $templateName.schem

This file serves as a placeholder to remind you that you need to place a WorldEdit schematic file here.

Required file: $templateName.schem

To create a schematic:
1. Build your arena in a test world
2. Select the area with WorldEdit (//wand)
3. Copy the selection (//copy)
4. Save it as a schematic (//schem save $templateName)
5. Move the .schem file to this folder: ${schemsFolder.absolutePath}

The schematic should contain:
- The complete arena structure
- All necessary blocks and decorations
- Spawn cages/platforms (if needed)
- Any special blocks or features

Make sure the schematic origin point (where you stand when copying) is at the center bottom of your arena!
            """.trimIndent()

            readmeFile.writeText(readmeContent)
            plugin.logger.info("[TemplateManager] Created: ${readmeFile.absolutePath}")
        }
    }

    private fun createSoloExampleConfig(): String {
        return """
Settings:
  DisplayName: "Solo Example Arena"
  LobbyTimer: 15
  CageTimer: 5
  GameTimer: 600
  ItemTimer: 10
  BuildHeight: 80
  BuildRange: 50
  DeathHeight: -10
  Can-Break-Map-Blocks: false
  SpawnPoint:
    X: 0
    Y: 65
    Z: 0

SpawnLocations:
  MaximumPlayers: 8
  MinimumPlayers: 4
  MaxTeams: 8
  PlayersPerTeam: 1
  Team1:
    Name: "&c[Red]"
    Color: RED
    Player1:
      X: -10.5
      Y: 45.5
      Z: 0.5
      Yaw: 90.0
      Pitch: 0.0
      Cage-Rotation: 0
  Team2:
    Name: "&9[Blue]"
    Color: BLUE
    Player1:
      X: 10.5
      Y: 45.5
      Z: 0.5
      Yaw: -90.0
      Pitch: 0.0
      Cage-Rotation: 0
  Team3:
    Name: "&a[Green]"
    Color: GREEN
    Player1:
      X: 0.5
      Y: 45.5
      Z: -10.5
      Yaw: 180.0
      Pitch: 0.0
      Cage-Rotation: 0
  Team4:
    Name: "&e[Yellow]"
    Color: YELLOW
    Player1:
      X: 0.5
      Y: 45.5
      Z: 10.5
      Yaw: 0.0
      Pitch: 0.0
      Cage-Rotation: 0
  Team5:
    Name: "&5[Purple]"
    Color: DARK_PURPLE
    Player1:
      X: -7.5
      Y: 45.5
      Z: -7.5
      Yaw: 135.0
      Pitch: 0.0
      Cage-Rotation: 0
  Team6:
    Name: "&6[Orange]"
    Color: GOLD
    Player1:
      X: 7.5
      Y: 45.5
      Z: -7.5
      Yaw: -135.0
      Pitch: 0.0
      Cage-Rotation: 0
  Team7:
    Name: "&b[Cyan]"
    Color: DARK_AQUA
    Player1:
      X: 7.5
      Y: 45.5
      Z: 7.5
      Yaw: -45.0
      Pitch: 0.0
      Cage-Rotation: 0
  Team8:
    Name: "&8[Gray]"
    Color: GRAY
    Player1:
      X: -7.5
      Y: 45.5
      Z: 7.5
      Yaw: 45.0
      Pitch: 0.0
      Cage-Rotation: 0
""".trimIndent()
    }

    private fun createTeamExampleConfig(): String {
        return """
Settings:
  DisplayName: "Team Example Arena"
  LobbyTimer: 20
  CageTimer: 5
  GameTimer: 900
  ItemTimer: 15
  BuildHeight: 100
  BuildRange: 75
  DeathHeight: -15
  Can-Break-Map-Blocks: false
  SpawnPoint:
    X: 0
    Y: 70
    Z: 0

SpawnLocations:
  MaximumPlayers: 16
  MinimumPlayers: 8
  MaxTeams: 4
  PlayersPerTeam: 4
  Team1:
    Name: "&c[Red Team]"
    Color: RED
    Player1:
      X: -15.5
      Y: 45.5
      Z: -15.5
      Yaw: 45.0
      Pitch: 0.0
      Cage-Rotation: 0
    Player2:
      X: -12.5
      Y: 45.5
      Z: -15.5
      Yaw: 45.0
      Pitch: 0.0
      Cage-Rotation: 0
    Player3:
      X: -15.5
      Y: 45.5
      Z: -12.5
      Yaw: 45.0
      Pitch: 0.0
      Cage-Rotation: 0
    Player4:
      X: -12.5
      Y: 45.5
      Z: -12.5
      Yaw: 45.0
      Pitch: 0.0
      Cage-Rotation: 0
  Team2:
    Name: "&9[Blue Team]"
    Color: BLUE
    Player1:
      X: 15.5
      Y: 45.5
      Z: -15.5
      Yaw: 135.0
      Pitch: 0.0
      Cage-Rotation: 0
    Player2:
      X: 12.5
      Y: 45.5
      Z: -15.5
      Yaw: 135.0
      Pitch: 0.0
      Cage-Rotation: 0
    Player3:
      X: 15.5
      Y: 45.5
      Z: -12.5
      Yaw: 135.0
      Pitch: 0.0
      Cage-Rotation: 0
    Player4:
      X: 12.5
      Y: 45.5
      Z: -12.5
      Yaw: 135.0
      Pitch: 0.0
      Cage-Rotation: 0
  Team3:
    Name: "&a[Green Team]"
    Color: GREEN
    Player1:
      X: 15.5
      Y: 45.5
      Z: 15.5
      Yaw: -135.0
      Pitch: 0.0
      Cage-Rotation: 0
    Player2:
      X: 12.5
      Y: 45.5
      Z: 15.5
      Yaw: -135.0
      Pitch: 0.0
      Cage-Rotation: 0
    Player3:
      X: 15.5
      Y: 45.5
      Z: 12.5
      Yaw: -135.0
      Pitch: 0.0
      Cage-Rotation: 0
    Player4:
      X: 12.5
      Y: 45.5
      Z: 12.5
      Yaw: -135.0
      Pitch: 0.0
      Cage-Rotation: 0
  Team4:
    Name: "&e[Yellow Team]"
    Color: YELLOW
    Player1:
      X: -15.5
      Y: 45.5
      Z: 15.5
      Yaw: -45.0
      Pitch: 0.0
      Cage-Rotation: 0
    Player2:
      X: -12.5
      Y: 45.5
      Z: 15.5
      Yaw: -45.0
      Pitch: 0.0
      Cage-Rotation: 0
    Player3:
      X: -15.5
      Y: 45.5
      Z: 12.5
      Yaw: -45.0
      Pitch: 0.0
      Cage-Rotation: 0
    Player4:
      X: -12.5
      Y: 45.5
      Z: 12.5
      Yaw: -45.0
      Pitch: 0.0
      Cage-Rotation: 0
""".trimIndent()
    }

    private fun createBasicExampleConfig(templateName: String): String {
        return """
Settings:
  DisplayName: "$templateName Arena"
  LobbyTimer: 15
  CageTimer: 5
  GameTimer: 600
  ItemTimer: 10
  BuildHeight: 80
  BuildRange: 50
  DeathHeight: -10
  Can-Break-Map-Blocks: false
  SpawnPoint:
    X: 0
    Y: 65
    Z: 0

SpawnLocations:
  MaximumPlayers: 8
  MinimumPlayers: 4
  MaxTeams: 8
  PlayersPerTeam: 1
  Team1:
    Name: "&c[Red]"
    Color: RED
    Player1:
      X: -10.5
      Y: 45.5
      Z: 0.5
      Yaw: 90.0
      Pitch: 0.0
      Cage-Rotation: 0
  Team2:
    Name: "&9[Blue]"
    Color: BLUE
    Player1:
      X: 10.5
      Y: 45.5
      Z: 0.5
      Yaw: -90.0
      Pitch: 0.0
      Cage-Rotation: 0
  Team3:
    Name: "&a[Green]"
    Color: GREEN
    Player1:
      X: 0.5
      Y: 45.5
      Z: -10.5
      Yaw: 180.0
      Pitch: 0.0
      Cage-Rotation: 0
  Team4:
    Name: "&e[Yellow]"
    Color: YELLOW
    Player1:
      X: 0.5
      Y: 45.5
      Z: 10.5
      Yaw: 0.0
      Pitch: 0.0
      Cage-Rotation: 0
""".trimIndent()
    }

    fun validateTemplate(templateName: String): TemplateValidationResult {
        plugin.logger.info("[TemplateManager] Validating template: $templateName")

        val gamesFolder = File(plugin.dataFolder, "Games")
        val schemsFolder = File(plugin.dataFolder, "Schems")
        val configFile = File(gamesFolder, "$templateName.yml")
        val schemFile = File(schemsFolder, "$templateName.schem")

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check if config file exists
        if (!configFile.exists()) {
            errors.add("Config file not found: ${configFile.absolutePath}")
        } else {
            plugin.logger.info("[TemplateManager] ✓ Config file found")
        }

        // Check if schematic file exists
        if (!schemFile.exists()) {
            errors.add("Schematic file not found: ${schemFile.absolutePath}")
        } else {
            plugin.logger.info("[TemplateManager] ✓ Schematic file found")

            // Validate schematic file
            if (!org.Kloppie74.locoPillars.utils.SchematicHelper.validateSchematicFile(plugin, schemFile)) {
                errors.add("Invalid schematic file: ${schemFile.name}")
            } else {
                plugin.logger.info("[TemplateManager] ✓ Schematic file is valid")
            }
        }

        // Try to load and validate config
        if (configFile.exists()) {
            try {
                val config = GameConfig.loadSingle(plugin, configFile)
                if (config != null) {
                    plugin.logger.info("[TemplateManager] ✓ Config loaded successfully")

                    // Validate config values
                    if (config.getMaxPlayers() <= 0) {
                        errors.add("MaximumPlayers must be greater than 0")
                    }
                    if (config.getMinPlayers() <= 0) {
                        errors.add("MinimumPlayers must be greater than 0")
                    }
                    if (config.getMinPlayers() > config.getMaxPlayers()) {
                        errors.add("MinimumPlayers cannot be greater than MaximumPlayers")
                    }
                    if (config.teams.isEmpty()) {
                        errors.add("No teams defined in config")
                    } else {
                        plugin.logger.info("[TemplateManager] ✓ Found ${config.teams.size} teams")

                        // Validate each team has spawn points
                        config.teams.forEachIndexed { index, team ->
                            if (team.spawns.isEmpty()) {
                                errors.add("Team ${index + 1} (${team.name}) has no spawn points")
                            }
                        }
                    }
                } else {
                    errors.add("Failed to load config file")
                }
            } catch (e: Exception) {
                errors.add("Error loading config: ${e.message}")
                plugin.logger.warning("[TemplateManager] Config validation error: ${e.message}")
            }
        }

        val isValid = errors.isEmpty()

        plugin.logger.info("[TemplateManager] Template validation result for $templateName:")
        plugin.logger.info("[TemplateManager]   Valid: $isValid")
        plugin.logger.info("[TemplateManager]   Errors: ${errors.size}")
        plugin.logger.info("[TemplateManager]   Warnings: ${warnings.size}")

        return TemplateValidationResult(isValid, errors, warnings)
    }

    fun listAvailableTemplates(): List<String> {
        val gamesFolder = File(plugin.dataFolder, "Games")
        if (!gamesFolder.exists()) return emptyList()

        val configFiles = gamesFolder.listFiles { f -> f.extension == "yml" } ?: emptyArray()
        return configFiles.map { it.nameWithoutExtension }
    }

    fun getTemplateInfo(templateName: String): TemplateInfo? {
        val gamesFolder = File(plugin.dataFolder, "Games")
        val schemsFolder = File(plugin.dataFolder, "Schems")
        val configFile = File(gamesFolder, "$templateName.yml")
        val schemFile = File(schemsFolder, "$templateName.schem")

        if (!configFile.exists()) return null

        return try {
            val config = GameConfig.loadSingle(plugin, configFile)
            if (config != null) {
                TemplateInfo(
                    name = templateName,
                    displayName = config.getDisplayName(),
                    maxPlayers = config.getMaxPlayers(),
                    minPlayers = config.getMinPlayers(),
                    teams = config.teams.size,
                    hasSchematic = schemFile.exists(),
                    configPath = configFile.absolutePath,
                    schemPath = schemFile.absolutePath
                )
            } else null
        } catch (e: Exception) {
            plugin.logger.warning("[TemplateManager] Error loading template info for $templateName: ${e.message}")
            null
        }
    }
}

data class TemplateValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
)

data class TemplateInfo(
    val name: String,
    val displayName: String,
    val maxPlayers: Int,
    val minPlayers: Int,
    val teams: Int,
    val hasSchematic: Boolean,
    val configPath: String,
    val schemPath: String
)