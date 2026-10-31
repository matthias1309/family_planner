package de.familyplanner.gamification.rewards

import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.gamification.account.Actor
import de.familyplanner.gamification.ledger.LedgerRepository
import de.familyplanner.gamification.requireChild
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

private const val REWARD_NOT_FOUND = "Belohnung nicht gefunden"
private const val REWARD_ARCHIVED = "Die Belohnung ist deaktiviert"

/** Reward catalog (REQ-GAME-040) and the savings goal of a child (REQ-GAME-041). */
class RewardService(
    private val members: MemberRepository,
    private val rewards: RewardRepository,
    private val ledger: LedgerRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(RewardService::class.java)

    suspend fun list(includeArchived: Boolean): List<Reward> = rewards.findAll(includeArchived)

    suspend fun create(
        actor: Actor,
        input: RewardInput,
    ): Reward {
        requireParent(actor)
        val valid = validated(input)
        val reward = Reward(RewardId(newId()), valid.title, valid.emoji, valid.price, archived = false)
        rewards.create(reward, actor.id, clock.instant())
        log.info("Reward created: {}", reward.id.value)
        return reward
    }

    suspend fun update(
        actor: Actor,
        id: RewardId,
        input: RewardInput,
    ): Reward {
        requireParent(actor)
        val valid = validated(input)
        val existing = rewards.findById(id) ?: throw AppException.NotFound(REWARD_NOT_FOUND)
        val reward = Reward(id, valid.title, valid.emoji, valid.price, archived = false)
        if (existing.archived || !rewards.update(reward, clock.instant())) throw AppException.Conflict(REWARD_ARCHIVED)
        log.info("Reward updated: {}", id.value)
        return reward
    }

    suspend fun archive(
        actor: Actor,
        id: RewardId,
    ) {
        requireParent(actor)
        if (!rewards.archive(id, clock.instant())) throw AppException.NotFound(REWARD_NOT_FOUND)
        log.info("Reward archived: {}", id.value)
    }

    suspend fun goal(
        actor: Actor,
        memberId: MemberId,
    ): SavingsGoal {
        members.requireChild(memberId)
        if (!actor.isParent && actor.id != memberId) throw AppException.Forbidden("Nur eigenes Sparziel")
        return goalOf(memberId)
    }

    suspend fun setGoal(
        actor: Actor,
        memberId: MemberId,
        rewardId: RewardId,
    ): SavingsGoal {
        requireOwnGoal(actor, memberId)
        val reward = rewards.findById(rewardId) ?: throw AppException.NotFound(REWARD_NOT_FOUND)
        if (reward.archived) throw AppException.Conflict(REWARD_ARCHIVED)
        rewards.setGoal(memberId, rewardId, clock.instant())
        log.info("Savings goal set: {} -> {}", memberId.value, rewardId.value)
        return goalOf(memberId)
    }

    suspend fun clearGoal(
        actor: Actor,
        memberId: MemberId,
    ) {
        requireOwnGoal(actor, memberId)
        rewards.clearGoal(memberId)
        log.info("Savings goal cleared: {}", memberId.value)
    }

    private fun requireParent(actor: Actor) {
        if (!actor.isParent) throw AppException.Forbidden()
    }

    private suspend fun requireOwnGoal(
        actor: Actor,
        memberId: MemberId,
    ) {
        members.requireChild(memberId)
        if (actor.id != memberId) throw AppException.Forbidden("Nur das Kind selbst wählt sein Sparziel")
    }

    private suspend fun goalOf(memberId: MemberId): SavingsGoal {
        val balance = ledger.totalsOf(memberId).balance
        val rewardId = rewards.goalOf(memberId) ?: return SavingsGoal(memberId, null, balance)
        val reward = rewards.findById(rewardId)
        if (reward == null) log.warn("Savings goal of {} points to unknown reward {}", memberId.value, rewardId.value)
        return SavingsGoal(memberId, reward, balance)
    }
}
