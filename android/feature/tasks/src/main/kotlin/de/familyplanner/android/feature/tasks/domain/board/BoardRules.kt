package de.familyplanner.android.feature.tasks.domain.board

enum class CardAction { REPORT, WITHDRAW, ACCEPT, REJECT, ACCEPT_DIRECTLY, REMOVE }

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
                TaskStatus.REVIEW -> listOf(CardAction.ACCEPT, CardAction.REJECT, CardAction.REMOVE)
                TaskStatus.TODO -> listOf(CardAction.ACCEPT_DIRECTLY, CardAction.REMOVE)
                TaskStatus.DONE -> emptyList()
            }
        card.memberId != viewerId -> emptyList()
        card.status == TaskStatus.TODO -> listOf(CardAction.REPORT)
        card.status == TaskStatus.REVIEW -> listOf(CardAction.WITHDRAW)
        else -> emptyList()
    }

data class LaneSections(
    val todo: List<TaskCard>,
    val review: List<TaskCard>,
    val done: List<TaskCard>,
)

/** TODO nach Zuweisung (älteste zuerst), REVIEW nach Meldung (älteste zuerst), DONE neueste zuerst. */
fun sections(cards: List<TaskCard>): LaneSections =
    LaneSections(
        todo = cards.filter { it.status == TaskStatus.TODO }.sortedBy { it.assignedAt },
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
fun openCards(lane: TaskLane?): List<TaskCard> = lane?.cards.orEmpty().filter { it.status != TaskStatus.DONE }

/** Ersetzt [updated] im Stand (Übergang) oder entfernt die Karte [removedId]. */
fun List<TaskLane>.replacing(updated: TaskCard): List<TaskLane> =
    map { lane ->
        if (lane.memberId != updated.memberId) lane else lane.copy(cards = lane.cards.map { if (it.id == updated.id) updated else it })
    }

fun List<TaskLane>.removing(cardId: String): List<TaskLane> = map { lane -> lane.copy(cards = lane.cards.filterNot { it.id == cardId }) }
