package org.Kloppie74.locoPillars.editor.listener

import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockFormEvent
import org.bukkit.event.block.BlockGrowEvent
import org.bukkit.event.block.BlockSpreadEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.world.StructureGrowEvent

/**
 * EditorWorldListener
 *
 * Final safety net that keeps editor worlds completely empty. The void generator
 * + per-world game rules already block almost everything, but some content
 * plugins (CraftEngine, ItemsAdder, Oraxen, …) place crops/flowers or spawn mobs
 * through their OWN listeners (e.g. on chunk load) rather than through the normal
 * generation pipeline. This listener cancels any such spawning inside editor
 * worlds, so nothing appears mid-air while building.
 *
 * Editor worlds are identified by the name prefix used in
 * [org.Kloppie74.locoPillars.game.map.EditorSessionManager] (`pillars_edit_…`).
 */
class EditorWorldListener : Listener {

    private fun isEditorWorld(world: World): Boolean =
        world.name.startsWith(EDITOR_WORLD_PREFIX)

    // -------------------------------------------------------------------------
    // Mob spawning — block every world/natural spawn, but allow plugin- and
    // admin-driven spawns (CUSTOM / COMMAND / SPAWNER_EGG) so editor tooling
    // such as TextDisplay holograms or manually-spawned helpers keep working.
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onCreatureSpawn(event: CreatureSpawnEvent) {
        if (!isEditorWorld(event.location.world ?: return)) return
        when (event.spawnReason) {
            CreatureSpawnEvent.SpawnReason.CUSTOM,
            CreatureSpawnEvent.SpawnReason.COMMAND,
            CreatureSpawnEvent.SpawnReason.SPAWNER_EGG -> return // allow intentional spawns
            else -> event.isCancelled = true
        }
    }

    // -------------------------------------------------------------------------
    // Block growth / spreading / forming — stops crops & flowers (vanilla or
    // custom) from growing or spreading inside an editor world.
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockGrow(event: BlockGrowEvent) {
        if (isEditorWorld(event.block.world)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockSpread(event: BlockSpreadEvent) {
        if (isEditorWorld(event.block.world)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onBlockForm(event: BlockFormEvent) {
        if (isEditorWorld(event.block.world)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onStructureGrow(event: StructureGrowEvent) {
        if (isEditorWorld(event.world)) event.isCancelled = true
    }

    companion object {
        /** Must match the world name prefix in EditorSessionManager. */
        const val EDITOR_WORLD_PREFIX = "pillars_edit_"
    }
}
