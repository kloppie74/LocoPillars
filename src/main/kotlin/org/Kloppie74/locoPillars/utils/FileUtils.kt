package org.Kloppie74.locoPillars.utils

import java.io.File
import java.nio.file.Files
import java.util.Comparator

/**
 * Verwijdert een map + alle inhoud via Java NIO.
 *
 * WHY NOT File.deleteRecursively()?
 * ─────────────────────────────────
 * Kotlin's deleteRecursively() gebruikt intern File.walk() → FileTreeWalk.
 * FileTreeWalk implementeert kotlin.sequences.Sequence.  Als een andere plugin
 * (bijv. LocoLevels) ook de Kotlin-stdlib shadet, laden twee verschillende
 * classloaders hun eigen versie van FileTreeWalk en Sequence.  De JVM beschouwt
 * deze als incompatibele typen → ClassCastException:
 *
 *   "class kotlin.io.FileTreeWalk cannot be cast to class kotlin.sequences.Sequence
 *    (kotlin.io.FileTreeWalk is in loader 'LocoLevels.jar'; kotlin.sequences.Sequence
 *    is in loader 'LocoPillars.jar')"
 *
 * Files.walk() retourneert java.util.stream.Stream<Path> — een puur Java-type
 * uit de JDK bootstrap classloader dat NOOIT via een plugin-classloader laadt
 * en dus altijd compatibel is, ongeacht welke plugins Kotlin shaden.
 *
 * @return true als de map niet meer bestaat na de aanroep
 */
fun File.deleteRecursivelyJava(): Boolean {
    if (!exists()) return true
    return try {
        Files.walk(toPath())
            .sorted(Comparator.reverseOrder())   // diepste bestanden eerst verwijderen
            .forEach { Files.deleteIfExists(it) }
        !exists()
    } catch (e: Exception) {
        false
    }
}
