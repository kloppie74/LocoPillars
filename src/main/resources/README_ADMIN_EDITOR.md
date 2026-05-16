# Admin-Editor System

This document describes the **Admin-Editor System** added to LocoPillars.
It lives alongside (and complements) the existing `/pillars admin map` commands.

---

## Overview

The Admin-Editor System provides admins with a lightweight **edit mode** that
gives them a special hotbar and a suite of `/editor` commands to manage maps,
spawn points and files without having to open a full map-editing world session.

### Architecture (new files)

| File | Purpose |
|------|---------|
| `editor/model/EditModeState.kt`            | Data class for per-player edit mode state |
| `editor/model/SpawnPointData.kt`           | Data class for a single spawn point entry |
| `editor/model/MapFileInfo.kt`              | Snapshot of map file existence/sizes      |
| `editor/AdminEditorManager.kt`             | Central registry – tracks active edit mode |
| `editor/EditorHotbarManager.kt`            | Gives/removes editor tool items from hotbar|
| `editor/SpawnPointEditor.kt`               | CRUD for spawn points in `settings.yml`   |
| `editor/GameFileService.kt`                | Reads raw file metadata (yml/schem)       |
| `editor/EditorFileValidator.kt`            | Validates map files, reports errors       |
| `editor/listener/EditorModeListener.kt`    | Cleans up state on player disconnect      |
| `editor/listener/EditorInteractionListener.kt` | Handles hotbar tool right-click events|
| `commands/editor/AdminEditorCommand.kt`    | `/editor` command executor + tab-complete |

---

## How Edit Mode Works

1. Admin runs `/editor enable [mapName]`
2. `AdminEditorManager` records an `EditModeState` for the player and snapshots
   their existing hotbar.
3. `EditorHotbarManager` replaces slots 0–8 with editor tools.
4. The admin uses the hotbar items or `/editor` sub-commands to interact with
   maps.
5. Admin runs `/editor disable` (or right-clicks the **Barrier** in slot 8).
6. Original hotbar is restored; state is removed.

---

## Commands

All commands require the `locopillars.admin` permission.

```
/editor enable [map]            Enable edit mode, optionally focus a map
/editor disable                 Disable edit mode and restore hotbar
/editor toggle [map]            Toggle edit mode on/off
/editor map <name|clear>        Select (or clear) the active map
/editor hotbar <on|off|toggle>  Toggle editor hotbar tools without leaving edit mode
/editor info                    Show current session info
/editor setup                   Auto-bootstrap Data/Games and Data/Schems assets
/editor validate [map]          Validate one or all maps
/editor files [map]             Show file status for one or all maps
/editor spawn list              List all spawn points for the active map
/editor spawn set <team> <slot> Set a spawn point at your current location
/editor spawn remove <team> <slot>  Remove a spawn point
/editor holograms <on|off|refresh|status> Manage spawn holograms in edit session
/editor help                    Show help
```

Also still available:
```
/pillars admin map create <name>    Create a new map
/pillars admin map edit <name>      Open full schematic edit session
/pillars admin map save             Save and close edit session
/pillars admin map cancel           Cancel edit session
/pillars admin map validate <name>  Validate map
/pillars admin map list             List all maps
```

---

## Editor Hotbar Layout

| Slot | Item             | Action                                       |
|------|------------------|----------------------------------------------|
| 0    | Position Tool    | Select schematic region (existing tool)      |
| 1    | Spawn Setter     | Right-click block → set Team1/Player1 spawn  |
| 2    | Schematic Tool   | Shows schematic save/load commands           |
| 3    | Validation Tool  | Right-click → runs file validation report    |
| 4    | Config Editor    | Right-click → opens map settings GUI         |
| 5    | Border Visualizer| Informational (particles via edit session)   |
| 6–7  | (empty)          | Reserved for future tools                   |
| 8    | Exit Edit Mode   | Right-click → disables edit mode             |

---

## File Locations

| Path                                         | Purpose                               |
|----------------------------------------------|---------------------------------------|
| `plugins/LocoPillars/maps/<name>/settings.yml` | Map metadata + spawn points         |
| `plugins/LocoPillars/maps/<name>/region.schem` | WorldEdit schematic of the map      |
| `plugins/LocoPillars/Data/Games/<name>.yml`    | Game-type configuration             |
| `plugins/LocoPillars/Data/Schems/<name>.schem` | Legacy per-game-type schematic      |
| `plugins/LocoPillars/maps/templates/`          | Default templates for new maps      |

---

## Permissions

| Permission             | Default | Description                            |
|------------------------|---------|----------------------------------------|
| `locopillars.admin`    | op      | All admin commands including `/editor` |
| `locopillars.editor`   | op      | Explicit permission for `/editor`      |

---

## Adding New Custom File Types / Handlers

1. Create a new service class in `src/.../editor/` (e.g. `CustomFileService.kt`)
2. Inject `plugin: JavaPlugin` and `configManager: ConfigManager`
3. Register it as a `lateinit var` in `LocoPillars.kt` and initialise it in `onEnable`
4. Optionally add a new sub-command in `AdminEditorCommand.kt`
5. Document the new file path pattern in this README

---

## Manual Testing Checklist

- [ ] `/editor enable` gives hotbar and prints activation message
- [ ] `/editor hotbar off` hides custom editor hotbar while keeping edit mode active
- [ ] `/editor hotbar on` restores custom editor hotbar
- [ ] `/editor disable` restores original hotbar
- [ ] `/editor map <name>` sets the active map
- [ ] `/editor edit` starts editing the currently active map without requiring map argument
- [ ] `/editor setup` creates/migrates Data/Games and Data/Schems assets
- [ ] `/editor validate <name>` prints an accurate report
- [ ] `/editor files` shows yml/schem status for all maps
- [ ] `/editor spawn list` lists spawn points from settings.yml
- [ ] `/editor holograms off` hides spawn holograms in the edit world
- [ ] `/editor holograms on` restores spawn holograms
- [ ] `/editor spawn set Team1 Player1` saves your location as a spawn point
- [ ] Right-clicking the Spawn Setter (name tag) saves Team1/Player1 spawn
- [ ] Right-clicking the Validation Tool triggers a validate report
- [ ] Right-clicking the Exit item disables edit mode
- [ ] Disconnecting while in edit mode cleans up state (no dangling session)
- [ ] `/pillars admin map edit <name>` still works alongside the new system
