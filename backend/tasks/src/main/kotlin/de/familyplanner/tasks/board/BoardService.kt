package de.familyplanner.tasks.board

import de.familyplanner.core.error.AppException
import de.familyplanner.core.error.validatedName
import de.familyplanner.core.events.EventNotifier
import de.familyplanner.core.member.Member
import de.familyplanner.core.member.MemberId
import de.familyplanner.core.member.MemberRepository
import de.familyplanner.core.member.Role
import de.familyplanner.tasks.catalog.CatalogRepository
import de.familyplanner.tasks.catalog.CatalogTaskId
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.util.UUID

internal const val CARD_NOT_FOUND = "Karte nicht gefunden"
internal const val MEMBER_NOT_FOUND = "Mitglied nicht gefunden"

class BoardService(
    private val members: MemberRepository,
    private val catalog: CatalogRepository,
    private val cards: TaskCardRepository,
    private val events: EventNotifier,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(BoardService::class.java)

    suspend fun assign(
        memberId: MemberId,
        catalogTaskIds: List<CatalogTaskId>,
    ): List<TaskCard> {
        if (catalogTaskIds.size !in 1..ASSIGN_MAX) {
            throw AppException.Validation("Bitte 1 bis $ASSIGN_MAX Aufgaben auswählen")
        }
        requireChild(memberId)
        val available = catalog.findActiveByIds(catalogTaskIds.distinct()).associateBy { it.id }
        val now = clock.instant()
        val created = catalogTaskIds.map { newCard(TaskCardId(newId()), memberId, availableTask(available, it), now) }
        cards.createAll(created)
        log.info("Task cards assigned: {} to {}", created.size, memberId.value)
        return created
    }

    suspend fun board(): List<Lane> {
        val children = members.findAll().filter { it.role == Role.CHILD }.sortedBy { it.name }
        val byMember = cards.findBoard(clock.instant().minus(Duration.ofDays(DONE_VISIBLE_DAYS))).groupBy { it.memberId }
        return children.map { Lane(it, sortedForLane(byMember[it.id].orEmpty())) }
    }

    suspend fun history(
        memberId: MemberId,
        limit: Int,
    ): List<TaskCard> {
        if (limit !in 1..HISTORY_LIMIT_MAX) throw AppException.Validation("Limit muss zwischen 1 und $HISTORY_LIMIT_MAX liegen")
        requireMember(memberId)
        return cards.findDone(memberId, limit)
    }

    suspend fun report(
        actor: Actor,
        id: TaskCardId,
    ) = move(actor, id, TaskTransition.REPORT, null)

    suspend fun withdraw(
        actor: Actor,
        id: TaskCardId,
    ) = move(actor, id, TaskTransition.WITHDRAW, null)

    suspend fun accept(
        actor: Actor,
        id: TaskCardId,
    ): TaskCard = move(actor, id, TaskTransition.ACCEPT, null).also { notifySubscribers() }

    suspend fun reject(
        actor: Actor,
        id: TaskCardId,
        reason: String?,
    ) = move(actor, id, TaskTransition.REJECT, validatedReason(reason))

    suspend fun remove(id: TaskCardId) {
        when (cards.remove(id)) {
            TransitionOutcome.CHANGED -> log.info("Task card removed: {}", id.value)
            TransitionOutcome.NOT_FOUND -> throw AppException.NotFound(CARD_NOT_FOUND)
            TransitionOutcome.WRONG_STATUS -> throw AppException.Conflict("Erledigte Aufgaben können nicht entfernt werden")
        }
    }

    private suspend fun move(
        actor: Actor,
        id: TaskCardId,
        transition: TaskTransition,
        reason: String?,
    ): TaskCard {
        val card = requireCard(id)
        if (!transition.isAllowedFor(actor, card)) throw forbidden(transition)
        requireChanged(cards.transition(id, transition, actor.id, reason, clock.instant()), transition)
        log.info("Task card {}: {}", transition, id.value)
        return requireCard(id)
    }

    private suspend fun requireCard(id: TaskCardId): TaskCard = cards.findById(id) ?: throw AppException.NotFound(CARD_NOT_FOUND)

    private suspend fun requireChild(id: MemberId) {
        val member = members.findById(id) ?: throw AppException.NotFound(MEMBER_NOT_FOUND)
        if (member.role != Role.CHILD) throw AppException.Validation("Aufgaben können nur Kindern zugewiesen werden")
    }

    /** A failing delivery never fails the request; the event stays in the log and is retried (ADR-0016). */
    private suspend fun notifySubscribers() {
        try {
            events.dispatchPending()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            @Suppress("TooGenericExceptionCaught") failure: Exception,
        ) {
            log.error("Event delivery failed", failure)
        }
    }

    private suspend fun requireMember(id: MemberId): Member = members.findById(id) ?: throw AppException.NotFound(MEMBER_NOT_FOUND)

    private fun validatedReason(reason: String?): String? =
        reason?.takeIf { it.isNotBlank() }?.let { validatedName(it, "Begründung", REASON_MAX) }
}
