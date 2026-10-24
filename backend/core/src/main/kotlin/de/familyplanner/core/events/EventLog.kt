package de.familyplanner.core.events

import de.familyplanner.core.persistence.DomainEventsTable
import de.familyplanner.core.persistence.EventCursorsTable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.upsert
import java.time.Instant

/** Appends [event] inside the caller's transaction - call it from the same `dbQuery` block as the change (ADR-0016). */
fun JdbcTransaction.recordEvent(
    event: DomainEvent,
    at: Instant,
) {
    val (eventType, eventPayload) = DomainEventCodec.encode(event)
    DomainEventsTable.insert {
        it[type] = eventType
        it[payload] = eventPayload
        it[occurredAt] = at.toEpochMilli()
    }
}

internal fun JdbcTransaction.eventsAfter(
    seq: Long,
    limit: Int,
): List<StoredEvent> =
    DomainEventsTable
        .selectAll()
        .where { DomainEventsTable.seq greater seq }
        .orderBy(DomainEventsTable.seq, SortOrder.ASC)
        .limit(limit)
        .map {
            val type = it[DomainEventsTable.type]
            StoredEvent(
                seq = it[DomainEventsTable.seq],
                type = type,
                occurredAt = Instant.ofEpochMilli(it[DomainEventsTable.occurredAt]),
                event = DomainEventCodec.decode(type, it[DomainEventsTable.payload]),
            )
        }

internal fun JdbcTransaction.cursorOf(subscriber: String): Long =
    EventCursorsTable
        .selectAll()
        .where { EventCursorsTable.subscriber eq subscriber }
        .firstOrNull()
        ?.get(EventCursorsTable.lastSeq) ?: 0L

internal fun JdbcTransaction.saveCursor(
    subscriber: String,
    seq: Long,
) {
    EventCursorsTable.upsert {
        it[EventCursorsTable.subscriber] = subscriber
        it[lastSeq] = seq
    }
}
