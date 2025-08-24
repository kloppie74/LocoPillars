# LuckyBlocks Configuration Guide

Create endless custom LuckyBlocks by combining **effects**, **conditions**, and **filters**.  
LuckyBlocks are loaded from this folder.  
When a LuckyBlock is broken, the system picks a random YAML file and executes its effects if all conditions and filters pass.

---

## Basic YAML Structure

```yaml
id: unique_id
display_name: "LuckyBlock Name"
description: "What happens when broken?"
icon:
  material: "DIAMOND_BLOCK"
  custom_model_data: 123
block_type: "LUCKY_BLOCK"
effects:
  - type: "EFFECT_TYPE"
    # effect-specific options
conditions:
  - key: value
filters:
  - key: value
triggers:
  # Only needed for custom items/triggers (like right-click, use, etc)
  - event: "ITEM_RIGHT_CLICK"
    item_name: "name"
    effect: {...}
```

---

## **LuckyBlock Effects**

| Effect Name             | Description                                                               | Options Example                                       |
|------------------------ |---------------------------------------------------------------------------|-------------------------------------------------------|
| SPAWN_MOB               | Spawns mob(s) near player                                                 | mob_type, amount, radius, aggro, target               |
| SPAWN_PRIME_TNT         | Spawns primed TNT at location                                             | amount, radius, fuse_ticks                            |
| GIVE_ITEM               | Gives item to player                                                      | material, name, lore, custom_model_data               |
| TRAIL                   | Leaves a block trail behind player as they move                           | block, duration, radius                               |
| BLOCK_TRAIL             | Leaves a block trail under player                                         | block, duration, radius                               |
| LOOT_RAIN               | Drops items from sky                                                      | items, amount, radius                                 |
| CAST_SPELL              | Casts a spell at player or location                                       | spell, radius, message                                |
| CUSTOM_COMMAND          | Executes server command                                                   | command, message                                      |
| TELEPORT                | Teleports player                                                          | location, radius                                      |
| EXPLOSION               | Creates explosion at player/block                                         | power, fire, damage                                   |
| SET_FIRE                | Sets blocks or player on fire                                             | duration, radius                                      |
| LAUNCH_PLAYER           | Launches player (up/side)                                                 | power, direction                                      |
| SUMMON_LIGHTNING        | Strikes lightning at location/player                                      | amount, radius                                        |
| CHANGE_WEATHER          | Changes world weather                                                     | to_weather, duration                                  |
| HEAL                    | Heals player                                                              | amount                                                |
| DAMAGE                  | Damages player                                                            | amount, type                                          |
| GIVE_EFFECT             | Gives potion effect                                                       | effect, duration, amplifier                           |
| REMOVE_EFFECT           | Removes potion effect                                                     | effect                                                |
| PLAY_SOUND              | Plays sound to player                                                     | sound, volume, pitch                                  |
| CHANGE_TIME             | Changes world time                                                        | time                                                  |
| GIVE_XP                 | Gives experience points                                                   | amount                                                |
| SPAWN_STRUCTURE         | Spawns structure near player                                              | structure_name, location                              |
| BLOCK_REPLACE           | Replaces LuckyBlock with another block                                    | material                                              |
| SPAWN_VEHICLE           | Spawns vehicle at location                                                | vehicle_type                                          |
| SLOWFALL                | Grants slow falling                                                       | duration                                              |
| SUMMON_PARTICLES        | Spawns particles at location/player                                       | type, amount                                          |
| CHANGE_GAMEMODE         | Changes player's gamemode                                                 | gamemode                                              |
| SWAP_PLACE              | Swaps place with random player                                            | -                                                     |
| DISGUISE_MOB            | Disguises player as mob                                                   | mob_type, duration                                    |
| SPAWN_BOSS              | Spawns boss mob                                                           | boss_type                                             |
| SUMMON_PORTAL           | Creates portal at location                                                | destination                                           |
| SHRINK                  | Shrinks player size                                                       | duration                                              |
| GROW                    | Grows player size                                                         | duration                                              |
| RANDOM_TP_ALL           | Teleports all players randomly                                            | radius                                                |
| NUKE                    | Massive explosion                                                         | radius                                                |
| FREEZE                  | Freezes player                                                            | duration                                              |
| UNFREEZE                | Unfreezes player                                                          | -                                                     |
| SET_BLOCK_AREA          | Sets blocks in area to material                                           | radius, material                                      |
| SUMMON_MINIONS          | Spawns minion mobs for player                                             | mob_type, amount                                      |
| UNLOCK_ACHIEVEMENT      | Unlocks achievement for player                                            | achievement_id                                        |
| SPAWN_SHULKER_BOX       | Spawns shulker box with loot                                              | loot_table                                            |
| SWAP_HEALTH             | Swaps health with random player                                           | -                                                     |
| SET_HUNGER              | Sets player's hunger level                                                | amount                                                |
| CLEAR_INVENTORY         | Empties player's inventory                                                | -                                                     |
| DUPLICATE_ITEM          | Duplicates held item                                                      | -                                                     |
| CURSE_ITEM              | Adds curse to item                                                        | curse_type                                            |
| ENCHANT_ITEM            | Adds enchantment to item                                                  | enchant, level                                        |
| SUMMON_ANVILS           | Drops anvils from above                                                   | amount, radius                                        |
| SWAP_ARMOR              | Swaps armor with another player                                           | -                                                     |
| SUMMON_FIREWORKS        | Spawns fireworks                                                          | amount, colors, power                                 |

---

### **All Supported Spells for `CAST_SPELL` Effect**
Use these for the `spell` key in `CAST_SPELL` or custom item triggers.

| Spell Name        | Description (effect)                   |
|-------------------|----------------------------------------|
| FANGS             | Evoker fangs attack                    |
| LEVITATION        | Levitation potion effect               |
| WITHER            | Wither potion effect                   |
| POISON            | Poison potion effect                   |
| BLINDNESS         | Blindness potion effect                |
| SLOWNESS          | Slowness potion effect                 |
| SPEED             | Speed potion effect                    |
| STRENGTH          | Strength potion effect                 |
| INVISIBILITY      | Invisibility effect                    |
| REGENERATION      | Regeneration effect                    |
| FIREBALL          | Shoots fireball                        |
| LIGHTNING         | Strikes with lightning                 |
| EXPLOSION         | Creates explosion                      |
| TELEPORT          | Teleports player randomly              |
| HEAL              | Heals player instantly                 |
| HUNGER            | Applies hunger effect                  |
| WEAKNESS          | Weakness potion effect                 |
| NAUSEA            | Nausea potion effect                   |
| CONFUSION         | Confusion (nausea) effect              |
| DAMAGE            | Direct damage                          |
| SLOWFALL          | Slow falling effect                    |
| JUMP_BOOST        | Jump boost effect                      |
| FIRE              | Sets player on fire                    |
| WATER             | Summons water bubble                   |
| MINION_SUMMON     | Summons minion mob                     |
| FREEZE            | Freezes player                         |
| UNFREEZE          | Unfreezes player                       |
| SHRINK            | Shrinks player                         |
| GROW              | Grows player                           |

You can always extend this list with vanilla potion effects, mob abilities, and projectile spells.

---

## **LuckyBlock Conditions**

| Condition Name          | Description                                               | Example Values                    |
|------------------------|-----------------------------------------------------------|-----------------------------------|
| player_health          | Player's health level                                     | ">=10", "<5"                      |
| player_y               | Player's Y position                                       | ">=70", "<50"                     |
| biome                  | Biome player stands in                                    | "DESERT", "SNOWY", "PLAINS"       |
| has_not                | Player does NOT have item                                 | "STONE"                           |
| has                    | Player has item                                           | "DIAMOND"                         |
| time                   | World time                                                | "DAY", "NIGHT", "SUNSET"          |
| is_on_team             | Player's team                                             | "RED", "BLUE", "NONE"             |
| has_status_effect      | Player has effect                                         | "POISON", "SPEED"                 |
| has_not_status_effect  | Player does NOT have effect                               | "SLOWNESS"                        |
| weather                | Current weather                                           | "CLEAR", "RAIN", "THUNDER"        |
| is_sneaking            | Player is sneaking                                        | "true"                            |
| is_sprinting           | Player is sprinting                                       | "true"                            |
| is_flying              | Player is flying                                          | "true"                            |
| on_ground              | Player is on ground                                       | "true"                            |
| is_wet                 | Player is wet                                             | "true"                            |
| is_burning             | Player is burning                                         | "true"                            |
| is_in_vehicle          | Player is in vehicle                                      | "true"                            |
| is_in_water            | Player is in water                                        | "true"                            |
| has_achievement        | Player has achievement                                    | "marathon_runner"                 |
| not_has_achievement    | Player does NOT have achievement                          | "builder_supreme"                 |
| is_in_combat           | Player is in combat                                       | "true"                            |
| mob_nearby             | Mob is nearby                                             | "ZOMBIE", "CREEPER"               |
| block_above            | Block above player                                        | "AIR", "STONE"                    |
| block_below            | Block below player                                        | "GRASS", "DIRT"                   |
| inventory_full         | Inventory is full                                         | "true"                            |
| inventory_empty        | Inventory is empty                                        | "true"                            |
| armor_equipped         | Armor type equipped                                       | "DIAMOND", "LEATHER"              |
| random_chance          | Chance to trigger                                         | "<0.25" (25% chance)              |
| block_count_nearby     | Number of certain blocks nearby                           | ">=5"                             |
| team_size              | Size of player's team                                     | ">=3"                             |
| weather                | Weather condition                                         | "RAIN", "CLEAR"                   |

---

## **LuckyBlock Filters**

| Filter Name            | Description                                               | Example Values                  |
|------------------------|-----------------------------------------------------------|---------------------------------|
| world                  | World name                                                | "world", "!nether", "end"       |
| team                   | Player's team                                             | "player", "!player", "RED"      |
| biome                  | Biome                                                     | "PLAINS", "!DESERT"             |
| has_not                | Player must NOT have item                                 | "STONE"                         |
| has                    | Player must have item                                     | "DIAMOND"                       |
| time                   | World time                                                | "DAY", "NIGHT"                  |
| status_effect          | Player must have effect                                   | "POISON", "!SPEED"              |
| player_name            | Player name                                               | "Notch", "!Herobrine"           |
| is_op                  | Player is OP                                              | "true", "false"                 |
| player_health          | Health threshold                                          | "<=10"                          |
| weather                | Weather condition                                         | "RAIN", "!CLEAR"                |
| is_sneaking            | Sneaking state                                            | "true"                          |
| is_flying              | Flying state                                              | "true"                          |
| is_in_vehicle          | Vehicle state                                             | "true"                          |
| has_achievement        | Achievement possession                                    | "marathon_runner"               |
| not_has_achievement    | Achievement absence                                       | "builder_supreme"               |
| mob_nearby             | Mob proximity                                             | "ZOMBIE"                        |
| block_above            | Block above                                               | "AIR"                           |
| block_below            | Block below                                               | "GRASS"                         |
| armor_equipped         | Armor equipped                                            | "DIAMOND", "LEATHER"            |
| region                 | Region                                                    | "spawn"                         |
| inventory_full         | Inventory status                                          | "true"                          |
| inventory_empty        | Inventory status                                          | "true"                          |
| is_in_combat           | Combat status                                             | "true"                          |
| has_pet                | Pet status                                                | "true"                          |
| is_riding              | Riding state                                              | "HORSE"                         |
| random_chance          | Chance to allow/block effect                              | "<0.10"                         |
| team_size              | Team size                                                 | ">=2"                           |

---

## Triggers

**BLOCK_BREAK** is the default for LuckyBlocks (when broken, a random LuckyBlock YAML is triggered).  
You can use other triggers for custom items/abilities:

- **ITEM_RIGHT_CLICK**: When player right-clicks with item
- **PLAYER_MOVE**: When player moves
- **ENTITY_DAMAGE**: When player deals/takes damage
- **ITEM_DROP**: When player drops item
- **INTERACT**: When player interacts with block
- **BLOCK_PLACE**: When player places block
- **effect**: (override main effect for this trigger)

**Example trigger (optional):**
```yaml
triggers:
  - event: "ITEM_RIGHT_CLICK"
    item_name: "&cEvoker's Curse"
    effect:
      type: "CAST_SPELL"
      spell: "FANGS"
      radius: 6
```

---

## Example LuckyBlock YAML

```yaml
id: firework_frenzy
display_name: "Firework Frenzy"
description: "Shoot fireworks when broken!"
icon:
  material: "FIREWORK_ROCKET"
effects:
  - type: "SUMMON_FIREWORKS"
    amount: 20
    message: "&dFireworks everywhere!"
conditions:
  - player_y: ">65"
  - biome: "PLAINS"
filters:
  - time: "NIGHT"
```

---

## Tips & Extending

- Mix effects, conditions & filters for unique LuckyBlocks.
- Add your own effect/condition/filter types in code!
- Use triggers for custom items/skills.
- Use random_chance for rare blocks.

---

**Happy LuckyBlock Building!**