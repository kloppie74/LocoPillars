package org.Kloppie74.locoPillars.utils.luckyblocks.engine

import org.Kloppie74.locoPillars.LocoPillars
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.Kloppie74.locoPillars.utils.MSG
import kotlin.random.Random

interface LuckyBlockEffect {
    fun execute(player: Player, location: Location, properties: Map<String, Any?>)
}

class EffectRegistry {
    private val effects = mutableMapOf<String, LuckyBlockEffect>()

    init {
        registerAllEffects()
    }

    private fun registerAllEffects() {
        // Combat & Damage Effects
        registerEffect("SPAWN_MOB", SpawnMobEffect())
        registerEffect("SPAWN_PRIME_TNT", SpawnPrimeTntEffect())
        registerEffect("EXPLOSION", ExplosionEffect())
        registerEffect("DAMAGE", DamageEffect())
        registerEffect("SUMMON_LIGHTNING", SummonLightningEffect())
        registerEffect("LAUNCH_PLAYER", LaunchPlayerEffect())
        registerEffect("NUKE", NukeEffect())

        // Item & Loot Effects
        registerEffect("GIVE_ITEM", GiveItemEffect())
        registerEffect("LOOT_RAIN", LootRainEffect())
        registerEffect("DUPLICATE_ITEM", DuplicateItemEffect())
        registerEffect("CURSE_ITEM", CurseItemEffect())
        registerEffect("ENCHANT_ITEM", EnchantItemEffect())
        registerEffect("CLEAR_INVENTORY", ClearInventoryEffect())
        registerEffect("SPAWN_SHULKER_BOX", SpawnShulkerBoxEffect())

        // Movement & Teleport Effects
        registerEffect("TELEPORT", TeleportEffect())
        registerEffect("SWAP_PLACE", SwapPlaceEffect())
        registerEffect("RANDOM_TP_ALL", RandomTpAllEffect())
        registerEffect("SLOWFALL", SlowFallEffect())

        // Block & World Effects
        registerEffect("TRAIL", TrailEffect())
        registerEffect("BLOCK_TRAIL", BlockTrailEffect())
        registerEffect("SET_FIRE", SetFireEffect())
        registerEffect("CHANGE_WEATHER", ChangeWeatherEffect())
        registerEffect("CHANGE_TIME", ChangeTimeEffect())
        registerEffect("BLOCK_REPLACE", BlockReplaceEffect())
        registerEffect("SET_BLOCK_AREA", SetBlockAreaEffect())
        registerEffect("SUMMON_ANVILS", SummonAnvilsEffect())

        // Potion & Status Effects
        registerEffect("GIVE_EFFECT", GiveEffectEffect())
        registerEffect("REMOVE_EFFECT", RemoveEffectEffect())
        registerEffect("HEAL", HealEffect())
        registerEffect("FREEZE", FreezeEffect())
        registerEffect("UNFREEZE", UnfreezeEffect())
        registerEffect("SHRINK", ShrinkEffect())
        registerEffect("GROW", GrowEffect())

        // Spell Effects
        registerEffect("CAST_SPELL", CastSpellEffect())

        // Misc Effects
        registerEffect("PLAY_SOUND", PlaySoundEffect())
        registerEffect("GIVE_XP", GiveXpEffect())
        registerEffect("SPAWN_STRUCTURE", SpawnStructureEffect())
        registerEffect("SPAWN_VEHICLE", SpawnVehicleEffect())
        registerEffect("SUMMON_PARTICLES", SummonParticlesEffect())
        registerEffect("CHANGE_GAMEMODE", ChangeGamemodeEffect())
        registerEffect("DISGUISE_MOB", DisguiseMobEffect())
        registerEffect("SPAWN_BOSS", SpawnBossEffect())
        registerEffect("SUMMON_PORTAL", SummonPortalEffect())
        registerEffect("SUMMON_MINIONS", SummonMinionsEffect())
        registerEffect("UNLOCK_ACHIEVEMENT", UnlockAchievementEffect())
        registerEffect("SWAP_HEALTH", SwapHealthEffect())
        registerEffect("SET_HUNGER", SetHungerEffect())
        registerEffect("SWAP_ARMOR", SwapArmorEffect())
        registerEffect("SUMMON_FIREWORKS", SummonFireworksEffect())
        registerEffect("CUSTOM_COMMAND", CustomCommandEffect())
    }

    fun registerEffect(name: String, effect: LuckyBlockEffect) {
        effects[name.uppercase()] = effect
    }

    fun executeEffect(player: Player, location: Location, effectName: String, properties: Map<String, Any?>) {
        val effect = effects[effectName.uppercase()]
        if (effect != null) {
            effect.execute(player, location, properties)
        } else {
            player.sendMessage(MSG.chatColors("&cUnknown effect: $effectName"))
        }
    }

    fun getRegisteredEffects(): Set<String> = effects.keys
}

// ===== COMBAT & DAMAGE EFFECTS =====

class SpawnMobEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val mobType = EntityType.valueOf((properties["mob_type"] as? String ?: "ZOMBIE").uppercase())
        val amount = (properties["amount"] as? Number)?.toInt() ?: 1
        val radius = (properties["radius"] as? Number)?.toDouble() ?: 3.0
        val aggro = properties["aggro"] as? Boolean ?: false
        val message = properties["message"] as? String

        repeat(amount) {
            val spawnLoc = location.clone().add(
                Random.nextDouble(-radius, radius),
                0.0,
                Random.nextDouble(-radius, radius)
            )
            val entity = player.world.spawnEntity(spawnLoc, mobType)
            if (aggro && entity is Monster) {
                (entity as Monster).target = player
            }
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

// ===== SPELL EFFECTS =====

class CastSpellEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val spells = (properties["spell"] as? List<*>)?.mapNotNull { it as? String } ?: listOf("FIREBALL")
        val radius = (properties["radius"] as? Number)?.toDouble() ?: 5.0
        val message = properties["message"] as? String

        val selectedSpell = spells.random().uppercase()
        executeSpell(player, location, selectedSpell, radius)

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }

    private fun executeSpell(player: Player, location: Location, spell: String, radius: Double) {
        when (spell) {
            "FANGS" -> {
                repeat(8) {
                    val fangLoc = location.clone().add(
                        Random.nextDouble(-radius, radius),
                        0.0,
                        Random.nextDouble(-radius, radius)
                    )
                    player.world.spawnEntity(fangLoc, EntityType.EVOKER_FANGS)
                }
            }
            "LEVITATION" -> player.addPotionEffect(PotionEffect(PotionEffectType.LEVITATION, 100, 1))
            "WITHER" -> player.addPotionEffect(PotionEffect(PotionEffectType.WITHER, 100, 1))
            "POISON" -> player.addPotionEffect(PotionEffect(PotionEffectType.POISON, 200, 1))
            "BLINDNESS" -> player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 200, 0))
            "SLOWNESS" -> player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 200, 2))
            "SPEED" -> player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 400, 2))
            "STRENGTH" -> player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 400, 1))
            "INVISIBILITY" -> player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 400, 0))
            "REGENERATION" -> player.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 200, 2))
            "FIREBALL" -> {
                val fireball = player.world.spawn(player.eyeLocation, Fireball::class.java)
                fireball.direction = player.location.direction
                fireball.yield = 2.0f
            }
            "LIGHTNING" -> player.world.strikeLightning(player.location)
            "EXPLOSION" -> player.world.createExplosion(player.location, 3.0f)
            "TELEPORT" -> {
                val randomLoc = location.clone().add(
                    Random.nextDouble(-radius * 2, radius * 2),
                    Random.nextDouble(-5.0, 5.0),
                    Random.nextDouble(-radius * 2, radius * 2)
                )
                player.teleport(randomLoc)
            }
            "HEAL" -> {
                val newHealth = kotlin.math.min(player.health + 10.0, player.maxHealth)
                player.health = newHealth
            }
            "HUNGER" -> player.addPotionEffect(PotionEffect(PotionEffectType.HUNGER, 300, 2))
            "WEAKNESS" -> player.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 300, 1))
            "NAUSEA" -> player.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 200, 0))
            "CONFUSION" -> player.addPotionEffect(PotionEffect(PotionEffectType.NAUSEA, 200, 1))
            "DAMAGE" -> player.damage(4.0)
            "SLOWFALL" -> player.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 400, 0))
            "JUMP_BOOST" -> player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 400, 3))
            "FIRE" -> player.fireTicks = 100
            "WATER" -> {
                // Summon water blocks around player
                for (x in -1..1) {
                    for (z in -1..1) {
                        val waterLoc = player.location.clone().add(x.toDouble(), 1.0, z.toDouble())
                        if (waterLoc.block.type == Material.AIR) {
                            waterLoc.block.type = Material.WATER
                        }
                    }
                }
            }
            "MINION_SUMMON" -> {
                val minion = player.world.spawnEntity(location, EntityType.IRON_GOLEM) as IronGolem
                minion.customName = "${player.name}'s Minion"
                minion.isCustomNameVisible = true
            }
            "FREEZE" -> {
                player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 200, 255))
                player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 200, 128))
            }
            "UNFREEZE" -> {
                player.removePotionEffect(PotionEffectType.SLOWNESS)
                player.removePotionEffect(PotionEffectType.JUMP_BOOST)
            }
            "SHRINK" -> {
                player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 600, 1))
                player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 600, 2))
            }
            "GROW" -> {
                player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 600, 1))
                player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, 600, 1))
            }
        }
    }
}

// ===== MISC EFFECTS =====

class PlaySoundEffect : LuckyBlockEffect {
    companion object {
        // Dit is je cache van alle geldige sound-namen (uppercase)
        private val validSounds = Sound::class.java.enumConstants
            ?.map { it.toString() }
            ?.toSet() ?: emptySet()
    }

    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val soundName = (properties["sound"] as? String)?.uppercase() ?: "ENTITY_PLAYER_LEVELUP"
        val volume = (properties["volume"] as? Number)?.toFloat() ?: 1.0f
        val pitch = (properties["pitch"] as? Number)?.toFloat() ?: 1.0f
        val message = properties["message"] as? String

        // Check of de sound bestaat in de cache
        val bukkitSound = if (soundName in validSounds) {
            // Haal het Sound enum object via reflection
            Sound::class.java.enumConstants?.firstOrNull { it.toString() == soundName }
                ?: Sound.ENTITY_PLAYER_LEVELUP
        } else {
            Sound.ENTITY_PLAYER_LEVELUP
        }

        player.playSound(player.location, bukkitSound, volume, pitch)
        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class GiveXpEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val amount = (properties["amount"] as? Number)?.toInt() ?: 100
        val message = properties["message"] as? String

        player.giveExp(amount)
        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SpawnStructureEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val structureName = properties["structure_name"] as? String ?: "HOUSE"
        val message = properties["message"] as? String

        // Create a simple structure based on name
        when (structureName.uppercase()) {
            "HOUSE" -> buildHouse(location)
            "TOWER" -> buildTower(location)
            "BRIDGE" -> buildBridge(location)
            "PYRAMID" -> buildPyramid(location)
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }

    private fun buildHouse(location: Location) {
        val world = location.world ?: return
        val baseLoc = location.clone().add(0.0, 1.0, 0.0)

        // Build walls
        for (x in -2..2) {
            for (z in -2..2) {
                for (y in 0..3) {
                    val blockLoc = baseLoc.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
                    when {
                        y == 0 -> blockLoc.block.type = Material.STONE // Floor
                        y == 3 -> blockLoc.block.type = Material.OAK_PLANKS // Roof
                        x == -2 || x == 2 || z == -2 || z == 2 -> {
                            if (y == 1 && x == 0 && z == -2) {
                                blockLoc.block.type = Material.OAK_DOOR // Door
                            } else if (y == 2 && (x == -1 || x == 1) && z == -2) {
                                blockLoc.block.type = Material.GLASS_PANE // Windows
                            } else {
                                blockLoc.block.type = Material.OAK_PLANKS // Walls
                            }
                        }
                        else -> blockLoc.block.type = Material.AIR // Interior
                    }
                }
            }
        }
    }

    private fun buildTower(location: Location) {
        val world = location.world ?: return
        val baseLoc = location.clone()

        for (y in 0..10) {
            for (x in -1..1) {
                for (z in -1..1) {
                    val blockLoc = baseLoc.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
                    when {
                        x == -1 || x == 1 || z == -1 || z == 1 -> blockLoc.block.type = Material.COBBLESTONE
                        else -> blockLoc.block.type = Material.AIR
                    }
                }
            }
        }
    }

    private fun buildBridge(location: Location) {
        val world = location.world ?: return
        val baseLoc = location.clone()

        for (x in -10..10) {
            for (z in -1..1) {
                val blockLoc = baseLoc.clone().add(x.toDouble(), 0.0, z.toDouble())
                blockLoc.block.type = Material.OAK_PLANKS
                if (z == -1 || z == 1) {
                    val railingLoc = baseLoc.clone().add(x.toDouble(), 1.0, z.toDouble())
                    railingLoc.block.type = Material.OAK_FENCE
                }
            }
        }
    }

    private fun buildPyramid(location: Location) {
        val world = location.world ?: return
        val baseLoc = location.clone()

        for (y in 0..5) {
            val size = 5 - y
            for (x in -size..size) {
                for (z in -size..size) {
                    val blockLoc = baseLoc.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
                    blockLoc.block.type = Material.SANDSTONE
                }
            }
        }
    }
}

class SpawnVehicleEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val vehicleType = (properties["vehicle_type"] as? String ?: "MINECART").uppercase()
        val message = properties["message"] as? String

        try {
            val entityType = EntityType.valueOf(vehicleType)
            val vehicle = player.world.spawnEntity(location.clone().add(0.0, 1.0, 0.0), entityType)
            if (vehicle is Vehicle) {
                vehicle.addPassenger(player)
            }
        } catch (e: Exception) {
            // Fallback to minecart
            val minecart = player.world.spawnEntity(location.clone().add(0.0, 1.0, 0.0), EntityType.MINECART)
            minecart.addPassenger(player)
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SummonParticlesEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val type = (properties["type"] as? String ?: "FLAME").uppercase()
        val amount = (properties["amount"] as? Number)?.toInt() ?: 50
        val message = properties["message"] as? String

        try {
            val particle = Particle.valueOf(type)
            player.world.spawnParticle(particle, location, amount)
        } catch (e: Exception) {
            player.world.spawnParticle(Particle.FLAME, location, amount)
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class ChangeGamemodeEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val gamemode = (properties["gamemode"] as? String ?: "CREATIVE").uppercase()
        val message = properties["message"] as? String

        try {
            val gm = GameMode.valueOf(gamemode)
            player.gameMode = gm
        } catch (e: Exception) {
            player.gameMode = GameMode.CREATIVE
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class DisguiseMobEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val mobType = (properties["mob_type"] as? String ?: "SHEEP").uppercase()
        val duration = (properties["duration"] as? Number)?.toInt() ?: 600
        val message = properties["message"] as? String

        // Note: This would require LibsDisguises or similar plugin
        // For now, we'll simulate with effects and items
        when (mobType) {
            "SHEEP" -> {
                val wool = ItemStack(Material.WHITE_WOOL)
                val woolMeta = wool.itemMeta
                woolMeta?.setDisplayName(MSG.chatColors("&fSheep Disguise"))
                wool.itemMeta = woolMeta
                player.inventory.helmet = wool

                // Remove after duration
                object : BukkitRunnable() {
                    override fun run() {
                        if (player.inventory.helmet?.type == Material.WHITE_WOOL) {
                            player.inventory.helmet = null
                        }
                    }
                }.runTaskLater(org.bukkit.Bukkit.getPluginManager().plugins.first(), duration.toLong())
            }
            "CREEPER" -> {
                val head = ItemStack(Material.CREEPER_HEAD)
                player.inventory.helmet = head
                player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, duration, 1))
            }
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SpawnBossEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val bossType = (properties["boss_type"] as? String ?: "WITHER").uppercase()
        val message = properties["message"] as? String

        val spawnLoc = location.clone().add(0.0, 2.0, 0.0)
        when (bossType) {
            "WITHER" -> {
                val wither = player.world.spawnEntity(spawnLoc, EntityType.WITHER) as Wither
                wither.target = player
            }
            "ENDER_DRAGON" -> {
                val dragon = player.world.spawnEntity(spawnLoc, EntityType.ENDER_DRAGON) as EnderDragon
                dragon.target = player
            }
            "ELDER_GUARDIAN" -> {
                val guardian = player.world.spawnEntity(spawnLoc, EntityType.ELDER_GUARDIAN) as ElderGuardian
                guardian.target = player
            }
            else -> {
                // Fallback to strong zombie
                val zombie = player.world.spawnEntity(spawnLoc, EntityType.ZOMBIE) as Zombie
                zombie.equipment?.helmet = ItemStack(Material.DIAMOND_HELMET)
                zombie.equipment?.chestplate = ItemStack(Material.DIAMOND_CHESTPLATE)
                zombie.equipment?.leggings = ItemStack(Material.DIAMOND_LEGGINGS)
                zombie.equipment?.boots = ItemStack(Material.DIAMOND_BOOTS)
                zombie.equipment?.setItemInMainHand(ItemStack(Material.DIAMOND_SWORD))
                zombie.target = player
                zombie.customName = "Boss Zombie"
                zombie.isCustomNameVisible = true
            }
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SummonPortalEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val destination = properties["destination"] as? String ?: "NETHER"
        val message = properties["message"] as? String

        // Build a simple nether portal structure
        val baseLoc = location.clone()

        // Build obsidian frame
        for (y in 0..4) {
            for (x in 0..3) {
                val blockLoc = baseLoc.clone().add(x.toDouble(), y.toDouble(), 0.0)
                if (y == 0 || y == 4 || x == 0 || x == 3) {
                    blockLoc.block.type = Material.OBSIDIAN
                } else {
                    blockLoc.block.type = Material.NETHER_PORTAL
                }
            }
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SummonMinionsEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val mobType = EntityType.valueOf((properties["mob_type"] as? String ?: "WOLF").uppercase())
        val amount = (properties["amount"] as? Number)?.toInt() ?: 3
        val message = properties["message"] as? String

        repeat(amount) {
            val minionLoc = location.clone().add(
                Random.nextDouble(-3.0, 3.0),
                0.0,
                Random.nextDouble(-3.0, 3.0)
            )
            val minion = player.world.spawnEntity(minionLoc, mobType)

            // Make them friendly if possible
            when (minion) {
                is Wolf -> {
                    (minion as Wolf).owner = player
                    (minion as Wolf).isTamed = true
                }
                is Cat -> {
                    (minion as Cat).owner = player
                    (minion as Cat).isTamed = true
                }
                is IronGolem -> {
                    minion.customName = "${player.name}'s Minion"
                    minion.isCustomNameVisible = true
                }
            }
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class UnlockAchievementEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val achievementId = properties["achievement_id"] as? String ?: "lucky_block_master"
        val message = properties["message"] as? String

        // This would integrate with your achievement system
        // For now, just send a message
        player.sendMessage(MSG.chatColors("&6Achievement Unlocked: &e$achievementId"))
        player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f)

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SwapHealthEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val message = properties["message"] as? String
        val onlinePlayers = Bukkit.getOnlinePlayers().filter { it != player && it.world == player.world }

        if (onlinePlayers.isNotEmpty()) {
            val targetPlayer = onlinePlayers.random()
            val playerHealth = player.health
            val targetHealth = targetPlayer.health

            player.health = kotlin.math.min(targetHealth, player.maxHealth)
            targetPlayer.health = kotlin.math.min(playerHealth, targetPlayer.maxHealth)

            targetPlayer.sendMessage(MSG.chatColors("&eYour health was swapped with ${player.name}!"))
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SetHungerEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val amount = (properties["amount"] as? Number)?.toInt() ?: 20
        val message = properties["message"] as? String

        player.foodLevel = kotlin.math.max(0, kotlin.math.min(amount, 20))
        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SwapArmorEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val message = properties["message"] as? String
        val onlinePlayers = Bukkit.getOnlinePlayers().filter { it != player && it.world == player.world }

        if (onlinePlayers.isNotEmpty()) {
            val targetPlayer = onlinePlayers.random()
            val playerArmor = arrayOf(
                player.inventory.helmet?.clone(),
                player.inventory.chestplate?.clone(),
                player.inventory.leggings?.clone(),
                player.inventory.boots?.clone()
            )
            val targetArmor = arrayOf(
                targetPlayer.inventory.helmet?.clone(),
                targetPlayer.inventory.chestplate?.clone(),
                targetPlayer.inventory.leggings?.clone(),
                targetPlayer.inventory.boots?.clone()
            )

            player.inventory.helmet = targetArmor[0]
            player.inventory.chestplate = targetArmor[1]
            player.inventory.leggings = targetArmor[2]
            player.inventory.boots = targetArmor[3]

            targetPlayer.inventory.helmet = playerArmor[0]
            targetPlayer.inventory.chestplate = playerArmor[1]
            targetPlayer.inventory.leggings = playerArmor[2]
            targetPlayer.inventory.boots = playerArmor[3]

            targetPlayer.sendMessage(MSG.chatColors("&eYour armor was swapped with ${player.name}!"))
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SummonFireworksEffect : LuckyBlockEffect {
    companion object {
        private val colorMap = mapOf(
            "AQUA" to org.bukkit.Color.AQUA,
            "BLACK" to org.bukkit.Color.BLACK,
            "BLUE" to org.bukkit.Color.BLUE,
            "FUCHSIA" to org.bukkit.Color.FUCHSIA,
            "GRAY" to org.bukkit.Color.GRAY,
            "GREEN" to org.bukkit.Color.GREEN,
            "LIME" to org.bukkit.Color.LIME,
            "MAROON" to org.bukkit.Color.MAROON,
            "NAVY" to org.bukkit.Color.NAVY,
            "OLIVE" to org.bukkit.Color.OLIVE,
            "ORANGE" to org.bukkit.Color.ORANGE,
            "PURPLE" to org.bukkit.Color.PURPLE,
            "RED" to org.bukkit.Color.RED,
            "SILVER" to org.bukkit.Color.SILVER,
            "TEAL" to org.bukkit.Color.TEAL,
            "WHITE" to org.bukkit.Color.WHITE,
            "YELLOW" to org.bukkit.Color.YELLOW
        )

        fun toColor(str: String?): org.bukkit.Color =
            colorMap[str?.uppercase() ?: "RED"] ?: org.bukkit.Color.RED
    }

    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val amount = (properties["amount"] as? Number)?.toInt() ?: 5
        val colors = (properties["colors"] as? List<*>)?.mapNotNull {
            toColor(it as? String)
        } ?: listOf(org.bukkit.Color.RED, org.bukkit.Color.BLUE, org.bukkit.Color.GREEN)
        val power = (properties["power"] as? Number)?.toInt() ?: 1
        val message = properties["message"] as? String

        repeat(amount) {
            val fireworkLoc = location.clone().add(
                Random.nextDouble(-5.0, 5.0),
                Random.nextDouble(1.0, 3.0),
                Random.nextDouble(-5.0, 5.0)
            )

            val firework = player.world.spawnEntity(fireworkLoc, EntityType.FIREWORK_ROCKET) as Firework
            val meta = firework.fireworkMeta

            val effect = FireworkEffect.builder()
                .with(FireworkEffect.Type.BALL)
                .withColor(colors)
                .withFade(org.bukkit.Color.WHITE)
                .flicker(Random.nextBoolean())
                .trail(Random.nextBoolean())
                .build()

            meta.addEffect(effect)
            meta.power = power
            firework.fireworkMeta = meta
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class CustomCommandEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val command = properties["command"] as? String ?: "say Hello World!"
        val message = properties["message"] as? String

        // Replace placeholders
        val processedCommand = command
            .replace("%player%", player.name)
            .replace("%x%", location.blockX.toString())
            .replace("%y%", location.blockY.toString())
            .replace("%z%", location.blockZ.toString())
            .replace("%world%", location.world?.name ?: "world")

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), processedCommand)
        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SpawnPrimeTntEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val amount = (properties["amount"] as? Number)?.toInt() ?: 1
        val radius = (properties["radius"] as? Number)?.toDouble() ?: 2.0
        val fuseTicks = (properties["fuse_ticks"] as? Number)?.toInt() ?: 80
        val message = properties["message"] as? String

        repeat(amount) {
            val spawnLoc = location.clone().add(
                Random.nextDouble(-radius, radius),
                1.0,
                Random.nextDouble(-radius, radius)
            )
            val tnt = player.world.spawnEntity(spawnLoc, EntityType.TNT) as TNTPrimed
            tnt.fuseTicks = fuseTicks
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class ExplosionEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val power = (properties["power"] as? Number)?.toFloat() ?: 2.0f
        val fire = properties["fire"] as? Boolean ?: false
        val damage = properties["damage"] as? Boolean ?: true
        val message = properties["message"] as? String

        player.world.createExplosion(location, power, fire, damage)
        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class DamageEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val amount = (properties["amount"] as? Number)?.toDouble() ?: 2.0
        val type = properties["type"] as? String ?: "MAGIC"
        val message = properties["message"] as? String

        try {
            val damageType = org.bukkit.event.entity.EntityDamageEvent.DamageCause.valueOf(type.uppercase())
            player.damage(amount)
        } catch (e: Exception) {
            player.damage(amount)
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SummonLightningEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val amount = (properties["amount"] as? Number)?.toInt() ?: 1
        val radius = (properties["radius"] as? Number)?.toDouble() ?: 0.0
        val message = properties["message"] as? String

        repeat(amount) {
            val strikeLoc = if (radius > 0) {
                location.clone().add(
                    Random.nextDouble(-radius, radius),
                    0.0,
                    Random.nextDouble(-radius, radius)
                )
            } else location

            player.world.strikeLightning(strikeLoc)
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class LaunchPlayerEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val power = (properties["power"] as? Number)?.toDouble() ?: 2.0
        val direction = (properties["direction"] as? String ?: "UP").uppercase()
        val message = properties["message"] as? String

        val velocity = when (direction) {
            "UP" -> org.bukkit.util.Vector(0.0, power, 0.0)
            "NORTH" -> org.bukkit.util.Vector(0.0, power * 0.5, -power)
            "SOUTH" -> org.bukkit.util.Vector(0.0, power * 0.5, power)
            "EAST" -> org.bukkit.util.Vector(power, power * 0.5, 0.0)
            "WEST" -> org.bukkit.util.Vector(-power, power * 0.5, 0.0)
            else -> org.bukkit.util.Vector(0.0, power, 0.0)
        }

        player.velocity = velocity
        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class NukeEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val radius = (properties["radius"] as? Number)?.toFloat() ?: 10.0f
        val message = properties["message"] as? String

        // Create multiple explosions for nuke effect
        for (i in 0..5) {
            val delay = i * 10L
            object : BukkitRunnable() {
                override fun run() {
                    val explodeLoc = location.clone().add(
                        Random.nextDouble((-radius/2).toDouble(), (radius/2).toDouble()),
                        Random.nextDouble(0.0, (radius/3).toDouble()),
                        Random.nextDouble((-radius/2).toDouble(), (radius/2).toDouble())
                    )
                    player.world.createExplosion(explodeLoc, radius, false, true)
                }
            }.runTaskLater(org.bukkit.Bukkit.getPluginManager().plugins.first(), delay)
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

// ===== ITEM & LOOT EFFECTS =====

class GiveItemEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val material = Material.valueOf((properties["material"] as? String ?: "DIAMOND").uppercase())
        val amount = (properties["amount"] as? Number)?.toInt() ?: 1
        val name = properties["name"] as? String
        val lore = (properties["lore"] as? List<*>)?.mapNotNull { it as? String }
        val customModelData = (properties["custom_model_data"] as? Number)?.toInt()
        val message = properties["message"] as? String

        val item = ItemStack(material, amount)
        val meta = item.itemMeta

        if (meta != null) {
            name?.let { meta.setDisplayName(MSG.chatColors(it)) }
            lore?.let { meta.lore = it.map { line -> MSG.chatColors(line) } }
            customModelData?.let { meta.setCustomModelData(it) }
            item.itemMeta = meta
        }

        if (player.inventory.firstEmpty() != -1) {
            player.inventory.addItem(item)
        } else {
            player.world.dropItemNaturally(player.location, item)
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class LootRainEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val items = (properties["items"] as? List<*>)?.mapNotNull { it as? String } ?: listOf("DIAMOND")
        val amount = (properties["amount"] as? Number)?.toInt() ?: 10
        val radius = (properties["radius"] as? Number)?.toDouble() ?: 5.0
        val message = properties["message"] as? String

        repeat(amount) {
            val material = Material.valueOf(items.random().uppercase())
            val item = ItemStack(material)
            val dropLoc = location.clone().add(
                Random.nextDouble(-radius, radius),
                Random.nextDouble(5.0, 15.0),
                Random.nextDouble(-radius, radius)
            )
            player.world.dropItemNaturally(dropLoc, item)
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class DuplicateItemEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val message = properties["message"] as? String
        val heldItem = player.inventory.itemInMainHand

        if (heldItem.type != Material.AIR) {
            val duplicate = heldItem.clone()
            if (player.inventory.firstEmpty() != -1) {
                player.inventory.addItem(duplicate)
            } else {
                player.world.dropItemNaturally(player.location, duplicate)
            }
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class CurseItemEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val curseType = properties["curse_type"] as? String ?: "VANISHING"
        val message = properties["message"] as? String
        val heldItem = player.inventory.itemInMainHand

        if (heldItem.type != Material.AIR) {
            val meta = heldItem.itemMeta
            if (meta != null) {
                try {
                    val curse = org.bukkit.enchantments.Enchantment.getByKey(
                        NamespacedKey.minecraft("curse_of_${curseType.lowercase()}")
                    )
                    curse?.let { meta.addEnchant(it, 1, true) }
                    heldItem.itemMeta = meta
                } catch (e: Exception) {
                    // Fallback to binding curse
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.BINDING_CURSE, 1, true)
                    heldItem.itemMeta = meta
                }
            }
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class EnchantItemEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val enchant = properties["enchant"] as? String ?: "SHARPNESS"
        val level = (properties["level"] as? Number)?.toInt() ?: 1
        val message = properties["message"] as? String
        val heldItem = player.inventory.itemInMainHand

        if (heldItem.type != Material.AIR) {
            val meta = heldItem.itemMeta
            if (meta != null) {
                try {
                    val enchantment = org.bukkit.enchantments.Enchantment.getByKey(
                        NamespacedKey.minecraft(enchant.lowercase())
                    )
                    enchantment?.let { meta.addEnchant(it, level, true) }
                    heldItem.itemMeta = meta
                } catch (e: Exception) {
                    // Fallback enchantment
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, level, true)
                    heldItem.itemMeta = meta
                }
            }
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class ClearInventoryEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val message = properties["message"] as? String

        player.inventory.clear()
        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SpawnShulkerBoxEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val lootTable = properties["loot_table"] as? String
        val message = properties["message"] as? String

        val shulkerBox = ItemStack(Material.SHULKER_BOX)
        val meta = shulkerBox.itemMeta as? org.bukkit.inventory.meta.BlockStateMeta

        if (meta != null) {
            val shulker = meta.blockState as? org.bukkit.block.ShulkerBox
            if (shulker != null) {
                // Add some random loot
                val lootItems = listOf(
                    ItemStack(Material.DIAMOND, Random.nextInt(1, 4)),
                    ItemStack(Material.EMERALD, Random.nextInt(1, 6)),
                    ItemStack(Material.GOLD_INGOT, Random.nextInt(2, 8))
                )

                lootItems.forEach { item ->
                    shulker.inventory.addItem(item)
                }

                meta.blockState = shulker
                shulkerBox.itemMeta = meta
            }
        }

        player.world.dropItemNaturally(location, shulkerBox)
        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

// ===== MOVEMENT & TELEPORT EFFECTS =====

class TeleportEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val radius = (properties["radius"] as? Number)?.toDouble() ?: 50.0
        val message = properties["message"] as? String

        val world = player.world
        var attempts = 0
        var safeLocation: Location? = null

        while (attempts < 10 && safeLocation == null) {
            val randomLoc = location.clone().add(
                Random.nextDouble(-radius, radius),
                Random.nextDouble(-10.0, 10.0),
                Random.nextDouble(-radius, radius)
            )

            if (randomLoc.block.type == Material.AIR &&
                randomLoc.clone().add(0.0, 1.0, 0.0).block.type == Material.AIR) {
                safeLocation = randomLoc
            }
            attempts++
        }

        if (safeLocation != null) {
            player.teleport(safeLocation)
        } else {
            player.teleport(location.clone().add(0.0, 5.0, 0.0))
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SwapPlaceEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val message = properties["message"] as? String
        val onlinePlayers = Bukkit.getOnlinePlayers().filter { it != player }

        if (onlinePlayers.isNotEmpty()) {
            val targetPlayer = onlinePlayers.random()
            val playerLoc = player.location.clone()
            val targetLoc = targetPlayer.location.clone()

            player.teleport(targetLoc)
            targetPlayer.teleport(playerLoc)

            targetPlayer.sendMessage(MSG.chatColors("&eYou swapped places with ${player.name}!"))
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class RandomTpAllEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val radius = (properties["radius"] as? Number)?.toDouble() ?: 100.0
        val message = properties["message"] as? String

        Bukkit.getOnlinePlayers().forEach { p ->
            val randomLoc = location.clone().add(
                Random.nextDouble(-radius, radius),
                Random.nextDouble(0.0, 10.0),
                Random.nextDouble(-radius, radius)
            )
            p.teleport(randomLoc)
            p.sendMessage(MSG.chatColors("&eEveryone was randomly teleported!"))
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SlowFallEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val duration = (properties["duration"] as? Number)?.toInt() ?: 300
        val message = properties["message"] as? String

        player.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, duration, 0))
        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

// ===== BLOCK & WORLD EFFECTS =====

class TrailEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val block = Material.valueOf((properties["block"] as? String ?: "GOLD_BLOCK").uppercase())
        val duration = (properties["duration"] as? Number)?.toInt() ?: 100
        val radius = (properties["radius"] as? Number)?.toInt() ?: 1
        val message = properties["message"] as? String

        // Create a trail task that follows the player
        object : BukkitRunnable() {
            var ticks = 0
            var lastLoc = player.location.clone()

            override fun run() {
                if (ticks >= duration || !player.isOnline) {
                    cancel()
                    return
                }

                val currentLoc = player.location
                if (currentLoc.distance(lastLoc) > 1.0) {
                    for (x in -radius..radius) {
                        for (z in -radius..radius) {
                            val trailLoc = lastLoc.clone().add(x.toDouble(), -1.0, z.toDouble())
                            val trailBlock = trailLoc.block
                            if (trailBlock.type == Material.AIR || trailBlock.type.isSolid.not()) {
                                trailBlock.type = block

                                // Remove block after a delay
                                object : BukkitRunnable() {
                                    override fun run() {
                                        if (trailBlock.type == block) {
                                            trailBlock.type = Material.AIR
                                        }
                                    }
                                }.runTaskLater(org.bukkit.Bukkit.getPluginManager().plugins.first(), 100L)
                            }
                        }
                    }
                    lastLoc = currentLoc.clone()
                }
                ticks++
            }
        }.runTaskTimer(org.bukkit.Bukkit.getPluginManager().plugins.first(), 0L, 1L)

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class BlockTrailEffect : LuckyBlockEffect {

    companion object {
        // Track active trails per player to prevent duplicates
        private val activeTrails = mutableMapOf<Player, BukkitRunnable>()
        private val trailBlocks = mutableMapOf<Player, MutableSet<org.bukkit.block.Block>>()

        fun stopPlayerTrail(player: Player) {
            // Cancel existing trail task
            activeTrails[player]?.cancel()
            activeTrails.remove(player)

            // Clean up trail blocks
            trailBlocks[player]?.forEach { block ->
                if (block.type.name.contains("ICE") || block.type.name.contains("GLASS")) {
                    block.type = Material.AIR
                }
            }
            trailBlocks[player]?.clear()
            trailBlocks.remove(player)
        }
    }

    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val block = Material.valueOf((properties["block"] as? String ?: "ICE").uppercase())
        val durationSeconds = (properties["duration"] as? Number)?.toInt() ?: 15
        val radius = (properties["radius"] as? Number)?.toInt() ?: 1
        val message = properties["message"] as? String
        val replaceBlocks = (properties["replace_blocks"] as? List<String>) ?: listOf("AIR", "WATER")
        val restoreOriginal = (properties["restore_original"] as? Boolean) ?: true
        val trailDurationTicks = (properties["trail_duration"] as? Number)?.toInt() ?: (durationSeconds * 20)

        // Stop any existing trail for this player first
        stopPlayerTrail(player)

        // Track placed blocks for this player
        val playerTrailBlocks = mutableSetOf<org.bukkit.block.Block>()
        trailBlocks[player] = playerTrailBlocks

        // Store original blocks for restoration
        val originalBlocks = mutableMapOf<org.bukkit.block.Block, Material>()

        // Convert duration from seconds to ticks
        val totalTicks = durationSeconds * 20

        val trailTask = object : BukkitRunnable() {
            var ticks = 0
            var lastLocation: org.bukkit.Location? = null

            override fun run() {
                // Check if duration expired or player offline
                if (ticks >= totalTicks || !player.isOnline) {
                    // Clean up when effect ends
                    if (restoreOriginal) {
                        originalBlocks.forEach { (block, originalMaterial) ->
                            if (playerTrailBlocks.contains(block)) {
                                block.type = originalMaterial
                            }
                        }
                    }

                    // Remove from tracking
                    activeTrails.remove(player)
                    trailBlocks.remove(player)
                    cancel()
                    return
                }

                val playerLoc = player.location

                // Only place blocks if player moved to a different block
                if (lastLocation == null ||
                    lastLocation!!.blockX != playerLoc.blockX ||
                    lastLocation!!.blockY != playerLoc.blockY ||
                    lastLocation!!.blockZ != playerLoc.blockZ) {

                    // Place trail blocks around player
                    for (x in -radius..radius) {
                        for (z in -radius..radius) {
                            val blockLoc = playerLoc.clone().add(x.toDouble(), -1.0, z.toDouble())
                            val targetBlock = blockLoc.block

                            // Only replace specified block types
                            if (replaceBlocks.contains(targetBlock.type.name)) {
                                // Store original block type
                                if (!originalBlocks.containsKey(targetBlock)) {
                                    originalBlocks[targetBlock] = targetBlock.type
                                }

                                // Place trail block
                                targetBlock.type = block
                                playerTrailBlocks.add(targetBlock)

                                // Schedule individual block removal (if not restoring all at once)
                                if (!restoreOriginal) {
                                    object : BukkitRunnable() {
                                        override fun run() {
                                            if (targetBlock.type == block) {
                                                targetBlock.type = originalBlocks[targetBlock] ?: Material.AIR
                                            }
                                        }
                                    }.runTaskLater(LocoPillars.instance, trailDurationTicks.toLong())
                                }
                            }
                        }
                    }
                    lastLocation = playerLoc.clone()
                }

                ticks += 5 // Check every 5 ticks (0.25 seconds)
            }
        }

        // Store the task so we can cancel it if needed
        activeTrails[player] = trailTask

        // Start the trail effect
        trailTask.runTaskTimer(LocoPillars.instance, 0L, 5L)

        // Send message to player
        message?.let { player.sendMessage(MSG.chatColors(it)) }

        // Debug message
        player.sendMessage(MSG.chatColors("&8[Debug] Started ${block.name} trail for ${durationSeconds} seconds"))
    }
}

class SetFireEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val duration = (properties["duration"] as? Number)?.toInt() ?: 100
        val radius = (properties["radius"] as? Number)?.toDouble() ?: 3.0
        val message = properties["message"] as? String

        // Set player on fire
        player.fireTicks = duration

        // Set nearby blocks on fire
        for (x in -radius.toInt()..radius.toInt()) {
            for (y in -2..2) {
                for (z in -radius.toInt()..radius.toInt()) {
                    val fireLoc = location.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
                    val fireBlock = fireLoc.block
                    if (fireBlock.type == Material.AIR) {
                        fireBlock.type = Material.FIRE
                    }
                }
            }
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class ChangeWeatherEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val toWeather = (properties["to_weather"] as? String ?: "RAIN").uppercase()
        val duration = (properties["duration"] as? Number)?.toInt() ?: 12000
        val message = properties["message"] as? String

        val world = player.world
        when (toWeather) {
            "CLEAR" -> {
                world.setStorm(false)
                world.isThundering = false
            }
            "RAIN" -> {
                world.setStorm(true)
                world.weatherDuration = duration
            }
            "THUNDER" -> {
                world.setStorm(true)
                world.isThundering = true
                world.thunderDuration = duration
            }
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class ChangeTimeEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val time = properties["time"] as? String ?: "DAY"
        val message = properties["message"] as? String

        val world = player.world
        when (time.uppercase()) {
            "DAY" -> world.time = 1000
            "NIGHT" -> world.time = 13000
            "DAWN" -> world.time = 23000
            "DUSK" -> world.time = 12000
            "NOON" -> world.time = 6000
            "MIDNIGHT" -> world.time = 18000
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class BlockReplaceEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val material = Material.valueOf((properties["material"] as? String ?: "DIAMOND_BLOCK").uppercase())
        val message = properties["message"] as? String

        location.block.type = material
        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SetBlockAreaEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val radius = (properties["radius"] as? Number)?.toInt() ?: 3
        val material = Material.valueOf((properties["material"] as? String ?: "GLASS").uppercase())
        val message = properties["message"] as? String

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val blockLoc = location.clone().add(x.toDouble(), y.toDouble(), z.toDouble())
                    blockLoc.block.type = material
                }
            }
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class SummonAnvilsEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val amount = (properties["amount"] as? Number)?.toInt() ?: 5
        val radius = (properties["radius"] as? Number)?.toDouble() ?: 5.0
        val message = properties["message"] as? String

        repeat(amount) {
            val anvilLoc = location.clone().add(
                Random.nextDouble(-radius, radius),
                Random.nextDouble(10.0, 20.0),
                Random.nextDouble(-radius, radius)
            )
            val anvil = player.world.spawnFallingBlock(anvilLoc, Material.ANVIL.createBlockData())
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

// ===== POTION & STATUS EFFECTS =====

class GiveEffectEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val effect = (properties["effect"] as? String ?: "SPEED").uppercase()
        val durationRaw = properties["duration"]
        val duration = when (durationRaw) {
            is Number -> durationRaw.toInt()
            is String -> durationRaw.toIntOrNull() ?: 600
            else -> 600
        }
        val amplifierRaw = properties["amplifier"]
        val amplifier = when (amplifierRaw) {
            is Number -> amplifierRaw.toInt()
            is String -> amplifierRaw.toIntOrNull() ?: 0
            else -> 0
        }
        val message = properties["message"] as? String

        try {
            val potionEffect = PotionEffectType.getByName(effect)
            potionEffect?.let {
                player.addPotionEffect(PotionEffect(it, duration, amplifier))
            }
        } catch (e: Exception) {
            player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, duration, amplifier))
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class RemoveEffectEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val effect = (properties["effect"] as? String ?: "POISON").uppercase()
        val message = properties["message"] as? String

        try {
            val potionEffect = PotionEffectType.getByName(effect)
            potionEffect?.let { player.removePotionEffect(it) }
        } catch (e: Exception) {
            // Remove all negative effects as fallback
            listOf("POISON", "WITHER", "SLOWNESS", "WEAKNESS").forEach { effectName ->
                PotionEffectType.getByName(effectName)?.let { player.removePotionEffect(it) }
            }
        }

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class HealEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val amount = (properties["amount"] as? Number)?.toDouble() ?: 6.0
        val message = properties["message"] as? String

        val newHealth = kotlin.math.min(player.health + amount, player.maxHealth)
        player.health = newHealth

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class FreezeEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val duration = (properties["duration"] as? Number)?.toInt() ?: 100
        val message = properties["message"] as? String

        // Apply slowness and jump boost negative to simulate freezing
        player.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, duration, 255))
        player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, duration, 128))

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class UnfreezeEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val message = properties["message"] as? String

        player.removePotionEffect(PotionEffectType.SLOWNESS)
        player.removePotionEffect(PotionEffectType.JUMP_BOOST)

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class ShrinkEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val duration = (properties["duration"] as? Number)?.toInt() ?: 600
        val message = properties["message"] as? String

        // Note: Player shrinking requires external plugins like LibsDisguises
        // For now, we'll simulate with speed and jump boost
        player.addPotionEffect(PotionEffect(PotionEffectType.SPEED, duration, 1))
        player.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, duration, 2))

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}

class GrowEffect : LuckyBlockEffect {
    override fun execute(player: Player, location: Location, properties: Map<String, Any?>) {
        val duration = (properties["duration"] as? Number)?.toInt() ?: 600
        val message = properties["message"] as? String

        // Simulate growing with strength and resistance
        player.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, duration, 1))
        player.addPotionEffect(PotionEffect(PotionEffectType.RESISTANCE, duration, 1))

        message?.let { player.sendMessage(MSG.chatColors(it)) }
    }
}