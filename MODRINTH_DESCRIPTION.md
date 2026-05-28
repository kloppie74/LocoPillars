<div align="center">

# LocoPillars — Pillars of Fortune

**The ultimate Pillars of Fortune minigame plugin for Minecraft 1.21+**

Build high. Fight smart. Survive longer than everyone else.

[![Modrinth](https://img.shields.io/modrinth/v/locopillars?label=Latest%20Version&color=00AF5C&logo=modrinth)](https://modrinth.com/plugin/locopillars)
[![Modrinth Downloads](https://img.shields.io/modrinth/dt/locopillars?color=00AF5C&logo=modrinth)](https://modrinth.com/plugin/locopillars)
[![MC Version](https://img.shields.io/badge/Minecraft-1.21%2B-brightgreen)](https://modrinth.com/plugin/locopillars)
[![License](https://img.shields.io/badge/License-ARR-red)](https://modrinth.com/plugin/locopillars)

</div>

<br>

## What is LocoPillars?

**LocoPillars** is a feature-complete, production-ready **Pillars of Fortune** server plugin. Players spawn in isolated cages, get released into the arena, and compete to build the tallest pillar while fighting off every other player. Last one standing wins.

Under the hood it runs **multi-instance arena management**, **fully scriptable LuckyBlocks**, a **drag-and-drop cosmetics shop**, **27+ achievements**, a **per-player stats database**, **100+ PlaceholderAPI placeholders**, and an **in-game map editor**. All configurable without touching a single line of code.

<br>

## Feature Overview

| Category | Highlights |
|---|---|
| **Gameplay** | Solo & team modes · Multi-instance arenas · Cage countdown |
| **LuckyBlocks** | 50 pre-designed effects · Fully scriptable via Kotlin .kts |
| **Cosmetics** | 64 pre-designed cosmetics · Kill / Death / Win effects · Custom cages |
| **Achievements** | 27+ achievements · 4 difficulty tiers · Reward system |
| **Statistics** | Full stats DB · Leaderboards · Streaks · Playtime · Multi-kills |
| **Voting Events** | Coming soon, fully scriptable via modules |
| **Chat** | Context-aware formats · Team chat · Social spy · Profanity filter |
| **Editor** | In-game map editor · WorldEdit/FAWE schematics · Validation |
| **Scoreboards** | 5 dynamic scoreboard types · Live team status · Timers |
| **PlaceholderAPI** | 100+ placeholders covering stats, teams, maps, achievements |
| **Database** | H2 (embedded) · MySQL · MariaDB · PostgreSQL · HikariCP |

<br>

## Gameplay

Players drop into cages at the start of each round. After the countdown, cages open and everyone scrambles to build a pillar and fight for survival. The last team (or player) alive wins.

### Multi-Instance Arenas
Run multiple simultaneous games on the same server, each in its own isolated world. Set a number in `config.yml` and LocoPillars handles the rest.

### Team Support
- Configurable players-per-team and number of teams
- Team-colored leather armor
- Team chat (`/teamchat`)
- Team status on scoreboard & TAB list

<br>

## LuckyBlocks

LuckyBlocks are fully scriptable. Each block is its own folder containing a `Settings.json` for metadata and a `Luckyblock.kts` for the effect. The plugin ships with **50 pre-designed LuckyBlocks**. Drop a new folder into `plugins/LocoPillars/Luckyblocks/` and it gets picked up automatically with no restart needed.

**Settings.json**
```json
{
  "id": "firework_frenzy",
  "display_name": "Firework Frenzy",
  "description": "Colorful fireworks explode in every direction!",
  "icon": {
    "material": "FIREWORK_ROCKET",
    "custom_model_data": 0
  },
  "block_type": "LUCKY_BLOCK",
  "rarity": "COMMON"
}
```

Rarity options: `COMMON`, `UNCOMMON`, `RARE`, `EPIC`, `LEGENDARY`. Chances per rarity are configured in `config.yml`.

**Luckyblock.kts**

The script receives `player` (who broke the block) and `game` (utility helper). The full Bukkit/Paper API is available.

```kotlin
repeat(5) {
    val offset = player.location.clone().add(
        Math.random() * 4 - 2, 0.0, Math.random() * 4 - 2
    )
    game.spawnFirework(offset)
}
game.playSound(player, org.bukkit.Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1.0f, 1.0f)
game.sendTitle(player, "§6Firework Frenzy!", "§7Celebrate!", 5, 50, 10)
```

<br>

## Cosmetics

Cosmetics work the same way as LuckyBlocks. Each cosmetic is a folder under `plugins/LocoPillars/Cosmetics/<type>/` with a `Settings.json` and a `Cosmetic.kts` script. The plugin ships with **64 pre-designed cosmetics** across four categories. Players buy and equip cosmetics through `/cosmetics` using in-game coins.

| Type | Count | Trigger |
|---|---|---|
| Kill effects | 20 | Triggered when the player gets a kill |
| Death effects | 20 | Triggered when the player is eliminated |
| Win effects | 20 | Triggered when the player's team wins |
| Cages | 4 | The skin used for the spawn cage |

**Settings.json**
```json
{
  "id": "firework_kill",
  "type": "KILL",
  "display_name": "§6Firework Kill",
  "lore": ["§7Launch a firework on every kill!"],
  "price": 300,
  "required_level": 3,
  "permission": "",
  "rarity": "UNCOMMON",
  "active": true,
  "icon": {
    "material": "FIREWORK_ROCKET",
    "custom_model_data": 0
  }
}
```

**Cosmetic.kts**

The script receives `player` (the cosmetic owner, killer for kill effects or the eliminated player for death effects) and `game`.

```kotlin
game.spawnFirework(player.location.add(0.0, 0.5, 0.0))
game.spawnParticles(player.location.add(0.0, 1.0, 0.0), org.bukkit.Particle.FIREWORK, 20, 0.5, 0.5, 0.5, 0.1)
```

<br>

## Voting Events *(Coming Soon)*

Voting events are mid-game modifiers that players vote on during a game. This system uses the same module-based approach as LuckyBlocks and Cosmetics. Drop a folder into `plugins/LocoPillars/modules/voting/` and it becomes available in-game without a restart.

Each voting event consists of two files:

**settings.json**
```json
{
  "id": "no_fall_mode",
  "display_name": "No Fall Mode",
  "description": "Fall damage is completely disabled for everyone in this round.",
  "icon": {
    "material": "FEATHER",
    "custom_model_data": 0
  },
  "active": true,
  "vote_weight": 1,
  "duration": -1,
  "announce": {
    "title": "§6§lNo Fall Mode!",
    "subtitle": "§7Fall damage is disabled!",
    "chat": "§6[Event] §eNo Fall Mode is now active!"
  }
}
```

**script.kts**

The script receives `game` and `players`. Use `game.registerEventListener` to hook into Bukkit events. The listener is automatically cleaned up when the game ends.

```kotlin
game.sendTitle("§6§lNo Fall Mode!", "§7Fall damage is disabled!", 10, 60, 10)
game.broadcast("§6[Event] §eNo Fall Mode is now active!")

game.getPlayers().forEach { player ->
    player.playSound(player.location, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)
}

game.registerEventListener<org.bukkit.event.entity.EntityDamageEvent> { event ->
    val entity = event.entity
    if (entity is org.bukkit.entity.Player &&
        game.isPlayerInGame(entity) &&
        event.cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL
    ) {
        event.isCancelled = true
    }
}
```

<br>

## Achievements

27+ built-in achievements across 4 difficulty tiers, each with configurable XP and coin rewards. Add your own by dropping a `.yml` file in `plugins/LocoPillars/Achievements/`.

| Difficulty | Examples |
|---|---|
| Easy | `first_win` · `builder_basics` · `explorer` |
| Normal | `warrior` · `demolisher` · `team_player` |
| Hard | `champion` · `survivor` · `multi_killer` |
| Extreme | `legendary` · `perfect_game` · `comeback_king` |

<br>

## Commands

### Player Commands

| Command | Aliases | Description |
|---|---|---|
| `/pillars join <type\|random>` | `/pof`, `/games` | Join a game or a random available game |
| `/pillars leave` | | Leave your current game |
| `/pillars list` | | List available game types |
| `/pillars status` | | View current game status |
| `/pillars stats` | | View server-wide statistics |
| `/pillars help` | | Show help overview |
| `/spawn` | `/hub`, `/lobby` | Teleport to the lobby |
| `/stats` | `/mystats` | View personal statistics |
| `/achievements` | `/ach` | Open achievements overview |
| `/cosmetics` | `/cosm` | Browse and equip cosmetics |
| `/luckyblocks` | `/luckyb` | Open the LuckyBlocks menu |
| `/teamchat` | `/tc` | Toggle team chat mode |
| `/hideme` | `/hm` | Hide yourself from other players |
| `/hideothers` | `/ho` | Hide all other players from your view |
| `/showothers <player>` | `/so` | Show a specific player while hideothers is on |

### Admin Commands

| Command | Aliases | Description |
|---|---|---|
| `/editor toggle` | `/adminedit` | Enter / exit admin editor mode |
| `/editor create <name>` | | Create a new arena map |
| `/editor edit <name>` | | Edit an existing arena |
| `/editor delete <name>` | | Delete an arena |
| `/editor list` | | List all arenas |
| `/editor save` | | Save current editor session |
| `/editor cancel` | | Cancel without saving |
| `/editor spawn` | | Manage spawn / cage positions |
| `/editor validate` | | Validate arena configuration |
| `/editor settings` | | Open editor settings GUI |
| `/editor hotbar` | | Open the editor hotbar tool manager |
| `/editor files` | | Manage arena schematic files |
| `/editor doctor` | | Run auto-diagnostics on arena |
| `/editor setup` | | Quick-setup wizard |
| `/editor resize` | | Resize a schematic |
| `/editor info` | | Show arena info |
| `/setspawn` | | Set the lobby spawn point |
| `/vanish [player]` | `/v` | Toggle vanish for yourself or another player |
| `/chatadmin reload` | `/ca reload` | Reload chat configuration |
| `/chatadmin announce <msg>` | `/ca announce` | Send a server announcement |
| `/chatadmin mute <player>` | `/ca mute` | Mute a player |
| `/chatadmin unmute <player>` | `/ca unmute` | Unmute a player |
| `/chatadmin clear` | `/ca clear` | Clear chat for all players |
| `/chatadmin stats` | `/ca stats` | View chat statistics |
| `/chatadmin cooldown <player> <s>` | | Set a player chat cooldown |
| `/socialspy` | `/spy`, `/ss` | Toggle social spy |
| `/cosmetics give <player> <id>` | | Give a cosmetic to a player |
| `/cosmetics coins <player> <amount>` | | Modify a player coin balance |
| `/cosmetics reload` | | Reload cosmetics configuration |
| `/luckyblocks getluckyblock` | | Give yourself a LuckyBlock item |
| `/memory check\|clear\|status` | `/mem` | View / manage plugin memory usage |
| `/tab reload` | | Reload TAB plugin config |

<br>

## Admin Editor (`/editor`)

LocoPillars ships with a full in-game map editor, no external tools needed.

1. Run `/editor setup` for the guided quick-setup wizard.
2. Use `/editor create <name>` to start a new arena.
3. Place cage spawn points with the hotbar tools (`/editor hotbar`).
4. Import WorldEdit / FAWE schematics and attach them to the arena.
5. Run `/editor validate` to catch configuration errors.
6. Save with `/editor save` and the arena is immediately available in-game.

Supports **multiple schematics per arena**, schematic resizing, and live hotbar-based spawn-point editing, all without leaving the game.

<br>

## Scoreboards

Five dynamic scoreboard layouts, each showing context-relevant information:

| Scoreboard | Shown When | Key Info |
|---|---|---|
| Lobby | Player is in the lobby | Rank · Coins · Level · XP · Achievements · Online count |
| Game Lobby | Waiting for a game to start | Game name · Map · Player count · Min players |
| Cage | Countdown before game starts | Start countdown · Game info |
| Active Game | During a live game | Live timer · Item drop timer · Team status |
| Spectate | Spectating a game | Live timer · Item timer · All team statuses |

<br>

## PlaceholderAPI

Requires **PlaceholderAPI**. All placeholders use the `%locopillars_` prefix.

<details>
<summary>Player Statistics</summary>

| Placeholder | Returns |
|---|---|
| `%locopillars_db_player_name%` | Player name |
| `%locopillars_db_display_name%` | Display name |
| `%locopillars_db_games_played%` | Total games played |
| `%locopillars_db_games_won%` | Total wins |
| `%locopillars_db_games_lost%` | Total losses |
| `%locopillars_db_games_quit%` | Total quits |
| `%locopillars_db_total_kills%` | Total kills |
| `%locopillars_db_total_deaths%` | Total deaths |
| `%locopillars_db_total_assists%` | Total assists |
| `%locopillars_db_best_killstreak%` | Best kill streak |
| `%locopillars_db_current_killstreak%` | Current kill streak |
| `%locopillars_db_double_kills%` | Double kills |
| `%locopillars_db_triple_kills%` | Triple kills |
| `%locopillars_db_quad_kills%` | Quad kills |
| `%locopillars_db_current_win_streak%` | Current win streak |
| `%locopillars_db_best_win_streak%` | Best win streak |
| `%locopillars_db_current_loss_streak%` | Current loss streak |
| `%locopillars_db_worst_loss_streak%` | Worst loss streak |
| `%locopillars_db_damage_dealt%` | Total damage dealt |
| `%locopillars_db_damage_taken%` | Total damage taken |
| `%locopillars_db_fall_damage_taken%` | Total fall damage taken |
| `%locopillars_db_pillars_destroyed%` | Pillars destroyed |
| `%locopillars_db_blocks_broken%` | Blocks broken |
| `%locopillars_db_blocks_placed%` | Blocks placed |
| `%locopillars_db_total_distance_moved%` | Total blocks walked |
| `%locopillars_db_total_playtime%` | Total playtime in seconds |
| `%locopillars_db_level%` | Player level |
| `%locopillars_db_experience%` | Player XP |
| `%locopillars_db_coins%` | Cosmetic coins |
| `%locopillars_db_global_rank%` | Global leaderboard rank |
| `%locopillars_db_perfect_games%` | Perfect games (no deaths) |
| `%locopillars_db_comeback_wins%` | Comeback victories |

</details>

<details>
<summary>Active Game</summary>

| Placeholder | Returns |
|---|---|
| `%locopillars_game_name%` | Name of the current game |
| `%locopillars_game_state%` | State: `WAITING` / `STARTING` / `ACTIVE` / `ENDED` / `RESETTING` |
| `%locopillars_game_map%` | Map file name |
| `%locopillars_game_map_name%` | Map display name |
| `%locopillars_game_world%` | World name of the game instance |
| `%locopillars_game_players_in_game%` | Current player count |
| `%locopillars_game_players_max%` | Maximum players |
| `%locopillars_game_players_min%` | Minimum players to start |
| `%locopillars_game_timer%` | Game timer in seconds |
| `%locopillars_game_timer_string%` | Game timer as MM:SS |
| `%locopillars_game_lobby_timer%` | Lobby countdown in seconds |
| `%locopillars_game_items_timer%` | Seconds until next item drop |
| `%locopillars_active_games%` | Total active games on the server |

</details>

<details>
<summary>Teams</summary>

| Placeholder | Returns |
|---|---|
| `%locopillars_team_name%` | The player's team name |
| `%locopillars_team_color%` | The player's team color |
| `%locopillars_team_size%` | The player's team size |
| `%locopillars_teams_total%` | Total number of teams in the game |
| `%locopillars_teams_names%` | All team names, comma-separated |
| `%locopillars_teams_colors%` | All team colors, comma-separated |
| `%locopillars_teams_sizes%` | All team sizes, comma-separated |

Replace `team1` with `team1`, `team2`, `team3`, etc.:

| Placeholder | Returns |
|---|---|
| `%locopillars_game_team_team1_displayname%` | Team display name |
| `%locopillars_game_team_team1_status_symbol%` | Status: alive / eliminated / warning / empty |
| `%locopillars_game_team_team1_players_alive%` | Players still alive in the team |
| `%locopillars_game_team_team1_players_total%` | Total team players |
| `%locopillars_game_team_team1_players_dead%` | Eliminated team players |

</details>

<details>
<summary>Maps & Server</summary>

Replace `<mapname>` with the map's folder name (e.g. `arena1`):

| Placeholder | Returns |
|---|---|
| `%locopillars_map_<mapname>_players_in_lobby%` | Players in lobby for this map |
| `%locopillars_map_<mapname>_players_playing%` | Players currently in a game on this map |
| `%locopillars_map_<mapname>_players_total%` | Total players (lobby + in-game) for this map |
| `%locopillars_map_<mapname>_games_active%` | Active game instances on this map |
| `%locopillars_map_<mapname>_games_waiting%` | Waiting game instances |
| `%locopillars_map_<mapname>_games_starting%` | Starting game instances |
| `%locopillars_map_<mapname>_games_total%` | Total instances for this map |
| `%locopillars_online_players%` | Total online players |
| `%locopillars_player_name%` | Shorthand for the player's name |

</details>

<details>
<summary>Achievements</summary>

| Placeholder | Returns |
|---|---|
| `%locopillars_achievement_total_count%` | Total achievements available |
| `%locopillars_achievement_total_completed_amount%` | Achievements unlocked by this player |
| `%locopillars_achievement_total_completed_percent%` | Completion percentage |

Replace `<id>` with the achievement ID (e.g. `first_win`):

| Placeholder | Returns |
|---|---|
| `%locopillars_achievement_<id>_completed%` | `1` if unlocked, `0` if not |
| `%locopillars_achievement_<id>_progress%` | Raw progress value |
| `%locopillars_achievement_<id>_percent%` | Progress as a percentage |
| `%locopillars_achievement_<id>_name%` | Achievement name |
| `%locopillars_achievement_<id>_description%` | Achievement description |
| `%locopillars_achievement_<id>_difficulty%` | `EASY` / `NORMAL` / `HARD` / `EXTREME` |
| `%locopillars_achievement_<id>_category%` | Achievement category |
| `%locopillars_achievement_<id>_type%` | Achievement type |
| `%locopillars_achievement_<category>_category_status%` | Percentage of a category completed |
| `%locopillars_achievement_<difficulty>_difficulty_status%` | Percentage of a difficulty tier completed |

</details>

<br>

## Requirements & Dependencies

| Dependency | Type | Notes |
|---|---|---|
| Paper | Server | 1.21+ |
| WorldEdit or FastAsyncWorldEdit | Required | Auto-detected at startup |
| PlaceholderAPI | Required | 2.11+ |
| TAB | Optional | 5.2+, enhanced player list |
| ProtocolLib | Optional | 5.4+, tab visibility management |
| Vault | Optional | 1.7+, economy reward integration |
| ItemsAdder | Optional | 3.6+, custom cosmetic item textures |

<br>

## Permissions

### Players (default: true)

| Permission | Description |
|---|---|
| `locopillars.pillars.*` | All `/pillars` subcommands |
| `locoPillars.play` | Join and play games |
| `locopillars.spawn` | Teleport to lobby |
| `locopillars.chat.use` | Send chat messages |
| `locopillars.chat.teamchat` | Use team chat |
| `locopillars.hideme` | Hide yourself from others |
| `locopillars.hideothers` | Hide other players |
| `locopillars.showothers` | Show a specific player |
| `locopillars.achievements.use` | Use achievement commands |
| `locopillars.luckyblocks.menu` | Open the LuckyBlocks menu |

### Staff (default: op)

| Permission | Description |
|---|---|
| `locopillars.admin` | All administrative access |
| `locopillars.editor` | Use `/editor` and all map tools |
| `locopillars.setspawn` | Set the lobby spawn location |
| `locopillars.spawn.others` | Teleport other players to spawn |
| `locopillars.vanish` | Toggle vanish mode |
| `locopillars.memory.use` | Memory management commands |
| `locopillars.chat.admin` | All chat administration |
| `locopillars.chat.socialspy` | View all player chat |
| `locopillars.chat.bypass-cooldown` | Skip chat cooldown |
| `locopillars.chat.bypass-filter` | Skip profanity filter |
| `locopillars.achievements.admin` | Administer achievements |
| `locopillars.luckyblocks.getluckyblock` | Get a LuckyBlock via command |
| `locopillars.notify.update` | Receive in-game update notifications |
| `locopillars.tab` | Reload TAB configuration |

<br>

## Database

Set in `config.yml`, no code changes needed.

| Backend | Notes |
|---|---|
| H2 | Default, embedded, zero setup |
| MySQL | Recommended for production / network servers |
| MariaDB | Fully supported |
| PostgreSQL | Fully supported |

All backends use HikariCP connection pooling.

<br>

## Configuration

```
plugins/LocoPillars/
├── config.yml              Main settings (database, arenas, chat, scoreboard, TAB)
├── language.yml            All player-facing messages
├── items.yml               GUI item definitions
├── Games/
│   └── <GameType>.yml      Per-game-type config (timers, build limits, rewards, teams)
├── Achievements/
│   └── <id>.yml            One file per achievement
├── Cosmetics/
│   ├── cages/<name>/       Settings.json
│   ├── kill/<name>/        Settings.json + Cosmetic.kts
│   ├── death/<name>/       Settings.json + Cosmetic.kts
│   └── win/<name>/         Settings.json + Cosmetic.kts
├── Luckyblocks/
│   └── <name>/             Settings.json + Luckyblock.kts
└── modules/
    └── voting/
        └── <name>/         settings.json + script.kts
```

<br>

## Automatic Update Checker

LocoPillars checks Modrinth on startup for new releases. Staff with the `locopillars.notify.update` permission receive an in-game notification when an update is available. Can be disabled in `config.yml`.

<br>

<div align="center">

Made by **kloppie74**

</div>
