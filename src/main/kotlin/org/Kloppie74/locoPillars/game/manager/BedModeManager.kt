package org.Kloppie74.locoPillars.game.manager

import org.bukkit.Location
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks player beds (BedModus) and pending bed-respawns.
 * A player's "bed" is identified by the HEAD block location.
 */
class BedModeManager {

    // playerUUID -> Pair(headLocation, footLocation)
    private val playerBeds = ConcurrentHashMap<UUID, Pair<Location, Location>>()
    // playerUUID -> headLocation they should respawn at after death
    private val pendingBedRespawn = ConcurrentHashMap<UUID, Location>()

    fun placeBed(playerUUID: UUID, headLoc: Location, footLoc: Location) {
        playerBeds[playerUUID] = Pair(headLoc.clone(), footLoc.clone())
    }

    fun hasActiveBed(playerUUID: UUID): Boolean = playerBeds.containsKey(playerUUID)

    fun getBedHeadLocation(playerUUID: UUID): Location? = playerBeds[playerUUID]?.first?.clone()

    /**
     * Called when a bed block at [location] is broken (explosion or break).
     * Finds the player whose bed covers this location and removes the entry.
     * Returns the ownerUUID + both locations if a match was found.
     */
    fun onBedBroken(location: Location): Pair<UUID, Pair<Location, Location>>? {
        val entry = playerBeds.entries.firstOrNull { (_, pair) ->
            sameBlock(pair.first, location) || sameBlock(pair.second, location)
        } ?: return null
        playerBeds.remove(entry.key)
        return Pair(entry.key, entry.value)
    }

    fun markPendingBedRespawn(playerUUID: UUID, bedHeadLocation: Location) {
        pendingBedRespawn[playerUUID] = bedHeadLocation.clone()
    }

    fun consumePendingBedRespawn(playerUUID: UUID): Location? = pendingBedRespawn.remove(playerUUID)

    fun clearPlayer(playerUUID: UUID) {
        playerBeds.remove(playerUUID)
        pendingBedRespawn.remove(playerUUID)
    }

    fun clearAll() {
        playerBeds.clear()
        pendingBedRespawn.clear()
    }

    private fun sameBlock(a: Location, b: Location): Boolean =
        a.world == b.world &&
        a.blockX == b.blockX &&
        a.blockY == b.blockY &&
        a.blockZ == b.blockZ
}
