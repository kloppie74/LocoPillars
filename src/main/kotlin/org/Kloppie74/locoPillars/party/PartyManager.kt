package org.Kloppie74.locoPillars.party

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.utils.MSG
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * PartyManager — manages all parties on the server.
 *
 * Party limits are driven by permissions:
 *   locopillars.party.limit.<n>   (e.g. locopillars.party.limit.16)
 * The highest permission the player holds wins.
 * Default limit (no matching permission found) = [DEFAULT_LIMIT] (8).
 * Absolute maximum = [MAX_LIMIT] (16).
 *
 * When a party leader joins a game, all online party members join together
 * to the same game instance and are kept in the same team (or split evenly
 * across teams if the party is larger than team capacity).
 */
class PartyManager(private val plugin: LocoPillars) {

    companion object {
        const val DEFAULT_LIMIT   = 8
        const val MAX_LIMIT       = 16
        const val INVITE_EXPIRY_MS = 60_000L   // 60 seconds
    }

    // partyId → Party
    private val parties     = ConcurrentHashMap<UUID, Party>()
    // playerUuid → partyId
    private val playerParty = ConcurrentHashMap<UUID, UUID>()
    // players who currently have party-chat toggled on
    private val partyChatOn = ConcurrentHashMap.newKeySet<UUID>()
    // invitee UUID → partyId they were invited to
    private val pendingInviteIndex = ConcurrentHashMap<UUID, UUID>()

    // =========================================================================
    // Permission-based party limit
    // =========================================================================

    /**
     * Returns the party size limit for [player], derived from their highest
     * `locopillars.party.limit.<n>` permission.
     */
    fun getPartyLimit(player: Player): Int {
        for (limit in MAX_LIMIT downTo 1) {
            if (player.hasPermission("locopillars.party.limit.$limit")) return limit
        }
        return DEFAULT_LIMIT
    }

    // =========================================================================
    // Create / Disband
    // =========================================================================

    /**
     * Creates a new party for [leader].  Returns null (with a message) if
     * the leader is already in a party.
     */
    fun createParty(leader: Player): Party? {
        if (isInParty(leader)) {
            leader.sendMessage(MSG.lang("party.error.already_in_party"))
            return null
        }
        val party = Party(leader = leader.uniqueId, members = mutableSetOf(leader.uniqueId))
        parties[party.id]      = party
        playerParty[leader.uniqueId] = party.id
        leader.sendMessage(MSG.lang("party.created"))
        debugLog("Party created by ${leader.name} (id=${party.id})")
        return party
    }

    /**
     * Disbands [leader]'s party.  Only the leader may disband.
     */
    fun disbandParty(leader: Player): Boolean {
        val party = getParty(leader) ?: run {
            leader.sendMessage(MSG.lang("party.error.not_in_party"))
            return false
        }
        if (party.leader != leader.uniqueId) {
            leader.sendMessage(MSG.lang("party.error.not_leader"))
            return false
        }
        broadcastToParty(party, MSG.lang("party.disbanded", "leader" to leader.name))
        dissolveParty(party.id)
        debugLog("Party disbanded by ${leader.name}")
        return true
    }

    /** Internal: clean up all data structures for the given party. */
    private fun dissolveParty(partyId: UUID) {
        val party = parties.remove(partyId) ?: return
        party.members.forEach { uuid ->
            playerParty.remove(uuid)
            partyChatOn.remove(uuid)
        }
        // Remove any pending invites that pointed at this party
        pendingInviteIndex.entries.removeIf { it.value == partyId }
        party.pendingInvites.clear()
    }

    // =========================================================================
    // Invite / Accept / Deny
    // =========================================================================

    /**
     * Leader invites [target] to their party (creates a party if they don't
     * have one yet).
     */
    fun invite(inviter: Player, target: Player): Boolean {
        if (inviter.uniqueId == target.uniqueId) {
            inviter.sendMessage(MSG.lang("party.error.invite_self"))
            return false
        }

        // Auto-create party if inviter has none
        val party = getParty(inviter) ?: (createParty(inviter) ?: return false)

        if (party.leader != inviter.uniqueId) {
            inviter.sendMessage(MSG.lang("party.error.not_leader"))
            return false
        }
        if (party.isMember(target.uniqueId)) {
            inviter.sendMessage(MSG.lang("party.error.already_member", "player" to target.name))
            return false
        }
        val limit = getPartyLimit(inviter)
        if (party.size >= limit) {
            inviter.sendMessage(MSG.lang("party.error.party_full", "limit" to limit.toString()))
            return false
        }
        if (isInParty(target)) {
            inviter.sendMessage(MSG.lang("party.error.target_in_party", "player" to target.name))
            return false
        }

        val expiry = System.currentTimeMillis() + INVITE_EXPIRY_MS
        party.pendingInvites[target.uniqueId]      = expiry
        pendingInviteIndex[target.uniqueId]        = party.id

        inviter.sendMessage(MSG.lang("party.invite.sent",  "player" to target.name))
        target.sendMessage(MSG.lang("party.invite.received", "leader" to inviter.name))
        target.sendMessage(MSG.lang("party.invite.hint"))

        // Auto-expire after 60 s
        val inviterName = inviter.name
        val targetName  = target.name
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            val stillPending = party.pendingInvites[target.uniqueId] ?: return@Runnable
            if (stillPending != expiry) return@Runnable   // was accepted/denied in the meantime
            party.pendingInvites.remove(target.uniqueId)
            pendingInviteIndex.remove(target.uniqueId)
            Bukkit.getPlayer(target.uniqueId)?.sendMessage(MSG.lang("party.invite.expired"))
            Bukkit.getPlayer(inviter.uniqueId)?.sendMessage(MSG.lang("party.invite.expired_leader", "player" to targetName))
            debugLog("Invite from $inviterName to $targetName expired")
        }, INVITE_EXPIRY_MS / 50L)   // ms → ticks

        debugLog("${inviter.name} invited ${target.name} to party ${party.id}")
        return true
    }

    /** [player] accepts a pending invite. */
    fun acceptInvite(player: Player): Boolean {
        val partyId = pendingInviteIndex[player.uniqueId] ?: run {
            player.sendMessage(MSG.lang("party.error.no_invite"))
            return false
        }
        val party = parties[partyId] ?: run {
            pendingInviteIndex.remove(player.uniqueId)
            player.sendMessage(MSG.lang("party.error.party_disbanded"))
            return false
        }
        val expiry = party.pendingInvites[player.uniqueId]
        if (expiry == null || expiry < System.currentTimeMillis()) {
            pendingInviteIndex.remove(player.uniqueId)
            party.pendingInvites.remove(player.uniqueId)
            player.sendMessage(MSG.lang("party.invite.expired"))
            return false
        }
        val leaderPlayer = Bukkit.getPlayer(party.leader)
        val limit = if (leaderPlayer != null) getPartyLimit(leaderPlayer) else DEFAULT_LIMIT
        if (party.size >= limit) {
            player.sendMessage(MSG.lang("party.error.party_full_join", "limit" to limit.toString()))
            party.pendingInvites.remove(player.uniqueId)
            pendingInviteIndex.remove(player.uniqueId)
            return false
        }

        party.members.add(player.uniqueId)
        party.pendingInvites.remove(player.uniqueId)
        pendingInviteIndex.remove(player.uniqueId)
        playerParty[player.uniqueId] = partyId

        broadcastToParty(party, MSG.lang("party.member.joined", "player" to player.name))
        player.sendMessage(MSG.lang("party.join.success", "leader" to (leaderPlayer?.name ?: "Unknown")))
        debugLog("${player.name} accepted invite to party $partyId")
        return true
    }

    /** [player] denies a pending invite. */
    fun denyInvite(player: Player): Boolean {
        val partyId = pendingInviteIndex.remove(player.uniqueId) ?: run {
            player.sendMessage(MSG.lang("party.error.no_invite"))
            return false
        }
        val party = parties[partyId]
        party?.pendingInvites?.remove(player.uniqueId)
        player.sendMessage(MSG.lang("party.invite.denied"))
        party?.let { Bukkit.getPlayer(it.leader)?.sendMessage(MSG.lang("party.invite.denied_leader", "player" to player.name)) }
        debugLog("${player.name} denied invite to party $partyId")
        return true
    }

    // =========================================================================
    // Leave / Kick / Transfer
    // =========================================================================

    /** [player] leaves their party.  If they are the leader, the party disbands. */
    fun leaveParty(player: Player): Boolean {
        val party = getParty(player) ?: run {
            player.sendMessage(MSG.lang("party.error.not_in_party"))
            return false
        }
        if (party.leader == player.uniqueId) return disbandParty(player)

        party.members.remove(player.uniqueId)
        playerParty.remove(player.uniqueId)
        partyChatOn.remove(player.uniqueId)

        player.sendMessage(MSG.lang("party.leave.success"))
        broadcastToParty(party, MSG.lang("party.member.left", "player" to player.name))
        debugLog("${player.name} left party ${party.id}")
        return true
    }

    /** Leader kicks [target] from the party. */
    fun kickMember(leader: Player, target: Player): Boolean {
        val party = getParty(leader) ?: run { leader.sendMessage(MSG.lang("party.error.not_in_party")); return false }
        if (party.leader != leader.uniqueId) { leader.sendMessage(MSG.lang("party.error.not_leader")); return false }
        if (!party.isMember(target.uniqueId)) { leader.sendMessage(MSG.lang("party.error.not_member", "player" to target.name)); return false }
        if (target.uniqueId == leader.uniqueId) { leader.sendMessage(MSG.lang("party.error.kick_self")); return false }

        party.members.remove(target.uniqueId)
        playerParty.remove(target.uniqueId)
        partyChatOn.remove(target.uniqueId)

        target.sendMessage(MSG.lang("party.kick.kicked"))
        broadcastToParty(party, MSG.lang("party.kick.broadcast", "player" to target.name))
        debugLog("${leader.name} kicked ${target.name} from party ${party.id}")
        return true
    }

    /** Leader transfers leadership to [target]. */
    fun transferLeadership(leader: Player, target: Player): Boolean {
        val party = getParty(leader) ?: run { leader.sendMessage(MSG.lang("party.error.not_in_party")); return false }
        if (party.leader != leader.uniqueId) { leader.sendMessage(MSG.lang("party.error.not_leader")); return false }
        if (!party.isMember(target.uniqueId)) { leader.sendMessage(MSG.lang("party.error.not_member", "player" to target.name)); return false }

        party.leader = target.uniqueId
        broadcastToParty(party, MSG.lang("party.transfer.broadcast",
            "old_leader" to leader.name, "new_leader" to target.name))
        debugLog("${leader.name} transferred leadership to ${target.name} in party ${party.id}")
        return true
    }

    // =========================================================================
    // Party Chat
    // =========================================================================

    /**
     * Toggles party chat for [player].
     * Returns the new state (true = party chat on), or null if the player is not in a party.
     */
    fun togglePartyChat(player: Player): Boolean? {
        if (!isInParty(player)) {
            player.sendMessage(MSG.lang("party.error.not_in_party"))
            return null
        }
        return if (partyChatOn.remove(player.uniqueId)) {
            player.sendMessage(MSG.lang("party.chat.disabled"))
            false
        } else {
            partyChatOn.add(player.uniqueId)
            player.sendMessage(MSG.lang("party.chat.enabled"))
            true
        }
    }

    fun isPartyChatActive(player: Player): Boolean = partyChatOn.contains(player.uniqueId)

    /**
     * Sends [message] to all online party members of [player].
     * Formats with the `party.chat.format` language key.
     */
    fun sendPartyChat(player: Player, message: String) {
        val party = getParty(player) ?: return
        val formatted = MSG.lang("party.chat.format", "player" to player.name, "message" to message)
        getOnlineMembers(party).forEach { it.sendMessage(formatted) }
    }

    /** Sends a raw (pre-formatted) [message] to all online members of [party]. */
    fun broadcastToParty(party: Party, message: String) {
        getOnlineMembers(party).forEach { it.sendMessage(message) }
    }

    // =========================================================================
    // Disconnect / cleanup
    // =========================================================================

    /**
     * Called when [player] goes offline.  Removes them from their party gracefully:
     * - If they are the leader, leadership is transferred to the next member, or
     *   the party is dissolved if there are no other members.
     * - Otherwise they are simply removed.
     */
    fun handlePlayerDisconnect(player: Player) {
        pendingInviteIndex.remove(player.uniqueId)
        partyChatOn.remove(player.uniqueId)

        val party = getParty(player) ?: return

        if (party.leader == player.uniqueId) {
            val nextLeader = party.members.firstOrNull { it != player.uniqueId }
            if (nextLeader != null) {
                party.members.remove(player.uniqueId)
                playerParty.remove(player.uniqueId)
                party.leader = nextLeader
                val nextName = Bukkit.getOfflinePlayer(nextLeader).name ?: "Unknown"
                broadcastToParty(party, MSG.lang("party.leader.disconnected_transfer",
                    "old_leader" to player.name, "new_leader" to nextName))
                debugLog("${player.name} disconnected — leadership transferred to $nextName")
            } else {
                dissolveParty(party.id)
                debugLog("${player.name} disconnected — empty party dissolved")
            }
        } else {
            party.members.remove(player.uniqueId)
            playerParty.remove(player.uniqueId)
            broadcastToParty(party, MSG.lang("party.member.disconnected", "player" to player.name))
            debugLog("${player.name} disconnected and was removed from party ${party.id}")
        }
    }

    /** Removes all parties from memory — call from onDisable(). */
    fun cleanup() {
        parties.clear()
        playerParty.clear()
        partyChatOn.clear()
        pendingInviteIndex.clear()
    }

    // =========================================================================
    // Queries
    // =========================================================================

    fun getParty(player: Player): Party? = getParty(player.uniqueId)
    fun getParty(uuid: UUID): Party?     = playerParty[uuid]?.let { parties[it] }
    fun isInParty(player: Player): Boolean = playerParty.containsKey(player.uniqueId)
    fun isLeader(player: Player): Boolean  = getParty(player)?.leader == player.uniqueId

    fun getOnlineMembers(party: Party): List<Player> =
        party.members.mapNotNull { Bukkit.getPlayer(it) }.filter { it.isOnline }

    fun getOnlineMemberUUIDs(party: Party): List<UUID> =
        party.members.filter { Bukkit.getPlayer(it)?.isOnline == true }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun debugLog(msg: String) {
        if (plugin.configManager.debugMode) plugin.logger.info("[PartyManager] $msg")
    }
}
