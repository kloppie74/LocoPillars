package org.Kloppie74.locoPillars.party

import java.util.UUID

/**
 * Represents a party group.
 *
 * @param id             Unique identifier for this party.
 * @param leader         UUID of the party leader.
 * @param members        All member UUIDs, including the leader.
 * @param pendingInvites UUID of the invitee → expiry timestamp (ms) of their pending invite.
 */
class Party(
    val id: UUID = UUID.randomUUID(),
    var leader: UUID,
    val members: MutableSet<UUID> = mutableSetOf(),
    val pendingInvites: MutableMap<UUID, Long> = mutableMapOf()
) {
    val size: Int get() = members.size

    fun isMember(uuid: UUID): Boolean = members.contains(uuid)
    fun isLeader(uuid: UUID): Boolean = leader == uuid

    /**
     * Returns true if [uuid] has a pending invite that has not yet expired.
     */
    fun hasPendingInvite(uuid: UUID): Boolean {
        val expiry = pendingInvites[uuid] ?: return false
        return expiry > System.currentTimeMillis()
    }
}
