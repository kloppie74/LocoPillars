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
            sender.sendMessage(MSG.chatColors("&cThis command is player-only!"))
            return true
        }
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(MSG.chatColors("&cYou do not have permission to use this command!"))
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
            "spawn"     -> handleSpawn(sender, args)
            "enable"    -> handleMapEnable(sender, args)
            "disable"   -> handleMapDisable(sender, args)
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

    private fun handleToggle(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1)
        if (mapName != null && !mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
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
        if (!mapRepository.mapExists(subArg)) {
            player.sendMessage(MSG.chatColors("&cMap '$subArg' does not exist!"))
            suggestMaps(player)
            return
        }
        adminEditorManager.setActiveMap(player, subArg)
        player.sendMessage(MSG.chatColors("&aActive map set to: &f$subArg"))
        player.sendMessage(MSG.chatColors("&7Use &e/editor edit &7to start editing this map."))
    }

    private fun handleInfo(player: Player) {
        val state = adminEditorManager.getState(player)
        if (state == null) {
            player.sendMessage(MSG.chatColors("&cEdit mode is not active."))
            return
        }
        player.sendMessage(MSG.chatColors("&6=== Edit Mode Info ==="))
        player.sendMessage(MSG.chatColors("&eDuration:     &f${state.activeDurationSeconds()}s"))
        player.sendMessage(MSG.chatColors("&eActive map:   &f${state.activeMapName ?: "&7(none)"}"))
        player.sendMessage(MSG.chatColors("&eCustom hotbar: &f${if (adminEditorManager.isHotbarEnabled(player)) "enabled" else "disabled"}"))
        val session = editorSessionManager.getSession(player)
        if (session != null) {
            player.sendMessage(MSG.chatColors("&eEditor session: &aactive &7(world: ${session.tempWorld.name})"))
            player.sendMessage(MSG.chatColors("&eSave radius: &f${session.getSaveRadius()}"))
            player.sendMessage(MSG.chatColors("&eBorder size: &f${session.getBorderSize().toInt()}"))
        } else {
            player.sendMessage(MSG.chatColors("&eEditor session: &7none"))
        }
        // Show lobby status if a map is selected
        state.activeMapName?.let { lobbyLocationEditor.showLobbyStatus(player, it) }
    }

    // -------------------------------------------------------------------------
    // Map management
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
        if (!mapRepository.createMap(mapName)) {
            player.sendMessage(MSG.chatColors("&c✗ Failed to create map '$mapName'. Check console."))
            return
        }
        player.sendMessage(MSG.chatColors("&a✓ Map '$mapName' created!"))

        if (!adminEditorManager.isActive(player)) adminEditorManager.enable(player, mapName)
        else adminEditorManager.setActiveMap(player, mapName)

        editorSessionManager.startSession(player, mapName)

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val world = editorSessionManager.getSession(player)?.tempWorld
            if (world != null) {
                player.teleport(world.spawnLocation)
                player.sendMessage(MSG.chatColors("&a✓ Teleported to the edit world for &e$mapName&a."))
                player.sendMessage(MSG.chatColors("&7Build your map, then &e/editor save &7when done."))
                player.sendMessage(MSG.chatColors("&7Tip: &e/editor settings &7| &e/editor lobby &7| &e/editor spawn"))
            } else {
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    val retryWorld = editorSessionManager.getSession(player)?.tempWorld
                    if (retryWorld != null) {
                        player.teleport(retryWorld.spawnLocation)
                        player.sendMessage(MSG.chatColors("&a✓ Teleported to edit world for &e$mapName&a."))
                    } else {
                        player.sendMessage(MSG.chatColors("&cCould not teleport — use &e/editor edit $mapName &cto retry."))
                    }
                }, 40L)
            }
        }, 10L)
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
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!")); suggestMaps(player); return
        }
        if (!adminEditorManager.isActive(player)) {
            adminEditorManager.enable(player, mapName)
            player.sendMessage(MSG.chatColors("&7Edit mode was auto-enabled."))
        } else {
            adminEditorManager.setActiveMap(player, mapName)
        }
        editorSessionManager.startSession(player, mapName)
        player.sendMessage(MSG.chatColors("&7Tip: open advanced controls with &e/editor settings"))
    }

    private fun handleDelete(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1) ?: run {
            player.sendMessage(MSG.chatColors("&cUsage: /editor delete <name>")); return
        }
        if (!mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!")); return
        }

        player.sendMessage(MSG.chatColors("&cDeleting map: &f$mapName&c…"))

        // 1. Delete maps/<name>/ folder
        val mapDeleted = mapRepository.deleteMap(mapName)

        // 2. Delete Data/Games/<name>.yml
        val gameConfig = java.io.File(plugin.dataFolder, "Data/Games/$mapName.yml")
        val gameDeleted = if (gameConfig.exists()) gameConfig.delete() else true

        // 3. Delete Data/Schems/<name>.schem (single file)
        val schemFile = java.io.File(plugin.dataFolder, "Data/Schems/$mapName.schem")
        val schemDeleted = if (schemFile.exists()) schemFile.delete() else true

        // 4. Delete Data/Schems/<name>/ folder (multi-schem layout)
        val schemFolder = java.io.File(plugin.dataFolder, "Data/Schems/$mapName")
        val schemFolderDeleted = if (schemFolder.exists() && schemFolder.isDirectory)
            schemFolder.deleteRecursively() else true

        if (mapDeleted) {
            val lines = mutableListOf("&a✓ &fmaps/$mapName/ &adeleted")
            if (gameDeleted && gameConfig.exists().not())
                lines += "&a✓ &fData/Games/$mapName.yml &adeleted"
            else if (!gameDeleted)
                lines += "&e⚠ &fData/Games/$mapName.yml &ecould not be deleted"
            else
                lines += "&7— &fData/Games/$mapName.yml &7was not present"

            if (schemDeleted && !schemFile.exists())
                lines += "&a✓ &fData/Schems/$mapName.schem &adeleted"
            else if (!schemDeleted)
                lines += "&e⚠ &fData/Schems/$mapName.schem &ecould not be deleted"
            else
                lines += "&7— &fData/Schems/$mapName.schem &7was not present"

            if (schemFolder.exists())
                lines += "&e⚠ &fData/Schems/$mapName/ &ecould not be fully deleted"
            else if (!schemFolderDeleted)
                lines += "&e⚠ &fData/Schems/$mapName/ &ecould not be deleted"

            player.sendMessage(MSG.chatColors("&6=== Deleted: $mapName ==="))
            lines.forEach { player.sendMessage(MSG.chatColors(it)) }

            if (adminEditorManager.getState(player)?.activeMapName == mapName) {
                adminEditorManager.setActiveMap(player, null)
                player.sendMessage(MSG.chatColors("&7Active map selection cleared."))
            }
        } else {
            player.sendMessage(MSG.chatColors("&c✗ Failed to delete maps/$mapName/. Check console."))
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
            val hasLobby   = lobbyLocationEditor.getLobbySpawn(mapName) != null
            val lobbyTag   = if (hasLobby) " &a[lobby]" else " &7[no lobby]"
            player.sendMessage(MSG.chatColors("$status &e$mapName &7(radius: $radius)$lobbyTag"))
            if (!validation.isValid) validation.errors.forEach { player.sendMessage(MSG.chatColors("    &c- $it")) }
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
        val mapName = adminEditorManager.getState(player)?.activeMapName
        editorSessionManager.saveAndEndSession(player)

        if (mapName != null) {
            // Always overwrite Data/Games + Data/Schems on save
            val result = gameFileService.runSetupForMap(mapName)
            player.sendMessage(MSG.chatColors(result.summary()))
            result.warnings.forEach { player.sendMessage(MSG.chatColors("  &e⚠ $it")) }
            if (result.fullyReady) player.sendMessage(MSG.chatColors("&a✓ $mapName synced to Data/ — ready for games."))
            else player.sendMessage(MSG.chatColors("&7Run &e/editor setup $mapName &7to retry syncing."))
        }
    }

    private fun handleCancel(player: Player) {
        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!")); return
        }
        editorSessionManager.cancelSession(player)
    }

    private fun handleResize(player: Player, args: Array<out String>) {
        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            player.sendMessage(MSG.chatColors("&7Use &e/editor edit &7to start one.")); return
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
            player.sendMessage(MSG.chatColors("&7Use &e/editor edit &7to start one.")); return
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
            player.sendMessage(MSG.chatColors("&cNo active map. Use &e/editor map <name> &cfirst."))
            return
        }

        when (args.getOrNull(1)?.lowercase()) {

            // /editor lobby  (no sub-arg) → status overview
            null -> lobbyLocationEditor.showLobbyStatus(player, mapName)

            // /editor lobby spawn [clear]
            "spawn" -> {
                when (args.getOrNull(2)?.lowercase()) {
                    "clear", "remove" -> {
                        val ok = lobbyLocationEditor.clearLobbySpawn(mapName)
                        if (ok) player.sendMessage(MSG.chatColors("&eSpawnPoint cleared for &f$mapName&e."))
                        else    player.sendMessage(MSG.chatColors("&c✗ Failed to clear SpawnPoint."))
                    }
                    else -> {
                        // Set spawn at current player location
                        val ok = lobbyLocationEditor.setLobbySpawn(mapName, player.location)
                        if (ok) {
                            val loc = player.location
                            player.sendMessage(MSG.chatColors(
                                "&a✓ Lobby SpawnPoint set at your location: " +
                                        "&b(${loc.blockX}, ${loc.blockY}, ${loc.blockZ}) " +
                                        "&7yaw:&f${"%.1f".format(loc.yaw)}"))
                        } else {
                            player.sendMessage(MSG.chatColors("&c✗ Failed to save SpawnPoint. Check console."))
                        }
                    }
                }
            }

            // /editor lobby area <set|clear>
            "area" -> {
                when (args.getOrNull(2)?.lowercase()) {
                    "clear", "remove" -> {
                        val ok = lobbyLocationEditor.clearLobbyArea(mapName)
                        if (ok) player.sendMessage(MSG.chatColors("&eSpawn-Area cleared for &f$mapName&e."))
                        else    player.sendMessage(MSG.chatColors("&c✗ Failed to clear Spawn-Area."))
                    }
                    // /editor lobby area set <x1> <y1> <z1> <x2> <y2> <z2>  (optional manual coords)
                    "set" -> {
                        val x1 = args.getOrNull(3)?.toDoubleOrNull()
                        val y1 = args.getOrNull(4)?.toDoubleOrNull()
                        val z1 = args.getOrNull(5)?.toDoubleOrNull()
                        val x2 = args.getOrNull(6)?.toDoubleOrNull()
                        val y2 = args.getOrNull(7)?.toDoubleOrNull()
                        val z2 = args.getOrNull(8)?.toDoubleOrNull()

                        if (x1 != null && y1 != null && z1 != null && x2 != null && y2 != null && z2 != null) {
                            // All 6 coords provided directly
                            val world  = player.world
                            val loc1 = org.bukkit.Location(world, x1, y1, z1)
                            val loc2 = org.bukkit.Location(world, x2, y2, z2)
                            val ok = lobbyLocationEditor.setLobbyArea(mapName, loc1, loc2)
                            if (ok) {
                                player.sendMessage(MSG.chatColors("&a✓ Spawn-Area saved for &e$mapName&a."))
                                player.sendMessage(MSG.chatColors("  &7Loc1: &b(${x1.toInt()}, ${y1.toInt()}, ${z1.toInt()})"))
                                player.sendMessage(MSG.chatColors("  &7Loc2: &b(${x2.toInt()}, ${y2.toInt()}, ${z2.toInt()})"))
                            } else {
                                player.sendMessage(MSG.chatColors("&c✗ Failed to save Spawn-Area. Check console."))
                            }
                        } else {
                            // No coords → guide the player to use the Lobby Tool
                            player.sendMessage(MSG.chatColors("&6=== Set Spawn-Area ==="))
                            player.sendMessage(MSG.chatColors("&7Option A — use the &5Lobby Tool &7(slot 6):"))
                            player.sendMessage(MSG.chatColors("  &7Left-click a block          → set Loc1"))
                            player.sendMessage(MSG.chatColors("  &7Shift+right-click a block   → set Loc2 & save"))
                            player.sendMessage(MSG.chatColors("&7Option B — provide coords directly:"))
                            player.sendMessage(MSG.chatColors("  &e/editor lobby area set <x1> <y1> <z1> <x2> <y2> <z2>"))
                        }
                    }
                    else -> {
                        player.sendMessage(MSG.chatColors("&7Usage: &e/editor lobby area <set|clear>"))
                    }
                }
            }

            // /editor lobby clear → wipe both SpawnPoint and Spawn-Area
            "clear" -> {
                val s1 = lobbyLocationEditor.clearLobbySpawn(mapName)
                val s2 = lobbyLocationEditor.clearLobbyArea(mapName)
                if (s1 && s2) {
                    player.sendMessage(MSG.chatColors("&eAll lobby settings cleared for &f$mapName&e."))
                } else {
                    player.sendMessage(MSG.chatColors("&c✗ Partial failure clearing lobby settings. Check console."))
                }
            }

            else -> {
                player.sendMessage(MSG.chatColors("&cUnknown lobby sub-command: ${args[1]}"))
                player.sendMessage(MSG.chatColors("&7Use: &elobby &7| &elobby spawn &7| &elobby area set &7| &elobby clear"))
            }
        }
    }

    // -------------------------------------------------------------------------
    // Hotbar / Holograms / Setup / Doctor / Validate / Files / Spawn
    // -------------------------------------------------------------------------

    private fun handleHotbar(player: Player, args: Array<out String>) {
        if (!adminEditorManager.isActive(player)) {
            player.sendMessage(MSG.chatColors("&cEdit mode is not active yet. Start with &e/editor edit <map>&c."))
            return
        }
        when (args.getOrNull(1)?.lowercase()) {
            null, "status" -> {
                player.sendMessage(MSG.chatColors("&eEditor hotbar: &f${if (adminEditorManager.isHotbarEnabled(player)) "enabled" else "disabled"}"))
                player.sendMessage(MSG.chatColors("&7Use: &e/editor hotbar <on|off|toggle|status>"))
            }
            "on", "enable"   -> adminEditorManager.setHotbarEnabled(player, true)
            "off", "disable" -> adminEditorManager.setHotbarEnabled(player, false)
            "toggle"         -> adminEditorManager.toggleHotbar(player)
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
            "on", "enable"   -> session.setHologramsEnabled(true)
            "off", "disable" -> session.setHologramsEnabled(false)
            "refresh"        -> session.refreshHolograms()
            else -> player.sendMessage(MSG.chatColors("&cUsage: /editor holograms <on|off|refresh|status>"))
        }
    }

    private fun handleSetup(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1)
        if (mapName != null) {
            if (!mapRepository.mapExists(mapName)) {
                player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!")); return
            }
            player.sendMessage(MSG.chatColors("&eRunning setup for &f$mapName&e…"))
            val result = gameFileService.runSetupForMap(mapName)
            player.sendMessage(MSG.chatColors("&6=== Setup: $mapName ==="))
            player.sendMessage(MSG.chatColors(result.summary()))
            result.warnings.forEach { player.sendMessage(MSG.chatColors("  &e⚠ $it")) }
            if (result.fullyReady) player.sendMessage(MSG.chatColors("&a✓ $mapName is ready for games."))
        } else {
            val result = gameFileService.runQuickSetup()
            player.sendMessage(MSG.chatColors("&6=== Editor Quick Setup ==="))
            player.sendMessage(MSG.chatColors("&7Data/Games path:  &f${result.dataGamesPath}"))
            player.sendMessage(MSG.chatColors("&7Data/Schems path: &f${result.dataSchemsPath}"))
            player.sendMessage(MSG.chatColors("&7Folders created:             &f${result.foldersCreated}"))
            player.sendMessage(MSG.chatColors("&7Bundled configs copied:      &f${result.bundledConfigsCopied}"))
            player.sendMessage(MSG.chatColors("&7Legacy configs migrated:     &f${result.legacyConfigsMoved}"))
            player.sendMessage(MSG.chatColors("&7Legacy schems migrated:      &f${result.legacySchemsMoved}"))
            player.sendMessage(MSG.chatColors("&7Maps mirrored to templates:  &f${result.mapTemplatesMirrored}"))
            player.sendMessage(MSG.chatColors("&7Template readiness: &f${result.readiness.readyCount}/${result.readiness.configCount} ready"))
            result.readiness.entries.take(10).forEach { entry ->
                player.sendMessage(MSG.chatColors("&7- &e${entry.configName}&7: ${if (entry.ready) "&aREADY" else "&cMISSING"}"))
            }
            if (result.readiness.entries.size > 10)
                player.sendMessage(MSG.chatColors("&7… and ${result.readiness.entries.size - 10} more."))
            if (result.warnings.isNotEmpty()) {
                player.sendMessage(MSG.chatColors("&eWarnings:"))
                result.warnings.forEach { player.sendMessage(MSG.chatColors("&e- $it")) }
                player.sendMessage(MSG.chatColors("&7Run &e/editor doctor &7for a detailed readiness report."))
            } else {
                player.sendMessage(MSG.chatColors("&aSetup completed successfully."))
            }
        }
    }

    private fun handleDoctor(player: Player) {
        val report = gameFileService.analyzeTemplateReadiness()
        player.sendMessage(MSG.chatColors("&6=== Editor Doctor: Data Template Readiness ==="))
        player.sendMessage(MSG.chatColors("&7Data/Games:  &f${report.dataGamesPath}"))
        player.sendMessage(MSG.chatColors("&7Data/Schems: &f${report.dataSchemsPath}"))
        player.sendMessage(MSG.chatColors("&7Ready templates: &f${report.readyCount}/${report.configCount}"))
        if (report.entries.isEmpty()) {
            player.sendMessage(MSG.chatColors("&cNo game configs found in Data/Games."))
            player.sendMessage(MSG.chatColors("&7Use &e/editor setup &7or add .yml files manually."))
            return
        }
        report.entries.forEach { entry ->
            val status = if (entry.ready) "&aREADY" else "&cMISSING_SCHEM"
            player.sendMessage(MSG.chatColors("&e${entry.configName}&7 -> $status"))
            if (!entry.ready) {
                player.sendMessage(MSG.chatColors("&7  Expected: Data/Schems/${entry.configName}.schem"))
            }
        }
    }

    private fun handleValidate(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1) ?: adminEditorManager.getState(player)?.activeMapName
        if (mapName != null) {
            if (!mapRepository.mapExists(mapName)) {
                player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!")); return
            }
            sendValidationReport(player, fileValidator.validate(mapName))
        } else {
            val reports = fileValidator.validateAll()
            if (reports.isEmpty()) { player.sendMessage(MSG.chatColors("&7No maps found.")); return }
            player.sendMessage(MSG.chatColors("&6=== Validation Report (all ${reports.size} maps) ==="))
            reports.values.forEach { r -> player.sendMessage(MSG.chatColors(r.summary())) }
        }
    }

    private fun handleFiles(player: Player, args: Array<out String>) {
        val mapName = args.getOrNull(1) ?: adminEditorManager.getState(player)?.activeMapName
        if (mapName != null) {
            if (!mapRepository.mapExists(mapName)) {
                player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!")); return
            }
            val info = gameFileService.getMapFileInfo(mapName)
            player.sendMessage(MSG.chatColors("&6=== Files: $mapName ==="))
            player.sendMessage(MSG.chatColors("&7settings.yml: " + if (info.settingsExists) "&a✓ (${info.settingsSizeBytes}B)" else "&c✗ missing"))
            player.sendMessage(MSG.chatColors("&7region.schem: " + if (info.schematicExists) "&a✓ (${info.schematicSizeBytes}B)" else "&c✗ missing"))
        } else {
            val infos = gameFileService.getAllMapFileInfos()
            if (infos.isEmpty()) { player.sendMessage(MSG.chatColors("&7No maps found.")); return }
            player.sendMessage(MSG.chatColors("&6=== Map Files (${infos.size}) ==="))
            infos.forEach { info ->
                val sy = if (info.settingsExists) "&a✓yml" else "&c✗yml"
                val ss = if (info.schematicExists) "&a✓schem" else "&c✗schem"
                player.sendMessage(MSG.chatColors("&e${info.mapName} &7- $sy &7$ss"))
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
                val teamKey   = args.getOrNull(2) ?: run { player.sendMessage(MSG.chatColors("&cUsage: /editor spawn set <team> <slot>")); return }
                val playerKey = args.getOrNull(3) ?: "Player1"
                val ok = spawnPointEditor.setSpawnPoint(mapName, teamKey, playerKey, player.location)
                if (ok) player.sendMessage(MSG.chatColors("&a✓ Spawn set: &e$teamKey/$playerKey"))
                else    player.sendMessage(MSG.chatColors("&c✗ Failed to save spawn. Check console."))
            }
            "remove" -> {
                val teamKey   = args.getOrNull(2) ?: run { player.sendMessage(MSG.chatColors("&cUsage: /editor spawn remove <team> <slot>")); return }
                val playerKey = args.getOrNull(3) ?: run { player.sendMessage(MSG.chatColors("&cUsage: /editor spawn remove <team> <slot>")); return }
                val ok = spawnPointEditor.removeSpawnPoint(mapName, teamKey, playerKey)
                if (ok) player.sendMessage(MSG.chatColors("&a✓ Removed spawn: &e$teamKey/$playerKey"))
                else    player.sendMessage(MSG.chatColors("&c✗ Failed to remove spawn. Check console."))
            }
            else -> {
                player.sendMessage(MSG.chatColors("&cUnknown spawn sub-command: ${args[1]}"))
                player.sendMessage(MSG.chatColors("&7Use: &eList &7| &eset <team> <slot> &7| &eremove <team> <slot>"))
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
            player.sendMessage(MSG.chatColors("&cUsage: /editor enable <name>"))
            return
        }
        if (!mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!")); suggestMaps(player); return
        }

        player.sendMessage(MSG.chatColors("&eEnabling map: &f$mapName&e..."))
        val result = gameFileService.runSetupForMap(mapName)

        player.sendMessage(MSG.chatColors("&6=== Enable: $mapName ==="))
        player.sendMessage(MSG.chatColors(
            if (result.settingsCopied) "&a✓ &fData/Games/$mapName.yml &awritten"
            else                       "&c✗ &fData/Games/$mapName.yml &ccould not be written"
        ))
        player.sendMessage(MSG.chatColors(
            if (result.schemCopied) "&a✓ &fData/Schems/$mapName.schem &awritten"
            else                    "&7— &fData/Schems/$mapName.schem &7not present (map has no schematic yet)"
        ))
        result.warnings.forEach { player.sendMessage(MSG.chatColors("  &e⚠ $it")) }

        if (result.fullyReady)
            player.sendMessage(MSG.chatColors("&a✓ $mapName is now &lenabled&a and ready for games."))
        else
            player.sendMessage(MSG.chatColors("&e⚠ $mapName enabled (partially) — check warnings above."))
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
            player.sendMessage(MSG.chatColors("&cUsage: /editor disable <name>"))
            return
        }
        if (!mapRepository.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!")); suggestMaps(player); return
        }

        val gameConfig  = java.io.File(plugin.dataFolder, "Data/Games/$mapName.yml")
        val schemFile   = java.io.File(plugin.dataFolder, "Data/Schems/$mapName.schem")
        val schemFolder = java.io.File(plugin.dataFolder, "Data/Schems/$mapName")

        val removedYml   = if (gameConfig.exists())                              gameConfig.delete()    else null
        val removedSchem = if (schemFile.exists())                               schemFile.delete()     else null
        val removedDir   = if (schemFolder.exists() && schemFolder.isDirectory)  schemFolder.deleteRecursively() else null

        player.sendMessage(MSG.chatColors("&6=== Disable: $mapName ==="))
        player.sendMessage(MSG.chatColors(when (removedYml) {
            true  -> "&a✓ &fData/Games/$mapName.yml &aremoved"
            false -> "&c✗ &fData/Games/$mapName.yml &ccould not be removed"
            null  -> "&7— &fData/Games/$mapName.yml &7was not present"
        }))
        player.sendMessage(MSG.chatColors(when (removedSchem) {
            true  -> "&a✓ &fData/Schems/$mapName.schem &aremoved"
            false -> "&c✗ &fData/Schems/$mapName.schem &ccould not be removed"
            null  -> "&7— &fData/Schems/$mapName.schem &7was not present"
        }))
        if (removedDir != null) {
            player.sendMessage(MSG.chatColors(
                if (removedDir) "&a✓ &fData/Schems/$mapName/ &afolder removed"
                else            "&c✗ &fData/Schems/$mapName/ &cfolder could not be removed"
            ))
        }

        val anyRemoved = removedYml == true || removedSchem == true || removedDir == true
        if (anyRemoved)
            player.sendMessage(MSG.chatColors(
                "&e✓ $mapName &edisabled. Master copy in &fmaps/$mapName/ &eis untouched."))
        else
            player.sendMessage(MSG.chatColors(
                "&7$mapName was already disabled (nothing found in Data/)."))

        player.sendMessage(MSG.chatColors("&8Re-enable anytime with &e/editor enable $mapName&8."))
    }

    // -------------------------------------------------------------------------
    // Help
    // -------------------------------------------------------------------------

    private fun showHelp(player: Player) {
        player.sendMessage(MSG.chatColors("&6=== Admin Editor — /editor ==="))
        player.sendMessage(MSG.chatColors("&b— Edit mode —"))
        player.sendMessage(MSG.chatColors("&e/editor toggle [map]              &7Toggle edit mode"))
        player.sendMessage(MSG.chatColors("&e/editor map <name|clear>          &7Select active map"))
        player.sendMessage(MSG.chatColors("&e/editor info                      &7Show session info"))
        player.sendMessage(MSG.chatColors("&e/editor doctor                    &7Check template readiness"))
        player.sendMessage(MSG.chatColors("&e/editor hotbar <on|off|toggle>    &7Enable/disable hotbar"))
        player.sendMessage(MSG.chatColors("&b— Map management —"))
        player.sendMessage(MSG.chatColors("&e/editor create <name>             &7Create map & jump into it"))
        player.sendMessage(MSG.chatColors("&e/editor edit [name]               &7Open edit session"))
        player.sendMessage(MSG.chatColors("&e/editor delete <name>             &7Delete a map"))
        player.sendMessage(MSG.chatColors("&e/editor list                      &7List all maps"))
        player.sendMessage(MSG.chatColors("&e/editor save                      &7Save & auto-sync files"))
        player.sendMessage(MSG.chatColors("&e/editor cancel                    &7Cancel (no save)"))
        player.sendMessage(MSG.chatColors("&e/editor resize <radius>           &7Adjust save region"))
        player.sendMessage(MSG.chatColors("&e/editor settings                  &7Open settings GUI"))
        player.sendMessage(MSG.chatColors("&b— Lobby —"))
        player.sendMessage(MSG.chatColors("&e/editor lobby                     &7Show lobby status"))
        player.sendMessage(MSG.chatColors("&e/editor lobby spawn               &7Set lobby spawn at location"))
        player.sendMessage(MSG.chatColors("&e/editor lobby spawn clear         &7Clear lobby spawn"))
        player.sendMessage(MSG.chatColors("&e/editor lobby area set            &7Guide to set spawn-area"))
        player.sendMessage(MSG.chatColors("&e/editor lobby area clear          &7Clear spawn-area"))
        player.sendMessage(MSG.chatColors("&e/editor lobby clear               &7Clear all lobby settings"))
        player.sendMessage(MSG.chatColors("&b— Validation & files —"))
        player.sendMessage(MSG.chatColors("&e/editor validate [map]            &7Validate map files"))
        player.sendMessage(MSG.chatColors("&e/editor files [map]               &7Show file status"))
        player.sendMessage(MSG.chatColors("&e/editor setup [map]               &7Sync Data/Games & Data/Schems"))
        player.sendMessage(MSG.chatColors("&b— Spawn points —"))
        player.sendMessage(MSG.chatColors("&e/editor spawn list                &7List team spawn points"))
        player.sendMessage(MSG.chatColors("&e/editor spawn set <team> <slot>   &7Set spawn at location"))
        player.sendMessage(MSG.chatColors("&e/editor spawn remove <t> <s>      &7Remove spawn point"))
        player.sendMessage(MSG.chatColors("&b— Holograms —"))
        player.sendMessage(MSG.chatColors("&e/editor holograms <on|off|refresh>&7Manage spawn holograms"))
        player.sendMessage(MSG.chatColors("&b— Map activation —"))
        player.sendMessage(MSG.chatColors("&e/editor enable <name>            &7Copy map to Data/ (make it playable)"))
        player.sendMessage(MSG.chatColors("&e/editor disable <name>           &7Remove map from Data/ (keep master copy)"))
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
                "lobby", "validate", "files", "spawn", "enable", "disable", "help"
            ).filter { it.startsWith(args[0], ignoreCase = true) }

            2 -> when (args[0].lowercase()) {
                "toggle", "edit", "delete" ->
                    mapRepository.getAllMapNames().filter { it.startsWith(args[1], ignoreCase = true) }
                "map" -> (mutableListOf("clear") + mapRepository.getAllMapNames())
                    .filter { it.startsWith(args[1], ignoreCase = true) }
                "validate", "files", "setup", "enable", "disable" ->
                    mapRepository.getAllMapNames().filter { it.startsWith(args[1], ignoreCase = true) }
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
        player.sendMessage(MSG.chatColors("&6=== Validation: ${report.mapName} ==="))
        if (report.isValid) player.sendMessage(MSG.chatColors("&a✓ Map is valid"))
        else {
            player.sendMessage(MSG.chatColors("&c✗ Errors:"))
            report.errors.forEach { player.sendMessage(MSG.chatColors("  &c- $it")) }
        }
        if (report.warnings.isNotEmpty()) {
            player.sendMessage(MSG.chatColors("&eWarnings:"))
            report.warnings.forEach { player.sendMessage(MSG.chatColors("  &e- $it")) }
        }
    }

    private fun isValidMapName(name: String): Boolean = name.matches(Regex("^[a-zA-Z0-9_-]+$"))

    private fun suggestMaps(player: Player) {
        val maps = mapRepository.getAllMapNames()
        if (maps.isNotEmpty()) player.sendMessage(MSG.chatColors("&7Available maps: &e${maps.joinToString(", ")}"))
    }
}