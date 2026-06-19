package org.Kloppie74.locoPillars.utils

import com.sk89q.worldedit.extent.clipboard.Clipboard
import org.bukkit.plugin.java.JavaPlugin
import org.Kloppie74.locoPillars.utils.config.ConfigManager
import java.io.File
import java.util.Collections

/**
 * Thread-safe LRU cache for FAWE Clipboard objects.
 *
 * Keeps at most [maxSize] clipboards in RAM.  When a new entry would exceed
 * the cap, the least-recently-accessed entry is silently evicted so its heap
 * can be reclaimed by GC.
 *
 * All async scheduling is the caller's responsibility — this class is
 * intentionally synchronous so it can be safely called from the shared
 * fixed-thread-pool executor in ArenaManager without any hidden BukkitScheduler
 * interaction (which would be illegal from a non-main thread).
 */
class SmartSchematicCache(
    private val plugin: JavaPlugin,
    private val configManager: ConfigManager,
    private val maxSize: Int = 5
) {

    private val cache: MutableMap<String, Clipboard> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Clipboard>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, Clipboard>): Boolean {
                val evict = size > maxSize
                if (evict && configManager.debugMode) {
                    plugin.logger.info(
                        "[SmartSchematicCache] LRU evict: ${eldest.key.substringAfterLast(File.separator)} (cache full at $maxSize)"
                    )
                }
                return evict
            }
        }
    )

    /**
     * Returns a cached clipboard for [schemFile], loading from disk synchronously
     * if it is not yet cached.  Thread-safe — intended to be called from the
     * paste executor thread.
     */
    fun getOrLoad(schemFile: File): Clipboard? {
        cache[schemFile.absolutePath]?.let { return it }

        val clipboard = SchematicHelper.loadSchematic(plugin, configManager, schemFile) ?: return null
        cache[schemFile.absolutePath] = clipboard

        if (configManager.debugMode)
            plugin.logger.info("[SmartSchematicCache] Loaded: ${schemFile.name}  (cache ${cache.size}/$maxSize)")

        return clipboard
    }

    fun size(): Int = cache.size

    fun invalidate(schemFile: File) {
        cache.remove(schemFile.absolutePath)
    }

    fun clear() {
        val n = cache.size
        cache.values.forEach { try { (it as? AutoCloseable)?.close() } catch (_: Exception) {} }
        cache.clear()
        if (n > 0) plugin.logger.info("[SmartSchematicCache] Cleared $n clipboard(s) from cache.")
    }

    fun getStats(): String = "SmartSchematicCache: ${cache.size}/$maxSize"
}
