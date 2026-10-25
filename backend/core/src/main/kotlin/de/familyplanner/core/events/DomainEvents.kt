package de.familyplanner.core.events

import kotlinx.serialization.Serializable
import java.time.Instant

/** Fact that another module may react to (ADR-0016). Payloads are versioned via their type name. */
sealed interface DomainEvent

/** A parent accepted a task card (REQ-TASK-023); GAME credits [points] to [memberId] once per [cardId]. */
@Serializable
data class TaskAccepted(
    val cardId: String,
    val memberId: String,
    val title: String,
    val points: Int,
    val acceptedBy: String,
    val acceptedAt: Long,
) : DomainEvent

/** A parent confirmed that a child cooked its approved dish (REQ-MEAL-034); GAME credits cooking points once per [dishId]. */
@Serializable
data class CookingConfirmed(
    val dishId: String,
    val memberId: String,
    val dishName: String,
    val date: String,
    val confirmedBy: String,
    val confirmedAt: Long,
) : DomainEvent

/** An event as read back from the log; [event] is `null` for a type this build does not know. */
data class StoredEvent(
    val seq: Long,
    val type: String,
    val occurredAt: Instant,
    val event: DomainEvent?,
)
