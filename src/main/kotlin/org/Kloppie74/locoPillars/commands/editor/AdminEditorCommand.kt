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
import org.Kloppie74.locoPillars.editor.LobbyLocationEditor
import org.Kloppie74.locoPillars.editor.SpawnPointEditor
import org.Kloppie74.locoPillars.game.map.EditorSessionManager
import org.Kloppie74.locoPillars.game.map.MapRepository
import org.Kloppie74.locoPillars.utils.MSG

/**
 * AdminEditorCommand — `/editor`
 *
 * Single entry-point for the Admin-Editor system.
 *
 * ## Sub-commands
 * ```
 * /editor toggle [map]               Toggle edit mode on/off
 * /editor map <name|clear>           Select / clear the active map
 * /editor info                       Show current session info
 * /editor doctor                     Validate Data/Games + Data/Schems readiness
 *
 * — Map management —
 * /editor create <name>              Create map, enable edit mode & teleport in one step
 * /editor edit [name]                Open a full schematic-edit session
 * /editor delete <name>              Delete a map and all its files
 * /editor list                       List all maps with validation status
 * /editor save                       Save schematic, close session & auto-sync files
 * /editor cancel                     Cancel the current session (no save)
 * /editor resize <radius>            Adjust schematic save radius
 * /editor settings                   Open the save-radius GUI
 *
 * — Lobby —
 * /editor lobby                      Show lobby status for the active map
 * /editor lobby spawn                Set lobby SpawnPoint at your location
 * /editor lobby area set             Set Spawn-Area using Lobby Tool selections (Loc1/Loc2)
 * /editor lobby area clear           Clear the Spawn-Area
 * /editor lobby clear                Clear both SpawnPoint and Spawn-Area
 *
 * — Validation & files —
 * /editor validate [map]             Validate one or all maps
 * /editor files [map]                Show file status for one or all maps
 *
 * — Setup —
 * /editor setup [map]                Sync Data/Games & Data/Schems (all maps or one)
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
 */
class AdminEditorCommand(
    private val plugin: LocoPillars,
    private val adminEditorManager: AdminEditorManager,
    private val mapRepository: MapRepository,
    private val editorSessionManager: EditorSessionManager,
    private val editorSettingsGui: EditorSettingsGui,
    private val spawnPointEditor: SpawnPointEditor,
    private val fileValidator: EditorFileValidator,
    private val gameFileService: GameFileService,
    private val lobbyLocationEditor: LobbyLocationEditor
) : CommandExecutor, TabCompleter {

    private val permission = "locopillars.admin"

    // -------------------------------------------------------------------------
    // CommandExecutor
    // -------------------------------------------------------------------------

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(MSG.lang("editor.player_only"))
            return true
        }
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(MSG.lang("editor.no_permission"))
            return true
        }

        when (args.getOrNull(0)?.lowercase()) {
            "toggle"    -> handleToggle(sender, args)
            "map"       -> handleMap(sender, args)
            "info"      -> handleInfo(sender)
            "doctor"    -> handleDoctor(sender)
            "hotbar"    -> handleHotbar(sender, args)
            "holograms" -> handleHolograms(sender, args)
            "setup"     -> handleSetup(sender, args)
            // Map management
            "create"    -> handleCreate(sender, args)
            "edit"      -> handleEdit(sender, args)
            "delete"    -> handleDelete(sender, args)
            "list"      -> handleList(sender)
            "save"      -> handleSave(sender)
            "cancel"    -> handleCancel(sender)
            "resize"    -> handleResize(sender, args)
            "settings"  -> handleSettings(sender)
            // Lobby
            "lobby"     -> handleLobby(sender, args)
            // Validation & files
            "validate"  -> handleValidate(sender, args)
            "files"     -> handleFiles(sender, args)
            // Spawn points
            "spawn"      -> handleSpawn(sender, args)
            "schematic"  -> handleSchematic(sender, args)
            "rewards"    -> handleRewards(sender, args)
            "enable"    -> handleMapEnable(sender, args)
            "disable"   -> handleMapDisable(sender, args)
            "help", null -> showHelp(sender)
            else -> {
                sender.sendMessage(MSG.lang("editor.unknown_subcommand", "input" to args[0]))
                showHelp(sender)
            }
        }
        return true
    }

    // -------------------------------------------------------------------------
    // Sub-command handlers
    // -------------------------------------------------------------------------

    private fun handleToggle(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1)
        if (mapName != null && !mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.lang("editor.map_not_found", "name" to mapName))
            return
        }
        val wasActive = adminEditorManager.isActive(player)
        if (wasActive && editorSessionManager.hasSession(player)) editorSessionManager.cancelSession(player)
        val nowActive = adminEditorManager.toggle(player)
        if (nowActive && mapName != null) adminEditorManager.setActiveMap(player, mapName)
    }

    private fun handleMap(player: Player, args: Array<out String>) {
        val subArg = args.getOrNull(1)
        if (subArg == null) {
            val current = adminEditorManager.getState(player)?.activeMapName
            player.sendMessage(
                if (current != null) MSG.lang("editor.map.current", "name" to current)
                else MSG.lang("editor.map.none_selected")
            )
            return
        }
        if (subArg.equals("clear", ignoreCase = true)) {
            adminEditorManager.setActiveMap(player, null)
            player.sendMessage(MSG.lang("editor.map.cleared"))
            return
        }
        if (!mapRepository.mapExists(subArg)) {
            player.sendMessage(MSG.lang("editor.map_not_found", "name" to subArg))
            suggestMaps(player)
            return
        }
        adminEditorManager.setActiveMap(player, subArg)
        player.sendMessage(MSG.lang("editor.map.set", "name" to subArg))
        player.sendMessage(MSG.lang("editor.map.set_hint"))
    }

    private fun handleInfo(player: Player) {
        val state = adminEditorManager.getState(player)
        if (state == null) {
            player.sendMessage(MSG.lang("editor.mode.not_active"))
            return
        }
        player.sendMessage(MSG.lang("editor.info.header"))
        player.sendMessage(MSG.lang("editor.info.duration", "seconds" to state.activeDurationSeconds().toString()))
        val mapDisplay = state.activeMapName ?: MSG.lang("editor.info.active_map_none")
        player.sendMessage(MSG.lang("editor.info.active_map", "map" to mapDisplay))
        val hotbarStatus = if (adminEditorManager.isHotbarEnabled(player)) "enabled" else "disabled"
        player.sendMessage(MSG.lang("editor.info.hotbar_status", "status" to hotbarStatus))
        val session = editorSessionManager.getSession(player)
        if (session != null) {
            player.sendMessage(MSG.lang("editor.info.session_active", "world" to session.tempWorld.name))
            player.sendMessage(MSG.lang("editor.info.save_radius", "radius" to session.getSaveRadius().toString()))
            player.sendMessage(MSG.lang("editor.info.border_size", "size" to session.getBorderSize().toInt().toString()))
        } else {
            player.sendMessage(MSG.lang("editor.info.session_none"))
        }
        state.activeMapName?.let { lobbyLocationEditor.showLobbyStatus(player, it) }
    }

    // -------------------------------------------------------------------------
    // Map management
    // -------------------------------------------------------------------------

    private fun handleCreate(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1) ?: run {
            player.sendMessage(MSG.lang("editor.create.usage"))
            return
        }
        if (!isValidMapName(mapName)) {
            player.sendMessage(MSG.lang("editor.create.invalid_name"))
            return
        }
        if (mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.lang("editor.create.already_exists", "name" to mapName))
            return
        }
        player.sendMessage(MSG.lang("editor.create.creating", "name" to mapName))
        if (!mapRepository.createMap(mapName)) {
            player.sendMessage(MSG.lang("editor.create.failed", "name" to mapName))
            return
        }
        player.sendMessage(MSG.lang("editor.create.success", "name" to mapName))

        if (!adminEditorManager.isActive(player)) adminEditorManager.enable(player, mapName)
        else adminEditorManager.setActiveMap(player, mapName)

        editorSessionManager.startSession(player, mapName)

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val world = editorSessionManager.getSession(player)?.tempWorld
            if (world != null) {
                player.teleport(world.spawnLocation)
                player.sendMessage(MSG.lang("editor.create.teleported", "name" to mapName))
                player.sendMessage(MSG.lang("editor.create.build_hint"))
                player.sendMessage(MSG.lang("editor.create.tools_hint"))
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    val retryWorld = editorSessionManager.getSession(player)?.tempWorld
                    if (retryWorld != null) {
                        player.teleport(retryWorld.spawnLocation)
                        player.sendMessage(MSG.lang("editor.create.teleported", "name" to mapName))
                    } else {
                        player.sendMessage(MSG.lang("editor.create.teleport_failed", "name" to mapName))
                    }
                }, 40L)
            }
        }, 10L)
    }

    private fun handleEdit(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1) ?: adminEditorManager.getState(player)?.activeMapName ?: run {
            player.sendMessage(MSG.lang("editor.edit.usage"))
            player.sendMessage(MSG.lang("editor.edit.usage_tip"))
            val maps = mapRepository.getAllMapNames()
            if (maps.isNotEmpty()) player.sendMessage(MSG.lang("editor.edit.available_maps", "maps" to maps.joinToString(", ")))
            return
        }
        if (!mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.lang("editor.map_not_found", "name" to mapName)); suggestMaps(player); return
        }
        if (!adminEditorManager.isActive(player)) {
            adminEditorManager.enable(player, mapName)
            player.sendMessage(MSG.lang("editor.mode.auto_enabled"))
        } else {
            adminEditorManager.setActiveMap(player, mapName)
        }
        editorSessionManager.startSession(player, mapName)
        player.sendMessage(MSG.lang("editor.edit.settings_tip"))
    }

    private fun handleDelete(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1) ?: run {
            player.sendMessage(MSG.lang("editor.delete.usage")); return
        }
        if (!mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.lang("editor.map_not_found", "name" to mapName)); return
        }

        player.sendMessage(MSG.lang("editor.delete.deleting", "name" to mapName))

        val mapDeleted = mapRepository.deleteMap(mapName)
        val gameConfig = java.io.File(plugin.dataFolder, "Data/Games/$mapName.yml")
        val gameDeleted = if (gameConfig.exists()) gameConfig.delete() else true
        val schemFile = java.io.File(plugin.dataFolder, "Data/Schems/$mapName.schem")
        val schemDeleted = if (schemFile.exists()) schemFile.delete() else true
        val schemFolder = java.io.File(plugin.dataFolder, "Data/Schems/$mapName")
        val schemFolderDeleted = if (schemFolder.exists() && schemFolder.isDirectory)
            schemFolder.deleteRecursively() else true

        if (mapDeleted) {
            player.sendMessage(MSG.lang("editor.delete.header", "name" to mapName))
            player.sendMessage(MSG.lang("editor.delete.file_deleted", "file" to "maps/$mapName/"))

            if (gameDeleted && !gameConfig.exists())
                player.sendMessage(MSG.lang("editor.delete.file_deleted", "file" to "Data/Games/$mapName.yml"))
            else if (!gameDeleted)
                player.sendMessage(MSG.lang("editor.delete.file_not_deleted", "file" to "Data/Games/$mapName.yml"))
            else
                player.sendMessage(MSG.lang("editor.delete.file_not_present", "file" to "Data/Games/$mapName.yml"))

            if (schemDeleted && !schemFile.exists())
                player.sendMessage(MSG.lang("editor.delete.file_deleted", "file" to "Data/Schems/$mapName.schem"))
            else if (!schemDeleted)
                player.sendMessage(MSG.lang("editor.delete.file_not_deleted", "file" to "Data/Schems/$mapName.schem"))
            else
                player.sendMessage(MSG.lang("editor.delete.file_not_present", "file" to "Data/Schems/$mapName.schem"))

            if (schemFolder.exists() || !schemFolderDeleted)
                player.sendMessage(MSG.lang("editor.delete.folder_not_deleted", "folder" to "Data/Schems/$mapName/"))

            if (adminEditorManager.getState(player)?.activeMapName == mapName) {
                adminEditorManager.setActiveMap(player, null)
                player.sendMessage(MSG.lang("editor.map.cleared"))
            }
        } else {
            player.sendMessage(MSG.lang("editor.delete.failed", "name" to mapName))
        }
    }

    private fun handleList(player: Player) {
        val maps = mapRepository.getAllMapNames()
        if (maps.isEmpty()) {
            player.sendMessage(MSG.lang("editor.list.no_maps"))
            player.sendMessage(MSG.lang("editor.list.create_hint"))
            return
        }
        player.sendMessage(MSG.lang("editor.list.header", "count" to maps.size.toString()))
        maps.forEach { mapName ->
            val validation = mapRepository.validateMap(mapName)
            val status     = if (validation.isValid) "&a✓" else "&c✗"
            val radius     = mapRepository.getSaveRadius(mapName)
            val hasLobby   = lobbyLocationEditor.getLobbySpawn(mapName) != null
            val lobbyTag   = if (hasLobby) MSG.lang("editor.list.lobby_tag_yes") else MSG.lang("editor.list.lobby_tag_no")
            player.sendMessage(MSG.lang("editor.list.entry", "status" to status, "name" to mapName, "radius" to radius.toString(), "lobby_tag" to lobbyTag))
            if (!validation.isValid) validation.errors.forEach { player.sendMessage(MSG.lang("editor.list.error_entry", "error" to it)) }
            validation.warnings.forEach { player.sendMessage(MSG.lang("editor.list.warning_entry", "warning" to it)) }
        }
        player.sendMessage(MSG.lang("editor.list.edit_hint"))
    }

    private fun handleSave(player: Player) {
        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.lang("editor.save.no_session"))
            player.sendMessage(MSG.lang("editor.save.no_session_hint"))
            return
        }
        val mapName = adminEditorManager.getState(player)?.activeMapName
        editorSessionManager.saveAndEndSession(player)

        if (mapName != null) {
            val result = gameFileService.runSetupForMap(mapName)
            player.sendMessage(MSG.chatColors(result.summary()))
            result.warnings.forEach { player.sendMessage(MSG.lang("editor.setup.warning_entry", "warning" to it)) }
            if (result.fullyReady) player.sendMessage(MSG.lang("editor.save.synced", "name" to mapName))
            else player.sendMessage(MSG.lang("editor.save.sync_retry", "name" to mapName))
        }
    }

    private fun handleCancel(player: Player) {
        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.lang("editor.cancel.no_session")); return
        }
        editorSessionManager.cancelSession(player)
    }

    private fun handleResize(player: Player, args: Array<out String>) {
        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.lang("editor.resize.no_session"))
            player.sendMessage(MSG.lang("editor.resize.no_session_hint")); return
        }
        val radius = args.getOrNull(1)?.toIntOrNull() ?: run {
            val current = editorSessionManager.getSession(player)?.getSaveRadius() ?: 50
            player.sendMessage(MSG.lang("editor.resize.usage"))
            player.sendMessage(MSG.lang("editor.resize.current_radius", "radius" to current.toString()))
            return
        }
        editorSessionManager.resizeSession(player, radius)
    }

    private fun handleSettings(player: Player) {
        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.lang("editor.settings.no_session"))
            player.sendMessage(MSG.lang("editor.settings.no_session_hint")); return
        }
        Bukkit.getScheduler().runTask(plugin, Runnable { editorSettingsGui.open(player) })
    }

    // -------------------------------------------------------------------------
    // /editor lobby [spawn|area|clear]
    // -------------------------------------------------------------------------

    /**
     * Manages the pre-game lobby location data for the active map.
     *
     * ```
     * /editor lobby                — show current lobby status
     * /editor lobby spawn          — set SpawnPoint at player's current location
     * /editor lobby spawn clear    — remove SpawnPoint
     * /editor lobby area set       — save Loc1+Loc2 using Lobby Tool selections
     * /editor lobby area clear     — remove Spawn-Area
     * /editor lobby clear          — remove both SpawnPoint and Spawn-Area
     * ```
     */
    private fun handleLobby(player: Player, args: Array<out String>) {
        val mapName = adminEditorManager.getState(player)?.activeMapName ?: run {
            player.sendMessage(MSG.lang("editor.lobby.no_active_map"))
            return
        }

        when (args.getOrNull(1)?.lowercase()) {

            null -> lobbyLocationEditor.showLobbyStatus(player, mapName)

            "spawn" -> {
                when (args.getOrNull(2)?.lowercase()) {
                    "clear", "remove" -> {
                        val ok = lobbyLocationEditor.clearLobbySpawn(mapName)
                        if (ok) player.sendMessage(MSG.lang("editor.lobby.spawn_cleared", "name" to mapName))
                        else    player.sendMessage(MSG.lang("editor.lobby.spawn_clear_failed"))
                    }
                    else -> {
                        val ok = lobbyLocationEditor.setLobbySpawn(mapName, player.location)
                        if (ok) {
                            val loc = player.location
                            player.sendMessage(MSG.lang("editor.lobby.spawn_set",
                                "x" to loc.blockX.toString(), "y" to loc.blockY.toString(),
                                "z" to loc.blockZ.toString(), "yaw" to "%.1f".format(loc.yaw)))
                        } else {
                            player.sendMessage(MSG.lang("editor.lobby.spawn_set_failed"))
                        }
                    }
                }
            }

            "area" -> {
                when (args.getOrNull(2)?.lowercase()) {
                    "clear", "remove" -> {
                        val ok = lobbyLocationEditor.clearLobbyArea(mapName)
                        if (ok) player.sendMessage(MSG.lang("editor.lobby.area_cleared", "name" to mapName))
                        else    player.sendMessage(MSG.lang("editor.lobby.area_clear_failed"))
                    }
                    "set" -> {
                        val x1 = args.getOrNull(3)?.toDoubleOrNull()
                        val y1 = args.getOrNull(4)?.toDoubleOrNull()
                        val z1 = args.getOrNull(5)?.toDoubleOrNull()
                        val x2 = args.getOrNull(6)?.toDoubleOrNull()
                        val y2 = args.getOrNull(7)?.toDoubleOrNull()
                        val z2 = args.getOrNull(8)?.toDoubleOrNull()

                        if (x1 != null && y1 != null && z1 != null && x2 != null && y2 != null && z2 != null) {
                            val world  = player.world
                            val loc1 = org.bukkit.Location(world, x1, y1, z1)
                            val loc2 = org.bukkit.Location(world, x2, y2, z2)
                            val ok = lobbyLocationEditor.setLobbyArea(mapName, loc1, loc2)
                            if (ok) {
                                player.sendMessage(MSG.lang("editor.lobby.area_saved", "name" to mapName))
                                player.sendMessage(MSG.lang("editor.lobby.area_loc1", "x" to x1.toInt().toString(), "y" to y1.toInt().toString(), "z" to z1.toInt().toString()))
                                player.sendMessage(MSG.lang("editor.lobby.area_loc2", "x" to x2.toInt().toString(), "y" to y2.toInt().toString(), "z" to z2.toInt().toString()))
                            } else {
                                player.sendMessage(MSG.lang("editor.lobby.area_save_failed_check"))
                            }
                        } else {
                            player.sendMessage(MSG.lang("editor.lobby.area_set_header"))
                            player.sendMessage(MSG.lang("editor.lobby.area_set_option_a"))
                            player.sendMessage(MSG.lang("editor.lobby.area_set_left"))
                            player.sendMessage(MSG.lang("editor.lobby.area_set_shift_right"))
                            player.sendMessage(MSG.lang("editor.lobby.area_set_option_b"))
                            player.sendMessage(MSG.lang("editor.lobby.area_set_coords"))
                        }
                    }
                    else -> {
                        player.sendMessage(MSG.lang("editor.lobby.area_usage"))
                    }
                }
            }

            "clear" -> {
                val s1 = lobbyLocationEditor.clearLobbySpawn(mapName)
                val s2 = lobbyLocationEditor.clearLobbyArea(mapName)
                if (s1 && s2) {
                    player.sendMessage(MSG.lang("editor.lobby.cleared_all", "name" to mapName))
                } else {
                    player.sendMessage(MSG.lang("editor.lobby.clear_failed"))
                }
            }

            else -> {
                player.sendMessage(MSG.lang("editor.lobby.unknown_sub", "input" to args[1]))
                player.sendMessage(MSG.lang("editor.lobby.unknown_sub_hint"))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Hotbar / Holograms / Setup / Doctor / Validate / Files / Spawn
    // -------------------------------------------------------------------------

    private fun handleHotbar(player: Player, args: Array<out String>) {
        if (!adminEditorManager.isActive(player)) {
            player.sendMessage(MSG.lang("editor.mode.not_active_start"))
            return
        }
        when (args.getOrNull(1)?.lowercase()) {
            null, "status" -> {
                val status = if (adminEditorManager.isHotbarEnabled(player)) "enabled" else "disabled"
                player.sendMessage(MSG.lang("editor.hotbar.status", "status" to status))
                player.sendMessage(MSG.lang("editor.hotbar.status_hint"))
            }
            "on", "enable"   -> adminEditorManager.setHotbarEnabled(player, true)
            "off", "disable" -> adminEditorManager.setHotbarEnabled(player, false)
            "toggle"         -> adminEditorManager.toggleHotbar(player)
            else -> player.sendMessage(MSG.lang("editor.hotbar.usage"))
        }
    }

    private fun handleHolograms(player: Player, args: Array<out String>) {
        val session = editorSessionManager.getSession(player) ?: run {
            player.sendMessage(MSG.lang("editor.holograms.no_session"))
            return
        }
        when (args.getOrNull(1)?.lowercase()) {
            null, "status" -> {
                val status = if (session.isHologramsEnabled()) "enabled" else "disabled"
                player.sendMessage(MSG.lang("editor.holograms.status", "status" to status))
                player.sendMessage(MSG.lang("editor.holograms.status_hint"))
            }
            "on", "enable"   -> session.setHologramsEnabled(true)
            "off", "disable" -> session.setHologramsEnabled(false)
            "refresh"        -> session.refreshHolograms()
            else -> player.sendMessage(MSG.lang("editor.holograms.usage"))
        }
    }

    private fun handleSetup(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1)
        if (mapName != null) {
            if (!mapRepository.mapExists(mapName)) {
                player.sendMessage(MSG.lang("editor.map_not_found", "name" to mapName)); return
            }
            player.sendMessage(MSG.lang("editor.setup.running", "name" to mapName))
            val result = gameFileService.runSetupForMap(mapName)
            player.sendMessage(MSG.lang("editor.setup.header_map", "name" to mapName))
            player.sendMessage(MSG.chatColors(result.summary()))
            result.warnings.forEach { player.sendMessage(MSG.lang("editor.setup.warning_entry", "warning" to it)) }
            if (result.fullyReady) player.sendMessage(MSG.lang("editor.setup.ready", "name" to mapName))
        } else {
            val result = gameFileService.runQuickSetup()
            player.sendMessage(MSG.lang("editor.setup.header_quick"))
            player.sendMessage(MSG.lang("editor.setup.games_path", "path" to result.dataGamesPath))
            player.sendMessage(MSG.lang("editor.setup.schems_path", "path" to result.dataSchemsPath))
            player.sendMessage(MSG.lang("editor.setup.folders_created", "count" to result.foldersCreated.toString()))
            player.sendMessage(MSG.lang("editor.setup.configs_copied", "count" to result.bundledConfigsCopied.toString()))
            player.sendMessage(MSG.lang("editor.setup.configs_migrated", "count" to result.legacyConfigsMoved.toString()))
            player.sendMessage(MSG.lang("editor.setup.schems_migrated", "count" to result.legacySchemsMoved.toString()))
            player.sendMessage(MSG.lang("editor.setup.templates_mirrored", "count" to result.mapTemplatesMirrored.toString()))
            player.sendMessage(MSG.lang("editor.setup.readiness", "ready" to result.readiness.readyCount.toString(), "total" to result.readiness.configCount.toString()))
            result.readiness.entries.take(10).forEach { entry ->
                val key = if (entry.ready) "editor.setup.readiness_entry_ready" else "editor.setup.readiness_entry_missing"
                player.sendMessage(MSG.lang(key, "name" to entry.configName))
            }
            if (result.readiness.entries.size > 10)
                player.sendMessage(MSG.lang("editor.setup.readiness_more", "count" to (result.readiness.entries.size - 10).toString()))
            if (result.warnings.isNotEmpty()) {
                player.sendMessage(MSG.lang("editor.setup.warnings_header"))
                result.warnings.forEach { player.sendMessage(MSG.lang("editor.setup.warning_entry", "warning" to it)) }
                player.sendMessage(MSG.lang("editor.setup.doctor_hint"))
            } else {
                player.sendMessage(MSG.lang("editor.setup.success"))
            }
        }
    }

    private fun handleDoctor(player: Player) {
        val report = gameFileService.analyzeTemplateReadiness()
        player.sendMessage(MSG.lang("editor.doctor.header"))
        player.sendMessage(MSG.lang("editor.doctor.games_path", "path" to report.dataGamesPath))
        player.sendMessage(MSG.lang("editor.doctor.schems_path", "path" to report.dataSchemsPath))
        player.sendMessage(MSG.lang("editor.doctor.readiness", "ready" to report.readyCount.toString(), "total" to report.configCount.toString()))
        if (report.entries.isEmpty()) {
            player.sendMessage(MSG.lang("editor.doctor.no_configs"))
            player.sendMessage(MSG.lang("editor.doctor.no_configs_hint"))
            return
        }
        report.entries.forEach { entry ->
            val key = if (entry.ready) "editor.doctor.entry_ready" else "editor.doctor.entry_missing"
            player.sendMessage(MSG.lang(key, "name" to entry.configName))
            if (!entry.ready) {
                player.sendMessage(MSG.lang("editor.doctor.entry_expected", "name" to entry.configName))
            }
        }
    }

    private fun handleValidate(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1) ?: adminEditorManager.getState(player)?.activeMapName
        if (mapName != null) {
            if (!mapRepository.mapExists(mapName)) {
                player.sendMessage(MSG.lang("editor.map_not_found", "name" to mapName)); return
            }
            sendValidationReport(player, fileValidator.validate(mapName))
        } else {
            val reports = fileValidator.validateAll()
            if (reports.isEmpty()) { player.sendMessage(MSG.lang("editor.validate.no_maps")); return }
            player.sendMessage(MSG.lang("editor.validate.header_all", "count" to reports.size.toString()))
            reports.values.forEach { r -> sendValidationReport(player, r) }
        }
    }

    private fun handleFiles(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1) ?: adminEditorManager.getState(player)?.activeMapName
        if (mapName != null) {
            if (!mapRepository.mapExists(mapName)) {
                player.sendMessage(MSG.lang("editor.map_not_found", "name" to mapName)); return
            }
            val info = gameFileService.getMapFileInfo(mapName)
            player.sendMessage(MSG.lang("editor.files.header", "name" to mapName))
            if (info.settingsExists) player.sendMessage(MSG.lang("editor.files.settings_present", "size" to info.settingsSizeBytes.toString()))
            else player.sendMessage(MSG.lang("editor.files.settings_missing"))
            if (info.schematicExists) player.sendMessage(MSG.lang("editor.files.schem_present", "size" to info.schematicSizeBytes.toString()))
            else player.sendMessage(MSG.lang("editor.files.schem_missing"))
        } else {
            val infos = gameFileService.getAllMapFileInfos()
            if (infos.isEmpty()) { player.sendMessage(MSG.lang("editor.files.no_maps")); return }
            player.sendMessage(MSG.lang("editor.files.header_all", "count" to infos.size.toString()))
            infos.forEach { info ->
                val sy = if (info.settingsExists) "&a✓yml" else "&c✗yml"
                val ss = if (info.schematicExists) "&a✓schem" else "&c✗schem"
                player.sendMessage(MSG.lang("editor.files.entry", "name" to info.mapName, "yml" to sy, "schem" to ss))
            }
        }
    }

    private fun handleSpawn(player: Player, args: Array<out String>) {
        val mapName = adminEditorManager.getState(player)?.activeMapName ?: run {
            player.sendMessage(MSG.lang("editor.spawn.no_active_map"))
            return
        }
        when (args.getOrNull(1)?.lowercase()) {
            "list", null -> spawnPointEditor.showSpawnPoints(player, mapName)
            "set" -> {
                val teamKey   = args.getOrNull(2) ?: run { player.sendMessage(MSG.lang("editor.spawn.usage_set")); return }
                val playerKey = args.getOrNull(3) ?: "Player1"
                val ok = spawnPointEditor.setSpawnPoint(mapName, teamKey, playerKey, player.location)
                if (ok) player.sendMessage(MSG.lang("editor.spawn.set_success", "team" to "$teamKey/$playerKey"))
                else    player.sendMessage(MSG.lang("editor.spawn.set_failed"))
            }
            "remove" -> {
                val teamKey   = args.getOrNull(2) ?: run { player.sendMessage(MSG.lang("editor.spawn.usage_remove")); return }
                val playerKey = args.getOrNull(3) ?: run { player.sendMessage(MSG.lang("editor.spawn.usage_remove")); return }
                val ok = spawnPointEditor.removeSpawnPoint(mapName, teamKey, playerKey)
                if (ok) player.sendMessage(MSG.lang("editor.spawn.remove_success", "team" to "$teamKey/$playerKey"))
                else    player.sendMessage(MSG.lang("editor.spawn.remove_failed"))
            }
            else -> {
                player.sendMessage(MSG.lang("editor.spawn.unknown_sub", "input" to args[1]))
                player.sendMessage(MSG.lang("editor.spawn.unknown_sub_hint"))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Enable / Disable map (Data/ folder only)
    // -------------------------------------------------------------------------

    /**
     * /editor enable <name>
     *
     * Copies the map's assets from maps/<name>/ into Data/Games/ and
     * Data/Schems/ so the game system can use it.  Equivalent to running
     * /editor setup <name>, but named for clarity.
     */
    private fun handleMapEnable(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1) ?: run {
            player.sendMessage(MSG.lang("editor.enable.usage"))
            return
        }
        if (!mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.lang("editor.map_not_found", "name" to mapName)); suggestMaps(player); return
        }

        player.sendMessage(MSG.lang("editor.enable.enabling", "name" to mapName))
        val result = gameFileService.runSetupForMap(mapName)

        player.sendMessage(MSG.lang("editor.enable.header", "name" to mapName))
        if (result.settingsCopied) player.sendMessage(MSG.lang("editor.enable.yml_written", "name" to mapName))
        else player.sendMessage(MSG.lang("editor.enable.yml_failed", "name" to mapName))
        if (result.schemCopied) player.sendMessage(MSG.lang("editor.enable.schem_written", "name" to mapName))
        else player.sendMessage(MSG.lang("editor.enable.schem_missing", "name" to mapName))
        result.warnings.forEach { player.sendMessage(MSG.lang("editor.enable.warning_entry", "warning" to it)) }

        if (result.fullyReady)
            player.sendMessage(MSG.lang("editor.enable.fully_ready", "name" to mapName))
        else
            player.sendMessage(MSG.lang("editor.enable.partial", "name" to mapName))
    }

    /**
     * /editor disable <name>
     *
     * Removes the map's assets from Data/Games/ and Data/Schems/ WITHOUT
     * touching the master copy in maps/<name>/.  The map can be re-enabled
     * at any time with /editor enable <name>.
     */
    private fun handleMapDisable(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1) ?: run {
            player.sendMessage(MSG.lang("editor.disable.usage"))
            return
        }
        if (!mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.lang("editor.map_not_found", "name" to mapName)); suggestMaps(player); return
        }

        val gameConfig  = java.io.File(plugin.dataFolder, "Data/Games/$mapName.yml")
        val schemFile   = java.io.File(plugin.dataFolder, "Data/Schems/$mapName.schem")
        val schemFolder = java.io.File(plugin.dataFolder, "Data/Schems/$mapName")

        val removedYml   = if (gameConfig.exists())                              gameConfig.delete()    else null
        val removedSchem = if (schemFile.exists())                               schemFile.delete()     else null
        val removedDir   = if (schemFolder.exists() && schemFolder.isDirectory)  schemFolder.deleteRecursively() else null

        player.sendMessage(MSG.lang("editor.disable.header", "name" to mapName))
        player.sendMessage(when (removedYml) {
            true  -> MSG.lang("editor.disable.yml_removed", "name" to mapName)
            false -> MSG.lang("editor.disable.yml_failed", "name" to mapName)
            null  -> MSG.lang("editor.disable.yml_absent", "name" to mapName)
        })
        player.sendMessage(when (removedSchem) {
            true  -> MSG.lang("editor.disable.schem_removed", "name" to mapName)
            false -> MSG.lang("editor.disable.schem_failed", "name" to mapName)
            null  -> MSG.lang("editor.disable.schem_absent", "name" to mapName)
        })
        if (removedDir != null) {
            player.sendMessage(
                if (removedDir) MSG.lang("editor.disable.folder_removed", "name" to mapName)
                else            MSG.lang("editor.disable.folder_failed", "name" to mapName)
            )
        }

        val anyRemoved = removedYml == true || removedSchem == true || removedDir == true
        if (anyRemoved)
            player.sendMessage(MSG.lang("editor.disable.disabled_success", "name" to mapName))
        else
            player.sendMessage(MSG.lang("editor.disable.already_disabled", "name" to mapName))

        player.sendMessage(MSG.lang("editor.disable.reenable_hint", "name" to mapName))
    }

    // -------------------------------------------------------------------------
    // Schematic (multi-schem support)
    // -------------------------------------------------------------------------

    private fun handleSchematic(player: Player, args: Array<out String>) {
        val hasSession = editorSessionManager.hasSession(player)
        val sessionMap = adminEditorManager.getState(player)?.activeMapName

        val sub = args.getOrNull(1)?.lowercase()

        // ── List: no sub or map name ─────────────────────────────────────────
        if (sub == null || (sub != "save" && sub != "load")) {
            val mapName = if (hasSession && sessionMap != null) sessionMap
                          else sub ?: run { player.sendMessage(MSG.lang("editor.schematic.usage")); return }

            if (!mapRepository.mapExists(mapName)) {
                player.sendMessage(MSG.lang("editor.map_not_found", "name" to mapName)); suggestMaps(player); return
            }
            val files = mapRepository.getMapSchematicFiles(mapName)
            if (files.isEmpty()) {
                player.sendMessage(MSG.lang("editor.schematic.none", "name" to mapName))
            } else {
                player.sendMessage(MSG.lang("editor.schematic.list_header", "name" to mapName, "count" to files.size.toString()))
                files.forEach { f -> player.sendMessage(MSG.lang("editor.schematic.list_entry", "file" to f.name)) }
            }
            player.sendMessage(MSG.lang("editor.schematic.usage_hint"))
            return
        }

        // ── Save / Load: require active session ──────────────────────────────
        if (!hasSession || sessionMap == null) {
            player.sendMessage(MSG.lang("editor.schematic.no_session")); return
        }
        val mapName = sessionMap

        val idArg = args.getOrNull(2)
        val id = idArg?.toIntOrNull()
        if (id == null || id < 1) {
            player.sendMessage(MSG.lang("editor.schematic.invalid_id")); return
        }

        mapRepository.getMapSchematicsFolder(mapName).mkdirs()
        val schemFile = mapRepository.getMapSchematicFile(mapName, id)

        if (sub == "save") {
            val session = editorSessionManager.getSession(player) ?: return
            val (loc1, loc2) = session.getSaveRegionBounds()
            player.sendMessage(MSG.lang("editor.schematic.saving", "id" to id.toString(), "name" to mapName))
            editorSessionManager.saveSchematicToFile(player, loc1, loc2, schemFile) { success ->
                if (success) {
                    player.sendMessage(MSG.lang("editor.schematic.saved", "id" to id.toString(), "name" to mapName))
                    val result = gameFileService.runSetupForMap(mapName)
                    result.warnings.forEach { player.sendMessage(MSG.lang("editor.schematic.sync_warning", "msg" to it)) }
                } else {
                    player.sendMessage(MSG.lang("editor.schematic.save_failed", "id" to id.toString()))
                }
            }
        } else { // load
            if (!schemFile.exists()) {
                player.sendMessage(MSG.lang("editor.schematic.not_found", "id" to id.toString(), "name" to mapName)); return
            }
            player.sendMessage(MSG.lang("editor.schematic.loading", "id" to id.toString(), "name" to mapName))
            editorSessionManager.loadSchematicIntoSession(player, schemFile) { success ->
                if (success) player.sendMessage(MSG.lang("editor.schematic.loaded", "id" to id.toString(), "name" to mapName))
                else         player.sendMessage(MSG.lang("editor.schematic.load_failed", "id" to id.toString()))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Rewards management in editor
    // -------------------------------------------------------------------------

    private fun handleRewards(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1) ?: adminEditorManager.getState(player)?.activeMapName ?: run {
            player.sendMessage(MSG.lang("editor.rewards.usage"))
            return
        }
        if (!mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.lang("editor.map_not_found", "name" to mapName)); suggestMaps(player); return
        }
        val sub = args.getOrNull(2)?.lowercase()
        when (sub) {
            "list", null -> showRewardsList(player, mapName)
            "add-message" -> {
                val target = args.getOrNull(3) ?: run { player.sendMessage(MSG.lang("editor.rewards.add_message_usage")); return }
                val message = args.drop(4).joinToString(" ").ifBlank { run { player.sendMessage(MSG.lang("editor.rewards.add_message_usage")); return } }
                addRewardEntry(player, mapName, target, "messages", message)
            }
            "add-command" -> {
                val target = args.getOrNull(3) ?: run { player.sendMessage(MSG.lang("editor.rewards.add_command_usage")); return }
                val command = args.drop(4).joinToString(" ").ifBlank { run { player.sendMessage(MSG.lang("editor.rewards.add_command_usage")); return } }
                addRewardEntry(player, mapName, target, "commands", command)
            }
            "clear" -> {
                val target = args.getOrNull(3) ?: run { player.sendMessage(MSG.lang("editor.rewards.clear_usage")); return }
                clearRewardSection(player, mapName, target)
            }
            else -> player.sendMessage(MSG.lang("editor.rewards.usage"))
        }
    }

    private fun showRewardsList(player: Player, mapName: String) {
        val settings = mapRepository.loadMapSettings(mapName) ?: run {
            player.sendMessage(MSG.lang("editor.rewards.load_error")); return
        }
        player.sendMessage(MSG.lang("editor.rewards.list_header", "name" to mapName))
        listOf("OnStartGameRewards", "OnKillRewards", "OnLeaveRewards", "OnEndGameRewards").forEach { section ->
            val msgs = settings.getStringList("$section.messages")
            val cmds = settings.getStringList("$section.commands")
            if (msgs.isNotEmpty() || cmds.isNotEmpty()) {
                player.sendMessage(MSG.lang("editor.rewards.section_header", "section" to section))
                msgs.forEach { player.sendMessage(MSG.lang("editor.rewards.message_entry", "msg" to it)) }
                cmds.forEach { player.sendMessage(MSG.lang("editor.rewards.command_entry", "cmd" to it)) }
            }
        }
    }

    private fun addRewardEntry(player: Player, mapName: String, target: String, type: String, value: String) {
        val settings = mapRepository.loadMapSettings(mapName) ?: run {
            player.sendMessage(MSG.lang("editor.rewards.load_error")); return
        }
        val path = resolveRewardPath(target, type)
        if (path == null) {
            player.sendMessage(MSG.lang("editor.rewards.unknown_target", "target" to target)); return
        }
        val list = settings.getStringList(path).toMutableList()
        list.add(value)
        settings.set(path, list)
        settings.set(resolveRewardPath(target, "enabled") ?: return, true)
        mapRepository.saveMapSettings(mapName, settings)
        val key = if (type == "messages") "editor.rewards.message_added" else "editor.rewards.command_added"
        player.sendMessage(MSG.lang(key, "value" to value, "target" to target))
    }

    private fun clearRewardSection(player: Player, mapName: String, target: String) {
        val settings = mapRepository.loadMapSettings(mapName) ?: run {
            player.sendMessage(MSG.lang("editor.rewards.load_error")); return
        }
        val basePath = rewardTargetToYamlPath(target)
        if (basePath == null) {
            player.sendMessage(MSG.lang("editor.rewards.unknown_target", "target" to target)); return
        }
        settings.set("$basePath.messages", emptyList<String>())
        settings.set("$basePath.commands", emptyList<String>())
        mapRepository.saveMapSettings(mapName, settings)
        player.sendMessage(MSG.lang("editor.rewards.cleared", "target" to target))
    }

    private fun resolveRewardPath(target: String, type: String): String? {
        val base = rewardTargetToYamlPath(target) ?: return null
        return "$base.$type"
    }

    private fun rewardTargetToYamlPath(target: String): String? = when (target.lowercase()) {
        "start"             -> "OnStartGameRewards"
        "kill.killer"       -> "OnKillRewards.killed_by_player.killer"
        "kill.victim"       -> "OnKillRewards.killed_by_player.killed_player"
        "kill.remaining"    -> "OnKillRewards.killed_by_player.remaining_players"
        "leave.player"      -> "OnLeaveRewards.left_player"
        "leave.remaining"   -> "OnLeaveRewards.remaining_players"
        "end.winner"        -> "OnEndGameRewards.winners"
        "end.spectator"     -> "OnEndGameRewards.spectators"
        else                -> null
    }

    // -------------------------------------------------------------------------
    // Help
    // -------------------------------------------------------------------------

    private fun showHelp(player: Player) {
        player.sendMessage(MSG.lang("editor.help.header"))
        player.sendMessage(MSG.lang("editor.help.section_mode"))
        player.sendMessage(MSG.lang("editor.help.cmd_toggle"))
        player.sendMessage(MSG.lang("editor.help.cmd_map"))
        player.sendMessage(MSG.lang("editor.help.cmd_info"))
        player.sendMessage(MSG.lang("editor.help.cmd_doctor"))
        player.sendMessage(MSG.lang("editor.help.cmd_hotbar"))
        player.sendMessage(MSG.lang("editor.help.section_maps"))
        player.sendMessage(MSG.lang("editor.help.cmd_create"))
        player.sendMessage(MSG.lang("editor.help.cmd_edit"))
        player.sendMessage(MSG.lang("editor.help.cmd_delete"))
        player.sendMessage(MSG.lang("editor.help.cmd_list"))
        player.sendMessage(MSG.lang("editor.help.cmd_save"))
        player.sendMessage(MSG.lang("editor.help.cmd_cancel"))
        player.sendMessage(MSG.lang("editor.help.cmd_resize"))
        player.sendMessage(MSG.lang("editor.help.cmd_settings"))
        player.sendMessage(MSG.lang("editor.help.section_lobby"))
        player.sendMessage(MSG.lang("editor.help.cmd_lobby"))
        player.sendMessage(MSG.lang("editor.help.cmd_lobby_spawn"))
        player.sendMessage(MSG.lang("editor.help.cmd_lobby_spawn_clear"))
        player.sendMessage(MSG.lang("editor.help.cmd_lobby_area_set"))
        player.sendMessage(MSG.lang("editor.help.cmd_lobby_area_clear"))
        player.sendMessage(MSG.lang("editor.help.cmd_lobby_clear"))
        player.sendMessage(MSG.lang("editor.help.section_validation"))
        player.sendMessage(MSG.lang("editor.help.cmd_validate"))
        player.sendMessage(MSG.lang("editor.help.cmd_files"))
        player.sendMessage(MSG.lang("editor.help.cmd_setup"))
        player.sendMessage(MSG.lang("editor.help.section_spawn"))
        player.sendMessage(MSG.lang("editor.help.cmd_spawn_list"))
        player.sendMessage(MSG.lang("editor.help.cmd_spawn_set"))
        player.sendMessage(MSG.lang("editor.help.cmd_spawn_remove"))
        player.sendMessage(MSG.lang("editor.help.section_holograms"))
        player.sendMessage(MSG.lang("editor.help.cmd_holograms"))
        player.sendMessage(MSG.lang("editor.help.section_activation"))
        player.sendMessage(MSG.lang("editor.help.cmd_enable"))
        player.sendMessage(MSG.lang("editor.help.cmd_disable"))
    }

    // -------------------------------------------------------------------------
    // TabCompleter
    // -------------------------------------------------------------------------

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (sender !is Player || !sender.hasPermission(permission)) return emptyList()

        return when (args.size) {
            1 -> listOf(
                "toggle", "map", "info", "doctor", "hotbar", "holograms", "setup",
                "create", "edit", "delete", "list", "save", "cancel", "resize", "settings",
                "lobby", "validate", "files", "spawn", "schematic", "rewards",
                "enable", "disable", "help"
            ).filter { it.startsWith(args[0], ignoreCase = true) }

            2 -> when (args[0].lowercase()) {
                "toggle", "edit", "delete" ->
                    mapRepository.getAllMapNames().filter { it.startsWith(args[1], ignoreCase = true) }
                "map" -> (mutableListOf("clear") + mapRepository.getAllMapNames())
                    .filter { it.startsWith(args[1], ignoreCase = true) }
                "validate", "files", "setup", "enable", "disable", "rewards" ->
                    mapRepository.getAllMapNames().filter { it.startsWith(args[1], ignoreCase = true) }
                "schematic" -> {
                    val inSession = editorSessionManager.hasSession(sender) &&
                            adminEditorManager.getState(sender)?.activeMapName != null
                    if (inSession) listOf("save", "load").filter { it.startsWith(args[1], ignoreCase = true) }
                    else mapRepository.getAllMapNames().filter { it.startsWith(args[1], ignoreCase = true) }
                }
                "hotbar"    -> listOf("on", "off", "toggle", "status").filter { it.startsWith(args[1], ignoreCase = true) }
                "holograms" -> listOf("on", "off", "refresh", "status").filter { it.startsWith(args[1], ignoreCase = true) }
                "resize"    -> listOf("25", "50", "75", "100", "150", "200").filter { it.startsWith(args[1]) }
                "spawn"     -> listOf("list", "set", "remove").filter { it.startsWith(args[1], ignoreCase = true) }
                "lobby"     -> listOf("spawn", "area", "clear").filter { it.startsWith(args[1], ignoreCase = true) }
                else -> emptyList()
            }

            3 -> when {
                args[0].equals("spawn", ignoreCase = true) &&
                        (args[1].equals("set", ignoreCase = true) || args[1].equals("remove", ignoreCase = true)) -> {
                    val mapName = adminEditorManager.getState(sender)?.activeMapName ?: return emptyList()
                    spawnPointEditor.getAllSpawnPoints(mapName)
                        .map { it.teamKey }.distinct()
                        .filter { it.startsWith(args[2], ignoreCase = true) }
                }
                args[0].equals("lobby", ignoreCase = true) && args[1].equals("spawn", ignoreCase = true) ->
                    listOf("clear").filter { it.startsWith(args[2], ignoreCase = true) }
                args[0].equals("lobby", ignoreCase = true) && args[1].equals("area", ignoreCase = true) ->
                    listOf("set", "clear").filter { it.startsWith(args[2], ignoreCase = true) }
                args[0].equals("schematic", ignoreCase = true) &&
                        (args[1].equals("save", ignoreCase = true) || args[1].equals("load", ignoreCase = true)) ->
                    listOf("1", "2", "3", "4", "5").filter { it.startsWith(args[2]) }
                args[0].equals("rewards", ignoreCase = true) ->
                    listOf("list", "add-message", "add-command", "clear").filter { it.startsWith(args[2], ignoreCase = true) }
                else -> emptyList()
            }

            4 -> when {
                args[0].equals("spawn", ignoreCase = true) &&
                        (args[1].equals("set", ignoreCase = true) || args[1].equals("remove", ignoreCase = true)) -> {
                    val mapName = adminEditorManager.getState(sender)?.activeMapName ?: return emptyList()
                    spawnPointEditor.getAllSpawnPoints(mapName)
                        .filter { it.teamKey == args[2] }
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
        player.sendMessage(MSG.lang("editor.validate.header_single", "name" to report.mapName))
        if (report.errors.isEmpty()) {
            player.sendMessage(MSG.lang("editor.validate.valid"))
        } else {
            player.sendMessage(MSG.lang("editor.validate.errors_header", "count" to report.errors.size.toString()))
            report.errors.forEach { player.sendMessage(MSG.lang("editor.validate.error_entry", "error" to it)) }
        }
        if (report.warnings.isNotEmpty()) {
            player.sendMessage(MSG.lang("editor.validate.warnings_header", "count" to report.warnings.size.toString()))
            report.warnings.forEach { player.sendMessage(MSG.lang("editor.validate.warning_entry", "warning" to it)) }
        }
        if (report.info.isNotEmpty()) {
            report.info.forEach { player.sendMessage(MSG.chatColors("  &8ℹ $it")) }
        }
    }

    private fun isValidMapName(name: String): Boolean = name.matches(Regex("^[a-zA-Z0-9_-]+$"))

    private fun suggestMaps(player: Player) {
        val maps = mapRepository.getAllMapNames()
        if (maps.isNotEmpty()) player.sendMessage(MSG.lang("editor.available_maps", "maps" to maps.joinToString(", ")))
    }
}