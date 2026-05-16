package org.Kloppie74.locoPillars.commands.editor

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.editor.AdminEditorManager
import org.Kloppie74.locoPillars.editor.EditorFileValidator
import org.Kloppie74.locoPillars.editor.EditorSettingsGui
import org.Kloppie74.locoPillars.editor.GameFileService
import org.Kloppie74.locoPillars.editor.SpawnPointEditor
import org.Kloppie74.locoPillars.game.map.EditorSessionManager
import org.Kloppie74.locoPillars.game.map.MapRepository
import org.Kloppie74.locoPillars.utils.MSG

/**
 * AdminEditorCommand — `/editor`
 *
 * Single entry-point for the Admin-Editor system. Replaces the old
 * `/pillars admin map` flow entirely.
 *
 * ## Sub-commands
 * ```
 * /editor enable [map]               Enable edit mode (optional map focus)
 * /editor disable                    Disable edit mode and restore hotbar
 * /editor toggle [map]               Toggle edit mode on/off
 * /editor map <name|clear>           Select / clear the active map
 * /editor info                       Show current session info
 *
 * — Map management (world-editing session) —
 * /editor create <name>              Create a new map from the default template
 * /editor edit <name>                Open a full schematic-edit session
 * /editor delete <name>              Delete a map and all its files
 * /editor list                       List all maps with validation status
 * /editor save                       Save the current schematic and close session
 * /editor cancel                     Cancel the current session (no save)
 * /editor resize <radius>            Adjust schematic save radius
 * /editor settings                   Open the save-radius GUI
 *
 * — Validation & files —
 * /editor validate [map]             Validate one or all maps
 * /editor files [map]                Show file status for one or all maps
 *
 * — Spawn points —
 * /editor spawn list                 List spawn points for the active map
 * /editor spawn set <team> <slot>    Set spawn at your current location
 * /editor spawn remove <team> <slot> Remove a spawn point
 *
 * /editor help                       Show this help
 * ```
 *
 * ## Permissions
 * All sub-commands require `locopillars.admin`.
 *
 * @param plugin Plugin instance for task scheduling.
 * @param adminEditorManager Central registry for edit-mode state.
 * @param mapRepository Source of map metadata and file paths.
 * @param editorSessionManager Creates / manages world-editing sessions.
 * @param editorSettingsGui GUI for adjusting save parameters.
 * @param spawnPointEditor Handles spawn point CRUD.
 * @param fileValidator Validates map files, reports errors.
 * @param gameFileService Reads raw file metadata.
 */
class AdminEditorCommand(
    private val plugin: LocoPillars,
    private val adminEditorManager: AdminEditorManager,
    private val mapRepository: MapRepository,
    private val editorSessionManager: EditorSessionManager,
    private val editorSettingsGui: EditorSettingsGui,
    private val spawnPointEditor: SpawnPointEditor,
    private val fileValidator: EditorFileValidator,
    private val gameFileService: GameFileService
) : CommandExecutor, TabCompleter {

    private val permission = "locopillars.admin"

    // -------------------------------------------------------------------------
    // CommandExecutor
    // -------------------------------------------------------------------------

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(MSG.chatColors("&cThis command is player-only!"))
            return true
        }

        if (!sender.hasPermission(permission)) {
            sender.sendMessage(MSG.chatColors("&cYou do not have permission to use this command!"))
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "enable"   -> handleEnable(sender, args)
            "disable"  -> handleDisable(sender)
            "toggle"   -> handleToggle(sender, args)
            "map"      -> handleMap(sender, args)
            "info"     -> handleInfo(sender)
            "hotbar"   -> handleHotbar(sender, args)
            "holograms" -> handleHolograms(sender, args)
            "setup"    -> handleSetup(sender)
            // Map management (replaces /pillars admin map ...)
            "create"   -> handleCreate(sender, args)
            "edit"     -> handleEdit(sender, args)
            "delete"   -> handleDelete(sender, args)
            "list"     -> handleList(sender)
            "save"     -> handleSave(sender)
            "cancel"   -> handleCancel(sender)
            "resize"   -> handleResize(sender, args)
            "settings" -> handleSettings(sender)
            // Validation & files
            "validate" -> handleValidate(sender, args)
            "files"    -> handleFiles(sender, args)
            // Spawn points
            "spawn"    -> handleSpawn(sender, args)
            "help", null -> showHelp(sender)
            else -> {
                sender.sendMessage(MSG.chatColors("&cUnknown sub-command: ${args[0]}"))
                showHelp(sender)
            }
        }

        return true
    }

    // -------------------------------------------------------------------------
    // Sub-command handlers
    // -------------------------------------------------------------------------

    private fun handleEnable(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1)

        if (mapName != null && !mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
            player.sendMessage(MSG.chatColors("&7Use &e/editor validate &7or &e/editor list"))
            return
        }

        adminEditorManager.enable(player, mapName)
    }

    private fun handleDisable(player: Player) {
        if (editorSessionManager.hasSession(player)) {
            editorSessionManager.cancelSession(player)
        }
        adminEditorManager.disable(player)
    }

    private fun handleToggle(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1)

        if (mapName != null && !mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
            return
        }

        val wasActive = adminEditorManager.isActive(player)
        if (wasActive && editorSessionManager.hasSession(player)) {
            editorSessionManager.cancelSession(player)
        }
        val nowActive = adminEditorManager.toggle(player)
        if (nowActive && mapName != null) {
            adminEditorManager.setActiveMap(player, mapName)
        }
    }

    private fun handleMap(player: Player, args: Array<out String>) {
        val subArg = args.getOrNull(1)

        if (subArg == null) {
            val current = adminEditorManager.getState(player)?.activeMapName
            player.sendMessage(
                if (current != null) MSG.chatColors("&eActive map: &f$current")
                else MSG.chatColors("&7No map selected. Use &e/editor map <name>")
            )
            return
        }

        if (subArg.equals("clear", ignoreCase = true)) {
            adminEditorManager.setActiveMap(player, null)
            player.sendMessage(MSG.chatColors("&7Active map selection cleared."))
            return
        }

        val mapName = subArg
        if (!mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
            suggestMaps(player)
            return
        }

        adminEditorManager.setActiveMap(player, mapName)
        player.sendMessage(MSG.chatColors("&aActive map set to: &f$mapName"))
        player.sendMessage(MSG.chatColors("&7Use &e/editor edit &7to start editing this map."))
    }

    private fun handleInfo(player: Player) {
        val state = adminEditorManager.getState(player)
        if (state == null) {
            player.sendMessage(MSG.chatColors("&cEdit mode is not active."))
            return
        }

        player.sendMessage(MSG.chatColors("&6=== Edit Mode Info ==="))
        player.sendMessage(MSG.chatColors("&eDuration: &f${state.activeDurationSeconds()}s"))
        player.sendMessage(MSG.chatColors("&eActive map: &f${state.activeMapName ?: "&7(none)"}"))

        val session = editorSessionManager.getSession(player)
        if (session != null) {
            player.sendMessage(MSG.chatColors("&eEditor session: &aactive &7(world: ${session.tempWorld.name})"))
            player.sendMessage(MSG.chatColors("&eSave radius: &f${session.getSaveRadius()}"))
            player.sendMessage(MSG.chatColors("&eBorder size: &f${session.getBorderSize().toInt()}"))
        } else {
            player.sendMessage(MSG.chatColors("&eEditor session: &7none"))
        }
    }

    // -------------------------------------------------------------------------
    // Map management sub-commands  (replaces /pillars admin map ...)
    // -------------------------------------------------------------------------

    private fun handleCreate(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1) ?: run {
            player.sendMessage(MSG.chatColors("&cUsage: /editor create <name>"))
            return
        }

        if (!isValidMapName(mapName)) {
            player.sendMessage(MSG.chatColors("&cInvalid map name! Only letters, numbers, dashes and underscores."))
            return
        }

        if (mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' already exists!"))
            return
        }

        player.sendMessage(MSG.chatColors("&eCreating map: &f$mapName&e…"))

        if (mapRepository.createMap(mapName)) {
            player.sendMessage(MSG.chatColors("&a✓ Map '$mapName' created successfully!"))
            player.sendMessage(MSG.chatColors("&7Use &e/editor edit $mapName &7to start editing."))
        } else {
            player.sendMessage(MSG.chatColors("&c✗ Failed to create map '$mapName'. Check console."))
        }
    }

    private fun handleEdit(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1) ?: adminEditorManager.getState(player)?.activeMapName ?: run {
            player.sendMessage(MSG.chatColors("&cUsage: /editor edit <name>"))
            player.sendMessage(MSG.chatColors("&7Tip: first select a map with &e/editor map <name>&7, then use &e/editor edit"))
            val maps = mapRepository.getAllMapNames()
            if (maps.isNotEmpty()) player.sendMessage(MSG.chatColors("&7Available maps: &e${maps.joinToString(", ")}"))
            return
        }

        if (!mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
            suggestMaps(player)
            return
        }

        if (!adminEditorManager.isActive(player)) {
            adminEditorManager.enable(player, mapName)
            player.sendMessage(MSG.chatColors("&7Edit mode was auto-enabled for cleaner workflow."))
        } else {
            adminEditorManager.setActiveMap(player, mapName)
        }

        editorSessionManager.startSession(player, mapName)
    }

    private fun handleDelete(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1) ?: run {
            player.sendMessage(MSG.chatColors("&cUsage: /editor delete <name>"))
            return
        }

        if (!mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
            return
        }

        player.sendMessage(MSG.chatColors("&cDeleting map: &f$mapName&c…"))

        if (mapRepository.deleteMap(mapName)) {
            player.sendMessage(MSG.chatColors("&c✓ Map '$mapName' has been deleted."))
            if (adminEditorManager.getState(player)?.activeMapName == mapName) {
                adminEditorManager.setActiveMap(player, null)
            }
        } else {
            player.sendMessage(MSG.chatColors("&c✗ Failed to delete map '$mapName'. Check console."))
        }
    }

    private fun handleList(player: Player) {
        val maps = mapRepository.getAllMapNames()

        if (maps.isEmpty()) {
            player.sendMessage(MSG.chatColors("&eNo maps found!"))
            player.sendMessage(MSG.chatColors("&7Use &e/editor create <name> &7to create a map."))
            return
        }

        player.sendMessage(MSG.chatColors("&6=== Available Maps (${maps.size}) ==="))
        maps.forEach { mapName ->
            val validation = mapRepository.validateMap(mapName)
            val status     = if (validation.isValid) "&a✓" else "&c✗"
            val radius     = mapRepository.getSaveRadius(mapName)
            player.sendMessage(MSG.chatColors("$status &e$mapName &7(radius: $radius)"))
            if (!validation.isValid) {
                validation.errors.forEach { player.sendMessage(MSG.chatColors("    &c- $it")) }
            }
            validation.warnings.forEach { player.sendMessage(MSG.chatColors("    &e⚠ $it")) }
        }
        player.sendMessage(MSG.chatColors("&7Use &e/editor map <name> &7then &e/editor edit &7to edit a map."))
    }

    private fun handleSave(player: Player) {
        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            player.sendMessage(MSG.chatColors("&7Use &e/editor edit &7to start one."))
            return
        }
        editorSessionManager.saveAndEndSession(player)
    }

    private fun handleCancel(player: Player) {
        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            return
        }
        editorSessionManager.cancelSession(player)
    }

    private fun handleResize(player: Player, args: Array<out String>) {
        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            player.sendMessage(MSG.chatColors("&7Use &e/editor edit &7to start one."))
            return
        }

        val radius = args.getOrNull(1)?.toIntOrNull() ?: run {
            val current = editorSessionManager.getSession(player)?.getSaveRadius() ?: 50
            player.sendMessage(MSG.chatColors("&cUsage: /editor resize <radius>"))
            player.sendMessage(MSG.chatColors("&7Current radius: &e$current &7| Recommended: 20–200"))
            return
        }

        editorSessionManager.resizeSession(player, radius)
    }

    private fun handleSettings(player: Player) {
        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            player.sendMessage(MSG.chatColors("&7Use &e/editor edit &7to start one."))
            return
        }
        Bukkit.getScheduler().runTask(plugin, Runnable { editorSettingsGui.open(player) })
    }

    private fun handleHotbar(player: Player, args: Array<out String>) {
        if (!adminEditorManager.isActive(player)) {
            player.sendMessage(MSG.chatColors("&cEdit mode is not active. Use &e/editor enable &cfirst."))
            return
        }

        when (args.getOrNull(1)?.lowercase()) {
            null, "status" -> {
                player.sendMessage(MSG.chatColors("&eEditor hotbar: &f${if (adminEditorManager.isHotbarEnabled(player)) "enabled" else "disabled"}"))
                player.sendMessage(MSG.chatColors("&7Use: &e/editor hotbar <on|off|toggle|status>"))
            }
            "on", "enable" -> adminEditorManager.setHotbarEnabled(player, true)
            "off", "disable" -> adminEditorManager.setHotbarEnabled(player, false)
            "toggle" -> adminEditorManager.toggleHotbar(player)
            else -> player.sendMessage(MSG.chatColors("&cUsage: /editor hotbar <on|off|toggle|status>"))
        }
    }

    private fun handleHolograms(player: Player, args: Array<out String>) {
        val session = editorSessionManager.getSession(player) ?: run {
            player.sendMessage(MSG.chatColors("&cNo active editor session. Use &e/editor edit &cfirst."))
            return
        }

        when (args.getOrNull(1)?.lowercase()) {
            null, "status" -> {
                player.sendMessage(MSG.chatColors("&eSpawn holograms: &f${if (session.isHologramsEnabled()) "enabled" else "disabled"}"))
                player.sendMessage(MSG.chatColors("&7Use: &e/editor holograms <on|off|refresh|status>"))
            }
            "on", "enable" -> session.setHologramsEnabled(true)
            "off", "disable" -> session.setHologramsEnabled(false)
            "refresh" -> session.refreshHolograms()
            else -> player.sendMessage(MSG.chatColors("&cUsage: /editor holograms <on|off|refresh|status>"))
        }
    }

    private fun handleSetup(player: Player) {
        val result = gameFileService.runQuickSetup()
        player.sendMessage(MSG.chatColors("&6=== Editor Quick Setup ==="))
        player.sendMessage(MSG.chatColors("&7Created folders: &f${result.foldersCreated}"))
        player.sendMessage(MSG.chatColors("&7Copied bundled configs: &f${result.bundledConfigsCopied}"))
        player.sendMessage(MSG.chatColors("&7Migrated legacy configs: &f${result.legacyConfigsMoved}"))
        player.sendMessage(MSG.chatColors("&7Migrated legacy schems: &f${result.legacySchemsMoved}"))
        player.sendMessage(MSG.chatColors("&7Mirrored maps to arena templates: &f${result.mapTemplatesMirrored}"))
        if (result.warnings.isNotEmpty()) {
            player.sendMessage(MSG.chatColors("&eWarnings:"))
            result.warnings.forEach { player.sendMessage(MSG.chatColors("&e- $it")) }
        } else {
            player.sendMessage(MSG.chatColors("&aSetup finished with no warnings."))
        }
    }

    private fun handleValidate(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1)
            ?: adminEditorManager.getState(player)?.activeMapName

        if (mapName != null) {
            if (!mapRepository.mapExists(mapName)) {
                player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
                return
            }
            val report = fileValidator.validate(mapName)
            sendValidationReport(player, report)
        } else {
            // Validate all maps
            val reports = fileValidator.validateAll()
            if (reports.isEmpty()) {
                player.sendMessage(MSG.chatColors("&7No maps found."))
                return
            }
            player.sendMessage(MSG.chatColors("&6=== Validation Report (all ${reports.size} maps) ==="))
            reports.values.forEach { r ->
                player.sendMessage(MSG.chatColors(r.summary()))
            }
        }
    }

    private fun handleFiles(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1)
            ?: adminEditorManager.getState(player)?.activeMapName

        if (mapName != null) {
            if (!mapRepository.mapExists(mapName)) {
                player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
                return
            }
            val info = gameFileService.getMapFileInfo(mapName)
            player.sendMessage(MSG.chatColors("&6=== Files: $mapName ==="))
            player.sendMessage(MSG.chatColors(
                "&7settings.yml: " + if (info.settingsExists) "&a✓ (${info.settingsSizeBytes}B)" else "&c✗ missing"))
            player.sendMessage(MSG.chatColors(
                "&7region.schem: " + if (info.schematicExists) "&a✓ (${info.schematicSizeBytes}B)" else "&c✗ missing"))
        } else {
            val infos = gameFileService.getAllMapFileInfos()
            if (infos.isEmpty()) {
                player.sendMessage(MSG.chatColors("&7No maps found."))
                return
            }
            player.sendMessage(MSG.chatColors("&6=== Map Files (${infos.size}) ==="))
            infos.forEach { info ->
                val statusYml  = if (info.settingsExists)  "&a✓yml"  else "&c✗yml"
                val statusSchem = if (info.schematicExists) "&a✓schem" else "&c✗schem"
                player.sendMessage(MSG.chatColors("&e${info.mapName} &7- $statusYml &7$statusSchem"))
            }
        }
    }

    private fun handleSpawn(player: Player, args: Array<out String>) {
        val mapName = adminEditorManager.getState(player)?.activeMapName ?: run {
            player.sendMessage(MSG.chatColors("&cNo active map. Use &e/editor map <name> &cfirst."))
            return
        }

        when (args.getOrNull(1)?.lowercase()) {
            "list", null -> spawnPointEditor.showSpawnPoints(player, mapName)

            "set" -> {
                val teamKey   = args.getOrNull(2) ?: run {
                    player.sendMessage(MSG.chatColors("&cUsage: /editor spawn set <team> <slot>"))
                    return
                }
                val playerKey = args.getOrNull(3) ?: "Player1"
                val success   = spawnPointEditor.setSpawnPoint(mapName, teamKey, playerKey, player.location)
                if (success) {
                    player.sendMessage(MSG.chatColors("&a✓ Spawn set: &e$teamKey/$playerKey"))
                } else {
                    player.sendMessage(MSG.chatColors("&c✗ Failed to save spawn. Check console."))
                }
            }

            "remove" -> {
                val teamKey   = args.getOrNull(2) ?: run {
                    player.sendMessage(MSG.chatColors("&cUsage: /editor spawn remove <team> <slot>"))
                    return
                }
                val playerKey = args.getOrNull(3) ?: run {
                    player.sendMessage(MSG.chatColors("&cUsage: /editor spawn remove <team> <slot>"))
                    return
                }
                val success = spawnPointEditor.removeSpawnPoint(mapName, teamKey, playerKey)
                if (success) {
                    player.sendMessage(MSG.chatColors("&a✓ Removed spawn: &e$teamKey/$playerKey"))
                } else {
                    player.sendMessage(MSG.chatColors("&c✗ Failed to remove spawn. Check console."))
                }
            }

            else -> {
                player.sendMessage(MSG.chatColors("&cUnknown spawn sub-command: ${args[1]}"))
                player.sendMessage(MSG.chatColors("&7Use: &eList &7| &eset <team> <slot> &7| &eremove <team> <slot>"))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Help
    // -------------------------------------------------------------------------

    private fun showHelp(player: Player) {
        player.sendMessage(MSG.chatColors("&6=== Admin Editor — /editor ==="))
        player.sendMessage(MSG.chatColors("&b— Edit mode —"))
        player.sendMessage(MSG.chatColors("&e/editor enable [map]              &7Enable edit mode"))
        player.sendMessage(MSG.chatColors("&e/editor disable                   &7Disable edit mode"))
        player.sendMessage(MSG.chatColors("&e/editor toggle [map]              &7Toggle edit mode"))
        player.sendMessage(MSG.chatColors("&e/editor map <name|clear>          &7Select active map"))
        player.sendMessage(MSG.chatColors("&e/editor info                      &7Show session info"))
        player.sendMessage(MSG.chatColors("&e/editor hotbar <on|off|toggle>    &7Enable/disable custom hotbar"))
        player.sendMessage(MSG.chatColors("&e/editor setup                     &7Auto-prepare Data/Games and Data/Schems"))
        player.sendMessage(MSG.chatColors("&b— Map management —"))
        player.sendMessage(MSG.chatColors("&e/editor create <name>             &7Create new map"))
        player.sendMessage(MSG.chatColors("&e/editor edit [name]               &7Open edit session (uses active map)"))
        player.sendMessage(MSG.chatColors("&e/editor delete <name>             &7Delete a map"))
        player.sendMessage(MSG.chatColors("&e/editor list                      &7List all maps"))
        player.sendMessage(MSG.chatColors("&e/editor save                      &7Save & close session"))
        player.sendMessage(MSG.chatColors("&e/editor cancel                    &7Cancel (no save)"))
        player.sendMessage(MSG.chatColors("&e/editor resize <radius>           &7Adjust save region"))
        player.sendMessage(MSG.chatColors("&e/editor settings                  &7Open settings GUI"))
        player.sendMessage(MSG.chatColors("&b— Validation & files —"))
        player.sendMessage(MSG.chatColors("&e/editor validate [map]            &7Validate map files"))
        player.sendMessage(MSG.chatColors("&e/editor files [map]               &7Show file status"))
        player.sendMessage(MSG.chatColors("&b— Spawn points —"))
        player.sendMessage(MSG.chatColors("&e/editor spawn list                &7List spawn points"))
        player.sendMessage(MSG.chatColors("&e/editor spawn set <team> <slot>   &7Set spawn at location"))
        player.sendMessage(MSG.chatColors("&e/editor spawn remove <t> <s>      &7Remove spawn point"))
        player.sendMessage(MSG.chatColors("&b— Holograms —"))
        player.sendMessage(MSG.chatColors("&e/editor holograms <on|off|refresh>&7Manage spawn holograms"))
    }

    // -------------------------------------------------------------------------
    // TabCompleter
    // -------------------------------------------------------------------------

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (sender !is Player || !sender.hasPermission(permission)) return emptyList()

        return when (args.size) {
            1 -> listOf(
                "enable", "disable", "toggle", "map", "info",
                "hotbar", "holograms", "setup",
                "create", "edit", "delete", "list",
                "save", "cancel", "resize", "settings",
                "validate", "files",
                "spawn", "help"
            ).filter { it.startsWith(args[0], ignoreCase = true) }

            2 -> when (args[0].lowercase()) {
                "enable", "toggle", "edit", "delete" ->
                    mapRepository.getAllMapNames().filter { it.startsWith(args[1], ignoreCase = true) }
                "map" -> {
                    val suggestions = mutableListOf("clear")
                    suggestions += mapRepository.getAllMapNames()
                    suggestions.filter { it.startsWith(args[1], ignoreCase = true) }
                }
                "validate", "files" ->
                    mapRepository.getAllMapNames().filter { it.startsWith(args[1], ignoreCase = true) }
                "hotbar" -> listOf("on", "off", "toggle", "status")
                    .filter { it.startsWith(args[1], ignoreCase = true) }
                "holograms" -> listOf("on", "off", "refresh", "status")
                    .filter { it.startsWith(args[1], ignoreCase = true) }
                "resize" -> listOf("25", "50", "75", "100", "150", "200")
                    .filter { it.startsWith(args[1]) }
                "spawn" -> listOf("list", "set", "remove")
                    .filter { it.startsWith(args[1], ignoreCase = true) }
                else -> emptyList()
            }

            3 -> when {
                args[0].equals("spawn", ignoreCase = true) &&
                (args[1].equals("set", ignoreCase = true) || args[1].equals("remove", ignoreCase = true)) -> {
                    val mapName = adminEditorManager.getState(sender)?.activeMapName ?: return emptyList()
                    val spawns  = spawnPointEditor.getAllSpawnPoints(mapName)
                    spawns.map { it.teamKey }.distinct().filter { it.startsWith(args[2], ignoreCase = true) }
                }
                else -> emptyList()
            }

            4 -> when {
                args[0].equals("spawn", ignoreCase = true) &&
                (args[1].equals("set", ignoreCase = true) || args[1].equals("remove", ignoreCase = true)) -> {
                    val mapName  = adminEditorManager.getState(sender)?.activeMapName ?: return emptyList()
                    val teamKey  = args[2]
                    val spawns   = spawnPointEditor.getAllSpawnPoints(mapName)
                    spawns.filter { it.teamKey == teamKey }
                          .map { it.playerKey }
                          .filter { it.startsWith(args[3], ignoreCase = true) }
                }
                else -> emptyList()
            }

            else -> emptyList()
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun sendValidationReport(player: Player, report: EditorFileValidator.ValidationReport) {
        player.sendMessage(MSG.chatColors("&6=== Validation: ${report.mapName} ==="))
        if (report.isValid) {
            player.sendMessage(MSG.chatColors("&a✓ Map is valid"))
        } else {
            player.sendMessage(MSG.chatColors("&c✗ Errors:"))
            report.errors.forEach { player.sendMessage(MSG.chatColors("  &c- $it")) }
        }
        if (report.warnings.isNotEmpty()) {
            player.sendMessage(MSG.chatColors("&eWarnings:"))
            report.warnings.forEach { player.sendMessage(MSG.chatColors("  &e- $it")) }
        }
    }

    private fun isValidMapName(name: String): Boolean =
        name.matches(Regex("^[a-zA-Z0-9_-]+$"))

    private fun suggestMaps(player: Player) {
        val maps = mapRepository.getAllMapNames()
        if (maps.isNotEmpty()) {
            player.sendMessage(MSG.chatColors("&7Available maps: &e${maps.joinToString(", ")}"))
        }
    }
}
