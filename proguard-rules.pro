###############################################################################
# LocoPillars — ProGuard Obfuscation Rules
###############################################################################

# ── Diagnostics (generated into build/proguard/) ─────────────────────────────
-verbose
-printmapping  build/proguard/mapping.txt
-printseeds    build/proguard/seeds.txt
-printusage    build/proguard/usage.txt

# ── Preserve runtime attributes needed by Bukkit / Kotlin / JDBC ─────────────
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable

# ── Obfuscation settings ──────────────────────────────────────────────────────
# Move every non-kept class into the flat package "lp" so the original
# package structure is completely hidden.
-repackageclasses 'lp'
-allowaccessmodification

# Reuse the same obfuscated name for methods with different signatures.
# This makes decompiled code extremely confusing to read.
-overloadaggressively

# Force unique member names across class hierarchy (avoids some decompiler tricks).
-useuniqueclassmembernames

# Use the confusing-character dictionary for class, method and field names.
-obfuscationdictionary      proguard-dict.txt
-classobfuscationdictionary proguard-dict.txt
-packageobfuscationdictionary proguard-dict.txt

# Skip optimization phase entirely — shrinking and obfuscation still run.
# Optimization adds no meaningful value for a Bukkit plugin and is extremely
# slow when the Kotlin compiler (~30k classes) is bundled via kotlin-scripting-jsr223.
-dontoptimize

# Keep resource files that reference class names by name.
-adaptresourcefilecontents 'plugin.yml,*.properties,META-INF/services/**'
-adaptresourcefilenames

# ─────────────────────────────────────────────────────────────────────────────
# 1. MAIN ENTRY POINT
#    plugin.yml references this class by full name — must never be renamed.
# ─────────────────────────────────────────────────────────────────────────────
-keep public class org.Kloppie74.locoPillars.LocoPillars {
    *;
}

# ─────────────────────────────────────────────────────────────────────────────
# 2. BUKKIT EVENT SYSTEM
#    Bukkit discovers @EventHandler methods via reflection at register time.
#    We keep the annotation and the annotated methods; everything else (the class
#    name, non-handler methods) can still be obfuscated.
# ─────────────────────────────────────────────────────────────────────────────
-keepclassmembers class * implements org.bukkit.event.Listener {
    @org.bukkit.event.EventHandler public *;
}

# ─────────────────────────────────────────────────────────────────────────────
# 3. BUKKIT COMMAND SYSTEM
#    onCommand / onTabComplete are called via the CommandExecutor interface.
# ─────────────────────────────────────────────────────────────────────────────
-keepclassmembers class * implements org.bukkit.command.CommandExecutor {
    public boolean onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[]);
}
-keepclassmembers class * implements org.bukkit.command.TabCompleter {
    public java.util.List onTabComplete(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[]);
}

# ─────────────────────────────────────────────────────────────────────────────
# 4. KOTLIN SCRIPTING CONTEXT CLASSES
#    These are referenced inside .kts files by their fully-qualified name in
#    import statements that are injected at compile time.  If ProGuard renames
#    them the runtime script compilation will fail with ClassNotFoundException.
#
#      LuckyblockScriptEngine injects:
#        import org.Kloppie74.locoPillars.utils.luckyblocks.engine.LuckyblockContext
#
#      CosmeticScriptEngine injects:
#        import org.Kloppie74.locoPillars.cosmetics.engine.CosmeticContext
#
#      VotingScriptEngine injects:
#        import org.Kloppie74.locoPillars.VotingContextBridge
#        (VotingContextBridge internally returns VotingGameContext)
#        gui.kts references: VotingGuiConfig
# ─────────────────────────────────────────────────────────────────────────────
-keep public class org.Kloppie74.locoPillars.utils.luckyblocks.engine.LuckyblockContext {
    public *;
}
-keep public class org.Kloppie74.locoPillars.cosmetics.engine.CosmeticContext {
    public *;
}
# CosmeticType / CosmeticRarity are referenced by name in several scripts
-keep public class org.Kloppie74.locoPillars.cosmetics.engine.CosmeticType {
    public *;
}
-keep public class org.Kloppie74.locoPillars.cosmetics.engine.CosmeticRarity {
    public *;
}
-keep public class org.Kloppie74.locoPillars.VotingContextBridge {
    public *;
}
-keep public class org.Kloppie74.locoPillars.game.voting.VotingGameContext {
    public *;
}
-keep public class org.Kloppie74.locoPillars.game.voting.VotingGuiConfig {
    public *;
}

# ─────────────────────────────────────────────────────────────────────────────
# 5. KOTLIN RUNTIME REQUIREMENTS
# ─────────────────────────────────────────────────────────────────────────────
# Kotlin metadata annotation — required for Kotlin reflection and the JSR 223
# scripting engine to understand the compiled bytecode.
-keep class kotlin.Metadata { *; }

# Kotlin companion objects and object singletons use a static INSTANCE field.
-keepclassmembers class * {
    public static ** INSTANCE;
    public static ** Companion;
    public static ** DEFAULT_INSTANCE;
}

# Kotlin enum values() and valueOf() are called reflectively.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public java.lang.String name();
    public int ordinal();
}

# Kotlin serialization / data-class generated methods (copy, componentN …)
-keepclassmembers class * {
    kotlin.jvm.functions.Function* component*();
    ** copy(...);
}

# ─────────────────────────────────────────────────────────────────────────────
# 6. DATABASE DRIVERS (loaded via Class.forName() / ServiceLoader)
# ─────────────────────────────────────────────────────────────────────────────
-keep class com.zaxxer.hikari.**                    { *; }
-keep class com.mysql.**                            { *; }
-keep class org.mariadb.**                          { *; }
-keep class org.h2.**                               { *; }

# ─────────────────────────────────────────────────────────────────────────────
# 6b. INTERNAL UTILITY SINGLETONS
#     Kotlin `object` singletons that are called from obfuscated code can be
#     silently lost when -overloadaggressively assigns the same obfuscated name
#     to two different classes (last writer wins in the JAR, one class
#     disappears → NoClassDefFoundError at runtime).
#     Keeping them by their original name prevents name collisions entirely.
# ─────────────────────────────────────────────────────────────────────────────
-keep class org.Kloppie74.locoPillars.utils.SchematicCageHelper {
    *;
}
-keep class org.Kloppie74.locoPillars.utils.SchematicCageHelper$** {
    *;
}
-keep class org.Kloppie74.locoPillars.utils.SmartSchematicCache {
    *;
}
-keep class org.Kloppie74.locoPillars.utils.SchematicHelper {
    *;
}

# ─────────────────────────────────────────────────────────────────────────────
# 7. BUNDLED LIBRARIES (keep public API so they work as expected at runtime)
# ─────────────────────────────────────────────────────────────────────────────
-keep class org.slf4j.**   { *; }
-keep class okhttp3.**    { *; }
-keep class okio.**       { *; }
-keep class org.json.**   { *; }
-keep class org.objectweb.asm.** { *; }
-keep class org.snakeyaml.engine.** { *; }

# javax.script — scripting engine glue
-keep class javax.script.**    { *; }
-keep class kotlin.script.**   { *; }

# META-INF services entries reference class names — keep any class registered
# as a service (scripting engine discovery uses this mechanism).
-keep class * implements javax.script.ScriptEngineFactory { *; }

# ─────────────────────────────────────────────────────────────────────────────
# 8. SUPPRESS WARNINGS FOR compileOnly / SERVER-PROVIDED CLASSES
#    These JARs are NOT in our shadow JAR — ProGuard will warn about missing
#    classes unless we silence them.
# ─────────────────────────────────────────────────────────────────────────────
-dontwarn io.papermc.**
-dontwarn org.bukkit.**
-dontwarn net.kyori.**
-dontwarn com.sk89q.**
-dontwarn com.fastasyncworldedit.**
-dontwarn me.clip.**
-dontwarn net.milkbowl.**
-dontwarn com.comphenix.**
-dontwarn com.github.retrooper.**
-dontwarn io.github.retrooper.**
-dontwarn com.github.LoneDev6.**
-dontwarn com.github.MilkBowl.**
-dontwarn com.github.NEZNAMY.**
-dontwarn org.yaml.snakeyaml.**

# General Kotlin / coroutine internals — keep entire Kotlin compiler/stdlib as-is
# (kotlin-scripting-jsr223 bundles thousands of compiler classes; processing them
#  multiplies build time without any benefit to your own plugin's obfuscation)
-keep class kotlin.** { *; }
-keep class kotlinx.** { *; }
-keep class org.jetbrains.kotlin.** { *; }
-keep class org.jetbrains.annotations.** { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn javax.annotation.**
-dontwarn org.jetbrains.annotations.**

# Database driver internals
-dontwarn com.zaxxer.**
-dontwarn com.mysql.**
-dontwarn org.mariadb.**
-dontwarn org.h2.**

# OkHttp / Okio
-dontwarn okhttp3.**
-dontwarn okio.**

# ProGuard treats any remaining missing-class warnings as fatal by default.
# For a Bukkit plugin the server-side classes are intentionally absent from
# the shadow JAR.  Suppress all remaining warnings/notes and do not abort.
-dontwarn **
-dontnote **
-ignorewarnings
