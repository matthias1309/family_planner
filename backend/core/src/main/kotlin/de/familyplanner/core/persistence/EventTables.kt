package de.familyplanner.core.persistence

import org.jetbrains.exposed.v1.core.Table

object DomainEventsTable : Table("core_domain_events") {
    val seq = long("seq").autoIncrement()
    val type = varchar("type", TYPE_LENGTH)
    val payload = text("payload")
    val occurredAt = long("occurred_at")

    override val primaryKey = PrimaryKey(seq)
}

object EventCursorsTable : Table("core_event_cursors") {
    val subscriber = varchar("subscriber", SUBSCRIBER_LENGTH)
    val lastSeq = long("last_seq")

    override val primaryKey = PrimaryKey(subscriber)
}

private const val TYPE_LENGTH = 60
private const val SUBSCRIBER_LENGTH = 60
