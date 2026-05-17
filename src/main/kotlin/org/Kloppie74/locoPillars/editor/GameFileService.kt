package org.Kloppie74.locoPillars.editor

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import org.Kloppie74.locoPillars.editor.model.MapFileInfo
import org.Kloppie74.locoPillars.game.map.MapRepository
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import java.io.File

/**
 * GameFileService
 *
 * Service layer for all file I/O operations related to game maps.
 *
 * NEW:
 * - [runSetupForMap] — mirrors a single map's assets (settings.yml → Data/Games,
 *   region.schem → Data/Schems).  Called automatically after every editor save
 *   so the game system picks up the updated files without a manual `/editor setup`.
 * - [runQuickSetup] — unchanged, runs setup for **all** maps at once; also
 *   called when the admin runs `/editor setup` without arguments.
 */
class GameFileService(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val mapRepository: MapRepository
) {
    private val mapsFolder: File get() = File(plugin.dataFolder, "maps")
    private val dataFolder: File get() = File(plugin.dataFolder, "Data")
    private val gamesFolder: File get() = File(dataFolder, "Games")
    private val schemsFolder: File get() = File(plugin.dataFolder, "Data/Schems")

    // -------------------------------------------------------------------------
    // Map file inspection
    // -------------------------------------------------------------------------

    fun getMapFileInfo(mapName: String): MapFileInfo {
        val settingsFile  = mapRepository.getMapSettingsFile(mapName)
        val schematicFile = mapRepository.getMapSchematicFile(mapName)
        return MapFileInfo(
            mapName            = mapName,
            settingsFile       = settingsFile,
            schematicFile      = schematicFile,
            settingsExists     = settingsFile.exists() && settingsFile.isFile,
            schematicExists    = schematicFile.exists() && schematicFile.isFile,
            settingsSizeBytes  = if (settingsFile.exists()) settingsFile.length() else 0L,
            schematicSizeBytes = if (schematicFile.exists()) schematicFile.length() else 0L
        )
    }

    fun getAllMapFileInfos(): List<MapFileInfo> =
        mapRepository.getAllMapNames().map { getMapFileInfo(it) }

    // -------------------------------------------------------------------------
    // Game-asset (.yml) helpers
    // -------------------------------------------------------------------------

    fun listGameConfigFiles(): List<File> {
        if (!gamesFolder.exists()) return emptyList()
        return gamesFolder.listFiles { f -> f.isFile && f.extension == "yml" }?.toList()
            ?: emptyList()
    }

    fun listLegacySchematicFiles(): List<File> {
        if (!schemsFolder.exists()) return emptyList()
        return schemsFolder.listFiles { f -> f.isFile && f.extension == "schem" }?.toList()
            ?: emptyList()
    }

    fun loadGameConfig(configName: String): YamlConfiguration? {
        val file = File(gamesFolder, "$configName.yml")
        if (!file.exists()) {
            plugin.logger.warning("[GameFileService] Game config not found: $configName")
            return null
        }
        return try {
            YamlConfiguration.loadConfiguration(file)
        } catch (e: Exception) {
            plugin.logger.severe("[GameFileService] Failed to load game config '$configName': ${e.message}")
            null
        }
    }

    fun saveGameConfig(configName: String, config: YamlConfiguration): Boolean {
        gamesFolder.mkdirs()
        val file = File(gamesFolder, "$configName.yml")
        return try {
            config.save(file)
            debugLog("Saved game config: $configName")
            true
        } catch (e: Exception) {
            plugin.logger.severe("[GameFileService] Failed to save game config '$configName': ${e.message}")
            false
        }
    }

    // -------------------------------------------------------------------------
    // Template & copy utilities
    // -------------------------------------------------------------------------

    fun copyGameConfigTemplate(sourceFile: File, destinationName: String): Boolean {
        val dest = File(gamesFolder, "$destinationName.yml")
        return try {
            sourceFile.copyTo(dest, overwrite = false)
            debugLog("Copied template ${sourceFile.name} → $destinationName.yml")
            true
        } catch (e: Exception) {
            plugin.logger.warning("[GameFileService] Copy failed for '$destinationName': ${e.message}")
            false
        }
    }

    // -------------------------------------------------------------------------
    // Folder readiness
    // -------------------------------------------------------------------------

    fun ensureDataFolders() {
        if (!gamesFolder.exists()) {
            gamesFolder.mkdirs()
            plugin.logger.info("[GameFileService] Created Data/Games folder")
        }
        if (!schemsFolder.exists()) {
            schemsFolder.mkdirs()
            plugin.logger.info("[GameFileService] Created Data/Schems folder")
        }
    }

    // -------------------------------------------------------------------------
    // NEW: Per-map setup (called automatically after /editor save)
    // -------------------------------------------------------------------------

    /**
     * Mirrors a single map's assets into Data/Games and Data/Schems.
     *
     * - `maps/<mapName>/settings.yml` → `Data/Games/<mapName>.yml`  (always overwrite after save)
     * - `maps/<mapName>/region.schem` → `Data/Schems/<mapName>.schem` (always overwrite after save)
     *
     * @return A [SingleMapSetupResult] describing what happened.
     */
    fun runSetupForMap(mapName: String): SingleMapSetupResult {
        ensureDataFolders()

        val mapFolder    = File(plugin.dataFolder, "maps/$mapName")
        val settingsSrc  = File(mapFolder, "settings.yml")
        val schemSrc     = File(mapFolder, "region.schem")
        val settingsDest = File(gamesFolder, "$mapName.yml")
        val schemDest    = File(schemsFolder, "$mapName.schem")
        val warnings     = mutableListOf<String>()
        var settingsCopied = false
        var schemCopied    = false

        if (settingsSrc.exists()) {
            try {
                settingsSrc.copyTo(settingsDest, overwrite = true)
                settingsCopied = true
                debugLog("Setup: copied settings.yml → Data/Games/$mapName.yml")
            } catch (e: Exception) {
                warnings += "Could not copy settings.yml to Data/Games: ${e.message}"
            }
        } else {
            warnings += "maps/$mapName/settings.yml not found — skipped Data/Games copy"
        }

        if (schemSrc.exists()) {
            try {
                schemSrc.copyTo(schemDest, overwrite = true)
                schemCopied = true
                debugLog("Setup: copied region.schem → Data/Schems/$mapName.schem")
            } catch (e: Exception) {
                warnings += "Could not copy region.schem to Data/Schems: ${e.message}"
            }
        } else {
            warnings += "maps/$mapName/region.schem not found — Data/Schems copy skipped"
        }

        return SingleMapSetupResult(
            mapName         = mapName,
            settingsCopied  = settingsCopied,
            schemCopied     = schemCopied,
            warnings        = warnings
        )
    }

    // -------------------------------------------------------------------------
    // Full setup (all maps)
    // -------------------------------------------------------------------------

    fun runQuickSetup(): QuickSetupResult {
        var foldersCreated = 0
        var bundledConfigsCopied = 0
        var legacyConfigsMoved = 0
        var legacySchemsMoved = 0
        var mapTemplatesMirrored = 0
        val warnings = mutableListOf<String>()

        if (!dataFolder.exists()) { dataFolder.mkdirs(); foldersCreated++ }
        if (!gamesFolder.exists()) { gamesFolder.mkdirs(); foldersCreated++ }
        if (!schemsFolder.exists()) { schemsFolder.mkdirs(); foldersCreated++ }
        ensureSetupGuideFiles()

        //bundledConfigsCopied += copyBundledGameConfigs(warnings)

        val legacyGames = File(plugin.dataFolder, "Games")
        if (legacyGames.exists()) {
            legacyGames.listFiles { f -> f.isFile && f.extension == "yml" }?.forEach { src ->
                val dest = File(gamesFolder, src.name)
                if (!dest.exists()) { src.copyTo(dest, overwrite = false); legacyConfigsMoved++ }
            }
        }

        val legacySchems = File(plugin.dataFolder, "Schems")
        if (legacySchems.exists()) {
            legacySchems.listFiles { f -> f.isFile && f.extension == "schem" }?.forEach { src ->
                val dest = File(schemsFolder, src.name)
                if (!dest.exists()) { src.copyTo(dest, overwrite = false); legacySchemsMoved++ }
            }
            legacySchems.listFiles { f -> f.isDirectory }?.forEach { folder ->
                val destFolder = File(schemsFolder, folder.name)
                destFolder.mkdirs()
                folder.listFiles { f -> f.isFile && f.extension == "schem" }?.forEach { src ->
                    val dest = File(destFolder, src.name)
                    if (!dest.exists()) { src.copyTo(dest, overwrite = false); legacySchemsMoved++ }
                }
            }
        }

        mapTemplatesMirrored += mirrorMapAssetsToArenaTemplates(warnings)

        if (listGameConfigFiles().isEmpty()) {
            warnings += "No Data/Games configs found after setup. Create one with /editor create <name>."
        }
        if (listLegacySchematicFiles().isEmpty() && (schemsFolder.listFiles()?.isEmpty() != false)) {
            warnings += "No Data/Schems schematics found; arena preloading still needs schematic files."
        }

        val readiness = analyzeTemplateReadiness()

        return QuickSetupResult(
            dataGamesPath          = gamesFolder.absolutePath,
            dataSchemsPath         = schemsFolder.absolutePath,
            foldersCreated         = foldersCreated,
            bundledConfigsCopied   = bundledConfigsCopied,
            legacyConfigsMoved     = legacyConfigsMoved,
            legacySchemsMoved      = legacySchemsMoved,
            mapTemplatesMirrored   = mapTemplatesMirrored,
            warnings               = warnings,
            readiness              = readiness
        )
    }

    private fun copyBundledGameConfigs(warnings: MutableList<String>): Int {
        val bundled = listOf("Games/solo-garden-8p.yml", "Games/team-garden-4p.yml")
        var copied = 0
        bundled.forEach { resourcePath ->
            val fileName = resourcePath.substringAfterLast("/")
            val dest = File(gamesFolder, fileName)
            if (dest.exists()) return@forEach
            val stream = plugin.getResource(resourcePath)
            if (stream == null) { warnings += "Bundled resource missing: $resourcePath"; return@forEach }
            stream.use { input -> dest.outputStream().use { out -> input.copyTo(out) } }
            copied++
        }
        return copied
    }

    private fun mirrorMapAssetsToArenaTemplates(warnings: MutableList<String>): Int {
        if (!mapsFolder.exists()) return 0
        var mirrored = 0
        mapsFolder.listFiles { file -> file.isDirectory }?.forEach { mapFolder ->
            val mapName = mapFolder.name
            val settings = File(mapFolder, "settings.yml")
            if (settings.exists()) {
                val targetConfig = File(gamesFolder, "$mapName.yml")
                if (!targetConfig.exists()) { settings.copyTo(targetConfig, overwrite = false); mirrored++ }
            }
            val region = File(mapFolder, "region.schem")
            if (region.exists()) {
                val targetSchem = File(schemsFolder, "$mapName.schem")
                if (!targetSchem.exists()) { region.copyTo(targetSchem, overwrite = false); mirrored++ }
            } else if (settings.exists()) {
                warnings += "Map '$mapName' has settings.yml but no region.schem."
            }
        }
        return mirrored
    }

    fun analyzeTemplateReadiness(): TemplateReadinessReport {
        val entries  = mutableListOf<TemplateReadinessEntry>()
        val warnings = mutableListOf<String>()
        val configs  = listGameConfigFiles().sortedBy { it.nameWithoutExtension.lowercase() }

        configs.forEach { config ->
            val name         = config.nameWithoutExtension
            val singleSchem  = File(schemsFolder, "$name.schem")
            val folder       = File(schemsFolder, name)
            val folderSchems = if (folder.exists() && folder.isDirectory)
                folder.listFiles { file -> file.isFile && file.extension == "schem" }?.size ?: 0
            else 0
            val mapFallback = File(mapsFolder, "$name/region.schem")
            val ready = singleSchem.exists() || folderSchems > 0 || mapFallback.exists()
            val notes = mutableListOf<String>()
            if (singleSchem.exists()) notes += "Found Data/Schems/$name.schem"
            if (folderSchems > 0)     notes += "Found $folderSchems schematic(s) in Data/Schems/$name/"
            if (mapFallback.exists()) notes += "Found fallback maps/$name/region.schem"
            if (!ready)               notes += "Missing schematic for '$name' in Data/Schems or maps fallback."
            entries += TemplateReadinessEntry(name, ready, singleSchem.exists(), folderSchems, mapFallback.exists(), notes)
        }

        if (configs.isEmpty()) warnings += "Data/Games contains no .yml files."
        entries.filter { !it.ready }.forEach { warnings += "Template '${it.configName}' is not preload-ready (missing matching schematic)." }

        return TemplateReadinessReport(gamesFolder.absolutePath, schemsFolder.absolutePath,
            configs.size, entries.count { it.ready }, entries, warnings)
    }

    private fun ensureSetupGuideFiles() {
        val gamesGuide = File(gamesFolder, "README_SETUP.txt")
        if (!gamesGuide.exists()) {
            gamesGuide.writeText("""
                LocoPillars Data/Games Setup
                Put one .yml game config per arena type in this folder.
                The file name (without .yml) must match a schematic in:
                - Data/Schems/<name>.schem  OR  Data/Schems/<name>/*.schem
            """.trimIndent())
        }
        val schemsGuide = File(schemsFolder, "README_SETUP.txt")
        if (!schemsGuide.exists()) {
            schemsGuide.writeText("""
                LocoPillars Data/Schems Setup
                Store schematic files here for every config in Data/Games.
                Valid structure:
                - Data/Schems/<name>.schem  OR  Data/Schems/<name>/*.schem
                <name> must match Data/Games/<name>.yml exactly.
            """.trimIndent())
        }
    }

    private fun debugLog(message: String) {
        if (configManager.debugMode) plugin.logger.info("[GameFileService] $message")
    }
}

// -------------------------------------------------------------------------
// Result types
// -------------------------------------------------------------------------

/**
 * Result of a single-map setup run (auto-triggered after /editor save).
 */
data class SingleMapSetupResult(
    val mapName: String,
    val settingsCopied: Boolean,
    val schemCopied: Boolean,
    val warnings: List<String>
) {
    val fullyReady: Boolean get() = settingsCopied && schemCopied

    fun summary(): String = buildString {
        append("&6Setup for &e$mapName&6: ")
        append(if (settingsCopied) "&asettings.yml ✓ " else "&csettings.yml ✗ ")
        append(if (schemCopied)    "&aregion.schem ✓"  else "&cregion.schem ✗")
    }
}

data class QuickSetupResult(
    val dataGamesPath: String,
    val dataSchemsPath: String,
    val foldersCreated: Int,
    val bundledConfigsCopied: Int,
    val legacyConfigsMoved: Int,
    val legacySchemsMoved: Int,
    val mapTemplatesMirrored: Int,
    val warnings: List<String>,
    val readiness: TemplateReadinessReport
)

data class TemplateReadinessReport(
    val dataGamesPath: String,
    val dataSchemsPath: String,
    val configCount: Int,
    val readyCount: Int,
    val entries: List<TemplateReadinessEntry>,
    val warnings: List<String>
)

data class TemplateReadinessEntry(
    val configName: String,
    val ready: Boolean,
    val hasSingleSchem: Boolean,
    val folderSchematics: Int,
    val hasMapFallback: Boolean,
    val notes: List<String>
)