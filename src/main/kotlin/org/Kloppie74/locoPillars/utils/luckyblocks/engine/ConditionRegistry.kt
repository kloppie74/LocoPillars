package org.Kloppie74.locoPillars.utils.luckyblocks.engine

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffectType

interface LuckyBlockCondition {
    fun check(player: Player, value: String): Boolean
}

class ConditionRegistry {
    private val conditions = mutableMapOf<String, LuckyBlockCondition>()

    init {
        registerAllConditions()
    }

    private fun registerAllConditions() {
        // Player Status Conditions
        registerCondition("player_health", PlayerHealthCondition())
        registerCondition("player_y", PlayerYCondition())
        registerCondition("is_sneaking", IsSneakingCondition())
        registerCondition("is_sprinting", IsSprintingCondition())
        registerCondition("is_flying", IsFlyingCondition())
        registerCondition("on_ground", OnGroundCondition())
        registerCondition("is_wet", IsWetCondition())
        registerCondition("is_burning", IsBurningCondition())
        registerCondition("is_in_vehicle", IsInVehicleCondition())
        registerCondition("is_in_water", IsInWaterCondition())

        // World & Environment Conditions
        registerCondition("biome", BiomeCondition())
        registerCondition("time", TimeCondition())
        registerCondition("weather", WeatherCondition())
        registerCondition("block_above", BlockAboveCondition())
        registerCondition("block_below", BlockBelowCondition())

        // Inventory & Items Conditions
        registerCondition("has", HasItemCondition())
        registerCondition("has_not", HasNotItemCondition())
        registerCondition("inventory_full", InventoryFullCondition())
        registerCondition("inventory_empty", InventoryEmptyCondition())
        registerCondition("armor_equipped", ArmorEquippedCondition())

        // Status Effects Conditions
        registerCondition("has_status_effect", HasStatusEffectCondition())
        registerCondition("has_not_status_effect", HasNotStatusEffectCondition())

        // Team & Social Conditions
        registerCondition("is_on_team", IsOnTeamCondition())
        registerCondition("team_size", TeamSizeCondition())

        // Achievement Conditions
        registerCondition("has_achievement", HasAchievementCondition())
        registerCondition("not_has_achievement", NotHasAchievementCondition())

        // Combat & Entity Conditions
        registerCondition("is_in_combat", IsInCombatCondition())
        registerCondition("mob_nearby", MobNearbyCondition())

        // Misc Conditions
        registerCondition("random_chance", RandomChanceCondition())
        registerCondition("block_count_nearby", BlockCountNearbyCondition())
    }

    fun registerCondition(name: String, condition: LuckyBlockCondition) {
        conditions[name.lowercase()] = condition
    }

    fun checkCondition(player: Player, conditionName: String, value: String): Boolean {
        val condition = conditions[conditionName.lowercase()]
        return condition?.check(player, value) ?: true // Default to true if condition not found
    }

    fun getRegisteredConditions(): Set<String> = conditions.keys
}

// ===== PLAYER STATUS CONDITIONS =====

class PlayerHealthCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        val health = player.health
        return when {
            value.startsWith(">=") -> health >= value.substring(2).toDoubleOrNull() ?: 0.0
            value.startsWith("<=") -> health <= value.substring(2).toDoubleOrNull() ?: Double.MAX_VALUE
            value.startsWith(">") -> health > value.substring(1).toDoubleOrNull() ?: 0.0
            value.startsWith("<") -> health < value.substring(1).toDoubleOrNull() ?: Double.MAX_VALUE
            value.startsWith("=") -> health == value.substring(1).toDoubleOrNull() ?: -1.0
            else -> health >= value.toDoubleOrNull() ?: 0.0
        }
    }
}

class PlayerYCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        val y = player.location.y
        return when {
            value.startsWith(">=") -> y >= value.substring(2).toDoubleOrNull() ?: 0.0
            value.startsWith("<=") -> y <= value.substring(2).toDoubleOrNull() ?: Double.MAX_VALUE
            value.startsWith(">") -> y > value.substring(1).toDoubleOrNull() ?: 0.0
            value.startsWith("<") -> y < value.substring(1).toDoubleOrNull() ?: Double.MAX_VALUE
            value.startsWith("=") -> y == value.substring(1).toDoubleOrNull() ?: -1.0
            else -> y >= value.toDoubleOrNull() ?: 0.0
        }
    }
}

class IsSneakingCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        return player.isSneaking == value.toBoolean()
    }
}

class IsSprintingCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        return player.isSprinting == value.toBoolean()
    }
}

class IsFlyingCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        return player.isFlying == value.toBoolean()
    }
}

class OnGroundCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        return player.isOnGround == value.toBoolean()
    }
}

class IsWetCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        val block = player.location.block
        val isInWater = block.type == Material.WATER || block.isLiquid
        val isRaining = player.world.hasStorm() && player.location.block.lightFromSky > 0
        return (isInWater || isRaining) == value.toBoolean()
    }
}

class IsBurningCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        return (player.fireTicks > 0) == value.toBoolean()
    }
}

class IsInVehicleCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        return player.isInsideVehicle == value.toBoolean()
    }
}

class IsInWaterCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        val block = player.location.block
        return (block.type == Material.WATER || block.isLiquid) == value.toBoolean()
    }
}

// ===== WORLD & ENVIRONMENT CONDITIONS =====

class BiomeCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        val biome = player.location.block.biome.toString()
        return biome.equals(value, ignoreCase = true) ||
                biome.contains(value, ignoreCase = true)
    }
}

class TimeCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        val time = player.world.time
        return when (value.uppercase()) {
            "DAY" -> time in 0..12000
            "NIGHT" -> time in 12000..24000
            "DAWN" -> time in 23000..24000 || time in 0..1000
            "DUSK", "SUNSET" -> time in 12000..14000
            "NOON" -> time in 5000..7000
            "MIDNIGHT" -> time in 17000..19000
            else -> {
                // Check for specific time ranges
                val targetTime = value.toLongOrNull()
                if (targetTime != null) {
                    kotlin.math.abs(time - targetTime) < 1000
                } else false
            }
        }
    }
}

class WeatherCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        val world = player.world
        return when (value.uppercase()) {
            "CLEAR" -> !world.hasStorm() && !world.isThundering
            "RAIN" -> world.hasStorm() && !world.isThundering
            "THUNDER", "STORM" -> world.hasStorm() && world.isThundering
            else -> false
        }
    }
}

class BlockAboveCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        val blockAbove = player.location.clone().add(0.0, 2.0, 0.0).block
        return try {
            val material = Material.valueOf(value.uppercase())
            blockAbove.type == material
        } catch (e: Exception) {
            blockAbove.type.name.contains(value, ignoreCase = true)
        }
    }
}

class BlockBelowCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        val blockBelow = player.location.clone().add(0.0, -1.0, 0.0).block
        return try {
            val material = Material.valueOf(value.uppercase())
            blockBelow.type == material
        } catch (e: Exception) {
            blockBelow.type.name.contains(value, ignoreCase = true)
        }
    }
}

// ===== INVENTORY & ITEMS CONDITIONS =====

class HasItemCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        return try {
            val material = Material.valueOf(value.uppercase())
            player.inventory.contains(material)
        } catch (e: Exception) {
            // Check by item name/lore
            player.inventory.contents.any { item ->
                item != null && (
                        item.type.name.contains(value, ignoreCase = true) ||
                                item.itemMeta?.displayName?.contains(value, ignoreCase = true) == true
                        )
            }
        }
    }
}

class HasNotItemCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        return !HasItemCondition().check(player, value)
    }
}

class InventoryFullCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        val isEmpty = player.inventory.firstEmpty() == -1
        return isEmpty == value.toBoolean()
    }
}

class InventoryEmptyCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        val isEmpty = player.inventory.contents.all { it == null || it.type == Material.AIR }
        return isEmpty == value.toBoolean()
    }
}

class ArmorEquippedCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
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

// ===== STATUS EFFECTS CONDITIONS =====

class HasStatusEffectCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        return try {
            val effectType = PotionEffectType.getByName(value.uppercase())
            effectType?.let { player.hasPotionEffect(it) } ?: false
        } catch (e: Exception) {
            false
        }
    }
}

class HasNotStatusEffectCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        return !HasStatusEffectCondition().check(player, value)
    }
}

// ===== TEAM & SOCIAL CONDITIONS =====

class IsOnTeamCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        // This would integrate with your team system
        // For now, check scoreboard teams
        val scoreboard = player.scoreboard
        val team = scoreboard.getPlayerTeam(player)
        return when {
            value.equals("NONE", ignoreCase = true) -> team == null
            team != null -> team.name.equals(value, ignoreCase = true) ||
                    team.displayName.equals(value, ignoreCase = true)
            else -> false
        }
    }
}

class TeamSizeCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
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

// ===== ACHIEVEMENT CONDITIONS =====

class HasAchievementCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        // This would integrate with your achievement system
        // For now, check advancement system or metadata
        try {
            val advancement = Bukkit.getAdvancement(org.bukkit.NamespacedKey.minecraft(value.lowercase()))
            if (advancement != null) {
                val progress = player.getAdvancementProgress(advancement)
                return progress.isDone
            }

            // Check custom achievements via metadata or persistent data
            val key = org.bukkit.NamespacedKey.minecraft("achievement_$value")
            return player.persistentDataContainer.has(key, org.bukkit.persistence.PersistentDataType.STRING)
        } catch (e: Exception) {
            return false
        }
    }
}

class NotHasAchievementCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        return !HasAchievementCondition().check(player, value)
    }
}

// ===== COMBAT & ENTITY CONDITIONS =====

class IsInCombatCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        // Check if player has combat-related effects or recent damage
        val hasCombatEffects = listOf(
            PotionEffectType.STRENGTH,
            PotionEffectType.RESISTANCE,
            PotionEffectType.REGENERATION
        ).any { player.hasPotionEffect(it) }

        // Check if holding weapon
        val heldItem = player.inventory.itemInMainHand
        val isHoldingWeapon = heldItem.type.name.contains("SWORD") ||
                heldItem.type.name.contains("AXE") ||
                heldItem.type.name.contains("BOW") ||
                heldItem.type.name.contains("CROSSBOW") ||
                heldItem.type.name.contains("TRIDENT")

        // Check for nearby hostile mobs
        val nearbyMobs = player.getNearbyEntities(10.0, 10.0, 10.0)
            .filterIsInstance<org.bukkit.entity.Monster>()
            .any { it.target == player }

        val inCombat = hasCombatEffects || isHoldingWeapon || nearbyMobs
        return inCombat == value.toBoolean()
    }
}

class MobNearbyCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        val radius = 10.0
        val nearbyEntities = player.getNearbyEntities(radius, radius, radius)

        return try {
            val entityType = org.bukkit.entity.EntityType.valueOf(value.uppercase())
            nearbyEntities.any { it.type == entityType }
        } catch (e: Exception) {
            // Check by name contains
            nearbyEntities.any { entity ->
                entity.type.name.contains(value, ignoreCase = true) ||
                        (entity.customName?.contains(value, ignoreCase = true) ?: false)
            }
        }
    }
}

// ===== MISC CONDITIONS =====

class RandomChanceCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        return when {
            value.startsWith("<=") -> kotlin.random.Random.nextDouble() <= value.substring(2).toDoubleOrNull() ?: 0.0
            value.startsWith(">=") -> kotlin.random.Random.nextDouble() >= value.substring(2).toDoubleOrNull() ?: 1.0
            value.startsWith("<") -> kotlin.random.Random.nextDouble() < value.substring(1).toDoubleOrNull() ?: 0.0
            value.startsWith(">") -> kotlin.random.Random.nextDouble() > value.substring(1).toDoubleOrNull() ?: 1.0
            else -> kotlin.random.Random.nextDouble() < value.toDoubleOrNull() ?: 0.5
        }
    }
}

class BlockCountNearbyCondition : LuckyBlockCondition {
    override fun check(player: Player, value: String): Boolean {
        val parts = value.split(":")
        if (parts.size != 2) return false

        val materialName = parts[0]
        val countRequirement = parts[1]

        val radius = 5
        val playerLoc = player.location
        var count = 0

        try {
            val material = Material.valueOf(materialName.uppercase())
            for (x in -radius..radius) {
                for (y in -radius..radius) {
                    for (z in -radius..radius) {
                        val checkLoc = playerLoc.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
                        if (checkLoc.block.type == material) {
                            count++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            return false
        }

        return when {
            countRequirement.startsWith(">=") -> count >= countRequirement.substring(2).toIntOrNull() ?: 0
            countRequirement.startsWith("<=") -> count <= countRequirement.substring(2).toIntOrNull() ?: Int.MAX_VALUE
            countRequirement.startsWith(">") -> count > countRequirement.substring(1).toIntOrNull() ?: 0
            countRequirement.startsWith("<") -> count < countRequirement.substring(1).toIntOrNull() ?: Int.MAX_VALUE
            countRequirement.startsWith("=") -> count == countRequirement.substring(1).toIntOrNull() ?: -1
            else -> count >= countRequirement.toIntOrNull() ?: 0
        }
    }
}