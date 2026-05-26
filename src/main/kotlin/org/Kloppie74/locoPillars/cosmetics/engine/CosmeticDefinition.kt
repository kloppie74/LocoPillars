package org.Kloppie74.locoPillars.cosmetics.engine

import org.bukkit.Material
import java.io.File

/**
 * A fully loaded cosmetic, ready for use.
 * For KILL/WIN/DEATH types, [scriptFile] contains the .kts logic.
 * For CAGE type, [schemFile] contains the schematic to paste.
 */
data class CosmeticDefinition(
    val id: String,
    val type: CosmeticType,
    val displayName: String,
    val lore: List<String>,
    val price: Int,
    val requiredLevel: Int,
    val permission: String,
    val rarity: CosmeticRarity,
    val iconMaterial: Material,
    val iconCustomModelData: Int,
    val active: Boolean,
    /** Only set for KILL / WIN / DEATH cosmetics */
    val scriptFile: File? = null,
    /** Only set for CAGE cosmetics */
    val schemFile: File? = null
)
