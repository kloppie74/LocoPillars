package org.Kloppie74.locoPillars

import org.Kloppie74.locoPillars.game.voting.VotingGameContext

/**
 * Bridge used exclusively by [game.voting.VotingScriptEngine.wrapVoteScript] to
 * retrieve the [VotingGameContext] from the JSR-223 script bindings.
 *
 * WHY this exists
 * ---------------
 * The Kotlin scripting compiler (JSR-223 engine) incorrectly resolves the `game`
 * token inside a fully-qualified class name — even one that starts unambiguously
 * with `org.` — as a reference to any in-scope local variable that happens to be
 * named `game`.  Because the wrapper declares `val game = …`, the FQN
 *
 *   org.Kloppie74.locoPillars.game.voting.VotingGameContext
 *
 * fails with "Unresolved reference: game" when used in a cast expression inside
 * the same script.  The same bug affects `import` statements containing `.game.`.
 *
 * By delegating the cast to this helper class — whose own FQN
 * (org.Kloppie74.locoPillars.VotingContextBridge) contains no `game` segment —
 * the wrapper avoids triggering the bug entirely.  This file is ordinary compiled
 * Kotlin, so the import of VotingGameContext here is completely fine.
 */
object VotingContextBridge {
    /**
     * Inside a JSR-223 `.kts` script the implicit `bindings` variable is typed as
     * `Map<String, Any?>` (a supertype of [javax.script.Bindings]), so this helper
     * must accept the wider `Map` type — accepting `Bindings` directly causes a
     * "Type mismatch: Map<String, Any?> but Bindings was expected" compile error
     * in every voting module that calls it.
     */
    @Suppress("UNCHECKED_CAST")
    fun get(bindings: Map<String, Any?>): VotingGameContext = bindings["game"] as VotingGameContext
}
