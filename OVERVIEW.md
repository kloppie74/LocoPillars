# LocoPillars — Plugin Overview

A competitive Minecraft minigame plugin built on Paper 1.21. Players build pillars, fight for survival, and compete in configurable arenas with full rewards, editor tooling, and team support.

---

## Table of Contents

1. [Features at a Glance](#features-at-a-glance)
2. [How the Game Works](#how-the-game-works)
3. [Game Configuration (per arena)](#game-configuration-per-arena)
4. [Rewards System](#rewards-system)
5. [Editor System](#editor-system)
6. [Multi-Schematic Support](#multi-schematic-support)
7. [FAWE Support](#fawe-support)
8. [Database](#database)
9. [Language Customization](#language-customization)
10. [Commands Reference](#commands-reference)
11. [Permissions](#permissions)
12. [Dependencies](#dependencies)
13. [File Structure](#file-structure)

---

## Features at a Glance

- **Multi-instance arenas** — multiple simultaneous games, each in an isolated world copy
- **Solo & team modes** — configurable players-per-team, any number of teams
- **Two join modes** — lobby waiting area OR direct-to-cage (configurable per game type)
- **Cage system** — players start locked in cages with a configurable countdown before opening
- **Item distribution** — random item drops on a configurable timer
- **Full rewards system** — per-event messages & console commands with placeholders
- **Voting events** — Floor Is Lava, Elytra Mode, No-Fall Mode, 1-Heart Mode, and more
- **In-game scoreboard & TAB integration** — team colors, live timer, player count
- **Editor tooling** — create, edit, and save maps in-game with WorldEdit/FAWE schematics
- **Multi-schematic maps** — store multiple numbered schematics per map
- **Achievement & stats system** — kill streaks, win streaks, fastest win, etc.
- **Database backend** — H2 (embedded), MySQL, or MariaDB
- **PAPI support** — PlaceholderAPI integration
- **Vault integration** — economy support for command rewards
- **ItemsAdder integration** — optional custom items support
- **Language file** — all player-facing messages in one editable `language.yml`

---

## How the Game Works

### Flow: Lobby Enabled (default)

```
Player joins (/pillars join <type>)
   │
   ▼
Teleported to shared waiting area (SpawnPoint)
   │
   ▼  (minPlayers reached)
Lobby countdown (LobbyTimer seconds)
   │
   ▼
startGame():
  - Spawn area cleared
  - Players assigned to teams → teleported to individual cage positions
  - Team armor applied (colored leather)
  - Cage placed around each player, slowness + jump boost applied
   │
   ▼  (CageTimer countdown)
Cages removed → PvP enabled → Item timer starts
   │
   ▼  (last player/team remaining OR GameTimer expires)
endGame() → rewards distributed → arena resets
```

### Flow: Lobby Disabled (`LobbyEnabled: false`)

```
Player joins (/pillars join <type>)
   │
   ▼
Immediately assigned to next available team slot
   - Teleported to cage position
   - Team armor applied
   - Cage placed, slowness + jump boost applied
   │
   ▼  (minPlayers reached)
startGame() called directly — no lobby countdown
   │
   ▼  (CageTimer countdown)
Cages removed → PvP enabled → Item timer starts
   │
   ▼  (win condition met OR timer expires)
endGame() → rewards distributed → arena resets
```

**Key difference:** With lobby disabled, players are placed in their cage the moment they join. There is no shared waiting lobby. The game starts as soon as `MinimumPlayers` is reached.

---

## Game Configuration (per arena)

Each arena has a config file in `Data/Games/<name>.yml`.

### Settings block

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| `LobbyEnabled` | boolean | `true` | `true` = shared lobby; `false` = direct-to-cage on join |
| `LobbyTimer` | int | `10` | Seconds of countdown before game starts (lobby mode only) |
| `CageTimer` | int | `5` | Seconds after game start before cages open |
| `GameTimer` | int | `900` | Max game duration in seconds |
| `ItemTimer` | int | `5` | Seconds between random item drops |
| `BuildHeight` | int | `60` | Max Y height players can build |
| `BuildRange` | int | `40` | Horizontal radius players can build within |
| `DeathHeight` | int | `-5` | Y level at which a player is considered dead |
| `Can-Break-Map-Blocks` | boolean | `false` | Allow breaking blocks that were in the original map |
| `Can-BlowUp-Map-Blocks` | boolean | `false` | Allow explosions (TNT, creepers, …) to destroy original map blocks |
| `Breakable-Blocks` | list | `[]` | Origin-relative `x,y,z` map blocks players may break even when Can-Break is off (set via the Breakable Selector, editor hotbar slot 7) |
| `SpawnPoint` | X/Y/Z | — | Center point of the arena (also used as lobby spawn) |
| `Spawn-Area` | Loc1/Loc2 | — | Bounding box for the shared lobby waiting area |
| `DisplayName` | string | file name | Friendly name shown in chat |
| `LoadMap` | boolean | `true` | Whether to paste the map schematic on arena setup |

### SpawnLocations block

| Key | Description |
|-----|-------------|
| `MaximumPlayers` | Max players in one instance |
| `MinimumPlayers` | Min players needed to start |
| `MaxTeams` | Number of teams |
| `PlayersPerTeam` | Players per team |
| `Team1..N` | Team definitions: Name, Color, Player1..N spawn coords |

---

## Rewards System

Rewards are defined under the `Rewards:` key in each game config. Every event section has `messages:` and `commands:` lists.

### Available placeholders

| Placeholder | Available in |
|-------------|-------------|
| `%player%` | All sections (the recipient of the reward) |
| `%killer%` | OnKillRewards |
| `%death%` | OnKillRewards |
| `%leftplayer%` | OnLeaveRewards |
| `%winnerteamname%` | OnEndGameRewards |

### Structure

```yaml
Rewards:
  OnStartGameRewards:
    enabled: true
    messages:
      - "&aWelcome to the game, %player%!"
    commands: []

  OnKillRewards:
    enabled: true
    killed_by_player:
      killer:
        messages:
          - "&aYou eliminated %death%!"
        commands:
          - "eco give %player% 10"
      killed_player:
        messages:
          - "&cYou were eliminated by %killer%!"
        commands: []
      remaining_players:
        messages:
          - "&e%killer% eliminated %death%!"
        commands: []

  OnLeaveRewards:
    enabled: true
    left_player:
      messages:
        - "&7You left the game."
      commands: []
    remaining_players:
      messages:
        - "&e%leftplayer% has left the game."
      commands: []

  OnEndGameRewards:
    enabled: true
    winners:
      messages:
        - "&6Congratulations %player%, you won!"
      commands:
        - "eco give %player% 50"
    spectators:
      messages:
        - "&7Better luck next time, %player%."
      commands: []
```

Commands are run as console. Messages support `&` color codes and hex colors (`&#RRGGBB`).

---

## Editor System

The editor lets admins create and modify game maps in-game without external tools.

### Editor workflow

1. **Create a map slot:** `/editor create <mapname>`
2. **Open an editor session:** `/editor select <mapname>`
   - A temporary void world is created
   - The existing schematic (if any) is pasted into it
   - You are teleported there in Creative mode
3. **Edit the world freely** using any blocks
4. **Save your work:** `/editor save` — saves the region back as a schematic
5. **Cancel without saving:** `/editor cancel`
6. **Resize the save region:** `/editor resize <radius>` — how far from center to save
7. **View session info:** `/editor info`

### Schematic management

Save multiple numbered schematics for the same map:

```
/editor schematic <mapname>          — list all saved schematics
/editor schematic <mapname> <id>     — save current session as schematic #<id>
```

Schematics are stored in `maps/<mapname>/schematics/1.schem`, `2.schem`, etc.

### Reward editing

Edit per-map reward messages and commands directly in-game:

```
/editor rewards <mapname> list
/editor rewards <mapname> add-message <target> <text>
/editor rewards <mapname> add-command <target> <command>
/editor rewards <mapname> clear <target>
```

**Targets:** `start`, `kill.killer`, `kill.victim`, `kill.remaining`, `leave.player`, `leave.remaining`, `end.winner`, `end.spectator`

---

## Multi-Schematic Support

Each map can have multiple numbered schematics stored under `maps/<mapname>/schematics/`.

When running `/editor schematic <map> 1`, the current editor session region is saved as `1.schem`. You can save as many numbered schematics as you want (`1`, `2`, `3`, …).

On arena setup (`runSetupForMap`), all numbered schematics are mirrored to `Data/Schems/<mapname>/`. The arena world then loads the appropriate schematic when creating a game instance.

Legacy maps with a single `region.schem` continue to work — the system falls back to the legacy file if no numbered schematics exist.

---

## FAWE Support

FastAsyncWorldEdit can be used for significantly faster schematic loading and pasting, especially on large maps.

Configure in `config.yml`:

```yaml
worldedit:
  # AUTO  — use FAWE if installed, otherwise WorldEdit
  # FAWE  — require FAWE (error if not installed)
  # WORLDEDIT — always use regular WorldEdit
  provider: AUTO
```

Both WorldEdit 7.x and FAWE 2.x are compileOnly dependencies, detected at runtime.

---

## Database

LocoPillars stores player stats, arena stats, and session data in a relational database.

### Supported backends

| Backend | Default | Notes |
|---------|---------|-------|
| H2 | Yes | Embedded, no setup needed, file-based |
| MySQL | No | Set in `config.yml` |
| MariaDB | No | Set in `config.yml` |

### Tracked stats (per player)

- Games played, won, lost, quit
- Kills, deaths, assists
- Damage dealt and taken
- Kill streaks, win streaks, loss streaks
- Double / triple / quad kills
- Fastest win, longest game
- Perfect game, comeback win counts

---

## Language Customization

All player-facing messages live in `plugins/LocoPillars/language.yml`. The file is auto-generated on first run. Edit it freely — changes take effect after `/reload` or plugin restart.

Color codes use `&` (e.g., `&a` = green, `&c` = red). Hex colors use `&#RRGGBB`.

Placeholders use `{key}` syntax (e.g., `{player}`, `{seconds}`, `{team}`).

---

## Commands Reference

### `/pillars` — Player commands

| Command | Description |
|---------|-------------|
| `/pillars join <type>` | Join a specific game type |
| `/pillars join random` | Join any available game |
| `/pillars leave` | Leave the current game |
| `/pillars list` | List all available game types |
| `/pillars status` | Show your current game status |
| `/pillars stats` | Show server-wide statistics |
| `/pillars help` | Show help |

### `/editor` — Admin map editor

| Command | Description |
|---------|-------------|
| `/editor create <map>` | Create a new map slot |
| `/editor select <map>` | Open an editor session for a map |
| `/editor save` | Save current session (writes schematic) |
| `/editor cancel` | Cancel session without saving |
| `/editor resize <radius>` | Set the save region radius |
| `/editor info` | Show current session info |
| `/editor list` | List all maps |
| `/editor schematic <map> [id]` | List or save a numbered schematic |
| `/editor rewards <map> <sub>` | Manage per-map rewards |

---

## Permissions

| Permission | Description |
|------------|-------------|
| `locopillars.play` | Join games (`/pillars join`) |
| `locopillars.leave` | Leave games (`/pillars leave`) |
| `locopillars.admin` | Access admin/editor commands |
| `locopillars.editor` | Use `/editor` commands |

---

## Dependencies

### Required

| Plugin | Version | Notes |
|--------|---------|-------|
| Paper | 1.21+ | Server software |

### Optional (compileOnly — detected at runtime)

| Plugin | Purpose |
|--------|---------|
| WorldEdit 7.3+ | Schematic save/paste |
| FastAsyncWorldEdit 2.x | Faster async schematic operations |
| WorldGuard 7.x | Region protection |
| PlaceholderAPI 2.x | Placeholder expansion |
| TAB 5.x | Scoreboard/TAB list integration |
| ProtocolLib 5.x | Packet-level features |
| Vault | Economy support in rewards |
| ItemsAdder 3.x | Custom items in item pool |

---

## File Structure

```
plugins/LocoPillars/
├── config.yml                  — main plugin config (DB, worldedit provider, debug, etc.)
├── language.yml                — all player-facing messages
│
├── Data/
│   ├── Games/
│   │   ├── solo-garden-8p.yml  — arena config (teams, settings, rewards)
│   │   └── team-garden-4p.yml
│   └── Schems/
│       ├── solo-garden-8p/
│       │   ├── 1.schem         — numbered schematics (multi-schematic)
│       │   └── 2.schem
│       └── team-garden-4p.schem  — legacy single schematic (still supported)
│
├── maps/
│   └── <mapname>/
│       ├── settings.yml        — editor map settings (spawn point, save radius)
│       └── schematics/
│           ├── 1.schem
│           └── 2.schem
│
└── Cages/
    └── Standard-Cage.schem     — cage structure pasted at each player's spawn
```

---

## config.yml overview

```yaml
database:
  type: H2          # H2 | MYSQL | MARIADB
  host: localhost
  port: 3306
  name: locopillars
  username: root
  password: ""

worldedit:
  provider: AUTO    # AUTO | WORLDEDIT | FAWE

debug: false

lobby:
  spawn:
    world: world
    x: 0.0
    y: 64.0
    z: 0.0
```
