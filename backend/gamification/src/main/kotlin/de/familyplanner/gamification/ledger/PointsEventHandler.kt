package de.familyplanner.gamification.ledger

import de.familyplanner.core.events.CookingConfirmed
import de.familyplanner.core.events.StoredEvent
import de.familyplanner.core.events.TaskAccepted
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.core.member.Role
import de.familyplanner.gamification.settings.GameSettingsService
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

/** Turns accepted tasks and confirmed cooking into credits; safe to call twice for the same event (ADR-0016). */
class PointsEventHandler(
    private val members: MemberRepository,
    private val ledger: LedgerRepository,
    private val settings: GameSettingsService,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(PointsEventHandler::class.java)

    suspend fun handle(stored: StoredEvent) {
        val credit =
            when (val event = stored.event) {
                is TaskAccepted ->
                    Credit(
                        event.memberId,
                        EntryKind.TASK,
                        event.points,
                        event.title,
                        taskSourceKey(event.cardId),
                        event.acceptedBy,
                        event.acceptedAt,
                    )
                is CookingConfirmed ->
                    Credit(
                        event.memberId,
                        EntryKind.COOKING,
                        settings.current().cookingPoints,
                        event.dishName,
                        cookingSourceKey(event.dishId),
                        event.confirmedBy,
                        event.confirmedAt,
                    )
                null -> return
            }
        book(stored.seq, credit)
    }

    private suspend fun book(
        seq: Long,
        credit: Credit,
    ) {
        val memberId = MemberId(credit.memberId)
        if (members.findById(memberId)?.role != Role.CHILD) {
            log.warn("Skipping points for seq {}: member {} is not a child", seq, credit.memberId)
            return
        }
        if (credit.amount == 0) {
            log.warn("Skipping points for seq {}: amount is 0", seq)
            return
        }
        val entry =
            LedgerEntry(
                id = LedgerEntryId(newId()),
                memberId = memberId,
                kind = credit.kind,
                amount = credit.amount,
                title = credit.title,
                note = null,
                sourceKey = credit.sourceKey,
                createdBy = MemberId(credit.createdBy),
                bookedAt = Instant.ofEpochMilli(credit.bookedAtMillis),
            )
        if (ledger.append(entry)) {
            log.info("Points credited: {} {} to {}", credit.kind, credit.amount, credit.memberId)
        } else {
            log.debug("Points already credited for seq {}", seq)
        }
    }

    private data class Credit(
        val memberId: String,
        val kind: EntryKind,
        val amount: Int,
        val title: String,
        val sourceKey: String,
        val createdBy: String,
        val bookedAtMillis: Long,
    )
}
