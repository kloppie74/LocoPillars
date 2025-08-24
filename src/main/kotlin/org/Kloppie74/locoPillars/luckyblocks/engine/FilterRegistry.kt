package org.Kloppie74.locoPillars.luckyblocks.engine

import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType

interface LuckyBlockFilter {
    fun passes(player: Player, value: String): Boolean
}

class FilterRegistry {
    private val filters = mutableMapOf<String, LuckyBlockFilter>()

    init {
        registerAllFilters()
    }

    private fun registerAllFilters() {
        // World & Environment Filters
        registerFilter("world", WorldFilter())
        registerFilter("biome", BiomeFilter())
        registerFilter("time", TimeFilter())
        registerFilter("weather", WeatherFilter())
        registerFilter("region", RegionFilter())

        // Player Status Filters
        registerFilter("player_health", PlayerHealthFilter())
        registerFilter("is_sneaking", IsSneakingFilter())
        registerFilter("is_flying", IsFlyingFilter())
        registerFilter("is_in_vehicle", IsInVehicleFilter())
        registerFilter("is_in_combat", IsInCombatFilter())
        registerFilter("is_op", IsOpFilter())

        // Inventory & Items Filters
        registerFilter("has", HasItemFilter())
        registerFilter("has_not", HasNotItemFilter())
        registerFilter("inventory_full", InventoryFullFilter())
        registerFilter("inventory_empty", InventoryEmptyFilter())
        registerFilter("armor_equipped", ArmorEquippedFilter())

        // Status Effects Filters
        registerFilter("status_effect", StatusEffectFilter())

        // Team & Social Filters
        registerFilter("team", TeamFilter())
        registerFilter("player_name", PlayerNameFilter())
        registerFilter("team_size", TeamSizeFilter())

        // Achievement Filters
        registerFilter("has_achievement", HasAchievementFilter())
        registerFilter("not_has_achievement", NotHasAchievementFilter())

        // Entity & World Filters
        registerFilter("mob_nearby", MobNearbyFilter())
        registerFilter("block_above", BlockAboveFilter())
        registerFilter("block_below", BlockBelowFilter())
        registerFilter("has_pet", HasPetFilter())
        registerFilter("is_riding", IsRidingFilter())

        // Misc Filters
        registerFilter("random_chance", RandomChanceFilter())
    }

    fun registerFilter(name: String, filter: LuckyBlockFilter) {
        filters[name.lowercase()] = filter
    }

    fun checkFilter(player: Player, filterName: String, value: String): Boolean {
        val filter = filters[filterName.lowercase()]
        return filter?.passes(player, value) ?: true // Default to true if filter not found
    }

    fun getRegisteredFilters(): Set<String> = filters.keys
}

// ===== WORLD & ENVIRONMENT FILTERS =====

class WorldFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        val worldName = player.world.name
        return when {
            value.startsWith("!") -> !worldName.equals(value.substring(1), ignoreCase = true)
            else -> worldName.equals(value, ignoreCase = true) ||
                    worldName.contains(value, ignoreCase = true)
        }
    }
}

class BiomeFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        val biome = player.location.block.biome.toString()
        return when {
            value.startsWith("!") -> !biome.contains(value.substring(1), ignoreCase = true)
            else -> biome.contains(value, ignoreCase = true)
        }
    }
}

class TimeFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        val time = player.world.time
        return when {
            value.startsWith("!") -> {
                val checkValue = value.substring(1)
                !checkTimeCondition(time, checkValue)
            }
            else -> checkTimeCondition(time, value)
        }
    }

    private fun checkTimeCondition(time: Long, value: String): Boolean {
        return when (value.uppercase()) {
            "DAY" -> time in 0..12000
            "NIGHT" -> time in 12000..24000
            "DAWN" -> time in 23000..24000 || time in 0..1000
            "DUSK", "SUNSET" -> time in 12000..14000
            "NOON" -> time in 5000..7000
            "MIDNIGHT" -> time in 17000..19000
            else -> {
                val targetTime = value.toLongOrNull()
                if (targetTime != null) {
                    kotlin.math.abs(time - targetTime) < 1000
                } else false
            }
        }
    }
}

class WeatherFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        val world = player.world
        val currentWeather = when {
            !world.hasStorm() && !world.isThundering -> "CLEAR"
            world.hasStorm() && !world.isThundering -> "RAIN"
            world.hasStorm() && world.isThundering -> "THUNDER"
            else -> "CLEAR"
        }

        return when {
            value.startsWith("!") -> !currentWeather.equals(value.substring(1), ignoreCase = true)
            else -> currentWeather.equals(value, ignoreCase = true)
        }
    }
}

class RegionFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        // This would integrate with WorldGuard regions
        // For now, just check basic coordinates or world spawn area
        val location = player.location
        val spawnLoc = player.world.spawnLocation
        val distanceFromSpawn = location.distance(spawnLoc)

        return when (value.lowercase()) {
            "spawn" -> distanceFromSpawn < 100
            "wilderness" -> distanceFromSpawn > 500
            "safe" -> distanceFromSpawn < 50
            else -> true // Allow all regions if not specifically defined
        }
    }
}

// ===== PLAYER STATUS FILTERS =====

class PlayerHealthFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        val health = player.health
        return when {
            value.startsWith("<=") -> health <= value.substring(2).toDoubleOrNull() ?: Double.MAX_VALUE
            value.startsWith(">=") -> health >= value.substring(2).toDoubleOrNull() ?: 0.0
            value.startsWith("<") -> health < value.substring(1).toDoubleOrNull() ?: Double.MAX_VALUE
            value.startsWith(">") -> health > value.substring(1).toDoubleOrNull() ?: 0.0
            else -> health >= value.toDoubleOrNull() ?: 0.0
        }
    }
}

class IsSneakingFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        return player.isSneaking == value.toBoolean()
    }
}

class IsFlyingFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        return player.isFlying == value.toBoolean()
    }
}

class IsInVehicleFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        return player.isInsideVehicle == value.toBoolean()
    }
}

class IsInCombatFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        // Similar logic as condition but for filtering
        val hasCombatEffects = listOf(
            PotionEffectType.STRENGTH,
            PotionEffectType.RESISTANCE,
            PotionEffectType.REGENERATION
        ).any { player.hasPotionEffect(it) }

        val heldItem = player.inventory.itemInMainHand
        val isHoldingWeapon = heldItem.type.name.contains("SWORD") ||
                heldItem.type.name.contains("AXE") ||
                heldItem.type.name.contains("BOW") ||
                heldItem.type.name.contains("CROSSBOW")

        val nearbyMobs = player.getNearbyEntities(8.0, 8.0, 8.0)
            .filterIsInstance<org.bukkit.entity.Monster>()
            .any { it.target == player }

        val inCombat = hasCombatEffects || isHoldingWeapon || nearbyMobs
        return inCombat == value.toBoolean()
    }
}

class IsOpFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        return player.isOp == value.toBoolean()
    }
}

// ===== INVENTORY & ITEMS FILTERS =====

class HasItemFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        return try {
            val material = Material.valueOf(value.uppercase())
            player.inventory.contains(material)
        } catch (e: Exception) {
            player.inventory.contents.any { item ->
                item != null && (
                        item.type.name.contains(value, ignoreCase = true) ||
                                item.itemMeta?.displayName?.contains(value, ignoreCase = true) == true
                        )
            }
        }
    }
}

class HasNotItemFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        return !HasItemFilter().passes(player, value)
    }
}

class InventoryFullFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        val isFull = player.inventory.firstEmpty() == -1
        return isFull == value.toBoolean()
    }
}

class InventoryEmptyFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        val isEmpty = player.inventory.contents.all { it == null || it.type == Material.AIR }
        return isEmpty == value.toBoolean()
    }
}

class ArmorEquippedFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        val armorPieces = listOf(
            player.inventory.helmet,
            player.inventory.chestplate,
            player.inventory.leggings,
            player.inventory.boots
        )

        return when (value.uppercase()) {
            "DIAMOND" -> armorPieces.any { it?.type?.name?.contains("DIAMOND") == true }
            "IRON" -> armorPieces.any { it?.type?.name?.contains("IRON") == true }
            "LEATHER" -> armorPieces.any { it?.type?.name?.contains("LEATHER") == true }
            "CHAINMAIL" -> armorPieces.any { it?.type?.name?.contains("CHAINMAIL") == true }
            "GOLDEN", "GOLD" -> armorPieces.any { it?.type?.name?.contains("GOLDEN") == true }
            "NETHERITE" -> armorPieces.any { it?.type?.name?.contains("NETHERITE") == true }
            "ANY" -> armorPieces.any { it != null && it.type != Material.AIR }
            "FULL" -> armorPieces.all { it != null && it.type != Material.AIR }
            else -> armorPieces.any { it?.type?.name?.contains(value, ignoreCase = true) == true }
        }
    }
}

// ===== STATUS EFFECTS FILTERS =====

class StatusEffectFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        return when {
            value.startsWith("!") -> {
                val effectName = value.substring(1)
                try {
                    val effectType = PotionEffectType.getByName(effectName.uppercase())
                    effectType?.let { !player.hasPotionEffect(it) } ?: true
                } catch (e: Exception) {
                    true
                }
            }
            else -> {
                try {
                    val effectType = PotionEffectType.getByName(value.uppercase())
                    effectType?.let { player.hasPotionEffect(it) } ?: false
                } catch (e: Exception) {
                    false
                }
            }
        }
    }
}

// ===== TEAM & SOCIAL FILTERS =====

class TeamFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        val scoreboard = player.scoreboard
        val team = scoreboard.getPlayerTeam(player)

        return when {
            value.startsWith("!") -> {
                val teamName = value.substring(1)
                when {
                    teamName.equals("player", ignoreCase = true) -> team == null
                    team != null -> !team.name.equals(teamName, ignoreCase = true) &&
                            !team.displayName.equals(teamName, ignoreCase = true)
                    else -> true
                }
            }
            value.equals("player", ignoreCase = true) -> team != null
            team != null -> team.name.equals(value, ignoreCase = true) ||
                    team.displayName.equals(value, ignoreCase = true)
            else -> value.equals("NONE", ignoreCase = true)
        }
    }
}

class PlayerNameFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        return when {
            value.startsWith("!") -> !player.name.equals(value.substring(1), ignoreCase = true)
            else -> player.name.equals(value, ignoreCase = true) ||
                    player.displayName.equals(value, ignoreCase = true)
        }
    }
}

class TeamSizeFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        val scoreboard = player.scoreboard
        val team = scoreboard.getPlayerTeam(player) ?: return value.startsWith("=") && value.substring(1) == "1"

        val teamSize = team.entries.size
        return when {
            value.startsWith(">=") -> teamSize >= value.substring(2).toIntOrNull() ?: 0
            value.startsWith("<=") -> teamSize <= value.substring(2).toIntOrNull() ?: Int.MAX_VALUE
            value.startsWith(">") -> teamSize > value.substring(1).toIntOrNull() ?: 0
            value.startsWith("<") -> teamSize < value.substring(1).toIntOrNull() ?: Int.MAX_VALUE
            value.startsWith("=") -> teamSize == value.substring(1).toIntOrNull() ?: -1
            else -> teamSize >= value.toIntOrNull() ?: 0
        }
    }
}

// ===== ACHIEVEMENT FILTERS =====

class HasAchievementFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        try {
            val advancement = Bukkit.getAdvancement(org.bukkit.NamespacedKey.minecraft(value.lowercase()))
            if (advancement != null) {
                val progress = player.getAdvancementProgress(advancement)
                return progress.isDone
            }

            val key = org.bukkit.NamespacedKey.minecraft("achievement_$value")
            return player.persistentDataContainer.has(key, org.bukkit.persistence.PersistentDataType.STRING)
        } catch (e: Exception) {
            return false
        }
    }
}

class NotHasAchievementFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        return !HasAchievementFilter().passes(player, value)
    }
}

// ===== ENTITY & WORLD FILTERS =====

class MobNearbyFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        val radius = 10.0
        val nearbyEntities = player.getNearbyEntities(radius, radius, radius)

        return try {
            val entityType = org.bukkit.entity.EntityType.valueOf(value.uppercase())
            nearbyEntities.any { it.type == entityType }
        } catch (e: Exception) {
            nearbyEntities.any { entity ->
                entity.type.name.contains(value, ignoreCase = true) ||
                        (entity.customName?.contains(value, ignoreCase = true) ?: false)
            }
        }
    }
}

class BlockAboveFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        val blockAbove = player.location.clone().add(0.0, 2.0, 0.0).block
        return try {
            val material = Material.valueOf(value.uppercase())
            blockAbove.type == material
        } catch (e: Exception) {
            blockAbove.type.name.contains(value, ignoreCase = true)
        }
    }
}

class BlockBelowFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        val blockBelow = player.location.clone().add(0.0, -1.0, 0.0).block
        return try {
            val material = Material.valueOf(value.uppercase())
            blockBelow.type == material
        } catch (e: Exception) {
            blockBelow.type.name.contains(value, ignoreCase = true)
        }
    }
}

class HasPetFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        val nearbyPets = player.getNearbyEntities(20.0, 20.0, 20.0)
            .filterIsInstance<org.bukkit.entity.Tameable>()
            .filter { it.isTamed && it.owner == player }

        val hasPets = nearbyPets.isNotEmpty()
        return hasPets == value.toBoolean()
    }
}

class IsRidingFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        val vehicle = player.vehicle
        return when {
            value.equals("true", ignoreCase = true) -> vehicle != null
            value.equals("false", ignoreCase = true) -> vehicle == null
            vehicle != null -> vehicle.type.name.contains(value, ignoreCase = true)
            else -> false
        }
    }
}

// ===== MISC FILTERS =====

class RandomChanceFilter : LuckyBlockFilter {
    override fun passes(player: Player, value: String): Boolean {
        return when {
            value.startsWith("<=") -> kotlin.random.Random.nextDouble() <= value.substring(2).toDoubleOrNull() ?: 0.0
            value.startsWith(">=") -> kotlin.random.Random.nextDouble() >= value.substring(2).toDoubleOrNull() ?: 1.0
            value.startsWith("<") -> kotlin.random.Random.nextDouble() < value.substring(1).toDoubleOrNull() ?: 0.0
            value.startsWith(">") -> kotlin.random.Random.nextDouble() > value.substring(1).toDoubleOrNull() ?: 1.0
            else -> kotlin.random.Random.nextDouble() < value.toDoubleOrNull() ?: 0.5
        }
    }
}