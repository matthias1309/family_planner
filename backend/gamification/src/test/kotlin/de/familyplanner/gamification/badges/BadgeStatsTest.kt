package de.familyplanner.gamification.badges

import de.familyplanner.core.member.MemberId
import de.familyplanner.gamification.ledger.EntryKind
import de.familyplanner.gamification.ledger.LedgerEntry
import de.familyplanner.gamification.ledger.LedgerEntryId
import java.time.Instant
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

private var entryCounter = 0

fun ledgerEntry(
    kind: EntryKind,
    amount: Int,
    at: String = "2026-10-24T10:00:00Z",
    memberId: MemberId = MemberId("child-1"),
) = LedgerEntry(LedgerEntryId("e-${++entryCounter}"), memberId, kind, amount, "t", null, null, null, Instant.parse(at))

class BadgeStatsTest {
    @Test
    fun `REQ-GAME-020 counts tasks and cookings`() {
        val entries =
            List(3) { ledgerEntry(EntryKind.TASK, 2) } +
                List(2) { ledgerEntry(EntryKind.COOKING, 3) } +
                ledgerEntry(EntryKind.MANUAL, 10)

        val stats = badgeStatsFrom(entries)

        assertEquals(3, stats.tasks)
        assertEquals(2, stats.cookings)
        assertEquals(22, stats.collected)
    }

    @Test
    fun `REQ-GAME-020 negative manual lowers collected but not counts`() {
        val stats = badgeStatsFrom(listOf(ledgerEntry(EntryKind.MANUAL, 100), ledgerEntry(EntryKind.MANUAL, -5)))

        assertEquals(95, stats.collected)
        assertEquals(0, stats.tasks)
    }

    @Test
    fun `REQ-GAME-020 longest streak uses Berlin days`() {
        val entries =
            listOf(
                ledgerEntry(EntryKind.TASK, 1, "2026-10-24T22:30:00Z"),
                ledgerEntry(EntryKind.TASK, 1, "2026-10-25T08:00:00Z"),
            )

        assertEquals(1, badgeStatsFrom(entries).longestTaskStreak)
    }

    @Test
    fun `REQ-GAME-020 longest streak finds the longest run`() {
        val days = listOf(1, 2, 3, 5, 6).map { LocalDate.of(2026, 10, it) }.toSet()

        assertEquals(3, longestStreak(days))
    }

    @Test
    fun `REQ-GAME-020 empty ledger yields zero stats`() {
        assertEquals(BadgeStats.ZERO, badgeStatsFrom(emptyList()))
        assertEquals(0, longestStreak(emptySet()))
    }

    @Test
    fun `REQ-GAME-020 task and cooking on same day count`() {
        val entries =
            listOf(
                ledgerEntry(EntryKind.TASK, 1, "2026-10-24T08:00:00Z"),
                ledgerEntry(EntryKind.COOKING, 3, "2026-10-24T16:00:00Z"),
                ledgerEntry(EntryKind.COOKING, 3, "2026-10-25T16:00:00Z"),
            )

        assertEquals(1, badgeStatsFrom(entries).taskAndCookingDays)
    }

    @Test
    fun `REQ-GAME-021 catalog ids are unique and stable`() {
        val expected =
            listOf(
                "first-task",
                "tasks-10",
                "tasks-50",
                "first-cooking",
                "cooking-10",
                "cooking-25",
                "points-100",
                "points-500",
                "streak-7",
                "streak-30",
                "all-rounder",
                "points-1000",
            )

        assertEquals(expected, Badge.entries.map { it.id })
    }
}
