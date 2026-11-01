package de.familyplanner.tasks.board

import de.familyplanner.core.error.AppException
import de.familyplanner.tasks.catalog.CatalogRepository
import de.familyplanner.tasks.catalog.CatalogTask
import de.familyplanner.tasks.catalog.CatalogTaskId
import org.slf4j.LoggerFactory
import java.time.Clock
import java.util.UUID

/** Children take tasks that parents released in the catalog (REQ-TASK-028). */
class PoolService(
    private val catalog: CatalogRepository,
    private val cards: TaskCardRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(PoolService::class.java)

    /** Creates a card for [actor]: TODO, or REVIEW (reported at once) when [alreadyDone]. */
    suspend fun take(
        actor: Actor,
        catalogTaskId: CatalogTaskId,
        alreadyDone: Boolean,
    ): TaskCard {
        if (actor.isParent) throw AppException.Forbidden("Nur Kinder können Aufgaben aus dem Pool nehmen")
        val task = catalog.findById(catalogTaskId) ?: throw AppException.NotFound("Aufgabe nicht gefunden")
        requireTakeable(task)
        val card = newCard(TaskCardId(newId()), actor.id, task, clock.instant(), selfAssigned = true, alreadyDone = alreadyDone)
        cards.createAll(listOf(card))
        log.info("Pool task taken: {} by {}", card.id.value, actor.id.value)
        return card
    }

    private fun requireTakeable(task: CatalogTask) {
        if (task.archived) throw AppException.Conflict("Die Aufgabe ist archiviert")
        if (!task.childSelectable) throw AppException.Forbidden("Diese Aufgabe ist nicht frei wählbar")
    }
}
