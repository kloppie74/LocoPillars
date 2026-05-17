package org.Kloppie74.locoPillars.editor

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.EntityType
import org.bukkit.entity.TextDisplay
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f

/**
 * EditorHologramHelper
 *
 * ### Fix 3 – Holograms zichtbaar vanuit alle hoeken
 *
 * Gebruikt [TextDisplay] entities (Minecraft 1.19.4+, standaard in Paper) met
 * [Display.Billboard.CENTER]. CENTER laat de display altijd naar de camera van
 * de speler draaien, net als een naam-label boven een mob — volledig zichtbaar
 * vanuit elke richting.
 *
 * ### Gebruik (in EditorSession / EditorSessionManager)
 * ```kotlin
 * // Spawn
 * val holo = EditorHologramHelper.spawn(location, "&eTeam1 / Player1", "&b(10, 65, -10)")
 * holograms.add(holo)
 *
 * // Bij refresh of sessie-einde
 * EditorHologramHelper.removeAll(holograms)
 * ```
 */
object EditorHologramHelper {

    private val legacy = LegacyComponentSerializer.legacyAmpersand()

    /**
     * Spawnt een [TextDisplay] hologram op [location].
     *
     * @param location Spawn-locatie (Y = onderkant eerste regel).
     * @param lines    Eén of meer tekstregels; ondersteunt `&`-kleurcodes.
     * @return De gespawnde [TextDisplay].
     */
    fun spawn(location: Location, vararg lines: String): TextDisplay {
        require(lines.isNotEmpty()) { "At least one line required" }

        // Combineer regels tot één Component met newlines ertussen
        val combined: Component = lines
            .map { legacy.deserialize(it) }
            .reduce { acc, comp -> acc.append(Component.newline()).append(comp) }

        val world = requireNotNull(location.world) { "Location has no world" }

        @Suppress("UNCHECKED_CAST")
        val display = world.spawnEntity(location, EntityType.TEXT_DISPLAY) as TextDisplay

        display.apply {
            text(combined)

            // ### Fix 3: CENTER billboard → draait altijd naar de camera
            billboard = Display.Billboard.CENTER

            // Semi-transparante zwarte achtergrond voor leesbaarheid
            backgroundColor = Color.fromARGB(160, 0, 0, 0)
            isShadowed      = false

            // Iets groter zodat het op editeer-afstand leesbaar is
            val scale = 1.2f
            transformation = Transformation(
                Vector3f(0f, 0f, 0f),
                AxisAngle4f(0f, 0f, 0f, 1f),
                Vector3f(scale, scale, scale),
                AxisAngle4f(0f, 0f, 0f, 1f)
            )

            isPersistent    = false   // sessie-scoped, niet op disk opslaan
            isVisibleByDefault = true
            viewRange       = 32f
        }

        return display
    }

    /** Verwijdert één hologram-entity (no-op als al verwijderd). */
    fun remove(entity: Entity?) {
        entity?.takeIf { it.isValid }?.remove()
    }

    /** Verwijdert alle hologram-entities in [entities] en leegt de collectie. */
    fun removeAll(entities: MutableCollection<out Entity>) {
        entities.forEach { it.takeIf { e -> e.isValid }?.remove() }
        entities.clear()
    }
}