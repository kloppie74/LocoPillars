package org.Kloppie74.locoPillars.game.voting

import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.script.Compilable
import javax.script.CompiledScript
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.ScriptException

class VotingScriptEngine(private val plugin: JavaPlugin) {

    private val engine: ScriptEngine? by lazy { initEngine() }
    private val moduleCache = ConcurrentHashMap<String, Pair<CompiledScript, Long>>()
    private var guiCache: Pair<CompiledScript, Long>? = null

    private fun initEngine(): ScriptEngine? {
        val prev = Thread.currentThread().contextClassLoader
        return try {
            Thread.currentThread().contextClassLoader = plugin.javaClass.classLoader
            val mgr = ScriptEngineManager(plugin.javaClass.classLoader)
            mgr.getEngineByExtension("kts")
                ?: mgr.getEngineByName("kotlin")
                ?: run {
                    plugin.logger.severe("[Voting] Kotlin script engine not found — is kotlin-scripting-jsr223 on the classpath?")
                    null
                }
        } catch (e: Exception) {
            plugin.logger.severe("[Voting] Failed to init script engine: ${e.message}")
            null
        } finally {
            Thread.currentThread().contextClassLoader = prev
        }
    }

    private fun wrapVoteScript(code: String): String = """
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.*
import org.bukkit.event.*
import org.bukkit.event.entity.*
import org.bukkit.event.player.*
import org.bukkit.event.inventory.*
import org.Kloppie74.locoPillars.VotingContextBridge

// VotingContextBridge (package: org.Kloppie74.locoPillars — no 'game' segment)
// is imported so the Kotlin scripting compiler finds it via the plugin classloader,
// same as LuckyblockContext.  Using a FQN without an import does NOT work for
// plugin-loaded classes in JSR-223 scripting.
@Suppress("UNCHECKED_CAST")
val game = VotingContextBridge.get(bindings)

$code
""".trimIndent()

    private fun wrapGuiScript(code: String): String = """
import org.Kloppie74.locoPillars.game.voting.VotingGuiConfig

$code
""".trimIndent()

    fun compileModule(id: String, scriptFile: File): Boolean {
        val e = engine ?: return false
        val compilable = e as? Compilable ?: return false
        val prev = Thread.currentThread().contextClassLoader
        return try {
            Thread.currentThread().contextClassLoader = plugin.javaClass.classLoader
            val compiled = compilable.compile(wrapVoteScript(scriptFile.readText()))
            moduleCache[id] = Pair(compiled, scriptFile.lastModified())
            plugin.logger.info("[Voting] Compiled: $id")
            true
        } catch (ex: ScriptException) {
            plugin.logger.severe("[Voting] Compile error in '$id': ${ex.message}")
            false
        } finally {
            Thread.currentThread().contextClassLoader = prev
        }
    }

    fun executeModule(id: String, scriptFile: File, context: VotingGameContext): Boolean {
        val e = engine ?: return false
        val prev = Thread.currentThread().contextClassLoader
        return try {
            Thread.currentThread().contextClassLoader = plugin.javaClass.classLoader
            val bindings = e.createBindings()
            bindings["game"] = context
            val cached = moduleCache[id]
            if (cached != null && cached.second == scriptFile.lastModified()) {
                cached.first.eval(bindings)
            } else {
                e.eval(wrapVoteScript(scriptFile.readText()), bindings)
            }
            true
        } catch (ex: ScriptException) {
            plugin.logger.severe("[Voting] Runtime error in '$id': ${ex.message}")
            false
        } catch (ex: Exception) {
            plugin.logger.severe("[Voting] Unexpected error in '$id': ${ex.message}")
            false
        } finally {
            Thread.currentThread().contextClassLoader = prev
        }
    }

    fun evaluateGuiConfig(guiFile: File): VotingGuiConfig {
        val e = engine ?: return VotingGuiConfig()
        val prev = Thread.currentThread().contextClassLoader
        return try {
            Thread.currentThread().contextClassLoader = plugin.javaClass.classLoader
            val cached = guiCache
            val result = if (cached != null && cached.second == guiFile.lastModified()) {
                cached.first.eval()
            } else {
                val compilable = e as? Compilable
                if (compilable != null) {
                    val compiled = compilable.compile(wrapGuiScript(guiFile.readText()))
                    guiCache = Pair(compiled, guiFile.lastModified())
                    compiled.eval()
                } else {
                    e.eval(wrapGuiScript(guiFile.readText()))
                }
            }
            result as? VotingGuiConfig ?: VotingGuiConfig()
        } catch (ex: Exception) {
            plugin.logger.warning("[Voting] Failed to evaluate gui.kts: ${ex.message} — using defaults")
            VotingGuiConfig()
        } finally {
            Thread.currentThread().contextClassLoader = prev
        }
    }

    fun clearCache() {
        moduleCache.clear()
        guiCache = null
    }
}
