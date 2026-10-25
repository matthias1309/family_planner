package de.familyplanner.gamification.account

import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.validatedName
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.core.member.Role
import de.familyplanner.gamification.ledger.ENTRIES_LIMIT_MAX
import de.familyplanner.gamification.ledger.EntryKind
import de.familyplanner.gamification.ledger.LedgerEntry
import de.familyplanner.gamification.ledger.LedgerEntryId
import de.familyplanner.gamification.ledger.LedgerRepository
import de.familyplanner.gamification.ledger.MANUAL_AMOUNT_MAX
import de.familyplanner.gamification.ledger.REASON_MAX
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID
import kotlin.math.abs

class AccountService(
    private val members: MemberRepository,
    private val ledger: LedgerRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(AccountService::class.java)

    suspend fun leaderboard(): List<LeaderboardEntry> =
        rankedLeaderboard(members.findAll().filter { it.role == Role.CHILD }, ledger.allTotals())

    suspend fun account(
        actor: Actor,
        memberId: MemberId,
        limit: Int,
    ): Account {
        if (limit !in 1..ENTRIES_LIMIT_MAX) throw AppException.Validation("Ungültiger Wert für 'limit'")
        requireChild(memberId)
        if (!actor.isParent && actor.id != memberId) throw AppException.Forbidden("Nur eigenes Punktekonto")
        return Account(memberId, ledger.totalsOf(memberId), ledger.entriesOf(memberId, limit))
    }

    suspend fun book(
        actor: Actor,
        memberId: MemberId,
        amount: Int,
        reason: String,
    ): LedgerEntry {
        if (!actor.isParent) throw AppException.Forbidden()
        requireValidAmount(amount)
        val title = validatedName(reason, "Begründung", REASON_MAX)
        requireChild(memberId)
        val entry = LedgerEntry(LedgerEntryId(newId()), memberId, EntryKind.MANUAL, amount, title, null, null, actor.id, clock.instant())
        ledger.append(entry)
        log.info("Manual points booked: {} to {}", amount, memberId.value)
        return entry
    }

    private fun requireValidAmount(amount: Int) {
        if (amount == 0) throw AppException.Validation("Der Betrag darf nicht 0 sein")
        if (abs(amount) > MANUAL_AMOUNT_MAX) {
            throw AppException.Validation("Der Betrag muss zwischen -$MANUAL_AMOUNT_MAX und $MANUAL_AMOUNT_MAX liegen")
        }
    }

    private suspend fun requireChild(id: MemberId) {
        val member = members.findById(id) ?: throw AppException.NotFound("Mitglied nicht gefunden")
        if (member.role != Role.CHILD) throw AppException.Validation("Eltern sammeln keine Punkte")
    }
}
