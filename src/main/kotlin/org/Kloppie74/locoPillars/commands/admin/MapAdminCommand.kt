package org.Kloppie74.locoPillars.commands.admin

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.map.EditorSessionManager
import org.Kloppie74.locoPillars.game.map.MapIOManager
import org.Kloppie74.locoPillars.utils.MSG

/**
 * MapAdminCommand - Handles `/pillars admin map` subcommands
 * 
 * Commands:
 * - /pillars admin map create <name> - Create a new map
 * - /pillars admin map edit <name> - Start editing a map
 * - /pillars admin map delete <name> - Delete a map
 * - /pillars admin map list - List all maps
 * - /pillars admin map save - Save current editing session
 * - /pillars admin map cancel - Cancel current editing session
 * - /pillars admin map resize <radius> - Adjust schematic save radius
 * - /pillars admin map info - Show current session info
 * - /pillars admin map settings <name> - Open map settings GUI
 */
class MapAdminCommand(
    private val plugin: LocoPillars,
    private val mapIOManager: MapIOManager,
    private val editorSessionManager: EditorSessionManager
) : CommandExecutor, TabCompleter {
    
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage(MSG.chatColors("&cThis command can only be used by players!"))
            return true
        }
        
        if (!sender.hasPermission("locopillars.admin")) {
            sender.sendMessage(MSG.chatColors("&cYou don't have permission to use this command!"))
            return true
        }
        
        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }
        
        when (args[0].lowercase()) {
            "create" -> handleCreate(sender, args)
            "edit" -> handleEdit(sender, args)
            "delete" -> handleDelete(sender, args)
            "list" -> handleList(sender)
            "save" -> handleSave(sender)
            "cancel" -> handleCancel(sender)
            "resize" -> handleResize(sender, args)
            "info" -> handleInfo(sender)
            "settings" -> handleSettings(sender, args)
            "validate" -> handleValidate(sender, args)
            else -> {
                sender.sendMessage(MSG.chatColors("&cUnknown subcommand: ${args[0]}"))
                showHelp(sender)
            }
        }
        
        return true
    }
    
    private fun handleCreate(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(MSG.chatColors("&cUsage: /pillars admin map create <name>"))
            return
        }
        
        val mapName = args[1]
        
        // Validate map name (no spaces, special chars, etc.)
        if (!isValidMapName(mapName)) {
            player.sendMessage(MSG.chatColors("&cInvalid map name! Use only letters, numbers, dashes, and underscores."))
            return
        }
        
        if (mapIOManager.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' already exists!"))
            return
        }
        
        player.sendMessage(MSG.chatColors("&eCreating map: &f$mapName&e..."))
        
        val success = mapIOManager.createMap(mapName)
        
        if (success) {
            player.sendMessage(MSG.chatColors("&a✓ Map '$mapName' created successfully!"))
            player.sendMessage(MSG.chatColors("&7Use &e/pillars admin map edit $mapName &7to start editing"))
        } else {
            player.sendMessage(MSG.chatColors("&c✗ Failed to create map '$mapName'!"))
        }
    }
    
    private fun handleEdit(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(MSG.chatColors("&cUsage: /pillars admin map edit <name>"))
            
            val maps = mapIOManager.getAllMapNames()
            if (maps.isNotEmpty()) {
                player.sendMessage(MSG.chatColors("&7Available maps: &e${maps.joinToString(", ")}"))
            }
            return
        }
        
        val mapName = args[1]
        
        if (!mapIOManager.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
            player.sendMessage(MSG.chatColors("&7Use &e/pillars admin map list &7to see available maps"))
            return
        }
        
        // Start editor session
        editorSessionManager.startSession(player, mapName)
    }
    
    private fun handleDelete(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(MSG.chatColors("&cUsage: /pillars admin map delete <name>"))
            return
        }
        
        val mapName = args[1]
        
        if (!mapIOManager.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
            return
        }
        
        // Require confirmation (type command twice within 10 seconds)
        // For now, just delete it
        player.sendMessage(MSG.chatColors("&cDeleting map: &f$mapName&c..."))
        
        val success = mapIOManager.deleteMap(mapName)
        
        if (success) {
            player.sendMessage(MSG.chatColors("&c✓ Map '$mapName' has been deleted!"))
        } else {
            player.sendMessage(MSG.chatColors("&c✗ Failed to delete map '$mapName'!"))
        }
    }
    
    private fun handleList(player: Player) {
        val maps = mapIOManager.getAllMapNames()
        
        if (maps.isEmpty()) {
            player.sendMessage(MSG.chatColors("&eNo maps found!"))
            player.sendMessage(MSG.chatColors("&7Use &e/pillars admin map create <name> &7to create a map"))
            return
        }
        
        player.sendMessage(MSG.chatColors("&6=== Available Maps (${maps.size}) ==="))
        
        maps.forEach { mapName ->
            val validation = mapIOManager.validateMap(mapName)
            val status = if (validation.isValid) "&a✓" else "&c✗"
            val radius = mapIOManager.getSchematicRadius(mapName)
            
            player.sendMessage(MSG.chatColors("$status &e$mapName &7(radius: ${radius})"))
            
            if (!validation.isValid) {
                validation.errors.forEach { error ->
                    player.sendMessage(MSG.chatColors("    &c- $error"))
                }
            }
            
            if (validation.warnings.isNotEmpty()) {
                validation.warnings.forEach { warning ->
                    player.sendMessage(MSG.chatColors("    &e- $warning"))
                }
            }
        }
        
        player.sendMessage(MSG.chatColors("&7Use &e/pillars admin map edit <name> &7to edit a map"))
    }
    
    private fun handleSave(player: Player) {
        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            player.sendMessage(MSG.chatColors("&7Use &e/pillars admin map edit <name> &7to start editing"))
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
            player.sendMessage(MSG.chatColors("&7Use &e/pillars admin map edit <name> &7to start editing"))
            return
        }
        
        if (args.size < 2) {
            val session = editorSessionManager.getSession(player)
            val currentRadius = session?.getSchematicRadius() ?: 50
            player.sendMessage(MSG.chatColors("&cUsage: /pillars admin map resize <radius>"))
            player.sendMessage(MSG.chatColors("&7Current radius: &e$currentRadius"))
            player.sendMessage(MSG.chatColors("&7Recommended range: 20-200"))
            return
        }
        
        val radius = args[1].toIntOrNull()
        
        if (radius == null) {
            player.sendMessage(MSG.chatColors("&cInvalid radius! Must be a number."))
            return
        }
        
        editorSessionManager.resizeSession(player, radius)
    }
    
    private fun handleInfo(player: Player) {
        if (!editorSessionManager.hasSession(player)) {
            player.sendMessage(MSG.chatColors("&cYou don't have an active editor session!"))
            return
        }
        
        editorSessionManager.showSessionInfo(player)
    }
    
    private fun handleSettings(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(MSG.chatColors("&cUsage: /pillars admin map settings <name>"))
            return
        }
        
        val mapName = args[1]
        
        if (!mapIOManager.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
            return
        }
        
        // Open GUI (to be implemented)
        player.sendMessage(MSG.chatColors("&eMap settings GUI coming soon!"))
        player.sendMessage(MSG.chatColors("&7For now, edit the settings.yml file directly"))
        player.sendMessage(MSG.chatColors("&7File: &fplugins/LocoPillars/maps/$mapName/settings.yml"))
    }
    
    private fun handleValidate(player: Player, args: Array<out String>) {
        if (args.size < 2) {
            player.sendMessage(MSG.chatColors("&cUsage: /pillars admin map validate <name>"))
            return
        }
        
        val mapName = args[1]
        
        if (!mapIOManager.mapExists(mapName)) {
            player.sendMessage(MSG.chatColors("&cMap '$mapName' does not exist!"))
            return
        }
        
        val validation = mapIOManager.validateMap(mapName)
        
        player.sendMessage(MSG.chatColors("&6=== Map Validation: $mapName ==="))
        
        if (validation.isValid) {
            player.sendMessage(MSG.chatColors("&a✓ Map is valid!"))
        } else {
            player.sendMessage(MSG.chatColors("&c✗ Map has errors:"))
            validation.errors.forEach { error ->
                player.sendMessage(MSG.chatColors("  &c- $error"))
            }
        }
        
        if (validation.warnings.isNotEmpty()) {
            player.sendMessage(MSG.chatColors("&eWarnings:"))
            validation.warnings.forEach { warning ->
                player.sendMessage(MSG.chatColors("  &e- $warning"))
            }
        }
    }
    
    private fun showHelp(player: Player) {
        player.sendMessage(MSG.chatColors("&6=== Map Admin Commands ==="))
        player.sendMessage(MSG.chatColors("&e/pillars admin map create <name> &7- Create new map"))
        player.sendMessage(MSG.chatColors("&e/pillars admin map edit <name> &7- Start editing map"))
        player.sendMessage(MSG.chatColors("&e/pillars admin map delete <name> &7- Delete map"))
        player.sendMessage(MSG.chatColors("&e/pillars admin map list &7- List all maps"))
        player.sendMessage(MSG.chatColors("&e/pillars admin map save &7- Save current session"))
        player.sendMessage(MSG.chatColors("&e/pillars admin map cancel &7- Cancel current session"))
        player.sendMessage(MSG.chatColors("&e/pillars admin map resize <radius> &7- Adjust save region"))
        player.sendMessage(MSG.chatColors("&e/pillars admin map info &7- Show session info"))
        player.sendMessage(MSG.chatColors("&e/pillars admin map validate <name> &7- Validate map"))
    }
    
    private fun isValidMapName(name: String): Boolean {
        return name.matches(Regex("^[a-zA-Z0-9_-]+$"))
    }
    
    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String>? {
        if (sender !is Player || !sender.hasPermission("locopillars.admin")) {
            return emptyList()
        }
        
        return when (args.size) {
            1 -> listOf("create", "edit", "delete", "list", "save", "cancel", "resize", "info", "settings", "validate")
                .filter { it.startsWith(args[0].lowercase()) }
            
            2 -> {
                when (args[0].lowercase()) {
                    "edit", "delete", "settings", "validate" -> {
                        // Suggest map names
                        mapIOManager.getAllMapNames()
                            .filter { it.lowercase().startsWith(args[1].lowercase()) }
                    }
                    "resize" -> {
                        // Suggest radius values
                        listOf("25", "50", "75", "100", "150", "200")
                    }
                    else -> emptyList()
                }
            }
            
            else -> emptyList()
        }
    }
}
