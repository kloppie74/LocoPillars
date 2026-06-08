package org.Kloppie74.locoPillars.utils.luckyblocks.engine

import org.Kloppie74.locoPillars.utils.scripting.SharedScriptEngine
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.script.Compilable
import javax.script.CompiledScript
import javax.script.ScriptException

/**
 * Wraps the shared Kotlin JSR 223 script engine for LuckyBlock scripts.
 * Scripts are compiled once at load time and cached for fast re-execution.
 * Each script receives `player` and `game` as injected variables.
 *
 * Uses [SharedScriptEngine] — the Kotlin compiler is loaded only ONCE for the
 * entire plugin instead of once per scripting subsystem.
 */
class LuckyblockScriptEngine(private val plugin: JavaPlugin) {

    // Delegates to the plugin-wide singleton — no private engine instance here.
    private val engine get() = SharedScriptEngine.get(plugin)

    // id → (compiled script, file last-modified timestamp)
    private val cache = ConcurrentHashMap<String, Pair<CompiledScript, Long>>()

    fun isAvailable(): Boolean = engine != null

    /**
     * Wraps the user's script body with common imports and variable binding extraction.
     * Users only write their gameplay logic — no boilerplate needed.
     */
    private fun wrap(userCode: String): String = """
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffectType
import org.Kloppie74.locoPillars.utils.luckyblocks.engine.LuckyblockContext

@Suppress("UNCHECKED_CAST")
val player = bindings["player"] as org.bukkit.entity.Player
@Suppress("UNCHECKED_CAST")
val game = bindings["game"] as LuckyblockContext

$userCode
""".trimIndent()

    /**
     * Compiles and caches the script for [id].
     * Returns true on success, false on compilation error.
     */
    fun compile(id: String, scriptFile: File): Boolean {
        val e = engine ?: return false
        val compilable = e as? Compilable ?: return false
        val prev = Thread.currentThread().contextClassLoader
        return try {
            Thread.currentThread().contextClassLoader = plugin.javaClass.classLoader
            val compiled = compilable.compile(wrap(scriptFile.readText()))
            cache[id] = Pair(compiled, scriptFile.lastModified())
            plugin.logger.info("[LuckyBlocks] Compiled: $id")
            true
        } catch (ex: ScriptException) {
            plugin.logger.severe("[LuckyBlocks] Compile error in '$id' (${scriptFile.name}): ${ex.message}")
            false
        } finally {
            Thread.currentThread().contextClassLoader = prev
        }
    }

    /**
     * Executes the script for [id].
     * Uses the cached compiled version if available and not stale.
     */
    fun execute(id: String, scriptFile: File, player: Player, context: LuckyblockContext): Boolean {
        val e = engine ?: run {
            player.sendMessage("§c[LuckyBlocks] Script engine unavailable.")
            return false
        }
        val prev = Thread.currentThread().contextClassLoader
        return try {
            Thread.currentThread().contextClassLoader = plugin.javaClass.classLoader
            val bindings = e.createBindings()
            bindings["player"] = player
            bindings["game"] = context

            val cached = cache[id]
            if (cached != null && cached.second == scriptFile.lastModified()) {
                cached.first.eval(bindings)
            } else {
                e.eval(wrap(scriptFile.readText()), bindings)
            }
            true
        } catch (ex: ScriptException) {
            plugin.logger.severe("[LuckyBlocks] Runtime error in '$id': ${ex.message}")
            player.sendMessage("§c[LuckyBlocks] Script error in $id — check console.")
            false
        } catch (ex: Exception) {
            plugin.logger.severe("[LuckyBlocks] Unexpected error in '$id': ${ex.message}")
            false
        } finally {
            Thread.currentThread().contextClassLoader = prev
        }
    }

    fun clearCache() = cache.clear()
}
