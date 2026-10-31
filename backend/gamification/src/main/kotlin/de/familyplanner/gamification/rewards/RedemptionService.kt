package de.familyplanner.gamification.rewards

import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.validatedName
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.gamification.account.Actor
import de.familyplanner.gamification.ledger.ENTRIES_LIMIT_MAX
import de.familyplanner.gamification.requireChild
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

private const val REDEMPTION_NOT_FOUND = "Einlösung nicht gefunden"

/** Redeeming rewards with points (REQ-GAME-042) and the parents' decision (REQ-GAME-043). */
class RedemptionService(
    private val members: MemberRepository,
    private val redemptions: RedemptionRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(RedemptionService::class.java)

    suspend fun redeem(
        actor: Actor,
        rewardId: RewardId,
    ): Redemption {
        if (actor.isParent) throw AppException.Forbidden("Nur Kinder lösen Belohnungen ein")
        members.requireChild(actor.id)
        val outcome = redemptions.redeem(RedemptionId(newId()), actor.id, rewardId, newId(), clock.instant())
        val redemption = (outcome as? RedeemOutcome.Redeemed)?.redemption ?: throw failure(outcome)
        log.info("Reward redeemed: {} by {}", redemption.id.value, actor.id.value)
        return redemption
    }

    suspend fun list(
        actor: Actor,
        status: RedemptionStatus?,
        limit: Int,
    ): List<Redemption> {
        if (limit !in 1..ENTRIES_LIMIT_MAX) throw AppException.Validation("Ungültiger Wert für 'limit'")
        return redemptions.find(actor.id.takeUnless { actor.isParent }, status, limit)
    }

    suspend fun fulfill(
        actor: Actor,
        id: RedemptionId,
    ): Redemption {
        if (!actor.isParent) throw AppException.Forbidden()
        decided(redemptions.fulfill(id, actor.id, clock.instant()))
        log.info("Redemption fulfilled: {}", id.value)
        return reload(id)
    }

    suspend fun reject(
        actor: Actor,
        id: RedemptionId,
        reason: String,
    ): Redemption {
        if (!actor.isParent) throw AppException.Forbidden()
        val validReason = validatedName(reason, "Begründung", REJECTION_REASON_MAX)
        decided(redemptions.reject(id, actor.id, validReason, clock.instant(), newId()))
        log.info("Redemption rejected: {}", id.value)
        return reload(id)
    }

    private fun failure(outcome: RedeemOutcome): AppException =
        when (outcome) {
            is RedeemOutcome.Redeemed -> AppException.Conflict("Die Belohnung wurde bereits eingelöst")
            RedeemOutcome.RewardNotFound -> AppException.NotFound("Belohnung nicht gefunden")
            RedeemOutcome.RewardArchived -> AppException.Conflict("Die Belohnung ist deaktiviert")
            is RedeemOutcome.InsufficientBalance ->
                AppException.Conflict("Nicht genug Guthaben: ${outcome.balance} von ${outcome.price} Punkten")
        }

    private fun decided(outcome: DecisionOutcome) {
        when (outcome) {
            DecisionOutcome.CHANGED -> Unit
            DecisionOutcome.NOT_FOUND -> throw AppException.NotFound(REDEMPTION_NOT_FOUND)
            DecisionOutcome.NOT_PENDING -> throw AppException.Conflict("Die Einlösung ist bereits entschieden")
        }
    }

    private suspend fun reload(id: RedemptionId): Redemption = redemptions.findById(id) ?: throw AppException.NotFound(REDEMPTION_NOT_FOUND)
}
