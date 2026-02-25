package org.Kloppie74.locoPillars.commands.editor

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.editor.AdminEditorManager
import org.Kloppie74.locoPillars.editor.EditorFileValidator
import org.Kloppie74.locoPillars.editor.GameFileService
import org.Kloppie74.locoPillars.editor.SpawnPointEditor
import org.Kloppie74.locoPillars.game.map.EditorSessionManager
import org.Kloppie74.locoPillars.game.map.MapRepository
import org.Kloppie74.locoPillars.utils.MSG

/**
 * AdminEditorCommand — `/editor`
 *
 * Top-level command for the Admin-Editor system. Provides a clean, single
 * entry-point for toggling edit mode and managing maps without having to go
 * through `/pillars admin map`.
 *
 * ## Sub-commands
 * ```
 * /editor enable [map]          Enable edit mode, optionally focusing a map
 * /editor disable               Disable edit mode and restore hotbar
 * /editor toggle [map]          Toggle edit mode on/off
 * /editor map <name>            Set the active map in the current edit session
 * /editor map clear             Clear the active map selection
 * /editor info                  Show current edit mode info
 * /editor validate [map]        Validate one or all maps
 * /editor files [map]           Show file info for one or all maps
 * /editor spawn list            List spawn points for the active map
 * /editor spawn set <t> <slot>  Set a spawn point at your current location
 * /editor spawn remove <t> <s>  Remove a spawn point
 * /editor help                  Show this help
 * ```
 *
 * ## Permissions
 * All sub-commands require `locopillars.admin`.
 *
 * @param adminEditorManager Central registry for edit mode state.
 * @param mapRepository Source of map metadata.
 * @param spawnPointEditor Handles spawn point CRUD.
 * @param fileValidator Validates map files.
 * @param gameFileService Reads raw file metadata.
 * @param editorSessionManager Read-only access to active map-editing sessions.
 */
class AdminEditorCommand(
    private val adminEditorManager: AdminEditorManager,
    private val mapRepository: MapRepository,
    private val spawnPointEditor: SpawnPointEditor,
    private val fileValidator: EditorFileValidator,
    private val gameFileService: GameFileService,
    private val editorSessionManager: EditorSessionManager
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
            "validate" -> handleValidate(sender, args)
            "files"    -> handleFiles(sender, args)
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
            player.sendMessage(MSG.chatColors("&7Use &e/editor validate &7or &e/pillars admin map list"))
            return
        }

        adminEditorManager.enable(player, mapName)
    }

    private fun handleDisable(player: Player) {
        adminEditorManager.disable(player)
    }

    private fun handleToggle(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1)

        if (mapName != null && !mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
            return
        }

        val nowActive = adminEditorManager.toggle(player)
        if (nowActive && mapName != null) {
            adminEditorManager.setActiveMap(player, mapName)
        }
    }

    private fun handleMap(player: Player, args: Array<out String>) {
        if (!adminEditorManager.isActive(player)) {
            player.sendMessage(MSG.chatColors("&cEdit mode is not active! Use &e/editor enable &cfirst."))
            return
        }

        val subArg = args.getOrNull(1)

        if (subArg == null) {
            val current = adminEditorManager.getState(player)?.activeMapName
            if (current != null) {
                player.sendMessage(MSG.chatColors("&eActive map: &f$current"))
            } else {
                player.sendMessage(MSG.chatColors("&7No map selected. Use &e/editor map <name>"))
            }
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
            val maps = mapRepository.getAllMapNames()
            if (maps.isNotEmpty()) {
                player.sendMessage(MSG.chatColors("&7Available: &e${maps.joinToString(", ")}"))
            }
            return
        }

        adminEditorManager.setActiveMap(player, mapName)
        player.sendMessage(MSG.chatColors("&aActive map set to: &f$mapName"))
        player.sendMessage(MSG.chatColors("&7Hotbar tools will now operate on this map."))
    }

    private fun handleInfo(player: Player) {
        val state = adminEditorManager.getState(player)
        if (state == null) {
            player.sendMessage(MSG.chatColors("&cEdit mode is not active."))
            return
        }

        player.sendMessage(MSG.chatColors("&6=== Edit Mode Info ==="))
        player.sendMessage(MSG.chatColors("&eDuration: &f${state.activeDurationSeconds()}s"))
        player.sendMessage(MSG.chatColors("&eActive map: &f${state.activeMapName ?: "&7none"}"))

        val session = editorSessionManager.getSession(player)
        if (session != null) {
            player.sendMessage(MSG.chatColors("&eEditor session: &aactive &7(world: ${session.tempWorld.name})"))
            player.sendMessage(MSG.chatColors("&eSave radius: &f${session.getSaveRadius()}"))
        } else {
            player.sendMessage(MSG.chatColors("&eEditor session: &7none"))
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
        player.sendMessage(MSG.chatColors("&6=== Admin Editor Commands ==="))
        player.sendMessage(MSG.chatColors("&e/editor enable [map]         &7- Enable edit mode"))
        player.sendMessage(MSG.chatColors("&e/editor disable              &7- Disable edit mode"))
        player.sendMessage(MSG.chatColors("&e/editor toggle [map]         &7- Toggle edit mode"))
        player.sendMessage(MSG.chatColors("&e/editor map <name|clear>     &7- Select active map"))
        player.sendMessage(MSG.chatColors("&e/editor info                 &7- Show session info"))
        player.sendMessage(MSG.chatColors("&e/editor validate [map]       &7- Validate map files"))
        player.sendMessage(MSG.chatColors("&e/editor files [map]          &7- Show file status"))
        player.sendMessage(MSG.chatColors("&e/editor spawn list           &7- List spawn points"))
        player.sendMessage(MSG.chatColors("&e/editor spawn set <t> <s>    &7- Set spawn at location"))
        player.sendMessage(MSG.chatColors("&e/editor spawn remove <t> <s> &7- Remove spawn point"))
        player.sendMessage(MSG.chatColors("&7Also see: &e/pillars admin map <sub> &7for full editing"))
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
            1 -> listOf("enable", "disable", "toggle", "map", "info", "validate", "files", "spawn", "help")
                .filter { it.startsWith(args[0], ignoreCase = true) }

            2 -> when (args[0].lowercase()) {
                "enable", "toggle" ->
                    mapRepository.getAllMapNames()
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                "map" -> {
                    val suggestions = mutableListOf("clear")
                    suggestions += mapRepository.getAllMapNames()
                    suggestions.filter { it.startsWith(args[1], ignoreCase = true) }
                }
                "validate", "files" ->
                    mapRepository.getAllMapNames()
                        .filter { it.startsWith(args[1], ignoreCase = true) }
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
}
