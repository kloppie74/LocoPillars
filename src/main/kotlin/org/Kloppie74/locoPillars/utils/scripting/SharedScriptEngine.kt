package org.Kloppie74.locoPillars.utils.scripting

import org.bukkit.plugin.java.JavaPlugin
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager

/**
 * Holds ONE shared Kotlin KTS ScriptEngine for the entire plugin.
 *
 * Problem solved:
 *   Previously LuckyblockScriptEngine, CosmeticScriptEngine, and VotingScriptEngine
 *   each created their OWN ScriptEngineManager + KTS engine.  The Kotlin scripting
 *   runtime (compiler + stdlib + reflection) loads ~500 MB – 1 GB of classes into
 *   the JVM per engine instance.  Three engines = ~1.5 – 3 GB of overhead just from
 *   script-engine initialization.
 *
 * Solution:
 *   Every script engine wrapper calls SharedScriptEngine.get(plugin) instead of
 *   constructing its own.  The engine is initialized lazily (only when the first
 *   scripted feature is actually used) and shared across all subsystems.
 *
 * Thread-safety:
 *   Initialization is double-checked and synchronized.  Evaluation is always called
 *   from the main server thread (or synchronous tasks), so concurrent eval races do
 *   not occur in practice.
 */
object SharedScriptEngine {

    @Volatile private var engine: ScriptEngine? = null
    @Volatile private var initialized = false

    /**
     * Returns the shared engine, initializing it on first call.
     * Returns null if the Kotlin KTS engine is not available on the classpath.
     */
    fun get(plugin: JavaPlugin): ScriptEngine? {
        if (initialized) return engine
        synchronized(this) {
            if (initialized) return engine
            val prev = Thread.currentThread().contextClassLoader
            engine = try {
                Thread.currentThread().contextClassLoader = plugin.javaClass.classLoader
                val mgr = ScriptEngineManager(plugin.javaClass.classLoader)
                (mgr.getEngineByExtension("kts") ?: mgr.getEngineByName("kotlin"))
                    ?.also { plugin.logger.info("[SharedScriptEngine] Kotlin KTS engine initialized (shared by LuckyBlocks, Cosmetics & Voting).") }
                    ?: run {
                        plugin.logger.severe("[SharedScriptEngine] Kotlin KTS engine not found — is kotlin-scripting-jsr223 on the classpath?")
                        null
                    }
            } catch (e: Exception) {
                plugin.logger.severe("[SharedScriptEngine] Failed to initialize: ${e.message}")
                null
            } finally {
                Thread.currentThread().contextClassLoader = prev
            }
            initialized = true
        }
        return engine
    }

    /**
     * Resets the singleton so a plugin reload gets a fresh engine.
     * Call from LocoPillars.onDisable().
     */
    fun reset() {
        synchronized(this) {
            engine = null
            initialized = false
        }
    }
}
