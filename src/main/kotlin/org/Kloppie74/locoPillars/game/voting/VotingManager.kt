package org.Kloppie74.locoPillars.game.voting

import org.bukkit.entity.Player
import org.Kloppie74.locoPillars.LocoPillars
import org.Kloppie74.locoPillars.game.data.GameState
import org.Kloppie74.locoPillars.game.instance.GameInstance
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class VotingManager(private val plugin: LocoPillars, val loader: VotingLoader) {

    // instanceId -> (playerId -> moduleId)
    internal val votes = ConcurrentHashMap<UUID, ConcurrentHashMap<UUID, String>>()
    // instanceId -> active context
    private val activeEffects = ConcurrentHashMap<UUID, VotingGameContext>()

    /** Voting window is open only before the game start-timer / cage teleport fires. */
    fun isVotingOpen(instance: GameInstance): Boolean =
        instance.state == GameState.WAITING || instance.state == GameState.STARTING

    fun castVote(player: Player, instance: GameInstance, moduleId: String): Boolean {
        if (!isVotingOpen(instance)) return false
        if (loader.get(moduleId) == null) return false
        votes.getOrPut(instance.id) { ConcurrentHashMap() }[player.uniqueId] = moduleId
        return true
    }

    fun unvote(player: Player, instance: GameInstance) {
        votes[instance.id]?.remove(player.uniqueId)
    }

    fun hasVoted(player: Player, instance: GameInstance): Boolean =
        votes[instance.id]?.containsKey(player.uniqueId) == true

    fun getPlayerVote(player: Player, instance: GameInstance): String? =
        votes[instance.id]?.get(player.uniqueId)

    fun getVoteCounts(instance: GameInstance): Map<String, Int> =
        votes[instance.id]?.values?.groupingBy { it }?.eachCount() ?: emptyMap()

    fun getWinner(instance: GameInstance): VotingSettings? {
        val counts = getVoteCounts(instance)
        if (counts.isEmpty()) return loader.getAll().takeIf { it.isNotEmpty() }?.random()
        val winnerId = counts.maxByOrNull { it.value }?.key ?: return null
        return loader.get(winnerId)
    }

    fun activateWinner(instance: GameInstance) {
        if (activeEffects.containsKey(instance.id)) return
        val winner = getWinner(instance) ?: run {
            plugin.logger.info("[Voting] No voting modules available for instance ${instance.id}")
            return
        }
        val context = VotingGameContext(plugin, instance)
        activeEffects[instance.id] = context
        plugin.logger.info("[Voting] Activating '${winner.id}' for instance ${instance.id}")
        loader.scriptEngine.executeModule(winner.id, winner.scriptFile, context)
    }

    fun deactivateEffect(instance: GameInstance) {
        val context = activeEffects.remove(instance.id) ?: return
        context.cleanup()
        plugin.logger.info("[Voting] Deactivated voting effect for instance ${instance.id}")
        resetVotes(instance)
    }

    fun resetVotes(instance: GameInstance) {
        votes.remove(instance.id)
    }

    fun isEffectActive(instance: GameInstance): Boolean = activeEffects.containsKey(instance.id)
}
