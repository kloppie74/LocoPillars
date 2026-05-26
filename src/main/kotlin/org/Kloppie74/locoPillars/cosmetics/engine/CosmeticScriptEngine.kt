package org.Kloppie74.locoPillars.cosmetics.engine

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.script.Compilable
import javax.script.CompiledScript
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.ScriptException

/**
 * Kotlin JSR 223 script engine for cosmetic .kts scripts.
 * Scripts are compiled once and cached for fast re-execution.
 *
 * Injected bindings per cosmetic type:
 *   KILL  → player (killer), victim (killed player), game (CosmeticContext)
 *   WIN   → player (winner),                         game (CosmeticContext)
 *   DEATH → player (dying player),                   game (CosmeticContext)
 */
class CosmeticScriptEngine(private val plugin: JavaPlugin) {

    private val engine: ScriptEngine? by lazy { initEngine() }
    private val cache = ConcurrentHashMap<String, Pair<CompiledScript, Long>>()

    private fun initEngine(): ScriptEngine? {
        val prev = Thread.currentThread().contextClassLoader
        return try {
            Thread.currentThread().contextClassLoader = plugin.javaClass.classLoader
            val mgr = ScriptEngineManager(plugin.javaClass.classLoader)
            mgr.getEngineByExtension("kts")
                ?: mgr.getEngineByName("kotlin")
                ?: run {
                    plugin.logger.severe("[Cosmetics] Kotlin script engine not found — is kotlin-scripting-jsr223 on the classpath?")
                    null
                }
        } catch (e: Exception) {
            plugin.logger.severe("[Cosmetics] Failed to init script engine: ${e.message}")
            null
        } finally {
            Thread.currentThread().contextClassLoader = prev
        }
    }

    fun isAvailable(): Boolean = engine != null

    /** Wraps user code with imports and binding extraction (KILL variant — has victim). */
    private fun wrapKill(userCode: String): String = """
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffectType
import org.Kloppie74.locoPillars.cosmetics.engine.CosmeticContext

@Suppress("UNCHECKED_CAST")
val player = bindings["player"] as org.bukkit.entity.Player
@Suppress("UNCHECKED_CAST")
val victim = bindings["victim"] as? org.bukkit.entity.Player
@Suppress("UNCHECKED_CAST")
val game = bindings["game"] as CosmeticContext

$userCode
""".trimIndent()

    /** Wraps user code with imports and binding extraction (WIN / DEATH — no victim). */
    private fun wrapSimple(userCode: String): String = """
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffectType
import org.Kloppie74.locoPillars.cosmetics.engine.CosmeticContext

@Suppress("UNCHECKED_CAST")
val player = bindings["player"] as org.bukkit.entity.Player
@Suppress("UNCHECKED_CAST")
val game = bindings["game"] as CosmeticContext

$userCode
""".trimIndent()

    private fun wrap(type: CosmeticType, code: String) =
        if (type == CosmeticType.KILL) wrapKill(code) else wrapSimple(code)

    fun compile(id: String, scriptFile: File, type: CosmeticType): Boolean {
        val e = engine ?: return false
        val compilable = e as? Compilable ?: return false
        val prev = Thread.currentThread().contextClassLoader
        return try {
            Thread.currentThread().contextClassLoader = plugin.javaClass.classLoader
            val compiled = compilable.compile(wrap(type, scriptFile.readText()))
            cache[id] = Pair(compiled, scriptFile.lastModified())
            plugin.logger.info("[Cosmetics] Compiled: $id")
            true
        } catch (ex: ScriptException) {
            plugin.logger.severe("[Cosmetics] Compile error in '$id' (${scriptFile.name}): ${ex.message}")
            false
        } finally {
            Thread.currentThread().contextClassLoader = prev
        }
    }

    /**
     * Executes a KILL cosmetic script.
     * @param killer  the player who earned the kill
     * @param victim  the player who died (may be null if killed by environment)
     */
    fun executeKill(id: String, scriptFile: File, killer: Player, victim: Player?, context: CosmeticContext): Boolean {
        val e = engine ?: return false
        val prev = Thread.currentThread().contextClassLoader
        return try {
            Thread.currentThread().contextClassLoader = plugin.javaClass.classLoader
            val bindings = e.createBindings()
            bindings["player"] = killer
            bindings["victim"] = victim
            bindings["game"] = context
            evalWithCache(e, id, scriptFile, bindings, CosmeticType.KILL)
        } catch (ex: ScriptException) {
            plugin.logger.severe("[Cosmetics] Runtime error in '$id': ${ex.message}")
            false
        } catch (ex: Exception) {
            plugin.logger.severe("[Cosmetics] Unexpected error in '$id': ${ex.message}")
            false
        } finally {
            Thread.currentThread().contextClassLoader = prev
        }
    }

    /**
     * Executes a WIN or DEATH cosmetic script.
     */
    fun executeSimple(id: String, scriptFile: File, player: Player, context: CosmeticContext): Boolean {
        val e = engine ?: return false
        val prev = Thread.currentThread().contextClassLoader
        return try {
            Thread.currentThread().contextClassLoader = plugin.javaClass.classLoader
            val bindings = e.createBindings()
            bindings["player"] = player
            bindings["game"] = context
            val type = if (id.startsWith("win_") || id.contains("win")) CosmeticType.WIN else CosmeticType.DEATH
            evalWithCache(e, id, scriptFile, bindings, type)
        } catch (ex: ScriptException) {
            plugin.logger.severe("[Cosmetics] Runtime error in '$id': ${ex.message}")
            false
        } catch (ex: Exception) {
            plugin.logger.severe("[Cosmetics] Unexpected error in '$id': ${ex.message}")
            false
        } finally {
            Thread.currentThread().contextClassLoader = prev
        }
    }

    private fun evalWithCache(
        e: ScriptEngine,
        id: String,
        scriptFile: File,
        bindings: javax.script.Bindings,
        type: CosmeticType
    ): Boolean {
        val cached = cache[id]
        if (cached != null && cached.second == scriptFile.lastModified()) {
            cached.first.eval(bindings)
        } else {
            e.eval(wrap(type, scriptFile.readText()), bindings)
        }
        return true
    }

    fun clearCache() = cache.clear()
}
