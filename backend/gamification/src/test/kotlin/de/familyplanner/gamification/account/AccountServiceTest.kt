package de.familyplanner.gamification.account

import de.familyplanner.core.error.AppException
import de.familyplanner.core.member.MemberId
import de.familyplanner.gamification.fakes.GamificationFixture
import de.familyplanner.gamification.ledger.EntryKind
import de.familyplanner.gamification.ledger.LedgerEntry
import de.familyplanner.gamification.ledger.LedgerEntryId
import de.familyplanner.gamification.ledger.REASON_MAX
import kotlinx.coroutines.test.runTest
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountServiceTest {
    private val fixture = GamificationFixture()

    private suspend fun credit(
        child: Actor,
        kind: EntryKind,
        amount: Int,
    ) {
        fixture.clock.advance(Duration.ofMinutes(1))
        val id = LedgerEntryId("e-$kind-$amount")
        fixture.ledger.append(LedgerEntry(id, child.id, kind, amount, "t", null, null, null, fixture.clock.instant()))
    }

    @Test
    fun `REQ-GAME-012 child sees own collected, balance and entries newest first`() =
        runTest {
            val child = fixture.child()
            credit(child, EntryKind.TASK, 2)
            credit(child, EntryKind.COOKING, 3)
            credit(child, EntryKind.MANUAL, -1)

            val account = fixture.accounts.account(child, child.id, 50)

            assertEquals(4, account.totals.collected)
            assertEquals(4, account.totals.balance)
            assertEquals(listOf(-1, 3, 2), account.entries.map { it.amount })
        }

    @Test
    fun `REQ-GAME-012 collected ignores spending kinds`() =
        runTest {
            val child = fixture.child()
            credit(child, EntryKind.TASK, 10)
            credit(child, EntryKind.SHOP, -15)

            val totals = fixture.accounts.account(child, child.id, 50).totals

            assertEquals(10, totals.collected)
            assertEquals(-5, totals.balance)
        }

    @Test
    fun `REQ-GAME-012 child cannot see a sibling's account`() =
        runTest {
            val child = fixture.child()
            val sibling = fixture.child()

            assertFailsWith<AppException.Forbidden> { fixture.accounts.account(child, sibling.id, 50) }
        }

    @Test
    fun `REQ-GAME-012 parent sees every child's account, parent member is rejected, limit 1 to 100`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()

            fixture.accounts.account(parent, child.id, 1)
            fixture.accounts.account(parent, child.id, 100)
            assertFailsWith<AppException.Validation> { fixture.accounts.account(parent, parent.id, 50) }
            assertFailsWith<AppException.Validation> { fixture.accounts.account(parent, child.id, 0) }
            assertFailsWith<AppException.Validation> { fixture.accounts.account(parent, child.id, 101) }
            assertFailsWith<AppException.NotFound> { fixture.accounts.account(parent, MemberId("x"), 50) }
        }

    @Test
    fun `REQ-GAME-013 parent books plus 12 from the kitchen board`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()

            val entry = fixture.accounts.book(parent, child.id, 12, " Übernahme Küchenboard ")

            assertEquals(EntryKind.MANUAL, entry.kind)
            assertEquals("Übernahme Küchenboard", entry.title)
            assertEquals(parent.id, entry.createdBy)
            val totals = fixture.accounts.account(parent, child.id, 50).totals
            assertEquals(12, totals.collected)
            assertEquals(12, totals.balance)
        }

    @Test
    fun `REQ-GAME-013 negative correction lowers both values and may go below zero`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()

            fixture.accounts.book(parent, child.id, -2, "Fehler")

            val totals = fixture.accounts.account(parent, child.id, 50).totals
            assertEquals(-2, totals.collected)
            assertEquals(-2, totals.balance)
        }

    @Test
    fun `REQ-GAME-013 amount 0, over 1000 or empty reason is rejected`() =
        runTest {
            val parent = fixture.parent()
            val child = fixture.child()

            assertFailsWith<AppException.Validation> { fixture.accounts.book(parent, child.id, 0, "x") }
            assertFailsWith<AppException.Validation> { fixture.accounts.book(parent, child.id, 1001, "x") }
            assertFailsWith<AppException.Validation> { fixture.accounts.book(parent, child.id, -1001, "x") }
            assertFailsWith<AppException.Validation> { fixture.accounts.book(parent, child.id, 5, "  ") }
            assertFailsWith<AppException.Validation> { fixture.accounts.book(parent, child.id, 5, "x".repeat(REASON_MAX + 1)) }
            assertFailsWith<AppException.Validation> { fixture.accounts.book(parent, parent.id, 5, "x") }
            fixture.accounts.book(parent, child.id, 1000, "x".repeat(REASON_MAX))
            fixture.accounts.book(parent, child.id, -1000, "x")
        }

    @Test
    fun `REQ-GAME-013 child cannot book`() =
        runTest {
            val child = fixture.child()

            assertFailsWith<AppException.Forbidden> { fixture.accounts.book(child, child.id, 5, "x") }
        }

    @Test
    fun `REQ-GAME-014 leaderboard lists children only`() =
        runTest {
            fixture.parent()
            val a = fixture.child("Anna")
            fixture.child("Ben")
            credit(a, EntryKind.TASK, 4)

            val board = fixture.accounts.leaderboard()

            assertEquals(listOf("Anna", "Ben"), board.map { it.name })
        }
}
