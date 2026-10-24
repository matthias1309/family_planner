package de.familyplanner.tasks.fakes

import de.familyplanner.core.fakes.InMemoryMemberRepository
import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.Role
import de.familyplanner.tasks.board.Actor
import de.familyplanner.tasks.board.BoardService
import de.familyplanner.tasks.catalog.CatalogService
import de.familyplanner.tasks.catalog.CatalogTask
import de.familyplanner.tasks.catalog.CatalogTaskInput
import java.time.Instant

/** Wires the in-memory fakes that the task services share in unit tests. */
class TasksFixture(
    start: Instant = Instant.parse("2026-10-24T10:00:00Z"),
) {
    val clock = MutableClock(start)
    val members = InMemoryMemberRepository()
    val catalogRepository = InMemoryCatalogRepository()
    val cardRepository = InMemoryTaskCardRepository()
    val notifier = CountingEventNotifier()
    private var nextCatalogId = 0
    private var nextCardId = 0
    private var nextChild = 0
    val catalog = CatalogService(catalogRepository, clock) { "catalog-${++nextCatalogId}" }
    val board = BoardService(members, catalogRepository, cardRepository, notifier, clock) { "card-${++nextCardId}" }

    suspend fun parent(): Actor = Actor(members.create(aMember(role = Role.PARENT)).id, Role.PARENT)

    suspend fun child(name: String = "Kind-${++nextChild}"): Actor =
        Actor(members.create(aMember(name = name, role = Role.CHILD)).id, Role.CHILD)

    suspend fun catalogTask(
        points: Int = 2,
        title: String = "Aufgabe ${nextCatalogId + 1}",
        emoji: String? = null,
    ): CatalogTask = catalog.create(CatalogTaskInput(title, emoji, points))
}
