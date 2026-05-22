# LuckyBlocks — Kotlin Script Engine

LocoPillars uses a **Kotlin scripting system** for LuckyBlocks.
Every lucky block is a folder with two files — a JSON config and a Kotlin script.

```
plugins/LocoPillars/Luckyblocks/
├── my_luckyblock/
│   ├── Settings.json    <- metadata (id, name, icon, rarity)
│   └── Luckyblock.kts   <- your Kotlin script
└── README.md            <- this file (auto-updated on server start)
```

Reload without restarting: `/luckyblocks reload`

---

## Settings.json

```json
{
  "id": "my_luckyblock",
  "display_name": "My LuckyBlock",
  "description": "Describe what happens when broken.",
  "icon": {
    "material": "DIAMOND",
    "custom_model_data": 0
  },
  "block_type": "LUCKY_BLOCK",
  "rarity": "COMMON"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `id` | string | Unique identifier — no spaces, use underscores |
| `display_name` | string | Display name in menus |
| `description` | string | Short description |
| `icon.material` | string | Bukkit Material name (e.g. `"DIAMOND"`, `"BLAZE_ROD"`) |
| `icon.custom_model_data` | int | Custom model data (0 = none) |
| `block_type` | string | Always `"LUCKY_BLOCK"` |
| `rarity` | string | `COMMON`, `UNCOMMON`, `RARE`, `EPIC`, or `LEGENDARY` |

---

## Luckyblock.kts — Writing Scripts

When a player breaks a lucky block, your `Luckyblock.kts` runs on the server main thread.

Two variables are automatically injected:

| Variable | Type | Description |
|----------|------|-------------|
| `player` | `Player` | The player who broke the block |
| `game` | `LuckyblockContext` | Full game and world API (see below) |

### Minimal example

```kotlin
player.sendMessage("§aYou found something cool!")
game.spawnLightning(player.location)
```

### Full example

```kotlin
// Give loot
game.giveItem(player, org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 5))
game.giveEffect(player, org.bukkit.potion.PotionEffectType.SPEED, 30, 2)

// Visual flair
game.spawnFirework(player.location)
game.spawnParticles(player.location, org.bukkit.Particle.HEART, 20)
game.playSound(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f)

// Titles and messages
game.sendTitle(player, "§6★ Lucky!", "§7You found diamonds and speed!", 5, 50, 10)
player.sendMessage("§6§l★ Lucky! §r§7You struck it rich!")
```

### Enchanted item example

```kotlin
val sword = org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_SWORD)
val meta = sword.itemMeta
if (meta != null) {
    meta.addEnchant(org.bukkit.enchantments.Enchantment.SHARPNESS, 3, true)
    meta.addEnchant(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 2, true)
    sword.itemMeta = meta
}
game.giveItem(player, sword)
```

### Mob interaction example

```kotlin
val wolves = game.spawnMobs(org.bukkit.entity.EntityType.WOLF, player.location, 3)
wolves.forEach { entity ->
    val wolf = entity as? org.bukkit.entity.Wolf ?: return@forEach
    wolf.isTamed = true
    wolf.owner = player
}
player.sendMessage("§aYour wolf pack is ready!")
```

### Multi-player swap example

```kotlin
val others = game.getPlayers().filter { it.uniqueId != player.uniqueId }
val target = others.randomOrNull()
if (target != null) {
    val myLoc = player.location.clone()
    player.teleport(target.location)
    target.teleport(myLoc)
    player.sendMessage("§eSwapped with ${target.name}!")
    target.sendMessage("§eSwapped with ${player.name}!")
}
```

---

## LuckyblockContext API (game.*)

### Player Access

| Method | Returns | Description |
|--------|---------|-------------|
| `getPlayers()` | `List<Player>` | All players in the game (or all online if outside game) |
| `getAlivePlayers()` | `List<Player>` | Alive players |
| `randomPlayer()` | `Player?` | Random player, null if none |

### World Effects

| Method | Description |
|--------|-------------|
| `spawnLightning(location)` | Real lightning bolt (damages entities) |
| `spawnLightningEffect(location)` | Visual lightning only (no damage) |
| `spawnExplosion(location, power, fire)` | Explosion — power: Float, fire: Boolean |
| `spawnMob(type, location)` | Spawn one entity |
| `spawnMobs(type, location, count)` | Spawn multiple entities, returns List |
| `spawnFirework(location)` | Random colorful firework |
| `spawnParticles(location, particle, count)` | Particle burst |

### Player Interactions

| Method | Description |
|--------|-------------|
| `giveEffect(player, type, durationSeconds, amplifier)` | Add potion effect |
| `removeEffect(player, type)` | Remove potion effect |
| `giveItem(player, itemStack)` | Give item (auto-drops if full) |
| `teleport(player, location)` | Teleport to exact location |
| `randomTeleport(player, radius)` | Random teleport within radius |
| `teleportToRandomPlayer(player)` | Teleport to random other player |

### Messages and Display

| Method | Description |
|--------|-------------|
| `sendMessage(player, message)` | Chat message |
| `broadcastToGame(message)` | Message to all game players |
| `sendTitle(player, title, subtitle, fadeIn, stay, fadeOut)` | Title on screen |
| `playSound(player, sound, volume, pitch)` | Play sound |

### Blocks

| Method | Description |
|--------|-------------|
| `setBlock(location, material)` | Place a block |
| `setBlockRelative(x, y, z, material)` | Place relative to break position |

### Game Info

| Property/Method | Returns | Description |
|-----------------|---------|-------------|
| `blockLocation` | `Location` | Where the lucky block was broken |
| `getGame()` | `GameInstance?` | Current game instance |
| `isInGame()` | `Boolean` | Whether a game is active |
| `getWorld()` | `World?` | World of the break location |

---

## Rarity System

| Rarity | Weight | Base chance (50 blocks loaded) |
|--------|--------|----------------------------------|
| COMMON | 35 | ~26.9% shared across all COMMON |
| UNCOMMON | 30 | ~23.1% shared |
| RARE | 20 | ~15.4% shared |
| EPIC | 10 | ~7.7% shared |
| LEGENDARY | 5 | ~3.8% shared |

Weight is shared across all blocks of the same rarity.
With 20 COMMON blocks loaded, each individual COMMON block has ~1.35% chance.

---

## All 50 Included LuckyBlocks

### COMMON (20)
| ID | Description |
|----|-------------|
| `firework_frenzy` | Colorful fireworks burst around you |
| `random_teleport` | Teleported to a random location |
| `tnt_surprise` | Small explosion (you survive!) |
| `speed_rush` | 30 seconds of turbo speed + jump boost |
| `arrow_rain` | Arrows fall from the sky |
| `xp_shower` | 10 levels of XP rain down |
| `pumpkin_head` | Carved pumpkin forced onto your head |
| `milk_cure` | All effects removed, health restored |
| `dirt_tower` | A dirt pillar launches you upward |
| `cake_gift` | Three cakes in your inventory |
| `snowball_fight` | 64 snowballs — fight! |
| `confusion_cloud` | Brief nausea + blindness |
| `night_hunter` | Night vision + speed for 30 seconds |
| `pig_ride` | A saddled pig appears — hop on! |
| `item_shower` | Random items drop around you |
| `leap_launch` | Launched high into the air |
| `web_trap` | Cobwebs slow you down |
| `baby_creeper` | A tiny silent creeper appears |
| `flower_circle` | Flowers bloom in a ring around you |
| `sand_avalanche` | Sand falls from 10 blocks above |

### UNCOMMON (16)
| ID | Description |
|----|-------------|
| `mob_swarm` | Zombie + skeleton + spider horde |
| `treasure_chest` | Diamonds, gold, food haul |
| `potion_rain` | Regen + absorption + saturation |
| `freeze_trap` | Frozen solid — slowness + blindness |
| `bone_storm` | Wither skeletons attack! |
| `obsidian_cage` | Trapped in a 3x3 obsidian box |
| `enchanted_sword` | Sharpness + Fire Aspect diamond sword |
| `ender_chest_gift` | A free ender chest |
| `double_strength` | Strength II + Resistance II combo |
| `diamond_helmet_gift` | Protection IV diamond helmet |
| `guardian_swarm` | 5 guardians attack! |
| `tnt_cluster` | 4 small explosions in all directions |
| `healing_aura` | Regen + absorption + saturation + health boost |
| `enchanted_bow_gift` | Power + Infinity bow with an arrow |
| `wolf_pack` | 4 loyal tamed wolves |
| `lava_ring` | Ring of lava appears around you |

### RARE (9)
| ID | Description |
|----|-------------|
| `lightning_storm` | Ring of lightning + direct strike |
| `chaos_swap` | Swap positions with a random player |
| `pillar_of_fire` | Lightning cross of fire pillars |
| `wither_awakens` | A Wither is summoned above you! |
| `god_apple_gift` | Enchanted golden apple |
| `diamond_kit` | Full diamond armor + diamond sword |
| `earthquake` | Ground turns to gravel around you |
| `levitation_trap` | Floated up, then slow fall |
| `mass_teleport` | Everyone in the game teleports randomly |

### EPIC (3)
| ID | Description |
|----|-------------|
| `thunder_god` | Massive storm + all combat buffs |
| `undead_army` | Massive undead horde (15+ mobs) |
| `black_hole` | Pulls all nearby players toward you |

### LEGENDARY (2)
| ID | Description |
|----|-------------|
| `lucky_legend` | ALL good effects + full diamond gear + god apple + fireworks |
| `apocalypse` | CHAOS — explosion, lightning, mob army, everyone blinds |

---

## Tips

- Scripts run synchronously on the main thread — avoid heavy loops or blocking code.
- Use `game.isInGame()` before accessing game data to avoid null errors.
- Access any Paper API class with the full package path: `org.bukkit.Material.DIAMOND`.
- Errors in scripts are logged to console with the luckyblock ID — easy to debug.
- Reload without restarting the server: `/luckyblocks reload`
- The README file is always re-extracted on server start to stay up to date.

---

Happy Scripting! - LocoPillars LuckyBlocks Engine
