package de.familyplanner.android.feature.tasks.domain.board

import java.time.Instant

enum class CardAction { REPORT, WITHDRAW, ACCEPT, REJECT, ACCEPT_DIRECTLY, SET_DUE_DATE, REMOVE, PUT_BACK }

/** Aktionen, die der Betrachter an [card] sieht (REQ-TASK-022–024); offline keine. */
fun cardActions(
    card: TaskCard,
    viewerId: String,
    isParent: Boolean,
    isOffline: Boolean,
): List<CardAction> =
    when {
        isOffline -> emptyList()
        isParent ->
            when (card.status) {
                TaskStatus.REVIEW -> listOf(CardAction.ACCEPT, CardAction.REJECT) + parentCardActions(card)
                TaskStatus.TODO -> listOf(CardAction.ACCEPT_DIRECTLY) + parentCardActions(card)
                TaskStatus.DONE -> emptyList()
            }
        card.memberId != viewerId -> emptyList()
        card.status == TaskStatus.TODO && card.selfAssigned -> listOf(CardAction.PUT_BACK, CardAction.REPORT)
        card.status == TaskStatus.TODO -> listOf(CardAction.REPORT)
        card.status == TaskStatus.REVIEW -> listOf(CardAction.WITHDRAW)
        else -> emptyList()
    }

/** Frist ändern gibt es nicht für selbst genommene Karten (haben keine Frist, REQ-TASK-028). */
private fun parentCardActions(card: TaskCard): List<CardAction> =
    if (card.selfAssigned) listOf(CardAction.REMOVE) else listOf(CardAction.SET_DUE_DATE, CardAction.REMOVE)

data class LaneSections(
    val todo: List<TaskCard>,
    val review: List<TaskCard>,
    val done: List<TaskCard>,
)

/** Überfällige zuerst (früheste Frist zuerst), der Rest in der Reihenfolge von [restOrder]. */
private fun List<TaskCard>.overdueFirst(
    now: Instant,
    restOrder: Comparator<TaskCard>,
): List<TaskCard> {
    val (overdue, rest) = partition { it.isOverdue(now) }
    return overdue.sortedBy { it.dueAt } + rest.sortedWith(restOrder)
}

private val ByAssignment = compareBy<TaskCard> { it.assignedAt }
private val ByDueThenAssignment = compareBy<TaskCard> { it.dueAt == null }.thenBy { it.dueAt }.thenBy { it.assignedAt }

/** TODO überfällig zuerst, dann nach Zuweisung (REQ-TASK-027), REVIEW nach Meldung (älteste zuerst), DONE neueste zuerst. */
fun sections(
    cards: List<TaskCard>,
    now: Instant,
): LaneSections =
    LaneSections(
        todo = cards.filter { it.status == TaskStatus.TODO }.overdueFirst(now, ByAssignment),
        review = cards.filter { it.status == TaskStatus.REVIEW }.sortedBy { it.reportedAt ?: it.assignedAt },
        done = cards.filter { it.status == TaskStatus.DONE }.sortedByDescending { it.doneAt ?: it.assignedAt },
    )

/** Alle gemeldeten Karten über alle Kinder, älteste Meldung zuerst (Sammelansicht der Eltern). */
fun reviewQueue(lanes: List<TaskLane>): List<Pair<TaskLane, TaskCard>> =
    lanes
        .flatMap { lane -> lane.cards.filter { it.status == TaskStatus.REVIEW }.map { lane to it } }
        .sortedBy { (_, card) -> card.reportedAt ?: card.assignedAt }

fun reviewCount(lanes: List<TaskLane>): Int = lanes.sumOf { lane -> lane.cards.count { it.status == TaskStatus.REVIEW } }

/** Reihenfolge der Spuren: für ein Kind die eigene zuerst, sonst wie vom Server (nach Name). */
fun orderedLanes(
    lanes: List<TaskLane>,
    viewerId: String,
    isParent: Boolean,
): List<TaskLane> = if (isParent) lanes else lanes.sortedBy { it.memberId != viewerId }

/** Karten des Kindes, die noch zu tun sind: eigene TODO und REVIEW. */
fun openCards(
    lane: TaskLane?,
    now: Instant,
): List<TaskCard> =
    lane
        ?.cards
        .orEmpty()
        .filter { it.status != TaskStatus.DONE }
        .overdueFirst(now, ByDueThenAssignment)

/** Überfällige Karten über alle [lanes] (Eltern) bzw. die übergebene Spur (Kind). */
fun overdueCount(
    lanes: List<TaskLane>,
    now: Instant,
): Int = lanes.sumOf { lane -> lane.cards.count { it.isOverdue(now) } }

/** Fügt [card] der Spur ihres Kindes hinzu (Karte aus dem Pool). */
fun List<TaskLane>.adding(card: TaskCard): List<TaskLane> =
    map { lane ->
        if (lane.memberId == card.memberId &&
            lane.cards.none { it.id == card.id }
        ) {
            lane.copy(cards = lane.cards + card)
        } else {
            lane
        }
    }

/** Ersetzt [updated] im Stand (Übergang) oder entfernt die Karte [removedId]. */
fun List<TaskLane>.replacing(updated: TaskCard): List<TaskLane> =
    map { lane ->
        if (lane.memberId != updated.memberId) lane else lane.copy(cards = lane.cards.map { if (it.id == updated.id) updated else it })
    }

fun List<TaskLane>.removing(cardId: String): List<TaskLane> = map { lane -> lane.copy(cards = lane.cards.filterNot { it.id == cardId }) }
