package de.familyplanner.core.events

import de.familyplanner.core.persistence.dbQuery
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/** What a module that records events needs: trigger delivery after its transaction committed. */
fun interface EventNotifier {
    suspend fun dispatchPending()
}

/** Delivers logged events to subscribers in order, at least once (ADR-0016). */
class DomainEventDispatcher(
    private val db: Database,
) : EventNotifier {
    private val log = LoggerFactory.getLogger(DomainEventDispatcher::class.java)
    private val subscribers = ConcurrentHashMap<String, suspend (StoredEvent) -> Unit>()
    private val mutex = Mutex()

    /** [name] identifies the cursor and must stay stable across releases. */
    fun subscribe(
        name: String,
        handler: suspend (StoredEvent) -> Unit,
    ) {
        subscribers[name] = handler
    }

    /** Delivers everything each subscriber has not handled yet; a failing subscriber stops at that event and retries next time. */
    override suspend fun dispatchPending() =
        mutex.withLock {
            subscribers.forEach { (name, handler) -> deliver(name, handler) }
        }

    private suspend fun deliver(
        name: String,
        handler: suspend (StoredEvent) -> Unit,
    ) {
        while (true) {
            val batch = dbQuery(db) { eventsAfter(cursorOf(name), BATCH_SIZE) }
            if (batch.isEmpty()) return
            for (event in batch) {
                try {
                    handler(event)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (
                    @Suppress("TooGenericExceptionCaught") failure: Exception,
                ) {
                    log.error("Event subscriber {} failed at seq {} ({})", name, event.seq, event.type, failure)
                    return
                }
                dbQuery(db) { saveCursor(name, event.seq) }
            }
        }
    }

    private companion object {
        const val BATCH_SIZE = 100
    }
}
