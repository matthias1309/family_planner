package de.familyplanner.android.feature.gamification.domain.notice

import de.familyplanner.android.feature.gamification.domain.Redemption
import de.familyplanner.android.feature.gamification.domain.RedemptionStatus

/** Ohne [previous] keine Hinweise (Basislinie, ADR-0013 Nr. 5). */
fun rewardNotices(
    isParent: Boolean,
    previous: RedemptionSnapshot?,
    current: List<Redemption>,
    names: Map<String, String>,
): List<RewardNotice> {
    if (previous == null) return emptyList()
    return if (isParent) {
        current
            .filter { it.status == RedemptionStatus.PENDING && it.id !in previous.statuses }
            .map { RewardNotice.Requested(it.id, names[it.memberId], it.title, it.emoji) }
    } else {
        current.mapNotNull { childNotice(previous, it) }
    }
}

private fun childNotice(
    previous: RedemptionSnapshot,
    now: Redemption,
): RewardNotice? {
    val before = previous.statuses[now.id]
    val decidedSinceSync = before == null && now.decidedAt?.isAfter(previous.syncedAt) == true
    if (before != RedemptionStatus.PENDING && !decidedSinceSync) return null
    return when (now.status) {
        RedemptionStatus.FULFILLED -> RewardNotice.Fulfilled(now.id, now.title, now.emoji)
        RedemptionStatus.REJECTED -> RewardNotice.Rejected(now.id, now.title, now.emoji, now.rejectionReason)
        RedemptionStatus.PENDING -> null
    }
}
