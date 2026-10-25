package de.familyplanner.gamification.account

import de.familyplanner.core.member.Member
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.Role
import de.familyplanner.gamification.ledger.LedgerEntry
import de.familyplanner.gamification.ledger.Totals

/** Who performs a request. */
data class Actor(
    val id: MemberId,
    val role: Role,
) {
    val isParent: Boolean get() = role == Role.PARENT
}

data class Account(
    val memberId: MemberId,
    val totals: Totals,
    val entries: List<LedgerEntry>,
)

data class LeaderboardEntry(
    val memberId: MemberId,
    val name: String,
    val rank: Int,
    val collected: Int,
)

/** Highest collected first; ties share a rank (1, 1, 3) and are ordered by name (REQ-GAME-014). */
fun rankedLeaderboard(
    children: List<Member>,
    totals: Map<MemberId, Totals>,
): List<LeaderboardEntry> {
    val sorted =
        children
            .filter { it.role == Role.CHILD }
            .map { it to (totals[it.id] ?: Totals.ZERO).collected }
            .sortedWith(compareByDescending<Pair<Member, Int>> { it.second }.thenBy { it.first.name })
    return sorted.map { (member, collected) ->
        LeaderboardEntry(member.id, member.name, sorted.indexOfFirst { it.second == collected } + 1, collected)
    }
}
