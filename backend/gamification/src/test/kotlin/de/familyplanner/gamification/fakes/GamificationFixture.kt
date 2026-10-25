package de.familyplanner.gamification.fakes

import de.familyplanner.core.events.DomainEvent
import de.familyplanner.core.events.StoredEvent
import de.familyplanner.core.fakes.InMemoryMemberRepository
import de.familyplanner.core.fakes.MutableClock
import de.familyplanner.core.fakes.aMember
import de.familyplanner.core.member.Role
import de.familyplanner.gamification.account.AccountService
import de.familyplanner.gamification.account.Actor
import de.familyplanner.gamification.ledger.PointsEventHandler
import de.familyplanner.gamification.settings.GameSettingsService
import java.time.Instant

class GamificationFixture(
    start: Instant = Instant.parse("2026-10-24T10:00:00Z"),
) {
    val clock = MutableClock(start)
    val members = InMemoryMemberRepository()
    val ledger = InMemoryLedgerRepository()
    val settings = GameSettingsService(InMemoryGameSettingsRepository(), clock)
    private var nextId = 0
    private var nextChild = 0
    val accounts = AccountService(members, ledger, clock) { "entry-${++nextId}" }
    val handler = PointsEventHandler(members, ledger, settings) { "credit-${++nextId}" }

    suspend fun parent(): Actor = Actor(members.create(aMember(role = Role.PARENT)).id, Role.PARENT)

    suspend fun child(name: String = "Kind-${++nextChild}"): Actor =
        Actor(members.create(aMember(name = name, role = Role.CHILD)).id, Role.CHILD)

    fun stored(
        event: DomainEvent?,
        seq: Long = 1,
    ) = StoredEvent(seq, "test", clock.instant(), event)
}
