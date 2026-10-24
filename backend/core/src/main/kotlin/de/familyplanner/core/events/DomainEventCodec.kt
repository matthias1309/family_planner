package de.familyplanner.core.events

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Maps events to their versioned type name and JSON payload (ADR-0016). */
internal object DomainEventCodec {
    const val TASK_ACCEPTED = "task.accepted.v1"

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(event: DomainEvent): Pair<String, String> =
        when (event) {
            is TaskAccepted -> TASK_ACCEPTED to json.encodeToString(event)
        }

    /** Returns `null` for a type this build does not know. */
    fun decode(
        type: String,
        payload: String,
    ): DomainEvent? =
        when (type) {
            TASK_ACCEPTED -> json.decodeFromString<TaskAccepted>(payload)
            else -> null
        }
}
