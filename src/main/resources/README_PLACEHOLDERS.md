# LocoPillars PlaceholderAPI Placeholders

This plugin provides a wide range of placeholders for the LocoPillars Minecraft minigame via PlaceholderAPI.

## Usage

Use placeholders in your scoreboard configs, GUI, chat, or anywhere PlaceholderAPI is supported.  
Syntax: `%locopillars_<placeholder>%`

---

## **General Game Placeholders**

| Placeholder                       | Returns                                            |
|------------------------------------|----------------------------------------------------|
| `%locopillars_active_games%`       | Number of active games                             |
| `%locopillars_player_name%`        | Player's Minecraft name                            |
| `%locopillars_online_players%`     | Number of online players                           |
| `%locopillars_game_name%`          | Name of the current game instance                  |
| `%locopillars_players%`            | Number of players in current game                  |
| `%locopillars_max_players%`        | Maximum players allowed in the game                |
| `%locopillars_min_players%`        | Minimum players required to start                  |
| `%locopillars_map_name%`           | Name of the current map                            |
| `%locopillars_game_timer%`         | Time left in the current game (HH:MM:SS)           |
| `%locopillars_cage_timer%`         | Time left in the cage phase (HH:MM:SS)             |
| `%locopillars_players_left%`       | Number of players left in the game                 |
| `%locopillars_team_name%`          | Player's team name                                 |
| `%locopillars_team_color%`         | Player's team color                                |
| `%locopillars_team_size%`          | Number of players in player's team                 |
| `%locopillars_teams_total%`        | Total teams in the game                            |
| `%locopillars_teams_names%`        | Comma-separated list of team names                 |
| `%locopillars_teams_colors%`       | Comma-separated list of team colors                |
| `%locopillars_teams_sizes%`        | Comma-separated list of team sizes                 |

---

## **Game State Placeholders**

| Placeholder                       | Returns                                            |
|------------------------------------|----------------------------------------------------|
| `%locopillars_game_state%`         | Game state (WAITING, STARTING, ACTIVE, ENDED, etc) |
| `%locopillars_game_status%`        | Human-readable game status                         |
| `%locopillars_game_map_name%`      | Current map name                                   |
| `%locopillars_game_world%`         | World name                                         |
| `%locopillars_game_origin_x%`      | Arena origin X coordinate                          |
| `%locopillars_game_origin_y%`      | Arena origin Y coordinate                          |
| `%locopillars_game_origin_z%`      | Arena origin Z coordinate                          |
| `%locopillars_game_players_left%`  | Players left in game                               |
| `%locopillars_game_players_max%`   | Max players in game                                |
| `%locopillars_game_players_min%`   | Min players in game                                |
| `%locopillars_game_team%`          | Player's team name                                 |
| `%locopillars_game_team_color%`    | Player's team color                                |
| `%locopillars_game_players_online%`| Online players                                     |
| `%locopillars_game_timer_string%`  | Game timer (HH:MM:SS)                              |
| `%locopillars_game_items_timer%`   | Items timer (HH:MM:SS)                             |
| `%locopillars_game_lobby_timer%`   | Lobby timer (HH:MM:SS)                             |

---

## **Database & Stats Placeholders**

| Placeholder                       | Returns                                            |
|------------------------------------|----------------------------------------------------|
| `%locopillars_db_player_name%`     | Player name                                        |
| `%locopillars_db_player_id%`       | Player's unique database ID                        |
| `%locopillars_db_display_name%`    | Player's display name                              |
| `%locopillars_db_total_sessions%`  | Total login sessions                               |
| `%locopillars_db_first_played%`    | First played date                                  |
| `%locopillars_db_last_seen%`       | Last seen date                                     |
| `%locopillars_db_games_played%`    | Games played                                       |
| `%locopillars_db_games_won%`       | Games won                                          |
| `%locopillars_db_games_lost%`      | Games lost                                         |
| `%locopillars_db_games_quit%`      | Games quit                                         |
| `%locopillars_db_total_kills%`     | Total kills                                        |
| `%locopillars_db_total_deaths%`    | Total deaths                                       |
| `%locopillars_db_total_assists%`   | Total assists                                      |
| `%locopillars_db_best_killstreak%` | Best killstreak                                    |
| `%locopillars_db_current_killstreak%`| Current killstreak                              |
| `%locopillars_db_pillars_destroyed%`| Pillars destroyed                                |
| `%locopillars_db_blocks_broken%`   | Blocks broken                                      |
| `%locopillars_db_blocks_placed%`   | Blocks placed                                      |
| `%locopillars_db_total_distance_moved%`| Total distance moved (meters)                   |
| `%locopillars_db_total_playtime%`  | Total playtime (HH:MM:SS)                          |
| `%locopillars_db_longest_game%`    | Longest game (HH:MM:SS)                            |
| `%locopillars_db_fastest_win%`     | Fastest win (HH:MM:SS)                             |
| `%locopillars_db_average_game_time%`| Average game time (HH:MM:SS)                     |
| `%locopillars_db_current_win_streak%`| Current win streak                              |
| `%locopillars_db_best_win_streak%` | Best win streak                                    |
| `%locopillars_db_current_loss_streak%`| Current loss streak                            |
| `%locopillars_db_worst_loss_streak%`| Worst loss streak                                |
| `%locopillars_db_experience%`      | Player's experience points                         |
| `%locopillars_db_level%`           | Player's level                                     |
| `%locopillars_db_coins%`           | Player's coins                                     |
| `%locopillars_db_damage_dealt%`    | Total damage dealt                                 |
| `%locopillars_db_damage_taken%`    | Total damage taken                                 |
| `%locopillars_db_fall_damage_taken%`| Fall damage taken                                |
| `%locopillars_db_double_kills%`    | Double kills                                       |
| `%locopillars_db_triple_kills%`    | Triple kills                                       |
| `%locopillars_db_quad_kills%`      | Quad kills                                         |
| `%locopillars_db_perfect_games%`   | Perfect games                                      |
| `%locopillars_db_comeback_wins%`   | Comeback wins                                      |
| `%locopillars_db_global_rank%`     | Player's global leaderboard rank                   |

---

## **Achievements Placeholders**

| Placeholder                                                | Returns                                                     |
|------------------------------------------------------------|-------------------------------------------------------------|
| `%locopillars_achievement_total_completed_amount%`         | Number of achievements unlocked                             |
| `%locopillars_achievement_total_completed_percent%`        | Percentage of achievements unlocked                         |
| `%locopillars_achievement_total_count%`                    | Total available achievements                                |
| `%locopillars_achievement_unlocked_ids%`                   | Achievement IDs unlocked (comma-separated)                  |
| `%locopillars_achievement_unlocked_names%`                 | Achievement names unlocked (comma-separated)                |
| `%locopillars_achievement_<type>_type_status%`             | Achievements unlocked of given type (e.g., progressive)     |
| `%locopillars_achievement_<category>_category_status%`     | Achievements unlocked in category (e.g., combat)            |
| `%locopillars_achievement_<difficulty>_difficulty_status%` | Achievements unlocked of given difficulty (e.g., easy)      |
| `%locopillars_achievement_<id>_completed%`                 | "1" if achievement unlocked, "0" otherwise                  |
| `%locopillars_achievement_<id>_progress%`                  | Achievement progress: current/target                        |
| `%locopillars_achievement_<id>_percent%`                   | Achievement unlock percent (0-100)                          |
| `%locopillars_achievement_<id>_name%`                      | Achievement display name                                    |
| `%locopillars_achievement_<id>_desc%`                      | Achievement description                                     |
| `%locopillars_achievement_<id>_difficulty%`                | Achievement difficulty                                      |
| `%locopillars_achievement_<id>_category%`                  | Achievement category                                        |
| `%locopillars_achievement_<id>_type%`                      | Achievement type                                            |
| `%locopillars_achievement_<id>_unlock_message%`            | Unlock message displayed when achievement is unlocked       |

Replace `<id>`, `<type>`, `<category>`, `<difficulty>` with the actual achievement's ID, type, category, or difficulty as configured in your achievements.

---

## **Notes**

- All placeholders are case-insensitive.
- Placeholders returning percentages are formatted with 1 decimal (e.g., "85.7").
- Placeholders returning times are formatted as "HH:MM:SS".
- Some placeholders require the player to be online.

---

## Example Usage

```
ScoreBoards:
  PlayerStats:
    Title: "&aStats"
    Lines:
      1: "Games won: %locopillars_db_games_won%"
      2: "Achievements: %locopillars_achievement_total_completed_percent%%"
      3: "Progressive: %locopillars_achievement_progressive_type_status%"
```

---