package org.Kloppie74.locoPillars.database.models

import java.util.UUID

data class VanishState(
    val playerId: String,
    var vanished: Boolean,
    var hideMe: Boolean,
    var hideOthers: Boolean,
    var showOthers: MutableSet<UUID>
) {
    fun getPlayerUUID(): UUID = UUID.fromString(playerId)
}