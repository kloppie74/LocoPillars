package org.Kloppie74.locoPillars.game.arena

import com.fastasyncworldedit.core.configuration.Settings
import com.fastasyncworldedit.core.extent.processor.lighting.RelightMode
import com.sk89q.worldedit.WorldEdit
import com.sk89q.worldedit.bukkit.BukkitAdapter
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import org.Kloppie74.locoPillars.utils.SmartSchematicCache
import org.Kloppie74.locoPillars.utils.SchematicHelper
import org.Kloppie74.locoPillars.game.config.GameConfig
import java.io.File
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

enum class ArenaStatus {
    AVAILABLE, OCCUPIED, RESETTING
}

data class ArenaTemplate(
    val name: String,
    val configFile: File,
    val schemFiles: List<File>
)

data class ArenaInstance(
    val id: UUID,
    val template: ArenaTemplate,
    val config: GameConfig,
    /** World-absolute origin.  In single-world mode: world=LocoPillars_Gameworld, x=index×5000, y=0, z=0 */
    val origin: Location,
    /** Always [ArenaWorldManager.GAMEWORLD_NAME] in single-world mode. */
    val world: World,
    var status: ArenaStatus = ArenaStatus.AVAILABLE,
    val regionId: String,
    var currentSchematicFile: File? = null
)

class ArenaManager(
    private val plugin: LocoPillars,
    private val worldManager: ArenaWorldManager,
    private val configManager: ConfigManager
) {
    companion object {
        /**
         * Blocks between adjacent arena origins on the X axis.
         *
         * 5 000 blocks of separation is deliberately much larger than any possible
         * render distance (max 32 chunks = 512 blocks), so players in one arena
         * can never see, hear, or interfere with another arena.
         */
        private const val GRID_SPACING = 5000
    }

    private val templates = mutableMapOf<String, ArenaTemplate>()
    private val instances = mutableListOf<ArenaInstance>()

    /**
     * Arena IDs whose reset is currently in progress. Guards [releaseInstance] against
     * being fired multiple times for the same game (last-player-leave + game-end + timer
     * can all call it), which previously caused a map to reset 2-3× and overlapping FAWE
     * pastes to corrupt the arena. An ID is added on reset start and removed on completion.
     */
    private val resettingArenas = java.util.Collections.synchronizedSet(mutableSetOf<UUID>())

    /**
     * Radius (blocks) used for entity-cleanup and player-detection scans.
     * Kept as a separate concept from [GRID_SPACING]:
     *   - [GRID_SPACING]   = distance between arena origins   (5 000 blocks, fixed)
     *   - [instanceRadius] = half-width of each arena's "cell" (from config, default 500 blocks)
     * instanceRadius must be < GRID_SPACING / 2 to ensure cells never overlap.
     */
    private val instanceRadius = configManager.instanceDistance / 2

    /** LRU clipboard cache — at most 5 clipboard objects in RAM during gameplay. */
    private val schematicCache = SmartSchematicCache(plugin, configManager, maxSize = 5)

    /**
     * Fixed-size thread pool used exclusively for IN-GAME arena resets.
     * Startup pastes run synchronously on the main thread (see [preloadInstances]).
     * Two threads means at most 2 arenas reset concurrently during live play,
     * which is enough throughput without spiking the OS process count.
     */
    private val pasteExecutor: ExecutorService = Executors.newFixedThreadPool(2) { r ->
        Thread(r, "LocoPillars-Paste").also { it.isDaemon = true }
    }

    private fun debugLog(message: String) {
        if (configManager.debugMode) plugin.logger.info("[ArenaManager] $message")
    }

    init {
        loadArenaTemplates()
    }

    // ── Template loading (unchanged from original) ────────────────────────────

    private fun loadArenaTemplates() {
        val dataFolder   = File(plugin.dataFolder, "Data")
        val gamesFolder  = File(dataFolder, "Games")
        val schemsFolder = File(dataFolder, "Schems")
        val mapsFolder   = File(plugin.dataFolder, "maps")

        listOf(dataFolder, gamesFolder, schemsFolder).forEach { dir ->
            if (!dir.exists()) { dir.mkdirs(); debugLog("Created: ${dir.absolutePath}") }
        }

        val configs = gamesFolder.listFiles { f -> f.extension == "yml" } ?: emptyArray()
        debugLog("Found ${configs.size} .yml config file(s) in ${gamesFolder.absolutePath}")

        configs.forEach { configFile ->
            val name = configFile.nameWithoutExtension
            val schemFolder = File(schemsFolder, name)
            val schemFiles  = mutableListOf<File>()

            // 1. Data/Schems/<name>/ folder
            if (schemFolder.exists() && schemFolder.isDirectory) {
                schemFolder.listFiles { f -> f.extension == "schem" }?.forEach { f ->
                    if (SchematicHelper.validateSchematicFile(plugin, configManager, f)) {
                        schemFiles += f
                        debugLog("  ✓ $name/${f.name}")
                    }
                }
            }

            // 2. Data/Schems/<name>.schem single file
            if (schemFiles.isEmpty()) {
                val single = File(schemsFolder, "$name.schem")
                if (single.exists() && SchematicHelper.validateSchematicFile(plugin, configManager, single)) {
                    schemFiles += single
                    debugLog("  ✓ $name.schem (single file)")
                }
            }

            // 3. maps/<name>/region.schem legacy fallback
            if (schemFiles.isEmpty()) {
                val legacy = File(mapsFolder, "$name/region.schem")
                if (legacy.exists() && SchematicHelper.validateSchematicFile(plugin, configManager, legacy)) {
                    schemFiles += legacy
                    debugLog("  ✓ $name (legacy maps/ fallback)")
                }
            }

            if (schemFiles.isNotEmpty()) {
                templates[name] = ArenaTemplate(name, configFile, schemFiles)
                debugLog("✓ Template '$name' registered (${schemFiles.size} schematic variant(s))")
            } else {
                plugin.logger.warning("[ArenaManager] No valid schematics for config '$name' — template skipped.")
            }
        }

        plugin.logger.info("[ArenaManager] ${templates.size} arena template(s) loaded.")
    }

    private fun pickRandomSchematic(template: ArenaTemplate): File {
        if (template.schemFiles.isEmpty())
            throw IllegalStateException("No schematic files for template '${template.name}'")
        return template.schemFiles[Random.nextInt(template.schemFiles.size)]
    }

    // ── Instance preload (startup) ────────────────────────────────────────────

    /**
     * Initialises all arena instances and pastes their maps into [ArenaWorldManager.GAMEWORLD_NAME].
     *
     * Single-world grid layout
     * ────────────────────────
     *   Every instance gets a unique X offset:  origin.x = globalIndex × [GRID_SPACING]
     *   Z is always 0.  The 5 000-block gap exceeds the maximum render distance
     *   (32 chunks = 512 blocks), so arenas are invisible and acoustically isolated
     *   from one another.
     *
     * Batch-paste (single FAWE EditSession)
     * ────────────────────────────────────────
     *   All 175 arenas share ONE EditSession opened before the loop and closed
     *   once after.  FAWE accumulates every block change in its internal queue;
     *   Starlight relighting and the chunk-flush to disk fire exactly ONCE when
     *   the session closes — instead of 175 times with the old per-arena approach.
     *
     *   The builder carries the full FAWE 2.15.2 speed-flag set (confirmed via
     *   javap): fastMode / relightMode(NONE) / checkMemory(false) /
     *   limitUnlimited / allowedRegionsEverywhere.  FAWE global settings
     *   LIGHTING.MODE and QUEUE.TARGET_SIZE are overridden for the duration
     *   and restored after close().  Each paste also skips air blocks (void
     *   world) to cut 60–70 % of block-write calls.
     *
     *   Observed throughput (35 configs × 5 instances = 175 arenas):
     *     Before (per-session, no flags): 2–3 s/arena  → 6–8 minutes
     *     After  (batch + all flags):     target < 5 seconds total
     */
    fun preloadInstances(configs: List<GameConfig>, amountPerType: Int) {
        if (templates.isEmpty()) {
            plugin.logger.severe("[ArenaManager] No templates found — cannot preload instances.")
            plugin.logger.severe("[ArenaManager]  • .yml configs → plugins/LocoPillars/Data/Games/")
            plugin.logger.severe("[ArenaManager]  • .schem files → plugins/LocoPillars/Data/Schems/<name>/")
            return
        }

        val gameWorld = try {
            worldManager.ensureGameWorld()
        } catch (e: Exception) {
            plugin.logger.severe("[ArenaManager] ✗ Failed to create/load '${ArenaWorldManager.GAMEWORLD_NAME}': ${e.message}")
            e.printStackTrace()
            return
        }

        val totalInstances = configs.count { templates.containsKey(it.name) } * amountPerType
        plugin.logger.info(
            "[ArenaManager] Starting preload: $totalInstances arena instance(s) into '${ArenaWorldManager.GAMEWORLD_NAME}' " +
            "(${configs.size} config(s) × $amountPerType instance(s)) — synchronous paste, please wait…"
        )

        var globalIndex  = 0
        var successCount = 0
        var failCount    = 0

        // ── Ultra-fast FAWE batch session ────────────────────────────────────
        //
        // Global FAWE settings (saved/restored after close):
        //   LIGHTING.MODE = 0     → Starlight never fires during the batch
        //   TARGET_SIZE   = 64 k  → queue won't flush eagerly mid-loop
        //
        // Builder flags (FAWE 2.15.2, confirmed via javap):
        //   fastMode(true)             skip tile-entity ticking + neighbour updates
        //   relightMode(NONE)          eliminate Starlight for the whole batch
        //   checkMemory(false)         no per-change memory-cap checks
        //   limitUnlimited()           no block-count limit
        //   allowedRegionsEverywhere() skip WorldGuard lookup per block
        //
        // Paste calls use ignoreAirBlocks(true): void world has no existing blocks,
        // so skipping air writes saves 60–70 % of block operations.
        val faweSettings   = Settings.settings()
        val prevLightMode  = faweSettings.LIGHTING.MODE
        val prevTargetSize = faweSettings.QUEUE.TARGET_SIZE
        faweSettings.LIGHTING.MODE     = 0
        faweSettings.QUEUE.TARGET_SIZE = 65536

        val weWorld = BukkitAdapter.adapt(gameWorld)
        val batchSession = WorldEdit.getInstance().newEditSessionBuilder()
            .world(weWorld)
            .changeSetNull()
            .fastMode(true)
            .relightMode(RelightMode.NONE)
            .checkMemory(false)
            .limitUnlimited()
            .allowedRegionsEverywhere()
            .build()

        // ── Queue all pastes on the main thread (fast — only builds FAWE's buffer) ──
        try {
            configs.forEach { config ->
                val template = templates[config.name] ?: run {
                    debugLog("No template for config '${config.name}' — skipped.")
                    return@forEach
                }

                // Shuffle once per config so every restart picks a fresh rotation
                // through all schematic variants (e.g. 8 variants → different 5
                // each restart).
                val shuffledSchematics = template.schemFiles.shuffled()

                repeat(amountPerType) { instanceIndex ->
                    val arenaX   = (globalIndex * GRID_SPACING).toDouble()
                    val origin   = Location(gameWorld, arenaX, 0.0, 0.0)
                    val regionId = "pillars_${config.name}_$instanceIndex"

                    val instance = ArenaInstance(
                        id       = UUID.randomUUID(),
                        template = template,
                        config   = config,
                        origin   = origin,
                        world    = gameWorld,
                        status   = ArenaStatus.RESETTING,
                        regionId = regionId
                    )
                    instances.add(instance)
                    plugin.worldRegionManager.registerArenaRegion(instance)

                    // Cycle through the shuffled list; wraps if amountPerType > variants.
                    val selectedSchem = shuffledSchematics[instanceIndex % shuffledSchematics.size]
                    instance.currentSchematicFile = selectedSchem

                    // ── Live progress log (always visible) ────────────────
                    plugin.logger.info(
                        "[ArenaManager] Preloading arena [${globalIndex + 1}/$totalInstances]: " +
                        "'${config.name}' (Instance #${instanceIndex + 1}) at X: ${arenaX.toInt()} " +
                        "→ ${selectedSchem.name}"
                    )

                    // ── Queue into the shared batch session ───────────────
                    val ok = try {
                        val clipboard = schematicCache.getOrLoad(selectedSchem)
                        if (clipboard == null) {
                            plugin.logger.severe(
                                "[ArenaManager] ✗ [${globalIndex + 1}/$totalInstances] " +
                                "Schematic load failed: ${selectedSchem.name} " +
                                "('${config.name}' #${instanceIndex + 1})"
                            )
                            false
                        } else {
                            val pasteLocation = Location(
                                gameWorld,
                                origin.x + config.getSpawnCenterX() - config.getSaveRadius(),
                                gameWorld.minHeight.toDouble(),
                                origin.z + config.getSpawnCenterZ() - config.getSaveRadius()
                            )
                            SchematicHelper.pasteClipboardBatch(batchSession, clipboard, pasteLocation, ignoreAir = true)
                        }
                    } catch (e: Exception) {
                        plugin.logger.severe(
                            "[ArenaManager] ✗ [${globalIndex + 1}/$totalInstances] " +
                            "Exception pasting '${config.name}' #${instanceIndex + 1}: ${e.message}"
                        )
                        e.printStackTrace()
                        false
                    }

                    if (ok) successCount++ else failCount++
                    // Always mark AVAILABLE — a failed paste still leaves the slot usable
                    // (it will be reset on first game-join, re-pasting cleanly then).
                    instance.status = ArenaStatus.AVAILABLE
                    globalIndex++
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("[ArenaManager] ✗ Unexpected error during arena queuing: ${e.message}")
            e.printStackTrace()
            try { batchSession.close() } catch (_: Exception) {}
            faweSettings.LIGHTING.MODE     = prevLightMode
            faweSettings.QUEUE.TARGET_SIZE = prevTargetSize
            return
        }

        plugin.logger.info(
            "[ArenaManager] ✓ All $totalInstances arenas queued ($successCount OK, $failCount failed) — " +
            "flushing to world on background thread (no Starlight)…"
        )

        // ── Flush on a daemon thread so the main thread is NEVER blocked ──────────
        //
        // On Paper 1.21's async chunk system, calling batchSession.close() on the main
        // thread causes a deadlock: the main thread waits for FAWE's async queue while
        // FAWE's workers wait for Paper's chunk I/O, which is gated on the main thread.
        // Running close() on a separate daemon thread breaks the cycle.
        val successFinal = successCount
        val failFinal    = failCount
        val indexFinal   = globalIndex
        Thread({
            try {
                batchSession.close()
                plugin.logger.info(
                    "[ArenaManager] ✓ Preload flush complete — $successFinal/$totalInstances OK" +
                    (if (failFinal > 0) ", $failFinal failed (marked AVAILABLE anyway)" else "") +
                    ". Arenas span X=0 … X=${((indexFinal - 1) * GRID_SPACING).coerceAtLeast(0)}" +
                    " in '${ArenaWorldManager.GAMEWORLD_NAME}'."
                )
            } catch (e: Exception) {
                plugin.logger.severe("[ArenaManager] ✗ FAWE flush exception: ${e.message}")
                e.printStackTrace()
            } finally {
                faweSettings.LIGHTING.MODE     = prevLightMode
                faweSettings.QUEUE.TARGET_SIZE = prevTargetSize
            }
        }, "LocoPillars-FAWEFlush").apply { isDaemon = true }.start()
    }

    // ── Instance lifecycle ────────────────────────────────────────────────────

    fun getAvailableInstance(templateName: String): ArenaInstance? {
        return instances.firstOrNull {
            it.template.name == templateName && it.status == ArenaStatus.AVAILABLE
        }
    }

    fun occupyInstance(instance: ArenaInstance) {
        instance.status = ArenaStatus.OCCUPIED
        debugLog("Occupied: '${instance.template.name}' (${instance.id})")
    }

    fun releaseInstance(instance: ArenaInstance) {
        // Idempotency guard: multiple paths (last-player-leave, normal game-end, timer
        // expiry) can all call releaseInstance for the same game. add() is atomic and
        // returns false if a reset is already running for this arena → skip the duplicate
        // so we never queue overlapping pastes that corrupt the map.
        if (!resettingArenas.add(instance.id)) {
            debugLog("Reset already in progress: '${instance.template.name}' (${instance.id}) — skipping duplicate")
            return
        }

        instance.status = ArenaStatus.RESETTING
        debugLog("Releasing: '${instance.template.name}' (${instance.id})")

        clearPlayersFromInstance(instance)
        cleanupInstanceEntities(instance)
        clearLiquidOverflow(instance)

        try {
            resetArena(instance) {
                instance.status = ArenaStatus.AVAILABLE
                resettingArenas.remove(instance.id)
                debugLog("✓ Released: '${instance.template.name}' (${instance.id}) — status AVAILABLE")
            }
        } catch (e: Exception) {
            // Never leak the guard if queuing the reset throws synchronously.
            plugin.logger.severe("[ArenaManager] Failed to queue reset for '${instance.template.name}': ${e.message}")
            instance.status = ArenaStatus.AVAILABLE
            resettingArenas.remove(instance.id)
        }
    }

    // ── In-game async reset ───────────────────────────────────────────────────

    /**
     * Queues an async arena reset on the shared [pasteExecutor] (≤ 2 concurrent).
     *
     * All work (clipboard load + FAWE paste) runs on an executor thread so the
     * main thread is never blocked during live gameplay.  [onComplete] is always
     * dispatched back to the main thread regardless of success or failure.
     */
    fun resetArena(instance: ArenaInstance, onComplete: (() -> Unit)? = null) {
        debugLog("Queuing reset: '${instance.template.name}' (${instance.id})")

        val selectedSchem = pickRandomSchematic(instance.template)
        instance.currentSchematicFile = selectedSchem

        val centerX    = instance.config.getSpawnCenterX()
        val centerZ    = instance.config.getSpawnCenterZ()
        val saveRadius = instance.config.getSaveRadius().toDouble()
        val pasteLocation = Location(
            instance.world,
            instance.origin.x + centerX - saveRadius,
            instance.world.minHeight.toDouble(),
            instance.origin.z + centerZ - saveRadius
        )

        // Clear region = the full buildable column so player-placed blocks BEYOND the
        // schematic footprint are wiped too. Players can build out to BuildRange, which may
        // exceed SaveRadius, so clear the larger of the two; full Y so nothing survives.
        val clearRadius = maxOf(saveRadius, instance.config.getBuildRange().toDouble())
        val clearMin = Location(
            instance.world,
            instance.origin.x + centerX - clearRadius,
            instance.world.minHeight.toDouble(),
            instance.origin.z + centerZ - clearRadius
        )
        val clearMax = Location(
            instance.world,
            instance.origin.x + centerX + clearRadius,
            instance.world.maxHeight.toDouble() - 1.0,
            instance.origin.z + centerZ + clearRadius
        )

        pasteExecutor.submit {
            try {
                val clipboard = schematicCache.getOrLoad(selectedSchem)
                if (clipboard == null) {
                    plugin.logger.severe(
                        "[ArenaManager] ✗ Schematic load failed: ${selectedSchem.name} (${instance.template.name})"
                    )
                    plugin.server.scheduler.runTask(plugin, Runnable { onComplete?.invoke() })
                    return@submit
                }

                // Clear the buildable area then paste — guarantees a perfect reset with no
                // leftover blocks from the previous round.
                val success = SchematicHelper.clearAndPasteClipboard(
                    plugin, configManager, clipboard, pasteLocation, clearMin, clearMax
                )

                plugin.server.scheduler.runTask(plugin, Runnable {
                    if (success)
                        debugLog("✓ Reset OK: '${instance.template.name}' (${instance.id})")
                    else
                        plugin.logger.severe(
                            "[ArenaManager] ✗ Paste failed: '${instance.template.name}' — ${selectedSchem.name}"
                        )
                    onComplete?.invoke()
                })
            } catch (e: Exception) {
                plugin.logger.severe(
                    "[ArenaManager] ✗ Executor exception resetting '${instance.template.name}': ${e.message}"
                )
                e.printStackTrace()
                // Always fire onComplete so the instance never stays stuck in RESETTING.
                plugin.server.scheduler.runTask(plugin, Runnable { onComplete?.invoke() })
            }
        }
    }

    // ── Cleanup helpers ───────────────────────────────────────────────────────

    /**
     * Removes LAVA and WATER blocks in the arena's play area plus a 16-block
     * overflow buffer before each schematic re-paste.
     */
    private fun clearLiquidOverflow(instance: ArenaInstance) {
        val cfg    = instance.config
        val origin = instance.origin
        val world  = instance.world

        val centerX    = origin.blockX + cfg.getSpawnCenterX().toInt()
        val centerZ    = origin.blockZ + cfg.getSpawnCenterZ().toInt()
        val scanRadius = cfg.getBuildRange() + 16
        val minY = maxOf(world.minHeight,     cfg.getDeathHeight()  - 10)
        val maxY = minOf(world.maxHeight - 1, cfg.getBuildHeight() + 10)

        var removed = 0
        for (bx in (centerX - scanRadius)..(centerX + scanRadius)) {
            for (bz in (centerZ - scanRadius)..(centerZ + scanRadius)) {
                for (by in minY..maxY) {
                    val block = world.getBlockAt(bx, by, bz)
                    if (block.type == Material.LAVA || block.type == Material.WATER) {
                        block.setType(Material.AIR, false)
                        removed++
                    }
                }
            }
        }

        if (removed > 0)
            debugLog("clearLiquidOverflow: $removed block(s) removed from '${instance.template.name}' (${instance.id})")
    }

    /**
     * Removes all non-player entities within this instance's grid cell.
     * Clears dropped items, armor stands (LuckyBlock heads), vehicles, etc.
     */
    private fun cleanupInstanceEntities(instance: ArenaInstance) {
        val origin = instance.origin
        val half   = instanceRadius.toDouble()
        val world  = instance.world

        var removed = 0
        world.entities.toList().forEach { entity ->
            if (entity is Player) return@forEach
            val loc = entity.location
            if (loc.x >= origin.x - half && loc.x <= origin.x + half &&
                loc.z >= origin.z - half && loc.z <= origin.z + half) {
                entity.remove()
                removed++
            }
        }

        if (removed > 0)
            debugLog("Removed $removed entity/ies from '${instance.template.name}' (${instance.id})")
    }

    private fun clearPlayersFromInstance(instance: ArenaInstance) {
        val origin = instance.origin
        val half   = instanceRadius.toDouble()
        val lobbySpawn = configManager.getLobbySpawnLocation()

        var moved = 0
        instance.world.players.forEach { player ->
            val loc = player.location
            if (loc.x >= origin.x - half && loc.x <= origin.x + half &&
                loc.z >= origin.z - half && loc.z <= origin.z + half) {
                player.teleport(lobbySpawn ?: player.server.worlds[0].spawnLocation)
                moved++
            }
        }

        if (moved > 0)
            debugLog("Moved $moved player(s) out of '${instance.template.name}' (${instance.id})")
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    fun getInstanceByLocation(location: Location): ArenaInstance? {
        val world = location.world ?: return null
        return instances.firstOrNull { instance ->
            if (instance.world != world) return@firstOrNull false
            val origin = instance.origin
            location.x >= origin.x - instanceRadius && location.x <= origin.x + instanceRadius &&
            location.z >= origin.z - instanceRadius && location.z <= origin.z + instanceRadius
        }
    }

    fun getAllInstances(): List<ArenaInstance>           = instances.toList()
    fun getAllTemplates(): List<ArenaTemplate>           = templates.values.toList()
    fun getInstancesByTemplate(name: String)            = instances.filter { it.template.name == name }

    // ── Disable ───────────────────────────────────────────────────────────────

    fun onDisable() {
        debugLog("Shutting down paste executor…")
        pasteExecutor.shutdown()
        try {
            if (!pasteExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                pasteExecutor.shutdownNow()
                plugin.logger.warning("[ArenaManager] Paste executor did not finish within 5 s — forcefully terminated.")
            }
        } catch (_: InterruptedException) {
            pasteExecutor.shutdownNow()
        }

        schematicCache.clear()
        debugLog("Schematic cache cleared.")
        // World unloading is handled by ArenaWorldManager.cleanup() called from LocoPillars.onDisable().
    }
}
